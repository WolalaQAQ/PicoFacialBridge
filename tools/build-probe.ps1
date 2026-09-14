param([switch]$DeclareTrackingPermissions)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
$java = (Get-Content "$PSScriptRoot\jdk-path.txt" -Raw).Trim()
$sdk = "$env:USERPROFILE\.cache\pico-android\sdk"
$bt = "$sdk\build-tools\35.0.0"
$env:JAVA_HOME = $java
$env:PATH = "$java\bin;$env:PATH"
$variant = if ($DeclareTrackingPermissions) { 'declared' } else { 'bare' }
$out = "$root\build\probe-$variant"
New-Item -ItemType Directory -Path "$out\classes","$out\dex","$root\artifacts" -Force | Out-Null
function Checked { param([scriptblock]$Command) & $Command; if ($LASTEXITCODE -ne 0) { throw "Build command failed ($LASTEXITCODE)" } }
$clang = "$sdk\ndk\26.1.10909125\toolchains\llvm\prebuilt\windows-x86_64\bin\aarch64-linux-android29-clang++.cmd"
Checked { & $clang -shared -fPIC -std=c++17 -Wall -Wextra -Werror -static-libstdc++ "$root\shared\src\main\cpp\probe.cpp" -lbinder_ndk -llog -ldl -o "$out\libpico_probe.so" }
$manifest = Get-Content "$root\probe\src\main\AndroidManifest.xml" -Raw
if ($DeclareTrackingPermissions) {
    $manifest = $manifest.Replace('<application ', '<uses-permission android:name="com.picovr.permission.EYE_TRACKING" /><uses-permission android:name="com.picovr.permission.FACE_TRACKING" /><application ')
}
$manifest | Set-Content "$out\AndroidManifest.xml" -Encoding utf8
Checked { & "$bt\aapt2.exe" link -I "$sdk\platforms\android-35\android.jar" --manifest "$out\AndroidManifest.xml" -o "$out\unsigned.apk" }
$sources = @(Get-ChildItem "$root\probe\src\main\java","$root\shared\src\main\java" -Filter '*.java' -Recurse | Select-Object -ExpandProperty FullName)
Checked { & "$java\bin\javac.exe" '-J-Duser.language=en' -encoding UTF-8 -source 8 -target 8 -classpath "$sdk\platforms\android-35\android.jar" -d "$out\classes" @sources }
Checked { & "$java\bin\jar.exe" cf "$out\classes.jar" -C "$out\classes" . }
Checked { & "$bt\d8.bat" --lib "$sdk\platforms\android-35\android.jar" --min-api 29 --output "$out\dex" "$out\classes.jar" }
Checked { & "$env:USERPROFILE\miniconda3\python.exe" -c "import zipfile; z=zipfile.ZipFile(r'$out\unsigned.apk','a'); z.write(r'$out\dex\classes.dex','classes.dex'); z.write(r'$out\libpico_probe.so','lib/arm64-v8a/libpico_probe.so'); z.close()" }
Checked { & "$bt\zipalign.exe" -f 4 "$out\unsigned.apk" "$out\aligned.apk" }
$keystore = "$root\tools\debug.keystore"
if (-not (Test-Path $keystore)) {
    Checked { & "$java\bin\keytool.exe" -genkeypair -keystore $keystore -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US' }
}
$apk = "$root\artifacts\PicoFacialBinderProbe-$variant.apk"
Checked { & "$bt\apksigner.bat" sign --ks $keystore --ks-pass pass:android --key-pass pass:android --out $apk "$out\aligned.apk" }
Checked { & "$bt\apksigner.bat" verify --verbose $apk }
Write-Output "APK: $apk"
