# Compose 迁移任务

- [x] Task: 接入 Kotlin 与 Compose 编译链
  - Acceptance: Java 现有模块和一个最小 Compose 测试可以同时编译。
  - Verify: `:app:testDebugUnitTest`、`:app:assembleRelease`
  - Files: 根目录 Gradle 配置、`app/build.gradle`、Compose 基础目录

- [x] Task: 建立统一 Compose 主题与窗口信息
  - Acceptance: 黑灰主题、界面缩放、文字缩放、圆屏/方屏状态可以从现有 SessionStore 映射。
  - Verify: Compose 单元测试与 4:3、1:1、圆屏截图
  - Files: `app/src/main/kotlin/.../ui/theme`、主题测试

- [x] Task: 迁移应用 Shell
  - Acceptance: 首页、我的、设置页面可以在 Compose Shell 中切换，返回和动画等级行为不回归。
  - Verify: 导航行为测试、真机手势测试、Release 构建
  - Files: Shell、导航状态、MainActivity 适配层

- [x] Task: 迁移设置和低风险页面
  - Acceptance: 所有设置状态与现有值一致，输入、开关、滑杆和弹窗可用。
  - Verify: 状态恢复测试、圆屏/方屏截图、真机操作
  - Files: 设置 Composable、状态映射和测试

- [x] Task: 迁移信息流和搜索
  - Acceptance: 分页、下拉刷新、预取、缓存图片、帖子类型标签和列表位置保持一致。
  - Verify: 分页单测、滚动行为测试、真机性能测试
  - Files: Feed/Search Compose 页面和适配器

- [~] Task: 迁移详情、评论、图片和视频
  - Acceptance: 正文、评论、游戏卡片和详情路由已使用 Compose；图片手势与播放器仍由兼容层承载，待真机验收后再迁移。
  - Verify: instrumented/screenshot tests + 多品牌手表手测
  - Files: Detail/Comment/Image/Video Compose 页面和手势桥接

- [~] Task: 删除无引用 View 并完成发布验证
  - Acceptance: 不删除仍被媒体、WebView、崩溃恢复或旧 ROM 路径使用的 View；签名和 APK 输出路径保持正确。
  - Verify: 全量测试、Release、签名、R8、git diff 审查
  - Files: 仅删除已确认无引用的旧实现
