param(
 [Parameter(Mandatory)][ValidateSet('capture','dto','unanswered','regressions')][string]$Case,
 [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]+$')][string]$Label,
 [switch]$Maven
)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path "$PSScriptRoot/../..").Path
$run=Join-Path $repo ".local-tools/stage-c-dto-20261018/$Label"
if(Test-Path $run){throw 'Use a new isolated run label; never overwrite evidence.'}
foreach($name in @('home','tmp','appdata','localappdata','reports')){New-Item -ItemType Directory -Force -Path (Join-Path $run $name)|Out-Null}
function Read-Env([string]$Name){
 $exists=Test-Path -LiteralPath "Env:$Name"
 return @{exists=$exists;value=$(if($exists){(Get-Item -LiteralPath "Env:$Name").Value}else{$null})}
}
function Restore-Env([string]$Name,$State){
 if($State.exists){Set-Item -LiteralPath "Env:$Name" -Value $State.value}
 else{Remove-Item -LiteralPath "Env:$Name" -ErrorAction SilentlyContinue}
}
function Equal-Env($A,$B){return $A.exists -eq $B.exists -and [string]::Equals($A.value,$B.value,[StringComparison]::Ordinal)}
# Prove restoration for both absent and present values before any JVM/test launch.
$probe='FORGE_STAGE_C_RESTORE_PROBE'
$probeOriginal=Read-Env $probe
try{
 Remove-Item -LiteralPath "Env:$probe" -ErrorAction SilentlyContinue
 $absent=Read-Env $probe
 Set-Item -LiteralPath "Env:$probe" -Value 'fixture-only'
 $present=Read-Env $probe
 Restore-Env $probe $absent
 if(-not(Equal-Env (Read-Env $probe) $absent)){throw 'Missing-variable restoration preflight failed'}
 Restore-Env $probe $present
 if(-not(Equal-Env (Read-Env $probe) $present)){throw 'Present-variable restoration preflight failed'}
}finally{Restore-Env $probe $probeOriginal}
if(-not(Equal-Env (Read-Env $probe) $probeOriginal)){throw 'Probe original state not restored'}
$keys=@('JAVA_HOME','JAVA_TOOL_OPTIONS','APPDATA','LOCALAPPDATA','TEMP','TMP')
$saved=@{};foreach($key in $keys){$saved[$key]=Read-Env $key}
$methods=@{capture='humanBoltCaptureCheckpoint';dto='humanBoltThroughBrowserDtos';unanswered='browserDtoUnansweredPromptIsReported';regressions='humanBoltTargetsOpponentWithManualMountainPayment+aiBoltRunsThroughOriginalPhaseLoop+aiEmptyHandDoesNotCastOrPay+unansweredRequiredPromptIsReportedToTestOwner+humanControllerRunsThroughInProcessRemote'}
$exitCode=$null;$started=[DateTime]::UtcNow
$testSourceHash=(Get-FileHash -LiteralPath "$repo/forge-gui-desktop/src/test/java/forge/net/embedded/ProtocolGuiGameInProcessTest.java").Hash.ToLowerInvariant()
try{
 $env:JAVA_HOME='C:/Program Files/Java/jdk-17'
 $forward=$run.Replace('\','/')
 $env:JAVA_TOOL_OPTIONS="-Duser.home=$forward/home -Djava.io.tmpdir=$forward/tmp -Djava.awt.headless=true -Xmx1536m"
 $env:APPDATA="$run/appdata";$env:LOCALAPPDATA="$run/localappdata";$env:TEMP="$run/tmp";$env:TMP="$run/tmp"
 $writer=[IO.StreamWriter]::new("$run/console.log",$false,[Text.UTF8Encoding]::new($false))
 Push-Location $repo
 try{
  if($Maven){
   $selector='forge.net.embedded.ProtocolGuiGameInProcessTest#'+$methods[$Case]
   if($Case -eq 'regressions'){$selector+=',forge.net.embedded.ProtocolGuiGameSingleThreadTest'}
   & "$repo/.local-tools/apache-maven-3.9.16/bin/mvn.cmd" -o -B -ntp -T1 "-Dmaven.repo.local=$repo/.local-tools/m2" -pl forge-gui-desktop -am "-Dtest=$selector" '-Dsurefire.failIfNoSpecifiedTests=false' '-DfailIfNoTests=false' test 2>&1|ForEach-Object{$writer.WriteLine($_.ToString());$writer.Flush()}
   $exitCode=$LASTEXITCODE
   # A compiler failure can leave old Surefire reports in place. Never relabel them as this run.
   Get-ChildItem "$repo/forge-gui-desktop/target/surefire-reports" -File|Where-Object{$_.LastWriteTimeUtc -ge $started}|Copy-Item -Destination "$run/reports"
  }else{
   if($Case -eq 'regressions'){throw 'Use Maven for the explicit regression selector'}
   [xml]$report=Get-Content "$repo/forge-gui-desktop/target/surefire-reports/TEST-forge.net.embedded.ProtocolGuiGameInProcessTest.xml"
   $cp=($report.testsuite.properties.property|Where-Object name -eq 'surefire.test.class.path').value
   if(-not $cp){throw 'Missing compiled classpath; stop offline'}
   foreach($entry in $cp.Split(';')|Where-Object{$_}){if(-not(Test-Path -LiteralPath $entry)){throw 'Missing offline dependency'}}
   [xml]$pom=Get-Content "$repo/pom.xml"
   $opens=$pom.SelectSingleNode('//*[local-name()="argLine"]').InnerText.Trim() -split '\s+'
   Set-Location "$repo/forge-gui-desktop"
   & "$env:JAVA_HOME/bin/java.exe" @opens -cp $cp org.testng.TestNG -methods ("forge.net.embedded.ProtocolGuiGameInProcessTest."+$methods[$Case]) -d "$run/reports" 2>&1|ForEach-Object{$writer.WriteLine($_.ToString());$writer.Flush()}
   $exitCode=$LASTEXITCODE
  }
 }finally{Pop-Location;$writer.Dispose()}
}finally{
 foreach($key in $keys){Restore-Env $key $saved[$key]}
 $checks=@(foreach($key in $keys){$actual=Read-Env $key;[ordered]@{name=$key;beforeExists=$saved[$key].exists;afterExists=$actual.exists;exactMatch=(Equal-Env $saved[$key] $actual)}})
 $restored=@($checks|Where-Object{-not $_.exactMatch}).Count -eq 0
 $result=[ordered]@{case=$Case;label=$Label;maven=[bool]$Maven;nativeExit=$exitCode;startedUtc=$started.ToString('o');endedUtc=[DateTime]::UtcNow.ToString('o');testSourceSha256=$testSourceHash;sourceUnchangedDuringRun=((Get-FileHash -LiteralPath "$repo/forge-gui-desktop/src/test/java/forge/net/embedded/ProtocolGuiGameInProcessTest.java").Hash.ToLowerInvariant() -ceq $testSourceHash);restorationPreflightPassed=$true;environmentRestored=$restored;environmentChecks=$checks}
 $result|ConvertTo-Json -Depth 6|Set-Content "$run/result.json" -Encoding utf8
 $result|ConvertTo-Json -Depth 6
 if(-not $restored){throw 'Exact environment restoration failed; stop further runs'}
}
exit $exitCode
