# 教程布局与正文蓝链修复

## 问题与修复

- 手机教程原先与手表共用全屏、强制占满剩余高度的布局，导致短内容下方大面积留空；弹窗也没有明确避开系统栏。现在手机使用居中、最大 420dp 的紧凑面板，正文超高时滚动、底部操作保留；圆屏及小尺寸方屏继续使用全屏教程。系统边距由 Dialog 自身的 `WindowInsets.safeDrawing` 处理，不修改全应用窗口策略。
- 正文蓝链内部用私有标记保存地址、图标及标题。嵌套图标被正文图片解析器拆开，或标题里的标点、编号触发文章自动分段，都会截断标记，让 UTF-16 十六进制元数据进入正文。现在先规范链接标签，保留表情 token、去除链接内的装饰图片和 HTML 格式，再将整条链接作为不可拆分单元分段；隐藏元数据不再参与正文可见长度计算。没有把普通十六进制文本当作链接解码或直接隐藏。
- 纯图片锚点继续交给正文图片解析；普通正文分段规则、图片加载、表情目录与网络所有者不变。无 HTML/实体的标签走纯文本路径，避免不必要的 Android HTML 解析。
- 补齐截图所用 `/v3/bbs/app/api/web/share` 分享地址的站内路由，沿用原官方主机、协议、端口及 ID 检查；正文链接继续复用帖子导航，不新增接口调用。

## 回归范围

修复前新增的 15 项教程/正文链路用例中有 6 项失败，分别复现手机教程空白布局、嵌套图标被拆成正文图片、标题标点/编号导致元数据泄漏，以及链接内换行未规范。

最终检查包含：

- 手机竖屏、横屏、短横屏大字，以及 192dp 圆屏、227dp 圆屏大字、192dp 方屏大字的实际文字布局、按钮范围和滚动可达性。
- API 30 原生系统栏边距注入后，面板按可用空间重新居中、底部操作不与系统栏重叠；其余布局夹具使用 API 28。API 28 的兼容层合成边距不能代表物理设备，未将它列为系统栏验收结果。
- 从 `DetailContentParser`、HTML 解析、`ArticleText` 分段到 Compose 内联模型的整条离线路径，覆盖图标、格式、标点、编号、纯图片锚点和链接内官方表情。
- 对真实 `ComposeDetailBlock` 执行触摸点击，验证蓝链进入正确的站内帖子，而非浏览器；原评论蓝链、@用户、游戏链接和外部抽奖链接行为一并回归。

使用离线夹具，没有访问真实账号、执行登录或签到。未连接物理手表、未启动模拟器，离线排版检查不代表 GPU、系统字体和真实触控体验已经验收。

## 构建与交付

版本保持 `2.19 / 222`，沿用原固定签名。只提交并推送本次源码、测试和说明；不部署服务器、不发布更新、不上传 APK、不创建 GitHub Release。

修改前 APK 与 mapping 保留于本地忽略目录 `build-artifacts/before-tutorial-link-fix-20261115-113307/`。修复前失败证据和最终检查结果保存于 `build-artifacts/tutorial-link-fix-validation-20261115/`，不进入提交。

最终相关回归为 **20 类 / 117 项，0 失败、0 错误、0 跳过**；未运行完整套件。与 Debug/Release Lint、Release 和仪器测试 APK 构建同次完成，耗时 3 分 20 秒。Debug/Release Lint 分别为 0 错误、124/125 警告、13 提示，与修改前基线一致，未放宽检查。

```powershell
$testClasses = @('RichTextLinkPipelineTest','ArticleTextTest','RichGameLinkMarkupTest',
  'RichInlineReliabilityTest','OfficialContentLinkTest','RichLinkClassifierTest',
  'RichTransportDecoderTest','RichContentDiagnosticsTest','RichGameCardParserTest',
  'ComposeInlineLinkUiTest','ComposeContentLinkNavigationUiTest','ComposeInlineTextModelTest',
  'ComposeInlineTextUiTest','ComposeInlineLinksTest','ComposeDetailTypographyUiTest',
  'TutorialStateTest','ComposeTutorialUiTest','ComposeTutorialHostUiTest',
  'ComposeCheckinAccountGuideUiTest','ComposeFeedNavigationUiTest')
$arguments = @(':app:testDebugUnitTest')
foreach ($testClass in $testClasses) {
  $arguments += @('--tests', "com.ronan.heyboxlite.$testClass")
}
$arguments += @(':app:lintDebug', ':app:lintRelease', ':app:assembleRelease',
  ':app:assembleDebugAndroidTest', '--console=plain')
& .\gradlew.bat @arguments
```

Release 包名保持 `com.ronan.heyboxlite.preview`；v1/v2 签名通过，证书 SHA-256 保持 `fbd5642c3c1b5882545f6f1227cf2dc38a54bcd18609203935eedbef408d1382`。`app-release.apk` 与两份 dist 副本 SHA-256 一致：`5D56578EBC3ED32DFFDCF34F472EB68CC8D8F35729071F1B90A055C810429FFA`。仪器测试包只构建，未执行实机测试。
