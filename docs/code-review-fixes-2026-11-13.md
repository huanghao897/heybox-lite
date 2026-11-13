# 2026-11-13 审查问题修复记录

## 基线与范围

- 基线提交：`017bb9f1bff391a7a0cba4415d9c16bbc8c68a4e`，分支 `feature/compose-migration`。审查确认 16 项：高危 3、中等 12、轻微 1；本批逐项修复，不重写 UI 或更改官方 API、签到协议。
- 修改前完整回归为 203 个套件、1383 项测试通过。测试通过不等于没有缺陷，原审查用生产调用链和离线探针确认了异步及资源问题。
- 保持 `2.19 / 222`、原包名与固定 Release 签名；不增加依赖，不修改第三方提取代码、服务器或 `build-artifacts/`。此前 PJX110 认证修复已在基线中，本批不重复重置设备。

## 逐项落实

| 编号 / 等级 | 确认的问题 | 修复落点 | 主要行为证据 |
|---|---|---|---|
| 01 高危 | 原生评论迟到成功清掉另一篇帖子的草稿 | `NativeCommentDrafts` 绑定 entry、编辑器、编辑版本和发送快照；`CommentController` 只清原草稿，仅当前原 entry 可刷新 | `NativeCommentOwnershipTest` 覆盖切帖、编辑中、同文案重新编辑、同帖重开、新回复编辑器和迟到失败 |
| 02 中等 | 详情渐进替换覆盖已成功或在途的收藏 | `PostInteractionState` 按稳定帖子 ID 统一持有待定/确认互动，完整文档与保留页面合并覆盖值 | `PostInteractionStateTest`、`ComposePostInteractionTest` 覆盖成功/失败、文档替换、乱序完成和会话切换 |
| 03 中等 | 返回加载中的父详情后请求/正文不恢复 | `DetailLoadCoordinator.State` 保留加载阶段、待渲染内容/失败；恢复按阶段续载，`NativeDetailViewUpdate` 复用原分页器与滚动所有者 | `NativeDetailRecoveryTest` 覆盖在途请求、排队正文、网页补充、嵌套返回与历史丢弃，含圆屏 |
| 04 中等 | 帖子根对象通用 ID 被误当作作者 ID | `AuthorIdentity` 优先已知用户对象的身份字段，根对象只允许显式用户字段；列表、关注、详情头和作者判断共用 | `AuthorIdentityTest`、`ComposePostInteractionTest` 覆盖同一 JSON 的列表和详情映射，不把帖子 ID 用作关注目标 |
| 05 中等 | Compose 同作者其他卡片不更新关注状态 | 关注按作者 ID 归属同一操作 owner；`ComposePostInteractionBinding` 覆盖信息流、搜索、阅读库、用户空间及保留详情，新数据发布同样合并 | `ComposePostInteractionTest` 覆盖同作者多卡片、原生与 Compose、后续集合发布及旧回调 |
| 06 中等 | Compose 签到/二维码页面后台仍轮询和计时 | `CheckinFlowLifecycle`、`ComposeCheckinActivityLifecycle` 绑定实际 Activity；`CheckinRequestScope` 暂停已批准的延迟重试，保留合法在途结果；QR/配对限制单在途 | `ComposeCheckinLifecycleRegressionTest`、`NativeCheckinLifecycleRegressionTest`、`CheckinCoordinatorLifecycleRegressionTest`、`ComposeLoginLifecycleRegressionTest` 执行 pause/stop/resume/close |
| 07 高危 | 长图全量解码并驻留，绕过内存预算 | 压缩源流入私有文件；`LongImageTileView` 只解码视口及邻近瓦片，`ImageTileBudget` 计入解码/交付/显示；离屏、暂停、关闭释放 | `LongImageViewportTest` 使用实际区域解码与原生图形后端检查峰值、回收和失败；`ImageViewerRequestLifecycleTest` 检查失败保留预览与可重试 |
| 08 中等 | 视图回收未取消排队图片任务 | `ImageViewBinding` 持请求，取消时移除队列并释放回调；`ImageWorkToken` 在 I/O/解码前检查并断开连接；队列有界、近期优先 | `ImageQueueBehaviorTest` 阻塞工作线程，检查真实绑定回收、队列上限、协作取消，以及离线下载/大小探测溢出必定完成 |
| 09 高危 | 自动跨主机重定向可转发设备令牌 | `RemoteConfig`、`AnnouncementChecker` 明确禁止自动重定向，更新保持同一策略；配置仅向初始受信 URL 发送设备头 | `MetadataClientBoundaryTest` 在 API 24/28 验证连接设置、拒绝跨来源跳转，公告不发送认证头 |
| 10 中等 | 更新、公告、配置响应无大小上限 | `MetadataResponse` 限制声明长度及实际解压后 256 KiB，检查中断与 15 秒累计读取期限，统一关闭输入流 | `MetadataResponseTest` 覆盖精确上限、错误/缺失长度、UTF-8 字节、gzip、错误正文和超时；各生产客户端及更新超限回归 |
| 11 中等 | 过滤空页误判结束，阻断后续结果 | 信息流用原始数量/游标进展/服务端结束信号；搜索以原始返回数量推进 offset，显示数独立；空页最多自动推进两次，保留手动继续入口 | `FeedCollectionTest`、`ComposeFeedControllerTest`、`ComposeSearchControllerTest` 覆盖全过滤、重复页、游标停滞、原始 offset、串行边界与继续加载 |
| 12 中等 | 旧 SMS/验证码结果污染重新进入的登录 | 手机及服务账号流程使用尝试代次、手机号/SMS session 归属；旧验证码 Activity requestCode 不能回填新流程，后台返回合法结果不重发 | `CheckinMobileAttemptRegressionTest`、`CheckinServiceAttemptRegressionTest`、`NativeCheckinLifecycleRegressionTest` 覆盖发送/提交/密码/验证码迟到及重入 |
| 13 中等 | 任务设置离页后保存失败，UI 永久忙碌 | `CheckinTaskSettingsFlow` 为权威忙态，完成时先发布控件状态，不因页面不可见跳过；Compose 只做投影 | `ComposeCheckinLifecycleRegressionTest` 覆盖离页失败后再次保存、隐藏成功、前后台恢复不重写 |
| 14 中等 | 永久损坏的加密 Cookie 阻止新登录覆盖 | `SessionCookies` 区分永久 bad-tag/损坏与暂时密钥不可用；写入失败不替换缓存/会话代次，并报告给请求错误；QR 确认凭据后才完成 | `SessionCookieMigrationTest`、`ApiSessionLifecycleTest` 覆盖永久恢复、临时保留、加密/持久化失败及实际 API 登录错误；Compose QR 无假成功 |
| 15 轻微 | 正文 renderer 直接持客户端加载游戏资料 | `NativeDetailGamesController` 拥有按 entry 保存的结果、前台串行批次和不可变快照；`DetailContentRenderer` 不再持 `ApiClient` | `NativeDetailGamesControllerTest` 覆盖只渲染不请求、同步缓存命中、挂起父页、替换视图、后台停批、失败与销毁 |
| 16 中等 | 源码字符串检查替代了生命周期/异步行为验证 | 补充真实 Activity、Compose owner、原生页面、队列和解码器行为回归；原静态接线测试只保留作辅助并更新拆分后的调用边界 | 上述测试真实调用生产对象及延迟回调；不以包含某个 import/方法名作为行为修复证据 |

## 生命周期与回调矩阵

| 边界 | 暂停/离页 | 恢复/重新进入 | 迟到结果/销毁 |
|---|---|---|---|
| 签到配对与二维码 | 移除轮询调度，不重置有效在途请求 | 同会话按原最早时间恢复，至多一个轮询在途 | 重入用新代次，关闭后丢弃旧结果 |
| 手机/服务账号登录 | 停倒计时与验证码启动；保留本次合法请求 | 根据实际截止时间更新；不重复发送 SMS/登录/批准写操作 | 原手机号和尝试代次匹配才接收；验证码 resultCode 属于原请求 |
| 签到状态/执行/撤销 | 停页面请求作用域中的延迟重试 | 仅继续原策略批准的剩余重试，不增加预算 | 合法完成只落地一次，关闭后不交付页面 |
| 任务设置保存 | 页面隐藏仍完成权威忙态 | 从 Flow 投影，不带旧 saving 副本 | 结果不强制重开设置，不重复写入 |
| 原生父详情 | 保存文档/视图/未完成阶段 | 续载未完成阶段并复用分页器、两页滚动位置 | 旧 token 不能填入子详情；丢弃历史后不复活 |
| 原生游戏资料 | 暂停时不发布视图或发下一批 | 同 entry 使用已有结果和在途状态 | 挂起父页只保留数据，丢弃/销毁后不回填 |
| 图片绑定/长图 | 回收取消排队工作；长图暂停释放瓦片 | 重新绑定按当前视口请求 | 取消交付回收独占结果；关闭源和临时文件 |
| 点赞/收藏/关注 | 状态属于会话与 ID，不属于 View | 新文档/集合合并同一覆盖值 | 旧操作不能覆盖新确认值；账号切换清 owner |

## 结构与粒度

本批只按确认的业务边界拆分，不引入依赖、通用任务框架或新的页面架构。视图工厂、异步 owner、I/O 与资源预算各自独立。

| 生产类 | 修改前行数 | 修改后行数 |
|---|---:|---:|
| `MainActivity` | 1479 | 1436 |
| `ImageViewerActivity` | 1080 | 1037 |
| `ImageLoader` | 735 | 571 |
| `SessionStore` | 900 | 890 |
| `CheckinCenterPage` | 724 | 690 |
| `PostActionController` | 499 | 428 |
| `CommentController` | 489 | 369 |
| `DetailContentRenderer` | 423 | 382 |

`FeedPage` 降至 445 行并移出遗留大小例外。新文件遵守默认 500 行限制；Java 遗留上限随实际拆分降低，没有提高相对基线的上限。`MainActivity`、图片查看器、`RichContent` 等仍为遗留大类，未宣称已完成全项目拆分。

## 验证记录

- 第一轮完整执行 1488 项，6 项失败全部来自 `ComposeNativeDetailHandoffTest` 的旧构造方法链接；修正夹具调用，不移除断言。其后复核还补上原生验证码交接、图片队列溢出完成和长图解码失败恢复覆盖。
- 最新专项 8 个类、41 项全部通过，0 失败、错误、跳过，覆盖上述最新修改与原生详情交接。
- 专项后补上验证码清理归属回归：未启动验证码的 owner 关闭时，不能清除另一个 owner 的监听器。此前为补修而中止的完整命令不计为通过。
- 最终重新执行 `:app:lintDebug :app:testDebugUnitTest :app:assembleRelease :app:assembleDebugAndroidTest --console=plain`，全部成功，耗时 22 分 23 秒。新生成的 JUnit XML 共 219 个套件、1496 项测试，0 失败、错误、跳过，覆盖全部最终生产修改。
- Lint 报告于 2026-11-13 18:35（Asia/Shanghai）重新生成：0 errors、124 warnings、14 hints。总警告数与基线相同，但不是没有新增警告：`LongImageTileView` 增加一条 `ViewConstructor`，该控件仅由程序传入图片源创建，不供 XML/布局工具实例化；保留警告，不添加不可用的空构造器或压制规则。
- Java/Kotlin 大小、编码及 `git diff --check` 通过；新增内容的凭据模式扫描未发现匹配项，没有把日志、密钥、APK 或第三方文件纳入修改。
- 实际 Release APK 保持 `com.ronan.heyboxlite.preview`、`2.19 / 222`、minSdk 21、targetSdk 35。v1/v2 签名通过，证书 SHA-256 为 `fbd5642c3c1b5882545f6f1227cf2dc38a54bcd18609203935eedbef408d1382`。
- `app/build/outputs/apk/release/app-release.apk`、`dist/heybox-Lite-2.19.apk` 和 `dist/heybox-Lite-latest.apk` 均为 3,835,859 字节，SHA-256 一致：`0CE4AB5CCA8869B15D947F1557FEF92DAF88408FFA004894918224D559BB37FD`。

## 验证边界与后续

- 不执行真实账号登录/签到或生产接口调用，不修改服务器，不上传包、不创建 Release、不操作模拟器或关机。仪器测试包编译不等于在设备上运行。
- 必须在圆屏/方屏手表上补验图片 GPU 转场、峰值堆内存、快速滑动、息屏/唤醒、表冠及 OEM WebView；离线测试不能证明这些真机性能指标。
- 元数据 15 秒期限在读前后及每次读取检查；阻塞的一次读取仍受客户端 readTimeout 约束，不宣称独立严格 15 秒看门狗。
- 保留现有未改动的 Lint/Gradle/API 弃用提示，不通过压制规则隐藏。后续设置、用户空间等页面仍可按完整行为补充重建/错误恢复覆盖，不把这次 16 项关闭泛化为项目已无缺陷。
- 后续拆分优先沿剩余 Activity 回退页面装配、图片查看器转场/页面管理、`RichContent` 解析边界进行；每批限定一个行为及其调用方，保留可回退提交和对应回归。
