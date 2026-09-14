# Publishing an APK

English | [简体中文](RELEASING_zh.md) · [README](../README.md)

**No keystore setup or custom GitHub Secrets are required.** This project distributes sideload APKs, not Google Play builds. Gradle's `assembleDebug` compiles and signs an installable APK automatically.

## Publish to GitHub Releases

1. Commit and push the source and [workflow](../.github/workflows/android.yml). For a new app version, update `versionName` and increment `versionCode` in `bridge/build.gradle`.
2. Create and publish a GitHub Release from that commit. Its tag must match `v` + `versionName`, for example **`v0.2.0`** for the current version. Pre-releases work too, provided the tag matches the APK version.
3. Wait for **Android CI and Release** to finish. Download from the release's **Assets**:

   ```text
   PicoFacialBridge-v0.2.0-arm64-v8a-debug.apk
   SHA256SUMS.txt
   LICENSE
   THIRD_PARTY_NOTICES.md
   ```

The workflow runs host tests, translation checks and Android lint, builds the APK, verifies its automatic signature and uploads it. The filename identifies it as a **debug build**: it is debuggable but can be installed and used normally. No manual signing or Play Console account is involved.

Uploads use GitHub's built-in `GITHUB_TOKEN`; repository/organization policy must allow Actions and the publish job's `contents: write` permission. Publishing a release, not merely pushing a tag, triggers the upload. The tagged source must include this workflow.

If you need to retry, rerun the failed job or use **Actions → Android CI and Release → Run workflow**, entering the existing release tag. Manual publishing builds that tag, not the selected branch, and replaces same-named assets. An empty tag runs CI only. Manual dispatch must be available on the default branch.

This flow adds assets after publication, so the release must remain mutable. It cannot append files to an immutable/locked release.

## Updating the app

Each CI build may generate a different debug key. If Android refuses an in-place update because signatures differ, verify the APK's source, uninstall the previous app and install the new one. **Uninstalling resets app preferences.** Do not commit private signing keys to solve this; persistent signing can be added later if seamless upgrades become a requirement.

## Local build

With the SDK and JDK configured as described in the [README](../README.md#build-from-source):

```powershell
.\gradlew.bat :bridge:assembleDebug
adb install -r .\bridge\build\outputs\apk\debug\bridge-debug.apk
```

Linux/macOS: `bash ./gradlew :bridge:assembleDebug`. The APK is automatically signed in both cases. There is no need to use `assembleRelease` or `keytool` for this distribution flow.

Branch pushes and pull requests also produce an `android-build` Actions artifact (14-day retention) containing the user debug APK, instrumentation test APK and notices. The instrumentation APK is for developer tests, not the user app. Lint reports are separate. CI does not replace real headset testing.
