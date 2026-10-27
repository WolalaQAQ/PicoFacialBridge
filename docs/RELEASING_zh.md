# 发布 APK

[English](RELEASING.md) | 简体中文 · [README](../README_zh.md)

**无需配置 keystore 或自定义 GitHub Secrets。** 本项目分发侧载 APK，不上 Google Play。Gradle 的 `assembleDebug` 会直接编译并自动签名，生成可安装的 APK。

## 发布到 GitHub Releases

1. 提交并推送源码及[工作流](../.github/workflows/android.yml)。发布新的应用版本时，在 `bridge/build.gradle` 更新 `versionName` 并递增 `versionCode`，并在 `CHANGELOG.md` 和 `CHANGELOG_zh.md` 中都加上 `## [<版本>]` 一节。
2. 在该提交上推送 tag。标签必须等于 `v` + `versionName`，例如当前版本使用 **`v0.6.0`**：

   ```powershell
   git tag -a v0.6.0 -m "PicoFacialBridge v0.6.0"
   git push origin v0.6.0
   ```

   带后缀的标签（例如 `v0.6.0-rc.1`）会发布为 Pre-release；构建仍需实机验收时可以这样用。
3. 等待 **Android CI and Release** 运行完成。它会创建标题为 `PicoFacialBridge v0.6.0` 的 Release，说明为中英文更新日志中对应版本的内容。从该 Release 的 **Assets** 下载：

   ```text
   PicoFacialBridge-v0.6.0-arm64-v8a-debug.apk
   SHA256SUMS.txt
   LICENSE
   THIRD_PARTY_NOTICES.md
   ```

工作流会执行主机测试、界面源码检查、翻译检查和 Android lint，构建 APK、验证自动签名，全部通过后才创建 Release，附件上传完成再公开。文件名明确标注 **debug 构建**：它允许调试，但可以正常安装和使用。不需要手动签名，也不需要 Play Console 账号。标签与 `versionName` 不一致，或更新日志缺少该版本的一节时，工作流会在发布前失败。

上传使用 GitHub 自带的 `GITHUB_TOKEN`；仓库／组织策略需要允许 Actions 和发布任务的 `contents: write` 权限。标签对应的源码必须包含此工作流。

需要重试时，重跑失败任务，或进入 **Actions → Android CI and Release → Run workflow**，填写已有的 tag。手动发布构建的是该标签，而不是所选分支。Release 已存在时，替换同名附件并保留原有说明；不存在时创建 Release。tag 留空只运行 CI。手动触发功能需要工作流已存在于默认分支。

对已存在的 Release 重试时，附件在发布后追加，因此该 Release 必须可修改；无法向 immutable／已锁定的 Release 追加文件。

## 更新应用

不同 CI 构建可能生成不同的 debug 密钥。如果 Android 因签名不一致拒绝覆盖更新，请先核实 APK 来源，再卸载旧版并安装新版。**卸载会清除应用偏好设置。** 不要为此把私有签名密钥提交到仓库；以后确实需要无缝覆盖升级时，再增加固定签名即可。

## 本地编译

按照 [README](../README_zh.md#从源码构建) 配好 SDK 和 JDK 后：

```powershell
.\gradlew.bat :bridge:assembleDebug
adb install -r .\bridge\build\outputs\apk\debug\bridge-debug.apk
```

Linux/macOS 使用 `bash ./gradlew :bridge:assembleDebug`，同样自动签名。这条分发流程不需要使用 `assembleRelease` 或 `keytool`。

推送分支和 Pull Request 也会生成 `android-build` Actions 附件，保留 14 天，包含用户 debug APK、instrumentation 测试 APK 和许可声明。Instrumentation APK 仅用于开发测试，不是用户应用；lint 报告单独提供。CI 不能代替真实头显测试。
