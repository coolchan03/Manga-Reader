param([int]$TimeoutSec=12)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$items=@()
$items+=Import-Csv (Join-Path $root 'source-audit/js-alternatives.csv') | Where-Object status -eq 'POSSIBLE_JS_ALTERNATIVE' | ForEach-Object { [pscustomobject]@{name=$_.dartName;url=$_.dartUrl;method='JS';match=$_.jsName} }
$items+=Import-Csv (Join-Path $root 'source-audit/kotatsu-matches.csv') | Where-Object status -eq 'POSSIBLE_KOTATSU_MATCH' | ForEach-Object { [pscustomobject]@{name=$_.name;url=$_.site;method='Kotatsu';match=$_.parserPaths} }
Add-Type -AssemblyName System.Net.Http
$client=[System.Net.Http.HttpClient]::new()
$client.Timeout=[TimeSpan]::FromSeconds($TimeoutSec)
$client.DefaultRequestHeaders.UserAgent.ParseAdd('Mozilla/5.0 (compatible; MonogatariSourceAudit/1.0)')
$results=foreach($item in $items) {
 $status='NETWORK_ERROR';$http=0;$finalUrl='';$detail=''
 try {
  $res=$client.GetAsync($item.url).GetAwaiter().GetResult()
  $http=[int]$res.StatusCode;$finalUrl=$res.RequestMessage.RequestUri.AbsoluteUri
  $status=if($http -eq 200){'REACHABLE_UNTESTED'}elseif($http -in @(403,429,503)){'BLOCKED_OR_RATE_LIMITED'}else{'HTTP_ERROR'}
 } catch { $detail=$_.Exception.Message;if($detail -match 'timed out|canceled'){$status='TIMEOUT'}elseif($detail -match 'name|DNS|host'){$status='DNS_ERROR'} }
 [pscustomobject]@{name=$item.name;method=$item.method;url=$item.url;match=$item.match;status=$status;http=$http;finalUrl=$finalUrl;detail=$detail}
}
$out=Join-Path $root 'source-audit/candidate-probes.csv'
$results|Export-Csv $out -NoTypeInformation -Encoding UTF8
$results|Group-Object status|Select-Object Name,Count|Format-Table -AutoSize
Write-Output ('Network-only probe; no in-app functionality tested. Report: '+$out)
$client.Dispose()
