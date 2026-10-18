param([switch]$Approved)
$ErrorActionPreference = 'Stop'
if (-not $Approved) { throw 'Server startup requires separate owner approval. Pass -Approved only after receiving it.' }
$artifact = (Resolve-Path (Join-Path $PSScriptRoot '../build/web')).Path
& python -u -m http.server 8765 --bind 127.0.0.1 --directory $artifact
exit $LASTEXITCODE
