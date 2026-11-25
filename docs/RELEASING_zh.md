# 发布 PicoFacialBridge

[English](RELEASING.md) | **简体中文** · [README](../README_zh.md)

PicoFacialBridge 通过 GitHub Releases 分发侧载 APK，不上架 Google Play。**整个流程无需配置 keystore 或自定义 GitHub Secrets**：Gradle 的 `assembleDebug` 会自动编译并签名可安装的 APK，上传附件则使用 GitHub 内置的 `GITHUB_TOKEN`。

---

## 发布前检查清单

在打 tag 之前，请先完成以下三项准备：

1. 在 `bridge/build.gradle` 中更新 `versionName` 并递增 `versionCode`。
2. 在 `CHANGELOG.md` 和 `CHANGELOG_zh.md` 中分别添加对应的 `## [<版本>]` 章节。
3. 提交并推送全部代码更改（包含 [`.github/workflows/android.yml`](../.github/workflows/android.yml)）。

> [!IMPORTANT]
> 标签必须严格等于 `v` + `versionName`（例如 `v0.7.0`），且中英文更新日志都必须包含非空的 `## [<版本>]` 章节。任一条件不满足时，工作流会在创建 Release 前自动中止。

---

## 打标签并自动发布

在发布提交上创建并推送附注标签：

```powershell
git tag -a v0.7.0 -m "PicoFacialBridge v0.7.0"
git push origin v0.7.0
```

- 常规标签（如 `v0.7.0`）会发布为正式 Release。
- 带连字符后缀的标签（如 `v0.7.0-rc.1`）会自动标记为 **Pre-release**。

### CI 发布前自动校验步骤

**Android CI and Release**（`.github/workflows/android.yml`）按顺序执行两个任务：

1. **`build`（`ubuntu-24.04`，JDK 17，Android SDK 35 / build-tools 35.0.0 / NDK 26.1.10909125 / CMake 3.22.1）：**
   - 运行 `bash tests/run-host-tests.sh`
   - 运行静态契约检查（`test_ui_dashboard.py`、`test_inspector_frames.py`、`test_rootless_boundary.py`）
   - 运行 `tests/check-translations.ps1`
   - 执行 `./gradlew :bridge:assembleDebug :bridge:assembleDebugAndroidTest :bridge:lintDebug`
   - 运行 `tests/test_apk_distribution.py`、`apksigner verify`、`zipalign -c 4` 以及 `aapt dump badging`，核对 APK 的 `versionName` 与发布标签一致
2. **`publish`（使用 `GITHUB_TOKEN` 的 `contents: write` 权限）：**
   - 从 `CHANGELOG.md` 与 `CHANGELOG_zh.md` 提取对应版本说明写入 Release Notes
   - 生成 `SHA256SUMS.txt`，先创建草稿 Release 并上传全部附件，附件就绪后再转为公开

### 发布的 Release 附件

| 附件 | 说明 |
|---|---|
| `PicoFacialBridge-v<版本>-arm64-v8a-debug.apk` | 适用于 PICO 4 Pro（`arm64-v8a`）的 debug 签名侧载 APK |
| `SHA256SUMS.txt` | APK 与许可声明文件的 SHA-256 校验和 |
| `LICENSE` | MIT 许可证全文 |
| `THIRD_PARTY_NOTICES.md` | 保留的上游项目版权声明 |

普通分支推送和 Pull Request 也会生成两份保留 14 天的 Actions 构建产物：`android-build`（包含 `PicoFacialBridge-debug.apk`、`PicoFacialBridge-debug-androidTest.apk`、`LICENSE` 与 `THIRD_PARTY_NOTICES.md`）以及 `android-lint`。其中 `androidTest` APK 仅供开发测试，不是用户安装包。

---

## 重试或手动触发发布

需要重试失败任务，或为已有标签刷新附件时：

1. 打开 **Actions → Android CI and Release → Run workflow**（要求工作流已存在于默认分支）。
2. 在 `tag` 输入框填写已有标签（例如 `v0.7.0`）；留空则仅运行 CI 检查而不发布。

手动触发时，工作流始终检出并构建指定标签，而非当前所选分支。如果对应 Release 已存在，工作流会覆盖同名附件（`--clobber`）并保留原有说明，因此该 Release 需保持可修改状态（未锁定 / 非 immutable）。

仓库或组织设置需允许运行 Actions，并允许 `publish` 任务获取 `contents: write` 权限。

---

## Debug 签名与版本更新说明

每次 CI 运行都可能生成不同的临时 debug 签名密钥，跨版本覆盖安装时 Android 可能会提示签名不一致。此时请核实 APK 来源，先卸载旧版再安装新版（卸载会清空应用偏好设置）。切勿将私有 keystore 提交到代码仓库。

---

## 本地构建

按照 [README_zh.md](../README_zh.md#从源码构建) 配好 JDK 17 与固定版本的 Android SDK 依赖后执行：

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

Gradle 会自动完成本地 debug 签名，不需要执行 `assembleRelease` 或使用 `keytool`。
