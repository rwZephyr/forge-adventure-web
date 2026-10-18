param(
    [ValidateSet('jvmControl','browserDefault','browserExplicit','browserMissingAmount','dependencyInventory')]
    [string]$Task = 'jvmControl',
    [switch]$WriteLocks,
    [string]$JdkHome = 'C:/Program Files/Java/jdk-17'
)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$projectRoot = Join-Path $repoRoot 'experiments/rules-teavm'
$gradle = Join-Path $repoRoot '.local-tools/gradle-9.6.1/bin/gradle.bat'
$logDir = Join-Path $projectRoot 'evidence'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$savedJava = $env:JAVA_HOME
$savedGradle = $env:GRADLE_USER_HOME
$arguments = @('-p', $projectRoot, '--offline', '--no-daemon', '--max-workers=1', '--console=plain', $Task)
if ($WriteLocks) { $arguments += '--write-locks' }
try {
    $env:JAVA_HOME = $JdkHome
    $env:GRADLE_USER_HOME = Join-Path $repoRoot '.local-tools/gradle-cache'
    Write-Output "JDK: $JdkHome; Gradle: $($arguments -join ' ')"
    & $gradle @arguments 2>&1 | Tee-Object -FilePath (Join-Path $logDir "$Task.log")
    $probeExitCode = $LASTEXITCODE
    Write-Output "PROBE_EXIT_CODE=$probeExitCode"
    if ($probeExitCode -ne 0) { throw "$Task failed with exit code $probeExitCode; see evidence/$Task.log" }
} finally { $env:JAVA_HOME = $savedJava; $env:GRADLE_USER_HOME = $savedGradle }
