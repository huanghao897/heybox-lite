# 2.20 更新范围核对

## 基线

- 2.19 首次升版提交：`5620cf7`；正式标签 `v2.19` 指向 `dd8f419`。
- 已有功能截止提交：`4ba76c8`。核对 `5620cf7..4ba76c8` 的 11 个后续提交及最终代码差异，同时比较正式标签后的差异，避免只依据提交标题编写日志；另包含本轮用户追加的长图缩放闪烁修复。
- 新版本为 `2.20 / 223`，保留包名、最低系统版本、固定签名和现有接口。
- 公开日志位于 `CHANGELOG.md`。2.19 已列出的整套 Compose 界面迁移、会员购买等不重复作为新增；中间方案及随后撤回的样式不列入最终变化。内部拆分类、安全实现、后台和心跳不写入用户日志。

## 逐项映射

| 用户可见变化 | 当前实现及核对依据 |
| --- | --- |
| 省流占位、点击加载 | `SessionMediaSettings`、`ComposeContentImageGate`；信息流、详情、评论、视频与游戏封面使用同一内容图片入口，头像和表情不经过该限制 |
| 分场景教程、两类签到账号指引、首页重选刷新 | `TutorialCatalog`、`TutorialState`、`ComposeTutorial`、`ComposeCheckinAccountGuide`、`ComposeFeedNavigation`；参考 `3df8c36`、`4ba76c8` |
| 图片查看器、长图与清晰度 | `ComposeImageViewer`、`ComposeViewer*`、`LongImageTileView`、`ImageDecodePolicy`；保留原图选项但不再因圆屏强制请求原图 |
| 长图放大闪烁 | `LongImageTileView` 固定源坐标图块网格，缩放期间继续绘制已有图块，清晰版本就绪后替换；内存受限或升级失败时保留旧画面，缩小时不降级已解码图块 |
| 图片列表变化、加载退出、双击动画 | `ComposeDetailImages`、`ComposeViewerGestures`、`ComposeViewerTransform`；对应图库快照、请求取消、双击动画行为回归 |
| 蓝链、站内帖子与用户跳转、官方表情 | `RichInlineMarkup`、`RichGameLinkMarkup`、`ArticleText`、`OfficialContentLink`、`EmojiRenderer`、`EmojiStore`；正文、一级和二级评论共用行内渲染 |
| 二级评论及回复胶囊 | `ComposeCommentThread`、`ComposeCommentSupport`；最终保留圆角底色、父级正文对齐与原胶囊，无新增醒目分隔线 |
| 互动状态和评论草稿 | `PostInteractionState`、`ComposePostInteractionBinding`、`NativeCommentDrafts`；异步覆盖、同作者同步、迟到发送结果分别有行为回归 |
| 通用界面、小屏排版、阅读参数 | `ComposeWatchTokens`、`ComposeWatchStatus`、`ComposeWatchComponents`、`ComposeFeedCardActions`、`ComposeReadingStyle` 及实际调用页面；未将共享组件文件存在等同于全机型验收 |
| 信息流和搜索分页、失败重试 | `FeedCollection`、`ComposeFeedController`、`ComposeSearchController`、`ComposeSearch`；原始页进度与显示数量分离，错误保留已有翻页结果 |
| 导航、返回和阅读恢复 | `ComposeBottomNavigation`、`BottomNavigationController`、`NativeDetailNavigator`、`DetailLoadCoordinator`、`MainActivityLifecycle`、`ComposeDetailContext` |
| 滚动、任务取消、表冠自定义与触感 | `OfficialPullRefresh`、`ImageWorkToken`、`ImageNetworkAbort`、`ComposeFeedRotaryInput`、`ComposeSettingsContent`、`CrownScrollController`、cnwearoverlay runtime 1.2.0 |
| 空间统计与清理 | `DisposableStorage`、`CacheMaintenance`、`SavedOfflineStorage`、`StorageFileLeases`、`ComposeStorageMaintenance`；只统计确认删除的字节，保护离线内容和使用中的文件 |
| 登录、签到与更新稳定性 | `SessionCookies`、`WriteTokenProvider`、`CheckinFlowLifecycle`、`CheckinRequestScope`、`UpdateChecker`；二维码和验证码代次、任务设置恢复及正式更新降级路径均有专项回归 |

## 本轮长图闪烁修复

- 旧 `windowViewport()` 在显示宽度每次变化时重置图块高度；`viewport()` 随后取消请求并回收全部位图。原实现的回归测试在宽度从 240 变成 250 时，中心像素由绿色变成透明，确认存在空白帧。
- 源区域网格改为随实际视口划分，缩放只调整绘制和所需清晰度。旧图块在替换版本解码完成前保留，缩小复用清晰版本；共享额度同时包含新旧位图，升级失败或预算不足不清空可用图块，不反复尝试同一失败清晰度。
- 新增 4 项图块行为测试，覆盖连续缩放、缩小复用、升级失败与受限共享额度；新增圆、方屏 Compose 像素测试，在解码队列阻塞时验证缩放仍绘制图像。
- 原图切换测试改为等待预览图块就绪并先检查实际绿色画面，再阻塞替换解码；避免仅收到首块就开始验证尚未显示的预览。未放宽颜色或所有权断言。

## 验证

- 本轮递增版本、整理日志，并修复用户追加的长图缩放闪烁，不重新实现其他已有功能。23 个相关测试类、117 项测试全部通过，0 失败、错误或跳过；没有重新运行完整套件，不将旧文档的全量测试数量算作本次结果。
- 实际命令如下，耗时 5 分 27 秒，`BUILD SUCCESSFUL`：

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests com.ronan.heyboxlite.UpdateCheckerTest `
  --tests com.ronan.heyboxlite.UpdateCheckerNotesTest `
  --tests com.ronan.heyboxlite.UpdateAuthorizationTest `
  --tests com.ronan.heyboxlite.InstalledApkFingerprintTest `
  --tests com.ronan.heyboxlite.UpdateInstallerRoundUiTest `
  --tests com.ronan.heyboxlite.ComposeDataSaverSettingsUiTest `
  --tests com.ronan.heyboxlite.ComposeViewerZoomAnimationUiTest `
  --tests com.ronan.heyboxlite.ComposeViewerLoadingExitUiTest `
  --tests com.ronan.heyboxlite.ComposeDetailGalleryUpdatesTest `
  --tests com.ronan.heyboxlite.RichTextLinkPipelineTest `
  --tests com.ronan.heyboxlite.EmojiRendererReliabilityTest `
  --tests com.ronan.heyboxlite.TutorialStateTest `
  --tests com.ronan.heyboxlite.ComposeTutorialUiTest `
  --tests com.ronan.heyboxlite.ComposeTutorialHostUiTest `
  --tests com.ronan.heyboxlite.ComposeCheckinAccountGuideUiTest `
  --tests com.ronan.heyboxlite.ComposeCrownSettingsUiTest `
  --tests com.ronan.heyboxlite.StorageCleanupTextTest `
  --tests com.ronan.heyboxlite.ComposeCommentCompactUiTest `
  --tests com.ronan.heyboxlite.LongImageViewportTest `
  --tests com.ronan.heyboxlite.ComposeViewerLongImageUiTest `
  --tests com.ronan.heyboxlite.ComposeLongImageViewportTest `
  --tests com.ronan.heyboxlite.ComposeViewerHandoffUiTest `
  --tests com.ronan.heyboxlite.LongImageSourceCloseRaceTest `
  :app:lintDebug :app:lintRelease :app:assembleRelease --console=plain
```

- Debug/Release Lint 均为 0 错误，分别保留 124/125 条警告和 13 条提示，与升版前基线一致；Gradle/API 弃用提示保留，未屏蔽检查。
- 从安装包读取到 `2.20 / 223`、`com.ronan.heyboxlite.preview`、minSdk 21、targetSdk 35。v1/v2 签名验证通过，证书 SHA-256 为 `fbd5642c3c1b5882545f6f1227cf2dc38a54bcd18609203935eedbef408d1382`，并与现有 2.19 安装包逐一比较一致。
- `dist/heybox-Lite-2.20.apk`、`dist/heybox-Lite-latest.apk` 和 `app/build/outputs/apk/release/app-release.apk` 均为 3,877,236 字节，SHA-256 均为 `BDF97FB2B6A17B61621BF9FAFFA19A0B3D728D8E4022CF1E36BDEA38ABF7194A`。
- 原 2.19 安装包、latest 副本、mapping 和输出元数据另存于忽略目录 `build-artifacts/before-version-2.20-20261115-121919/`；历史产物没有清理。

本轮未连接手表、不启动模拟器，不执行真实账号登录或签到，不发布更新服务器或 GitHub Release。自动化检查与签名一致不能替代圆屏、方屏、表冠、GPU 动画及低内存真机验收。
