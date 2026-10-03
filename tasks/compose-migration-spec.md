# Spec: heybox Lite Compose 重构

## Objective

在独立分支 `feature/compose-migration` 中，将 heybox Lite 的主界面和业务页面迁移到 Kotlin + Jetpack Compose，同时保留现有登录、接口、缓存、图片、视频、签到和诊断业务逻辑。

目标是让页面状态、主题、圆屏/方屏布局和设置项更容易维护，并保留手表上的滚动、表冠、图片缩放和返回手势体验。原生 View 分支继续作为稳定回退版本，不在本分支之外被改动。

## Current Baseline

- 基线提交：`8c2c906`
- 当前版本：`2.18` / `221`，本次不修改版本号
- 当前 UI：Compose 主壳和主要页面；媒体查看器、播放器、验证码 WebView 和少数兼容页仍使用 Java View
- 当前 `minSdk`：21；`compileSdk`：36；AGP：8.13.2
- 当前 APK：单 APK、原固定签名

## Assumptions Requiring Confirmation

1. 本分支允许引入 Kotlin、Compose 编译器和 Compose 依赖。
2. 网络、数据解析、缓存、登录和业务接口先继续使用现有 Java 实现。
3. 主壳、列表、设置、详情、评论和签到页面已使用 Compose；图片查看器、播放器和 WebView 作为设备兼容边界单独迁移。
4. 旧版 `feature/ui-v2-native-view` 永远保留，任何阶段都可以切回该分支。

## Compatibility Decision

这是当前必须确认的架构选择：

- **兼容旧手表优先**：使用普通 Compose，最低目标按 API 21 评估；仍会放弃当前 API 14-20 的安装兼容性。
- **采用 Wear Compose Material 3**：按 Wear Compose skill 要求最低 API 25，使用 `AppScaffold`、`ScreenScaffold`、`TransformingLazyColumn` 和旋转表冠 API；会进一步缩小旧设备覆盖范围。

本分支采用**普通 Compose、最低 API 21**。不采用 Wear Compose Material 3，避免把最低版本进一步提高到 API 25；手表专用布局、表冠和圆屏行为由项目自己的 Compose 组件实现。主要页面已完成切换，媒体与 WebView 不用 `AndroidView` 假装迁移完成，而是保留明确的原生兼容边界。

这意味着当前 API 14-20 的设备将不再安装此分支 APK。原生 View 分支继续保留，作为旧设备和生产版本的回退。

## Commands

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleRelease --no-daemon --console=plain
```

Release 必须继续通过固定证书校验，并复制到 `dist/heybox-Lite-2.18.apk` 和 `dist/heybox-Lite-latest.apk`。

## Target Structure

```text
app/src/main/java/com/ronan/heyboxlite/   # 现有 Java 业务、网络、缓存、播放器和兼容层
app/src/main/kotlin/com/ronan/heyboxlite/ # Compose UI、状态映射和页面组件
app/src/main/res/                         # 主题、图标、网络配置和必要资源
app/src/test/                              # 解析、状态和迁移 wiring 测试
app/src/androidTest/                      # 真正的 View/Compose 手势与屏幕测试
docs/                                     # 迁移记录和回退说明
tasks/                                    # 本规格、计划和任务清单
```

## Migration Boundaries

### Always

- 每次迁移只替换一个可验证页面或组件。
- 保留现有 Java Host 接口，Compose 只接收不可变 UI 状态和事件回调。
- 所有设置都必须可恢复、可重组，不能在 Composable 中直接发网络请求。
- 保留圆屏、方屏、4:3、1:1、窄屏和大字体测试。
- 迁移前保存基线截图，迁移后执行截图和行为对比。
- 每个阶段都运行单测和固定签名 Release 构建。

### Ask First

- 修改 `minSdk`、`targetSdk` 或签名配置。
- 引入 Wear Compose Material 3、Navigation 3 或其它重量级依赖。
- 修改现有手势协议、返回行为、接口请求和本地数据格式。
- 删除原生 View 页面或删除回退分支。

### Never

- 不在主分支或 `feature/ui-v2-native-view` 上直接重写。
- 不把 `AndroidView` 当作整页迁移完成的替代品。
- 不在 Compose 重构中顺便修改接口、签到协议或版本号。
- 不提交 Cookie、日志、keystore、密码和构建产物。

## Code Style

Compose 页面只负责渲染状态和发送事件；业务逻辑留在已有 Controller/Coordinator 中：

```kotlin
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
) {
    ScreenScaffold {
        TransformingLazyColumn {
            item { SettingsHeader(state.title) }
            item { SettingsToggleRow("表冠滚动", state.crownEnabled) {
                onEvent(SettingsEvent.SetCrownEnabled(it))
            } }
        }
    }
}
```

不得在 Composable 中直接读取 Cookie、调用 API、修改 SharedPreferences 或持有 Activity 引用。

## Testing Strategy

- 现有 Java 纯逻辑继续使用 JUnit4。
- Compose 页面增加 Compose UI behavior tests，优先通过 semantics 定位控件。
- 圆屏/方屏/4:3/1:1/大字体增加截图基线。
- 图片查看器、表冠、返回手势和视频播放必须使用真机或 instrumented test 验证。
- 每个迁移页面至少覆盖：初始态、加载态、错误态、空态、设置变更和返回恢复状态。
- 迁移不得降低现有 API 解析和缓存测试覆盖。

## Success Criteria

1. Compose 页面和原生页面可以在同一个 APK 中稳定共存。
2. 迁移页面在圆屏、方屏、4:3 和 1:1 屏幕没有截字、溢出或重叠。
3. 表冠滚动、系统返回、右滑返回、图片缩放和播放器手势不发生回归。
4. 旧原生分支仍可直接构建、签名和回退。
5. 每个阶段有独立提交、测试结果和可安装 Release APK。
6. 只有在核心页面全部通过真机验证后，才删除对应原生实现。

## Current Boundary

- Compose 页面：应用壳、底部导航、信息流、搜索、收藏/稍后看、我的、个人动态、阅读中心、设置、签到、排行榜、帖子详情和评论。
- Java 兼容层：业务 API、解析、缓存、加密、图片查看器、视频播放器、验证码 WebView、崩溃恢复和少数旧 ROM 页面。
- 主 Activity 仍是生命周期协调器，但页面渲染优先走 `ComposeAppHost`；原生页面只在媒体、WebView、崩溃或兼容回退路径使用。

## Open Questions

- 是否接受引入 Kotlin 和 Compose 后 APK 体积、构建时间及低端手表内存占用增加？当前分支按接受处理。
- 全量迁移按阶段提交；每阶段仍必须可构建、可回退，不能为了赶进度删除原生实现。
