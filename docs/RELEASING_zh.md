# 构建与发布

[English](RELEASING.md) | 简体中文 · [README](../README_zh.md)

## 自动化流程

[Android CI and Release](../.github/workflows/android.yml) 工作流支持以下触发方式：

| 事件 | 结果 |
| --- | --- |
| 推送分支 / Pull Request | 主机测试、翻译检查、lint、debug/release/测试 APK 构建，并提供 Actions 附件 |
| 发布 GitHub Release（含 Pre-release） | 执行相同检查，再签名并将正式 APK 上传到该 Release |
| Actions → Run workflow，`tag` 留空 | 仅构建界面所选 ref，不签名发布，不需要签名 Secrets |
| Actions → Run workflow，填写已有 `tag` | 构建该标签对应的源码，上传到其已有 Release，可用于重试或准备草稿 |

**仅推送 tag 不会发布。** 工作流不会自动创建 Release。标签对应的提交必须包含工作流和发布测试；手动触发功能还需要工作流已存在于默认分支。

Release 附件包括：

```text
PicoFacialBridge-v<版本>-arm64-v8a.apk
SHA256SUMS.txt
LICENSE
THIRD_PARTY_NOTICES.md
```

APK 是使用仓库固定密钥签名的 **不可调试 release 构建**，只分发 `arm64-v8a` 的 bridge，不打包诊断 Probe、原始采样、PICO 私有库或 PC 模块 DLL。

Actions 的 `android-build` 附件保留 14 天，包含 debug APK、未签名 release APK、debug instrumentation 测试 APK 及许可声明。Unsigned APK 不可直接安装；CI debug 签名在不同运行器之间不固定，不是公开更新渠道；测试 APK 也不是用户应用。Lint 报告单独上传。

## 首次配置签名

Android 覆盖升级要求应用 ID 和签名身份一致。请安全备份正式 keystore 及密码，不要每次发布都生成新密钥。已有正式密钥时应复用，跳过下面的创建步骤。不要将密钥提交到 Git，也不要把密钥／密码贴进 Issue、日志或聊天。

### 1. 在仓库外创建私有密钥

确保 JDK 17 的 `keytool` 在 PATH 中。PowerShell 示例：

```powershell
New-Item -ItemType Directory -Force "$HOME/.android" | Out-Null
keytool -genkeypair -storetype JKS -keyalg RSA -keysize 3072 -validity 10000 `
  -alias bridge -keystore "$HOME/.android/pico-facial-bridge-release.jks"
```

Linux/macOS：

```sh
mkdir -p "$HOME/.android"
chmod 700 "$HOME/.android"
keytool -genkeypair -storetype JKS -keyalg RSA -keysize 3072 -validity 10000 \
  -alias bridge -keystore "$HOME/.android/pico-facial-bridge-release.jks"
chmod 600 "$HOME/.android/pico-facial-bridge-release.jks"
```

按提示交互填写信息。仓库密码和私钥密码可以相同，但下面两项密码 Secret 都需要设置。证书主体填写自己的身份／组织信息即可，没有固定要求。

### 2. 配置 GitHub Actions 仓库 Secrets

进入 **Settings → Secrets and variables → Actions → New repository secret**：

| Secret | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | 整个正式 keystore 文件的 Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore 密码 |
| `ANDROID_KEY_ALIAS` | 私钥别名，例如 `bridge` |
| `ANDROID_KEY_PASSWORD` | 私钥密码 |

PowerShell 可直接将 Base64 放进剪贴板，避免在终端输出：

```powershell
Set-Clipboard -Value ([Convert]::ToBase64String(
  [IO.File]::ReadAllBytes("$HOME/.android/pico-facial-bridge-release.jks")))
# 粘贴到 GitHub 的 Secret 输入框并保存后，清空剪贴板：
Set-Clipboard -Value ''
```

注意剪贴板历史和云同步也可能保留敏感内容。也可通过已登录的 GitHub CLI 直接传入，将 `OWNER/REPO` 换成你的仓库：

```sh
base64 < "$HOME/.android/pico-facial-bridge-release.jks" | tr -d '\n' | \
  gh secret set ANDROID_KEYSTORE_BASE64 --repo OWNER/REPO
```

不需要另配个人访问令牌 Secret，上传使用 GitHub 自带的 `GITHUB_TOKEN`。仓库／组织策略需要允许 Actions 和发布任务的 `contents: write` 权限。Fork PR 构建不会获得签名 Secrets 或写令牌。持有签名 Secrets 的 job 不检出仓库、不执行 Gradle，并在失败时也清理临时密钥。

签名 Secrets 缺失或错误时，**发布任务会失败**，不会退回 debug 签名或上传 unsigned APK。请限制修改工作流、发布标签／Release、运行特权工作流的人员权限，并按团队需要增加分支保护或环境审批。

## 发布一个版本

1. 在 `bridge/build.gradle` 更新 `versionName`，并为每个新的应用版本递增 `versionCode`。首次公开发布可使用现有的 `0.2.0` / `3`；不能只换 Release 名字，却不更新 APK 的版本信息。
2. 行为变化时同步中英文 README，并在 `CHANGELOG.md` 写明版本变化，保留真实的设备验证边界。
3. 提交并推送源码和工作流，等待分支 CI 通过。
4. 基于该提交创建 GitHub Release，标签必须严格等于 **`v` + `versionName`**，例如 `v0.2.0`。预发布 `v0.3.0-beta.1` 需要标签对应提交写有 `versionName '0.3.0-beta.1'`。草稿构建失败后若修改源码，应使用合适的新标签，不要悄悄移动已发布标签。
5. 发布 Release。Actions 构建事件对应的提交，核对 APK 内的实际版本，签名、验证签名和对齐、生成 SHA-256，再附加四个文件。运行期间 Release 页面可能暂时只有源码压缩包。
6. 检查 Actions 结果和附件。正式宣布发布前，在支持的头显上安装，核对版本和追踪功能；已有正式版本时还应验证覆盖升级。CI 无法代替真实 PICO 硬件和 Avatar 验收。

此流程在 Release 发布后添加附件，因此要求 **Release 附件可修改**。如果仓库开启了 immutable releases，不要原样使用这条路径：已锁定 Release 无法追加附件。应关闭该设置，或另外调整为先在草稿中完成附件、再锁定发布的流程。

上传失败时，可重跑失败任务，或在 **Run workflow** 中填写已有 Release 的 tag。手动路径会检出 `refs/tags/<tag>`，而不是 UI 所选分支。重试通过 `--clobber` 替换同名附件及校验和，因此只能使用原本预期的源码和同一签名密钥，不要将已有版本悄悄改作他用。

## 本地构建与检查

SDK 配置见 [README](../README_zh.md#从源码构建)。在仓库目录中，确保 JDK 17 在 PATH：

```sh
bash tests/run-host-tests.sh
pwsh -File tests/check-translations.ps1
bash ./gradlew --no-daemon :bridge:assembleDebug :bridge:assembleRelease \
  :bridge:assembleDebugAndroidTest :bridge:lintRelease
python3 tests/test_apk_distribution.py
```

Windows 使用 `gradlew.bat`、`python`（或现有 Python 可执行文件）及 README 中的 PowerShell 主机测试脚本。Lint 只豁免 `ExpiredTargetSdkVersion`：这是面向特定 Android 10 固件的侧载应用，不用于 Google Play 上架。其他 lint 错误仍阻止发布，警告本身不阻断。

`assembleRelease` 有意输出未签名 APK。本地正式签名可使用 Android Studio 的签名 APK 功能及同一固定密钥，也可使用 SDK 的 `apksigner`。密码不要写进命令行参数或历史，使用交互输入或基于环境变量的密码选项；Actions 文件提供了自动签名示例。

下载后可用 SDK 工具检查，按平台调整文件名和可执行文件后缀：

```sh
apksigner verify --verbose --print-certs PicoFacialBridge-v0.2.0-arm64-v8a.apk
sha256sum -c SHA256SUMS.txt
```

校验前请下载 `SHA256SUMS.txt` 中列出的全部文件。Windows 可用 `Get-FileHash <apk> -Algorithm SHA256` 比对相应条目。校验和只用于发现文件损坏，不能替代来源和签名身份验证。正式密钥与本机 debug 密钥不同，从测试版切换到正式版时可能需要先卸载测试版一次。

## 参考文档

- [GitHub Release 工作流事件](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#release)
- [GitHub CLI Release 附件上传](https://cli.github.com/manual/gh_release_upload)
- [Android 应用签名](https://developer.android.com/studio/publish/app-signing)
