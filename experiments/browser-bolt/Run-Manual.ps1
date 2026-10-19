[CmdletBinding(DefaultParameterSetName='Verify')]
param(
 [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]+$')][string]$Label,
 [Parameter(Mandatory,ParameterSetName='Verify')][switch]$VerifyOnly,
 [Parameter(ParameterSetName='Verify')][ValidateSet('policy','dto','unanswered','regressions')][string]$Check='policy',
 [Parameter(Mandatory,ParameterSetName='Listen')][switch]$ApproveListener,
 [Parameter(ParameterSetName='Listen')][ValidateSet('127.0.0.1','100.122.229.121')][string]$BindAddress='127.0.0.1',
 [ValidateRange(8765,8765)][int]$Port=8765
)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path "$PSScriptRoot/../..").Path
$run=Join-Path $repo ".local-tools/browser-bolt/$Label"
if(Test-Path -LiteralPath $run){throw 'Use a fresh label; never overwrite evidence'}
if(-not $VerifyOnly -and -not $ApproveListener){throw 'Separate owner approval required before any listener'}
if($ApproveListener){
 $local=@([Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces()|ForEach-Object{$_.GetIPProperties().UnicastAddresses}|ForEach-Object{$_.Address.ToString()})
 if($BindAddress -notin $local){throw 'Approved literal bind address is not present locally'}
 if(@([Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()|Where-Object Port -eq $Port).Count){throw 'Port is occupied; do not disturb its listener'}
}
$jdk='C:/Program Files/Java/jdk-17';$maven="$repo/.local-tools/apache-maven-3.9.16/bin/mvn.cmd"
foreach($tool in @("$jdk/bin/java.exe",$maven)){if(-not(Test-Path -LiteralPath $tool)){throw 'Existing approved tool missing; no installation/fallback'}}
foreach($directory in @('home','tmp','appdata','localappdata','reports')){New-Item -ItemType Directory -Path "$run/$directory" -Force|Out-Null}
function Parent-Environment {
 $values=@{};foreach($key in @('JAVA_HOME','JAVA_TOOL_OPTIONS','APPDATA','LOCALAPPDATA','TEMP','TMP')){
  $values[$key]=@{exists=(Test-Path -LiteralPath "Env:$key");value=[Environment]::GetEnvironmentVariable($key,'Process')}
 };return $values
}
function Inventory {
 $paths=@(git -C $repo ls-files 'forge-*/src/main/java/*.java' 'forge-gui-desktop/src/test/java/forge/net/*.java' 'forge-gui-desktop/src/test/java/forge/net/embedded/*.java' 'experiments/battlefield-static/*')
 if($LASTEXITCODE -ne 0){throw 'Source inventory failed'}
 $paths+=@('pom.xml','forge-gui-desktop/pom.xml','forge-gui/res/cardsfolder/l/lightning_bolt.txt','forge-gui/res/cardsfolder/m/mountain.txt',
  'forge-gui-desktop/src/test/java/forge/net/embedded/ManualBrowserBoltHost.java','forge-gui-desktop/src/test/java/forge/net/embedded/ManualBrowserBoltHostTest.java',
  'experiments/browser-bolt/Run-Manual.ps1','experiments/battlefield-static/live.html','experiments/battlefield-static/live-client.js')
 $source=[ordered]@{};foreach($path in @($paths|Sort-Object -Unique)){$source[$path]=(Get-FileHash -LiteralPath (Join-Path $repo $path)).Hash.ToLowerInvariant()}
 $cache=[ordered]@{};foreach($file in Get-ChildItem "$repo/.local-tools/m2" -Recurse -File){$cache[[IO.Path]::GetRelativePath($repo,$file.FullName).Replace('\','/')]=(Get-FileHash -LiteralPath $file.FullName).Hash.ToLowerInvariant()}
 return [ordered]@{source=$source;cache=$cache}
}
function Changes($A,$B){return @(Compare-Object @($A.Keys|ForEach-Object{"$_ $($A[$_])"}) @($B.Keys|ForEach-Object{"$_ $($B[$_])"}))}
$parentBefore=Parent-Environment;$before=Inventory
$before|ConvertTo-Json -Depth 5|Set-Content "$run/integrity-before.json" -Encoding utf8
$selectors=@{
 policy='forge.net.embedded.ManualBrowserBoltHostTest'
 dto='forge.net.embedded.ProtocolGuiGameInProcessTest#humanBoltThroughBrowserDtos'
 unanswered='forge.net.embedded.ProtocolGuiGameInProcessTest#browserDtoUnansweredPromptIsReported'
 regressions='forge.net.embedded.ProtocolGuiGameInProcessTest#humanBoltTargetsOpponentWithManualMountainPayment+aiBoltRunsThroughOriginalPhaseLoop+aiEmptyHandDoesNotCastOrPay+unansweredRequiredPromptIsReportedToTestOwner+humanControllerRunsThroughInProcessRemote,forge.net.embedded.ProtocolGuiGameSingleThreadTest'
}
$selector=if($VerifyOnly){$selectors[$Check]}else{'forge.net.embedded.ProtocolGuiGameInProcessTest#manualBrowserBoltSession'}
$arguments=@('-o','-B','-ntp','-T1',"-Dmaven.repo.local=$repo/.local-tools/m2",'-pl','forge-gui-desktop','-am',"-Dtest=$selector",'-Dsurefire.failIfNoSpecifiedTests=false','-DfailIfNoTests=false',
 ('-Dforge.browser.listenerApproved='+([bool]$ApproveListener).ToString().ToLowerInvariant()))
if($ApproveListener){$arguments+=@("-Dforge.browser.repo=$repo","-Dforge.browser.output=$run","-Dforge.browser.bind=$BindAddress")}
$arguments+='test'
[ordered]@{maven=$maven;arguments=$arguments;console="$run/console.log"}|ConvertTo-Json -Depth 5|Set-Content "$run/maven-input.json" -Encoding utf8
# The owned child invokes the existing .cmd via PowerShell's native argument handling,
# avoiding string-built cmd.exe commands. This generated helper contains no capability.
@'
param([string]$SpecFile)
$ErrorActionPreference='Stop'
$spec=Get-Content -LiteralPath $SpecFile -Raw|ConvertFrom-Json
$arguments=@($spec.arguments|ForEach-Object{[string]$_})
$writer=[IO.StreamWriter]::new($spec.console,$false,[Text.UTF8Encoding]::new($false))
try{& $spec.maven @arguments 2>&1|ForEach-Object{$writer.WriteLine($_.ToString());$writer.Flush();Write-Output $_}}
finally{$writer.Dispose()}
exit $LASTEXITCODE
'@|Set-Content "$run/invoke-maven.ps1" -Encoding utf8
$info=[Diagnostics.ProcessStartInfo]::new()
$info.FileName=(Get-Process -Id $PID).Path;$info.WorkingDirectory=$repo;$info.UseShellExecute=$false;$info.CreateNoWindow=$true
$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
foreach($arg in @('-NoLogo','-NoProfile','-NonInteractive','-File',"$run/invoke-maven.ps1",'-SpecFile',"$run/maven-input.json")){$info.ArgumentList.Add($arg)}
# Isolation is supplied only to the owned child; these six parent variables are never changed.
$forward=$run.Replace('\','/')
$info.Environment['JAVA_HOME']=$jdk
$info.Environment['JAVA_TOOL_OPTIONS']="-Duser.home=$forward/home -Djava.io.tmpdir=$forward/tmp -Djava.awt.headless=true -Xmx1536m"
$info.Environment['APPDATA']="$run/appdata";$info.Environment['LOCALAPPDATA']="$run/localappdata";$info.Environment['TEMP']="$run/tmp";$info.Environment['TMP']="$run/tmp"
$p=[Diagnostics.Process]::new();$p.StartInfo=$info;$started=$false;$start=[DateTime]::UtcNow;$primary=$null;$secondary=[Collections.Generic.List[string]]::new()
$exitCode=$null;$timedOut=$false;$forced=$false;$rootExited=$false;$rootId=$null;$stdout=$null;$stderr=$null
$observed=[pscustomobject]@{exited=$false;exitCode=$null;integrityChecked=$false;sourceChanges=@();cacheChanges=@();environmentSafe=$false;environmentChecks=@()}
function Final-Step([string]$Stage,[scriptblock]$Action){try{[void](& $Action)}catch{$secondary.Add($Stage+':'+$_.Exception.GetType().FullName)}}
try{
 if(-not $p.Start()){throw 'Owned Maven driver did not start'};$started=$true;[void]$p.Handle;$rootId=$p.Id
 $stdout=$p.StandardOutput.ReadToEndAsync();$stderr=$p.StandardError.ReadToEndAsync()
 Write-Host "Mode: $(if($VerifyOnly){'listener-free verification'}else{'separately approved listener'}). Private progress: .local-tools/browser-bolt/$Label/console.log"
 if($ApproveListener){Write-Host "Open http://${BindAddress}:$Port/experiments/battlefield-static/live.html ; paste capability.txt privately into its password field. Never share or publish that file."}
 $runBudgetMinutes=if($ApproveListener){40}else{20}
 $clock=[Diagnostics.Stopwatch]::StartNew()
 while(-not $p.WaitForExit(1000)){if($clock.Elapsed.TotalMinutes -ge $runBudgetMinutes){$timedOut=$true;throw 'Owned session elapsed-time budget exceeded'}}
 $exitCode=$p.ExitCode
 if($exitCode -ne 0){throw 'Offline Maven/test failure; inspect isolated logs'}
}catch{$primary=$_}
finally{
 if($started){
  Final-Step 'process-exit' {$observed.exited=$p.HasExited}
  if(-not $observed.exited){$forced=$true;Final-Step 'kill-owned-tree' {$p.Kill($true)}}
  Final-Step 'observe-owned-exit' {if(-not $p.WaitForExit(10000)){throw 'Owned driver did not exit within observation window'}}
  Final-Step 'record-exit' {$observed.exited=$p.HasExited;if($observed.exited){$observed.exitCode=$p.ExitCode}}
 }
 foreach($stream in @('stdout','stderr')){
  Final-Step ('preserve-'+$stream) {
   $reader=if($stream -eq 'stdout'){$stdout}else{$stderr}
   if($null -ne $reader){if(-not $reader.Wait(5000)){throw 'Owned output did not finish'};$reader.GetAwaiter().GetResult()|Set-Content "$run/driver.$stream.log" -Encoding utf8}
  }
 }
 Final-Step 'dispose-driver' {$p.Dispose()}
 Final-Step 'copy-fresh-reports' {Get-ChildItem "$repo/forge-gui-desktop/target/surefire-reports" -File|Where-Object{$_.LastWriteTimeUtc -ge $start}|Copy-Item -Destination "$run/reports"}
 Final-Step 'final-integrity' {
  $after=Inventory;$after|ConvertTo-Json -Depth 5|Set-Content "$run/integrity-after.json" -Encoding utf8
  $observed.sourceChanges=@(Changes $before.source $after.source);$observed.cacheChanges=@(Changes $before.cache $after.cache);$observed.integrityChecked=$true
 }
 Final-Step 'parent-environment-check' {
  $parentAfter=Parent-Environment
  $observed.environmentChecks=@(foreach($key in $parentBefore.Keys){[ordered]@{name=$key;exactMatch=($parentBefore[$key].exists -eq $parentAfter[$key].exists -and [string]::Equals($parentBefore[$key].value,$parentAfter[$key].value,[StringComparison]::Ordinal))}})
  $observed.environmentSafe=@($observed.environmentChecks|Where-Object{-not $_.exactMatch}).Count -eq 0
 }
 if($ApproveListener){Final-Step 'port-after-session' {if(@([Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()|Where-Object Port -eq $Port).Count){throw 'Port remains occupied; no unrelated listener will be stopped'}}}
 $result=[ordered]@{label=$Label;verifyOnly=[bool]$VerifyOnly;check=$(if($VerifyOnly){$Check}else{'manual'});listenerApproved=[bool]$ApproveListener;nativeExit=$exitCode;startedUtc=$start.ToString('o');endedUtc=[DateTime]::UtcNow.ToString('o')
  primaryFailureType=$(if($null -ne $primary){$primary.Exception.GetType().FullName}else{$null});secondaryFailures=@($secondary.ToArray());environmentUnchanged=$observed.environmentSafe;environmentChecks=$observed.environmentChecks
  protectedFileCount=$before.source.Count;cacheFileCount=$before.cache.Count;integrityChecked=$observed.integrityChecked;sourceChanges=$observed.sourceChanges;cacheChanges=$observed.cacheChanges
  driverPid=$rootId;driverExited=$observed.exited;observedDriverExit=$observed.exitCode;timedOut=$timedOut;forceTerminationRequested=$forced;descendantExitIndependentlyObserved=$false;unconditionalWallClockGuarantee=$false}
 Final-Step 'write-result' {$result|ConvertTo-Json -Depth 7|Set-Content "$run/result.json" -Encoding utf8}
}
if($null -ne $primary){throw $primary}
if($secondary.Count -or -not $observed.environmentSafe -or -not $observed.integrityChecked -or $observed.sourceChanges.Count -or $observed.cacheChanges.Count){throw 'Finalization/integrity verification failed'}
Write-Output "Completed: .local-tools/browser-bolt/$Label/result.json"
