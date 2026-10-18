param([switch]$Download)
$ErrorActionPreference = 'Stop'
if (!$Download) { throw 'Downloading tools requires explicit approval. After approval, pass -Download.' }
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$toolsDir = Join-Path $repoRoot '.local-tools'
$downloadDir = Join-Path $toolsDir 'downloads'
New-Item -ItemType Directory -Force -Path $downloadDir | Out-Null
$packages = @(
    @{
        Archive = 'apache-maven-3.9.16-bin.zip'
        Url = 'https://downloads.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip'
        Algorithm = 'SHA512'
        Hash = 'ed41650d42485cfc243fad22158caf9cbb5dc408ce7a09ddb94dd42a019de929ca43065bfa450612cf12bf78b5cafa3884b96c090de326ff590448c933454af3'
        Directory = 'apache-maven-3.9.16'
    },
    @{
        Archive = 'gradle-9.6.1-bin.zip'
        Url = 'https://downloads.gradle.org/distributions/gradle-9.6.1-bin.zip'
        Algorithm = 'SHA256'
        Hash = '9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14'
        Directory = 'gradle-9.6.1'
    }
)
foreach ($package in $packages) {
    $archivePath = Join-Path $downloadDir $package.Archive
    if (!(Test-Path -LiteralPath $archivePath)) {
        Write-Output "Downloading $($package.Archive)"
        Invoke-WebRequest -Uri $package.Url -OutFile $archivePath
    }
    $actual = (Get-FileHash -LiteralPath $archivePath -Algorithm $package.Algorithm).Hash
    if ($actual -ne $package.Hash) { throw "Checksum mismatch: $archivePath. Do not extract or execute it." }
    Write-Output "$($package.Algorithm) verified: $($package.Archive)"
    if (!(Test-Path -LiteralPath (Join-Path $toolsDir $package.Directory))) {
        Expand-Archive -LiteralPath $archivePath -DestinationPath $toolsDir
    }
}
Write-Output "Portable tools ready in $toolsDir; no environment variables or system settings changed."
