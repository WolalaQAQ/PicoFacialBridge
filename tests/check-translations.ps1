$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot
$enPath="$root/bridge/src/main/res/values/strings.xml"
$zhPath="$root/bridge/src/main/res/values-zh/strings.xml"
if (!(Test-Path $enPath) -or !(Test-Path $zhPath)) { throw 'English and Chinese UI resources are required' }
[xml]$en=Get-Content $enPath -Raw -Encoding UTF8
[xml]$zh=Get-Content $zhPath -Raw -Encoding UTF8
$english=@{}; $chinese=@{}
foreach($entry in $en.resources.string) { $english[$entry.name]=$entry.InnerText }
foreach($entry in $zh.resources.string) { $chinese[$entry.name]=$entry.InnerText }
if (Compare-Object @($english.Keys | Sort-Object) @($chinese.Keys | Sort-Object)) { throw 'Translation keys differ' }
foreach($key in $english.Keys) {
    if ([string]::IsNullOrWhiteSpace($chinese[$key])) { throw "Missing Chinese: $key" }
    $a=@([regex]::Matches($english[$key], '%\d+\$[\d.]*[sdf]') | ForEach-Object Value | Sort-Object)
    $b=@([regex]::Matches($chinese[$key], '%\d+\$[\d.]*[sdf]') | ForEach-Object Value | Sort-Object)
    if (($a -join ',') -ne ($b -join ',')) { throw "Format placeholders differ: $key" }
}
Write-Output "PASS: $($english.Count) English/Chinese strings and format placeholders match"
