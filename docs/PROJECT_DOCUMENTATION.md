# heybox Lite 项目详细文档

最后更新：2026-07-09

本文档面向后续维护者、测试者和发布者，尽量把 heybox Lite 的项目目标、代码结构、核心流程、后台接口、构建发布、测试排障和安全注意事项讲清楚。它不是一份面向普通用户的宣传说明，而是一份“以后接手这个项目时可以少踩坑”的工程文档。

## 1. 项目定位

heybox Lite 是一个面向 Android 手表和小屏 Android 设备的小黑盒社区第三方客户端。它的目标不是完整复刻手机端小黑盒，而是在手表屏幕上优先保留这些高频能力：

- 刷社区信息流；
- 搜索帖子；
- 查看帖子正文；
- 查看图片和长图；
- 查看评论和二级评论；
- 进行点赞、收藏、关注、评论等常用互动；
- 查看收藏、历史和个人动态；
- 接收公告和检查更新；
- 在出问题时导出诊断日志。

项目从 1.x 线逐步演进到 2.0 线。1.x 更偏“功能能跑”，2.0 开始重做 UI、交互、圆屏适配和后台统计。当前代码仍以 Java View 体系为主，没有引入 Compose，也没有使用大型 UI 框架。这是一个现实选择：手表 ROM、低性能设备和旧 Android 版本对包体积、兼容性和渲染性能都更敏感。

## 2. 当前状态

### 2.1 Android App

Android 项目路径：

```text
C:\opencode\HeyBoxCommunity
```

当前 Gradle 配置中的版本：

```text
versionName: 2.0.3
versionCode: 206
applicationId: com.ronan.heyboxlite.preview
namespace: com.openzen.heyboxcommunity
```

注意：README 里可能仍保留旧版本文字，发布前应以 `app/build.gradle` 为准。

### 2.2 后台服务

后台项目路径：

```text
C:\opencode\heybox-admin
```

服务器部署路径：

```text
/opt/heybox-admin
```

当前线上服务器：

```text
http://8.138.134.236
```

后台负责公告、更新接口、APK 下载和在线人数统计。后台数据保存在远端 `data/` 目录下，部署时不要覆盖。

## 3. 重要原则

### 3.1 不要把手机端完整搬进手表

手表端屏幕小、操作慢、输入不方便。新增功能时优先考虑：

- 页面能不能少一层；
- 是否会挡住底部胶囊；
- 圆屏边缘是否裁切；
- 手表性能能不能扛住；
- 网络失败时旧内容能不能保留。

### 3.2 尽量隔离高风险功能

签到、小黑盒写入接口、移动端签名、官方 App 凭据读取都属于高风险区域。它们可能受风控和接口变动影响。实现时必须与主浏览流程隔离，不能因为签到失败污染登录态，也不能因为互动接口失败导致主页无法加载。

### 3.3 每次改 UI 后都要给测试 APK

用户已经明确要求：每次改动完成后要构建出 APK。通常使用旧签名构建 release 包，输出到：

```text
dist/heybox-Lite-版本号.apk
dist/heybox-Lite-latest.apk
```

如果只是文档改动，可以不用重新构建 APK，但应在最终回复中说明“文档改动不影响安装包”。

### 3.4 不要覆盖用户数据

部署后台时只同步代码和静态资源，不覆盖服务器上的：

```text
/opt/heybox-admin/data
```

该目录包含 SQLite 数据库、上传的 APK、公告和下载统计。

## 4. Android 项目目录

核心目录：

```text
HeyBoxCommunity
├─ app
│  ├─ build.gradle
│  ├─ proguard-rules.pro
│  └─ src/main
│     ├─ AndroidManifest.xml
│     ├─ java/com/openzen/heyboxcommunity
│     └─ res
├─ dist
├─ gradle
├─ README.md
└─ docs
```

### 4.1 `app/build.gradle`

主要职责：

- 定义应用包名、版本号、SDK；
- 定义 BuildConfig 中的更新和公告地址；
- 配置 release 签名；
- 配置混淆和资源压缩；
- 注册文本乱码检查任务；
- 注册 APK 复制到 `dist/` 的任务；
- 校验 release APK 是否使用预期签名。

关键字段：

```gradle
applicationId "com.ronan.heyboxlite.preview"
minSdk 14
targetSdk 35
versionCode 206
versionName "2.0.3"
```

更新服务地址：

```gradle
UPDATE_API_URL = "http://8.138.134.236/api/heybox-lite/update"
UPDATE_FALLBACK_URL = "http://8.138.134.236/"
ANNOUNCEMENT_API_URL = "http://8.138.134.236/api/heybox-lite/announcement"
```

签名校验：

- preview 包使用 `previewReleaseCertSha256`；
- 非 preview 包使用 `RELEASE_CERT_SHA256`；
- 如果签名不一致，`verifyReleaseApkSignature` 会阻止 release 构建通过。

当前 2.0 preview 旧签名证书 SHA-256：

```text
fbd5642c3c1b5882545f6f1227cf2dc38a54bcd18609203935eedbef408d1382
```

本机固定使用旧签名构建时，通常设置：

```powershell
$env:HEYBOX_RELEASE_STORE_FILE='C:\Users\15989\.android\debug.keystore'
$env:HEYBOX_RELEASE_STORE_PASSWORD='android'
$env:HEYBOX_RELEASE_KEY_ALIAS='androiddebugkey'
$env:HEYBOX_RELEASE_KEY_PASSWORD='android'
.\gradlew.bat --no-daemon assembleRelease
```

### 4.2 `AndroidManifest.xml`

主要权限：

```xml
INTERNET
ACCESS_NETWORK_STATE
REQUEST_INSTALL_PACKAGES
WRITE_EXTERNAL_STORAGE maxSdkVersion=28
```

说明：

- `INTERNET` 用于小黑盒接口、图片、更新和公告；
- `ACCESS_NETWORK_STATE` 用于更友好的网络状态判断；
- `REQUEST_INSTALL_PACKAGES` 用于应用内下载 APK 后调用系统安装器；
- `WRITE_EXTERNAL_STORAGE` 只给 Android 9 及以下用于导出日志等兼容场景。

查询声明：

```xml
<package android:name="com.max.xiaoheihe" />
<provider android:authorities="com.max.xiaoheihe.statusprovider" />
```

用途：

- 检查官方 App；
- 尝试读取官方 App 暴露的状态/认证信息；
- 与签到实验功能相关。

主要组件：

- `SplashActivity`：启动页；
- `MainActivity`：主界面和绝大多数页面；
- `ImageViewerActivity`：大图查看器；
- `NativeSignService`：独立进程中的 native 签名尝试；
- `DiagnosticsProvider`：诊断日志分享；
- `UpdateApkProvider`：应用内更新 APK 分享给系统安装器。

### 4.3 网络安全配置

文件：

```text
app/src/main/res/xml/network_security_config.xml
```

当前配置：

- 默认禁止明文流量；
- 临时允许自建更新服务器 IP 明文 HTTP：
  - `8.138.134.236`
  - `103.236.54.97`

这是临时方案。后续绑定域名并配置 HTTPS 后，应移除明文 IP 白名单。

## 5. Android 端核心模块

### 5.1 `MainActivity`

`MainActivity` 是当前项目中最大的类，负责绝大多数 UI 和页面流程。它包含：

- 底部胶囊导航；
- 主页信息流；
- 搜索页；
- 帖子详情页；
- 图片块；
- 评论区；
- 评论发布弹窗；
- 个人页；
- 收藏和历史；
- 用户动态空间；
- 设置页；
- 公告页；
- 关于页；
- 更新弹窗；
- 滑动返回和页面切换动画。

这个类很大，是历史演进形成的。后续如果继续整理，建议按页面拆分：

- `FeedPage`；
- `SearchPage`；
- `DetailPage`；
- `CommentSection`；
- `ProfilePage`；
- `SettingsPage`；
- `NavigationController`。

拆分时要谨慎，优先保证功能不退化。

#### 5.1.1 主页面

主要入口：

- `showFeed()`：主页；
- `showSearch()`：搜索页；
- `showProfile()`：我的页；
- `showDetail(FeedItem item)`：帖子详情；
- `showSettingsHome()`：设置中心；
- `showAbout()`：关于页。

页面状态通过 `screen` 字符串维护，例如：

```text
feed
search
profile
detail
settings_home
saved
user_space
announcement_board
```

底部胶囊导航会根据当前页面显示/隐藏，并且尽量不占用主内容布局空间。

#### 5.1.2 帖子详情

帖子详情负责：

- 加载正文；
- 渲染富文本；
- 渲染图片；
- 渲染作者；
- 渲染点赞/收藏/评论按钮；
- 渲染评论区；
- 支持左滑到评论区；
- 支持右滑返回上一层；
- 按设置决定是否记住阅读位置。

与详情相关的重点方法：

- `showDetail(FeedItem item)`；
- `addAuthorHeader(...)`；
- `addRichContent(...)`；
- `addDetailActions(...)`；
- `addDetailCommentSection(...)`；
- `addComments(...)`；
- `addComment(...)`。

#### 5.1.3 评论区

评论区当前设计目标：

- 一级评论保留头像；
- 一级评论显示昵称、等级名牌、时间/IP、正文、点赞；
- 二级评论无头像；
- 二级评论不显示等级名牌；
- 二级评论尽量紧凑，减少手表滑动距离；
- 展开/收起按钮尽量接近官方样式；
- 官方表情应在一级和二级评论中正常显示；
- 双击评论回复可在设置中开关。

评论时间显示规则：

- 小于 1 分钟：`刚刚`；
- 小于 1 小时：`X分钟前`；
- 小于 1 天：`X小时前`；
- 超过 1 天：`MM-dd`。

等级名牌颜色按等级区间近似官方：

- 低等级：蓝色；
- 中等级：粉色；
- 较高等级：青色；
- 高等级：紫色；
- 很高等级：金色。

注意：官方具体等级颜色可能随版本变化，目前是近似实现。

### 5.2 `ApiClient`

`ApiClient` 是小黑盒接口请求层，负责：

- 拼接 baseUrl + path；
- 设置请求头；
- 加入签名参数；
- 处理 GET/POST；
- 超时控制；
- JSON 解析；
- 错误归一化；
- 写入诊断日志；
- 在部分接口失败时尝试 fallback。

相关依赖：

- `EndpointProvider`：接口 path；
- `HeyboxSigner`：签名；
- `HeaderProvider`：请求头；
- `SessionStore`：Cookie、用户 ID、设备参数；
- `NativeSignBridge` / `OfficialNativeSigner`：native 签名尝试。

网络相关原则：

- 不要因为请求失败清空旧列表；
- UI 回调前检查 Activity 状态；
- 写入操作失败时应恢复按钮状态；
- 小黑盒接口返回风控时应直接提示，不要重复轰炸接口。

### 5.3 `EndpointProvider`

`EndpointProvider` 集中保存小黑盒接口路径。路径经过简单异或混淆。

典型接口分类：

- 信息流；
- 帖子详情；
- 搜索；
- 评论；
- 收藏；
- 关注；
- 点赞；
- 用户资料；
- 历史记录；
- 表情；
- 签到。

注意：

- 这些不是公开稳定 API；
- path 和参数可能随小黑盒版本变化；
- 任何接口修复都应先看诊断日志，不要盲改。

### 5.4 `SessionStore`

`SessionStore` 负责本地状态和设置项。主要内容：

- 登录 Cookie；
- 用户 ID、昵称、头像；
- 图片加载开关；
- 深色模式；
- UI 缩放；
- 字号；
- 页面边距；
- 圆屏边距；
- 正文行距、字距、段距；
- 自动检查更新；
- 开屏动画；
- 右滑返回；
- 记住帖子阅读位置；
- 双击评论回复；
- 公告已读；
- 搜索历史；
- 屏蔽关键词；
- 签到实验凭据。

重要默认值：

```text
DEFAULT_SPLASH_TEXT = 方寸之间，看见热爱
```

安全注意：

- Cookie 会加密存储；
- Android 低版本无法使用现代 KeyStore 时会降级；
- 任何导出日志都不要输出完整 Cookie、pkey 或 token；
- 签到相关字段必须与普通登录态隔离。

### 5.5 `FeedItem` 和 `FeedAdapter`

`FeedItem` 是帖子列表的中间模型，负责把接口中的 JSON 转成列表需要的数据：

- 帖子 ID；
- 标题；
- 摘要；
- 作者；
- 点赞数；
- 评论数；
- 图片；
- 原始 JSON。

`FeedAdapter` 负责主页/搜索/收藏/历史等列表 UI。

图片策略：

- 主页预览图使用缩略图；
- 失败时显示灰色占位；
- 不用上一张图覆盖当前项；
- 返回列表时尽量保留旧图片和滚动位置。

### 5.6 `RichContent`

`RichContent` 负责帖子正文和评论文本解析。它处理的输入可能非常混乱，包括：

- 纯文本；
- HTML；
- JSON 数组；
- 富文本对象；
- 图片标签；
- 官方表情；
- 转义后的 HTML/JSON；
- 接口返回的嵌套结构。

输出模型：

```java
Block {
    boolean image;
    String value;
}
```

设计目标：

- 正文图片按正文顺序显示；
- 图片之间的文字不能丢；
- 官方表情要转成可渲染 token；
- 评论使用单行紧凑文本；
- 正文保留段落感；
- 避免把 JSON 噪声当正文。

评论相关重点：

- `commentText(...)` 会从多个候选字段中选择最完整文本；
- 评论里的内联表情会压回同一行；
- 如果 `<img>` 是官方表情，应转成 token，而不是当正文图片块丢掉。

### 5.7 `EmojiStore` / `EmojiRenderer` / `OfficialEmojiFallback`

官方表情渲染分三层：

1. `EmojiStore`：保存表情 token 到图片 URL 的映射；
2. `OfficialEmojiFallback`：内置常见官方表情 URL；
3. `EmojiRenderer`：在 TextView 中把 token 替换成 `ImageSpan`。

支持的 token 示例：

```text
[cube_微笑]
cube_微笑
[heygirl_哈哈]
```

常见问题：

- 接口返回 `<img>` 没有 alt/title；
- 表情 URL 中只有 `cube_62.png` 这样的文件名；
- 评论区字段只返回一个表情，完整文字在另一个字段；
- 表情被当成正文图片块，导致文字被吞。

排查时优先看：

- `RichContent.commentText(...)`；
- `RichContent.normalizeInlineImageEmojis(...)`；
- `EmojiRenderer.set(...)`；
- `OfficialEmojiFallback` 是否缺少对应表情。

### 5.8 `ImageLoader`

`ImageLoader` 负责图片加载、缓存和解码。主要职责：

- 根据目标尺寸加载图片；
- 缩略图和原图策略分离；
- 控制最大 Bitmap；
- 尽量避免 OOM；
- 列表图片失败时不清空旧内容；
- 正文图片支持渐进显示；
- Activity 销毁时取消请求。

常用入口：

- `into(...)`；
- `intoPlain(...)`；
- `intoStable(...)`；
- `intoMeasuredRevealStable(...)`；
- `intoOriginal(...)`；
- `loadOriginal(...)`。

注意：

- 主页预览图不做渐进显示；
- 头像不做渐进显示；
- 帖子正文图片可以渐进显示；
- 大图查看器优先使用现有缓存预览，再加载更高清图片。

### 5.9 `ImageViewerActivity` 和 `ZoomImageView`

`ImageViewerActivity` 是大图查看页面，负责：

- 半透明背景；
- 底部返回/查看原图按钮；
- 点击空白退出；
- 使用缓存预览；
- 加载原图；
- 调用 `ZoomImageView` 支持手势缩放。

`ZoomImageView` 支持：

- 双指缩放；
- 拖动查看；
- 双击放大；
- 多级放大；
- 单击恢复；
- 长截图第一次双击按屏幕宽度铺满。

当前双击逻辑：

- 第一次双击：至少放大到基础倍率，长截图会尽量铺满屏幕宽度；
- 第二次双击：继续放大；
- 第三次双击或特定单击：回到适配大小。

### 5.10 `UpdateChecker`

更新检查负责读取自建服务器接口。

App 当前使用：

```text
http://8.138.134.236/api/heybox-lite/update
```

更新结果会用于：

- 自动检查更新；
- 设置页手动检查更新；
- 更新弹窗；
- 应用内下载 APK；
- 调用系统安装器。

失败原则：

- 更新检查失败不能影响进入主界面；
- TLS/网络失败要优雅失败；
- 下载失败保留浏览器 fallback；
- asset 命名或服务器返回不正确时应提示明确。

### 5.11 `AnnouncementChecker`

公告检查负责读取自建公告接口。

相关行为：

- 新用户首次打开显示欢迎公告；
- 已点“知道了”的公告不重复弹；
- 公告列表可在关于页查看；
- 公告内容来自后台；
- 本地记录已读公告 ID。

### 5.12 `PresenceReporter`

`PresenceReporter` 负责在线人数统计心跳。

上报频率：

```text
同一进程内最短 60 秒一次
```

上报内容：

- 设备 ID；
- 登录用户 ID；
- 用户昵称；
- 头像 URL；
- App 版本；
- versionCode；
- 设备型号；
- Android 版本。

明确不上传：

- Cookie；
- pkey；
- token；
- 签到凭据；
- 评论内容；
- 浏览内容。

服务器按 IP 和用户信息统计在线/今日活跃。

### 5.13 `LocalCache`

`LocalCache` 负责本地缓存和诊断日志。

典型用途：

- 缓存信息流；
- 缓存帖子详情；
- 缓存离线内容；
- 写入诊断日志；
- 导出日志。

注意：

- 用户要求日志导出为 `.txt`；
- 默认导出目录是 `Download/heyboxlite`；
- 新启动后日志应更偏本次会话，便于诊断；
- 清除缓存按钮不应删除正常图片缓存，除非用户明确要求。

### 5.14 `SignInManager`

签到仍是实验功能。真实小黑盒签到涉及移动端凭据、签名、风控和官方 App 行为。当前项目尝试过多种方式：

- 公开接口；
- `task/sign_v3/sign`；
- 移动端参数；
- pkey/imei/token；
- 官方 Provider；
- native signer；
- 请求重放。

维护原则：

- 签到失败不能影响主页；
- 签到失败不能清除登录态；
- 签到凭据必须与普通 Cookie 隔离；
- 不要在没有日志证据时盲改点赞/收藏/关注请求；
- 如果签到和其它互动冲突，优先保住其它互动。

### 5.15 native 签名相关模块

相关类：

- `NativeLibraryLoader`；
- `NativeSecuritySigner`；
- `NativeSignBridge`；
- `NativeSignService`；
- `OfficialAppVerifier`；
- `OfficialContext`；
- `OfficialNativeSigner`；
- `AppIntegrityCheck`。

用途：

- 尝试复用官方 App/native 相关能力；
- 校验官方 App；
- 隔离 native 签名进程；
- 避免 native 失败拖垮主进程。

这是高风险区域，修改前必须先看诊断日志。

## 6. 后台项目

后台路径：

```text
C:\opencode\heybox-admin
```

线上路径：

```text
/opt/heybox-admin
```

后台技术栈：

- Python 3；
- 标准库 HTTPServer；
- SQLite；
- 无 Flask/Django；
- 前端静态 HTML/CSS/JS；
- systemd 托管；
- nginx 反向代理。

### 6.1 后台目录结构

```text
heybox-admin
├─ server.py
├─ README.md
├─ static
│  ├─ admin.html
│  ├─ index.html
│  ├─ heybox-logo.png
│  ├─ screen-about.jpg
│  └─ screen-community.jpg
├─ deploy
│  ├─ heybox-admin.service
│  └─ nginx.conf
└─ data
   ├─ admin.db
   ├─ uploads
   └─ admin_password.txt
```

部署时不要覆盖：

```text
data/
```

### 6.2 环境变量

后台支持的主要环境变量：

```bash
HEYBOX_DATA_DIR=/opt/heybox-admin/data
HEYBOX_ADMIN_PASSWORD=后台初始密码
HEYBOX_ADMIN_PASSWORD_FILE=/opt/heybox-admin/data/admin_password.txt
HEYBOX_SESSION_SECRET=随机长字符串
HEYBOX_PUBLIC_BASE_URL=http://8.138.134.236
HEYBOX_BIND=127.0.0.1
HEYBOX_PORT=8088
HEYBOX_MAX_UPLOAD_MB=300
```

说明：

- `HEYBOX_ADMIN_PASSWORD_FILE` 优先级高于 `HEYBOX_ADMIN_PASSWORD`；
- 后台修改密码后会写入 password file；
- `HEYBOX_PUBLIC_BASE_URL` 会影响更新接口返回的下载地址；
- `HEYBOX_BIND` 建议保持 `127.0.0.1`，由 nginx 对外暴露；
- 如果没有 HTTPS，Cookie secure 策略会受限，后续备案和域名配置后应升级到 HTTPS。

### 6.3 SQLite 表

后台数据库：

```text
data/admin.db
```

主要表：

- `announcements`：公告；
- `releases`：上传的 APK 版本；
- `access_logs`：访问日志；
- `access_visitors`：总访问 IP 去重；
- `access_daily`：每日访问 IP 去重；
- `download_logs`：下载记录；
- `error_logs`：错误记录；
- `presence`：按 IP 的在线心跳；
- `presence_daily`：每日 IP 活跃；
- `presence_users`：按用户/设备聚合的在线数据；
- `presence_users_daily`：每日用户/设备活跃。

### 6.4 公共接口

#### `GET /health`

健康检查。

返回示例：

```json
{
  "ok": true,
  "time": 1783579558674
}
```

#### `GET /api/latest`

返回当前发布版本。兼容旧接口。

#### `GET /api/heybox-lite/update`

App 当前使用的更新接口。

返回字段通常包括：

```json
{
  "id": 10,
  "versionName": "2.0.2",
  "versionCode": 205,
  "changelog": "更新日志",
  "apkUrl": "http://8.138.134.236/download/heybox-Lite-2.0.2.apk",
  "latestApkUrl": "http://8.138.134.236/download/latest.apk",
  "fileSize": "1.66 MB",
  "sha256": "...",
  "forceUpdate": false
}
```

#### `GET /api/releases`

公开版本列表。

#### `GET /api/announcements`

公开公告列表。

#### `GET /api/heybox-lite/announcement`

App 当前公告接口之一。

#### `GET /download/latest.apk`

下载当前发布 APK。

#### `GET /download/<filename>`

下载指定版本 APK。

### 6.5 在线统计接口

#### `POST /api/presence`

App 前台启动/恢复时上报心跳。

请求体示例：

```json
{
  "deviceId": "device-id",
  "userId": "123456",
  "username": "用户昵称",
  "avatar": "https://...",
  "version": "2.0.3",
  "versionCode": 206,
  "model": "OPPO Watch",
  "os": "9"
}
```

服务器记录：

- IP；
- UA；
- 用户 ID；
- deviceId；
- 昵称；
- 头像；
- 版本；
- 机型；
- 系统版本；
- 首次看到时间；
- 最后心跳时间。

在线窗口：

```text
300 秒
```

### 6.6 管理接口

管理接口需要登录后台。

#### `POST /admin/login`

登录后台。

#### `POST /admin/logout`

退出后台。

#### `GET /api/admin/summary`

后台首页总览数据，包括：

- releases；
- announcements；
- announcementStats；
- stats；
- presence；
- 下载统计；
- 访问统计；
- 最新版本信息。

#### `POST /api/admin/releases`

上传 APK。

表单字段：

- `versionName`；
- `versionCode`；
- `changelog`；
- `forceUpdate`；
- `publish`；
- `apk`。

#### `POST /api/admin/releases/<id>/publish`

发布某个 APK 为当前最新版。

#### `POST /api/admin/releases/<id>/delete`

删除某个 APK 记录和文件。

#### `POST /api/admin/announcements`

创建公告。

#### `POST /api/admin/announcements/<id>/delete`

删除公告。

#### `POST /api/admin/password`

修改后台密码。

### 6.7 后台部署流程

推荐流程：

1. 本地检查 `server.py`：

```powershell
C:\Users\15989\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -m py_compile server.py
```

2. 上传这些内容：

```text
server.py
README.md
.gitignore
static/
deploy/
```

3. 不上传：

```text
data/
__pycache__/
*.bak
_out.txt
_err.txt
```

4. 服务器上备份旧代码：

```bash
cp -a /opt/heybox-admin/server.py /opt/heybox-admin/static /opt/heybox-admin/deploy /opt/heybox-admin-backup-时间/
```

5. 覆盖代码后重启：

```bash
systemctl restart heybox-admin
systemctl is-active heybox-admin
```

6. 检查：

```bash
curl -fsS http://127.0.0.1:8088/health
curl -fsS http://127.0.0.1:8088/api/heybox-lite/update
curl -fsS http://127.0.0.1:8088/admin
```

公网检查：

```powershell
Invoke-WebRequest -Uri "http://8.138.134.236/health" -UseBasicParsing
Invoke-WebRequest -Uri "http://8.138.134.236/api/heybox-lite/update" -UseBasicParsing
Invoke-WebRequest -Uri "http://8.138.134.236/admin" -UseBasicParsing
```

## 7. 构建与发布

### 7.1 环境要求

本项目需要：

- Windows；
- JDK 17；
- Android SDK；
- Gradle Wrapper；
- Android build-tools 中的 `apksigner`；
- 旧签名 keystore。

当前 Android SDK 来自：

```text
C:\opencode\android-sdk
```

### 7.2 Debug 构建

```powershell
cd C:\opencode\HeyBoxCommunity
.\gradlew.bat --no-daemon assembleDebug
```

输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

### 7.3 Release 构建

使用旧签名：

```powershell
cd C:\opencode\HeyBoxCommunity
$env:HEYBOX_RELEASE_STORE_FILE='C:\Users\15989\.android\debug.keystore'
$env:HEYBOX_RELEASE_STORE_PASSWORD='android'
$env:HEYBOX_RELEASE_KEY_ALIAS='androiddebugkey'
$env:HEYBOX_RELEASE_KEY_PASSWORD='android'
.\gradlew.bat --no-daemon assembleRelease
```

输出：

```text
dist/heybox-Lite-2.0.3.apk
dist/heybox-Lite-latest.apk
```

### 7.4 签名检查

```powershell
C:\opencode\android-sdk\build-tools\36.0.0\apksigner.bat verify --print-certs dist\heybox-Lite-2.0.3.apk
```

期望：

```text
Signer #1 certificate SHA-256 digest: fbd5642c3c1b5882545f6f1227cf2dc38a54bcd18609203935eedbef408d1382
```

如果签名不是这个，用户设备可能提示“签名不一致”，无法覆盖安装旧测试包。

### 7.5 版本号规则

用户曾定过规则：

- bug 数量较多时更新版本号；
- 新功能较多时更新版本号；
- 更新版本号必须上传 GitHub，除非用户明确说不要传；
- 服务器上传需要用户明确说“传服务器”；
- 当前阶段 GitHub/服务器上传不要自行推断。

实际执行时应遵循用户最新指令。

### 7.6 发布到服务器

如果发布 APK 到后台，通常通过后台页面上传，或用脚本调用后台接口。

发布前检查：

- APK 是否旧签名；
- versionName/versionCode 是否正确；
- 更新日志是否正确；
- `latest.apk` 是否指向新版本；
- App 内更新弹窗是否能显示更新内容；
- 后台接口是否返回 200。

### 7.7 发布到 GitHub

GitHub 发布前检查：

- 工作区是否包含用户未提交改动；
- README 是否需要更新；
- changelog 是否准备好；
- dist APK 是否旧签名；
- tag 和 release version 是否匹配；
- 不要提交本地日志、HAR、APK 反编译文件、服务器密码。

## 8. 测试清单

### 8.1 基础启动

- 首次安装能启动；
- 开屏动画可开关；
- 深色/浅色模式正常；
- 圆屏模式不会裁切；
- 横屏不崩溃；
- 手表底部胶囊不挡设置入口。

### 8.2 主页

- 主页能加载信息流；
- 下拉/点击刷新可用；
- 快速上下滑动不卡死；
- 主页图片未加载时显示灰色占位；
- 图片加载失败不复用上一张图；
- 再次点击主页按钮可回到顶部并刷新；
- 从我的页滑回主页保留原滚动位置。

### 8.3 搜索

- 搜索入口位于主页顶部；
- 搜索历史显示正常；
- 搜索结果能进入帖子；
- 从帖子返回搜索结果时回到原位置；
- 搜索结果中的官方表情正常。

### 8.4 帖子详情

- 正文完整显示；
- 图片顺序正确；
- 图片之间文字不丢；
- 官方表情正常；
- 长图点开可查看；
- 双击长图能放大到屏幕宽度；
- 点击空白退出大图；
- 查看原图按钮可用；
- 返回后主页图片不消失；
- 开启阅读位置记忆后重新进入恢复位置；
- 关闭阅读位置记忆后进入从顶部开始。

### 8.5 评论区

- 一级评论头像显示；
- 一级评论等级名牌显示；
- 一级评论时间/IP 显示；
- 二级评论无头像；
- 二级评论无等级名牌；
- 二级评论紧凑；
- 二级评论展开/收起按钮不占太多空间；
- 评论中的官方表情不吞文字；
- 双击评论回复开关可用；
- 发布评论成功/失败提示正确；
- 风控提示不导致 App 崩溃。

### 8.6 互动功能

- 点赞帖子；
- 取消点赞；
- 收藏帖子；
- 取消收藏；
- 关注用户；
- 取消关注；
- 评论点赞；
- 发布评论；
- 登录失效时提示重新登录；
- 写入失败后按钮状态能恢复。

### 8.7 我的页

- 未登录显示游客页；
- 登录后显示用户信息；
- 收藏可打开；
- 浏览历史可打开；
- 用户动态空间可打开；
- 收藏里进入帖子后返回收藏位置；
- 动态里进入帖子后返回动态位置。

### 8.8 设置

- 显示与主题；
- 圆屏适配；
- 字号；
- 正文字号；
- 行距；
- 字距；
- 页面边距；
- 滑动返回开关；
- 记住阅读位置开关；
- 双击评论回复开关；
- 自动检查更新开关；
- 开屏动画开关；
- 恢复默认设置；
- 导出日志；
- 清除缓存；
- 交流群二维码。

### 8.9 更新与公告

- 自动检查更新可关闭；
- 手动检查更新可用；
- 更新弹窗显示版本号和更新内容；
- 应用内下载显示进度；
- 下载完成可打开系统安装器；
- 公告首次弹出后不重复弹；
- 公告列表可滚动；
- 公告列表可刷新；
- 后台新公告能被 App 看到。

### 8.10 后台

- `/health` 返回 200；
- `/api/heybox-lite/update` 返回当前版本；
- `/api/announcements` 返回公告；
- `/download/latest.apk` 可下载；
- 后台登录可用；
- 修改密码可用；
- 上传 APK 可用；
- 发布版本可用；
- 删除公告可用；
- 在线人数统计变化正常；
- 连续错误密码触发限速。

## 9. 常见问题排查

### 9.1 安装提示签名不一致

原因：

- APK 不是用旧签名构建；
- 用户手机上已有旧签名安装包；
- 当前 release 使用了 Codex 沙盒用户的 debug.keystore。

解决：

1. 使用 `C:\Users\15989\.android\debug.keystore`；
2. 设置签名环境变量；
3. 重新 `assembleRelease`；
4. 用 `apksigner verify --print-certs` 检查 SHA-256；
5. 确认是 `fbd564...`。

### 9.2 更新弹窗没有更新内容

检查：

- 后台 release 的 changelog 是否为空；
- `/api/heybox-lite/update` 是否返回 changelog；
- App 是否读取了正确服务器；
- App 是否缓存旧结果；
- 后台上传后是否发布为最新版。

### 9.3 公告每次都弹

检查：

- 公告 ID 是否每次变化；
- `SessionStore` 中已读公告是否保存；
- 欢迎公告是否被当成新公告；
- App 数据是否被清空；
- 服务器公告 `id` 是否稳定。

### 9.4 评论只显示表情或吞字

常见原因：

- 接口 `text/content/html/description` 字段内容不一致；
- 第一个字段只有表情；
- 官方表情 `<img>` 没被识别；
- 表情被当图片块丢掉。

检查：

- `RichContent.commentText(...)`；
- `normalizeInlineImageEmojis(...)`；
- `EmojiStore.url(...)`；
- 诊断日志中的原始评论字段。

### 9.5 帖子正文图片顺序错误

常见原因：

- 正文 JSON 中图片和文字混排；
- fallbackImages 被重复插入；
- HTML 图片和 `imgs` 字段重复；
- 去重 key 不正确。

检查：

- `RichContent.parse(...)`；
- `addStructured(...)`；
- `addArticleHtml(...)`；
- `addFallbackImagesIfNeeded(...)`。

### 9.6 点赞/收藏/关注突然全失败

优先怀疑：

- 写入 token 失效；
- Cookie 失效；
- 风控；
- 签名参数变化；
- 签到相关改动污染了普通写入请求。

不要第一时间大改接口。先看：

- 诊断日志；
- `ApiClient` 写入请求路径；
- `WriteTokenProvider`；
- `SessionStore` Cookie；
- 近期是否改过签到逻辑。

### 9.7 签到失败

这是已知高风险点。原因可能包括：

- 缺少移动端 pkey；
- deviceId/imei 不匹配；
- x_xhh_tokenid 缺失；
- 官方接口风控；
- native 安全链不完整；
- 请求重放过期。

处理原则：

- 不要让签到影响主流程；
- 不要因为签到失败清登录；
- 不要为了签到破坏点赞/收藏/评论；
- 需要抓包或日志证据再改。

### 9.8 后台上传后 App 收不到更新

检查：

- 后台是否发布该版本；
- versionCode 是否大于 App 当前版本；
- `/api/heybox-lite/update` 是否返回新版本；
- App 的 `UPDATE_API_URL` 是否指向新服务器；
- 手机网络是否能访问服务器；
- 是否仍访问老服务器。

### 9.9 后台页面打不开

检查服务器：

```bash
systemctl status heybox-admin --no-pager -l
curl -fsS http://127.0.0.1:8088/health
nginx -t
systemctl status nginx --no-pager -l
```

如果服务启动失败：

- 看 `server.py` 语法；
- 看环境变量；
- 看端口占用；
- 看 `data/` 权限；
- 看 SQLite 文件是否损坏。

## 10. 安全说明

### 10.1 不要提交的内容

不要提交：

- keystore；
- release-signing.properties；
- 服务器密码；
- Cookie；
- pkey；
- token；
- HAR 抓包；
- 诊断日志；
- 官方 APK；
- 反编译临时目录；
- frida server；
- 数据库文件；
- 上传的 APK。

### 10.2 后台安全

当前后台是轻量实现，不是高安全后台。已经有：

- 登录；
- Session Cookie；
- 登录失败限速；
- POST 来源检查；
- 密码文件；
- 错误信息收敛；
- SQLite 持久化。

仍建议：

- 尽快使用 HTTPS；
- 绑定域名；
- nginx 加访问日志和基本限速；
- 定期备份 `data/admin.db`；
- 后台密码不要复用其它账号；
- 不要把后台直接暴露给搜索引擎；
- 上传 APK 后检查 sha256。

### 10.3 App 隐私

App 会保存登录态，也会导出诊断日志。日志可能包含：

- 接口错误；
- 状态码；
- 部分路径；
- 设备型号；
- App 版本；
- 功能状态；
- 非完整的认证摘要。

导出日志前应提醒测试用户不要公开发到群外。

## 11. 开发约定

### 11.1 UI

- 手表优先；
- 小屏优先；
- 圆屏不裁切；
- 不要把按钮做得太大；
- 不要让底部胶囊遮住关键操作；
- 评论、列表、设置项要优先考虑信息密度；
- 动画要短，通常 120-220ms；
- 低性能设备上不要堆复杂阴影和大图模糊。

### 11.2 网络

- 请求失败不要清空旧内容；
- 写入失败要恢复按钮状态；
- Activity 销毁后不要回调 UI；
- 请求超时要短；
- 图片 URL 优先转 HTTPS；
- 不要为了 HTTP 图片打开全局明文。

### 11.3 缓存

- 列表返回要保留位置；
- 图片缓存不要轻易清；
- 手动清缓存要明确清什么；
- 页面刷新时才更新列表数据；
- 返回详情页不要导致主页图片重新加载。

### 11.4 文案

用户多次强调“不要 AI 味”。文案应：

- 短；
- 像正常 App；
- 少解释；
- 少“高级词”；
- 不要在按钮里塞太多字。

例子：

```text
导出日志
清除缓存
交流群
群二维码
检查更新
正在下载
下载失败
```

不推荐：

```text
导出诊断信息
查看反馈群二维码
清除临时缓存和图片缓存
自建服务器更新通道
```

## 12. 建议的后续重构

当前项目能跑，但仍有结构压力。建议按风险从低到高拆：

1. 把评论区从 `MainActivity` 拆成 `CommentSection`；
2. 把设置页拆成 `SettingsPages`；
3. 把帖子详情正文渲染拆成 `DetailRenderer`；
4. 把用户空间拆成 `UserSpacePage`；
5. 把导航和滑动返回拆成独立 controller；
6. 把签到彻底隔离成实验入口；
7. 为 `RichContent` 加小型样本测试；
8. 为后台 `server.py` 加最小 API 自测脚本。

不要一次性大拆。每拆一步都要构建 APK，并至少在手机或模拟器上走：

- 主页；
- 进帖子；
- 看评论；
- 看图；
- 返回；
- 设置；
- 更新检查。

## 13. 发布前总清单

发布前逐项确认：

- [ ] `versionName` 正确；
- [ ] `versionCode` 递增；
- [ ] README 或更新日志已改；
- [ ] `assembleRelease` 成功；
- [ ] APK 已复制到 `dist/`；
- [ ] APK 使用旧签名；
- [ ] APK 可覆盖安装；
- [ ] 主页可加载；
- [ ] 帖子详情可打开；
- [ ] 图片查看器可用；
- [ ] 评论区无明显错位；
- [ ] 点赞/收藏/关注/评论至少抽测；
- [ ] 更新弹窗显示版本号和更新内容；
- [ ] 后台接口返回新版本；
- [ ] 公告接口正常；
- [ ] 没有提交密码、日志、HAR、数据库。

## 14. 维护口径

向测试用户说明问题时建议直接一点：

- “这个版本主要修评论 UI 和图片查看。”
- “签到仍是实验功能，不保证成功。”
- “如果互动失败，先重新登录，再导出日志。”
- “安装提示签名不一致时，说明包不是同一个签名，需要重新构建。”
- “公告点过知道了还弹，优先看公告 ID 是否变化。”

## 15. 快速命令

### 构建 debug

```powershell
cd C:\opencode\HeyBoxCommunity
.\gradlew.bat --no-daemon assembleDebug
```

### 构建旧签名 release

```powershell
cd C:\opencode\HeyBoxCommunity
$env:HEYBOX_RELEASE_STORE_FILE='C:\Users\15989\.android\debug.keystore'
$env:HEYBOX_RELEASE_STORE_PASSWORD='android'
$env:HEYBOX_RELEASE_KEY_ALIAS='androiddebugkey'
$env:HEYBOX_RELEASE_KEY_PASSWORD='android'
.\gradlew.bat --no-daemon assembleRelease
```

### 检查签名

```powershell
C:\opencode\android-sdk\build-tools\36.0.0\apksigner.bat verify --print-certs dist\heybox-Lite-2.0.3.apk
```

### 检查后台语法

```powershell
cd C:\opencode\heybox-admin
C:\Users\15989\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -m py_compile server.py
```

### 检查公网后台

```powershell
Invoke-WebRequest -Uri "http://8.138.134.236/health" -UseBasicParsing
Invoke-WebRequest -Uri "http://8.138.134.236/api/heybox-lite/update" -UseBasicParsing
Invoke-WebRequest -Uri "http://8.138.134.236/admin" -UseBasicParsing
```

## 16. 结语

heybox Lite 现在已经从“能跑的手表小黑盒”进入“需要长期维护体验”的阶段。后续最重要的不是堆功能，而是保持三件事：

1. 主浏览流程稳定；
2. 小屏体验舒服；
3. 高风险接口隔离。

只要这三件事守住，签到、互动、后台统计、公告更新这些功能都可以慢慢修。反过来，如果为了某个高风险接口把主页、帖子、评论、图片这些主流程弄崩，就不值得。
