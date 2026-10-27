$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
$java = (Get-Content "$root\tools\jdk-path.txt" -Raw).Trim()
$out = "$root\build\gaze-tests"
New-Item -ItemType Directory -Path $out -Force | Out-Null
$sources = @(
    "$root\bridge\src\main\java\dev\pico\facialprobe\PeerProtocol.java",
    "$root\bridge\src\main\java\dev\pico\facialprobe\GazeSubscription.java",
    "$root\bridge\src\main\java\dev\pico\facialprobe\GazeStatus.java",
    "$root\shared\src\main\java\dev\pico\facialprobe\TrackingData.java",
    "$root\shared\src\main\java\dev\pico\facialprobe\FramePump.java",
    "$root\shared\src\main\java\dev\pico\facialprobe\StreamHealth.java",
    "$PSScriptRoot\GazeSubscriptionTest.java",
    "$PSScriptRoot\GazeStatusTest.java"
)
& "$java\bin\javac.exe" '-J-Dfile.encoding=UTF-8' -encoding UTF-8 -Xlint:all -Werror -d $out @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& "$java\bin\java.exe" '-Dfile.encoding=UTF-8' -cp $out dev.pico.facialprobe.GazeSubscriptionTest
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& "$java\bin\java.exe" '-Dfile.encoding=UTF-8' -cp $out dev.pico.facialprobe.GazeStatusTest
exit $LASTEXITCODE
