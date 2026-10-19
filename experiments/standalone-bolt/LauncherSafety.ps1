# D3c-specific helpers. Definitions only: no process creation, CIM or environment changes.
function New-LauncherFailureLedger {
 [pscustomobject]@{Primary=$null;PrimaryStage=$null;PrimaryDiagnostic=$null;Secondary=[Collections.Generic.List[object]]::new()}
}
function Save-LauncherFailure($Ledger,$Failure,[string]$Stage,[switch]$Secondary){
 $first=$null -eq $Ledger.Primary
 if($first){$Ledger.Primary=$Failure;$Ledger.PrimaryStage=$Stage}
 # Keep the original ErrorRecord in memory for rethrow; never serialize its message/arguments/stack.
 $type=$Failure.Exception.GetType().FullName
 $diagnostic=[ordered]@{stage=$Stage;exceptionType=$type.Substring(0,[Math]::Min(160,$type.Length));hresult=$Failure.Exception.HResult}
 if($first){$Ledger.PrimaryDiagnostic=$diagnostic}
 if($Secondary -or (-not $first -and -not [object]::ReferenceEquals($Ledger.Primary.Exception,$Failure.Exception))){
  $Ledger.Secondary.Add($diagnostic)
 }
}
function Get-LauncherFailureEvidence($Ledger){
 [ordered]@{primaryFailure=$Ledger.PrimaryDiagnostic;secondaryFailures=@($Ledger.Secondary.ToArray())}
}
function Invoke-LauncherFinalStep($Ledger,[string]$Stage,[scriptblock]$Action){
 try{[void](& $Action)}catch{Save-LauncherFailure $Ledger $_ $Stage -Secondary}
}
function Get-StrictJavaMainIdentity([string]$CommandLine,[string]$JavaHome,[string]$DriverResult){
 $answer=[ordered]@{unambiguous=$false;mainClassMatches=$false;reason='empty-or-oversize'}
 if([string]::IsNullOrWhiteSpace($CommandLine) -or $CommandLine.Length -gt 32767){return $answer}
 # Deliberately restricted Windows quoting, sufficient for the pinned JDI launch shape.
 # Backslash-escaped quotes, concatenated quote segments, control characters and unknown options refuse.
 $answer.reason='ambiguous-quoting'
 if($CommandLine -match '[\x00-\x08\x0a-\x1f]'){return $answer}
 $arguments=[Collections.Generic.List[string]]::new();$at=0
 while($at -lt $CommandLine.Length){
  while($at -lt $CommandLine.Length -and $CommandLine[$at] -in @(' ',"`t")){$at++}
  if($at -eq $CommandLine.Length){break}
  if($CommandLine[$at] -eq '"'){
   $end=$CommandLine.IndexOf('"',$at+1);if($end -lt 0){return $answer}
   $token=$CommandLine.Substring($at+1,$end-$at-1)
   if($token.EndsWith('\')){return $answer}
   $at=$end+1
   if($at -lt $CommandLine.Length -and $CommandLine[$at] -notin @(' ',"`t")){return $answer}
  }else{
   $begin=$at
   while($at -lt $CommandLine.Length -and $CommandLine[$at] -notin @(' ',"`t")){$at++}
   $token=$CommandLine.Substring($begin,$at-$begin)
   if($token.Contains('"')){return $answer}
  }
  $arguments.Add($token)
 }
 $answer.reason='executable-argument'
 if($arguments.Count -lt 2){return $answer}
 $program=$arguments[0].Replace('/','\');$base=$JavaHome.Replace('/','\')+'\bin\java'
 if(-not [string]::Equals($program,$base,[StringComparison]::OrdinalIgnoreCase) -and
    -not [string]::Equals($program,$base+'.exe',[StringComparison]::OrdinalIgnoreCase)){return $answer}
 $at=1;$classpaths=0
 while($at -lt $arguments.Count){
  $token=$arguments[$at]
  if($token -ceq '-cp' -or $token -ceq '--add-opens'){
   $answer.reason='missing-option-value'
   if($at+1 -ge $arguments.Count -or -not $arguments[$at+1] -or $arguments[$at+1].StartsWith('-')){return $answer}
   if($token -ceq '-cp'){$classpaths++;if($classpaths -gt 1){$answer.reason='duplicate-classpath';return $answer}}
   $at+=2;continue
  }
  if($token -ceq '-Xdebug' -or $token.StartsWith('-D',[StringComparison]::Ordinal) -or
     $token.StartsWith('--add-opens=',[StringComparison]::Ordinal)){$at++;continue}
  if($token.StartsWith('-Xrunjdwp:',[StringComparison]::Ordinal) -or $token.StartsWith('-agentlib:jdwp=',[StringComparison]::Ordinal)){
   $answer.reason='unexpected-debug-transport'
   if($token -cnotmatch '[:=,]transport=dt_shmem(?:,|$)'){return $answer}
   $at++;continue
  }
  if($token.StartsWith('-') -or $token.StartsWith('@')){$answer.reason='unknown-option';return $answer}
  break
 }
 $answer.unambiguous=$true;$answer.reason='main-class'
 if($classpaths -ne 1 -or $at -ge $arguments.Count -or $arguments[$at] -cne 'experiment.BoltLifecycleMain'){return $answer}
 $answer.reason='application-arguments'
 if($at+2 -ne $arguments.Count -or -not [string]::Equals($arguments[$at+1],$DriverResult,[StringComparison]::OrdinalIgnoreCase)){return $answer}
 $answer.mainClassMatches=$true;$answer.reason='accepted';return $answer
}
function Finish-OwnedLauncherProcess($Entry,$Ledger,$Rows,[string]$OutputDirectory){
 $row=[ordered]@{role=$Entry.Role;pid=$Entry.Id;parentPid=$Entry.ParentId;startedUtc=$Entry.StartedUtc;timeout=$Entry.Timeout
  terminationAttempted=$false;forceTerminatedByLauncher=$false;waitCompleted=$null;hasExited=$null;exitCode=$null;disposed=$false;logs=[ordered]@{}}
 Invoke-LauncherFinalStep $Ledger 'cleanup-inspect' {$row.hasExited=$Entry.Process.HasExited}
 if($row.hasExited -ne $true){
  $row.terminationAttempted=$true
  Invoke-LauncherFinalStep $Ledger 'cleanup-kill' {$Entry.Process.Kill();$row.forceTerminatedByLauncher=$true}
 }
 Invoke-LauncherFinalStep $Ledger 'cleanup-wait' {
  $row.waitCompleted=$Entry.Process.WaitForExit(10000)
  if(-not $row.waitCompleted){throw 'Owned process exit was not observed within cleanup wait'}
 }
 Invoke-LauncherFinalStep $Ledger 'cleanup-exit-observation' {$row.hasExited=$Entry.Process.HasExited}
 if($row.hasExited -eq $true){Invoke-LauncherFinalStep $Ledger 'cleanup-exit-code' {$row.exitCode=$Entry.Process.ExitCode}}
 if($null -ne $Entry.OutputTasks){
  foreach($stream in @('stdout','stderr')){
   $row.logs[$stream]=$false
   Invoke-LauncherFinalStep $Ledger ('cleanup-'+$stream) {
    $reader=$Entry.OutputTasks[$stream]
    if($null -eq $reader){throw 'Owned output reader unavailable'}
    if(-not $reader.Wait(5000)){throw 'Owned output reader deadline'}
    $content=$reader.GetAwaiter().GetResult()
    [IO.File]::WriteAllText((Join-Path $OutputDirectory ($Entry.Role+'.'+$stream+'.log')),$content,[Text.UTF8Encoding]::new($false))
    $row.logs[$stream]=$true
   }
  }
 }
 Invoke-LauncherFinalStep $Ledger 'cleanup-record' {$Rows.Add($row)}
 Invoke-LauncherFinalStep $Ledger 'cleanup-dispose' {$Entry.Process.Dispose();$row.disposed=$true}
}
