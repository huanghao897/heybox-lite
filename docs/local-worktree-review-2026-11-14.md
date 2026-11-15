# 本地未完成改动审查与收尾

范围：`feature/compose-migration`，基线 `3faabb4`，版本保持 `2.19 / 222`。开始时有 75 个已跟踪文件修改及 3 个未跟踪 UI 文件，暂存区为空。逐项检查这些差异和相关调用方，在现有设计上修复，不回退另一会话的 UI。

## 已确认并处理的问题

| 严重程度 | 位置 | 触发与原因 | 处理 |
| --- | --- | --- | --- |
| 中等 | `ComposeCheckinAccountCard.kt`、`ComposeWatchComponents.kt` | UI 缩小时，断点也缩小但文字没有同比缩小，账号和脱敏 ID 被挤进窄列；大字菜单固定两行会省略入口名称。 | 账号断点按有效字号判断；窄屏身份信息纵向排列；菜单主标题自然换行，不截断入口名称。 |
| 中等 | `ComposeFeedComponents.kt`、`ComposeFeedCardActions.kt` | 小屏大字下，固定触控宽度与分区胶囊争抢同一行，短分区也只剩省略号。 | 将卡片底部操作独立拆出，根据实际文字和触控尺寸决定分行；保留点赞、评论数量和动作。 |
| 中等 | `ComposeDetailComponents.kt` | 操作栏用固定估算预留每个标签宽度，240dp 方屏也被迫显示两行。 | 测量实际计数文字，正常方屏维持一行；圆屏和不足宽度保留两列。 |
| 中等 | `ComposeCommentThread.kt` | 将整个回复胶囊扩大到触控尺寸，外观过厚；圆屏点赞触控区域贴近圆弧。 | 紧凑胶囊与 36dp 点击区域分开，保留五条一组展开；圆屏点赞保留少量局部边缘余量。二级回复仍与一级正文左侧对齐，不加分隔线。 |
| 中等 | `ComposeSearchController.kt`、`ComposeSearch.kt`、`ComposeRouteScreen.kt` | 请求失败只写日志，页面可能声称没有结果；更换关键词后也可能继续显示旧结果。 | 独立发布错误并提供重试，首屏请求开始后清除旧词结果；翻页失败保留已加载帖子和原始偏移；失败后不自动循环重试，迟到结果继续被拒绝。 |
| 中等 | `ComposeSearchInput.kt`、`ComposeWatchStatus.kt` | 固定搜索框高度会裁切较大字号；整页错误过长时重试按钮无法到达。 | 输入框随文字高度增长；有界整页状态可滚动，放进惰性列表时仍采用内联布局，避免嵌套无限高度。 |
| 轻微 | `ComposeTheme.kt` | 语义色作为构造器默认值，`copy(dark = false)` 仍携带深色状态色。 | 改为从当前明暗模式派生的只读属性，切换和预览不保留旧颜色。 |
| 轻微 | `ComposeReadingStats.kt`、`ComposeReadingStatsState.kt` | 为图表缩短文字时同时改变数据层日期标签，破坏原有语义和测试约定。 | 数据保留“今天”，仅图表绘制“今”。 |
| 轻微 | `ComposeSettingsDisplay.kt` | 设置预览仍在描述和展示已经被用户否定的二级评论竖线。 | 改为当前的回复文字和圆角背景示例，不额外缩进。 |
| 轻微 | `ComposeFavoritesScrollingUiTest.kt` | 测试通过合并后的整张卡片节点点击中心，小圆屏操作栏分行后会点到点赞按钮，无法证明标题是否可点击。 | 直接触摸实际绘制的标题，并独立触摸点赞按钮；同时断言帖子打开和点赞回调，不通过改变产品点击行为迁就测试。 |
| 轻微 | `tasks/compose-migration-status.md`、`tasks/compose-migration-spec.md` | 顶部迁移摘要仍称图片查看器未完成，与当前代码不符。 | 更新当前边界，保留带日期的历史记录；明确原生解码叶节点及实机验收仍然保留。 |

## 验证

- 初次集成回归：24 个测试类、210 项，7 项失败，实际复现签到文字截断、胶囊尺寸、圆屏点赞边缘、详情排布和分区挤压问题。
- 修复后的定向回归：7 个测试类、78 项通过，包含搜索失败/重试、失效回调、大字输入框、可滚动错误页及无限高度列表场景。
- 完整回归：245 个测试类、1,642 项，1,639 项通过、2 项失败、1 项跳过，耗时 25 分 37 秒。失败仅涉及上述收藏卡片测试的点击落点；修正后单独复测该类 6 项，全部通过。跳过的是 `DisposableStorageTest.ignoresSymlinkEscapesWhenSupported`，当前 Windows 环境无法创建该用例需要的符号链接。
- 完整回归后没有修改生产源码，只改了上述测试文件；对 824 个源码、测试和构建配置文件核对指纹，确认没有并发改动混入最终验证。完整回归与后续复测结果分别记录。
- 最终命令：`:app:testDebugUnitTest --tests com.ronan.heyboxlite.ComposeFavoritesScrollingUiTest :app:lintDebug :app:lintRelease :app:assembleRelease :app:assembleDebugAndroidTest --console=plain`，成功完成，耗时 1 分 39 秒。
- Debug Lint：0 错误、124 警告、13 提示；Release Lint：0 错误、125 警告、13 提示。Release 多出的项目是 Gradle 可用版本提示，未为消除提示改动构建工具版本；其余警告未在本轮批量清理。
- 已生成固定签名 Release 和 Debug 仪器测试 APK；没有执行设备端测试。APK 的 v1/v2 签名验证通过，证书 SHA-256 仍为 `fbd5642c3c1b5882545f6f1227cf2dc38a54bcd18609203935eedbef408d1382`。

## 构建产物

- 包名 `com.ronan.heyboxlite.preview`，版本 `2.19 / 222`，Release 非调试包，minSdk 21、targetSdk 35。
- `dist/heybox-Lite-2.19.apk`、`dist/heybox-Lite-latest.apk` 与 `app/build/outputs/apk/release/app-release.apk` 内容一致，大小 3,863,092 字节。
- APK SHA-256：`0AFE0827926387498FE32379D0729FBE8763D35D53229FA9DED0CA744CDB0316`。
- 原始源码、清单、旧 APK、mapping 和本次完整测试 XML 保留在 `C:/opencode/.codex_tmp/heybox-local-review-before-20261114-203419`。

## 2026-11-14 当时的保留事项

- 另一会话的 UI 工作继续保留；开始时的 78 个文件没有删除，59 个保持原样，其余仅叠加本轮针对性修复。共享 UI 改动继续未暂存，没有将其原有修改混入单独提交，本轮没有提交或推送。
- 本轮不更新版本、不部署、不上传安装包、不创建 GitHub Release。
- 当前 ADB 没有已连接设备。圆屏/方屏布局有 Robolectric 原生绘制和触控断言，不能替代手表 GPU、表冠触感、实际帧率和低内存实测。
- 原生兼容详情、验证码网页及媒体解码叶节点是明确保留的兼容边界，不因文件存在就删除。

## 2026-11-15 共享 UI 统一收尾

用户确认另一会话已经关闭，授权审核后一起提交其 UI 工作。以 `3faabb4` 为基线复核通用控件、设置、签到、会员、信息流、详情、评论、搜索和个人页，并保留此前已经完成的修复。这次没有增加依赖、修改版本号或改变官方接口。

复核补齐三处边界：

- 信息流操作栏根据每个按钮的实际测量宽度逐行安排；两个长计数超出一行时允许分别占一行，不再固定两列并挤压评论按钮。
- 我的页面和用户空间复用的 `WatchStat` 允许完整计数、单位和标题换行，行高与字号一起缩放，取消单行裁切。
- 搜索首屏错误使用与结果相同的惰性列表状态；表冠和手势都滚动当前错误内容，保留“重试”操作，不增加第二个请求或滚动所有者。

新增操作栏分行、192dp 圆屏大字统计与真实触摸动作测试，并补搜索错误页的表冠滚动和重试点击测试。先行定向 4 类 / 9 项通过，随后补齐统计行高和相应断言，纳入下面的最终完整回归。最初新增截图夹具缺少原生绘图配置，以及离屏控件使用裁切后的坐标判断移动，均已修正夹具；保留对实际文字排版、滚动位置和回调的断言，不放宽产品行为来迁就测试。

此前为等待复核而开始的完整命令已中止，没有将它记作通过。最终代码重新执行完整测试、Debug/Release Lint、固定签名 Release 和仪器测试包构建：

- 命令为 `:app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleRelease :app:assembleDebugAndroidTest --console=plain`，成功完成，耗时 25 分 55 秒。254 个非空测试类、1678 项，1677 项通过、0 失败/错误、1 项跳过；跳过仍是 `DisposableStorageTest.ignoresSymlinkEscapesWhenSupported`，当前 Windows 环境无法创建该用例所需的符号链接。
- Debug Lint 为 0 错误、124 警告、13 提示；Release Lint 为 0 错误、125 警告、13 提示。既有 Gradle 弃用与 Lint 提示保留，没有增加依赖或屏蔽检查。签名校验的 62 条提示均为 `META-INF/` 条目提示，v1/v2 验证仍通过。
- 838 个源码、测试和构建配置文件在完整命令前后的指纹一致：`NUCJQwZATRqAuu0uhAWS1x46OjoUqT1fXq74/ajBsps=`。之后仅补验证文档；按 105 个明确文件暂存，未包含日志、凭据或安装包。
- 最终 APK 保持 `com.ronan.heyboxlite.preview`、`2.19 / 222`、minSdk 21、targetSdk 35，非调试包，固定证书 SHA-256 为 `fbd5642c3c1b5882545f6f1227cf2dc38a54bcd18609203935eedbef408d1382`。三份 APK 均为 3,867,282 字节，SHA-256 一致：`1B66AAACA74E48A0428FD22154B3548D46BADFF70246193B7E8D37135C5B30E7`。
- 最终测试 XML、Lint、APK、mapping 和输出元数据保存在 `C:/opencode/.codex_tmp/heybox-ui-final-validation-20261115/final-validated/`，不覆盖此前的中间结果。仪器测试仅构建，未启动模拟器或进行真实手表 GPU、表冠与账号联网验收。
