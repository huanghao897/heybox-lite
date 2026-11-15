# 使用教程与首页回顶刷新

## 范围

- 参考本地 `reference/BiliClient-reference` 的 `TutorialActivity`、`TutorialHelper` 和页面 XML，采用按页面分组的教程；不复制其已经禁用的触发逻辑、强制阅读倒计时或不可返回行为。
- 首页、帖子与评论、我的、搜索、图片查看器、自动签到入门、服务账号登录/注册、小黑盒手机号登录分别提供教程。首次到达相关页面时展示，设置中的“使用教程”可以逐组重看。
- “稍后”和系统返回只关闭当前教程，不伪造已读；本次宿主会话不再自动展示同组教程，重启后仍可提示。只有最后一步“知道了”按教程版本保存完成状态，不与账号、App 版本或凭据混存。
- 教程使用当前主题、字号与按钮；长内容可滚动，操作按钮固定，圆屏按窗口宽高放置。教程不执行实际登录、刷新、发送评论或签到。

## 行为与所有者

- `TutorialCatalog`：页面映射和教程内容。详情的实际方向为**左滑正文进入评论，右滑评论回正文**；正文右滑仍是页面返回。教程不能把返回手势写成进入评论。
- `TutorialStore` / `TutorialState`：分别保存完成版本、拥有当前展示与当次稍后状态，不访问网络或账号凭据。
- `ComposeTutorial`：触发、可滚动教程、固定操作与教程目录。只有当前活动页面提供触发环境，滑动预览层不能提前触发；离开页面关闭该页面的自动教程。
- `ComposeCheckinAccountGuide`：登录/注册表单内持久显示两类账号的区别，不因教程已读而隐藏。先注册/登录签到服务，再按实际会员状态和页面指引手机号登录小黑盒；扫码浏览登录不能替代签到凭据。
- `ComposeFeedNavigation`：当前首页胶囊重选信号，仅由活动列表消费。先回顶，再经原 `ComposeFeedController` 刷新；正在加载时保留一个待刷新意图，等原请求结束后执行。重复点击不能创建并行请求，保留旧 View 回退。

## 验证

- 先完成 18 个相关测试类、142 项回归，0 失败/错误/跳过，覆盖首页控制器与重选、账号/阅读生命周期、详情分页、签到表单、导航和图片手势。随后调整教程标题的圆屏位置、补齐设置目录及真实宿主用例，最终 8 类、47 项全部通过；两轮有重复，不合计为一次全量测试。
- 最终用例覆盖教程完成/稍后/重看、非活动预览不触发、真实宿主的系统返回、加载中首页重选、图片加载退出与缩放、圆/方屏底部胶囊，以及 192dp 圆屏、227dp 圆屏大字、192dp 方屏大字的实际文字布局和触控范围。所有网络使用夹具，没有执行真实登录或签到。
- `:app:lintDebug :app:lintRelease :app:assembleRelease :app:assembleDebugAndroidTest` 成功。Debug / Release Lint 分别为 0 错误、124 / 125 警告、13 提示，与本轮前基线一致；未禁用检查。源码大小、编码和差异检查通过，新增生产组件 33～136 行，`ComposeAppHost` 为 493 行。
- 版本保持 `2.19 / 222`，包名仍为 `com.ronan.heyboxlite.preview`。固定证书 SHA-256 为 `fbd5642c3c1b5882545f6f1227cf2dc38a54bcd18609203935eedbef408d1382`；Release v1/v2 校验通过。三份 APK 的 SHA-256 均为 `8F05123F3FFEAEDB867E84583C38025C6C50CB361BC6572A22658EF15492D191`。
- 回归 XML、排版截图、Lint 和产物备份保存在本地忽略目录 `build-artifacts/usage-tutorial-validation-20261115/`；修改前 APK 与 mapping 另存于 `build-artifacts/before-usage-tutorial-20261115-103629/`，不提交工件。

最终相关测试命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests com.ronan.heyboxlite.TutorialStateTest `
  --tests com.ronan.heyboxlite.ComposeTutorialUiTest `
  --tests com.ronan.heyboxlite.ComposeTutorialHostUiTest `
  --tests com.ronan.heyboxlite.ComposeCheckinAccountGuideUiTest `
  --tests com.ronan.heyboxlite.ComposeViewerLoadingExitUiTest `
  --tests com.ronan.heyboxlite.ComposeViewerZoomAnimationUiTest `
  --tests com.ronan.heyboxlite.ComposeSwipePresentationTest `
  --tests com.ronan.heyboxlite.ComposeBottomNavigationUiTest `
  :app:lintDebug :app:lintRelease :app:assembleRelease :app:assembleDebugAndroidTest --console=plain
```

未运行完整测试套件；仪器测试包只构建。未连接真机或启动模拟器，真实手表的 GPU、表冠和触控体验尚未验收。不修改服务器、不发布更新或上传 APK，只按相关文件列表提交并推送当前分支。
