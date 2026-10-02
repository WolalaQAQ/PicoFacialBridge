# PicoFacialBridge

English | [简体中文](README_zh.md)

[![Android CI](https://github.com/WolalaQAQ/PicoFacialBridge/actions/workflows/android.yml/badge.svg)](https://github.com/WolalaQAQ/PicoFacialBridge/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

PicoFacialBridge is an Android app for the PICO 4 Pro that forwards the headset's eye and face tracking data over the local network to a PC, where [UnifiedPicoModule](https://github.com/WolalaQAQ/UnifiedPicoModule) feeds it into VRCFaceTracking (VRCFT).

The app does not require root, Magisk, Shizuku or PICO Connect, does not occupy an OpenXR session, and can run alongside streaming software such as Virtual Desktop. ADB is optional for installation and debugging and is not needed for daily use.

**[Download APK](https://github.com/WolalaQAQ/PicoFacialBridge/releases) · [UnifiedPicoModule](https://github.com/WolalaQAQ/UnifiedPicoModule) · [PicoET-Enhance](https://github.com/WolalaQAQ/PicoET-Enhance) · [Report an issue](https://github.com/WolalaQAQ/PicoFacialBridge/issues)**

> Version 0.3.0 is a pre-release. It uses the BridgeSplit protocol, so the PC must run UnifiedPicoModule. The earlier PicoFacialDataModule and its forks cannot receive data from this version. If you still need the old protocol, continue to use [v0.2.0](https://github.com/WolalaQAQ/PicoFacialBridge/releases/tag/v0.2.0).

## Features

- Start and stop tracking from inside the headset. A foreground service and persistent notification keep forwarding after the app window is closed.
- Eye and face data are sent independently at their own source rates, with no smoothing or interpolation.
- Eye and face transmission can be switched separately, and the interface shows the actual send rate of each channel.
- Only the channels the PC has subscribed to are sent, and no tracking data is sent before the subscription is acknowledged.
- The tracking mode is detected automatically and displayed. A stock headset without the enhancement module runs in normal mode and forwards fused gaze and per-eye openness. When the enhancement module is active, enhanced mode additionally forwards per-eye gaze and pupil diameter.
- The interface is available in English and Chinese, and the language and transmission settings are saved.

## Normal and enhanced mode

Without any additional component on the headset, the bridge can only forward the fused gaze and the per-eye openness the firmware itself provides. This is normal mode.

On a rooted (Magisk) headset, installing [PicoET-Enhance](https://github.com/WolalaQAQ/PicoET-Enhance) makes the headset output true per-eye gaze and pupil diameter instead. The bridge detects the change automatically and switches to enhanced mode. No extra PC-side configuration is needed for this feature.

PicoET-Enhance works at runtime, modifies no system file and changes no device-wide property, but it currently supports only PICO OS 5.13.7. See that project's README for installation, mode switching and removal.

## Compatibility

| Item | Requirement |
|---|---|
| Headset | PICO 4 Pro with working eye and face tracking |
| Verified firmware | PICO OS 5.13.7 (Android 10 / API 29, arm64-v8a) |
| PC | Windows with VRCFaceTracking 5.4.5 and [UnifiedPicoModule](https://github.com/WolalaQAQ/UnifiedPicoModule) |
| Network | PC and headset on the same trusted local network, with UDP port 9030 reachable |
| VRChat | An avatar set up for face tracking, with OSC enabled |

The app depends on PICO's internal tracking service and shared-memory layout, so system updates may affect it. Other models and firmware versions have not been confirmed compatible. The app cannot provide eye or face tracking on devices without the tracking hardware.

As of 2026-10-02, the following have been verified on a PICO 4 Pro:

- channel subscription and acknowledgement with UnifiedPicoModule;
- all four combinations of the eye and face switches, with caches cleared on each change;
- pausing capture when both switches are off;
- return to neutral on the PC when data stops;
- automatic recovery after an app restart: the headset interface returns to running in about 6 seconds, and the PC re-subscribes within about 33 seconds;
- avatar expression tracking in enhanced mode;
- running alongside Virtual Desktop.

Recovery after a full headset reboot, long-term stability and avatar behavior in normal mode have not yet been verified.

## Installation and use

### 1. Install the headset app

1. Download `PicoFacialBridge-v<version>-arm64-v8a-debug.apk` from [Releases](https://github.com/WolalaQAQ/PicoFacialBridge/releases). The source ZIP on the release page is not an installation package.
2. Sideload it with your usual headset APK installer. Alternatively, enable developer mode and USB debugging on the headset, connect and authorize it, then install with [Android platform-tools](https://developer.android.com/tools/releases/platform-tools):

   ```sh
   adb install -r PicoFacialBridge-v0.3.0-arm64-v8a-debug.apk
   ```

3. Open PicoFacialBridge from the headset's 2D app list. Make sure eye and face tracking are enabled in the system settings, tap Start, and grant both tracking permissions the app requests.

Released APKs use an automatically generated debug signature that may differ between builds, so Android may refuse an in-place update. In that case, verify the APK's source, uninstall the old version and install the new one. Uninstalling clears the app's settings.

### 2. Install UnifiedPicoModule

Install the module in VRCFT by following the [UnifiedPicoModule installation guide](https://github.com/WolalaQAQ/UnifiedPicoModule#installation). Disable other PICO modules first to avoid port conflicts.

By default, the module discovers the headset automatically via multicast on the local network. If discovery fails, or if more than one headset is on the network, specify the headset address in the module's configuration file `UnifiedPicoModule.json`:

```json
"Bridge": { "IP": "192.168.1.123" }
```

Replace the example with the headset IPv4 address shown in PicoFacialBridge, then restart VRCFT. The address may change after the headset reconnects to Wi-Fi. For the location of the configuration file and the other fields, see the UnifiedPicoModule [configuration reference](https://github.com/WolalaQAQ/UnifiedPicoModule/blob/main/docs/configuration.md).

### 3. Daily use

1. Open PicoFacialBridge on the headset and tap Start.
2. Start VRCFT on the PC. Once connected, PicoFacialBridge shows a connected client and non-zero send rates, and VRCFT's Output page shows `Bridge/BridgeSplit: state=Connected`.
3. Start VRChat and enable OSC.
4. When finished, tap Stop in the app or the persistent notification.

The app does not start automatically at boot. After the headset restarts, open the app and start it again.

## Interface

| Control or status | Description |
|---|---|
| Tracking mode | Normal or enhanced mode, as detected on the headset |
| Start / Stop / Restart | Control the tracking service without ADB |
| Send eye / Send face | Control transmission of each channel separately; changes take effect immediately and are saved |
| Both off | Pauses tracking capture and data transmission while keeping discovery and control communication available |
| Eye / Face Hz | Source frames successfully sent in the last second; not the capture rate or a display frame rate |
| 0 Hz | No client, the channel is off, or there are no new samples; repeated samples and heartbeats are not counted |
| Waiting for fresh tracking data | Put on and wake the headset, and check the system tracking settings and permissions |
| English / 中文 | Switches the interface and notification language without affecting tracking |

PICO's eye and face tracking share one algorithm, so both permissions are required even when only one channel is transmitted. The transmission switches only control whether data is sent; they do not control camera power.

The channels actually sent are the intersection of the local transmission switches and the PC's subscription. When only eye data is transmitted, eye-region expressions such as blink, brows, EyeWide and EyeSquint are sent alongside the native eye data, and Face Hz shows 0. When only face data is transmitted, neither native eye data nor eye-region expressions are sent.

## Troubleshooting

| Symptom | What to check |
|---|---|
| VRCFT cannot find the headset | Whether the PC and headset are on the same network, whether the router uses a guest network or client isolation, and whether the PC has VPN or virtual adapters. Try setting `Bridge.IP` in the module configuration |
| Blocked by the firewall | Allow VRCFT to use UDP 9030 on private networks. Disabling the firewall entirely is not recommended |
| Connected but always 0 Hz | Make sure the transmission switches are on, put on and wake the headset, and check the tracking permissions and system tracking settings |
| An error or unsupported layout is shown | Open the diagnostic log and note the error, the headset model and the firmware version |
| Tracking stops after sleep or a network change | Wake the headset, check its current IP, tap Restart and reconnect VRCFT |
| Data arrives but the avatar does not move | Check VRCFT's output, VRChat OSC and the avatar parameters |
| APK update fails | See the signing note above. Download the user APK, not the instrumentation test APK |

When [opening an issue](https://github.com/WolalaQAQ/PicoFacialBridge/issues), please include the app version, headset model, PICO OS version, VRCFT and module versions, steps to reproduce, and log excerpts with personal information removed. Do not upload raw face tracking samples, device identifiers or signing keys.

## Privacy and security

The app forwards tracking data only to connected clients on the local network, does not store raw expression frames, and logs only negotiation state, rates and validity. The UDP protocol has no encryption or authentication. Use the app only on trusted networks and do not forward UDP port 9030 to the internet. The diagnostic tools may record biometric samples; review and remove sensitive content before sharing them.

The app uses standard Android runtime permissions, and the system camera and sensor indicators remain in effect. It does not modify system partitions, does not relax SELinux, and does not bundle PICO's private libraries.

## Development

### Building from source

Building requires JDK 17 and the Android SDK command-line tools (or Android Studio). Point `JAVA_HOME` and `ANDROID_HOME` at the corresponding installations and add `sdkmanager` to PATH, then accept the SDK licenses and install the pinned dependencies:

```sh
sdkmanager --licenses
sdkmanager "platforms;android-35" "build-tools;35.0.0" "ndk;26.1.10909125" "cmake;3.22.1"
git clone https://github.com/WolalaQAQ/PicoFacialBridge.git
cd PicoFacialBridge
# Linux / macOS
bash ./gradlew :bridge:assembleDebug
# Windows PowerShell
.\gradlew.bat :bridge:assembleDebug
```

The Gradle Wrapper uses Gradle 8.9 and the Android Gradle Plugin is 8.7.3. The first build requires network access. Only `:bridge` is needed for the user app; `:probe` is a developer diagnostic app. The output is `bridge/build/outputs/apk/debug/bridge-debug.apk`, signed with the local debug key and directly installable.

### Host checks

```sh
bash tests/run-host-tests.sh
pwsh -File tests/check-translations.ps1
# Requires a built debug APK; uses only the Python 3 standard library
python3 tests/test_apk_distribution.py
```

On Windows, `run-unit.ps1`, `run-protocol.ps1`, `run-cadence.ps1` and `run-forwarding.ps1` under `tests/` are also available. These scripts read the JDK directory from the local file `tools/jdk-path.txt`, which is not under version control and can be created with `Set-Content tools/jdk-path.txt $env:JAVA_HOME`. Device UI tests and real-headset acceptance are outside the scope of host CI.

### Project structure

```text
PICO tracking service → Binder / read-only shared memory
                      → Android foreground service → LAN UDP
                      → UnifiedPicoModule → VRCFaceTracking → VRChat OSC
```

| Directory | Contents |
|---|---|
| `bridge/` | User app and UDP service |
| `shared/` | JNI, data parsing and forwarding logic |
| `probe/` | Developer diagnostic app |
| `tests/` | Host, device and network checks |

See the [changelog](CHANGELOG.md) for version history and the [release guide](docs/RELEASING.md) for the release process.

### Protocol

The app and UnifiedPicoModule communicate with the BridgeSplit protocol over UDP port 9030, with discovery via the multicast address 239.255.255.250. Eye data and face data are sent as separate tagged datagrams at their own source rates, carrying only the subscribed channels and the fields the receiver actually reads. The eye stream at about 90 Hz no longer re-sends the face frame at about 23 Hz.

In normal mode, the app removes the per-eye markers that the vendor firmware derives from a fixed depth, so the receiver uses fused gaze. These markers are kept only when the enhancement module actually computes independent per-eye gaze.

For the full description of packet formats, control messages and the connection flow, see the UnifiedPicoModule [protocol reference](https://github.com/WolalaQAQ/UnifiedPicoModule/blob/main/docs/protocol.md). When the protocol changes, this app and UnifiedPicoModule must be updated together.

### Contributing

Issues and pull requests are welcome. Changes should keep the protocol compatible and preserve each channel's original send cadence, and should update both READMEs and the interface translations. Do not commit keys, local SDK paths, `reference/`, `evidence/`, raw biometric samples, or development plans, research and test records. By default, `docs/` tracks only the two public release guides.

CI runs host tests, translation checks, Android lint and APK builds. When a GitHub Release is published, CI uploads the debug-signed user APK, checksums and license notices automatically, with no custom Secrets required.

## Acknowledgements

Thanks to [thoricelli](https://github.com/thoricelli) for the original [PicoFacialDataDaemon](https://github.com/thoricelli/PicoFacialDataDaemon) and [PicoFacialDataModule](https://github.com/thoricelli/PicoFacialDataModule).

This project was developed with reference to the original projects' Binder calls, shared-memory data layouts and UDP protocol, which laid the foundation for accessing the headset's tracking service and for the original VRCFT integration. Building on that work, PicoFacialBridge implements the headset side as a rootless Android app with its own lifecycle management and forwarding, and now uses a different wire format from the original projects.

This is an independent community project. It is not an official PICO product and is not an official release by the upstream author.

## License

Consistent with the original projects, this project is licensed under [MIT](LICENSE). The full license and the retained upstream copyright notice are in [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md); both are included in the APK and the release assets.
