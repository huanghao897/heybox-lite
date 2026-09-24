# HeyBox Lite 接入说明

Lite 只接入 `cnwearoverlay` 的 runtime 模块，不启用上游面向 Wear Compose 的 Gradle ASM 插件，也不接入 Xposed 模块。

当前接入点是 `CrownHapticProvider`：

- 小米设备优先使用 `SCROLL_TICK=18`；
- OPPO 设备使用项目提供的 `SCROLL_TICK=12`；
- 小米设备使用 runtime 提供的 `CLOCK_TICK -> SEGMENT_TICK` 修正作为第二回退；
- 其他设备继续使用 Android 系统触感；
- 表冠输入、速度计算和列表滚动仍由 Lite 自己负责。

上游源码按 LGPL-3.0 保留在本目录，许可证文件与来源说明不得删除。
