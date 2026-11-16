$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
$java = (Get-Content "$root\tools\jdk-path.txt" -Raw).Trim()
$out = "$root\build\expression-tests"
New-Item -ItemType Directory -Path $out -Force | Out-Null
$sources = @(
    "$root\shared\src\main\java\dev\pico\facialprobe\TrackingData.java",
    "$root\bridge\src\main\java\dev\pico\facialprobe\ExpressionStatus.java",
    "$root\bridge\src\main\java\dev\pico\facialprobe\ExpressionCatalog.java",
    "$root\bridge\src\main\java\dev\pico\facialprobe\NativeEyeStatus.java",
    "$root\bridge\src\main\java\dev\pico\facialprobe\OutputStatus.java",
    "$PSScriptRoot\ExpressionStatusTest.java",
    "$PSScriptRoot\NativeEyeStatusTest.java"
)
& "$java\bin\javac.exe" '-J-Dfile.encoding=UTF-8' -encoding UTF-8 -Xlint:all -Werror -d $out @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& "$java\bin\java.exe" '-Dfile.encoding=UTF-8' -cp $out dev.pico.facialprobe.ExpressionStatusTest
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& "$java\bin\java.exe" '-Dfile.encoding=UTF-8' -cp $out dev.pico.facialprobe.NativeEyeStatusTest
exit $LASTEXITCODE
