# heybox Lite — Android 项目

小黑盒手表端第三方客户端，Java View 体系，面向 Android 7.0+。当前版本 `2.0.3`。

## 构建

```powershell
.\gradlew.bat assembleDebug      # 调试包
.\gradlew.bat assembleRelease    # 正式包 → dist/heybox-Lite-<version>.apk
```

需要 JDK 17 + Android SDK（`local.properties` 指定路径）。

## 关键模块（`app/src/main/java/com/openzen/heyboxcommunity/`）

| 类 | 职责 |
|----|------|
| `MainActivity` | 页面组织、列表、详情、评论、设置 |
| `ApiClient` | 小黑盒接口请求 |
| `SessionStore` | 本地登录态与开关 |
| `ImageLoader` | 图片缓存、缩略图、查看器 |
| `SignInManager` | 签到（实验性，与其它逻辑隔离） |
| `UpdateChecker` / `AnnouncementChecker` | 更新与公告 |
| `PresenceReporter` | 后台在线心跳 |

## 调试产物说明

根目录有 170+ 个 ADB 截图/日志/XML（`adb-*`、`window-*.xml` 等），均已由 `.gitignore` 覆盖，不会进入 git。

## 注意

- 签到接口依赖非公开风控，不保证稳定
- 不要提交 `*.har`、Cookie、logcat、keystore、`release-signing.properties`
