# Non-launching helper checks only. Never dot-source or execute Run-Lifecycle.ps1.
$ErrorActionPreference='Stop'
. "$PSScriptRoot/LauncherSafety.ps1"
$results=[Collections.Generic.List[object]]::new()
function Check([string]$Name,[bool]$Condition){
 $results.Add([ordered]@{name=$Name;passed=$Condition})
 if(-not $Condition){throw "Static helper check failed: $Name"}
}
foreach($file in @('Run-Lifecycle.ps1','LauncherSafety.ps1','Test-LauncherSafetyStatic.ps1')){
 $tokens=$null;$issues=$null
 [void][Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot $file),[ref]$tokens,[ref]$issues)
 Check ('parse-'+$file) ($issues.Count -eq 0)
}
$launcher=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'Run-Lifecycle.ps1'))
$gateAt=$launcher.IndexOf("throw 'D3c safety gate HOLD");$loadAt=$launcher.IndexOf('. "$PSScriptRoot/LauncherSafety.ps1"')
Check 'unconditional-safety-gate-before-helper-load' ($gateAt -ge 0 -and $loadAt -gt $gateAt)
$javaHome='C:\Test JDK';$destination='C:\Test Output\driver-result.json'
$prefix='"C:\Test JDK\bin\java.exe" --add-opens java.base/java.lang=ALL-UNNAMED -cp "C:\synthetic classes" -Xdebug -Xrunjdwp:transport=dt_shmem,address=synthetic,suspend=y '
$cases=@(
 @{name='literal-main';tail='experiment.BoltLifecycleMain "C:\Test Output\driver-result.json"';accept=$true},
 @{name='wrong-main-with-decoy-argument';tail='OtherMain experiment.BoltLifecycleMain';accept=$false},
 @{name='class-suffix';tail='experiment.BoltLifecycleMainExtra "C:\Test Output\driver-result.json"';accept=$false},
 @{name='wrong-class-case';tail='experiment.boltlifecyclemain "C:\Test Output\driver-result.json"';accept=$false},
 @{name='quoted-main';tail='"experiment.BoltLifecycleMain" "C:\Test Output\driver-result.json"';accept=$true},
 @{name='argument-file';tail='@synthetic.args';accept=$false},
 @{name='jar-entry';tail='-jar experiment.BoltLifecycleMain.jar';accept=$false},
 @{name='unclosed-quote';tail='"experiment.BoltLifecycleMain';accept=$false},
 @{name='quote-concatenation';tail='experiment."BoltLifecycleMain" "C:\Test Output\driver-result.json"';accept=$false},
 @{name='extra-application-argument';tail='experiment.BoltLifecycleMain "C:\Test Output\driver-result.json" extra';accept=$false}
)
foreach($case in $cases){$verdict=Get-StrictJavaMainIdentity ($prefix+$case.tail) $javaHome $destination;Check $case.name ($verdict.mainClassMatches -eq $case.accept)}
$decoy='"C:\Test JDK\bin\java.exe" -Dlabel=experiment.BoltLifecycleMain -cp "experiment.BoltLifecycleMain" OtherMain "C:\Test Output\driver-result.json"'
Check 'option-and-classpath-decoys' (-not (Get-StrictJavaMainIdentity $decoy $javaHome $destination).mainClassMatches)
Check 'missing-command-line' (-not (Get-StrictJavaMainIdentity '' $javaHome $destination).mainClassMatches)
Check 'network-debug-transport-rejected' (-not (Get-StrictJavaMainIdentity (($prefix -replace 'dt_shmem','dt_socket')+$cases[0].tail) $javaHome $destination).mainClassMatches)

# These are ordinary in-memory objects with ScriptMethods, never System.Diagnostics.Process.
function New-FakeOwnedProcess([switch]$Broken){
 $fake=[pscustomobject]@{HasExited=$false;ExitCode=0;Broken=[bool]$Broken;Calls=[Collections.Generic.List[string]]::new()}
 $fake|Add-Member ScriptMethod Kill {$this.Calls.Add('kill');if($this.Broken){throw 'synthetic kill failure'};$this.HasExited=$true}
 $fake|Add-Member ScriptMethod WaitForExit {param($milliseconds)$this.Calls.Add('wait');return (-not $this.Broken)}
 $fake|Add-Member ScriptMethod Dispose {$this.Calls.Add('dispose');if($this.Broken){throw 'synthetic disposal failure'}}
 return $fake
}
$ledger=New-LauncherFailureLedger;$primary=$null
try{throw [InvalidOperationException]::new('PRIVATE_SENTINEL never publish this message')}catch{$primary=$_;Save-LauncherFailure $ledger $_ 'synthetic-primary'}
$rows=[Collections.Generic.List[object]]::new();$bad=New-FakeOwnedProcess -Broken;$good=New-FakeOwnedProcess
foreach($spec in @(@{process=$bad;role='JDI-target';id=1},@{process=$good;role='observer';id=2})){
 $entry=[pscustomobject]@{Process=$spec.process;Role=$spec.role;Id=$spec.id;ParentId=0;StartedUtc='synthetic';Timeout=$false;OutputTasks=$null}
 Invoke-LauncherFinalStep $ledger 'synthetic-process-cleanup' {Finish-OwnedLauncherProcess $entry $ledger $rows ''}
}
Check 'later-observer-cleanup-after-child-failures' (($good.Calls -join ',') -eq 'kill,wait,dispose')
Check 'failed-child-all-cleanup-steps-attempted' (($bad.Calls -join ',') -eq 'kill,wait,dispose')
Check 'both-process-records-retained' ($rows.Count -eq 2)
Check 'false-wait-recorded' ($rows[0].waitCompleted -eq $false)
Check 'kill-wait-dispose-secondary-errors' ($ledger.Secondary.Count -eq 3)
$progress=[pscustomobject]@{restored=0;evidenceAttempted=$false}
Invoke-LauncherFinalStep $ledger 'synthetic-restore-first' {throw 'synthetic restoration failure'}
Invoke-LauncherFinalStep $ledger 'synthetic-restore-second' {$progress.restored++}
Invoke-LauncherFinalStep $ledger 'synthetic-result-write' {throw 'synthetic write failure'}
Invoke-LauncherFinalStep $ledger 'synthetic-evidence-fallback' {$progress.evidenceAttempted=$true}
Check 'restoration-and-evidence-continue' ($progress.restored -eq 1 -and $progress.evidenceAttempted)
Check 'original-error-record-identity' ([object]::ReferenceEquals($primary,$ledger.Primary))
$owner=$null
try{throw $ledger.Primary}catch{$owner=$_}
Check 'original-exception-reaches-owner' ([object]::ReferenceEquals($primary.Exception,$owner.Exception))
$safe=Get-LauncherFailureEvidence $ledger|ConvertTo-Json -Depth 6
Check 'privacy-no-primary-message' (-not $safe.Contains('PRIVATE_SENTINEL'))
Check 'primary-and-secondary-stages-retained' ($ledger.PrimaryDiagnostic.stage -eq 'synthetic-primary' -and $ledger.Secondary.Count -eq 5)
$onlyCleanup=New-LauncherFailureLedger
Invoke-LauncherFinalStep $onlyCleanup 'synthetic-first-finally-failure' {throw 'synthetic cleanup-only failure'}
Check 'first-finally-failure-becomes-primary' ($null -ne $onlyCleanup.Primary -and $onlyCleanup.PrimaryStage -eq 'synthetic-first-finally-failure')
$recordLedger=New-LauncherFailureLedger;$recordProcess=New-FakeOwnedProcess
$badRows=[pscustomobject]@{}
$badRows|Add-Member ScriptMethod Add {param($item)throw 'synthetic process recording failure'}
$entry=[pscustomobject]@{Process=$recordProcess;Role='observer';Id=3;ParentId=0;StartedUtc='synthetic';Timeout=$false;OutputTasks=$null}
Finish-OwnedLauncherProcess $entry $recordLedger $badRows ''
Check 'disposal-after-recording-failure' (($recordProcess.Calls -join ',') -eq 'kill,wait,dispose')
Check 'recording-failure-retained' ($recordLedger.PrimaryStage -eq 'cleanup-record')
[ordered]@{kind='non-launching synthetic helper checks';javaLaunched=$false;launcherBodyExecuted=$false;processMethods='in-memory fake ScriptMethods only';checks=@($results.ToArray())}|ConvertTo-Json -Depth 6
