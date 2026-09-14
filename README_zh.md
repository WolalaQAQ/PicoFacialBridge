# PicoFacialBridge

[English](README.md) | 简体中文

[![Android CI](https://github.com/WolalaQAQ/PicoFacialBridge/actions/workflows/android.yml/badge.svg)](https://github.com/WolalaQAQ/PicoFacialBridge/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

让 **PICO 4 Pro 的眼动与面部追踪接入 VRCFaceTracking**，无需 Root。PicoFacialBridge 是安装在头显上的 Android 应用，读取头显追踪数据，通过局域网转发给现有的 Pico Facial Data Module。

**[下载 APK](https://github.com/WolalaQAQ/PicoFacialBridge/releases) · [反馈问题](https://github.com/WolalaQAQ/PicoFacialBridge/issues) · [发布指南](docs/RELEASING_zh.md)**

## 功能

- 在头显内启动、停止追踪，通过前台服务和常驻通知支持后台转发。
- 独立转发眼动和面部原始样本，不额外添加平滑或插值。
- 支持仅眼追、仅面捕或两路同时传输，并显示各路实际发送速率。
- 应用内切换中文、英文，保存语言和传输偏好。
- 兼容原版 **PicoFacialDataModule**，支持自动发现或指定头显 IP。

无需 Root、Magisk、Shizuku、PICO Connect，也不占用 OpenXR session。ADB 仅是安装和调试的可选工具，日常使用不需要。使用此应用时，不必另外安装原项目的 daemon。

## 使用条件与兼容性

| 组件 | 要求 |
| --- | --- |
| 头显 | 具备正常眼动、面部追踪功能的 **PICO 4 Pro** |
| 已验证固件 | **PICO OS 5.13.7**，Android 10 / API 29，arm64-v8a |
| PC | Windows，安装 [VRCFaceTracking](https://github.com/benaclejames/VRCFaceTracking) 和 [PicoFacialDataModule](https://github.com/thoricelli/PicoFacialDataModule) |
| 网络 | PC 与头显位于同一可信局域网，UDP 9030 可达 |
| VRChat | 已配置面捕的 Avatar，并启用 VRChat OSC |

> **目前仍为实验性、固件相关项目。** 尚未确认其他型号和固件兼容。本项目依赖 PICO 内部追踪服务及内存布局，系统更新可能影响可用性；无法让缺少追踪硬件的设备获得眼追或面捕。

该设备上的局域网、VRCFT 和 VRChat 核心链路已有实测。当前版本的单通道表现、长时间休眠唤醒恢复及 Virtual Desktop 并存仍需进一步真机测试。各版本证据见[验证摘要](docs/VERIFICATION.md)，历史测试结果不代表对所有环境的保证。

## 快速开始

### 1. 安装头显应用

1. 打开 [Releases](https://github.com/WolalaQAQ/PicoFacialBridge/releases)，在所选版本的 **Assets** 中下载 **`PicoFacialBridge-v<版本>-arm64-v8a-debug.apk`**。源码 ZIP 不是安装包。
2. 使用你习惯的头显 APK 安装工具侧载。也可以启用开发者／USB 调试，连接并授权头显后，通过 [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) 安装：

   ```sh
   adb install -r PicoFacialBridge-v0.2.0-arm64-v8a-debug.apk
   ```

   请将示例文件名替换为实际下载的 APK。使用 Release 安装包不需要配置 Android 开发环境。
3. 在头显的 **2D 应用**中打开 **PicoFacialBridge**。在系统设置中启用眼动和面部追踪，点击 **启动 / Start**，允许请求的**两项**追踪权限。
4. 佩戴并唤醒头显。首次连接时建议保持两项传输开关开启。

**构建类型与更新：**下载的是自动 debug 签名的侧载 APK，不用于 Google Play。下载后即可安装，不需要配置签名。不同 CI 构建可能使用不同的 debug 密钥，因此 Android 可能拒绝覆盖更新；核实下载来源后，必要时先卸载旧版再安装新版（**会清除应用偏好设置**）。

### 2. 配置 VRCFaceTracking

1. 从 [PicoFacialDataModule Releases](https://github.com/thoricelli/PicoFacialDataModule/releases) 下载模块 ZIP。
2. 在 VRCFaceTracking 的 **Module Registry** 中点击 **+**，选择该 ZIP。详情参见[原模块说明](https://github.com/thoricelli/PicoFacialDataModule#running)。
3. 在同一局域网启动 bridge 和 VRCFaceTracking，模块应自动发现头显。请使用此模块，而不是 PICO Connect / Streaming Assistant 模块。

如果发现失败，先关闭 VRCFaceTracking，将 `IP` 设置为 bridge 界面显示的头显 IPv4。在以下目录的 `PicoFacialDataModule.json` 中修改或合并这些字段：

```text
%APPDATA%\VRCFaceTracking\CustomLibs\61ee1324-fd45-42f1-9636-8e28717cf6db\
```

```json
{
  "DisableEyeTracking": false,
  "DisableFaceTracking": false,
  "IP": "192.168.1.123"
}
```

替换示例 IP，并保留文件中其他已有设置。`IP` 留空时使用自动发现。修改后重启 VRCFaceTracking；头显重新连接 Wi-Fi 后，IP 可能变化。

### 3. 日常使用

1. 打开 bridge，点击 **启动 / Start**，戴上头显。
2. 启动加载了 Pico 模块的 VRCFaceTracking。追踪活动时，确认 bridge 显示已连接的客户端和非零发送速率。
3. 启动平时使用的 PCVR／VRChat 环境，在 VRChat 中开启 **OSC**，使用兼容的 Avatar。Bridge 只负责追踪数据转发，不负责画面串流或配置 Avatar。
4. 结束时，在应用或常驻通知中点击 **停止 / Stop**。头显重启后，需要重新打开应用并启动；本项目不会开机自启。

前台服务用于在应用窗口关闭后继续转发，但系统后台策略或强制停止仍可能中断运行。与 Virtual Desktop 的并存尚未完成充分验证。

## 控件与状态

| 控件／状态 | 含义 |
| --- | --- |
| 启动 / 停止 / 重启 | 无需 ADB 即可控制追踪服务 |
| 传输眼追 / 传输面捕 | 独立控制发送，修改立即生效并保存 |
| 两项都关闭 | 暂停追踪采集和数据发送，保留 UDP 发现与控制 |
| 眼追 / 面捕 Hz | 最近一秒成功发出的不同源帧数，不是采集速率或视频 FPS |
| 0 Hz | 无客户端、通道关闭或没有新鲜样本；重复样本和心跳不计数 |
| 等待新鲜追踪数据 | 佩戴／唤醒头显，检查系统追踪设置和权限 |
| 中文 / English | 切换界面与通知语言，不重启追踪 |

PICO 共用追踪算法，因此仅传一路时仍需要两项权限。这些开关**不是独立摄像头电源开关**。接收端可能在某一路停止后保持最后的视线／表情，关闭传输不会让 Avatar 自动恢复中性姿态。

## 常见问题

| 问题 | 检查方法 |
| --- | --- |
| VRCFT 找不到头显 | 检查模块、局域网、当前 IP、访客网络／客户端隔离、VPN／虚拟网卡；尝试直接设置 `IP` |
| 被防火墙阻挡 | 按需允许 VRCFaceTracking 在可信专用网络访问 UDP 9030，不要关闭整个防火墙 |
| 已连接但一直 0 Hz | 打开传输开关，佩戴／唤醒头显，允许两项权限并开启系统追踪 |
| `Error` 或不支持的布局 | 打开**显示诊断日志**，记录错误、型号和固件；不要默认其他固件兼容 |
| 休眠／换网络后停止追踪 | 唤醒头显、核对当前 IP，尝试**重启 / Restart**并重新连接 VRCFT |
| 收到数据但 Avatar 不动 | 检查 VRCFT 输出、VRChat OSC 和 Avatar 实际参数；设置页面本身不等于实时遥测 |
| APK 更新失败 | 检查前述签名／版本兼容性；下载用户 APK，而不是 instrumentation 测试 APK |

[提交 Issue](https://github.com/WolalaQAQ/PicoFacialBridge/issues) 时，请提供应用版本、头显型号、PICO OS、VRCFT／模块版本、复现步骤及脱敏后的错误片段。不要上传原始面捕样本、设备标识或签名密钥。

## 隐私与安全

为兼容原模块，UDP 协议**没有加密和身份认证**。仅在可信局域网使用，不要将 UDP 9030 映射到公网。Bridge 向已连接的局域网客户端转发数据，不持久保存原始表情帧。诊断工具可能记录生物特征样本和日志，分享前请检查并脱敏。

应用保留 Android 运行时权限和系统摄像头／传感器指示，不修改系统分区、不放宽 SELinux，也不打包 PICO 私有库。

## 开发

### 从源码构建

安装 **JDK 17** 和 Android SDK 命令行工具（或 Android Studio），将 `JAVA_HOME`、`ANDROID_HOME` 指向对应安装目录，并将 `sdkmanager` 加入 PATH。阅读并接受 SDK 许可，然后安装固定版本的依赖：

```sh
sdkmanager --licenses
sdkmanager "platforms;android-35" "build-tools;35.0.0" "ndk;26.1.10909125" "cmake;3.22.1"
git clone https://github.com/WolalaQAQ/PicoFacialBridge.git
cd PicoFacialBridge
# Linux / macOS
bash ./gradlew :bridge:assembleDebug
# Windows PowerShell: .\gradlew.bat :bridge:assembleDebug
```

Wrapper 使用 **Gradle 8.9**，Android Gradle Plugin 为 **8.7.3**。首次构建需要联网，也可直接用 Android Studio 打开仓库。普通用户应用只需构建 `:bridge`，无需构建诊断用的 `:probe`。

- Debug APK：`bridge/build/outputs/apk/debug/bridge-debug.apk`，使用本机 debug 签名。
- `:bridge:assembleDebug` 会自动签名，产物可直接安装，无需准备 keystore 或签名 Secrets。自动上传步骤见[发布指南](docs/RELEASING_zh.md)。

### 测试

确保 JDK 17 在 PATH 中：

```sh
bash tests/run-host-tests.sh
pwsh -File tests/check-translations.ps1
# 先构建 debug APK；只需要 Python 3 标准库：
python3 tests/test_apk_distribution.py
```

Windows 也可使用 `tests/run-unit.ps1`、`run-protocol.ps1`、`run-cadence.ps1` 和 `run-forwarding.ps1`。这些脚本从本地忽略文件 `tools/jdk-path.txt` 读取 JDK 目录，可先运行 `Set-Content tools/jdk-path.txt $env:JAVA_HOME` 初始化。设备 UI 测试和真实头显验收不包含在主机 CI 中。

### 结构与贡献

```text
PICO 追踪服务 → Binder / 只读共享内存
             → Android 前台服务 → 局域网 UDP
             → PicoFacialDataModule → VRCFaceTracking → VRChat OSC
```

`bridge/` 包含应用与 UDP 服务；`shared/` 包含 JNI、解析和转发逻辑；`probe/` 是开发者诊断应用；`tests/` 包含主机、设备和网络检查。更多细节见[协议审计](docs/UPSTREAM-AUDIT.md)、[验证摘要](docs/VERIFICATION.md)和[更新日志](CHANGELOG.md)。

欢迎提交 Issue 和 Pull Request。修改时请保持协议兼容及各路原始发送节奏，为行为变化补充测试，并同步两份 README 和 UI 翻译。不要提交密钥、本机 SDK 路径、`reference/`、`evidence/` 或原始生物特征采样。

CI 会执行主机测试、翻译检查、Android lint 和 APK 构建；发布 GitHub Release 后自动上传 debug 签名的用户 APK、校验和及许可证。无需配置自定义 Secrets，步骤见[发布指南](docs/RELEASING_zh.md)。

## 致谢

衷心感谢 **[thoricelli](https://github.com/thoricelli)** 开发的原项目 [PicoFacialDataDaemon](https://github.com/thoricelli/PicoFacialDataDaemon) 和 [PicoFacialDataModule](https://github.com/thoricelli/PicoFacialDataModule)。

本项目在开发过程中**参考了原项目**，尤其是 Binder 调用、共享内存数据布局和 UDP 协议。原作者的工作为访问头显追踪服务、保持与现有 VRCFaceTracking 模块兼容奠定了基础。PicoFacialBridge 将头显端实现为无需 Root 的 Android 应用，并提供自身的生命周期管理和数据转发实现。

这是独立的社区项目，不是 PICO 官方产品，也不代表上游作者的官方发布。

## 许可证

与原项目一致，本项目采用 **MIT 协议**。完整许可和保留的上游版权声明见 [LICENSE](LICENSE) 与 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。两份声明同时包含在 bridge APK 和 Release 附件中。
