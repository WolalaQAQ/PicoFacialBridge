# Releasing PicoFacialBridge

**English** | [简体中文](RELEASING_zh.md) · [README](../README.md)

PicoFacialBridge distributes sideload APKs via GitHub Releases rather than Google Play. **No keystore setup or custom GitHub Secrets are required:** Gradle's `assembleDebug` builds and signs an installable APK automatically, and release uploads use GitHub's built-in `GITHUB_TOKEN`.

---

## Pre-Release Checklist

Before tagging a release:

1. Update `versionName` and increment `versionCode` in `bridge/build.gradle`.
2. Add a matching `## [<version>]` section to both `CHANGELOG.md` and `CHANGELOG_zh.md`.
3. Commit and push all changes, including [`.github/workflows/android.yml`](../.github/workflows/android.yml).

> [!IMPORTANT]
> The release tag must equal `v` + `versionName` (for example `v0.7.0`), and both `CHANGELOG.md` and `CHANGELOG_zh.md` must contain a non-empty `## [<version>]` section. If either check fails, the workflow aborts before creating a release.

---

## Tag & Publish

Push an annotated tag pointing at the release commit:

```powershell
git tag -a v0.7.0 -m "PicoFacialBridge v0.7.0"
git push origin v0.7.0
```

- Standard tags (`v0.7.0`) publish as a full GitHub Release.
- Tags with a hyphenated suffix (`v0.7.0-rc.1`) publish automatically as a **Pre-release**.

### What CI Verifies Before Publishing

The **Android CI and Release** workflow (`.github/workflows/android.yml`) runs two sequential jobs:

1. **`build` (`ubuntu-24.04`, JDK 17, Android SDK 35 / build-tools 35.0.0 / NDK 26.1.10909125 / CMake 3.22.1):**
   - Runs `bash tests/run-host-tests.sh`
   - Runs static contract checks (`test_ui_dashboard.py`, `test_inspector_frames.py`, `test_rootless_boundary.py`)
   - Runs `tests/check-translations.ps1`
   - Runs `./gradlew :bridge:assembleDebug :bridge:assembleDebugAndroidTest :bridge:lintDebug`
   - Runs `tests/test_apk_distribution.py`, `apksigner verify`, `zipalign -c 4`, and `aapt dump badging` to confirm the APK's `versionName` matches the release tag
2. **`publish` (`contents: write` via `GITHUB_TOKEN`):**
   - Extracts the matching English and Chinese sections from `CHANGELOG.md` and `CHANGELOG_zh.md` into the release notes
   - Generates `SHA256SUMS.txt` and creates the release as a draft, uploads all assets, then marks it published

### Published Release Assets

| Asset | Description |
|---|---|
| `PicoFacialBridge-v<version>-arm64-v8a-debug.apk` | Debug-signed sideload APK for PICO 4 Pro (`arm64-v8a`) |
| `SHA256SUMS.txt` | SHA-256 checksums for the APK and notice files |
| `LICENSE` | MIT License |
| `THIRD_PARTY_NOTICES.md` | Retained upstream copyright notice |

Branch pushes and pull requests also upload two Actions artifacts (14-day retention): `android-build` (containing `PicoFacialBridge-debug.apk`, `PicoFacialBridge-debug-androidTest.apk`, `LICENSE`, and `THIRD_PARTY_NOTICES.md`) and `android-lint`. The `androidTest` APK is for developer instrumentation tests, not end users.

---

## Retrying or Running Manually

To retry a failed publish or refresh assets for an existing tag:

1. Open **Actions → Android CI and Release → Run workflow** (workflow must exist on the default branch).
2. Enter the existing tag (for example `v0.7.0`) in the `tag` input. Leaving `tag` blank runs CI only without publishing.

Manual dispatch always checks out and builds the specified tag rather than the selected branch. If the GitHub Release already exists, the workflow replaces same-named assets (`--clobber`) and keeps the existing release notes, so the release must remain mutable (not locked or immutable).

Repository or organization settings must permit Actions and grant the `publish` job `contents: write`.

---

## Debug Signing & App Updates

Each CI runner generates an ephemeral debug signing key, so Android may reject an in-place upgrade when signatures differ between releases. Users should verify the APK source, uninstall the older version, and install the new one (uninstalling clears saved preferences). Do not commit private keystores to the repository.

---

## Local Build

With JDK 17 and the pinned Android SDK components configured as described in [README.md](../README.md#building-from-source):

```powershell
# Windows PowerShell
.\gradlew.bat :bridge:assembleDebug
adb install -r .\bridge\build\outputs\apk\debug\bridge-debug.apk
```

```sh
# Linux / macOS
bash ./gradlew :bridge:assembleDebug
adb install -r bridge/build/outputs/apk/debug/bridge-debug.apk
```

Gradle automatically signs the local debug APK; `assembleRelease` and `keytool` are not needed.
