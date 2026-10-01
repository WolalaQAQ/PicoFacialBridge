# PicoFacialBridge

[English](README.md) | 简体中文

[![Android CI](https://github.com/WolalaQAQ/PicoFacialBridge/actions/workflows/android.yml/badge.svg)](https://github.com/WolalaQAQ/PicoFacialBridge/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

让 **PICO 4 Pro 的眼动与面部追踪接入 VRCFaceTracking**，无需 Root。PicoFacialBridge 是安装在头显上的 Android 应用，读取头显追踪数据，通过局域网转发给配套的 UnifiedPicoModule。

**[下载 APK](https://github.com/WolalaQAQ/PicoFacialBridge/releases) · [反馈问题](https://github.com/WolalaQAQ/PicoFacialBridge/issues) · [发布指南](docs/RELEASING_zh.md)**

## 功能

- 在头显内启动、停止追踪，通过前台服务和常驻通知支持后台转发。
- 独立转发眼动和面部原始样本，不额外添加平滑或插值。
- 支持仅眼追、仅面捕或两路同时传输，并显示各路实际发送速率。
- 应用内切换中文、英文，保存语言和传输偏好。
- 使用配套 UnifiedPicoModule，支持自动发现或指定头显 IP；只发送 PC 订阅的通道。
- 自动检测追踪模式并显示：普通头显（免 root）为**普通模式**，检测到增强模块生效时进入**增强模式**。增强模式在模块提供数据时转发真正的逐眼视线与真实瞳孔直径；普通模式保留融合视线，以及固件本身就会给出的真实逐眼睁眼度。

无需 Root、Magisk、Shizuku、PICO Connect，也不占用 OpenXR session。ADB 仅是安装和调试的可选工具，日常使用不需要。使用此应用时，不必另外安装原项目的 daemon。

## 使用条件与兼容性

| 组件 | 要求 |
| --- | --- |
| 头显 | 具备正常眼动、面部追踪功能的 **PICO 4 Pro** |
| 已验证固件 | **PICO OS 5.13.7**，Android 10 / API 29，arm64-v8a |
| PC | Windows，安装 [VRCFaceTracking](https://github.com/benaclejames/VRCFaceTracking) 和配套 UnifiedPicoModule（见下方配置步骤） |
| 网络 | PC 与头显位于同一可信局域网，UDP 9030 可达 |
| PCVR 串流 | 已实测可与 **Virtual Desktop（VD）** 同时使用 |
| VRChat | 已配置面捕的 Avatar，并启用 VRChat OSC |

> **目前仍为实验性、固件相关项目。** 尚未确认其他型号和固件兼容。本项目依赖 PICO 内部追踪服务及内存布局，系统更新可能影响可用性；无法让缺少追踪硬件的设备获得眼追或面捕。

**已在上述支持环境中实测，bridge 可以搭配 Virtual Desktop（VD）正常使用。** 长时间休眠唤醒恢复和单通道 Avatar 表现仍需进一步测试；VD 兼容性测试不代表这些独立场景或其他固件也已完成验证。

## 快速开始

> **v0.3.0 是预发布版本。**它使用 BridgeSplit 协议，因此必须搭配第 2 步的配套 UnifiedPicoModule，且**不能**从 v0.2.0 直接覆盖升级。2026-10-01 已通过第一轮实机验收（订阅与 ACK、四种有效通道组合、两端 epoch 一致、切换时清理缓存、两路全关暂停、缺帧转 neutral、应用重启恢复）；整机重启、旧 daemon 共存与长时稳定性仍未验证。[v0.2.0](https://github.com/WolalaQAQ/PicoFacialBridge/releases/tag/v0.2.0) 仍是最新稳定版。

### 1. 安装头显应用

1. 打开 [Releases](https://github.com/WolalaQAQ/PicoFacialBridge/releases)，在所选版本的 **Assets** 中下载 **`PicoFacialBridge-v<版本>-arm64-v8a-debug.apk`**。源码 ZIP 不是安装包。
2. 使用你习惯的头显 APK 安装工具侧载。也可以启用开发者／USB 调试，连接并授权头显后，通过 [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) 安装：

   ```sh
   adb install -r PicoFacialBridge-v0.3.0-arm64-v8a-debug.apk
   ```

   请将示例文件名替换为实际下载的 APK。使用 Release 安装包不需要配置 Android 开发环境。
3. 在头显的 **2D 应用**中打开 **PicoFacialBridge**。在系统设置中启用眼动和面部追踪，点击 **启动 / Start**，允许请求的**两项**追踪权限。
4. 佩戴并唤醒头显。首次连接时建议保持两项传输开关开启。

**构建类型与更新：**下载的是自动 debug 签名的侧载 APK，不用于 Google Play。下载后即可安装，不需要配置签名。不同 CI 构建可能使用不同的 debug 密钥，因此 Android 可能拒绝覆盖更新；核实下载来源后，必要时先卸载旧版再安装新版（**会清除应用偏好设置**）。

### 2. 配置 VRCFaceTracking

**本版本使用 BridgeSplit 协议，不兼容上游 PicoFacialDataModule、早期 PicoFacialDataModule fork 和旧 daemon。** 请使用配套的 **UnifiedPicoModule**。

1. 按 UnifiedPicoModule 的 README 从源码自行构建（目前尚未发布 Release），得到模块 ZIP。
2. 在 VRCFaceTracking 的 **Module Registry** 中使用 **Install Module from .zip** 选择该 ZIP，然后完全退出并重启 VRCFaceTracking。替换旧安装前备份模块配置，不要同时加载多个 PICO 模块。
3. 在同一局域网启动 PicoFacialBridge 和 VRCFaceTracking。无需 daemon；Root 仅用于可选增强能力。

如果发现失败，先关闭 VRCFaceTracking，将 `Bridge.IP` 设置为 bridge 界面显示的头显 IPv4。修改以下目录中的 `UnifiedPicoModule.json`：

```text
%APPDATA%\VRCFaceTracking\CustomLibs\8322e16d-c38b-42f0-8d35-68f27227b442\
```

```json
{
  "Bridge": {
    "IP": "192.168.1.123"
  }
}
```

这里只列出要改的字段：保留文件里的 `ConfigVersion` 和其他已有设置。替换示例 IP；`null` 时使用自动发现。修改后重启 VRCFaceTracking；头显重新连接 Wi-Fi 后，IP 可能变化。

### 3. 日常使用

1. 打开 bridge，点击 **启动 / Start**，戴上头显。
2. 启动加载了 UnifiedPicoModule 的 VRCFaceTracking。追踪活动时，确认 bridge 显示已连接的客户端和非零发送速率。
3. 启动平时使用的 PCVR／VRChat 环境，在 VRChat 中开启 **OSC**，使用兼容的 Avatar。Bridge 只负责追踪数据转发，不负责画面串流或配置 Avatar。
4. 结束时，在应用或常驻通知中点击 **停止 / Stop**。头显重启后，需要重新打开应用并启动；本项目不会开机自启。

Bridge 可以与 Virtual Desktop 同时运行。前台服务用于在应用窗口关闭后继续转发，但系统后台策略或强制停止仍可能中断运行。

## 控件与状态

| 控件／状态 | 含义 |
| --- | --- |
| 追踪模式 | 在头显侧自动检测的普通／增强模式；增强模式在模块提供数据时使用逐眼视线与真实瞳孔 |
| 启动 / 停止 / 重启 | 无需 ADB 即可控制追踪服务 |
| 传输眼追 / 传输面捕 | 独立控制发送，修改立即生效并保存 |
| 两项都关闭 | 暂停追踪采集和数据发送，保留 UDP 发现与控制 |
| 眼追 / 面捕 Hz | 最近一秒成功发出的不同源帧数，不是采集速率或视频 FPS |
| 0 Hz | 无客户端、通道关闭或没有新鲜样本；重复样本和心跳不计数 |
| 等待新鲜追踪数据 | 佩戴／唤醒头显，检查系统追踪设置和权限 |
| 中文 / English | 切换界面与通知语言，不重启追踪 |

PICO 共用追踪算法，因此仅传一路时仍需要两项权限。这些开关**不是独立摄像头电源开关**。接收端可能在某一路停止后保持最后的视线／表情，关闭传输不会让 Avatar 自动恢复中性姿态。

Bridge 只发送 UnifiedPicoModule 订阅的通道：实际发送 = 本地传输开关与 PC 宿主可用、配置启用通道的交集，**订阅确认前不发送任何追踪数据**。仅眼追发送 83 字节原生眼帧和 95 字节眼周形态辅助帧（眨眼回退、眉毛、EyeWide/EyeSquint），不发嘴部形态；仅面捕只发 151 字节面部帧，不发原生眼帧、辅助帧或清零的眼周占位字段。辅助仍按原始面部源频率发送，**面捕 Hz** 保持 0。双路使用原生眼帧与完整面部帧，不另发辅助帧。订阅、有效发送 mask 与 epoch 可在诊断日志确认；停止、超时、重发现会清理订阅，模式通告与心跳保持。全部有效通道关闭时暂停采集但保留控制。2026-10-01 已通过第一轮实机验收（应用重启：头显界面约 6 秒回到“运行中”，PC 侧约 33 秒重新订阅）；整机重启与旧 daemon 共存仍未验证。

### BridgeSplit 线格式（已通过第一轮实机验收）

UDP 9030。每个数据报共用 **19 字节**头部：偏移 0 tag（`E`/`A`/`F`）、1 有效通道 mask（眼=1/面=2）、2 形态有效位（眼=1/面=2，必须是 mask 子集，`E` 为 0）、3 正 int64 epoch、11 正 int64 原始 timestamp。多字节字段均 little-endian；float 为 IEEE 754 binary32。

| tag / 总长度 | 偏移 19 起的 payload |
|---|---|
| `E` / **83** | 19/23/27 左/右/combined uint32 状态；31/43/55 gaze float3；67/71 openness；75/79 pupil vendor 值（PC /10 转 mm）。允许状态位 0x002、0x004、0x100、0x800 |
| `A` / **95** | 19 个眼周 shape float32；仅 mask=1，face 无效 |
| `F` / **151** | 33 个非眼周 shape float32；仅 mask=2，眼有效位为 0，不发送眼周占位字段 |
| `F` / **227** | 52 个 PICO shape float32，按原顺序；仅 mask=3 |

`A` 的稳定槽顺序为 `0,2,3,4,11,12,16,26,28,30,31,35,36,38,41,44,45,46,47`，依次为 EyeLookDownL、EyeLookInL、BrowInnerUp、BrowDownR、EyeLookInR、EyeLookDownR、BrowDownL、EyeSquintL、EyeBlinkL、BrowOuterUpL、EyeLookUpL、EyeLookUpR、BrowOuterUpR、EyeBlinkR、EyeSquintR、EyeLookOutL、EyeLookOutR、EyeWideR、EyeWideL。`A`/`F` 的 timestamp 来自原始面部样本，不合成或插值。

仅面捕 `F` 的槽顺序为 `1,5,6,7,8,9,10,13,14,15,17,18,19,20,21,22,23,24,25,27,29,32,33,34,37,39,40,42,43,48,49,50,51`，即 52 槽中剔除上述 19 个眼周槽后按原编号升序排列。

控制报文为精确 ASCII，单空格、固定字段顺序、无终止符（尖括号是占位符）：

```text
PXR_MODE mode=<normal|enhance> rooted=<0|1> enhance=<0|1> gate=<0|1> plugin=<off|left|right|dual>
PXR_SUB id=<16位小写hex> mask=<0..3>
PXR_SUB_ACK id=<同一id> mask=<有效mask> epoch=<16位小写hex>
```

收到 `DISCOVER_DAEMON` 后，Bridge 立即发送 `PXR_MODE`，之后在模式变化时和每 10 秒重发，并保持 `MARCO\0`/`POLO` 心跳。`PXR_SUB`（34 字节）与 `PXR_SUB_ACK`（61 字节）只接受当前锁定 peer；需求与 id 固定至下次发现，重复请求幂等，新 id/不同需求不能覆盖会话。PC 未确认时每秒重试，Bridge 立即回复 ACK，并随模式通告重发。未知字段/额外空白不接受。

epoch 在新订阅或本地开关变化时推进；接收端按 ACK 清缓存、拒绝旧 epoch 或不匹配 mask，关掉通道不残留在途数据。双路不另发 `A`，避免眼辅助覆盖低频嘴部。所有 float 必须有限；形态中单个非有限厂商值只把该槽置 0，不清除整组眼/面有效位；原生眼帧中的非有限值归零并清对应的视线/睁眼度/瞳孔有效位，让接收端看到失效，不冻结旧帧。数据长度、状态位和正时间严格校验。日志仅记录协商/频率/有效性，不输出原始样本。

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
             → UnifiedPicoModule → VRCFaceTracking → VRChat OSC
```

`bridge/` 包含应用与 UDP 服务；`shared/` 包含 JNI、解析和转发逻辑；`probe/` 是开发者诊断应用；`tests/` 包含主机、设备和网络检查。版本变化见[更新日志](CHANGELOG.md)。

协议使用 UDP **9030** 端口和 **239.255.255.250** 多播发现地址。眼动与面捕是按各自源频率发送的带标签数据报，只携带已订阅的通道和接收端会读的字段，因此约 90 Hz 的眼动流不会再重复携带约 23 Hz 的面捕帧，未被读取的 3D 视线点、position guide、foveated 槽和恒 0 字段全部丢弃。另有简短 ASCII 控制报文（`PXR_MODE ...`）通告检测到的模式。眼动数据报里，普通模式下仍会屏蔽厂商的固定深度拆分分眼标记，让接收端继续使用融合视线；当增强模块生效、确实给出独立逐眼视线时，Bridge 会保留这些有效位。详见 [BridgeSplit 线格式](#bridgesplit-线格式未实机验收)；Bridge 与 UnifiedPicoModule 必须一起更新。

欢迎提交 Issue 和 Pull Request。修改时请保持协议兼容及各路原始发送节奏，为行为变化补充测试，并同步两份 README 和 UI 翻译。不要提交密钥、本机 SDK 路径、`reference/`、`evidence/`、原始生物特征采样或开发过程中的计划、研究、测试流水账。`docs/` 默认仅跟踪两份公开发布指南。

CI 会执行主机测试、翻译检查、Android lint 和 APK 构建；发布 GitHub Release 后自动上传 debug 签名的用户 APK、校验和及许可证。无需配置自定义 Secrets，步骤见[发布指南](docs/RELEASING_zh.md)。

## 致谢

衷心感谢 **[thoricelli](https://github.com/thoricelli)** 开发的原项目 [PicoFacialDataDaemon](https://github.com/thoricelli/PicoFacialDataDaemon) 和 [PicoFacialDataModule](https://github.com/thoricelli/PicoFacialDataModule)。

本项目在开发过程中**参考了原项目**，尤其是 Binder 调用、共享内存数据布局和 UDP 协议。原作者的工作为访问头显追踪服务和原始 VRCFaceTracking 集成奠定了基础；当前 fork 已使用不同的线格式。PicoFacialBridge 将头显端实现为无需 Root 的 Android 应用，并提供自身的生命周期管理和数据转发实现。

这是独立的社区项目，不是 PICO 官方产品，也不代表上游作者的官方发布。

## 许可证

与原项目一致，本项目采用 **MIT 协议**。完整许可和保留的上游版权声明见 [LICENSE](LICENSE) 与 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。两份声明同时包含在 bridge APK 和 Release 附件中。
