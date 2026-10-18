param([string]$JdkHome = 'C:/Program Files/Java/jdk-17')
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$savedJava = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $JdkHome
    Push-Location $repoRoot
    New-Item -ItemType Directory -Force 'experiments/ai-teavm/evidence' | Out-Null
    & "$repoRoot/.local-tools/apache-maven-3.9.16/bin/mvn.cmd" '-o' '-B' '-ntp' '-T1' "-Dmaven.repo.local=$repoRoot/.local-tools/m2" '-pl' 'forge-ai' '-am' '-DskipTests' 'compile' 2>&1 | Tee-Object -FilePath 'experiments/ai-teavm/evidence/maven-compile.log'
    if ($LASTEXITCODE -ne 0) { throw "Original Forge compile failed: $LASTEXITCODE" }
    & 'C:/Users/Silent/AppData/Local/Programs/Python/Python312/python.exe' "$PSScriptRoot/prepare_inputs.py"
    if ($LASTEXITCODE -ne 0) { throw 'Input preparation failed' }
} finally { Pop-Location; $env:JAVA_HOME = $savedJava }
