# HeyBox Lite 代码边界

项目保持单 APK。当前迁移分支以 Kotlin + Jetpack Compose 承载主界面，Java 保留网络、缓存、媒体和旧 ROM 兼容层。页面类负责组合视图与转发生命周期，网络请求、持久化、手势和有状态业务流程必须由独立组件负责。

## 当前边界

- `MainActivity`：Activity 生命周期和顶层组件装配。详情请求由 `DetailLoadCoordinator` 管理，底部导航由 `BottomNavigationController` 管理，转场位图由 `TransitionSnapshotStore` 管理。
- `CheckinCenterPage`：签到子页面切换、生命周期和业务回调接线。配对轮询、服务账号、手机号登录、任务设置分别由对应的 `Checkin*Flow` 管理，输入视图由 `CheckinAccountForms` 管理，稳定内容状态由 `CheckinCenterPageRenderer` 创建，结果文案由 `CheckinResultText` 统一生成。
- `CheckinCenterClient` / `CheckinCenterTransport`：前者负责签到业务入口、异步回调和协议模型，后者负责固定 HTTPS 地址、响应大小、重定向和网络错误边界。
- `*Flow` / `*Coordinator`：拥有一个完整业务流程的状态和异步请求，不直接承担整页视觉设计。
- `*Ui` / `*Renderer` / `*Forms`：只创建或绑定视图，不发起业务网络请求。
- `SessionStore` / `LocalCache`：分别负责设置与会话、内容缓存，不承接页面导航逻辑。
- `ComposeAppHost`：生命周期、路由及共享控制器装配；`ComposeRouteScreen` 只选择页面，不执行请求。
- `ComposeSavedController`：协调收藏、收藏夹和历史的独立状态，不混入页面 UI。`ComposeSavedPostsController` 负责账号隔离、缓存和请求失效；`ComposeWatchLaterController` 负责本地读取、图片大小统计和删除；`ComposeReadingCenterController` 负责异步摘要。
- `ComposeFavoritesScreen` / `ComposeHistoryScreen` / `ComposeWatchLaterScreen` / `ComposeReadingCenterScreen`：各自的阅读库页面，共享 `ComposePageHeader` 和 `ComposeSearchInput`。
- `ComposeReadingStatsMapper`：纯阅读统计展示映射；日期、时区、比例和格式结果可独立测试，不增加计时业务。

## 修改规则

1. 新功能不得继续堆入 `MainActivity` 或 `CheckinCenterPage`；先确定业务所有者，再新增或扩展聚焦组件。
2. 新 Java / Kotlin 文件默认不超过 500 行。Java 遗留大文件的行数上限记录在 `config/java-size-limits.properties`，只能随拆分降低，不能提高。Compose 页面、状态映射、控制器分别保留单一职责，不用混合页面大文件规避限制。
3. 一个异步流程只能有一个状态所有者。页面暂停后，轮询、倒计时和动画调度必须停止。
4. UI 工厂不持有网络客户端；网络协调器不直接拼装复杂 View 树。
5. 不为受信任的内部调用叠加重复空值检查或宽泛 `catch (Throwable)`。边界校验放在外部输入、存储和网络响应入口。
6. 拆分必须保持现有接口行为，并为可独立验证的状态逻辑增加单元测试。

`verifyJavaClassSize` / `verifyKotlinSourceSize` 会在编译前检查这些限制。需要修改大文件时，应先抽取职责并同步降低其上限。文字编码检查覆盖 Java、Kotlin 和资源。

验证分为纯逻辑、Robolectric Compose 行为与真实 View 渲染、真机三层。前两层通过不能替代手表上的 GPU、表冠、媒体解码或系统返回验收；迁移未完成项持续记录在 `tasks/compose-migration-status.md`。
