param(
    [ValidateSet('Compile', 'Test')][string]$Phase = 'Compile',
    [string]$JdkHome = 'C:/Program Files/Java/jdk-17',
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$toolsDir = Join-Path $repoRoot '.local-tools'
$maven = Join-Path $toolsDir 'apache-maven-3.9.16/bin/mvn.cmd'
if (!(Test-Path -LiteralPath $maven)) { throw 'Install the approved portable Maven first.' }
if (!(Test-Path -LiteralPath (Join-Path $JdkHome 'bin/javac.exe'))) { throw "Missing JDK: $JdkHome" }
$logDir = Join-Path $toolsDir 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$logPath = Join-Path $logDir ("baseline-$($Phase.ToLower())-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
$savedJava = $env:JAVA_HOME
$savedMavenOpts = $env:MAVEN_OPTS
$savedAppData = $env:APPDATA
$savedLocalAppData = $env:LOCALAPPDATA
$savedJavaOptions = $env:JAVA_TOOL_OPTIONS
$pushed = $false
try {
    $env:JAVA_HOME = $JdkHome
    $env:MAVEN_OPTS = '-Xmx1536m -Dfile.encoding=UTF-8'
    Push-Location $repoRoot
    $pushed = $true
    & $maven -version 2>&1 | Tee-Object -FilePath $logPath
    if ($LASTEXITCODE -ne 0) { throw 'Maven version check failed.' }
    $mavenArgs = @('-B', '-ntp', '-T1', "-Dmaven.repo.local=$(Join-Path $toolsDir 'm2')", '-pl', 'forge-gui-mobile-dev', '-am')
    if ($Offline) { $mavenArgs += '-o' }
    if ($Phase -eq 'Compile') { $mavenArgs += @('-DskipTests', 'compile') }
    else {
        $testHome = Join-Path $toolsDir 'test-home'
        $testTemp = Join-Path $testHome 'tmp'
        $env:APPDATA = Join-Path $testHome 'Roaming'
        $env:LOCALAPPDATA = Join-Path $testHome 'Local'
        New-Item -ItemType Directory -Force -Path $testHome,$testTemp,$env:APPDATA,$env:LOCALAPPDATA | Out-Null
        $env:JAVA_TOOL_OPTIONS = '-Xmx1536m'
        $mavenArgs += @('-DforkCount=1', '-DreuseForks=true', "-Duser.home=$testHome", "-Djava.io.tmpdir=$testTemp", 'test')
    }
    Write-Output "Command: $maven $($mavenArgs -join ' ')"
    & $maven @mavenArgs 2>&1 | Tee-Object -FilePath $logPath -Append
    $buildExit = $LASTEXITCODE
    Write-Output "Baseline $Phase exit code: $buildExit; log: $logPath"
    if ($buildExit -ne 0) { throw "Baseline $Phase failed (exit $buildExit). See $logPath" }
}
finally {
    if ($pushed) { Pop-Location }
    $env:JAVA_HOME = $savedJava
    $env:MAVEN_OPTS = $savedMavenOpts
    $env:APPDATA = $savedAppData
    $env:LOCALAPPDATA = $savedLocalAppData
    $env:JAVA_TOOL_OPTIONS = $savedJavaOptions
}
