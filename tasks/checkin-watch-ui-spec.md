# 小黑盒签到手表 UI

## 目标与已确认要求

- 按用户提供的六页参考图重排签到概览、设置、会员服务、签到记录、记录详情和确认开通。
- 保留当前主题与黑灰默认配色，不复制参考图蓝色。页面采用紧凑独立卡片与明确的主操作，保持已有登录、配对与撤销功能。
- 真实套餐、金额、权益、订单与日志来自 CheckinCenter；不硬编码参考图的账号、日期、价格或支付成功状态。
- 网站默认套餐为 30 天 5 元、90 天 10 元；离线视觉夹具使用同一基准，实际展示和下单仍遵循服务器套餐。非整额及其他币种只用于专门的协议回归测试。
- 用户已授权补齐 Lite 的套餐、兑换和购买记录接口。沿用现有设备授权、收费服务和支付确认，不改变网站原有行为、不触碰签到签名实现。
- 同时适配方屏与圆屏。小尺寸内容允许换行，列表末项能滚动至圆形可点击区；保留表冠与返回。

## 结构与协议

- Android: `app/src/main/kotlin/com/ronan/heyboxlite/ComposeCheckin*` 为 UI 与控制器；Java 服务继续负责加密、授权和请求。
- 会员请求独立放入 `CheckinMembershipApi`，不继续扩大 `CheckinCenterClient`。
- `GET /checkin/api/lite/billing/catalog`: 与状态接口相同的平铺会员字段和 `products`；增加真实服务端 `checkout_provider`。
- `GET /checkin/api/lite/billing/orders`: `items` 数组，每项含 `order_id/product_sku/product_name/amount_cents/payable_amount_cents/currency/status/created_at/expires_at/duration_days`。不得含授权凭据、扫码载荷或他人订单。
- `POST /checkin/api/lite/billing/redeem`: 仅接收 `{"code":"..."}`，成功返回与 catalog 相同的平铺会员信息。
- 现有创建订单使用服务端 `product_sku`，付费模式不上传客户端金额或指定支付渠道。`checkout_url` 来自服务端，验证 HTTPS/主机后在手表生成扫码入口；遗留 PNG 支付码仍可显示。
- 支付查询仅在确认开通页面可见且应用前台时进行，至少 15 秒一次，单个在途请求；支付完成和权益以服务器为准。

## 实施计划

- [x] 复用网站授权与业务服务增加三个接口，并验证账号隔离、HTTPS、限速、兑换与真实套餐字段。126 项相关服务端测试通过，未部署。
- [x] 补齐 App 会员解析、请求与页面状态；保留旧构造和旧客户端兼容行为。
- [x] 按参考重排概览与设置，记录和详情独立呈现；登录入口和错误重试仍可达。
- [x] 完成会员、扫码确认、兑换和购买记录页面及返回/生命周期。
- [x] 运行测试、Lint、固定签名 Release，并检查圆屏/方屏真实控件渲染与按钮可达性。控件和业务回归共 629 项通过；二维码通过实际渲染像素解码验证。无已连接设备，未进行真机或线上支付验收。

## 代码规范

继续使用项目现有 Kotlin/Java/Compose 模式，不添加 UI 或支付依赖。手动修改使用 apply_patch；Kotlin 新文件不超过 500 行，Java 遵守现有尺寸上限。无关代码、用户现有修改和构建产物保持原样。UI 可组合函数只渲染，网络与轮询由独立控制器管理。

```kotlin
@Composable
internal fun CheckinHistoryScreen(state: ComposeCheckinUiState, onOpen: (CheckinHistory.Entry) -> Unit) {
    // Render the server snapshot and delegate actions; do not request during composition.
}
```

## 验证命令与边界

- Android: `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease --console=plain`
- 服务端: 在 CheckinCenter 项目使用已配置 Python 运行 `-m pytest tests/test_lite_billing_api.py tests/test_lite_api.py tests/test_billing.py tests/test_billing_web.py`。
- 控件测试: 240x320 方屏、227x227 圆屏、小尺寸及浅色主题；验证各页面布局、末项滚动点击、记录详情、真实套餐选择和状态。
- 必须保持原证书、版本 `2.18 / 221` 和本地签到禁用；不进行真实账号登录、兑换或付款测试，不启动模拟器。
- 本次先实现和构建。用户此前暂停测试管理员上传，未要求恢复；不部署、不发布 Release、不关机。生产接口仍需上线后联调，构建和模拟数据控件测试不等于真机/真实到账验收。
