$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
$javaHome = (Get-Content "$root\tools\jdk-path.txt" -Raw).Trim()
$out = "$root\build\unit-tests"
New-Item -ItemType Directory -Path $out -Force | Out-Null
$production = "$root\shared\src\main\java\dev\pico\facialprobe\TrackingBuffer.java"
$sources = @("$PSScriptRoot\TrackingBufferTest.java")
if (Test-Path $production) { $sources += $production }
$data = "$root\shared\src\main\java\dev\pico\facialprobe\TrackingData.java"
if (Test-Path $data) { $sources += $data }
$mode = "$root\shared\src\main\java\dev\pico\facialprobe\TrackingMode.java"
if (Test-Path $mode) { $sources += $mode }
foreach($name in @('StreamHealth','ResourceScope')) {
    $file = "$root\shared\src\main\java\dev\pico\facialprobe\$name.java"
    if(Test-Path $file){$sources+=$file}
}
& "$javaHome\bin\javac.exe" -d $out @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& "$javaHome\bin\java.exe" -cp $out dev.pico.facialprobe.TrackingBufferTest
exit $LASTEXITCODE
