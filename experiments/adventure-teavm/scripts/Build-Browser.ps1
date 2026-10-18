param([string]$JdkHome = 'C:/Program Files/Java/jdk-17', [switch]$Offline, [switch]$WriteLocks)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$toolsDir = Join-Path $repoRoot '.local-tools'
$gradle = Join-Path $toolsDir 'gradle-9.6.1/bin/gradle.bat'
if (!(Test-Path -LiteralPath $gradle)) { throw 'Install the approved portable Gradle first.' }
$logDir = Join-Path $toolsDir 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$logPath = Join-Path $logDir ('browser-build-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
$savedJava = $env:JAVA_HOME
$savedGradle = $env:GRADLE_USER_HOME
try {
    $env:JAVA_HOME = $JdkHome
    $env:GRADLE_USER_HOME = Join-Path $toolsDir 'gradle-cache'
    $gradleArgs = @('-p', (Join-Path $repoRoot 'experiments/adventure-teavm'), '--no-daemon', '--max-workers=1', '--console=plain')
    if ($Offline) { $gradleArgs += '--offline' }
    if ($WriteLocks) { $gradleArgs += '--write-locks' }
    $gradleArgs += 'browserArtifact'
    Write-Output "Command: $gradle $($gradleArgs -join ' ')"
    & $gradle @gradleArgs 2>&1 | Tee-Object -FilePath $logPath
    $buildExit = $LASTEXITCODE
    Write-Output "Browser build exit code: $buildExit; log: $logPath"
    if ($buildExit -ne 0) { throw "Browser build failed (exit $buildExit). See $logPath" }
}
finally {
    $env:JAVA_HOME = $savedJava
    $env:GRADLE_USER_HOME = $savedGradle
}
