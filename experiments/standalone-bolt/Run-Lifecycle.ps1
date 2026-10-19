param(
 [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]+$')][string]$Label,
 [switch]$ApprovedSingleRun
)
$ErrorActionPreference='Stop'
if(-not $ApprovedSingleRun){throw 'D3c execution requires explicit owner approval of one run; no JVM started.'}
$repo=(Resolve-Path "$PSScriptRoot/../..").Path
$run=Join-Path $repo ".local-tools/bolt-lifecycle/$Label"
if(Test-Path -LiteralPath $run){throw 'Use a fresh label; never overwrite evidence.'}
# No bypass switch: CIM operation timeouts do not establish an unconditional OS/provider wall-clock bound.
throw 'D3c safety gate HOLD: unconditional supervision bound not established; no processes started.'

. "$PSScriptRoot/LauncherSafety.ps1"
$jdk='C:/Program Files/Java/jdk-17'
$ledger=New-LauncherFailureLedger;$stage='preflight';$start=[DateTime]::UtcNow;$ok=$false;$restorationPreflightPassed=$false
$processes=[Collections.Generic.List[object]]::new();$saved=@{};$before=$null
$keys=@('JAVA_HOME','JAVA_TOOL_OPTIONS','APPDATA','LOCALAPPDATA','TEMP','TMP')
function Hash-Paths($Paths){
 $map=[ordered]@{}
 foreach($path in @($Paths|Sort-Object -Unique)){$map[$path]=(Get-FileHash -LiteralPath (Join-Path $repo $path)).Hash.ToLowerInvariant()}
 return $map
}
function Snapshot{
 $cache=@(Get-ChildItem "$repo/.local-tools/m2" -Recurse -File|ForEach-Object{[IO.Path]::GetRelativePath($repo,$_.FullName).Replace('\','/')})
 return [ordered]@{protected=(Hash-Paths $protected);cache=(Hash-Paths $cache)}
}
function Differences($Before,$After){
 return @(Compare-Object @($Before.Keys|ForEach-Object{"$_`t$($Before[$_])"}) @($After.Keys|ForEach-Object{"$_`t$($After[$_])"}))
}
function Read-Env([string]$Name){
 $exists=Test-Path -LiteralPath "Env:$Name"
 return @{exists=$exists;value=$(if($exists){(Get-Item -LiteralPath "Env:$Name").Value}else{$null})}
}
function Restore-Env([string]$Name,$State){
 if($State.exists){Set-Item -LiteralPath "Env:$Name" -Value $State.value}
 elseif(Test-Path -LiteralPath "Env:$Name"){Remove-Item -LiteralPath "Env:$Name" -ErrorAction Stop}
}
function Equal-Env($A,$B){return $A.exists -eq $B.exists -and [string]::Equals($A.value,$B.value,[StringComparison]::Ordinal)}
function Record-ChildValidation($Observed,[string]$Stage,[int]$ExpectedParentId,$CreationTimeMatches,$StartedUtc){
 # Never persist arbitrary paths or arguments: unexpected children may contain secrets.
 $executable=[string]$Observed.ExecutablePath;$commandLine=[string]$Observed.CommandLine
 $executableMatches=[string]::Equals($executable,"$jdk/bin/java.exe".Replace('/','\'),[StringComparison]::OrdinalIgnoreCase)
 $identity=Get-StrictJavaMainIdentity $commandLine $jdk (Join-Path $run 'driver-result.json')
 $row=[ordered]@{
  observedUtc=[DateTime]::UtcNow.ToString('o');stage=$Stage;recordAvailable=($null -ne $Observed)
  pid=$(if($null -ne $Observed){$Observed.ProcessId}else{$null});expectedParentPid=$ExpectedParentId
  parentPid=$(if($null -ne $Observed){$Observed.ParentProcessId}else{$null})
  parentPidMatches=($null -ne $Observed -and $Observed.ParentProcessId -eq $ExpectedParentId)
  handleStartedUtc=$StartedUtc;creationTimeMatches=$CreationTimeMatches
  executablePathPresent=(-not [string]::IsNullOrEmpty($executable));executablePathLength=$executable.Length
  executablePathMatches=$executableMatches
  executablePathSummary=$(if($executableMatches){'<JDK>/bin/java.exe'}else{'<redacted-unrecognized-path>'})
  commandLinePresent=(-not [string]::IsNullOrEmpty($commandLine));commandLineLength=$commandLine.Length
  entryPointMatches=$identity.mainClassMatches;entryPointUnambiguous=$identity.unambiguous;entryPointReason=$identity.reason
  otherObservedData='<redacted: no raw executable path, class name or command-line arguments retained>'
 }
 $row|ConvertTo-Json -Depth 5 -Compress|Add-Content "$run/child-validation.jsonl" -Encoding utf8
 return $row
}
function Run-Owned([string]$Tool,[string[]]$Arguments,[ValidateSet('compile','observer')][string]$Role,[int]$Seconds,[bool]$TrackTarget){
 $p=[Diagnostics.Process]::new();$root=$null;$targets=@{};$stage=$Role+'-start'
 try{
  $info=[Diagnostics.ProcessStartInfo]::new()
  $info.FileName="$jdk/bin/$Tool";$info.WorkingDirectory="$repo/forge-gui-desktop"
  $info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
  foreach($argument in $Arguments){$info.ArgumentList.Add($argument)}
  $p.StartInfo=$info;$launched=[DateTime]::UtcNow
  if(-not $p.Start()){throw 'Owned process did not start'}
  # Register before ID/stream getters, so exceptions there still reach owned cleanup.
  $root=[pscustomobject]@{Process=$p;Role=$Role;Id=$null;ParentId=$PID;StartedUtc=$launched.ToString('o');Timeout=$false;OutputTasks=[ordered]@{stdout=$null;stderr=$null}}
  $root.Id=$p.Id
  $root.OutputTasks.stdout=$p.StandardOutput.ReadToEndAsync();$root.OutputTasks.stderr=$p.StandardError.ReadToEndAsync()
  $watch=[Diagnostics.Stopwatch]::StartNew()
  do{
   if($null -ne $ledger.Primary){throw $ledger.Primary}
   if($TrackTarget){
    $stage='child-cim-enumerate'
    foreach($candidate in @(Get-CimInstance Win32_Process -Filter "ParentProcessId = $($p.Id)" -OperationTimeoutSec 5 -ErrorAction Stop)){
     if($null -ne $ledger.Primary){throw $ledger.Primary}
     if($targets.ContainsKey([int]$candidate.ProcessId)){continue}
     $owned=$null;$retained=$false
     try{
      $stage='child-candidate-diagnostic';[void](Record-ChildValidation $candidate 'candidate' $p.Id $null $null)
      $stage='child-open-handle';$owned=[Diagnostics.Process]::GetProcessById([int]$candidate.ProcessId);[void]$owned.Handle
      if($owned.HasExited){continue}
      $stage='child-creation';$childStarted=$owned.StartTime.ToUniversalTime()
      $creationMatches=$childStarted -ge $launched.AddSeconds(-1)
      if(-not $creationMatches){
       [void](Record-ChildValidation $candidate 'creation-rejected' $p.Id $false $childStarted.ToString('o'))
       throw 'Child creation time does not match this launch'
      }
      $stage='child-cim-confirm'
      $confirm=Get-CimInstance Win32_Process -Filter "ProcessId = $($owned.Id)" -OperationTimeoutSec 5 -ErrorAction Stop
      if($owned.HasExited){continue}
      $stage='child-parent'
      if($null -eq $confirm -or $confirm.ProcessId -ne $owned.Id -or $confirm.ParentProcessId -ne $p.Id){
       [void](Record-ChildValidation $confirm 'parent-rejected' $p.Id $creationMatches $childStarted.ToString('o'))
       throw 'Child ownership changed before handle retention'
      }
      # Only handles with proven parent/creation identity enter terminating cleanup.
      $targets[[int]$candidate.ProcessId]=[pscustomobject]@{Process=$owned;Role='JDI-target';Id=$owned.Id;ParentId=$p.Id;StartedUtc=$childStarted.ToString('o');Timeout=$false;OutputTasks=$null}
      $retained=$true;$stage='child-identity'
      $validation=Record-ChildValidation $confirm 'retained-child' $p.Id $creationMatches $childStarted.ToString('o')
      if(-not $validation.executablePathMatches -or -not $validation.entryPointUnambiguous -or -not $validation.entryPointMatches){throw 'Unexpected owned child executable/entry point'}
     }catch{Save-LauncherFailure $ledger $_ $stage;throw}
     finally{
      if($null -ne $owned -and -not $retained){Invoke-LauncherFinalStep $ledger 'unretained-handle-dispose' {$owned.Dispose()}}
     }
    }
   }
   $stage=$Role+'-wait'
   if($p.WaitForExit(250)){break}
   if($watch.Elapsed.TotalSeconds -ge $Seconds){$root.Timeout=$true;throw 'Owned process elapsed-time budget exceeded; no retry'}
  }while($true)
  $stage='child-exit-wait'
  foreach($entry in $targets.Values){if(-not $entry.Process.WaitForExit(10000)){throw 'Target process survived observer exit'}}
  $stage=$Role+'-exit'
  if($p.ExitCode -ne 0){throw 'Owned process failed; preserve evidence and stop without fixes/retries'}
 }catch{Save-LauncherFailure $ledger $_ $stage}
 finally{
  foreach($entry in $targets.Values){Invoke-LauncherFinalStep $ledger 'child-cleanup' {Finish-OwnedLauncherProcess $entry $ledger $processes $run}}
  if($null -ne $root){Invoke-LauncherFinalStep $ledger 'root-cleanup' {Finish-OwnedLauncherProcess $root $ledger $processes $run}}
  else{Invoke-LauncherFinalStep $ledger 'unstarted-process-dispose' {$p.Dispose()}}
 }
 if($null -ne $ledger.Primary){throw $ledger.Primary}
}
try{
foreach($tool in @('javac.exe','java.exe')){if(-not(Test-Path "$jdk/bin/$tool")){throw 'Existing JDK missing; stop offline.'}}
# Needed for supervised direct-child ownership checks, before starting ANY Java process.
$stage='preflight-cim'
$self=Get-CimInstance Win32_Process -Filter "ProcessId = $PID" -OperationTimeoutSec 5 -ErrorAction Stop
if($null -eq $self -or -not $self.ExecutablePath -or -not $self.CommandLine){throw 'Process ownership preflight unavailable; no Java launch.'}
$stage='preflight-classpath'
[xml]$xml=Get-Content "$repo/forge-gui-desktop/target/surefire-reports/TEST-forge.net.embedded.ProtocolGuiGameInProcessTest.xml"
$cachedCp=($xml.testsuite.properties.property|Where-Object name -eq 'surefire.test.class.path').value
if(-not $cachedCp){throw 'Existing offline reactor classpath missing; no fallback.'}
$entries=@($cachedCp.Split(';')|Where-Object{$_ -and $_ -notmatch '[/\\]org[/\\]testng[/\\]'})
foreach($entry in $entries){
 if(-not(Test-Path -LiteralPath $entry)){throw 'Missing existing offline classpath entry.'}
 if(-not $entry.StartsWith($repo+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Classpath outside project requires review.'}
}
$stage='preflight-inventory'
$protected=@('pom.xml','forge-gui-desktop/pom.xml','forge-gui/res/cardsfolder/l/lightning_bolt.txt','forge-gui/res/cardsfolder/m/mountain.txt')
Push-Location $repo
try{
 $protected+=@(git ls-files 'forge-*/src/main/java/*.java' 'forge-gui-desktop/src/test/java/forge/net/*.java' 'forge-gui-desktop/src/test/java/forge/net/embedded/*.java' 'experiments/ai-teavm/src/observer/java/probe/JdiWitness.java' 'experiments/standalone-bolt/*')
 if($LASTEXITCODE -ne 0){throw 'Protected source inventory failed'}
}catch{Save-LauncherFailure $ledger $_ $stage;throw}
finally{Invoke-LauncherFinalStep $ledger 'preflight-pop-location' {Pop-Location}}
if($null -ne $ledger.Primary){throw $ledger.Primary}
$stage='environment-snapshot'
foreach($key in $keys){$saved[$key]=Read-Env $key}
$stage='restoration-preflight'
$probe='FORGE_D3C_RESTORE_PROBE';$originalProbe=Read-Env $probe
try{
 if(Test-Path -LiteralPath "Env:$probe"){Remove-Item -LiteralPath "Env:$probe" -ErrorAction Stop}
 $absent=Read-Env $probe
 Set-Item -LiteralPath "Env:$probe" -Value 'probe-only';$present=Read-Env $probe
 Restore-Env $probe $absent
 if(-not(Equal-Env (Read-Env $probe) $absent)){throw 'Absent restoration preflight failed'}
 Restore-Env $probe $present
 if(-not(Equal-Env (Read-Env $probe) $present)){throw 'Present restoration preflight failed'}
}catch{Save-LauncherFailure $ledger $_ $stage;throw}
finally{Invoke-LauncherFinalStep $ledger 'restore-probe' {Restore-Env $probe $originalProbe}}
if(-not(Equal-Env (Read-Env $probe) $originalProbe)){throw 'Probe state not restored'}
if($null -ne $ledger.Primary){throw $ledger.Primary}
$restorationPreflightPassed=$true
$stage='initial-integrity'
foreach($dir in @('home','tmp','appdata','localappdata','classes','empty-sourcepath')){New-Item -ItemType Directory -Force -Path "$run/$dir"|Out-Null}
$before=Snapshot;$before|ConvertTo-Json -Depth 6|Set-Content "$run/integrity-before.json" -Encoding utf8
$stage='isolated-environment'
 $env:JAVA_HOME=$jdk;$forward=$run.Replace('\','/')
 $env:JAVA_TOOL_OPTIONS="-Duser.home=$forward/home -Djava.io.tmpdir=$forward/tmp -Djava.awt.headless=true -Xmx1536m"
 $env:APPDATA="$run/appdata";$env:LOCALAPPDATA="$run/localappdata";$env:TEMP="$run/tmp";$env:TMP="$run/tmp"
 $cp=$entries -join ';'
 $sources=@("$PSScriptRoot/src/experiment/BoltLifecycleMain.java","$PSScriptRoot/src/experiment/BoltLifecycleObserver.java","$PSScriptRoot/src/experiment/LifecycleJson.java",
  "$repo/forge-gui-desktop/src/test/java/forge/net/TestUtils.java","$repo/forge-gui-desktop/src/test/java/forge/net/HeadlessGuiDesktop.java","$repo/forge-gui-desktop/src/test/java/forge/net/HeadlessNetworkGuiGame.java")
 $stage='compile-input-evidence'
 [ordered]@{entryPoint='experiment.BoltLifecycleObserver';target='experiment.BoltLifecycleMain';transport='dt_shmem';testngJarExcluded=$true;classpath=@($entries|ForEach-Object{[IO.Path]::GetRelativePath($repo,$_).Replace('\','/')});sources=(Hash-Paths @($sources|ForEach-Object{[IO.Path]::GetRelativePath($repo,$_).Replace('\','/')}))}|
  ConvertTo-Json -Depth 6|Set-Content "$run/inputs.json" -Encoding utf8
 $stage='compile'
 Run-Owned 'javac.exe' (@('-g','-encoding','UTF-8','-source','17','-target','17','--add-modules','jdk.jdi','-sourcepath',"$run/empty-sourcepath",'-cp',$cp,'-d',"$run/classes")+$sources) 'compile' 60 $false
 $stage='original-jvm-opens'
 [xml]$pom=Get-Content "$repo/pom.xml"
 $opens=$pom.SelectSingleNode('//*[local-name()="argLine"]').InnerText.Trim() -split '\s+'
 $stage='observer'
 Run-Owned 'java.exe' (@('--add-modules','jdk.jdi',('-Dlifecycle.opens='+($opens -join ' ')))+@($opens)+@('-cp',"$run/classes;$cp",'experiment.BoltLifecycleObserver',$run)) 'observer' 220 $true
 $stage='verify-results'
 $ordering=Get-Content "$run/ordering-result.json" -Raw|ConvertFrom-Json
 $driver=Get-Content "$run/driver-result.json" -Raw|ConvertFrom-Json
 if($ordering.outcome -cne 'PASS' -or $driver.outcome -cne 'PASS' -or $ordering.normalGenerations -ne 5 -or -not $ordering.targetHasExited -or
    $ordering.targetExit -ne 0 -or $ordering.targetForceTerminated -or $driver.c1Questions -ne 3 -or -not $driver.originalOpponent20To17){throw 'Witness/original-game verification did not pass'}
 $ok=$true
}catch{Save-LauncherFailure $ledger $_ $stage}
finally{
 try{
 $checks=[Collections.Generic.List[object]]::new()
 foreach($key in $keys){
  $check=[pscustomobject]@{name=$key;captured=$saved.ContainsKey($key);exactMatch=$null};$checks.Add($check)
  if($check.captured){
   Invoke-LauncherFinalStep $ledger 'restore-environment' {Restore-Env $key $saved[$key]}
   Invoke-LauncherFinalStep $ledger 'verify-environment' {
    $check.exactMatch=Equal-Env (Read-Env $key) $saved[$key]
    if(-not $check.exactMatch){throw 'Environment restoration mismatch'}
   }
  }
 }
 $integrity=[pscustomobject]@{verified=$false;sourceChanges=$null;cacheChanges=$null}
 if($null -ne $before){
  Invoke-LauncherFinalStep $ledger 'final-integrity' {
   $after=Snapshot
   $integrity.sourceChanges=@(Differences $before.protected $after.protected);$integrity.cacheChanges=@(Differences $before.cache $after.cache)
   $integrity.verified=$integrity.sourceChanges.Count -eq 0 -and $integrity.cacheChanges.Count -eq 0
   $after|ConvertTo-Json -Depth 6|Set-Content "$run/integrity-after.json" -Encoding utf8
   if(-not $integrity.verified){throw 'Integrity mismatch'}
  }
 }
 Invoke-LauncherFinalStep $ledger 'write-result' {
  $restored=$checks.Count -eq 6 -and @($checks|Where-Object{$_.exactMatch -ne $true}).Count -eq 0
  $failure=Get-LauncherFailureEvidence $ledger
  $result=[ordered]@{label=$Label;passed=($ok -and $restored -and $integrity.verified -and $null -eq $ledger.Primary);startedUtc=$start.ToString('o');endedUtc=[DateTime]::UtcNow.ToString('o')
   environmentRestored=$restored;restorationPreflightPassed=$restorationPreflightPassed;environmentChecks=@($checks.ToArray());integrityVerified=$integrity.verified
   protectedFileCount=$(if($null -ne $before){$before.protected.Count}else{$null});cacheFileCount=$(if($null -ne $before){$before.cache.Count}else{$null})
   sourceChanges=$integrity.sourceChanges;cacheChanges=$integrity.cacheChanges;processes=@($processes.ToArray());singleRun=$true;noGameplayCleanup=$true
   unconditionalWallClockBound=$false;primaryFailure=$failure.primaryFailure;secondaryFailures=$failure.secondaryFailures}
  $result|ConvertTo-Json -Depth 10|Set-Content "$run/result.json" -Encoding utf8
 }
 }catch{Save-LauncherFailure $ledger $_ 'outer-finally' -Secondary}
 finally{
 # Independent fallback attempt: result-write failures cannot erase the original ErrorRecord.
 Invoke-LauncherFinalStep $ledger 'write-failure-evidence' {
  Get-LauncherFailureEvidence $ledger|ConvertTo-Json -Depth 7|Set-Content "$run/failures.json" -Encoding utf8
 }
 Invoke-LauncherFinalStep $ledger 'console-failure-evidence' {Get-LauncherFailureEvidence $ledger|ConvertTo-Json -Depth 7|Write-Host}
 }
}
if($null -ne $ledger.Primary){throw $ledger.Primary}
