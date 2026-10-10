param([int]$TimeoutSec=10,[int]$MaxSources=0)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$store=Join-Path $root 'app/src/main/kotlin/org/koitharu/kotatsu/sourcerepo/data/SourceRepoStore.kt'
$urls=[regex]::Matches([IO.File]::ReadAllText($store),'https://[^"\s]+index\.json') | ForEach-Object Value | Sort-Object -Unique
$results=[Collections.Generic.List[object]]::new()
$seen=@{}
Add-Type -AssemblyName System.Net.Http
$client=[System.Net.Http.HttpClient]::new()
$client.Timeout=[TimeSpan]::FromSeconds($TimeoutSec)
function Fetch($url) {
 try { $r=$client.GetAsync($url).GetAwaiter().GetResult(); return @{code=[int]$r.StatusCode;body=$r.Content.ReadAsStringAsync().GetAwaiter().GetResult();error=''} }
 catch { return @{code=0;body='';error=$_.Exception.Message} }
}
foreach($url in $urls) {
 $f=Fetch $url
 if($f.code -ne 200) { $results.Add([pscustomobject]@{name='[repository]';url=$url;repo=$url;status='REPO_ERROR';http=$f.code;details=$f.error});continue }
 try { $items=ConvertFrom-Json -InputObject $f.body } catch { $results.Add([pscustomobject]@{name='[repository]';url=$url;repo=$url;status='INVALID_JSON';http=$f.code;details=$_.Exception.Message});continue }
 foreach($e in $items) {
  if(-not $e.sourceCodeUrl -or -not $e.id){continue}
  $id=[string]$e.id
  if($seen.ContainsKey($id)){continue}
  $seen[$id]=$true
  $js=($e.sourceCodeLanguage -eq 1 -or ($null -eq $e.sourceCodeLanguage -and $e.sourceCodeUrl -match '\.js($|\?)'))
  $status='UNSUPPORTED_DART';$http=0;$details='Dart or unknown runtime'
  if($js) {
   $code=Fetch ([string]$e.sourceCodeUrl);$http=$code.code;$details=$code.error
   if($http -eq 200){if($code.body -match 'DefaultExtension'){$status='CODE_AVAILABLE_UNTESTED'}else{$status='INCOMPATIBLE_ENTRYPOINT';$details='Missing DefaultExtension'}}
   elseif($http -eq 403 -or $http -eq 429){$status='BLOCKED_OR_RATE_LIMITED'}else{$status='CODE_FETCH_FAILED'}
  }
  $results.Add([pscustomobject]@{name=$e.name;url=$e.baseUrl;repo=$url;status=$status;http=$http;details=$details})
  if($MaxSources -gt 0 -and $results.Count -ge $MaxSources){break}
 }
 if($MaxSources -gt 0 -and $results.Count -ge $MaxSources){break}
}
$out=Join-Path $root 'source-audit';New-Item -ItemType Directory -Force $out|Out-Null
$results|Export-Csv (Join-Path $out 'sources.csv') -NoTypeInformation -Encoding UTF8
$results|Group-Object status|Sort-Object Name|Select-Object Name,Count|Format-Table -AutoSize
Write-Output ('Audited '+$results.Count+' unique source IDs; report: '+(Join-Path $out 'sources.csv'))
$client.Dispose()
