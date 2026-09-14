# PicoFacialBridge

[中文说明](README_zh.md)

[Verification summary](docs/VERIFICATION.md)

Rootless PICO 4 Pro eye/face tracking bridge, plus an ordinary-UID Binder probe.
Android Activity → ForegroundService → NDK Binder / Java Parcel → JNI read-only shared memory → UDP → existing PicoFacialDataModule.

**Experimental, firmware-specific build, v0.2.0.** Tested on PICO OS **5.13.7**, Android **10 / API 29**, arm64-v8a. The earlier **0.1.1** live acceptance verified ordinary-UID Binder, foreground service, UDP, VRCFT module loading and VRChat OSC input. Its 60-second delivery audit matched 6798 sent/received packets and ordered SHA-256, with zero reported ring-history overruns. Eye acquisition was around 90 Hz and facial source updates around 23 Hz. No smoothing/interpolation. New 0.2.0 single-channel live acceptance and Virtual Desktop coexistence remain pending. See the [verification summary](docs/VERIFICATION.md).

## Daily use
1. Open **PicoFacialBridge** from the headset's 2D applications.
2. Tap **Start** and allow eye/face runtime permissions on first use.
3. Wear the headset. Check Eye/Face and status; stale data is not reported as live.
4. Open Virtual Desktop, then your usual SteamVR/VRChat stack.
5. Run VRCFaceTracking with **Pico Facial Data Module** from thoricelli.
6. Tap **Stop** in the app or its persistent notification when finished.

No root, Shizuku, daily ADB, PICO Connect or OpenXR session is used by this APK.
One-time APK installation can use ADB; the running bridge uses Wi-Fi only.

### Transmission and language (0.2.0)
- **Transmit eye tracking** and **Transmit face tracking** are independent and default on. Changes apply immediately and persist. Both off pauses capture/data while keeping UDP control available.
- Each **Hz** is the number of distinct source frames successfully sent in the trailing second, not source acquisition speed or total UDP packets. Held samples and keepalives are excluded. No client, disabled channel or idle stream = 0 Hz (idle events expire within one second).
- Use **中文 / English** to change the app and foreground notification language without restarting tracking. The choice is saved; first launch follows the device language (Chinese, otherwise English). Diagnostic logs/system error details retain their original technical text.
- Single-channel transmission still needs both permissions because PICO uses a shared capture algorithm. The switches control forwarding, not individual hardware camera power.
- The stock module's `DisableEyeTracking` / `DisableFaceTracking` settings are receiver-side and remain untouched. The APK uses existing validity flags in the unchanged 536-byte protocol. Eye-only includes raw blink/brow fields used by the eye parser; disabled stream data is cleared.
- The stock receiver may retain its last expression/gaze after a stream stops. These switches do not force a neutral avatar or change VRCFT module capability indicators.

0.2.0 build, core/UDP-loopback tests and device UI/persistence tests passed; worn-headset single-channel delivery and avatar behavior still need live acceptance. The 60-second measurements above belong to 0.1.1.

## Build (Windows)
```powershell
# Existing Conda Python is used; no new Python installation is needed.
& "$env:USERPROFILE\miniconda3\python.exe" .\tools\bootstrap.py
# On a new SDK, review/accept its licenses:
$env:JAVA_HOME=(Get-Content .\tools\jdk-path.txt -Raw).Trim()
& "$env:USERPROFILE\.cache\pico-android\sdk\cmdline-tools\12.0\bin\sdkmanager.bat" --licenses
.\tools\build.ps1
.\tests\run-unit.ps1
.\tests\run-protocol.ps1
.\tests\run-cadence.ps1
.\tests\run-forwarding.ps1
.\tests\check-translations.ps1
```

Toolchain: JDK 17, Gradle 8.9, AGP 8.7.3, compileSdk/build-tools 35, NDK 26.1.10909125, CMake 3.22.1. Only arm64-v8a is packaged. The conventional Gradle project can also be opened in Android Studio; point it at your SDK and JDK 17.

Outputs:
- `artifacts/PicoFacialBridge-debug.apk`
- `artifacts/PicoFacialBinderProbe-bare.apk`
- `artifacts/PicoFacialBinderProbe-declared.apk`

These are locally debug-signed builds for personal testing, not Play Store releases. Keep signing keys private and retain them for compatible upgrades. No proprietary PICO libraries are bundled.

## PC module and network
See [Chinese configuration guide](README_zh.md#vrcfacetracking-配置).
The original protocol is preserved: discovery `239.255.255.250:9030`, `DISCOVER_DAEMON`, `MARCO\0` / `POLO`, `STOP`, 536-byte native-prefix data packets.
VPN or virtual adapters can interfere with multicast discovery. Select the physical LAN adapter, or use the module's direct headset `IP` setting with the current address shown in the APK.

Use only on a trusted LAN: the existing protocol has no authentication/encryption. Do not expose UDP 9030 to the Internet. Raw samples are saved only by the diagnostic probe in its private app storage; the bridge does not persist facial samples.

## Layout
- `probe/`: minimum diagnostic Activity and detailed per-step logs.
- `bridge/`: foreground service, UI and UDP peer lifecycle.
- `shared/`: JNI Binder/mmap ownership and firmware data parsing.
- `tests/`: deterministic wire/bounds/recovery tests and a VRCFT-compatible LAN receiver.
- `docs/`: upstream protocol audit and public verification summaries.
- `evidence/`: local device logs and biometric test snapshots; **do not publish**.
- `reference/`: upstream sources and read-only device inspection files; not distributed with APK.

380 core/protocol/cadence/UDP-loopback checks, 40 bilingual resource checks and 15 device UI checks pass. This is not a claim of 80% whole-application coverage; Android lifecycle and physical-device acceptance are separately tracked.

## Repository privacy
Only source, build scripts, the Gradle wrapper and public documentation are tracked. Signing keys, SDK paths, build products, downloaded reference binaries, raw device/biometric evidence and private session notes stay local. Diagnostic artifacts may contain biometric data: inspect them before sharing, and never force-add ignored evidence or credentials.

## Limitations
- Hard-checked against this firmware's 200-byte eye and 896-byte face slots; unknown layouts fail explicitly.
- The Android 10 service-manager symbol is firmware-exposed/APEX, not a stable cross-version public SDK contract.
- Sleep/wear state can pause sensor output. The bridge detects stale mappings and retries; wake/recovery still needs extended user testing.
- The foreground service improves survival but cannot override a user force-stop, device shutdown, or OEM policy.
- Restarting the device does not auto-start tracking: deliberately open the app and tap Start.
- UI and notifications support English/Chinese. Camera/sensor privacy indicators and runtime grants remain intact.

Upstream credit and MIT notice: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
