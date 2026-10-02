# PicoFacialBridge

[English](README.md) | 简体中文

[![Android CI](https://github.com/WolalaQAQ/PicoFacialBridge/actions/workflows/android.yml/badge.svg)](https://github.com/WolalaQAQ/PicoFacialBridge/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

PicoFacialBridge 是运行在 PICO 4 Pro 上的 Android 应用，用于将头显的眼动与面部追踪数据通过局域网转发至 PC，再由 [UnifiedPicoModule](https://github.com/WolalaQAQ/UnifiedPicoModule) 接入 VRCFaceTracking（VRCFT）。

本应用无需 Root、Magisk、Shizuku 或 PICO Connect，也不占用 OpenXR 会话，可以与 Virtual Desktop 等串流软件同时使用。ADB 仅在安装和调试时可选使用，日常使用不需要。

**[下载 APK](https://github.com/WolalaQAQ/PicoFacialBridge/releases) · [UnifiedPicoModule](https://github.com/WolalaQAQ/UnifiedPicoModule) · [PicoET-Enhance](https://github.com/WolalaQAQ/PicoET-Enhance) · [反馈问题](https://github.com/WolalaQAQ/PicoFacialBridge/issues)**

> 0.3.1 使用 BridgeSplit 协议，PC 端必须使用 UnifiedPicoModule。旧版 PicoFacialDataModule 及其分支无法接收该版本的数据。仍需使用旧协议时，可使用 [v0.2.0](https://github.com/WolalaQAQ/PicoFacialBridge/releases/tag/v0.2.0)。

## 功能

- 在头显内启动或停止追踪；通过前台服务与常驻通知，在应用窗口关闭后继续转发。
- 眼动与面部数据按各自的源频率独立发送，不做平滑或插值。
- 眼动与面部传输可分别开关，界面显示各通道的实际发送频率。
- 只发送 PC 端订阅的通道，订阅确认前不发送任何追踪数据。
- 自动检测并显示追踪模式。未安装增强模块的普通头显为普通模式，转发融合视线与逐眼睁眼度；增强模块生效时为增强模式，额外转发逐眼视线与瞳孔直径。
- 界面支持中文与英文，语言与传输设置会被保存。

## 普通模式与增强模式

头显没有安装额外组件时，Bridge 只能转发融合视线，以及固件本身就提供的逐眼睁眼度，这是普通模式。

在已 Root（Magisk）的头显上安装 [PicoET-Enhance](https://github.com/WolalaQAQ/PicoET-Enhance) 后，头显会改为输出真实的逐眼视线与瞳孔直径，Bridge 会自动检测到这一变化并切换到增强模式，PC 端不需要为本功能做额外配置。

PicoET-Enhance 在运行期生效，不修改系统文件，也不改动整机属性，但目前只支持 PICO OS 5.13.7 固件。安装、模式切换与卸载方法见该项目的 README。

## 兼容性

| 项目 | 要求 |
|---|---|
| 头显 | 眼动与面部追踪功能正常的 PICO 4 Pro |
| 已验证固件 | PICO OS 5.13.7（Android 10 / API 29，arm64-v8a） |
| PC | Windows，安装 VRCFaceTracking 5.4.5 与 [UnifiedPicoModule](https://github.com/WolalaQAQ/UnifiedPicoModule) |
| 网络 | PC 与头显位于同一可信局域网，UDP 9030 端口可达 |
| VRChat | 已配置面部追踪的 Avatar，并启用 OSC |

本应用依赖 PICO 内部追踪服务与共享内存布局，系统更新可能影响其可用性。其他型号与固件版本尚未确认兼容。本应用无法为不具备追踪硬件的设备提供眼动或面部追踪。

截至 2026-10-02，以下内容已在 PICO 4 Pro 上完成验证：

- 与 UnifiedPicoModule 之间的通道订阅与确认；
- 眼动与面部开关的四种组合，以及切换时清理缓存；
- 两路均关闭时暂停采集；
- 数据中断后 PC 端恢复中性；
- 应用重启后自动恢复：头显界面约 6 秒回到运行状态，PC 端约 33 秒内重新订阅；
- 增强模式下 Avatar 的表情跟随；
- 与 Virtual Desktop 同时运行。

尚未验证的内容包括头显整机重启后的恢复、长时间运行的稳定性，以及普通模式下的 Avatar 表现。

## 安装与使用

### 1. 安装头显应用

1. 从 [Releases](https://github.com/WolalaQAQ/PicoFacialBridge/releases) 下载 `PicoFacialBridge-v<版本>-arm64-v8a-debug.apk`。Release 页面上的源码 ZIP 不是安装包。
2. 使用常用的头显 APK 安装工具侧载。也可以在头显上启用开发者模式与 USB 调试，连接并授权后，使用 [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) 安装：

   ```sh
   adb install -r PicoFacialBridge-v0.3.1-arm64-v8a-debug.apk
   ```

3. 在头显的 2D 应用列表中打开 PicoFacialBridge。确认系统设置中已启用眼动与面部追踪，点击启动，并授予应用请求的两项追踪权限。

发布的 APK 使用自动生成的 debug 签名，不同构建之间的签名可能不同，因此 Android 可能拒绝覆盖安装。遇到这种情况时，请确认 APK 来源后卸载旧版再安装新版。卸载会清除应用设置。

### 2. 安装 UnifiedPicoModule

按照 [UnifiedPicoModule 的安装说明](https://github.com/WolalaQAQ/UnifiedPicoModule#安装)在 VRCFT 中安装模块。安装前请停用其他 PICO 模块，避免端口冲突。

模块默认通过局域网多播自动发现头显。自动发现失败，或局域网内有多台头显时，可以在模块配置文件 `UnifiedPicoModule.json` 中指定头显地址：

```json
"Bridge": { "IP": "192.168.1.123" }
```

请将示例地址替换为 PicoFacialBridge 界面上显示的头显 IPv4 地址，修改后重启 VRCFT。头显重新连接 Wi-Fi 后，地址可能发生变化。配置文件的位置与其他字段请参阅 UnifiedPicoModule 的[配置说明](https://github.com/WolalaQAQ/UnifiedPicoModule/blob/main/docs/configuration_zh.md)。

### 3. 日常使用

1. 在头显上打开 PicoFacialBridge 并点击启动。
2. 在 PC 上启动 VRCFT。连接成功后，PicoFacialBridge 会显示已连接的客户端与非零的发送频率；VRCFT 的 Output 页面会显示 `Bridge/BridgeSplit: state=Connected`。
3. 启动 VRChat 并开启 OSC。
4. 结束使用时，在应用或常驻通知中点击停止。

本应用不会开机自启。头显重启后，需要重新打开应用并启动。

## 界面说明

| 控件或状态 | 说明 |
|---|---|
| 追踪模式 | 头显端检测到的普通模式或增强模式 |
| 启动 / 停止 / 重启 | 控制追踪服务，无需 ADB |
| 传输眼追 / 传输面捕 | 分别控制两个通道的发送，修改立即生效并被保存 |
| 两项均关闭 | 暂停追踪采集与数据发送，保留发现与控制通信 |
| 眼追 / 面捕 Hz | 最近一秒内成功发出的源帧数，不是采集频率或画面帧率 |
| 0 Hz | 没有客户端、通道已关闭或没有新的样本；重复样本与心跳不计入 |
| 等待新鲜追踪数据 | 请佩戴并唤醒头显，检查系统追踪设置与权限 |
| 中文 / English | 切换界面与通知语言，不影响追踪 |

PICO 的眼动与面部追踪共用同一套算法，因此即使只传输一个通道，也需要授予两项权限。传输开关只控制数据是否发送，不控制摄像头电源。

实际发送的通道是本地传输开关与 PC 端订阅的交集。只传输眼动时，除原生眼动数据外，还会发送眨眼、眉毛、EyeWide 与 EyeSquint 等眼周表情，此时面捕 Hz 显示为 0；只传输面部时，不发送眼动数据和眼周表情。

## 故障排查

| 现象 | 检查项 |
|---|---|
| VRCFT 找不到头显 | 检查 PC 与头显是否在同一局域网、路由器是否开启了访客网络或客户端隔离、PC 是否有 VPN 或虚拟网卡；尝试在模块配置中指定 `Bridge.IP` |
| 被防火墙拦截 | 允许 VRCFT 在专用网络中使用 UDP 9030，不建议整体关闭防火墙 |
| 已连接但始终为 0 Hz | 确认传输开关已打开，佩戴并唤醒头显，检查追踪权限与系统追踪设置 |
| 显示错误或不支持的布局 | 打开诊断日志，记录错误信息、头显型号与固件版本 |
| 休眠或切换网络后停止追踪 | 唤醒头显，核对当前 IP，点击重启后重新连接 VRCFT |
| 有数据但 Avatar 无反应 | 检查 VRCFT 的输出、VRChat OSC 与 Avatar 参数 |
| APK 更新失败 | 参见上文关于签名的说明；请下载用户 APK，而不是 instrumentation 测试 APK |

[提交 Issue](https://github.com/WolalaQAQ/PicoFacialBridge/issues) 时，请提供应用版本、头显型号、PICO OS 版本、VRCFT 与模块版本、复现步骤，以及去除个人信息后的日志片段。请勿上传原始面部追踪样本、设备标识或签名密钥。

## 隐私与安全

本应用只在局域网内向已连接的客户端转发追踪数据，不持久保存原始表情帧，日志只记录协商状态、频率与有效性。UDP 协议没有加密与身份验证，请仅在可信网络中使用，不要将 UDP 9030 端口映射到公网。诊断工具可能记录生物特征样本，分享前请检查并去除敏感内容。

本应用使用标准的 Android 运行时权限，系统的摄像头与传感器指示保持有效。应用不修改系统分区，不放宽 SELinux，也不打包 PICO 私有库。

## 开发

### 从源码构建

构建需要 JDK 17 与 Android SDK 命令行工具（或 Android Studio）。请将 `JAVA_HOME` 与 `ANDROID_HOME` 指向对应的安装目录，并将 `sdkmanager` 加入 PATH，然后接受 SDK 许可并安装固定版本的依赖：

```sh
sdkmanager --licenses
sdkmanager "platforms;android-35" "build-tools;35.0.0" "ndk;26.1.10909125" "cmake;3.22.1"
git clone https://github.com/WolalaQAQ/PicoFacialBridge.git
cd PicoFacialBridge
# Linux / macOS
bash ./gradlew :bridge:assembleDebug
# Windows PowerShell
.\gradlew.bat :bridge:assembleDebug
```

Gradle Wrapper 版本为 8.9，Android Gradle Plugin 版本为 8.7.3。首次构建需要联网。用户应用只需构建 `:bridge`，`:probe` 是开发者诊断应用。构建产物为 `bridge/build/outputs/apk/debug/bridge-debug.apk`，使用本机 debug 签名，可以直接安装。

### 主机检查

```sh
bash tests/run-host-tests.sh
pwsh -File tests/check-translations.ps1
# 需先构建 debug APK，仅依赖 Python 3 标准库
python3 tests/test_apk_distribution.py
```

在 Windows 上也可以运行 `tests/` 下的 `run-unit.ps1`、`run-protocol.ps1`、`run-cadence.ps1` 与 `run-forwarding.ps1`。这些脚本从本地文件 `tools/jdk-path.txt` 读取 JDK 目录（该文件不纳入版本控制），可以通过 `Set-Content tools/jdk-path.txt $env:JAVA_HOME` 生成。设备 UI 测试与真机验收不在主机 CI 范围内。

### 项目结构

```text
PICO 追踪服务 → Binder / 只读共享内存
            → Android 前台服务 → 局域网 UDP
            → UnifiedPicoModule → VRCFaceTracking → VRChat OSC
```

| 目录 | 内容 |
|---|---|
| `bridge/` | 用户应用与 UDP 服务 |
| `shared/` | JNI、数据解析与转发逻辑 |
| `probe/` | 开发者诊断应用 |
| `tests/` | 主机、设备与网络检查 |

版本变化请参阅[更新日志](CHANGELOG_zh.md)，发布流程请参阅[发布指南](docs/RELEASING_zh.md)。

### 协议

本应用与 UnifiedPicoModule 之间使用 BridgeSplit 协议，基于 UDP 9030 端口，并通过多播地址 239.255.255.250 进行发现。眼动数据与面部数据分别作为带标签的数据报，按各自的源频率发送，只携带已订阅的通道与接收端实际读取的字段。约 90 Hz 的眼动流不再重复携带约 23 Hz 的面部帧。

普通模式下，应用会去除厂商固件中基于固定深度推算的逐眼标记，接收端因此使用融合视线；增强模块确实计算出独立逐眼视线时，才保留这些标记。

包格式、控制报文与连接流程的完整说明请参阅 UnifiedPicoModule 的[协议参考](https://github.com/WolalaQAQ/UnifiedPicoModule/blob/main/docs/protocol_zh.md)。协议变更时，本应用与 UnifiedPicoModule 需要同时更新。

### 参与贡献

欢迎提交 Issue 与 Pull Request。修改时请保持协议兼容与各通道的原始发送节奏，并同步更新两份 README 与界面翻译。请勿提交密钥、本机 SDK 路径、`reference/`、`evidence/`、原始生物特征样本，或开发过程中的计划、研究与测试记录。`docs/` 目录默认只跟踪两份公开的发布指南。

CI 会执行主机测试、翻译检查、Android lint 与 APK 构建。发布 GitHub Release 后，CI 会自动上传 debug 签名的用户 APK、校验和与许可声明，无需配置自定义 Secrets。

## 致谢

感谢 [thoricelli](https://github.com/thoricelli) 开发的 [PicoFacialDataDaemon](https://github.com/thoricelli/PicoFacialDataDaemon) 与 [PicoFacialDataModule](https://github.com/thoricelli/PicoFacialDataModule)。

本项目在开发过程中参考了原项目的 Binder 调用、共享内存数据布局与 UDP 协议，这些工作为访问头显追踪服务及最初的 VRCFT 集成奠定了基础。在此基础上，PicoFacialBridge 将头显端实现为无需 Root 的 Android 应用，提供独立的生命周期管理与转发实现，目前使用的线格式与原项目不同。

本项目是独立的社区项目，不是 PICO 官方产品，也不代表上游作者的官方发布。

## 许可证

与原项目一致，本项目采用 [MIT](LICENSE) 许可证。完整许可与保留的上游版权声明见 [LICENSE](LICENSE) 与 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)，两份文件同时包含在 APK 与 Release 附件中。
