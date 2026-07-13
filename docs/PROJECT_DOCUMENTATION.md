# heybox Lite 工程文档

面向后续维护、构建、发布与排查的工程说明。目标是让接手者能快速理解项目结构、核心流程与风险点，少踩坑。所有内容以仓库当前代码为准（版本 `2.0.5` / versionCode `208`）。

> 本文尽量只描述**项目本身**，不写入任何本机绝对路径、keystore 密码或服务器内部路径。这类信息属于各自环境的私有配置，请勿写进公开仓库。

---

## 1. 项目定位

heybox Lite 是面向 **Android 手表和小屏设备**的小黑盒（HeyBox）社区第三方客户端。它不复刻手机端，而是在腕上尺寸内优先保留高频能力：刷信息流、搜索、看正文与长图、看评论、点赞收藏关注评论、看收藏历史动态、收公告查更新、出问题时导出日志。

从 1.x 演进到 2.0 线：1.x 偏「功能能跑」，2.0 起重做 UI、交互、圆屏适配、后台统计，2.0.4 加入动效系统、GIF 播放与图片离线缓存。代码以 **Java View 体系**为主，不引入 Compose 或大型 UI 框架 —— 手表 ROM、低性能设备与旧安卓对包体积、兼容性、渲染开销更敏感。

---

## 2. 技术栈与关键配置

| 项 | 值 |
|----|----|
| 语言 / UI | Java，原生 View（无 Compose） |
| `applicationId` | `com.ronan.heyboxlite.preview` |
| `namespace` / 包名 | `com.ronan.heyboxlite` |
| `versionName` / `versionCode` | `2.0.5` / `208` |
| `minSdk` / `targetSdk` / `compileSdk` | `14` / `35` / `36` |
| JDK | 17 |
| 第三方依赖 | 仅 `com.google.zxing:core:3.3.3`（二维码登录） |
| APK 体积 | 约 1.7 MB（Release，R8 混淆 + 资源压缩） |

`BuildConfig` 注入的服务地址（来自 `app/build.gradle`）：

```gradle
RELEASE_CERT_SHA256      // 正式签名证书指纹（用于自校验）
UPDATE_API_URL           // 更新接口
UPDATE_FALLBACK_URL      // 更新失败时的浏览器兜底地址
ANNOUNCEMENT_API_URL     // 公告接口
```

`app/build.gradle` 还注册了几个自定义任务：

- `verifyTextEncoding`：扫描源码中的乱码标记（如 `锟斤拷`、`Ã`），编译前拦截，避免中文被错误编码破坏。
- `verifyReleaseApkSignature`：Release 打包后用 `apksigner` 校验 APK 证书指纹是否等于预期值（preview 包用 `previewReleaseCertSha256`，正式包用 `RELEASE_CERT_SHA256`），不符则终止发布。
- `copyReleaseApkToDist`：把签名后的 Release APK 复制为 `dist/heybox-Lite-<版本>.apk` 与 `dist/heybox-Lite-latest.apk`。

---

## 3. 设计原则

1. **不要把手机端整体搬进手表**：新增功能优先考虑能否少一层页面、是否遮挡底部胶囊、圆屏边缘是否裁切、低性能能否扛住、断网时旧内容能否保留。
2. **隔离高风险功能**：签到、写入接口、移动端签名、官方 App 凭据读取都可能受风控影响，必须与主浏览流程隔离 —— 签到失败不能污染登录态，互动失败不能拖垮主页加载。
3. **失败要优雅**：请求失败不清空旧列表；UI 回调前检查 Activity 状态；写入失败要恢复按钮状态；接口返回风控时直接提示，不重复轰炸。
4. **不覆盖用户数据**：部署后台时只同步代码与静态资源，不动服务器上的数据目录（SQLite、上传的 APK、公告、统计）。
5. **改完即验证**：改动源码后应能通过签名 Release 构建与签名校验，再提交。构建失败不推送未验证代码。

---

## 4. 目录结构

```text
HeyBoxCommunity/
├─ app/
│  ├─ build.gradle
│  ├─ proguard-rules.pro
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ java/com/ronan/heyboxlite/   # 全部源码
│     └─ res/
│        ├─ drawable/         # 矢量图标（il_* 线性图标、ic_* 功能图标）
│        ├─ mipmap-*/         # 应用图标
│        ├─ values/           # 主题、字符串
│        └─ xml/network_security_config.xml
├─ dist/                      # 构建产物（.gitignore 排除）
├─ gradle/ + gradlew.bat
├─ docs/PROJECT_DOCUMENTATION.md
└─ README.md
```

`.gitignore` 已排除：构建产物、`*.apk`、keystore/签名配置、`local.properties`、`*.har`、`*.log`、logcat、`adb-*` 调试截图与日志、诊断产物等，避免误提交敏感文件。

---

## 5. 应用组件（AndroidManifest）

**权限**

| 权限 | 用途 |
|------|------|
| `INTERNET` | 接口、图片、更新、公告 |
| `ACCESS_NETWORK_STATE` | 网络状态判断（离线缓存决策） |
| `REQUEST_INSTALL_PACKAGES` | 应用内下载 APK 后拉起系统安装器 |
| `WRITE_EXTERNAL_STORAGE`（`maxSdk 28`） | Android 9 及以下导出日志 |

**`<queries>`**：声明可见 `com.max.xiaoheihe`（官方 App）及其 `statusprovider`，用于检查官方 App 与签到实验相关的凭据读取。

**组件**

| 组件 | 类型 | 说明 |
|------|------|------|
| `SplashActivity` | Activity（LAUNCHER） | 启动页，可开关的打字机开屏动画 |
| `MainActivity` | Activity（`singleTop`） | 主界面与绝大多数页面 |
| `ImageViewerActivity` | Activity | 大图查看器（独立主题） |
| `NativeSignService` | Service（`:native_signer` 独立进程） | native 签名尝试，崩溃不拖垮主进程 |
| `DiagnosticsProvider` | FileProvider | 诊断日志分享 |
| `UpdateApkProvider` | FileProvider | 应用内更新 APK 分享给系统安装器 |

**网络安全**（`res/xml/network_security_config.xml`）：默认禁明文；临时对自建更新服务器 IP 放行明文 HTTP。绑定域名并配置 HTTPS 后应收敛该白名单。

---

## 6. Android 模块详解

全部源码约 40 个类，位于 `app/src/main/java/com/ronan/heyboxlite/`。下面按职责分组。

### 6.1 入口与外壳

- **`SplashActivity`** — 启动页。安装崩溃处理器（`CrashReporter`），按设置显示打字机开屏动画后进入 `MainActivity`；关闭开屏时直接跳转。
- **`MainActivity`** — 项目最大的类，承载底部胶囊导航、信息流、搜索、帖子详情、评论区、评论发布、我的、收藏、历史、动态空间、设置、公告、关于、更新弹窗、滑动返回与页面转场。页面状态用 `screen` 字符串维护（`feed` / `search` / `profile` / `detail` / `settings_home` / `saved` / `user_space` / `announcement_board` / …），各页通过 `show*()` 方法切换。历史演进导致其体量较大，后续若拆分建议按页面（Feed/Search/Detail/Comment/Profile/Settings/Navigation）切分，且优先保证功能不退化。
- **`ThemeTokens`** — 主题令牌：由深色开关与主/辅色派生出背景、面板、文字、次要文字、分隔线、`onPrimary` 等一整套颜色，并提供 `blend()` / `contrast()` 静态工具。
- **`UiComponents`** — 统一组件外观：卡片、主按钮、幽灵按钮、软胶囊、按压反馈 `press()`（尊重动效等级）。
- **`Compat`** — API 兼容层：`setBackground`、`clipToOutline`、`setLetterSpacing`、系统栏着色、各类 tint、全屏 flag 等，集中处理 `Build.VERSION` 分支，业务代码不再散落版本判断。

### 6.2 动效系统（2.0.4 新增）

- **`Motions`** — 全局动效等级与通用动画工具。等级三挡：`LEVEL_OFF`(0) / `LEVEL_LITE`(1) / `LEVEL_FULL`(2)，由 `SessionStore` 持久化并在启动时注入。提供 `reset()`（取消动画并复位 alpha/translation/scale，防复用污染）、`enter()`（面板入场）、`listEnter()`（列表交错入场）、`dialogIn()`（弹窗缩放淡入，完整挡带回弹）。只操作 GPU 友好属性，关闭挡直接到位。
- **`MotionSpec`** — 动效常量：各类时长阶梯、`EASE_OUT`（减速）与 `SPRING`（`OvershootInterpolator`，仅完整挡收尾）。
- **`PageTransitionController`** — 真实双 View 页面转场。切换时新旧页面同时存在于容器中，顶层加透明遮罩挡触摸；**旧页原地不动**（不 detach，避免丢列表滚动位置），动画结束移除并 `reset()` 供缓存页复用。完整挡为滑动 + 视差、精简挡为交叉淡入、关闭挡直接硬切；旧内容若是加载圈则退化为一次淡入。`MainActivity.transitionTo()` 是统一入口，`pendingBackTransition` 决定前进/返回方向；详情页有自己的滑动返回机制，不走此转场。

**首启选挡**：`SessionStore.motionLevel()` 首次读取时按设备判定 —— `ActivityManager.isLowRamDevice()` 或 `memoryClass <= 64` 判为关闭，否则精简；「完整」只由用户在设置里主动选择。判定结果固化，之后完全听用户设置。

### 6.3 内容渲染

- **`RichContent`** — 帖子正文与评论文本解析。输入可能非常混乱（纯文本 / HTML / JSON 数组 / 富文本对象 / 图片标签 / 官方表情 / 转义后的 HTML·JSON / 嵌套结构）。输出为 `Block` 列表，每块有类型：

  ```java
  Block { int kind; boolean image; String value; }
  // kind: TEXT / IMAGE / HEADING / CAPTION / QUOTE
  ```

  能力：正文图片按顺序显示、图片间文字不丢、官方表情转可渲染 token；`h1–h6`/`figcaption` 拆为**标题**块、`blockquote` 与 JSON `quote` 类型拆为**引用块**（保留内部换行与列表）、`<img>` 的 `desc`/`data-desc`/`data-caption` 及 JSON `caption` 拆为**图注**块；评论用 `commentText()` 从多个候选字段选最完整文本并把内联表情压回同一行；避免把 JSON 噪声当正文。

- **`EmojiStore` / `EmojiRenderer` / `OfficialEmojiFallback`** — 官方表情三层：`EmojiStore` 保存 token→URL 映射（接口拉取 + 多形态变体），`OfficialEmojiFallback` 内置常见表情 URL，`EmojiRenderer` 在 `TextView` 中把 token 替换成 `ImageSpan`。`EmojiRenderer` 还提供 **`Decorator`** 回调：在每次 `setText`（含表情异步加载完成后的重绘）前重新套用额外样式 —— 二级评论正是用它保证用户名颜色 span 不被异步刷新冲掉。

- **`ImageLoader`** — 图片加载、缓存、解码与**离线缓存**。内存 LruCache + 磁盘离线缓存（`offline-cache/images`，URL 哈希命名，上限约 96 MB，按时间 `pruneOffline`）。缩略图与原图策略分离，限制最大 Bitmap 尺寸/像素防 OOM；列表图失败不清空旧内容，正文图支持渐进 reveal，Activity 销毁时取消请求。关键入口：`into*`（缩略图/稳定/渐进）、`intoOriginal*`、`loadOriginal`、`prefetchOffline`（预取信息流/详情图）、`intoGif`（见下）、`offlineBytes`（统计用量）。
- **`GifSupport`** — 详情页 GIF 动图播放。`ImageLoader.intoGif` 拉原始 `.gif` 字节（上限 8 MB）后台解码：API 28+ 用系统 `AnimatedImageDrawable`（硬件解码），低版本用 `Movie` 软件渲染；超过 1280 px 或体积超限则放弃、保留静态缩略图，防低配手表 OOM。静态缩略图先显示，动图解好后原地替换。受设置「帖子内播放动图」开关控制。
- **`ImageViewerActivity` / `ZoomImageView`** — 大图查看器：半透明背景、缓存预览秒开、加载原图、点击空白退出；`ZoomImageView` 支持双指缩放、拖动、双击分级放大（长截图首次双击按屏宽铺满）与单击复位。
- **`LoadingSpinnerView`** — 自绘的轻量转圈 View（`Canvas` 画弧 + 定时重绘），可见时才动画，不可见/detach 时停止。

### 6.4 列表与模型

- **`FeedItem`** — 帖子列表中间模型：把接口 JSON 归一成 id、标题、摘要、作者、作者 id/头像、话题、封面图、多图、评论数、点赞数、是否文章、是否置顶、`hsrc` 等，并可序列化回 JSON 供离线缓存。
- **`FeedAdapter`** — 主页/搜索/收藏/历史等列表 UI（`BaseAdapter` + Holder 复用）。图片用缩略图、失败显示灰占位、不用旧图覆盖当前项。**入场动画只对本轮首批（按帖子 id 去重，前 6 条）交错播放一次**，每次绑定先 `Motions.reset()` 复位变换 —— 解决了「复用行滚动中途重播动画」的闪动问题。

### 6.5 网络层

- **`ApiClient`** — 小黑盒接口请求层。拼接 baseUrl+path、装配请求头、加签名参数、处理 GET/POST、超时控制、gzip 解码、JSON 解析、错误归一化、写诊断日志、按 `RequestProfile`（Web / Mobile / 多种 Official 变体）切换凭据与头，并在官方原生客户端场景下经 `NativeSignBridge` 追加原生安全参数。任务/写入类请求会额外记录调试日志（键名与顺序，不含敏感值）。
- **`EndpointProvider`** — 集中保存接口路径，经异或混淆存储（运行时解码），避免明文接口散落。覆盖：信息流、帖子详情（v1/v2）、二维码登录、评论（子评论/点赞/发布）、用户资料与动态、历史、收藏（标签/文件夹/链接/列表）、表情、搜索、互动（点赞组合/打赏/收藏/关注/取关）、签到（v2/v3 及状态）等。**这些不是公开稳定 API，path 与参数可能随官方版本变化，修接口前先看诊断日志，不要盲改。**
- **`HeaderProvider`** — 按请求档位组装请求头与 Cookie（Web UA / 移动端 UA / 官方各变体），签到走独立的凭据来源，与普通登录态隔离。
- **`HeyboxSigner`** — 请求签名（Legacy / Android 等算法）。
- **`SecureStrings`** — 敏感字符串（Cookie 键名、参数名、pkey/token/heyboxid 字段名等）异或编码，避免明文出现在反编译结果里。

### 6.6 本地状态与缓存

- **`SessionStore`** — 本地登录态与全部设置项（详见 [§8](#8-设置项清单)）。登录 Cookie 加密存储（`ModernCookieCrypto`），用户 id/昵称/头像，各类显示与交互开关，签到相关字段与普通 Cookie 严格隔离。`motionLevel()` 首启按设备判定。`resetToDefaults` 清空时保留设备 id、全部显示/交互偏好与搜索历史等，不误删用户配置。
- **`LocalCache`** — 本地缓存与诊断日志：缓存信息流与帖子详情（详情按数量上限滚动清理）、写会话日志（新会话把上一份转存为 previous）、写崩溃日志与 native 签名日志、导出诊断到 `Download/heyboxlite`（`.txt`）。也保存「本版本官方 native 是否已禁用」等运行标志。
- **`ModernCookieCrypto`** — Cookie 加密（Android 6.0+）：AES/GCM，密钥存 AndroidKeyStore，IV 与密文一起打包 Base64。低版本无法使用时由 `SessionStore` 降级处理。
- **`CrashReporter`** — 全局未捕获异常处理：写 `crash-latest.log`（旧的转 `crash-previous.log`），按字节预算截断（中文按 3 字节/字换算），末端兜底不让崩溃路径二次崩溃。

### 6.7 更新 / 公告 / 在线

- **`UpdateChecker`** — 读自建更新接口，判断是否有新版本（优先 `hasUpdate`/`versionCode`，回退版本名比较），产出版本、标题、更新日志、下载与兜底地址。更新失败不影响进主界面，TLS/网络失败优雅提示。仅信任白名单更新主机。
- **`AnnouncementChecker`** — 读公告接口：新用户首启显示欢迎公告，已「知道了」的不重复弹，关于页可看公告列表，本地记录已读公告 id。
- **`PresenceReporter`** — 前台在线心跳，每 10 分钟触发一次，退后台即停。账号首次登记时上报公开身份、头像与设备信息；成功登记后只发送用户 id、版本、versionCode 和累计阅读时长。服务端从连接中获取 IP；请求不携带 Cookie/pkey/token/签到凭据、评论正文或浏览记录。
- **`UpdateApkProvider`** — 应用内下载的更新 APK 通过 FileProvider 分享给系统安装器。
- **`DiagnosticsProvider`** — 诊断日志通过 FileProvider 分享。

### 6.8 签到与 native（实验，隔离）

签到是实验功能：真实小黑盒签到涉及移动端凭据、签名、风控与官方 App 行为。**2.0.4 已在 `SignInManager` 顶部用开关默认关闭**（服务端风控，第三方无法稳定通过校验），入口在设置里提示「已暂停」，底层 `currentState/autoSignInIfNeeded/signIn` 全部拦截、零请求。相关代码保留，改回开关即可恢复。

- **`SignInManager`** — 签到流程编排（当前被开关禁用）。曾尝试公开接口、`task/sign_v3`、移动端参数、pkey/imei/token、官方 Provider、native signer、请求重放等多路径。
- **`WriteTokenProvider`** — 通过隐藏 WebView 拿写入验证 token（点赞/收藏/关注/评论等写操作需要）。
- **`NativeSignBridge` / `NativeSignService` / `NativeSecuritySigner` / `NativeLibraryLoader`** — native 签名尝试，跑在 `:native_signer` 独立进程，崩溃不拖垮主进程。
- **`OfficialContext` / `OfficialAppVerifier` / `OfficialNativeSigner` / `AppIntegrityCheck` / `ModernSignatureReader`** — 校验官方 App、尝试复用官方相关能力、读取应用签名等。

> 这是高风险区域。任何改动前必须先看诊断日志；维护铁律：签到失败不影响主页、不清登录态、不污染普通写入请求。

---

## 7. 关键流程

**启动**：`SplashActivity` 装崩溃处理器 → 开屏动画（可关）→ `MainActivity.onCreate` 初始化 `SessionStore` / `ApiClient` / `LocalCache` / `ImageLoader.init` / 注入 `Motions` 等级 / `WriteTokenProvider` → 构建外壳 → `showFeed()` → 触发一次在线心跳、（可选）检查更新与公告。

**信息流**：`showFeed()` 优先复用缓存容器（走转场恢复滚动位置）；否则先填离线缓存再请求，`collectFeedItems` 递归从任意 JSON 结构里按 id 去重抽出帖子，`FeedAdapter` 渲染，滚动接近底部自动加载下一页。

**帖子详情**：`showDetail` 请求 link tree（失败回退 v2）→ `RichContent.parse` 出块列表 → 按块渲染（标题/引用/图注/图片/正文，多图非文章帖走横滑图集）→ GIF 块触发 `intoGif` → 评论区分级渲染（一级带头像/等级/时间地点，二级流式排版）→ 左滑到评论、右滑返回、按设置记住阅读位置。

**页面转场**：任意 `show*` 页通过 `transitionTo()` 进入 `PageTransitionController`，按动效等级与方向做滑动/淡入/硬切；详情页独立处理。

**搜索**：`offset/limit` 分页（官方接口忽略 page 参数），滚动到底自动加载、按 id 去重、连续空页判定到底；返回时恢复关键词、结果与滚动位置。

**离线缓存**：`prefetchOffline` 预取可见图到磁盘，重复浏览与离线走本地；设置里显示用量、可自动清理。

**在线上报**：`PresenceReporter` 前台定时 `POST /api/presence`，后台按用户/设备聚合（见下）。

---

## 8. 设置项清单

`SessionStore` 暴露的可配置项（设置页可调）：

| 分组 | 项 |
|------|----|
| 显示 | 深色模式、界面大小、文字大小、页面左右边距、圆屏适配、圆屏横/纵向边距、主色 / 辅色（`setTheme`） |
| 正文排版 | 正文字号、字距、段落间距、行距、正文加粗 |
| 图片 | 无图模式、查看器允许原图、帖子内播放动图、离线缓存自动清理 |
| 动效 | 动画等级（关闭 / 精简 / 完整） |
| 交互 | 右滑返回、记住帖子阅读位置、双击评论回复 |
| 更新 / 开屏 | 自动检查更新、开屏动画开关、开屏文案、开屏时长 |
| 内容 | 屏蔽关键词 |
| 公告（内部） | 已读公告 id |
| 签到（隔离，内部） | 签到尝试/成功日期、签到摘要、官方凭据是否导入 |

默认开屏文案：`方寸之间，看见热爱`。

---

## 9. 后台服务

配套项目 `heybox-admin`（独立仓库/目录）。技术栈：**Python 3 标准库 `HTTPServer`（`ThreadingMixIn`）+ SQLite，无 Flask/Django**；前端为纯静态 HTML/CSS/JS；生产环境由 systemd 托管、nginx 反代。

### 9.1 目录

```text
heybox-admin/
├─ server.py                 # 全部后端逻辑
├─ static/
│  ├─ index.html             # 官网（下载/公告/版本）
│  ├─ admin.html             # 管理后台（SPA）
│  └─ 静态图片资源
├─ deploy/                   # systemd service + nginx 配置
└─ data/                     # 运行数据（勿覆盖）：admin.db、uploads/、admin_password.txt
```

### 9.2 主要环境变量

| 变量 | 说明 |
|------|------|
| `HEYBOX_DATA_DIR` | 数据目录 |
| `HEYBOX_ADMIN_PASSWORD` / `HEYBOX_ADMIN_PASSWORD_FILE` | 后台密码（文件优先，改密写入文件） |
| `HEYBOX_SESSION_SECRET` | 会话签名密钥（随机长串） |
| `HEYBOX_PUBLIC_BASE_URL` | 影响更新接口返回的下载地址 |
| `HEYBOX_BIND` / `HEYBOX_PORT` | 监听地址/端口（建议绑 `127.0.0.1`，由 nginx 对外） |
| `HEYBOX_MAX_UPLOAD_MB` | APK 上传大小上限 |

### 9.3 SQLite 表

| 表 | 用途 |
|----|------|
| `announcements` | 公告 |
| `releases` | 上传的 APK 版本（版本名/号、日志、大小、sha256、下载数、是否发布） |
| `access_logs` / `access_visitors` / `access_daily` | 访问日志、总访客去重、每日访客去重 |
| `download_logs` | 下载记录 |
| `error_logs` | 服务错误记录 |
| `presence` / `presence_daily` | 按 **IP** 的在线心跳与每日活跃 |
| `presence_users` / `presence_users_daily` | 按**用户/设备身份**聚合的在线数据与每日活跃 |

> **在线统计为双轨**：`presence`（IP 级）用于兜底与访客视角；`presence_users`（用户/设备级，2.0.4 起 App 上报公开身份后启用）用于更精准的「当前在线 / 注册用户数 / 累计设备 / 今日活跃」。管理后台的用户视图主要基于 `presence_users`。2.0.5 起客户端每 10 分钟发送一次心跳，服务端在线窗口为 15 分钟。

### 9.4 公开接口

| 接口 | 说明 |
|------|------|
| `GET /health` | 健康检查 |
| `GET /api/latest`（别名 `/api/heybox-lite/update`） | 当前发布版本（版本、日志、apkUrl、大小、sha256、forceUpdate、hasUpdate） |
| `GET /api/releases` | 公开版本列表 |
| `GET /api/announcements`（别名 `/api/heybox-lite/announcement`） | 公开公告列表 |
| `GET /download/latest.apk` / `GET /download/<filename>` | 下载最新/指定 APK（记录下载） |
| `POST /api/presence` | App 前台在线心跳上报 |

### 9.5 管理接口（需登录）

| 接口 | 说明 |
|------|------|
| `POST /admin/login` / `POST /admin/logout` | 登录 / 退出（HMAC 签名会话 Cookie） |
| `GET /api/admin/summary` | 后台总览（releases、announcements、统计、在线、下载、访问、最新版本） |
| `POST /api/admin/releases`（`/<id>/publish`、`/<id>/delete`） | 上传 / 发布为最新 / 删除 APK |
| `POST /api/admin/announcements`（`/<id>/delete`） | 创建 / 删除公告 |
| `POST /api/admin/password` | 修改后台密码 |

安全：登录失败限速（5 分钟内 6 次）、写操作校验 same-origin、密码用常量时间比较、会话 7 天有效。

### 9.6 部署要点

只同步代码与静态资源（`server.py`、`static/`、`deploy/`），**不覆盖 `data/`**；部署前备份旧代码，覆盖后重启服务并用 `/health`、更新接口、`/admin` 做健康检查。`server.py` 每次请求实时读取 `static/*.html`，改前端无需重启服务。

---

## 10. 构建与发布

**环境**：JDK 17 + Android SDK（含 build-tools 的 `apksigner`）+ 随仓库的 Gradle Wrapper。

```powershell
# Debug
.\gradlew.bat assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk

# Release（签名 → 校验 → 复制 dist/）
.\gradlew.bat assembleRelease      # → dist/heybox-Lite-<版本>.apk、dist/heybox-Lite-latest.apk
```

**签名**：通过 `release-signing.properties` 或 `HEYBOX_RELEASE_STORE_FILE` / `..._STORE_PASSWORD` / `..._KEY_ALIAS` / `..._KEY_PASSWORD` 环境变量提供。`applicationId` 以 `.preview` 结尾且未配置 release 签名时自动回退 debug 签名，方便本地覆盖安装。

**签名校验**：`verifyReleaseApkSignature` 会用 `apksigner verify --print-certs` 核对证书 SHA-256；不符则 Release 失败。手动核对：

```powershell
apksigner verify --print-certs dist\heybox-Lite-<版本>.apk
```

**版本号**：`versionCode` 需单调递增；`versionName` 遵循既定节奏（当前 2.0.x）。仅在明确要发新版时才改版本号。

**发布分层**：代码提交推送到 GitHub 与「发布到更新服务器」是两件独立的事 —— 推 GitHub 不等于发布上线，服务器发布需单独执行（后台上传 APK 并设为最新版）。发布前核对：APK 签名正确、版本号与日志正确、`latest.apk` 指向新版、App 内更新弹窗能显示日志、后台接口返回 200。

---

## 11. 排查指南

| 现象 | 优先排查 |
|------|----------|
| 安装提示「签名不一致」 | Release 是否用预期签名构建；`apksigner verify --print-certs` 核对指纹；设备上是否已有不同签名的旧包 |
| 更新弹窗没有更新内容 | 后台 release 的 changelog 是否为空；更新接口是否返回 changelog；App 是否读对服务器、是否缓存旧结果；后台是否已「设为最新版」 |
| 公告每次都弹 | 公告 `id` 是否每次变化；`SessionStore` 已读公告是否保存；欢迎公告是否被当新公告；App 数据是否被清空 |
| 评论只显示表情 / 吞字 | `text/content/html/description` 字段是否不一致、首字段是否只有表情；`RichContent.commentText` / `normalizeInlineImageEmojis` / `EmojiStore.url`；诊断日志里的原始评论字段 |
| 正文图片顺序错 / 重复 | `RichContent.parse` / `addStructured` / `addArticleHtml` / `addFallbackImagesIfNeeded`；`imgs` 字段与 HTML 图片是否重复、去重 key 是否正确 |
| 图注/引用没生效 | 该帖正文块结构（导出日志看 `HEAD/CAP/QUOTE/TXT` 标注）；图注来源字段（`figcaption`/`desc`/`data-caption`/JSON `caption`） |
| GIF 不动 | 是否详情页、原图是否 `.gif`、体积是否超 8 MB / 尺寸超 1280 px、「帖子内播放动图」开关是否开 |
| 页面切换黑边/闪烁/位置丢 | 动效等级；`PageTransitionController` 是否对该页启用；缓存容器是否被正确复用；关闭挡应立即到位 |
| 列表滚动中卡片闪动画 | `FeedAdapter` 入场是否只对首批按 id 播一次、每次绑定是否 `Motions.reset()` |
| 点赞/收藏/关注突然全失败 | 写入 token 失效、Cookie 失效、风控、签名参数变化；是否有签到相关改动污染了普通写入请求 |

排查通用手法：设置 → 导出日志（`Download/heyboxlite`），日志含接口请求键名/顺序、错误归一化信息与正文块结构诊断，据此定位而非盲改接口。

---

## 12. 声明与约束

heybox Lite 为非官方项目，仅供学习、研究与个人使用。小黑盒接口非公开稳定 API，字段、签名、风控与访问策略均可能变化。不要提交 keystore、签名密码、Cookie、HAR、抓包文件、诊断日志或服务器密码到仓库；不要用于骚扰、刷量或绕过平台限制。
