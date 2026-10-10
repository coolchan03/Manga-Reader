param([string]$AuditCsv = (Join-Path (Split-Path $PSScriptRoot -Parent) 'source-audit/sources.csv'))
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$api='https://api.github.com/repos/clquwu/kotatsu-parsers-redo/git/trees/master?recursive=1'
$tree=Invoke-RestMethod -Uri $api -TimeoutSec 25
$paths=@($tree.tree | Where-Object { $_.path -match '^src/main/kotlin/.*/site/.+Parser\.kt$' } | ForEach-Object path)
function Normalize([string]$s) { return ($s.ToLowerInvariant() -replace '[^a-z0-9]','') -replace 'parser$','' }
$parsers=@{}
foreach($path in $paths) {
 $key=Normalize ([IO.Path]::GetFileNameWithoutExtension($path))
 if(-not $parsers.ContainsKey($key)) { $parsers[$key]=@() }
 $parsers[$key]+=$path
}
$rows=Import-Csv $AuditCsv
$matches=foreach($row in $rows) {
 if($row.status -ne 'UNSUPPORTED_DART'){continue}
 $key=Normalize $row.name
 $found=if($parsers.ContainsKey($key)) {@($parsers[$key])} else {@()}
 [pscustomobject]@{name=$row.name;site=$row.url;status=$(if($found.Count -gt 0){'POSSIBLE_KOTATSU_MATCH'}else{'NO_NAME_MATCH'});parserPaths=($found -join ';')}
}
$out=Join-Path $root 'source-audit/kotatsu-matches.csv'
$matches|Export-Csv $out -NoTypeInformation -Encoding UTF8
$matches|Group-Object status|Select-Object Name,Count|Format-Table -AutoSize
Write-Output ('Name-only candidate matching; not functional verification. Report: '+$out)
