param([switch]$Approved)
$ErrorActionPreference = 'Stop'
if (!$Approved) { throw 'Server startup requires separate user approval. After approval, pass -Approved.' }
$webRoot = (Resolve-Path (Join-Path $PSScriptRoot '../build/dist/js/webapp')).Path
if (!(Test-Path -LiteralPath (Join-Path $webRoot 'app.js'))) { throw 'Build the browser artifact first.' }
Write-Output "Serving only $webRoot at http://127.0.0.1:8765; Ctrl+C stops this temporary listener."
& python -m http.server 8765 --bind 127.0.0.1 --directory $webRoot
if ($LASTEXITCODE -ne 0) { throw "Loopback server exited with code $LASTEXITCODE" }
