param([string]$JdkHome = 'C:/Program Files/Java/jdk-17')
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$maven = Join-Path $repoRoot '.local-tools/apache-maven-3.9.16/bin/mvn.cmd'
$cache = Join-Path $repoRoot '.local-tools/m2'
$logDir = Join-Path $repoRoot 'experiments/rules-teavm/evidence'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$savedJava = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $JdkHome
    Push-Location $repoRoot
    Write-Output "JDK: $JdkHome; offline Maven core/game closure; isolated cache: $cache"
    & $maven '-o' '-B' '-ntp' '-T1' "-Dmaven.repo.local=$cache" '-pl' 'forge-game' '-am' '-DskipTests' 'compile' 2>&1 |
        Tee-Object -FilePath (Join-Path $logDir 'maven-compile.log')
    if ($LASTEXITCODE -ne 0) { throw "Original Forge compile failed: $LASTEXITCODE" }
    & python (Join-Path $PSScriptRoot 'prepare_inputs.py')
    if ($LASTEXITCODE -ne 0) { throw 'Original input verification failed' }
} finally { Pop-Location; $env:JAVA_HOME = $savedJava }
