param(
    [ValidateSet('jvmControl','metadataInventory','jvmTrace','browserOriginal','dependencyInventory')][string]$Task='jvmControl',
    [switch]$WriteLocks,
    [string]$JdkHome='C:/Program Files/Java/jdk-17'
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$savedJava=$env:JAVA_HOME
$savedGradle=$env:GRADLE_USER_HOME
$arguments=@('-p', "$root/experiments/ai-teavm", '--offline','--no-daemon','--max-workers=1','--console=plain',$Task)
if ($WriteLocks) { $arguments+='--write-locks' }
try {
    $env:JAVA_HOME=$JdkHome
    $env:GRADLE_USER_HOME="$root/.local-tools/gradle-cache"
    Write-Output "JDK=$JdkHome; Gradle arguments=$($arguments -join ' ')"
    $started=[DateTime]::UtcNow
    & "$root/.local-tools/gradle-9.6.1/bin/gradle.bat" @arguments 2>&1 | Tee-Object -FilePath "$root/experiments/ai-teavm/evidence/$Task.log"
    $probeExit=$LASTEXITCODE
    [ordered]@{task=$Task;arguments=$arguments;jdk=$JdkHome;startedUtc=$started.ToString('o');finishedUtc=[DateTime]::UtcNow.ToString('o');elapsedSeconds=([DateTime]::UtcNow-$started).TotalSeconds;exitCode=$probeExit} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath "$root/experiments/ai-teavm/evidence/$Task-status.json"
    Write-Output "PROBE_EXIT_CODE=$probeExit"
    if ($probeExit -ne 0) { throw "$Task failed: $probeExit; raw log retained" }
} finally { $env:JAVA_HOME=$savedJava; $env:GRADLE_USER_HOME=$savedGradle }
