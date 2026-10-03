# heybox Lite — Android 项目

小黑盒手表端第三方客户端。`feature/compose-migration` 使用 Jetpack Compose 承载主界面，Java 保留业务、网络、缓存、媒体和设备兼容层，面向 Android 7.0+。当前版本 `2.18`（versionCode `221`）。原 `feature/ui-v2-native-view` 分支是可回退的原生 View 基线。

## 构建

```powershell
.\gradlew.bat assembleDebug      # 调试包
.\gradlew.bat assembleRelease    # 正式包 → dist/heybox-Lite-<version>.apk
```

需要 JDK 17 + Android SDK（`local.properties` 指定路径）。

## 修改与提交规则

- 每次更新项目或修改代码后，都必须构建签名 Release APK，确认构建和签名校验通过。
- 只有用户明确要求“更新版本号”时，才允许修改 `versionCode` 或 `versionName`。
- 构建成功后，只提交本次相关改动，并推送到当前 GitHub 分支。
- 默认只推送代码提交，不创建 GitHub Release，也不上传 APK 附件；只有用户明确要求发布版本时才操作 Release。
- 如果构建失败，不得推送未验证的代码；应先修复或明确说明阻塞原因。
- 只有用户明确说“先别传”或“不用上传”时，才暂停 GitHub 提交与推送。
- 发布到应用更新服务器仍需用户明确授权，不能因为已推送 GitHub 而自动发布。

## 关键模块

Compose 页面位于 `app/src/main/kotlin/com/ronan/heyboxlite/`，控制器负责异步数据加载和生命周期取消；Java 页面与服务位于 `app/src/main/java/com/ronan/heyboxlite/`，其中媒体查看器、播放器和验证码仍属于兼容层。

| 类 | 职责 |
|----|------|
| `MainActivity` | 页面组织、列表、详情、评论、设置 |
| `ApiClient` | 小黑盒接口请求 |
| `SessionStore` | 本地登录态与开关 |
| `ImageLoader` | 图片缓存、缩略图、查看器 |
| `CheckinCenterClient` / `CheckinCenterCoordinator` / `CheckinCenterPage` | 云端自动签到连接、状态和设置页面 |
| `NativeSignBridge` / `NativeSignService` | 官方请求安全参数兼容层；不是本地签到流程 |
| `UpdateChecker` / `AnnouncementChecker` | 更新与公告 |
| `PresenceReporter` | 后台在线心跳 |

## 调试产物说明

根目录有 170+ 个 ADB 截图/日志/XML（`adb-*`、`window-*.xml` 等），均已由 `.gitignore` 覆盖，不会进入 git。

## 注意

- 云端签到与普通浏览流程隔离；不要恢复已经删除的本地签到实现
- 不要提交 `*.har`、Cookie、logcat、keystore、`release-signing.properties`
