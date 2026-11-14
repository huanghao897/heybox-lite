# CN Wear Overlay

为小米 / OPPO 手表补充 Wear Compose 表冠触觉兼容，并修复小米部分触觉反馈常量导致的震感异常：将 `CLOCK_TICK` / `SEGMENT_FREQUENT_TICK` 映射为 `SEGMENT_TICK`。

> [!NOTE]
> 本项目仍处于较早期阶段，暂时仅支持 Xiaomi Watch 5 系列、OPPO Watch 全智能系列 及其更新产品。其他设备尚待适配，兼容性未经验证。

项目提供两种接入方式：可修改应用源码时使用 **Gradle 插件**；需要在应用进程内安装 Hook 时使用 **Xposed 模块**。运行时根据系统类识别设备，检测到系统自带 Google Wear 触觉 SDK 时不介入。

## Gradle 插件使用说明

在应用项目根目录（`settings.gradle.kts` 所在目录）把本仓库添加为 git submodule：

```
git submodule add https://github.com/Star-ZER0/cnwearoverlay.git cnwearoverlay
```

后续升级时只需执行 `git submodule update --remote cnwearoverlay` 即可自动同步本仓库。

在应用项目的 `settings.gradle.kts` 中，将 `includeBuild` 加入已有的 `pluginManagement` 块，保留原有配置：

```kotlin
pluginManagement {
    includeBuild("cnwearoverlay")
    // 保留项目原有的 repositories 等配置。
}
```

在应用模块（通常为 `app/`）的 `build.gradle.kts` 中，将插件 ID 加入已有的 `plugins` 块：

```kotlin
plugins {
    // 保留项目原有的 Android、Kotlin 等插件。
    id("cc.star0.wear.lib.cnwearoverlay")
}
```

同步 Gradle，然后按原有方式构建应用即可。插件由应用项目的 Gradle 自动编译并应用，无需单独构建或发布插件，也无需修改业务代码。

**可选**配置写在同一应用模块的 `build.gradle.kts` 中：

```kotlin
// 可选，以下均为默认值。
cnWearOverlay {
    enabled.set(true)
    patchHapticsKt.set(true)
    remapXiaomiConstants.set(true)
    generateStub.set(true)
}
```

| 选项 | 作用 |
| --- | --- |
| `enabled` | 总开关；关闭后不注入运行时或修改 ASM 字节码 |
| `patchHapticsKt` | 适配 Wear Compose 的表冠常量、输入和旧版 OPPO 触觉调用；不依赖小米重映射开关 |
| `remapXiaomiConstants` | 将应用及依赖的 `View.performHapticFeedback` 调用接入运行时代理，重映射小米常量，并支持旧版 OPPO 的 `12` → `302` 路径 |
| `generateStub` | 注入缺失的 `com.google.wear.input.WearHapticFeedbackConstants` 桩类；与 `patchHapticsKt` 同时开启时才包装 `hasWearSDK` |

插件应用于 `com.android.application` 模块；应用于 Android library 模块时仅提示，不执行修改。运行时 JAR 和所需 R8 规则会自动注入，无需手动复制。

### 如何抓取日志

运行时诊断日志默认关闭，tag 为 `CnWearOverlay`。先使用包含诊断代码的本仓库版本重新构建、安装**使用方应用**；只构建插件不会更新手表上的 APK。Debug 和 Release 均可开启，但使用方若自行配置了移除 `android.util.Log` 的 R8 规则，应先停用这些规则。

连接 ADB 后，在电脑终端运行（把 `your.application.id` 换成应用包名）：

```shell
adb shell setprop log.tag.CnWearOverlay DEBUG
adb shell am force-stop your.application.id
adb logcat -b main -b system -b crash -v threadtime -T 1 "*:V" > cnwearoverlay-logcat.txt
```

保持抓取，手动打开应用，进入有表冠操作的列表或选择器，慢转、快转、反向转动表冠各数秒；列表可再滚到边界。完成后按 Ctrl+C。请从应用冷启动前开始抓取，以免漏掉只初始化一次的平台探测和反射异常。完整日志保留应用与系统进程的信息，先不要只按应用 PID 或本库 tag 过滤。

较新系统可另抓 `adb shell dumpsys vibrator_manager`；某项服务不存在时保留错误即可。厂商直接调用马达的记录未必出现在标准 vibrator dump 中，空记录不能证明没有调用。排查结束后执行 `adb shell setprop log.tag.CnWearOverlay ""` 关闭本库诊断日志。

## Xposed 模块

`xposed/` 是独立 Android 工程，使用 libxposed API 102。模块在目标应用的 `onPackageReady` 回调中安装 Hook，并在缺失 Wear SDK 时追加兼容 DEX。

构建、签名并安装 APK 后，在支持对应现代 libxposed API 的框架（如 LSPosed）中启用模块并选择目标应用作用域，然后重新启动目标应用。模块没有独立配置界面；当前只处理应用进程。LSPatch 等内嵌框架场景需确认所用版本支持对应 API，并在目标设备验证。

### 构建 Xposed 模块

先设置 `ANDROID_HOME`，或在 `xposed/local.properties` 中配置 `sdk.dir`。以下两组命令均从本仓库根目录执行，选择对应平台的一组：

**Windows（PowerShell）：**

```powershell
cd xposed
.\gradlew.bat :app:assembleRelease
```

**macOS / Linux（终端）：**

```bash
cd xposed
gradlew :app:assembleRelease
```

Xposed 工程使用 Gradle **9.7.1**、AGP **9.3.3**、Android SDK **37**，Java 源码目标版本为 **17**。

## 兼容范围

- Gradle 插件编译依赖 AGP API **9.4.1**，使用方的 AGP 兼容性需随具体项目验证。
- 当前 Compose 补丁针对 Wear Compose **1.6.2** 的类结构与方法签名；升级依赖后需重新验证。
- 小米的表冠常量为 `(19, 18, 20)`，OPPO 为 `(12, 12, 12)`，顺序为 focus / tick / limit。
- OPPO 鼠标滚轮转 rotary 时，MODERN 仅接受设备名精确为 `oplus_crown` 的输入，LEGACY 不限制设备名；两者均要求单指 `ACTION_SCROLL` 及有限非零的 `AXIS_VSCROLL`。
- OPPO 按系统类区分两代：同时存在 `android.os.linearmotorvibrator.LinearmotorVibrator` 和 `android.view.HapticScrollFeedbackProvider` 为 `OPPO_MODERN`，仅存在前者为 `OPPO_LEGACY`。MODERN 将 `12` 交给系统；LEGACY 直接播放表冠波形 `302`（强度 `2`、不循环），保留 View / 系统触觉开关及调用 flags 的语义。反射、服务或权限不可用时返回 `false`，不回退到 `VIRTUAL_KEY(1)` 或其他震动效果。
- 两个 OTA 的静态核验覆盖 OWW261 / OWW231；LEGACY 使用厂商隐藏 API，普通应用与 Xposed 环境中的可用性、实际震感仍需真机验证。
- Xposed 声明 `minSdk 26`，独立 Wear SDK bridge DEX 按 `min-api 29` 生成；低版本系统的完整功能兼容性尚需验证。
- Xposed 按名称查找 Compose 类。目标应用混淆这些名称后，Compose Hook 可能跳过；View Hook 与 SDK bridge 仍会分别尝试安装，实际效果取决于目标应用和设备。

## 目录内容

| 目录 | 内容 |
| --- | --- |
| `plugin/` | Gradle 插件、ASM 字节码改写与运行时打包 |
| `runtime/` | 设备识别、触觉常量、Google Wear 桩类和 consumer R8 规则 |
| `xposed/` | 独立的 libxposed 模块工程 |

开发与维护说明见 [AGENTS.md](AGENTS.md)。

## 许可证

见 [LICENSE](LICENSE)（Apache-2.0 with LLVM Exception）。
