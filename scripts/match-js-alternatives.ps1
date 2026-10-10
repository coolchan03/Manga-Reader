param([string]$AuditCsv=(Join-Path (Split-Path $PSScriptRoot -Parent) 'source-audit/sources.csv'))
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$rows=Import-Csv $AuditCsv
function Hostname([string]$url) {
 try { $hostName=([uri]$url).DnsSafeHost.ToLowerInvariant() -replace '^www\.','';return $hostName } catch { return '' }
}
function Normalize([string]$s) { return ($s.ToLowerInvariant() -replace '[^a-z0-9]','') }
$js=@($rows|Where-Object status -eq 'CODE_AVAILABLE_UNTESTED')
$dart=@($rows|Where-Object status -eq 'UNSUPPORTED_DART')
$report=foreach($d in $dart) {
 $hostName=Hostname $d.url
 $normalizedName=Normalize $d.name
 $candidates=@($js|Where-Object { ($hostName -and (Hostname $_.url) -eq $hostName) -or ($normalizedName.Length -ge 3 -and (Normalize $_.name) -eq $normalizedName) })
 $best=$candidates|Select-Object -First 1
 [pscustomobject]@{dartName=$d.name;dartUrl=$d.url;jsName=$best.name;jsUrl=$best.url;status=$(if($best){'POSSIBLE_JS_ALTERNATIVE'}else{'NO_JS_ALTERNATIVE'});matchType=$(if($best){if($hostName -and (Hostname $best.url) -eq $hostName){'same_host'}else{'same_name'}}else{''})}
}
$out=Join-Path $root 'source-audit/js-alternatives.csv'
$report|Export-Csv $out -NoTypeInformation -Encoding UTF8
$report|Group-Object status|Select-Object Name,Count|Format-Table -AutoSize
Write-Output ('Candidate matches only; functionality not verified. Report: '+$out)
