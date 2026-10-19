param(
 [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]+$')][string]$Label,
 [switch]$ApprovedSingleRun
)
$ErrorActionPreference='Stop'
if(-not $ApprovedSingleRun){throw 'D3c execution requires explicit owner approval of one run; no JVM started.'}
$repo=(Resolve-Path "$PSScriptRoot/../..").Path
$run=Join-Path $repo ".local-tools/bolt-lifecycle/$Label"
if(Test-Path -LiteralPath $run){throw 'Use a fresh label; never overwrite evidence.'}
$jdk='C:/Program Files/Java/jdk-17'
foreach($tool in @('javac.exe','java.exe')){if(-not(Test-Path "$jdk/bin/$tool")){throw 'Existing JDK missing; stop offline.'}}
# Needed for supervised direct-child ownership checks, before starting ANY Java process.
$self=Get-CimInstance Win32_Process -Filter "ProcessId = $PID" -ErrorAction Stop
if($null -eq $self -or -not $self.ExecutablePath -or -not $self.CommandLine){throw 'Process ownership preflight unavailable; no Java launch.'}
[xml]$xml=Get-Content "$repo/forge-gui-desktop/target/surefire-reports/TEST-forge.net.embedded.ProtocolGuiGameInProcessTest.xml"
$cachedCp=($xml.testsuite.properties.property|Where-Object name -eq 'surefire.test.class.path').value
if(-not $cachedCp){throw 'Existing offline reactor classpath missing; no fallback.'}
$entries=@($cachedCp.Split(';')|Where-Object{$_ -and $_ -notmatch '[/\\]org[/\\]testng[/\\]'})
foreach($entry in $entries){
 if(-not(Test-Path -LiteralPath $entry)){throw 'Missing existing offline classpath entry.'}
 if(-not $entry.StartsWith($repo+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Classpath outside project requires review.'}
}
$protected=@('pom.xml','forge-gui-desktop/pom.xml','forge-gui/res/cardsfolder/l/lightning_bolt.txt','forge-gui/res/cardsfolder/m/mountain.txt')
Push-Location $repo
try{
 $protected+=@(git ls-files 'forge-*/src/main/java/*.java' 'forge-gui-desktop/src/test/java/forge/net/*.java' 'forge-gui-desktop/src/test/java/forge/net/embedded/*.java' 'experiments/ai-teavm/src/observer/java/probe/JdiWitness.java' 'experiments/standalone-bolt/*')
 if($LASTEXITCODE -ne 0){throw 'Protected source inventory failed'}
}finally{Pop-Location}
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
 else{Remove-Item -LiteralPath "Env:$Name" -ErrorAction SilentlyContinue}
}
function Equal-Env($A,$B){return $A.exists -eq $B.exists -and [string]::Equals($A.value,$B.value,[StringComparison]::Ordinal)}
$probe='FORGE_D3C_RESTORE_PROBE';$originalProbe=Read-Env $probe
try{
 Remove-Item -LiteralPath "Env:$probe" -ErrorAction SilentlyContinue;$absent=Read-Env $probe
 Set-Item -LiteralPath "Env:$probe" -Value 'probe-only';$present=Read-Env $probe
 Restore-Env $probe $absent
 if(-not(Equal-Env (Read-Env $probe) $absent)){throw 'Absent restoration preflight failed'}
 Restore-Env $probe $present
 if(-not(Equal-Env (Read-Env $probe) $present)){throw 'Present restoration preflight failed'}
}finally{Restore-Env $probe $originalProbe}
if(-not(Equal-Env (Read-Env $probe) $originalProbe)){throw 'Probe state not restored'}
foreach($dir in @('home','tmp','appdata','localappdata','classes','empty-sourcepath')){New-Item -ItemType Directory -Force -Path "$run/$dir"|Out-Null}
$before=Snapshot;$before|ConvertTo-Json -Depth 6|Set-Content "$run/integrity-before.json" -Encoding utf8
$keys=@('JAVA_HOME','JAVA_TOOL_OPTIONS','APPDATA','LOCALAPPDATA','TEMP','TMP')
$saved=@{};foreach($key in $keys){$saved[$key]=Read-Env $key}
$processes=[Collections.Generic.List[object]]::new();$start=[DateTime]::UtcNow;$ok=$false;$primary=$null
function Run-Owned([string]$Tool,[string[]]$Arguments,[string]$Role,[int]$Seconds,[bool]$TrackTarget){
 $info=[Diagnostics.ProcessStartInfo]::new()
 $info.FileName="$jdk/bin/$Tool";$info.WorkingDirectory="$repo/forge-gui-desktop"
 $info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
 foreach($argument in $Arguments){$info.ArgumentList.Add($argument)}
 $p=[Diagnostics.Process]::new();$p.StartInfo=$info;$launched=[DateTime]::UtcNow
 if(-not $p.Start()){throw 'Owned process did not start'}
 $stdout=$p.StandardOutput.ReadToEndAsync();$stderr=$p.StandardError.ReadToEndAsync()
 $targets=@{};$timedOut=$false;$watch=[Diagnostics.Stopwatch]::StartNew()
 try{
  do{
   if($TrackTarget){
    # Retain a handle while the debugger target is alive, including a connector-launch stall.
    foreach($candidate in @(Get-CimInstance Win32_Process -Filter "ParentProcessId = $($p.Id)" -ErrorAction Stop)){
     if($targets.ContainsKey([int]$candidate.ProcessId)){continue}
     $owned=[Diagnostics.Process]::GetProcessById([int]$candidate.ProcessId)
     [void]$owned.Handle
     if($owned.HasExited){$owned.Dispose();continue}
     if($owned.StartTime.ToUniversalTime() -lt $launched.AddSeconds(-1)){$owned.Dispose();throw 'Child creation time does not match this launch'}
     $confirm=Get-CimInstance Win32_Process -Filter "ProcessId = $($owned.Id)" -ErrorAction Stop
     if($owned.HasExited){$owned.Dispose();continue}
     if($null -eq $confirm -or $confirm.ParentProcessId -ne $p.Id){$owned.Dispose();throw 'Child ownership changed before handle retention'}
     $targets[[int]$candidate.ProcessId]=$owned
     if(-not [string]::Equals($confirm.ExecutablePath,"$jdk/bin/java.exe".Replace('/','\'),[StringComparison]::OrdinalIgnoreCase) -or
        $confirm.CommandLine -notmatch 'experiment\.BoltLifecycleMain') {throw 'Unexpected owned child executable/entry point'}
    }
   }
   if($p.WaitForExit(250)){break}
   if($watch.Elapsed.TotalSeconds -ge $Seconds){$timedOut=$true;throw 'Owned process deadline; no retry'}
  }while($true)
  $stdout.GetAwaiter().GetResult()|Set-Content "$run/$Role.stdout.log" -Encoding utf8
  $stderr.GetAwaiter().GetResult()|Set-Content "$run/$Role.stderr.log" -Encoding utf8
  foreach($owned in $targets.Values){if(-not $owned.WaitForExit(10000)){throw 'Target process survived observer exit'}}
  if($p.ExitCode -ne 0){throw "Owned $Role failed; preserve evidence and stop without fixes/retries"}
 }finally{
  foreach($owned in $targets.Values){
   $forced=-not $owned.HasExited
   if($forced){$owned.Kill();[void]$owned.WaitForExit(10000)}
   $processes.Add([ordered]@{role='JDI-target';pid=$owned.Id;parentPid=$p.Id;hasExited=$owned.HasExited;forceTerminatedByLauncher=$forced;exitCode=$(if($owned.HasExited){$owned.ExitCode}else{$null})})
   $owned.Dispose()
  }
  $forcedRoot=-not $p.HasExited
  if($forcedRoot){$p.Kill();[void]$p.WaitForExit(10000)}
  $processes.Add([ordered]@{role=$Role;pid=$p.Id;parentPid=$PID;startedUtc=$launched.ToString('o');exitUtc=$(if($p.HasExited){$p.ExitTime.ToUniversalTime().ToString('o')}else{$null});exitCode=$(if($p.HasExited){$p.ExitCode}else{$null});hasExited=$p.HasExited;timeout=$timedOut;forceTerminatedByLauncher=$forcedRoot})
  # Preserve output even on a failed/expired observer, not just successful runs.
  [void][Threading.Tasks.Task]::WaitAll([Threading.Tasks.Task[]]@($stdout,$stderr),5000)
  if($stdout.IsCompleted){$stdout.GetAwaiter().GetResult()|Set-Content "$run/$Role.stdout.log" -Encoding utf8}
  if($stderr.IsCompleted){$stderr.GetAwaiter().GetResult()|Set-Content "$run/$Role.stderr.log" -Encoding utf8}
  $p.Dispose()
 }
}
try{
 $env:JAVA_HOME=$jdk;$forward=$run.Replace('\','/')
 $env:JAVA_TOOL_OPTIONS="-Duser.home=$forward/home -Djava.io.tmpdir=$forward/tmp -Djava.awt.headless=true -Xmx1536m"
 $env:APPDATA="$run/appdata";$env:LOCALAPPDATA="$run/localappdata";$env:TEMP="$run/tmp";$env:TMP="$run/tmp"
 $cp=$entries -join ';'
 $sources=@("$PSScriptRoot/src/experiment/BoltLifecycleMain.java","$PSScriptRoot/src/experiment/BoltLifecycleObserver.java","$PSScriptRoot/src/experiment/LifecycleJson.java",
  "$repo/forge-gui-desktop/src/test/java/forge/net/TestUtils.java","$repo/forge-gui-desktop/src/test/java/forge/net/HeadlessGuiDesktop.java","$repo/forge-gui-desktop/src/test/java/forge/net/HeadlessNetworkGuiGame.java")
 [ordered]@{entryPoint='experiment.BoltLifecycleObserver';target='experiment.BoltLifecycleMain';transport='dt_shmem';testngJarExcluded=$true;classpath=@($entries|ForEach-Object{[IO.Path]::GetRelativePath($repo,$_).Replace('\','/')});sources=(Hash-Paths @($sources|ForEach-Object{[IO.Path]::GetRelativePath($repo,$_).Replace('\','/')}))}|
  ConvertTo-Json -Depth 6|Set-Content "$run/inputs.json" -Encoding utf8
 Run-Owned 'javac.exe' (@('-g','-encoding','UTF-8','-source','17','-target','17','--add-modules','jdk.jdi','-sourcepath',"$run/empty-sourcepath",'-cp',$cp,'-d',"$run/classes")+$sources) 'compile' 60 $false
 [xml]$pom=Get-Content "$repo/pom.xml"
 $opens=$pom.SelectSingleNode('//*[local-name()="argLine"]').InnerText.Trim() -split '\s+'
 Run-Owned 'java.exe' (@('--add-modules','jdk.jdi',('-Dlifecycle.opens='+($opens -join ' ')))+@($opens)+@('-cp',"$run/classes;$cp",'experiment.BoltLifecycleObserver',$run)) 'observer' 220 $true
 $ordering=Get-Content "$run/ordering-result.json" -Raw|ConvertFrom-Json
 $driver=Get-Content "$run/driver-result.json" -Raw|ConvertFrom-Json
 if($ordering.outcome -cne 'PASS' -or $driver.outcome -cne 'PASS' -or $ordering.normalGenerations -ne 5 -or -not $ordering.targetHasExited -or
    $ordering.targetExit -ne 0 -or $ordering.targetForceTerminated -or $driver.c1Questions -ne 3 -or -not $driver.originalOpponent20To17){throw 'Witness/original-game verification did not pass'}
 $ok=$true
}catch{$primary=$_;throw}
finally{
 foreach($key in $keys){Restore-Env $key $saved[$key]}
 $checks=@(foreach($key in $keys){[ordered]@{name=$key;exactMatch=(Equal-Env (Read-Env $key) $saved[$key])}})
 $restored=@($checks|Where-Object{-not $_.exactMatch}).Count -eq 0
 $after=Snapshot;$after|ConvertTo-Json -Depth 6|Set-Content "$run/integrity-after.json" -Encoding utf8
 $sourceDiff=@(Differences $before.protected $after.protected);$cacheDiff=@(Differences $before.cache $after.cache)
 $integrity=$sourceDiff.Count -eq 0 -and $cacheDiff.Count -eq 0
 $result=[ordered]@{label=$Label;passed=($ok -and $restored -and $integrity);startedUtc=$start.ToString('o');endedUtc=[DateTime]::UtcNow.ToString('o');environmentRestored=$restored;restorationPreflightPassed=$true;environmentChecks=$checks;protectedFileCount=$before.protected.Count;cacheFileCount=$before.cache.Count;sourceChanges=$sourceDiff;cacheChanges=$cacheDiff;processes=$processes;singleRun=$true;noGameplayCleanup=$true}
 $result|ConvertTo-Json -Depth 8|Set-Content "$run/result.json" -Encoding utf8
 $result|ConvertTo-Json -Depth 8
 if((-not $restored -or -not $integrity) -and $null -eq $primary){throw 'Integrity/restoration failed; stop without retry'}
}
