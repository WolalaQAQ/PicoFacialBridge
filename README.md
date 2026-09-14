# PicoFacialBridge

English | [简体中文](README_zh.md)

[![Android CI](https://github.com/WolalaQAQ/PicoFacialBridge/actions/workflows/android.yml/badge.svg)](https://github.com/WolalaQAQ/PicoFacialBridge/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Use your **PICO 4 Pro's eye and face tracking in VRCFaceTracking**, without rooting the headset. PicoFacialBridge is an Android app that reads the headset's tracking data and forwards it over your local network to the existing Pico Facial Data Module.

**[Download APK](https://github.com/WolalaQAQ/PicoFacialBridge/releases) · [Report an issue](https://github.com/WolalaQAQ/PicoFacialBridge/issues) · [Release guide](docs/RELEASING.md)**

## Features

- Start and stop tracking from the headset, with a foreground notification for background operation.
- Forward raw eye and face samples independently, without added smoothing or interpolation.
- Choose eye tracking, face tracking, or both; see each stream's actual transmission rate.
- Switch between English and Chinese in the app. Language and transmission preferences are saved.
- Connect to the original **PicoFacialDataModule** using automatic discovery or a configured headset IP.

No root, Magisk, Shizuku, PICO Connect or OpenXR session is required. ADB is optional for installation/debugging, not daily use. You do not need to install the original daemon alongside this app.

## Requirements and compatibility

| Component | Requirement |
| --- | --- |
| Headset | **PICO 4 Pro** with working eye and face tracking |
| Verified firmware | **PICO OS 5.13.7**, Android 10 / API 29, arm64-v8a |
| PC | Windows with [VRCFaceTracking](https://github.com/benaclejames/VRCFaceTracking) and [PicoFacialDataModule](https://github.com/thoricelli/PicoFacialDataModule) |
| Network | PC and headset on the same trusted LAN; UDP 9030 reachable |
| VRChat use | An avatar configured for face tracking and VRChat OSC enabled |

> **Experimental and firmware-specific.** Other models and firmware versions are not confirmed compatible. The bridge depends on PICO's internal tracking service and memory layout, which system updates may change. It cannot add tracking hardware to an unsupported headset.

The core LAN/VRCFT/VRChat path has been tested on this device. Current single-channel behavior, extended sleep/wake recovery and coexistence with Virtual Desktop still need further live testing. See the [version-specific verification summary](docs/VERIFICATION.md); historical test results are not a guarantee for every setup.

## Quick start

### 1. Install the headset app

1. Open [Releases](https://github.com/WolalaQAQ/PicoFacialBridge/releases) and download **`PicoFacialBridge-v<version>-arm64-v8a.apk`** from the chosen release's **Assets**. The source-code ZIP is not an installer.
2. Sideload it with your preferred headset APK installer. Alternatively, enable developer/USB debugging, connect and authorize the headset, then use [Android platform-tools](https://developer.android.com/tools/releases/platform-tools):

   ```sh
   adb install -r PicoFacialBridge-v0.2.0-arm64-v8a.apk
   ```

   Replace the example filename with the downloaded APK. No Android development environment is needed to use a release APK.
3. Open **PicoFacialBridge** in the headset's 2D apps. Enable eye/face tracking in the headset's system settings, tap **Start**, and allow **both** tracking permissions.
4. Wear/wake the headset. Leave both transmission switches on for the first connection.

**Updating:** install the newer release over the old one. Official releases must retain the same signing key. If you previously installed a local or CI debug APK, Android may reject the different signature: uninstall that build first (**this resets app preferences**). For an unexpected official-to-official signature mismatch, verify the download rather than blindly uninstalling.

### 2. Set up VRCFaceTracking

1. Download the ZIP from [PicoFacialDataModule Releases](https://github.com/thoricelli/PicoFacialDataModule/releases).
2. In VRCFaceTracking, open **Module Registry**, click **+**, and select the ZIP. See the [module instructions](https://github.com/thoricelli/PicoFacialDataModule#running) for details.
3. Start the bridge and VRCFaceTracking on the same LAN. The module should discover the headset. Use this module, not a PICO Connect / Streaming Assistant module.

If discovery fails, close VRCFaceTracking and set `IP` to the headset IPv4 shown in the bridge. Edit or merge these fields in `PicoFacialDataModule.json` under:

```text
%APPDATA%\VRCFaceTracking\CustomLibs\61ee1324-fd45-42f1-9636-8e28717cf6db\
```

```json
{
  "DisableEyeTracking": false,
  "DisableFaceTracking": false,
  "IP": "192.168.1.123"
}
```

Replace the example IP; preserve other existing settings. An empty `IP` uses discovery. Restart VRCFaceTracking after editing. The headset IP may change when Wi-Fi reconnects.

### 3. Use it

1. Open the bridge, tap **Start**, and wear the headset.
2. Start VRCFaceTracking with the Pico module. Check for a connected client and nonzero transmission rates when tracking is active.
3. Start your usual PCVR/VRChat setup, enable **OSC** in VRChat, and use a compatible avatar. The bridge forwards tracking only; it is not a video streamer or an avatar configuration tool.
4. When finished, tap **Stop** in the app or its notification. After rebooting, open the app and start it again; there is no boot auto-start.

The foreground service is intended to keep forwarding with the app window closed, but OEM background policy and force-stop can interrupt it. Virtual Desktop coexistence is not yet fully verified.

## Controls and status

| Control / status | Meaning |
| --- | --- |
| Start / Stop / Restart | Control the service without ADB |
| Transmit eye / face tracking | Independent forwarding switches; changes apply immediately and persist |
| Both switches off | Pause tracking capture/data while keeping UDP discovery and control available |
| Eye / Face Hz | Distinct source frames successfully sent in the last second, not acquisition rate or video FPS |
| 0 Hz | No client, disabled stream, or no fresh samples; repeats and keepalives do not count |
| Waiting for fresh tracking | Wear/wake the headset and check tracking settings and permissions |
| 中文 / English | Change UI and notification language without restarting tracking |

Both permissions are required even in single-channel mode because PICO uses a shared algorithm. These are **not individual camera-power controls**. The receiver may hold the last gaze/expression after a stream stops; disabling transmission does not force a neutral avatar pose.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| VRCFT cannot discover the headset | Correct module, same LAN, current IP, guest Wi-Fi/client isolation, VPN/virtual adapters; try direct `IP` |
| Firewall blocks the connection | Allow VRCFaceTracking on the trusted private network and UDP 9030 as needed; do not disable the whole firewall |
| Client connected but 0 Hz | Enable transmission; wear/wake the headset; allow both permissions and enable system tracking |
| `Error` or unsupported layout | Open **Show diagnostic logs**; record the error, model and firmware. Other firmware is not assumed supported |
| Tracking stops after sleep/network change | Wake the headset, verify its IP, try **Restart** and reconnect VRCFT |
| Data arrives but avatar does not move | Check VRCFT output, VRChat OSC and the avatar's actual parameters; a settings page alone is not live telemetry |
| APK update fails | Check signing/version compatibility above; do not install unsigned CI artifacts |

[Open an issue](https://github.com/WolalaQAQ/PicoFacialBridge/issues) with the app version, model, PICO OS version, VRCFT/module versions, reproduction steps and a redacted error excerpt. Do not upload raw facial samples, device identifiers or signing keys.

## Privacy and security

The compatible UDP protocol is **unencrypted and unauthenticated**. Use a trusted LAN only; do not forward UDP 9030 to the Internet. The bridge forwards tracking to the connected LAN client and does not persist raw facial frames. Diagnostic tools can record biometric samples and logs: inspect and redact these before sharing.

The app preserves Android runtime permissions and system camera/sensor indicators. It does not modify system partitions, relax SELinux or bundle proprietary PICO libraries.

## Development

### Build from source

Install **JDK 17** and Android SDK command-line tools (or Android Studio). Set `JAVA_HOME` and `ANDROID_HOME` to your installations, and put `sdkmanager` on PATH. Review and accept the SDK licenses, then install the pinned packages:

```sh
sdkmanager --licenses
sdkmanager "platforms;android-35" "build-tools;35.0.0" "ndk;26.1.10909125" "cmake;3.22.1"
git clone https://github.com/WolalaQAQ/PicoFacialBridge.git
cd PicoFacialBridge
# Linux / macOS
bash ./gradlew :bridge:assembleDebug
# Windows PowerShell: .\gradlew.bat :bridge:assembleDebug
```

The wrapper uses **Gradle 8.9**, with **AGP 8.7.3**. The first build needs network access. You can also open the repository in Android Studio. Build only `:bridge` for the user app; the diagnostic `:probe` is not needed.

- Debug APK: `bridge/build/outputs/apk/debug/bridge-debug.apk` (locally debug-signed).
- `:bridge:assembleRelease` produces `bridge/build/outputs/apk/release/bridge-release-unsigned.apk`; sign it before installation. See [release signing](docs/RELEASING.md).

### Tests

With JDK 17 on PATH:

```sh
bash tests/run-host-tests.sh
pwsh -File tests/check-translations.ps1
# After building the release APK; Python 3, standard library only:
python3 tests/test_apk_distribution.py
```

Windows also has `tests/run-unit.ps1`, `run-protocol.ps1`, `run-cadence.ps1` and `run-forwarding.ps1`. They read the JDK directory from the local, ignored `tools/jdk-path.txt`; initialize it with `Set-Content tools/jdk-path.txt $env:JAVA_HOME`. Device UI tests and real headset acceptance are separate from host CI.

### Architecture and contributions

```text
PICO tracking service → Binder / read-only shared memory
                     → Android foreground service → LAN UDP
                     → PicoFacialDataModule → VRCFaceTracking → VRChat OSC
```

`bridge/` contains the app and UDP service; `shared/` contains JNI, parsing and forwarding logic; `probe/` is a developer diagnostic app; `tests/` holds host/device/network checks. See the [protocol audit](docs/UPSTREAM-AUDIT.md), [verification summary](docs/VERIFICATION.md) and [changelog](CHANGELOG.md).

Issues and pull requests are welcome. Preserve protocol compatibility and raw per-stream cadence, add tests for behavioral changes, and keep both READMEs and UI translations in sync. Never commit keys, local SDK paths, `reference/`, `evidence/` or raw biometric captures.

CI runs host tests, translation checks, Android lint and APK builds. Publishing a release additionally signs and uploads the user APK. Maintainers: follow the [release guide](docs/RELEASING.md) to configure signing secrets and version tags.

## Acknowledgements

**Thank you to [thoricelli](https://github.com/thoricelli)** for [PicoFacialDataDaemon](https://github.com/thoricelli/PicoFacialDataDaemon) and [PicoFacialDataModule](https://github.com/thoricelli/PicoFacialDataModule).

This project was developed **with reference to the original projects**, particularly their Binder calls, shared-memory data layouts and UDP protocol. Their work provided the foundation for communicating with the headset and retaining compatibility with the existing VRCFaceTracking module. PicoFacialBridge packages the headset side as a rootless Android app with its own lifecycle and forwarding implementation.

This is an independent community project, not an official PICO product or an upstream-endorsed release.

## License

**MIT**, consistent with the original projects. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the full license and retained upstream copyright notice. Both are included in bridge APKs and release assets.
