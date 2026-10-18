# HeyBox Lite 代码边界

项目保持单 APK。当前迁移分支以 Kotlin + Jetpack Compose 承载主界面，Java 保留网络、缓存、媒体和旧 ROM 兼容层。页面类负责组合视图与转发生命周期，网络请求、持久化、手势和有状态业务流程必须由独立组件负责。

## 当前边界

- `MainActivity`：Activity 生命周期和顶层组件装配，仍保留待拆分的原生回退布局与导航；原生内容回调接线由 `MainContentHost` 承担。原生回退详情请求由 `DetailLoadCoordinator` 管理，Compose 详情由路由状态拥有数据，`ComposeDetailRuntime` 只加载和发布文档，不再向 Activity 镜像一份详情元数据。
- `BottomNavigationController`：底部导航的挂载、尺寸更新和显隐；`ComposeBottomNavigation` 只绘制胶囊与处理点击。主题、圆屏模式或界面缩放变化时，宿主尺寸、图标尺寸和底部避让必须同步更新。
- `CheckinCenterPage`：签到子页面切换、生命周期和业务回调接线。配对轮询、服务账号、手机号登录、任务设置分别由对应的 `Checkin*Flow` 管理，输入视图由 `CheckinAccountForms` 管理，稳定内容状态由 `CheckinCenterPageRenderer` 创建，结果文案由 `CheckinResultText` 统一生成。
- `CheckinCenterClient` / `CheckinCenterTransport`：前者负责签到业务入口、异步回调和协议模型，后者负责固定 HTTPS 地址、响应大小、重定向和网络错误边界。
- `*Flow` / `*Coordinator`：拥有一个完整业务流程的状态和异步请求，不直接承担整页视觉设计。
- `*Ui` / `*Renderer` / `*Forms`：只创建或绑定视图，不发起业务网络请求。
- `SessionStore` / `LocalCache`：分别负责设置与会话、内容缓存，不承接页面导航逻辑。
- `ComposeAppHost`：生命周期、路由及共享控制器装配；`ComposeRouteScreen` 只选择页面，不执行请求。
- `ComposeSavedController`：协调收藏、收藏夹和历史的独立状态，不混入页面 UI。`ComposeSavedPostsController` 负责账号隔离、缓存和请求失效；`ComposeWatchLaterController` 负责本地读取、图片大小统计和删除；`ComposeReadingCenterController` 负责异步摘要。
- `ComposeFavoritesScreen` / `ComposeHistoryScreen` / `ComposeWatchLaterScreen` / `ComposeReadingCenterScreen`：各自的阅读库页面，共享 `ComposePageHeader` 和 `ComposeSearchInput`。
- `ComposeReadingStatsMapper`：纯阅读统计展示映射；日期、时区、比例和格式结果可独立测试，不增加计时业务。
- `ComposeReadingSettings` / `ComposeReadingStyle`：保存参数的可观察快照及统一阅读排版。正文、一级评论和设置预览不得各自维护互相不一致的加粗、字间和行距规则。
- `ComposeDetailContext`：从当前详情路由派生的操作快照；评论请求、诊断与前台阅读计时读取它，不从 Activity 的原生回退字段读取 Compose 数据。离开详情后不暴露上一个帖子的写入上下文。

## 修改规则

1. 新功能不得继续堆入 `MainActivity` 或 `CheckinCenterPage`；先确定业务所有者，再新增或扩展聚焦组件。
2. 新 Java / Kotlin 文件默认不超过 500 行。Java 遗留大文件的行数上限记录在 `config/java-size-limits.properties`，只能随拆分降低，不能提高。Compose 页面、状态映射、控制器分别保留单一职责，不用混合页面大文件规避限制。
3. 一个异步流程只能有一个状态所有者。页面暂停后，轮询、倒计时和动画调度必须停止。
4. UI 工厂不持有网络客户端；网络协调器不直接拼装复杂 View 树。
5. 不为受信任的内部调用叠加重复空值检查或宽泛 `catch (Throwable)`。边界校验放在外部输入、存储和网络响应入口。
6. 拆分必须保持现有接口行为，并为可独立验证的状态逻辑增加单元测试。
7. 圆屏正文保持可读宽度，顶部和底部固定控件按各自位置适配圆弧。不得用最窄圆弧的安全区压缩整页列表；渲染测试必须明确区分 dp、物理像素、页面坐标与局部位图坐标。

`verifyJavaClassSize` / `verifyKotlinSourceSize` 会在编译前检查这些限制。需要修改大文件时，应先抽取职责并同步降低其上限。文字编码检查覆盖 Java、Kotlin 和资源。

验证分为纯逻辑、Robolectric Compose 行为与真实 View 渲染、真机三层。前两层通过不能替代手表上的 GPU、表冠、媒体解码或系统返回验收；迁移未完成项持续记录在 `tasks/compose-migration-status.md`。
