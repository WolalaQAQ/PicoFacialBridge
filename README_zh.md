# PicoFacialBridge 使用说明

[English](README.md)

[公开验证摘要](docs/VERIFICATION.md)

## 当前结论

**普通 APK 直接访问 Binder 的 Rootless 路径已在真机跑通。**

实测设备：PICO 4 Pro / A8110，PICO OS **5.13.7**，Android **10（API 29）**，arm64-v8a，SELinux **Enforcing**。

- 普通 `untrusted_app`，没有使用 shell/system UID 来执行 APK 内的算法调用。
- NDK Binder 查找、Ping、Start/Stop、FD 获取及 JNI mmap 均成功。
- 授权并佩戴时，眼动与面部共享内存均持续变化。
- 后台 ForegroundService 已启动；PC 指定物理网卡后 multicast discovery、MARCO/POLO、STOP 已通。
- 佩戴后的60秒UDP验收已通过：1406帧，视线变化1215次、面部变化1405次，错误长度包0。
- **0.1.1已修复额外降采样**：眼动恢复约90Hz，任一路新数据即发包，面部仍按约23Hz的源节奏，未添加平滑。新60秒逐包校验发送6798/接收6798、有序SHA-256完全一致、ring历史溢出0。旧版1406帧测试不是新版本的发送速率。
- Steam VRCFT 原版模块已加载，历史录像已显示 VRChat OSC 接收参数变化。详见[公开验证摘要](docs/VERIFICATION.md)。
- **尚未完成**：Virtual Desktop并存、完全退出UI后的独立后台验收、重启/长时稳定性。不能保证任意网络下永不丢包，也不能把UDP包数等同于VRChat画面帧数。

验证结论与边界见 [验证摘要](docs/VERIFICATION.md) 和 [协议审计](docs/UPSTREAM-AUDIT.md)。原始设备日志、录像与生物特征样本不公开。

## 日常操作

1. 开机，在头显的 **2D 应用**中打开 `PicoFacialBridge`。
2. 点击 **Start**，首次使用允许眼动追踪和面部追踪权限。
3. 戴上头显，检查 Eye/Face 显示 Live；Running 表示数据链路有新鲜帧。
4. 退出应用窗口，打开 Virtual Desktop → SteamVR → VRChat。
5. PC 启动 VRCFaceTracking，加载下述兼容模块。
6. 结束时在应用或常驻通知里点击 **Stop**。

Start/Stop/Restart 都在 APK 内完成，**日常不需要 ADB、Root、PICO Connect、Shizuku 或 Wireless Debugging**。APK 不创建 OpenXR session。ADB 仅用于本次开发、安装与观察日志。

如果界面显示 `Starting / Waiting for fresh tracking`，先确认佩戴、系统眼动/面部开关和权限；旧共享内存即使有数值也不会被当作实时数据转发。`Error` 会保留具体原因。

## VRCFaceTracking 配置

### 0.2.0：传输开关、Hz 和语言

- **传输眼追**、**传输面捕**独立控制，默认都开启；可单独开任一路或两路都传。运行中修改即时生效并保存，不需要重启服务。
- 两项都关闭时暂停追踪订阅和数据发送，保留 UDP 发现／心跳；重新开启即可恢复。
- 两个 **Hz** 分别统计最近 1 秒实际成功发送的新源帧，不是采集速率或总 UDP 包数。重复携带上一帧、心跳不计数；关闭或没有客户端时为 0，流停止更新后最多 1 秒归零。
- 点击顶部 **中文 / English** 切换界面和常驻通知语言，选择会保存，且不重启转发。首次启动跟随系统语言（中文，否则英文）。日志与系统错误详情保留原始技术文本。
- 这是**发送端开关**，不是独立摄像头电源控制；PICO 共用算法仍需要两项权限。仅传眼追时保留眼追解析使用的原始眨眼／眉毛字段，关闭通道的数据和有效标记清空，保持原 536 字节协议。
- 已核对 PC 模块的 `DisableEyeTracking` / `DisableFaceTracking` 是接收端设置；此次不修改本机模块 DLL 或配置。模块跳过关闭通道后可能保留上次表情／视线，不代表会自动回到中性或改变 VRCFT 的模块能力指示。

新版已通过构建、核心／UDP 本机回环和真机 UI／设置持久化测试；佩戴头显后的单通道实时速率与 Avatar 表现仍待验收。前文 60 秒逐包结果是 **0.1.1** 的历史实测。

### PC 模块配置

使用 **thoricelli/PicoFacialDataModule**，不是依赖 PICO Connect 的 `Pico4SAFTExtTrackingModule` / Streaming Assistant 模块。

1. 从 [PicoFacialDataModule Releases](https://github.com/thoricelli/PicoFacialDataModule/releases) 获取模块；本项目已测试过上游 `v0.2-beta` 下载包。
2. 在 VRCFaceTracking 的模块管理中安装该模块，或者按其自定义模块方式放入 `%APPDATA%\VRCFaceTracking\CustomLibs\61ee1324-fd45-42f1-9636-8e28717cf6db\`。
3. 同一个目录放置 `PicoFacialDataModule.json`：

```json
{
  "DisableEyeTracking": false,
  "DisableFaceTracking": false,
  "IP": "",
  "TrackingSettings": { "eyePupilDilationEyeOpennessThreshold": 0.8 }
}
```

`IP` 是头显当前 Wi-Fi IPv4，重连网络后可能变化；留空则使用原 multicast discovery。APK UI 会显示当前 IP。

VPN／虚拟网卡可能干扰 multicast discovery；可选择物理局域网网卡或填写 APK 显示的头显 IP。**优先用模块原生 IP 配置，不用改 PC 模块代码，也不要关闭全局防火墙。** 若 Windows 弹出网络访问提示，请只允许可信局域网需要的访问。旧模块如有冲突，先备份再禁用，不要直接删除配置。

注意：上游 v0.2-beta ZIP 内 `module.json` 仍标着 0.1-beta；保留了原包，没有篡改 DLL 或版本元数据。当前源码目标为 .NET 10，仍需要核对实际 VRCFT 版本兼容性。

## 构建与安装

完整 Android Studio / Gradle 项目就是当前目录。已有环境下：

```powershell
.\tools\build.ps1
.\tests\run-unit.ps1
.\tests\run-protocol.ps1
.\tests\run-cadence.ps1
.\tests\run-forwarding.ps1
.\tests\check-translations.ps1
adb install -r .\artifacts\PicoFacialBridge-debug.apk
```

首次配置请看 [English README 的 Build 部分](README.md#build-windows)。Python 使用已有 Conda 环境，不需要另装。

构建依赖存放在 `%USERPROFILE%\.cache\pico-android\`，未修改全局 PATH 或 shell 配置。JDK 17 / Gradle 8.9 / AGP 8.7.3 / SDK 35 / NDK 26.1.10909125 / CMake 3.22.1。

输出 APK 是个人测试用的 **debug 签名版本**；不包含参考项目附带的私有 PICO `.so`。保留本机签名密钥，避免更新时签名不匹配。

## PC 无 ADB 数据链路测试

关闭占用 UDP 9030 的 VRCFT，再执行：

```powershell
$headsetIp = Read-Host 'APK 中显示的头显 IPv4'
& "$env:USERPROFILE\miniconda3\python.exe" .\tests\udp-client.py --headset $headsetIp --seconds 60
# 指定物理网卡测试原 multicast discovery：
$localIp = Read-Host 'PC 物理局域网网卡 IPv4'
& "$env:USERPROFILE\miniconda3\python.exe" .\tests\udp-client.py --multicast --local-ip $localIp --seconds 60
```

该脚本直接使用 Wi-Fi UDP，检查 536 字节帧、视线和 blendshape 变化，保存统计并在结束时发送 STOP。它不执行 ADB，不使用 PICO Connect，也不是 VRCFT 主程序实测的替代证明。

需要逐包校验时，退出 VRCFT 后运行 `tests/udp-delivery-audit.py --headset <头显IPv4>`；若 ADB 不在 PATH 中，用 `--adb <adb.exe完整路径>` 指定。它通过 UDP 收数据，结束后读取 ADB 发送日志，比对完整会话包数和有序 SHA-256；测试结束释放 9030 并发送 STOP，再启动 VRCFT 即可。

## 仓库公开范围

Git 只管理源码、构建脚本、Gradle wrapper 和公开文档。签名密钥、本机 SDK 配置、APK／构建目录、第三方参考二进制、原始实验记录和生物特征样本均忽略并留在本机。不要用 `git add -f` 上传这些内容；分享诊断材料前应单独检查和脱敏。

## 已知限制与隐私

- 当前只验证了 5.13.7 固件，未知内存布局会显式报错，避免错误表情数据。
- 追踪服务查找使用了固件开放的 Android 10 APEX 符号，不保证未来 PICO OS 仍兼容。
- 取下头显/休眠会让数据停止；有 stale/death 检测与重连，但完整休眠/唤醒和串流并存仍待长时测试。
- ForegroundService 不能阻止用户强制停止、关机或 OEM 极端后台管理。
- 协议为兼容原模块保留无认证、无加密的局域网 UDP；不要端口映射到公网，只在可信 Wi-Fi 下使用。
- `evidence/` 含本机设备日志和面捕样本，不要公开上传。正式 Bridge 不持久保存表情原始帧；Probe 为诊断会在自身私有存储写少量样本。
- 没有 Root、解锁 Bootloader、修改系统分区、放宽 SELinux、关闭防火墙或占用 XR session。
