## [Unreleased] - 2026-09-15
### Features
- Add GitHub Actions host tests, translation checks, Android lint and debug/release APK builds on branch pushes and pull requests.
- Sign and attach an arm64 bridge APK, SHA-256 checksums and license notices when a GitHub Release is published, with manual retry for an existing release tag.
- Rewrite separate English/Chinese READMEs for users and developers; add bilingual maintainer release guides.
- Adopt MIT licensing, explicitly credit thoricelli's original projects, and include both project and upstream notices in bridge APKs.
### Design Rationale
- Keep signing in a separate job using persistent repository Secrets so public updates retain their signing identity; missing secrets never fall back to an unsigned or debug release.
- Verify the tag against the APK's embedded version; build manual releases from the requested tag, not the workflow UI's selected branch.
- Keep the existing Android 10 runtime target and exempt only the Google Play target-SDK lint check; all other lint errors remain fatal.
### Notes & Caveats
- Maintainers must configure four signing Secrets before publishing. Post-publication uploads require mutable release assets; tag pushes alone do not create releases.
- The first migration from a local/debug signature to the public release key may require uninstalling the debug app and resetting its preferences.
- CI does not certify hardware, firmware, Virtual Desktop coexistence or avatar behavior. No tracking/protocol behavior is changed in this update.

## [0.2.0] - 2026-09-14
### Features
- Add independent, persistent eye/face transmission switches, including both-off pause and hot changes without restarting the service.
- Show separate successful-send eye/face source-frame Hz over the trailing second; held samples and keepalive packets do not inflate rates.
- Add switchable English/Chinese UI, statuses and foreground notification, with a saved language preference.
### Design Rationale
- Preserve the 536-byte PC module protocol. Use its existing VIDEO_INPUT_EYE / VIDEO_INPUT_FACE validity flags and clear disabled data, including eye-related fields inside the facial prefix.
- Eye-only mode retains raw blink/brow fields consumed by the stock eye parser. Enabled channels do not wait for a missing or stale other channel; no interpolation or smoothing is introduced.
- Transmission selection is separate from the shared PICO capture algorithm and PC module configuration. Do not modify the installed module DLL or its receiver-side DisableEyeTracking / DisableFaceTracking settings.
### Notes & Caveats
- Both switches default on; both off releases the capture subscription while leaving discovery/control handling available. Single-channel transmission still uses the shared algorithm and requires both PICO runtime permissions.
- Stock VRCFT parsers skip invalid channels and may retain their last output: disabling transmission does not reset an avatar to neutral or toggle module capability indicators.
- 62 selection/rate/UDP-loopback checks, 318 existing core/protocol/cadence checks, 40 bilingual resource checks, and 15 PICO device UI/persistence checks pass.
- New live, worn-headset single-channel delivery rates and avatar behavior remain to be verified. Prior 0.1.1 live measurements are not 0.2.0 acceptance results.
- Offline APK/test builds pass. Android lint was not available offline because lint-gradle:31.7.3 is not cached; no whole-application coverage percentage is claimed.

## [0.1.1] - 2026-09-14
### Features
- Remove the eye-and-face simultaneous-update gate; forward either source's new raw samples without interpolation or smoothing.
- Shorten control polling stalls and drain retained shared-memory history instead of skipping straight to the latest slot.
- Add publication-race checks, explicit ring-overrun reporting and per-client ordered SHA-256 delivery accounting without changing the536-byte protocol.
### Design Rationale
- Real firmware snapshots show eye updates around90Hz and facial updates around23Hz. Waiting for both discarded faster eye updates.
- Keep the other channel's last real sample unchanged; do not fabricate intermediate facial values to imitate a higher source rate.
- Compare complete sender and receiver sessions, not packet lengths alone, when assessing loss.
### Notes & Caveats
- Live60-second audit: sent6798 / received6798, identical ordered SHA-256, zero reported ring-history overruns.
- Steady log interval: eye90.014Hz, face23.287Hz, UDP113.234Hz. Rates vary with the sensors and scheduling.
- 259 cadence/history/race tests plus59 existing checks pass. Original module DLL unchanged; VRCFT Data Filter remains inactive.
- This is observed loss-free delivery in the tested session, not a guarantee against arbitrary network loss, overwritten history after long stalls, or downstream rendering/sampling limits.

## [0.1.0] - 2026-09-14
### Features
- Ordinary-UID arm64 Binder probe with linker, permissions, transaction, FD, mmap and tracking diagnostics.
- Rootless foreground facial bridge with Start/Stop/Restart, live status, bounded logs and original UDP discovery/control/data framing.
- Firmware-specific ring/field validation, deterministic FD/mmap cleanup and stale/dead-session recovery.
- Reproducible Windows/Conda-assisted Android build and protocol/resource regression tests.
### Design Rationale
- Public libbinder_ndk transport plus Java Parcel's raw-FD reader avoids private framework-library loading without changing UID/domain or creating an OpenXR session.
- Preserve 384 face bytes +152 eye bytes (536 total) rather than redesigning the module protocol.
- Keep freshness separate from gaze validity so blinking does not suppress facial updates.
- Restrict unsupported firmware layouts explicitly rather than silently misinterpreting biometric data.
### Notes & Caveats
- Experimental personal debug-signed APK, tested on PICO OS5.13.7 / Android10 only.
- Direct Binder, changing local tracking and continuous UDP motion verified (1406packets/60seconds); Virtual Desktop coexistence and actual VRCFT/VRChat integration remain pending.
- Existing unencrypted/unauthenticated LAN protocol requires a trusted network; VPN adapters can interfere with discovery.
- No root, system-partition changes, SELinux changes, bootloader unlock, or PICO Connect dependency introduced.
