# heybox Lite

**为 Android 手表和小屏设备打造的小黑盒（HeyBox）社区第三方客户端。**

heybox Lite 不追求把手机端小黑盒完整搬到手表上，而是把「刷社区、看帖子、看图、看评论、做常用互动」这些真正高频的事，重新裁剪进腕上的尺寸里：少一点装饰，多一点可读内容；能滑动解决的地方，就少塞按钮。项目最早参考了 [HeyWear](https://github.com/m16a4666/HeyWear) 的方向，之后围绕方屏、圆屏和低性能手表做了大量自己的取舍。

- **当前版本**：`2.0.5`
- **形态**：单个 Activity 为主的原生 Java View 应用，无 Jetpack Compose、无重量级 UI 框架
- **兼容**：`minSdk 14`，实际体验以 Android 7.0+ 为准；APK 体积约 1.7 MB
- **面向设备**：方屏 / 圆屏手表优先，同时兼顾旧安卓与低内存机型

> 详细的架构、模块、接口与排查说明见 [`docs/PROJECT_DOCUMENTATION.md`](docs/PROJECT_DOCUMENTATION.md)。

---

## 功能

**浏览**
- 社区信息流（双卡片列表 + 底部悬浮胶囊导航），下拉/点击刷新，自动分页加载
- 帖子详情：正文富文本、标题层级、引用块、图片、图片下方图注、作者信息、发布时间与归属地
- 图片查看器：缓存预览秒开、双指/双击分级缩放、点击空白退出、可选查看原图
- **GIF 动图播放**（详情页；低配设备自动降级为静态图）
- **图片离线缓存**：浏览过的图片缓存到本地，重复查看秒开、离线可看、省流量，可自动清理
- 官方表情渲染（正文与评论），失败字段兜底
- 一级 / 二级评论，二级评论采用与官方一致的流式排版

**搜索**
- 顶部搜索入口，搜索历史
- 基于 `offset` 的真实分页，可持续翻页加载更多
- 从帖子返回时保留搜索结果与滚动位置

**账号与互动**
- 二维码扫码登录，游客态可浏览
- 点赞、收藏、关注、评论、评论点赞（依赖小黑盒当前风控，可能偶发失败）
- 我的收藏、浏览历史、个人动态空间

**动效系统（2.0.4）**
- 三挡动画等级：**完整 / 精简 / 关闭**，首次启动按设备内存自动选挡，用户可随时手动切换
- 真实双 View 页面转场、列表首屏交错入场、按压与弹窗反馈
- 全部只操作 GPU 友好属性，尊重系统「减弱动画」，关闭挡零动画开销

**系统与设置**
- 启动公告、更新检查、应用内下载更新并调用系统安装器
- 诊断日志导出到 `Download/heyboxlite`，方便真机排查
- 32 项本地设置：主题色、深色模式、界面/文字/正文字号、行距字距段距、边距、圆屏适配、右滑返回、记住阅读位置、双击回复、动画等级、离线缓存清理、屏蔽关键词等

**配套后台**（[`heybox-admin`](../heybox-admin)，独立项目）
- 提供更新、公告、APK 下载接口与在线人数统计
- 轻量 Python 标准库 HTTP 服务 + SQLite，无框架依赖

---

## 构建

需要 **JDK 17** 与 **Android SDK**（Gradle Wrapper 已随仓库提供）。

```powershell
# 调试包
.\gradlew.bat assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk

# 正式包（签名 + 校验 + 复制到 dist/）
.\gradlew.bat assembleRelease      # → dist/heybox-Lite-<版本>.apk 与 heybox-Lite-latest.apk
```

Release 构建会：混淆 + 资源压缩（R8）→ 校验签名证书是否为预期指纹（`verifyReleaseApkSignature`）→ 复制到稳定路径 `dist/`。签名可通过 `release-signing.properties`，或环境变量 `HEYBOX_RELEASE_STORE_FILE` / `HEYBOX_RELEASE_STORE_PASSWORD` / `HEYBOX_RELEASE_KEY_ALIAS` / `HEYBOX_RELEASE_KEY_PASSWORD` 提供。当 `applicationId` 以 `.preview` 结尾且未配置 release 签名时，会自动回退到 debug 签名，方便本地测试安装。

> 不要把 keystore、签名密码、Cookie、HAR、抓包文件或诊断日志提交到仓库。构建产物 `dist/`、`*.apk`、日志、调试截图均已在 `.gitignore` 中排除。

---

## 项目结构

```text
HeyBoxCommunity/
├─ app/
│  ├─ build.gradle              # 版本、签名、混淆、乱码检查、dist 复制、签名校验任务
│  ├─ proguard-rules.pro
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ java/com/openzen/heyboxcommunity/   # 全部源码（约 40 个类）
│     └─ res/                                 # 矢量图标、主题、网络安全配置
├─ dist/                        # 构建产物（不入库）
├─ docs/PROJECT_DOCUMENTATION.md # 工程详细文档
└─ README.md
```

核心模块（详见工程文档）：

| 模块 | 职责 |
|------|------|
| `MainActivity` | 页面组织、信息流、详情、评论、搜索、我的、设置 |
| `ApiClient` / `EndpointProvider` / `HeyboxSigner` | 小黑盒接口请求、路径混淆、参数签名 |
| `SessionStore` / `LocalCache` / `ModernCookieCrypto` | 本地登录态、设置、离线缓存、Cookie 加密 |
| `RichContent` / `EmojiRenderer` / `ImageLoader` / `GifSupport` | 正文解析、表情、图片加载与离线缓存、GIF |
| `Motions` / `PageTransitionController` / `MotionSpec` | 动效等级与页面转场 |
| `UpdateChecker` / `AnnouncementChecker` / `PresenceReporter` | 更新、公告、在线心跳 |
| `SignInManager` + native 签名模块 | 签到（实验性，与主流程隔离，独立进程） |

主界面仍在 Java View 体系，不切 Compose —— 手表 ROM、低性能设备和旧安卓对包体积、兼容性与渲染开销更敏感，轻量 View 更合适。

---

## 隐私与安全

- 登录态仅保存在本机，Cookie 加密存储（Android 6.0+ 走系统 KeyStore，低版本降级）。
- 在线心跳只上报公开身份（小黑盒 ID / 昵称 / 头像）与版本机型，**不携带** Cookie、pkey、token、签到凭据或浏览内容。
- 诊断日志用于排查，可能包含接口错误和设备环境；不要公开自己的日志、Cookie、HAR 或扫码登录结果。
- 默认禁止明文流量，仅对自建更新服务器 IP 临时放行（待绑定域名后收敛为 HTTPS）。

## 已知限制

本项目依赖小黑盒非公开接口，部分能力不保证长期稳定：

- **签到为实验功能**：小黑盒移动端有额外签名与风控，当前实现不保证每天成功，已在 2.0.4 中默认关闭并与主流程隔离。
- 点赞 / 收藏 / 关注 / 评论等写入操作可能因账号状态、风控或接口参数变化而失败。
- 官方表情、正文结构、图片字段会随接口调整，可能需要持续跟进。
- 不同手表 ROM 的 Web/TLS/安装策略差异较大，2.0 线优先保证 Android 7.0+。

---

## 致谢

- [HeyWear](https://github.com/m16a4666/HeyWear) —— 最初的手表端思路来源。
- 小黑盒官方 App —— 界面与交互上的参考。
- 反馈群的测试用户 —— 很多问题只在真表、弱网和特殊 ROM 上才会暴露。

## 声明

heybox Lite 是**非官方**项目，仅供学习、研究与个人使用，不代表小黑盒、HeyWear 或相关方的官方立场。小黑盒接口非公开稳定 API，字段、签名、风控与访问策略均可能变化。使用时请遵守相关法律法规、平台规则与服务条款，不要用于骚扰、刷量、绕过平台限制等用途。
