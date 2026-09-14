# Building and publishing releases

English | [简体中文](RELEASING_zh.md) · [README](../README.md)

## What runs automatically

The [Android CI and Release workflow](../.github/workflows/android.yml) runs on:

| Event | Result |
| --- | --- |
| Branch push / pull request | Host tests, translation checks, lint, debug/release/test APK builds; downloadable Actions artifacts |
| Publish a GitHub Release (including a pre-release) | The same checks, then sign the release APK and upload it to that release |
| Actions → Run workflow, empty `tag` | CI-only build of the selected ref; no signing secrets required |
| Actions → Run workflow, existing `tag` | Build that exact tag and upload to its existing release; useful for retrying or preparing a draft |

Pushing a tag alone does **not** publish anything. The workflow never creates a release automatically. The tag's commit must include the workflow and distribution tests. Manual dispatch must also be available on the default branch.

Release assets are:

```text
PicoFacialBridge-v<version>-arm64-v8a.apk
SHA256SUMS.txt
LICENSE
THIRD_PARTY_NOTICES.md
```

The APK is a **non-debuggable release build**, signed with the repository's persistent key. Only `arm64-v8a` and the bridge are distributed; diagnostic probes, raw captures, proprietary PICO libraries and the PC module DLL are not bundled.

Actions' `android-build` artifact contains a debug APK, an unsigned release APK and a debug instrumentation test APK, plus license notices. It expires after 14 days. The unsigned APK is not installable; CI debug signatures are not stable between runners and are not the public update channel. The test APK is not the user app. Lint reports are uploaded separately.

## One-time signing setup

Android updates require the same application ID and signing identity. Back up the release keystore and its passwords securely; do not regenerate it for each release. If you already have a production key, reuse it rather than following the creation step below. Never commit a keystore or paste a key/password into an issue, log or chat.

### 1. Create a private key outside the repository

Use JDK 17's `keytool` on PATH. For example, in PowerShell:

```powershell
New-Item -ItemType Directory -Force "$HOME/.android" | Out-Null
keytool -genkeypair -storetype JKS -keyalg RSA -keysize 3072 -validity 10000 `
  -alias bridge -keystore "$HOME/.android/pico-facial-bridge-release.jks"
```

On Linux/macOS:

```sh
mkdir -p "$HOME/.android"
chmod 700 "$HOME/.android"
keytool -genkeypair -storetype JKS -keyalg RSA -keysize 3072 -validity 10000 \
  -alias bridge -keystore "$HOME/.android/pico-facial-bridge-release.jks"
chmod 600 "$HOME/.android/pico-facial-bridge-release.jks"
```

Answer the interactive prompts. Store/key passwords can be the same, but both Secrets below must be set. Use your own organization/identity information; no particular certificate subject is required.

### 2. Configure GitHub Actions repository Secrets

Open **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 of the entire release keystore file |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias, e.g. `bridge` |
| `ANDROID_KEY_PASSWORD` | Private-key password |

To avoid printing Base64 in a terminal, PowerShell can copy it directly to the clipboard:

```powershell
Set-Clipboard -Value ([Convert]::ToBase64String(
  [IO.File]::ReadAllBytes("$HOME/.android/pico-facial-bridge-release.jks")))
# Paste into the GitHub secret value, save it, then clear the clipboard:
Set-Clipboard -Value ''
```

Treat clipboard history/synchronization as sensitive, or use an authenticated GitHub CLI pipe instead (replace `OWNER/REPO` with your repository):

```sh
base64 < "$HOME/.android/pico-facial-bridge-release.jks" | tr -d '\n' | \
  gh secret set ANDROID_KEYSTORE_BASE64 --repo OWNER/REPO
```

No personal access token Secret is needed: release uploads use GitHub's built-in `GITHUB_TOKEN`. Repository/organization policy must permit Actions and the publish job's `contents: write` permission. Fork PR builds do not receive signing Secrets or a write token. The signing job does not check out the repository or run Gradle, and deletes the temporary key even on failure.

Missing or incorrect signing Secrets **fail the publish job**; there is no debug-key or unsigned fallback. Limit who can change workflows, publish tags/releases and run privileged workflows; add branch protection or environment approvals according to your team's needs.

## Publish a version

1. In `bridge/build.gradle`, update `versionName` and increment `versionCode` for each new app version. The initial public release may use the existing `0.2.0` / `3`. Do not relabel unchanged APK metadata as a newer version.
2. Update both READMEs if behavior changed and add the release entry to `CHANGELOG.md`. Keep device-validation limits accurate.
3. Commit and push the source, including this workflow. Wait for branch CI to pass.
4. Create a GitHub Release from that commit with a tag exactly matching **`v` + `versionName`**, for example `v0.2.0`. A pre-release such as `v0.3.0-beta.1` requires `versionName '0.3.0-beta.1'` in that tagged commit. For a new commit after a failed draft build, use a new appropriate tag instead of silently moving an already published tag.
5. Publish the release. Actions builds the release event's commit, verifies the version embedded in the APK, signs, verifies the signature/alignment, generates SHA-256 checksums and attaches the four assets. The release page may initially show only source archives while the job runs.
6. Check the Actions result and assets. Before announcing it, install on a supported headset, verify the displayed version and test tracking, including upgrades from the previous release where applicable. CI cannot test real PICO hardware or the user's avatar.

This post-publication upload flow requires **mutable release assets**. If your repository enables immutable releases, do not use the published-event upload path as-is: it cannot modify a locked release. Disable that setting for this flow, or adapt publishing to finish assets in a draft before locking it.

To retry a failed upload, rerun the failed job, or use **Run workflow** with the existing release tag. The manual path checks out `refs/tags/<tag>`, not the branch selected in the UI. Uploads replace same-named assets (`--clobber`), including checksums, so only retry with the same intended source and signing key. Existing releases/tags must not be silently repurposed.

## Local build and verification

See the [README build instructions](../README.md#build-from-source) for SDK setup. From the repository, with JDK 17 on PATH:

```sh
bash tests/run-host-tests.sh
pwsh -File tests/check-translations.ps1
bash ./gradlew --no-daemon :bridge:assembleDebug :bridge:assembleRelease \
  :bridge:assembleDebugAndroidTest :bridge:lintRelease
python3 tests/test_apk_distribution.py
```

On Windows use `gradlew.bat`, `python` (or your existing Python executable), and the PowerShell host test scripts described in the README. Lint excludes only `ExpiredTargetSdkVersion`: this firmware-specific Android 10 app is sideloaded, not submitted to Google Play. Other lint errors remain fatal; warnings do not by themselves block publishing.

`assembleRelease` deliberately produces an unsigned APK. For a local signed build, use Android Studio's signed APK flow with the same persistent key, or run the SDK's `apksigner` on the unsigned output. Keep passwords out of command-line arguments/history: use interactive prompts or environment-backed password options. The Actions file contains the automated signing example.

Verify a downloaded APK with the SDK tools, adjusting the filename and executable suffix for your platform:

```sh
apksigner verify --verbose --print-certs PicoFacialBridge-v0.2.0-arm64-v8a.apk
sha256sum -c SHA256SUMS.txt
```

Download all files listed in `SHA256SUMS.txt` before checking it. Windows can use `Get-FileHash <apk> -Algorithm SHA256` and compare it to the corresponding checksum. A checksum detects corruption; it does not replace checking the download source and signing identity. An official release key will differ from a local debug key, requiring a one-time uninstall of the debug app before switching channels.

## References

- [GitHub release workflow events](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#release)
- [GitHub CLI release uploads](https://cli.github.com/manual/gh_release_upload)
- [Android app signing](https://developer.android.com/studio/publish/app-signing)
