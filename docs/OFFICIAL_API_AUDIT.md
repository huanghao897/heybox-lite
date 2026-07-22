# 小黑盒官方接口核对记录

## 核对基准

- 官方 APK：小黑盒 `1.3.391`（build `1112`）。
- 本地反编译目录：`C:\opencode\_jadx_full\sources`。
- 主要依据：官方 Retrofit 声明、对应页面调用点和分页状态更新逻辑。
- 本文只核对请求路径、HTTP 方法、业务参数、分页和触发频率，不把非公开接口描述为长期稳定 API。

## 当前接口映射

| 功能 | Lite 请求 | 官方依据 | 结果 |
| --- | --- | --- | --- |
| 社区信息流 | `GET /bbs/app/feeds` | `datasource/a.java`、`FeedsRetrofitNetworkDataSource.java` | 使用 `pull/last_pull/lastval/unexposed/is_first/refresh_type/list_ver=2`；空可选字段不发送，不再使用 `offset` |
| 帖子详情 | `GET /bbs/app/link/tree/v2` | `com/max/common/common/datasource/f.java` | 使用 `h_src/link_id/page/limit/is_first/owner_only`；不再回退 v1 或发送 `index` |
| 楼中楼 | `GET /bbs/app/comment/sub/comments` | `network/e.java:L7` | 只发送 `root_comment_id/lastval/h_src/hide_cy`；按响应 `lastval/has_more` 翻页并阻止并发重复点击 |
| 搜索 | `GET /bbs/app/api/general/search/v1` | `network/e.java:i1` | 使用 `q/search_type/offset/limit`；不发送重复的 `keyword` 或无效 `page` |
| 用户动态 | `GET /bbs/app/profile/user/link/list` | `network/e.java:S7` | 使用 `userid/offset/limit/no_banner`；无自动轮询 |
| 云端历史 | `GET /bbs/app/profile/history/visit` | `network/e.java:T0` | 使用 `type=link/offset/limit`；不发送用户 id、桌面系统或浏览器伪装字段 |
| 收藏标签 | `GET /bbs/app/profile/fav/tab_list` | `network/e.java:ta` | 打开收藏时请求一次 |
| 默认收藏内容 | `GET /bbs/app/profile/fav/folder/v2/links` | `network/e.java:u6` | 标签成功后请求一次，默认标签不发送 `folder_id`；使用 `offset/limit=30/enable_new_style_collect=1` |
| 官方表情 | `GET /bbs/app/api/emojis/list` | `ie/a.java`、`hbexpression/f.java` | 首次无缓存版本时省略 `emoji_version`；成功后本进程不重复请求，失败冷却 5 分钟 |
| 帖子点赞 | `POST /bbs/app/profile/award/link` | `network/e.java:d3` | 查询参数 `h_src`，表单 `link_id/award_type` |
| 收藏/取消收藏 | `POST /bbs/app/link/favour` | `network/e.java:T/Z3` | 查询参数 `h_src`，表单 `link_id/favour_type`；默认收藏不发送空 `folder_id` |
| 关注/取关 | `POST /bbs/app/profile/follow/user`、`POST /bbs/app/profile/follow/user/cancel` | `network/e.java:T7/a6` | 独立路径；表单只发送 `following_id`，`h_src` 在查询参数中 |
| 评论点赞 | `POST /bbs/app/comment/support` | `network/e.java:t9` | 查询参数 `h_src`，表单 `comment_id/support_type` |
| 发评论 | `POST /bbs/app/comment/create` | `network/e.java:L5` | 顶层评论不发送 `root_id/reply_id/recommend_state`；回复时才发送两个 id；保留 `imgs=""/is_cy=0`，`auth_code` 可选 |
| 签到 | `GET /task/sign_v3/sign` | 官方签到管理器与 Retrofit 声明 | 当前双重禁用，不会发起状态查询或签到请求 |

写操作的状态值也按官方页面实际调用核对，而不只看 Retrofit 参数名：帖子点赞/取消为 `award_type=1/0`，收藏/取消为 `favour_type=1/2`，评论点赞/取消为 `support_type=1/2`。官方新帖子数据层关注作者时会给 Retrofit 的 `follows` 参数传 `null`，因此 Lite 只发送 `following_id`，取消关注使用独立的 `/cancel` 路径。

## 请求频率与重试审计

- `ApiClient` 本身没有自动重试。
- 信息流只在首屏、用户下拉/再次点社区，或列表接近末尾时请求；加载更多按 `lastval` 前进，游标不变即停止。
- 搜索、用户动态、历史、收藏、详情和评论均由明确页面操作触发，不存在定时轮询。
- 写操作一次点击只提交一种官方请求。参数错误、登录错误、风控错误都不会换字段或换身份环境再试。
- 只有服务端明确返回 `lack_token` 或 `x_xhh_tokenid` 时，才获取一次写入 token，并原样重放原请求一次。
- 风控响应立即终止，并在本进程暂停写操作 10 分钟；普通失败不会自动重试。
- 二维码登录是项目现有的网页登录流程，不是官方 APK 的移动端登录页。它只在登录页前台每 2 秒查询，网络失败退避到 5 秒，退后台暂停，5 分钟过期后停止。
- 自建服务器的 10 分钟在线心跳不访问小黑盒域名，不计入官方接口风控审计。

## 身份环境说明

Lite 当前登录态来自网页登录，因此普通读写请求继续使用 Web Cookie 和现有 Web 请求头。业务路径与字段已经按官方 App 对齐，但认证环境并不等同于官方移动端；直接把所有请求切换到移动端参数会缺少与账号绑定的官方移动凭据，并可能让目前可用的点赞、收藏、关注和评论全部失效。

因此本轮采用保守边界：

1. 不伪造不存在的移动端凭据。
2. 不在失败后轮换 Web/Mobile/Official 多种请求环境。
3. 不调用官方 App、也不读取官方 App 的 Cookie。
4. 不承诺可以规避平台风控；服务端仍可基于账号、设备、频率和行为判定风险。

## 后续核对规则

官方接口变化时，必须先从同版本官方 APK 或用户自行提供的合法请求记录确认路径与字段，再修改 `EndpointProvider` 和 `OfficialRequestParams`。不得通过增加兼容别名、连续回退接口或自动高频重试来“碰”出可用组合。
