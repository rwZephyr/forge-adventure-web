$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$experiment = Join-Path $repoRoot 'experiments/adventure-teavm'
$source = Join-Path $repoRoot 'forge-gui-mobile/src/forge/adventure/stage/SpriteGroup.java'
$originalAssets = Join-Path $repoRoot 'forge-gui/res/adventure/common/sprites'
$webRoot = Join-Path $experiment 'build/dist/js/webapp'
foreach ($asset in @('treasure.atlas', 'treasure.png')) {
    $original = (Get-FileHash -LiteralPath (Join-Path $originalAssets $asset) -Algorithm SHA256).Hash
    $built = (Get-FileHash -LiteralPath (Join-Path $webRoot "assets/sprites/$asset") -Algorithm SHA256).Hash
    if ($original -ne $built) { throw "Generated asset differs from original: $asset" }
    Write-Output "$asset SHA256 $built (original and generated match)"
}
$forgeClasses = @(Get-ChildItem -LiteralPath (Join-Path $experiment 'build/classes/java/main/forge') -Recurse -File)
$allowed = @('SpriteGroup.class', 'SpriteGroup$1.class')
if ($forgeClasses.Count -ne 2 -or @($forgeClasses | Where-Object { $_.Name -notin $allowed }).Count -ne 0) {
    throw 'Unexpected Forge classes compiled. The experiment must contain only SpriteGroup and its comparator.'
}
$builtSource = Join-Path $webRoot 'src/forge/adventure/stage/SpriteGroup.java'
if (Test-Path -LiteralPath $builtSource) {
    $originalText = [IO.File]::ReadAllText($source).Replace("`r`n", "`n")
    $sourceMapText = [IO.File]::ReadAllText($builtSource).Replace("`r`n", "`n")
    if ($originalText -ne $sourceMapText) { throw 'Source-map SpriteGroup differs from the original source.' }
}
Write-Output 'PASS: only the original SpriteGroup and comparator compiled; original asset bytes preserved.'
