# 媒体与阅读改进

## 范围

基线为 `a32b218`，保持版本 `2.19 / 222`、现有官方接口和云端签到行为。
本轮不操作服务器、不上传 APK、不创建 Release、不启动模拟器、不关机。
工作目录原有视觉调整独立保留；本轮提交只包含以下行为、测试及维护说明。

## 已实现

| 项目 | 行为与边界 | 主要回归 |
| --- | --- | --- |
| 省流模式 | 默认关闭；内容图片、评论图片、视频与游戏封面保留占位，点击后才读取缓存、解码或联网。首次点击只加载，不误开帖子或视频。头像和官方表情不受限制；无图设置独立生效。权限按图片源保存，不跟随列表复用槽位。 | `ComposeDataSaverFeedUiTest`、`ComposeDataSaverMediaUiTest`、`ComposeDataSaverSettingsUiTest` |
| 滚动 | 未拉出刷新头时跳过无效回弹等待；关闭动效时直接归位；取消手势不能误刷新。图片连接中断移入有界后台队列，饱和时由原超时和 finally 释放。表冠同向事件合并，反向事件按顺序保留。 | `OfficialPullRefreshStateTest`、`OfficialPullRefreshUiTest`、`ImageWorkTokenTest`、`ComposeFeedRotaryUiTest` |
| 二级评论 | 按最终反馈保留圆角底色，取消新增的上下分隔线；不新增水平缩进，保持与一级正文左右对齐。 | `ComposeCommentCompactUiTest` |
| 表情与蓝链 | 目录晚到后更新当前绑定；离页、换文字或换主题撤销旧回调。表情保留透明通道；大量等待者不再被固定数量截断。未知方括号表情不被误作 JSON 丢弃；抽奖等非游戏蓝链保留名称、样式和网页点击。仅接受合法 HTTP/HTTPS，不执行正文提供的任意协议。 | `EmojiRendererReliabilityTest`、`EmojiStoreTest`、`RichInlineReliabilityTest`、`RichLinkClassifierTest`、`ComposeInlineLinksTest`、`ComposeInlineLinkUiTest` |
| Compose 图片查看器 | 页面、分页、控制、手势和请求归属迁入独立 Compose 组件；保留原启动参数、GIF、保存、原图、缩放与拖动退出。长图采用固定视口和有预算的区域瓦片，不创建超高整图布局；按物理像素选采样。圆屏不自动请求原图，清晰度切换保留旧图层至新瓦片可用。 | `ImageViewerEnterOrderingTest`、`ImageViewerRequestLifecycleTest`、`ImageViewerRoundOriginalTest`、`ComposeViewerGesturesUiTest`、`ComposeViewerLongImageUiTest`、`ComposeViewerHandoffUiTest`、`LongImageViewportTest` |
| 空间清理 | 纳入实际内容图片、详情缓存、旧安装包、导出诊断及长图临时源；统计已确认删除的字节。保留凭据、设置、保存的离线内容、待上传崩溃记录、未知文件及正在使用的租约。安装文件和运行代码单列，不伪称可清理缓存。扫描在后台且不持全局锁，逐文件删除前复核元数据、离线清单版本和租约。 | `DisposableStorageTest`、`DisposableStorageConcurrencyTest`、`SavedOfflineStorageTest`、`StorageFileLeasesTest`、`LocalStorageWritesTest`、`CacheMaintenanceTest`、`CacheMaintenanceLegacyTest` |

## 收尾修复

- 图片入场结束时若页面暂不在前台，仍保存完成状态，恢复后续载；迟到结果不能复活已关闭页面。
- 清晰度切换复用同一原生瓦片 View，避免移入保留图层时先销毁已解码图块；像素回归验证旧图保留及新图替换。
- 手势被取消时复位退出偏移，已提交的退出不被 finally 拉回。
- 长图关闭恰好发生在解码解锁前时，由解码结束路径完成关闭交接，释放解码器、临时文件及租约；关闭不在界面线程等待解码锁。
- Android 24 以下的缓存保护时间不再是零，避免把所有旧详情、安装包和区域临时文件永久归为暂不清理。兼容分支在 API 28 沙箱模拟 SDK 23，非旧系统真机验收。
- 富文本拆为正文选择、JSON、HTML 和行内附件解析，稳定入口和块模型不变；移除已迁出的旧图片查看器页、无调用方的请求容器及仅依赖旧私有字段的测试，替换为新实现的行为回归。`ImageViewerActivity` 从 1037 行降至 63 行，`RichContent` 从 1221 行降至 248 行，删除两项大小例外；新源码不超过 390 行，没有增加外部依赖。

## 表冠兼容层

- 按上游 `1.2.0` / `5c4680139f2ebe6ec1754a93e0a379c429944618` 更新现有 cnwearoverlay runtime，不启用 Xposed、ASM 插件或额外输入转换。
- 旧 OPPO 走上游马达服务代理，保留 View、系统及 Lite 触感开关和频率限制；失败不叠加无关波形。现代 OPPO、小米和原生 Wear OS 继续分别选择对应路径。
- 补 `android.permission.VIBRATE` 普通权限，满足会校验应用调用身份的马达服务；无运行时权限弹窗。声明和打包检查不能证明具体手表固件允许隐藏 API 或马达已实际震动。
- Release 附件为 Xposed 模块；本项目更新的是内嵌 runtime，并非已在手表安装该模块。没有已连接设备，不声称真机滚动或震感已验收。

## 验证状态

- 仅含本轮暂存代码的隔离目录完整执行 `:app:testDebugUnitTest`，耗时 27 分 12 秒：242 个套件、1631 项测试，0 失败、0 错误、1 项跳过。跳过项为 Windows 无符号链接权限时的 `ignoresSymlinkEscapesWhenSupported`。包含六项功能与 cnwearoverlay 1.2.0；全量完成后的分隔线回改、权限补充和无用请求容器删除另做下述专项验证，不冒充再次全量。
- 最终隔离代码执行 5 个套件、42 项回归，全部通过，0 失败/错误/跳过；`:app:lintRelease :app:assembleRelease :app:assembleDebugAndroidTest` 成功，耗时 2 分 59 秒。实际 APK v1/v2 签名通过，固定证书 `fbd5642c3c1b5882545f6f1227cf2dc38a54bcd18609203935eedbef408d1382`。三个隔离 APK 副本 SHA-256 相同：`432CF7282B38850C355678E32A6C799B4771DCD18EF62BE2C76A0CB33F6575E1`。
- 当前工作目录包含另一会话持续修改的 UI；较早集成全量为 1619 项、7 项 UI 断言失败、1 项跳过，失败位于 `ComposeDetailBehaviorUiTest`、`ComposeDetailVisualUiTest` 和 `ComposeSectionChipUiTest`。按用户要求不处理、不提交这些 UI 调整；本轮不声称整个实时工作目录全量通过。之后本轮重点集成 95 项通过；最后分隔线与权限相关 42 项也通过，Release、Lint 和测试 APK 均成功。
- Release Lint 为 0 errors / 124 warnings / 13 hints；既有 Gradle/API 弃用、SDK XML、v1 META-INF 提示仍保留。低版本分支模拟不代表 API 21 真机验收；仪器测试包只完成构建。
- 当前工作目录最终 Release 再构建成功，包含构建当时的未暂存 UI；不是仅含本轮提交的 APK。`app-release.apk`、`dist/heybox-Lite-2.19.apk` 和 `dist/heybox-Lite-latest.apk` 均为 3,858,689 字节，SHA-256 为 `4D3BC37C0D50CBF4353781A76C6CCEA6468A19BAFA52023FC2223B740AB459E1`，v1/v2 证书与上述固定值一致。实际包为 `com.ronan.heyboxlite.preview`、`2.19 / 222`、minSdk 21、targetSdk 35，非 debuggable，已确认包含 `VIBRATE` 权限。
- Java/Kotlin 大小、文本编码、`git diff --check` 与暂存新增内容凭据模式扫描通过；暂存区无 `WatchType/WatchRadius/WatchIcon/WatchTouchTarget` 或新 UI token 文件。已查看圆/方屏二级评论及长图离线渲染，像素检查覆盖无新增分隔线、清晰度图层交接与实际瓦片绘制。

## 尚需真机验收

- 圆屏与方屏手表的触摸、表冠、系统返回、上下拖动退出和 GIF 前后台表现。
- GPU 转场闪烁、实际帧率、真实图片峰值内存和不同厂商原生解码器。
- 真实服务返回的表情目录和抽奖链接；网页链接需要设备具备可处理的浏览器，非网页深链暂只保留标签，不声称已实现站内路由。
- 系统设置所报应用大小可能含系统管理的编译产物，不承诺应用内统计与系统总数完全一致。

离线 Compose 行为、真实 View 绘制和像素断言不能替代上述真机验收。
