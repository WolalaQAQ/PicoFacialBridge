# Changelog

English | [简体中文](CHANGELOG_zh.md)

## [0.3.1] - 2026-10-03

### Fixed
- Monitor each required tracking source independently so a healthy stream cannot hide a stalled one from automatic reconnection. Eye-only mode also monitors the auxiliary morphology source; face-only mode does not wait for native eye data. Startup and channel changes retain a ten-second grace interval.
- Release the diagnostic probe's shared-memory mapping and file descriptor if writing the initial ring snapshot fails.
- Distinguish locally disabled transmission from a lack of overlap with the PC subscription in the English and Chinese pause messages.

### Validation
- All 398 existing host checks, translation checks, Bridge/Probe debug builds, Bridge lint and APK distribution checks pass.
- Single-source stalls, diagnostic write failures and the updated headset UI have not yet been revalidated on hardware.

## [0.3.0] - 2026-10-01
### Features
- With UnifiedPicoModule, expose the four eye-expression outputs left unmapped by the upstream PicoFacialDataModule parser: `EyeWideLeft/Right` (wide eyes) and `EyeSquintLeft/Right` (squinting). Bridge v0.2.0 already forwarded the corresponding PICO fields; v0.3.0 carries them in facial `F` packets or eye-only auxiliary `A` packets, and the companion module maps them to VRCFT. These mappings do not require enhanced mode or the enhancement module.
- Fix split-stream recovery: eye validity no longer depends on the cached facial validity flag; expire facial-derived eye data after 250 ms and clear it on reconnect.
- Detect the tracking mode automatically on the headset (normal when rootless or without an active enhancement module, enhanced when rooted with one) and show it in the app.
- Advertise the detected mode to the receiver on a separate `PXR_MODE` control datagram, sent on every accepted discovery (including same-endpoint reconnects), whenever the detected mode changes, and at least every 10 seconds as a keep-alive (instead of a fixed 2-second repeat).
- Speak the BridgeSplit protocol instead of one fixed 536-byte packet: the PC subscribes with `PXR_SUB` after `PXR_MODE`, and the bridge sends nothing until the subscription is acknowledged. Transmitted channels are the local switches AND the PC request, with an epoch that advances on every change.
- Send eye and facial updates as separate tagged datagrams with a 19-byte header, at their own source rates and carrying only the subscribed fields: `E` 83 bytes of native eyes, `A` 95 bytes of eye morphology for eye-only (blink fallback, brow, EyeWide/EyeSquint; Face Hz stays 0), `F` 151 bytes for face-only and 227 bytes for both. The eye stream (~90 Hz) no longer re-sends the facial frame (~23 Hz), and the unread gaze points, position guides, foveated slots and always-zero fields are dropped.
- Zero a single non-finite morphology value without invalidating its whole eye/face group; zero non-finite native eye fields with their own validity cleared.
- Keep the vendor's per-eye validity bits only when the enhancement module really computes independent per-eye gaze; strip them otherwise, as before.
### Design Rationale
- The enhancement module publishes the state it actually applied as transient namespaced properties, so a removed or disabled module can never leave a stale "enhanced" behind and no persistent system property is written.
- The mode is a hint, not a trust boundary: the receiver still checks the per-eye and pupil validity bits on every sample.
- Eye and facial tracking update at very different rates, so the old combined packet re-sent the unchanged half on every update of the other half. Splitting the streams removes that duplication and keeps the fast eye path independent of the slow facial model.
- The eye parser still needs the slow-rate facial blendshapes (blink fallback, EyeWide, EyeSquint, brow), so eye-only sends them as a compact auxiliary packet at their source rate instead of a full facial frame.
- The PC subscribes only after `PXR_MODE`, so it never sends unknown control to the legacy daemon, which treats any non-POLO heartbeat reply as a failure.
### Notes & Caveats
- A first real-headset acceptance round passed on 2026-10-01 (subscription and ACK, all four effective-channel combinations, epoch agreement, cache clearing on switch changes, pause with both switches off, missing-frame to neutral, and application-restart recovery: headset UI about 6 s, PC re-subscription about 33 s). Enhanced-mode avatar output was verified on 2026-10-02. A full headset reboot, legacy-daemon co-existence and long-run stability are still unverified.
- The wire format is a breaking change: the bridge and the companion [UnifiedPicoModule](https://github.com/WolalaQAQ/UnifiedPicoModule) must be updated together. The upstream module, the earlier PicoFacialDataModule fork and the stock 536-byte `picofacialdatadaemon` framing are no longer spoken.
- Mode detection is firmware-specific and reads no user data. Enhanced mode requires the companion enhancement module; without it the app behaves exactly as before.

## [0.2.0] - 2026-09-15
### Features
- Add independent, persistent eye/face transmission switches, including both-off pause and hot changes without restarting the service.
- Show separate successful-send eye/face source-frame Hz over the trailing second; held samples and keepalive packets do not inflate rates.
- Add switchable English/Chinese UI, statuses and foreground notification, with a saved language preference.
- Add GitHub Actions host tests, translation checks, Android lint and automatically debug-signed APK builds on branch pushes and pull requests.
- Attach an automatically debug-signed arm64 bridge APK, SHA-256 checksums and license notices when a GitHub Release is published, with manual retry for an existing release tag.
- Rewrite separate English/Chinese READMEs for users and developers; add bilingual maintainer release guides.
- Adopt MIT licensing, explicitly credit thoricelli's original projects, and include both project and upstream notices in bridge APKs.
- Document tested Virtual Desktop coexistence and keep intermediate development records out of the public file tree.
### Design Rationale
- Preserve the 536-byte PC module protocol. Use its existing VIDEO_INPUT_EYE / VIDEO_INPUT_FACE validity flags and clear disabled data, including eye-related fields inside the facial prefix.
- Eye-only mode retains raw blink/brow fields consumed by the stock eye parser. Enabled channels do not wait for a missing or stale other channel; no interpolation or smoothing is introduced.
- Transmission selection is separate from the shared PICO capture algorithm and PC module configuration; the receiver-side DisableEyeTracking / DisableFaceTracking settings are unchanged.
- Use Gradle's automatic debug signing for simple sideload distribution, with no keystore setup or custom Secrets. Keep the upload job's write token separate from the build.
- Verify the tag against the APK's embedded version; build manual releases from the requested tag, not the workflow UI's selected branch.
- Keep the existing Android 10 runtime target and exempt only the Google Play target-SDK lint check; all other lint errors remain fatal.
### Notes & Caveats
- Both switches default on; both off releases the capture subscription while leaving discovery/control handling available. Single-channel transmission still uses the shared algorithm and requires both PICO runtime permissions.
- Stock VRCFT parsers skip invalid channels and may retain their last output: disabling transmission does not reset an avatar to neutral or toggle module capability indicators.
- Single-channel avatar behavior requires further testing; disabling a channel is not a neutral-pose reset.
- No signing Secrets are required. Post-publication uploads require mutable release assets; tag pushes alone do not create releases.
- Different CI builds may use different debug keys, so updating can require uninstalling the old app and resetting its preferences. These are debuggable sideload APKs, not Google Play builds.
- Virtual Desktop coexistence has been tested successfully on the supported setup. CI does not replace device or avatar testing. No tracking/protocol behavior is changed in this update.

## [0.1.1] - 2026-09-14
### Features
- Remove the eye-and-face simultaneous-update gate; forward either source's new raw samples without interpolation or smoothing.
- Shorten control polling stalls and drain retained shared-memory history instead of skipping straight to the latest slot.
- Add publication-race checks, explicit ring-overrun reporting and per-client ordered SHA-256 delivery accounting without changing the 536-byte protocol.
### Design Rationale
- Eye and face sensors update at different rates. Waiting for both discarded faster eye updates.
- Keep the other channel's last real sample unchanged; do not fabricate intermediate facial values to imitate a higher source rate.
- Compare complete sender and receiver sessions, not packet lengths alone, when assessing loss.
### Notes & Caveats
- Transmission rates depend on sensor output and scheduling. Packet accounting does not guarantee against network loss, overwritten history after long stalls, or downstream rendering/sampling limits.

## [0.1.0] - 2026-09-14
### Features
- Ordinary-UID arm64 Binder probe with linker, permissions, transaction, FD, mmap and tracking diagnostics.
- Rootless foreground facial bridge with Start/Stop/Restart, live status, bounded logs and original UDP discovery/control/data framing.
- Firmware-specific ring/field validation, deterministic FD/mmap cleanup and stale/dead-session recovery.
- Reproducible Windows/Conda-assisted Android build and protocol/resource regression tests.
### Design Rationale
- Public libbinder_ndk transport plus Java Parcel's raw-FD reader avoids private framework-library loading without changing UID/domain or creating an OpenXR session.
- Preserve 384 face bytes + 152 eye bytes (536 total) rather than redesigning the module protocol.
- Keep freshness separate from gaze validity so blinking does not suppress facial updates.
- Restrict unsupported firmware layouts explicitly rather than silently misinterpreting biometric data.
### Notes & Caveats
- Experimental debug-signed APK targeting PICO OS 5.13.7 / Android 10; other firmware is not confirmed compatible.
- Existing unencrypted/unauthenticated LAN protocol requires a trusted network; VPN adapters can interfere with discovery.
- No root, system-partition changes, SELinux changes, bootloader unlock, or PICO Connect dependency introduced.
