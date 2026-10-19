param([Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]+$')][string]$Label)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path "$PSScriptRoot/../..").Path
$run=Join-Path $repo ".local-tools/standalone-bolt/$Label"
if(Test-Path -LiteralPath $run){throw 'Use a fresh label; evidence is never overwritten.'}
$jdk='C:/Program Files/Java/jdk-17'
foreach($tool in @('javac.exe','java.exe')){if(-not(Test-Path "$jdk/bin/$tool")){throw 'Existing JDK 17 missing; stop offline.'}}
[xml]$xml=Get-Content "$repo/forge-gui-desktop/target/surefire-reports/TEST-forge.net.embedded.ProtocolGuiGameInProcessTest.xml"
$cachedCp=($xml.testsuite.properties.property|Where-Object name -eq 'surefire.test.class.path').value
if(-not $cachedCp){throw 'Existing offline reactor classpath missing; no build/download fallback.'}
$entries=@($cachedCp.Split(';')|Where-Object{$_ -and $_ -notmatch '[/\\]org[/\\]testng[/\\]'})
foreach($entry in $entries){
 if(-not(Test-Path -LiteralPath $entry)){throw 'Offline classpath entry missing.'}
 if(-not $entry.StartsWith($repo+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Classpath outside project; review required.'}
}
$protected=@('pom.xml','forge-gui-desktop/pom.xml','forge-gui/res/cardsfolder/l/lightning_bolt.txt','forge-gui/res/cardsfolder/m/mountain.txt')
Push-Location $repo
try{
 $protected+=@(git ls-files 'forge-*/src/main/java/*.java' 'forge-gui-desktop/src/test/java/forge/net/*.java' 'forge-gui-desktop/src/test/java/forge/net/embedded/*.java' 'experiments/ai-teavm/src/observer/java/probe/JdiWitness.java')
 if($LASTEXITCODE -ne 0){throw 'Source inventory failed'}
}finally{Pop-Location}
function Hash-Paths($Paths){
 $map=[ordered]@{}
 foreach($path in @($Paths|Sort-Object -Unique)){$map[$path]=(Get-FileHash -LiteralPath (Join-Path $repo $path) -Algorithm SHA256).Hash.ToLowerInvariant()}
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
$probe='FORGE_D3A_RESTORE_PROBE';$originalProbe=Read-Env $probe
try{
 Remove-Item -LiteralPath "Env:$probe" -ErrorAction SilentlyContinue
 $absent=Read-Env $probe
 Set-Item -LiteralPath "Env:$probe" -Value 'probe-only';$present=Read-Env $probe
 Restore-Env $probe $absent
 if(-not(Equal-Env (Read-Env $probe) $absent)){throw 'Absent restoration preflight failed'}
 Restore-Env $probe $present
 if(-not(Equal-Env (Read-Env $probe) $present)){throw 'Present restoration preflight failed'}
}finally{Restore-Env $probe $originalProbe}
if(-not(Equal-Env (Read-Env $probe) $originalProbe)){throw 'Restoration probe failed'}
foreach($dir in @('home','tmp','appdata','localappdata','classes','empty-sourcepath')){New-Item -ItemType Directory -Path "$run/$dir" -Force|Out-Null}
$before=Snapshot
$before|ConvertTo-Json -Depth 6|Set-Content "$run/integrity-before.json" -Encoding utf8
$keys=@('JAVA_HOME','JAVA_TOOL_OPTIONS','APPDATA','LOCALAPPDATA','TEMP','TMP')
$saved=@{};foreach($key in $keys){$saved[$key]=Read-Env $key}
$processes=[Collections.Generic.List[object]]::new()
$start=[DateTime]::UtcNow;$ok=$false;$primary=$null;$integrity=$false
function Run-Owned([string]$Tool,[string[]]$Arguments,[string]$Name,[int]$Seconds){
 $info=[Diagnostics.ProcessStartInfo]::new()
 $info.FileName="$jdk/bin/$Tool";$info.WorkingDirectory="$repo/forge-gui-desktop"
 $info.UseShellExecute=$false;$info.CreateNoWindow=$true
 $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
 foreach($argument in $Arguments){$info.ArgumentList.Add($argument)}
 $p=[Diagnostics.Process]::new();$p.StartInfo=$info
 $launched=[DateTime]::UtcNow
 if(-not $p.Start()){throw 'Owned process did not start'}
 $stdout=$p.StandardOutput.ReadToEndAsync();$stderr=$p.StandardError.ReadToEndAsync()
 $timedOut=$false
 try{
  if(-not $p.WaitForExit($Seconds*1000)){$timedOut=$true;$p.Kill();$p.WaitForExit(10000)|Out-Null}
  if(-not $p.HasExited){throw 'Owned process did not exit; stop without retry'}
  $stdout.GetAwaiter().GetResult()|Set-Content "$run/$Name.stdout.log" -Encoding utf8
  $stderr.GetAwaiter().GetResult()|Set-Content "$run/$Name.stderr.log" -Encoding utf8
  $row=[ordered]@{role=$Name;pid=$p.Id;parentPid=$PID;startedUtc=$launched.ToString('o');exitUtc=$p.ExitTime.ToUniversalTime().ToString('o');exitCode=$p.ExitCode;supervisorTimeout=$timedOut;hasExited=$p.HasExited;elapsedMs=([DateTime]::UtcNow-$launched).TotalMilliseconds}
  $processes.Add($row)
  if($timedOut -or $p.ExitCode -ne 0){throw "Owned $Name failed/expired; no automatic retry. See private run logs."}
 }finally{
  # Only the Process handle created above; never a name/PID search or match mutation.
  if(-not $p.HasExited){$p.Kill();$p.WaitForExit(10000)|Out-Null}
  $p.Dispose()
 }
}
try{
 $env:JAVA_HOME=$jdk;$forward=$run.Replace('\','/')
 $env:JAVA_TOOL_OPTIONS="-Duser.home=$forward/home -Djava.io.tmpdir=$forward/tmp -Djava.awt.headless=true -Xmx1536m"
 $env:APPDATA="$run/appdata";$env:LOCALAPPDATA="$run/localappdata";$env:TEMP="$run/tmp";$env:TMP="$run/tmp"
 $cp=($entries -join ';')
 $sources=@("$PSScriptRoot/src/experiment/StandaloneBolt.java", "$repo/forge-gui-desktop/src/test/java/forge/net/TestUtils.java", "$repo/forge-gui-desktop/src/test/java/forge/net/HeadlessGuiDesktop.java", "$repo/forge-gui-desktop/src/test/java/forge/net/HeadlessNetworkGuiGame.java")
 $inputs=[ordered]@{entryPoint='experiment.StandaloneBolt';testngJarExcluded=$true;classpath=@($entries|ForEach-Object{[IO.Path]::GetRelativePath($repo,$_).Replace('\','/')});sources=(Hash-Paths @($sources|ForEach-Object{[IO.Path]::GetRelativePath($repo,$_).Replace('\','/')}))}
 $inputs|ConvertTo-Json -Depth 6|Set-Content "$run/inputs.json" -Encoding utf8
 Run-Owned 'javac.exe' (@('-encoding','UTF-8','-source','17','-target','17','-sourcepath',"$run/empty-sourcepath",'-cp',$cp,'-d',"$run/classes")+$sources) 'compile' 60
 [xml]$pom=Get-Content "$repo/pom.xml"
 $opens=$pom.SelectSingleNode('//*[local-name()="argLine"]').InnerText.Trim() -split '\s+'
 Run-Owned 'java.exe' (@($opens)+@('-cp',"$run/classes;$cp",'experiment.StandaloneBolt',"$run/diagnostic.json")) 'standalone' 180
 $diagnostic=Get-Content "$run/diagnostic.json" -Raw|ConvertFrom-Json
 if($diagnostic.outcome -cne 'PASS'){throw 'No successful standalone result'}
 $ok=$true
}catch{$primary=$_;throw}
finally{
 foreach($key in $keys){Restore-Env $key $saved[$key]}
 $checks=@(foreach($key in $keys){[ordered]@{name=$key;exactMatch=(Equal-Env (Read-Env $key) $saved[$key])}})
 $restored=@($checks|Where-Object{-not $_.exactMatch}).Count -eq 0
 $after=Snapshot
 $after|ConvertTo-Json -Depth 6|Set-Content "$run/integrity-after.json" -Encoding utf8
 $sourceDiff=Differences $before.protected $after.protected;$cacheDiff=Differences $before.cache $after.cache
 $integrity=$sourceDiff.Count -eq 0 -and $cacheDiff.Count -eq 0
 $result=[ordered]@{label=$Label;startedUtc=$start.ToString('o');endedUtc=[DateTime]::UtcNow.ToString('o');passed=($ok -and $restored -and $integrity);restorationPreflightPassed=$true;environmentRestored=$restored;environmentChecks=$checks;protectedFileCount=$before.protected.Count;sourceChanges=$sourceDiff;cacheFileCount=$before.cache.Count;cacheChanges=$cacheDiff;processes=$processes;noGameplayCleanup=$true}
 $result|ConvertTo-Json -Depth 7|Set-Content "$run/result.json" -Encoding utf8
 $result|ConvertTo-Json -Depth 7
 if((-not $restored -or -not $integrity) -and $null -eq $primary){throw 'Restoration/integrity failure; stop without retry'}
}
