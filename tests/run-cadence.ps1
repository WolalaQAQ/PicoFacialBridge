$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot
$java=(Get-Content "$root\tools\jdk-path.txt" -Raw).Trim()
$out="$root\build\cadence-tests"
New-Item -ItemType Directory -Path $out -Force | Out-Null
$sources=@("$PSScriptRoot\CadenceTest.java")
foreach($name in @('TrackingBuffer','TrackingData','FramePump','DeliveryLedger')) {
    $file="$root\shared\src\main\java\dev\pico\facialprobe\$name.java"
    if(Test-Path $file){$sources+=$file}
}
& "$java\bin\javac.exe" '-J-Duser.language=en' -d $out @sources
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& "$java\bin\java.exe" -cp $out dev.pico.facialprobe.CadenceTest
exit $LASTEXITCODE
