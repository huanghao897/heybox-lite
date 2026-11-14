# HeyBox Lite 接入说明

上游版本：`1.2.0`，提交 `5c4680139f2ebe6ec1754a93e0a379c429944618`。
来源：https://github.com/Star-ZER0/cnwearoverlay/releases/tag/1.2.0

Lite 沿用独立 Android runtime 模块，不启用上游 Gradle ASM 插件，不依赖 Xposed。
当前应用不使用 Wear Compose 1.6.2 的 rotaryScrollableBehavior，不需要对其字节码插桩。

## 接入边界

- `CrownHapticProvider` 通过 runtime 代理发出触感，不能直接绕过代理调用常量 12。
- 小米优先使用 `SCROLL_TICK=18`，必要时使用 `CLOCK_TICK -> SEGMENT_TICK` 修正。
- 现代 OPPO 使用系统原生常量 12；旧款 OPPO 由 runtime 检查 View/系统触感开关并请求厂商波形 302。
- 旧款 OPPO 的反射或服务失败时不叠加通用震动；现有 Lite 触感开关和频率限制保留。
- 应用声明普通权限 `android.permission.VIBRATE`，供检查应用身份的马达服务使用，无运行时弹窗；隐藏 API 可用性仍由具体固件决定。
- 系统 Google Wear SDK 优先，不替换三星等设备的原生触感。
- 输入、速度、方向和列表滚动仍由 Lite 的 `CrownInputHandler` 及 Compose 输入队列负责，不重复分发。
- 上游 `rotaryEvent` 为其他集成保留，Lite 当前已读取鼠标 VSCROLL 和 rotary SCROLL，不再额外转换一次。

## 本地兼容调整

本应用支持 API 21，runtime 模块保留原 minSdk 14 配置。
以数值常量表示 API 26 表冠轴/来源，有限数判断不使用 API 24 的 `Float.isFinite`。
公共反射构造入口避免依赖 API 19 才有的反射异常公共父类；旧 OPPO 专用组件仅在系统标记存在时使用。

runtime 的 `CnWearOverlay` 日志默认关闭；需要时使用
`adb shell setprop log.tag.CnWearOverlay DEBUG`，冷启动后观察，结束后清空该属性。
日志只涉及平台探测、输入和触感，不记录登录凭据。反射调用成功不等于已验证物理震动。

上游源码、README 和 LGPL-3.0 许可证保留在本目录。实际震感及 OEM 输入行为需要对应手表验收。
