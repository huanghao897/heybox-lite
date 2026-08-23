# HeyBox Lite 代码边界

项目保持 Java + Android 原生 View 和单 APK。页面类负责组合视图与转发生命周期，网络请求、持久化、手势和有状态业务流程必须由独立组件负责。

## 当前边界

- `MainActivity`：Activity 生命周期和顶层组件装配。详情请求由 `DetailLoadCoordinator` 管理，底部导航由 `BottomNavigationController` 管理，转场位图由 `TransitionSnapshotStore` 管理。
- `CheckinCenterPage`：签到子页面切换和页面组合。配对轮询、服务账号、手机号登录、任务设置分别由对应的 `Checkin*Flow` 管理，输入视图由 `CheckinAccountForms` 管理。
- `*Flow` / `*Coordinator`：拥有一个完整业务流程的状态和异步请求，不直接承担整页视觉设计。
- `*Ui` / `*Renderer` / `*Forms`：只创建或绑定视图，不发起业务网络请求。
- `SessionStore` / `LocalCache`：分别负责设置与会话、内容缓存，不承接页面导航逻辑。

## 修改规则

1. 新功能不得继续堆入 `MainActivity` 或 `CheckinCenterPage`；先确定业务所有者，再新增或扩展聚焦组件。
2. 新 Java 文件默认不超过 500 行。遗留大文件的行数上限记录在 `config/java-size-limits.properties`，只能随拆分降低，不能提高。
3. 一个异步流程只能有一个状态所有者。页面暂停后，轮询、倒计时和动画调度必须停止。
4. UI 工厂不持有网络客户端；网络协调器不直接拼装复杂 View 树。
5. 不为受信任的内部调用叠加重复空值检查或宽泛 `catch (Throwable)`。边界校验放在外部输入、存储和网络响应入口。
6. 拆分必须保持现有接口行为，并为可独立验证的状态逻辑增加单元测试。

`verifyJavaClassSize` 会在 Java 编译前检查这些限制。需要修改大文件时，应先抽取职责并同步降低其上限。
