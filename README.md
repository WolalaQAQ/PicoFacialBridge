# PicoFacialBridge

English | [简体中文](README_zh.md)

[![Android CI](https://github.com/WolalaQAQ/PicoFacialBridge/actions/workflows/android.yml/badge.svg)](https://github.com/WolalaQAQ/PicoFacialBridge/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Use your **PICO 4 Pro's eye and face tracking in VRCFaceTracking**, without rooting the headset. PicoFacialBridge is an Android app that reads the headset's tracking data and forwards it over your local network to the companion UnifiedPicoModule.

**[Download APK](https://github.com/WolalaQAQ/PicoFacialBridge/releases) · [Report an issue](https://github.com/WolalaQAQ/PicoFacialBridge/issues) · [Release guide](docs/RELEASING.md)**

## Features

- Start and stop tracking from the headset, with a foreground notification for background operation.
- Forward raw eye and face samples independently, without added smoothing or interpolation.
- Choose eye tracking, face tracking, or both; see each stream's actual transmission rate.
- Switch between English and Chinese in the app. Language and transmission preferences are saved.
- Connect to the companion UnifiedPicoModule using automatic discovery or a configured headset IP; only the channels the PC subscribes to are sent.
- Detect the tracking mode automatically and show it: **normal** on a stock, rootless headset, **enhanced** when a companion enhancement module is active. Enhanced mode forwards true per-eye gaze and real pupil diameter when the module provides them; normal mode keeps the fused gaze and the real per-eye openness the firmware exposes without the module.

No root, Magisk, Shizuku, PICO Connect or OpenXR session is required. ADB is optional for installation/debugging, not daily use. You do not need to install the original daemon alongside this app.

## Requirements and compatibility

| Component | Requirement |
| --- | --- |
| Headset | **PICO 4 Pro** with working eye and face tracking |
| Verified firmware | **PICO OS 5.13.7**, Android 10 / API 29, arm64-v8a |
| PC | Windows with [VRCFaceTracking](https://github.com/benaclejames/VRCFaceTracking) and the companion UnifiedPicoModule (see setup below) |
| Network | PC and headset on the same trusted LAN; UDP 9030 reachable |
| PCVR streaming | Tested working alongside **Virtual Desktop (VD)** |
| VRChat use | An avatar configured for face tracking and VRChat OSC enabled |

> **Experimental and firmware-specific.** Other models and firmware versions are not confirmed compatible. The bridge depends on PICO's internal tracking service and memory layout, which system updates may change. It cannot add tracking hardware to an unsupported headset.

**Using the bridge alongside Virtual Desktop (VD) has been tested and works on the supported setup.** Extended sleep/wake recovery and single-channel avatar behavior still need further testing; VD compatibility does not guarantee those separate scenarios or compatibility with other firmware.

## Quick start

> **v0.3.0 is a pre-release.** It speaks the BridgeSplit protocol, so it needs the companion UnifiedPicoModule from step 2 and is **not** a drop-in upgrade from v0.2.0. A first real-headset acceptance round passed on 2026-10-01 (subscription and ACK, all four effective-channel combinations, epoch agreement, cache clearing on switch changes, pause with both switches off, missing-frame to neutral, and application-restart recovery); a full headset reboot, the legacy daemon and long-run stability are still unverified. [v0.2.0](https://github.com/WolalaQAQ/PicoFacialBridge/releases/tag/v0.2.0) remains the latest stable release.

### 1. Install the headset app

1. Open [Releases](https://github.com/WolalaQAQ/PicoFacialBridge/releases) and download **`PicoFacialBridge-v<version>-arm64-v8a-debug.apk`** from the chosen release's **Assets**. The source-code ZIP is not an installer.
2. Sideload it with your preferred headset APK installer. Alternatively, enable developer/USB debugging, connect and authorize the headset, then use [Android platform-tools](https://developer.android.com/tools/releases/platform-tools):

   ```sh
   adb install -r PicoFacialBridge-v0.3.0-arm64-v8a-debug.apk
   ```

   Replace the example filename with the downloaded APK. No Android development environment is needed to use a release APK.
3. Open **PicoFacialBridge** in the headset's 2D apps. Enable eye/face tracking in the headset's system settings, tap **Start**, and allow **both** tracking permissions.
4. Wear/wake the headset. Leave both transmission switches on for the first connection.

**Build type and updates:** Downloads are automatically debug-signed APKs for sideloading, not Google Play builds. They are installable as downloaded; no signing setup is needed. Each CI build may use a different debug key, so Android can reject an in-place update. After verifying the download source, uninstall the old app and install the new APK if necessary (**this resets app preferences**).

### 2. Set up VRCFaceTracking

**This release uses the BridgeSplit protocol. The upstream PicoFacialDataModule, the earlier PicoFacialDataModule fork and the old daemon are incompatible.** Use the companion **UnifiedPicoModule**.

1. Build UnifiedPicoModule from source following its README (no release is published yet), producing the module ZIP.
2. In VRCFaceTracking, open **Module Registry** and use **Install Module from .zip** to select that ZIP, then fully exit and restart VRCFaceTracking. Back up existing module settings first, and do not load more than one PICO module at a time.
3. Start PicoFacialBridge and VRCFaceTracking on the same LAN. No daemon is needed. Root is optional, only for enhanced capabilities.

If discovery fails, close VRCFaceTracking and set `Bridge.IP` to the headset IPv4 shown in the bridge. Edit `UnifiedPicoModule.json` under:

```text
%APPDATA%\VRCFaceTracking\CustomLibs\8322e16d-c38b-42f0-8d35-68f27227b442\
```

```json
{
  "Bridge": {
    "IP": "192.168.1.123"
  }
}
```

This shows only the field to change: keep `ConfigVersion` and every other existing setting in the file. Replace the example IP; `null` uses discovery. Restart VRCFaceTracking after editing. The headset IP may change when Wi-Fi reconnects.

### 3. Use it

1. Open the bridge, tap **Start**, and wear the headset.
2. Start VRCFaceTracking with UnifiedPicoModule. Check for a connected client and nonzero transmission rates when tracking is active.
3. Start your usual PCVR/VRChat setup, enable **OSC** in VRChat, and use a compatible avatar. The bridge forwards tracking only; it is not a video streamer or an avatar configuration tool.
4. When finished, tap **Stop** in the app or its notification. After rebooting, open the app and start it again; there is no boot auto-start.

The bridge can run alongside Virtual Desktop. Its foreground service keeps forwarding with the app window closed, though OEM background policy or force-stop can still interrupt it.

## Controls and status

| Control / status | Meaning |
| --- | --- |
| Tracking mode | Normal or enhanced, detected on the headset; enhanced uses per-eye gaze and real pupil where the module provides them |
| Start / Stop / Restart | Control the service without ADB |
| Transmit eye / face tracking | Independent forwarding switches; changes apply immediately and persist |
| Both switches off | Pause tracking capture/data while keeping UDP discovery and control available |
| Eye / Face Hz | Distinct source frames successfully sent in the last second, not acquisition rate or video FPS |
| 0 Hz | No client, disabled stream, or no fresh samples; repeats and keepalives do not count |
| Waiting for fresh tracking | Wear/wake the headset and check tracking settings and permissions |
| 中文 / English | Change UI and notification language without restarting tracking |

Both permissions are required even in single-channel mode because PICO uses a shared algorithm. These are **not individual camera-power controls**. The receiver may hold the last gaze/expression after a stream stops; disabling transmission does not force a neutral avatar pose.

The bridge only sends the channels UnifiedPicoModule subscribes to: transmitted channels are the intersection of the local switches and the host-owned, configuration-enabled PC channels, and **no tracking data is sent before a subscription is acknowledged**. Eye-only sends an 83-byte native eye packet and a 95-byte eye morphology auxiliary packet (blink fallback, brows, EyeWide/EyeSquint), not mouth shapes. Face-only sends only a 151-byte face packet, without native eyes, eye auxiliary packets or zeroed eye placeholders. Auxiliary samples retain the original facial source cadence and **Face Hz** remains 0. Both channels use native eye and full face packets without an extra auxiliary packet. Diagnostics report subscription, effective mask and epoch. STOP, timeout and rediscovery clear subscriptions; mode announcements and heartbeats continue. With no effective channels, capture pauses while control stays available. A first hardware acceptance round passed on 2026-10-01 (application restart: headset UI back to Running in about 6 s, PC re-subscription about 33 s); a full headset reboot and the legacy daemon co-existence are still unverified.

### BridgeSplit wire format (first hardware acceptance round passed)

UDP 9030. Every datagram has a **19-byte** header: offset 0 tag (`E`/`A`/`F`), 1 effective channel mask (eye=1/face=2), 2 morphology validity (eye=1/face=2, subset of mask, always 0 for `E`), 3 positive int64 epoch, 11 positive int64 original source timestamp. Multi-byte fields are little-endian; floats are IEEE 754 binary32.

| Tag / total bytes | Payload starting at 19 |
|---|---|
| `E` / **83** | Left/right/combined uint32 status at 19/23/27; gaze float3 at 31/43/55; openness at 67/71; vendor pupil at 75/79 (PC divides by 10 for mm). Defined status bits: 0x002, 0x004, 0x100, 0x800 |
| `A` / **95** | 19 eye morphology float32; mask=1 only, face invalid |
| `F` / **151** | 33 non-eye shape float32; mask=2 only, eye validity clear; no eye placeholders |
| `F` / **227** | 52 PICO shape float32 in original order; mask=3 only |

The stable `A` slot order is `0,2,3,4,11,12,16,26,28,30,31,35,36,38,41,44,45,46,47`: EyeLookDownL, EyeLookInL, BrowInnerUp, BrowDownR, EyeLookInR, EyeLookDownR, BrowDownL, EyeSquintL, EyeBlinkL, BrowOuterUpL, EyeLookUpL, EyeLookUpR, BrowOuterUpR, EyeBlinkR, EyeSquintR, EyeLookOutL, EyeLookOutR, EyeWideR, EyeWideL. `A`/`F` retain the original facial timestamp, without synthesis or interpolation.

Face-only `F` slot order is `1,5,6,7,8,9,10,13,14,15,17,18,19,20,21,22,23,24,25,27,29,32,33,34,37,39,40,42,43,48,49,50,51`: all 52 slots except the 19 eye morphology slots above, in ascending original index order.

Exact ASCII controls, single spaces and fixed key order, no terminator (angle brackets are placeholders):

```text
PXR_MODE mode=<normal|enhance> rooted=<0|1> enhance=<0|1> gate=<0|1> plugin=<off|left|right|dual>
PXR_SUB id=<16 lowercase hex> mask=<0..3>
PXR_SUB_ACK id=<same id> mask=<effective mask> epoch=<16 lowercase hex>
```

After `DISCOVER_DAEMON`, the bridge sends `PXR_MODE` immediately, on mode changes and every 10 seconds, alongside the `MARCO\0`/`POLO` heartbeat. `PXR_SUB` (34 bytes) and `PXR_SUB_ACK` (61 bytes) are accepted only from the locked peer; id and demand stay fixed until rediscovery. Repeated requests are idempotent; another id/demand cannot replace the session. PC retries unconfirmed requests each second; the bridge ACKs immediately and repeats alongside mode announcements. Unknown fields/extra whitespace are rejected.

Epoch advances on new subscription or local switch changes. ACK clears receiver caches; stale epochs/mismatched masks cannot revive disabled channels. Both channels never add `A`, avoiding overwriting a low-rate mouth cache. All floats must be finite. A single non-finite morphology sentinel only becomes zero for that slot, without clearing the eye/face group validity; non-finite native eye fields become zero with the matching gaze/openness/pupil validity cleared, delivering invalidation rather than freezing output. Length/status bits/positive times are validated strictly. Logs expose negotiation/rates/validity, not raw samples.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| VRCFT cannot discover the headset | Correct module, same LAN, current IP, guest Wi-Fi/client isolation, VPN/virtual adapters; try direct `IP` |
| Firewall blocks the connection | Allow VRCFaceTracking on the trusted private network and UDP 9030 as needed; do not disable the whole firewall |
| Client connected but 0 Hz | Enable transmission; wear/wake the headset; allow both permissions and enable system tracking |
| `Error` or unsupported layout | Open **Show diagnostic logs**; record the error, model and firmware. Other firmware is not assumed supported |
| Tracking stops after sleep/network change | Wake the headset, verify its IP, try **Restart** and reconnect VRCFT |
| Data arrives but avatar does not move | Check VRCFT output, VRChat OSC and the avatar's actual parameters; a settings page alone is not live telemetry |
| APK update fails | Check signing/version compatibility above; download the user APK, not the instrumentation test APK |

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
- `:bridge:assembleDebug` signs automatically; install the resulting APK directly. No keystore or signing Secrets are required. See the [release guide](docs/RELEASING.md) for automated uploads.

### Tests

With JDK 17 on PATH:

```sh
bash tests/run-host-tests.sh
pwsh -File tests/check-translations.ps1
# After building the debug APK; Python 3, standard library only:
python3 tests/test_apk_distribution.py
```

Windows also has `tests/run-unit.ps1`, `run-protocol.ps1`, `run-cadence.ps1` and `run-forwarding.ps1`. They read the JDK directory from the local, ignored `tools/jdk-path.txt`; initialize it with `Set-Content tools/jdk-path.txt $env:JAVA_HOME`. Device UI tests and real headset acceptance are separate from host CI.

### Architecture and contributions

```text
PICO tracking service → Binder / read-only shared memory
                     → Android foreground service → LAN UDP
                     → UnifiedPicoModule → VRCFaceTracking → VRChat OSC
```

`bridge/` contains the app and UDP service; `shared/` contains JNI, parsing and forwarding logic; `probe/` is a developer diagnostic app; `tests/` holds host/device/network checks. Version changes are recorded in the [changelog](CHANGELOG.md).

The protocol uses UDP port **9030** and multicast discovery at **239.255.255.250**. Eye and facial updates are separate tagged datagrams sent at their own source rates, carrying only the subscribed channels and only the fields the receiver reads, so the ~90 Hz eye stream never re-sends the ~23 Hz facial frame and the unread gaze points, position guides, foveated slots and always-zero fields are dropped. A short ASCII control datagram (`PXR_MODE ...`) advertises the detected mode. Inside the eye datagram, the vendor's fixed-depth per-eye split marker is stripped in normal mode so the receiver keeps using the fused gaze; when the enhancement module is active and really computes independent per-eye gaze, the bridge leaves those validity bits intact. See [BridgeSplit wire format](#bridgesplit-wire-format-not-yet-accepted-on-hardware); the bridge and UnifiedPicoModule must be updated together.

Issues and pull requests are welcome. Preserve protocol compatibility and raw per-stream cadence, add tests for behavioral changes, and keep both READMEs and UI translations in sync. Never commit keys, local SDK paths, `reference/`, `evidence/`, raw biometric captures or intermediate plans/research/test transcripts. Only the two public release guides under `docs/` are tracked by default.

CI runs host tests, translation checks, Android lint and APK builds. Publishing a GitHub Release automatically uploads the debug-signed user APK, checksums and licenses. No custom Secrets are needed; see the [release guide](docs/RELEASING.md).

## Acknowledgements

**Thank you to [thoricelli](https://github.com/thoricelli)** for [PicoFacialDataDaemon](https://github.com/thoricelli/PicoFacialDataDaemon) and [PicoFacialDataModule](https://github.com/thoricelli/PicoFacialDataModule).

This project was developed **with reference to the original projects**, particularly their Binder calls, shared-memory data layouts and UDP protocol. Their work provided the foundation for communicating with the headset and the original VRCFaceTracking integration; this fork now uses a different wire format. PicoFacialBridge packages the headset side as a rootless Android app with its own lifecycle and forwarding implementation.

This is an independent community project, not an official PICO product or an upstream-endorsed release.

## License

**MIT**, consistent with the original projects. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the full license and retained upstream copyright notice. Both are included in bridge APKs and release assets.
