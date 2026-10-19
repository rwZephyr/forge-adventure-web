param([Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]+$')][string]$Label)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path "$PSScriptRoot/../..").Path
$private=Join-Path $repo ".local-tools/standalone-bolt/$Label"
$public=Join-Path $PSScriptRoot "evidence/$Label"
if(Test-Path -LiteralPath $public){throw 'Never overwrite evidence'}
$result=Get-Content "$private/result.json" -Raw|ConvertFrom-Json
if(-not $result.passed){throw 'Review a failed run before publishing'}
New-Item -ItemType Directory -Path $public -Force|Out-Null
$files=@('compile.stdout.log','compile.stderr.log','standalone.stdout.log','standalone.stderr.log','diagnostic.json','inputs.json','result.json','integrity-before.json','integrity-after.json')
$rows=@(foreach($name in $files){
 $original=Join-Path $private $name
 $text=[IO.File]::ReadAllText($original)
 $text=$text.Replace($repo.Replace('\','/'),'<REPO>').Replace($repo,'<REPO>')
 $text=[regex]::Replace($text,'/0x[0-9a-fA-F]+','/<runtime-lambda-address>')
 $text=$text.Replace("`r`n","`n").Replace("`r","`n")
 # Public evidence must not include private profile/host/user strings or credential-like data.
 foreach($privateString in @($env:COMPUTERNAME,$env:USERNAME,[Environment]::GetFolderPath('UserProfile'))){
  if($privateString -and $text.IndexOf($privateString,[StringComparison]::OrdinalIgnoreCase) -ge 0){throw "Private identifier remains in $name; inspect manually"}
 }
 if($text -match '(?i)(gh[pousr]_[a-z0-9]{20,}|github_pat_[a-z0-9_]+|Bearer\s+\S+|-----BEGIN .*PRIVATE KEY|https?://[^/\s]+:[^/\s]+@)'){throw "Potential secret in $name"}
 $out=Join-Path $public $name
 [IO.File]::WriteAllText($out,$text,[Text.UTF8Encoding]::new($false))
 [ordered]@{file=$name;privateBytes=(Get-Item $original).Length;privateSha256=(Get-FileHash $original).Hash.ToLowerInvariant();publicBytes=(Get-Item $out).Length;publicSha256=(Get-FileHash $out).Hash.ToLowerInvariant()}
})
$sources=[ordered]@{}
foreach($name in @('src/experiment/StandaloneBolt.java','Run-Standalone.ps1','Publish-Evidence.ps1')){$sources[$name]=(Get-FileHash (Join-Path $PSScriptRoot $name)).Hash.ToLowerInvariant()}
$manifest=[ordered]@{schemaVersion=1;label=$Label;privateOriginalsPreserved=$true;normalization=@('Repository absolute path -> <REPO>','Generated lambda /0x address -> /<runtime-lambda-address>; identityHashCode diagnostic labels retained','CRLF/CR -> LF; UTF-8 without BOM','No other content removed; original private files unchanged');credentialScan='Host/user/profile and credential-pattern checks passed; complete logs manually reviewed';sourceSha256=$sources;files=$rows}
[IO.File]::WriteAllText((Join-Path $public 'manifest.json'),($manifest|ConvertTo-Json -Depth 8)+"`n",[Text.UTF8Encoding]::new($false))
Write-Output "Published sanitized evidence for $Label"
