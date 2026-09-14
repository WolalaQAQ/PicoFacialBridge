$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot
$java=(Get-Content "$root\tools\jdk-path.txt" -Raw).Trim()
$out="$root\build\protocol-tests"
New-Item -ItemType Directory -Path $out -Force | Out-Null
$sources=@("$PSScriptRoot\PeerProtocolTest.java")
$production="$root\bridge\src\main\java\dev\pico\facialprobe\PeerProtocol.java"
if(Test-Path $production){$sources+=$production}
& "$java\bin\javac.exe" '-J-Duser.language=en' -d $out @sources
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& "$java\bin\java.exe" -cp $out dev.pico.facialprobe.PeerProtocolTest
exit $LASTEXITCODE
