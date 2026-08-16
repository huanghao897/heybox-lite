package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class CheckinPaymentPage {
    interface Host {
        void closeSponsorship();
        void showMessage(String message);
    }

    private static final long POLL_DELAY_MS = 15_000L;

    private final Activity activity;
    private final SessionStore session;
    private final CheckinCenterCoordinator coordinator;
    private final ThemeTokens tokens;
    private final Host host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float scale;
    private final boolean roundLayout;
    private final SettingsUi settingsUi;
    private final ScrollView root;
    private final LinearLayout page;
    private CheckinBilling.Membership membership;
    private CheckinBilling.Order order;
    private TextView orderState;
    private ImageView qrImage;
    private LoadingSpinnerView qrSpinner;
    private EditText sponsorshipAmount;
    private EditText paymentReference;
    private Button createButton;
    private Button claimButton;
    private boolean requestInFlight;
    private boolean qrRequestInFlight;
    private int qrRequestSerial;
    private boolean visible;
    private boolean closed;
    private boolean firstRender = true;
    private Bitmap qrBitmap;
    private String qrOrderId = "";
    private final Runnable pollTask = this::pollOrder;
    private final Runnable qrRetryTask = this::loadQr;

    CheckinPaymentPage(Activity activity, SessionStore session,
                       CheckinCenterCoordinator coordinator, ThemeTokens tokens,
                       boolean roundLayout, CheckinBilling.Membership membership,
                       Host host) {
        this.activity = activity;
        this.session = session;
        this.coordinator = coordinator;
        this.tokens = tokens;
        this.roundLayout = roundLayout;
        this.membership = membership == null ? CheckinBilling.Membership.freeDefault() : membership;
        this.host = host;
        this.scale = session.uiScale() / 100.0f;
        int width = activity.getResources().getDisplayMetrics().widthPixels;
        int headerInset = roundLayout ? RoundLayoutMetrics.headerInnerInset(width) : 0;
        this.settingsUi = new SettingsUi(activity, session, tokens, roundLayout,
                headerInset, handler, this::navigateBack);
        this.root = new ScrollView(activity);
        this.root.setFillViewport(true);
        this.page = column(tokens.background);
        this.root.addView(page, new ScrollView.LayoutParams(-1, -2));
        render();
    }

    View view() {
        return root;
    }

    private void navigateBack() {
        if (!handleBack()) host.closeSponsorship();
    }

    void updateMembership(CheckinBilling.Membership value) {
        if (value != null) this.membership = value;
        if (order == null) render();
    }

    boolean handleBack() {
        if (closed) return false;
        stopPolling();
        host.closeSponsorship();
        return true;
    }

    void onResume() {
        visible = true;
        if (order != null && order.pending() && !hasQrBitmap()) loadQr();
        if (order != null && order.pending() && (order.review == null || order.review.pending())) {
            schedulePoll(POLL_DELAY_MS);
        }
    }

    void onPause() {
        visible = false;
        stopPolling();
        stopQrRetry();
    }

    void close() {
        closed = true;
        visible = false;
        stopPolling();
        stopQrRetry();
        if (qrImage != null) qrImage.setImageDrawable(null);
        clearQrBitmap();
        handler.removeCallbacksAndMessages(null);
    }

    private void render() {
        cancelQrRequest();
        Motions.resetTree(page);
        page.removeAllViews();
        orderState = null;
        qrImage = null;
        qrSpinner = null;
        sponsorshipAmount = null;
        paymentReference = null;
        createButton = null;
        claimButton = null;

        page.setPadding(pageInset(), dp(8), pageInset(), dp(18));
        page.addView(settingsUi.topCard("赞助"));

        LinearLayout summary = card();
        summary.addView(statusHeader(R.drawable.il_qr, "自愿赞助",
                "签到功能永久免费", tokens.text));
        addTop(summary, infoRow("用途", "服务器运行与维护"), 11);
        TextView notice = body("赞助不会解锁功能，也不会影响签到计划。", tokens.muted);
        notice.setLineSpacing(0f, 1.12f);
        addTop(summary, notice, 8);
        if (order == null && membership.plan.variableAmount) {
            sponsorshipAmount = input("赞助金额（元）", 10);
            sponsorshipAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                    | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            sponsorshipAmount.setText(SponsorshipAmount.formatYuan(
                    membership.plan.amountCents));
            sponsorshipAmount.setSelection(sponsorshipAmount.length());
            addTop(summary, sponsorshipAmount, 9);
        } else if (order == null) {
            addTop(summary, infoRow("金额", "¥" + SponsorshipAmount.formatYuan(
                    membership.plan.amountCents)), 9);
        }
        page.addView(summary);

        if (order == null) {
            addTop(page, body(membership.checkoutAvailable
                    ? "完全自愿，不赞助也可以使用全部签到功能"
                    : "当前没有可用赞助码", tokens.muted), 9);
            createButton = primaryButton("显示赞助码");
            createButton.setEnabled(membership.checkoutAvailable && !requestInFlight);
            createButton.setOnClickListener(view -> {
                UiComponents.press(view);
                createOrder();
            });
            addTop(page, createButton, 10);
            animateRender(summary);
            return;
        }

        LinearLayout payment = card();
        TextView amount = label(amountValue(order), roundLayout ? 25f : 28f, tokens.text);
        amount.setGravity(Gravity.CENTER);
        amount.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        payment.addView(amount, new LinearLayout.LayoutParams(-1, -2));
        orderState = body(orderStateLabel(order), stateColor(order));
        orderState.setGravity(Gravity.CENTER);
        addTop(payment, orderState, 4);

        FrameLayout qrFrame = new FrameLayout(activity);
        qrFrame.setBackgroundColor(Color.WHITE);
        qrImage = new ImageView(activity);
        qrImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        qrImage.setPadding(dp(7), dp(7), dp(7), dp(7));
        qrFrame.addView(qrImage, new FrameLayout.LayoutParams(-1, -1));
        qrSpinner = new LoadingSpinnerView(activity);
        qrSpinner.setColor(Color.rgb(48, 50, 54));
        int spinnerSize = dp(24);
        qrFrame.addView(qrSpinner,
                new FrameLayout.LayoutParams(spinnerSize, spinnerSize, Gravity.CENTER));
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(qrSize(), qrSize());
        qrParams.gravity = Gravity.CENTER_HORIZONTAL;
        qrParams.topMargin = dp(9);
        payment.addView(qrFrame, qrParams);
        if (hasQrBitmap()) {
            qrImage.setImageBitmap(qrBitmap);
            qrSpinner.setVisibility(View.GONE);
        } else if (Motions.off()) {
            qrSpinner.setVisibility(View.GONE);
        }
        addTop(payment, body("使用" + providerLabel(order) + "扫描赞助码", tokens.muted), 7);

        if (order.manualReview) addManualClaim(payment);
        page.addView(payment);

        Button regenerate = quietButton("paid".equals(order.status)
                ? "完成" : "重新生成赞助码");
        regenerate.setEnabled(!requestInFlight && !order.pending());
        regenerate.setOnClickListener(view -> {
            UiComponents.press(view);
            if ("paid".equals(order.status)) {
                host.closeSponsorship();
                return;
            }
            clearQrBitmap();
            order = null;
            render();
        });
        addTop(page, regenerate, 9);
        if (order.pending() && order.qrReady) {
            if (hasQrBitmap()) {
                if (!order.manualReview) schedulePoll(POLL_DELAY_MS);
            } else {
                loadQr();
            }
        }
        animateRender(payment);
    }

    private void addManualClaim(LinearLayout parent) {
        TextView hint = body(order.review == null
                ? "赞助后填写账单中的支付订单号，管理员核对到账后记录赞助。"
                : reviewLabel(order.review), tokens.muted);
        hint.setLineSpacing(0f, 1.12f);
        addTop(parent, hint, 9);
        if (order.review != null && order.review.pending()) return;
        if (order.review != null && "approved".equals(order.review.status)) return;
        if (!order.pending() && !"expired".equals(order.status)) return;
        paymentReference = input("支付订单号", 80);
        addTop(parent, paymentReference, 7);
        claimButton = ghostButton("提交赞助记录");
        claimButton.setEnabled(!requestInFlight);
        claimButton.setOnClickListener(view -> {
            UiComponents.press(view);
            submitClaim();
        });
        addTop(parent, claimButton, 7);
    }

    private void createOrder() {
        if (requestInFlight || closed) return;
        int amountCents = membership.plan.variableAmount
                ? SponsorshipAmount.parseCents(sponsorshipAmount == null
                        ? "" : sponsorshipAmount.getText().toString(),
                        membership.plan.minimumAmountCents,
                        membership.plan.maximumAmountCents)
                : membership.plan.amountCents;
        if (!SponsorshipAmount.validCents(amountCents)
                || amountCents < membership.plan.minimumAmountCents
                || amountCents > membership.plan.maximumAmountCents) {
            host.showMessage("请输入有效的赞助金额");
            return;
        }
        requestInFlight = true;
        if (createButton != null) {
            createButton.setEnabled(false);
            createButton.setText("正在生成");
        }
        coordinator.createBillingOrder(amountCents,
                new CheckinCenterClient.Callback<CheckinBilling.Order>() {
            @Override
            public void onSuccess(CheckinBilling.Order value) {
                if (closed) return;
                requestInFlight = false;
                setOrder(value);
                render();
            }

            @Override
            public void onError(CheckinCenterClient.ApiError error) {
                if (closed) return;
                requestInFlight = false;
                host.showMessage(error.getMessage());
                render();
            }
        });
    }

    private void loadQr() {
        if (qrRequestInFlight || order == null || !order.pending()
                || !order.qrReady || qrImage == null) return;
        stopQrRetry();
        final String orderId = order.id;
        final ImageView target = qrImage;
        final int requestSerial = ++qrRequestSerial;
        qrRequestInFlight = true;
        if (qrSpinner != null && !Motions.off()) qrSpinner.setVisibility(View.VISIBLE);
        coordinator.loadBillingQr(orderId, new CheckinCenterClient.Callback<byte[]>() {
            @Override
            public void onSuccess(byte[] value) {
                if (requestSerial != qrRequestSerial) return;
                qrRequestInFlight = false;
                if (closed || target != qrImage || order == null
                        || !orderId.equals(order.id) || !order.pending()) return;
                Bitmap bitmap = decodeQr(value);
                if (bitmap == null) {
                    if (qrSpinner != null) qrSpinner.setVisibility(View.GONE);
                    showOrderState("赞助码无法显示", tokens.text);
                    scheduleQrRetry();
                    return;
                }
                replaceQrBitmap(orderId, bitmap);
                target.setImageBitmap(qrBitmap);
                if (qrSpinner != null) qrSpinner.setVisibility(View.GONE);
                Motions.dialogIn(target);
                if (order.manualReview) return;
                schedulePoll(POLL_DELAY_MS);
            }

            @Override
            public void onError(CheckinCenterClient.ApiError error) {
                if (requestSerial != qrRequestSerial) return;
                qrRequestInFlight = false;
                if (closed) return;
                if (qrSpinner != null) qrSpinner.setVisibility(View.GONE);
                showOrderState(error.getMessage(), tokens.text);
                if (!error.authorizationInvalid()) scheduleQrRetry();
            }
        });
    }

    private void pollOrder() {
        if (closed || !visible || order == null || !order.pending()) return;
        coordinator.getBillingOrder(order.id,
                new CheckinCenterClient.Callback<CheckinBilling.Order>() {
                    @Override
                    public void onSuccess(CheckinBilling.Order value) {
                        if (closed) return;
                        setOrder(value);
                        if ("paid".equals(value.status)) {
                            render();
                            return;
                        }
                        render();
                        if (order.pending() && (order.review == null || order.review.pending())) {
                            schedulePoll(POLL_DELAY_MS);
                        }
                    }

                    @Override
                    public void onError(CheckinCenterClient.ApiError error) {
                        if (closed) return;
                        showOrderState(error.getMessage(), tokens.text);
                        if (error.authorizationInvalid()) return;
                        schedulePoll(POLL_DELAY_MS);
                    }
                });
    }

    private void submitClaim() {
        if (requestInFlight || order == null || paymentReference == null) return;
        String reference = paymentReference.getText().toString().trim();
        if (!CheckinBilling.validPaymentReference(reference)) {
            host.showMessage("请输入有效的支付订单号");
            return;
        }
        requestInFlight = true;
        if (claimButton != null) claimButton.setEnabled(false);
        coordinator.submitBillingClaim(order.id, reference,
                new CheckinCenterClient.Callback<CheckinBilling.Review>() {
                    @Override
                    public void onSuccess(CheckinBilling.Review value) {
                        if (closed) return;
                        requestInFlight = false;
                        order = new CheckinBilling.Order(order.id, order.provider,
                                order.amountCents, order.payableAmountCents, order.currency,
                                order.status, order.qrReady, order.manualReview,
                                order.expiresAt, value);
                        render();
                        schedulePoll(POLL_DELAY_MS);
                    }

                    @Override
                    public void onError(CheckinCenterClient.ApiError error) {
                        if (closed) return;
                        requestInFlight = false;
                        host.showMessage(error.getMessage());
                        render();
                    }
                });
    }

    private void showOrderState(String value, int color) {
        if (orderState != null) {
            orderState.setText(value);
            orderState.setTextColor(color);
            Motions.selected(orderState);
        }
    }

    private void schedulePoll(long delay) {
        stopPolling();
        if (visible && order != null && order.pending()) handler.postDelayed(pollTask, delay);
    }

    private void scheduleQrRetry() {
        stopQrRetry();
        if (!closed && visible && order != null && order.pending()
                && order.qrReady && qrImage != null) {
            handler.postDelayed(qrRetryTask, POLL_DELAY_MS);
        }
    }

    private void stopPolling() {
        handler.removeCallbacks(pollTask);
    }

    private void stopQrRetry() {
        handler.removeCallbacks(qrRetryTask);
    }

    private void cancelQrRequest() {
        qrRequestSerial++;
        qrRequestInFlight = false;
        stopQrRetry();
    }

    private void setOrder(CheckinBilling.Order value) {
        if (value == null) return;
        if (order == null || !order.id.equals(value.id)) clearQrBitmap();
        order = value;
    }

    private boolean hasQrBitmap() {
        return order != null && order.id.equals(qrOrderId)
                && qrBitmap != null && !qrBitmap.isRecycled();
    }

    private void replaceQrBitmap(String orderId, Bitmap value) {
        clearQrBitmap();
        qrOrderId = orderId;
        qrBitmap = value;
    }

    private void clearQrBitmap() {
        cancelQrRequest();
        if (qrBitmap != null && !qrBitmap.isRecycled()) qrBitmap.recycle();
        qrBitmap = null;
        qrOrderId = "";
    }

    private void animateRender(View content) {
        boolean animate = !firstRender;
        firstRender = false;
        if (animate) Motions.enter(content, dp(roundLayout ? 5 : 8));
    }

    private Bitmap decodeQr(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int largestSide = Math.max(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        while (largestSide / sample > 1024) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private String amountValue(CheckinBilling.Order value) {
        return "¥" + SponsorshipAmount.formatYuan(value.payableAmountCents);
    }

    private String orderStateLabel(CheckinBilling.Order value) {
        if ("paid".equals(value.status)) return "赞助已确认，感谢支持";
        if ("expired".equals(value.status)) return "赞助码已过期，请重新生成";
        if ("failed".equals(value.status)) return "赞助记录创建失败";
        if (value.review != null && value.review.pending()) return "赞助记录待审核";
        return "等待赞助";
    }

    private String providerLabel(CheckinBilling.Order value) {
        return value.provider.contains("alipay") ? "支付宝" : "微信";
    }

    private int stateColor(CheckinBilling.Order value) {
        return "paid".equals(value.status) ? tokens.text : tokens.muted;
    }

    private String reviewLabel(CheckinBilling.Review value) {
        if ("approved".equals(value.status)) return "审核通过，感谢支持";
        if ("rejected".equals(value.status)) return value.reason.isEmpty()
                ? "审核未通过，请检查赞助记录" : "审核未通过：" + value.reason;
        return "赞助记录待审核";
    }

    private LinearLayout infoRow(String name, String value) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView left = body(name, tokens.muted);
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 0.35f));
        TextView right = body(value, tokens.text);
        right.setGravity(Gravity.END);
        right.setMaxLines(2);
        row.addView(right, new LinearLayout.LayoutParams(0, -2, 0.65f));
        return row;
    }

    private LinearLayout statusHeader(int iconRes, String title, String subtitle,
                                      int titleColor) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(activity);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(9), dp(9), dp(9), dp(9));
        Drawable drawable = Compat.tintedDrawable(activity, iconRes, tokens.text);
        if (drawable != null) icon.setImageDrawable(drawable);
        Compat.setBackground(icon, UiComponents.monoChip(activity, tokens, scale));
        int iconSize = dp(roundLayout ? 38 : 42);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.rightMargin = dp(10);
        row.addView(icon, iconParams);

        LinearLayout copy = column(Color.TRANSPARENT);
        copy.setPadding(0, 0, 0, 0);
        TextView titleView = label(title, roundLayout ? 15f : 16f, titleColor);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleView.setMaxLines(2);
        copy.addView(titleView);
        TextView subtitleView = body(subtitle, tokens.muted);
        subtitleView.setPadding(0, dp(2), 0, 0);
        copy.addView(subtitleView);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    private LinearLayout card() {
        LinearLayout value = column(Color.TRANSPARENT);
        int horizontal = dp(roundLayout ? 12 : 14);
        int vertical = dp(roundLayout ? 11 : 13);
        value.setPadding(horizontal, vertical, horizontal, vertical);
        Compat.setBackground(value, UiComponents.card(activity, tokens, scale));
        return value;
    }

    private LinearLayout column(int color) {
        LinearLayout value = new LinearLayout(activity);
        value.setOrientation(LinearLayout.VERTICAL);
        value.setBackgroundColor(color);
        return value;
    }

    private TextView heading(String value) {
        TextView result = label(value, roundLayout ? 17f : 18f, tokens.text);
        result.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return result;
    }

    private TextView sectionTitle(String value) {
        TextView result = label(value, 15f, tokens.text);
        result.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return result;
    }

    private TextView body(String value, int color) {
        return label(value, 12f, color);
    }

    private TextView label(String value, float size, int color) {
        TextView result = UiComponents.label(activity, value, size, color,
                session.textScale() / 100.0f);
        result.setIncludeFontPadding(false);
        return result;
    }

    private Button primaryButton(String value) {
        Button result = baseButton(value);
        result.setTextColor(tokens.onPrimary);
        Compat.setBackground(result, UiComponents.primaryButton(activity, tokens, scale));
        return result;
    }

    private Button ghostButton(String value) {
        Button result = baseButton(value);
        result.setTextColor(tokens.text);
        Compat.setBackground(result, UiComponents.ghostButton(activity, tokens, scale));
        return result;
    }

    private Button quietButton(String value) {
        Button result = baseButton(value);
        result.setTextColor(tokens.muted);
        Compat.setBackground(result, UiComponents.round(
                activity, Color.TRANSPARENT, 10, scale));
        result.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(36)));
        return result;
    }

    private Button baseButton(String value) {
        Button result = UiComponents.button(activity);
        result.setText(value);
        result.setTextSize(12f * session.textScale() / 100.0f);
        result.setAllCaps(false);
        result.setMinHeight(0);
        result.setMinimumHeight(0);
        result.setPadding(dp(12), 0, dp(12), 0);
        result.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(roundLayout ? 38 : 42)));
        return result;
    }

    private EditText input(String hint, int maxLength) {
        EditText result = new EditText(activity);
        result.setSingleLine(true);
        result.setHint(hint);
        result.setHintTextColor(tokens.muted);
        result.setTextColor(tokens.text);
        result.setTextSize(12f * session.textScale() / 100.0f);
        result.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        result.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(maxLength)});
        Compat.setBackground(result, UiComponents.round(
                activity, tokens.panelElevated, 9, scale));
        result.setPadding(dp(10), 0, dp(10), 0);
        result.setMinimumHeight(dp(38));
        return result;
    }

    private int qrSize() {
        int width = activity.getResources().getDisplayMetrics().widthPixels;
        int height = activity.getResources().getDisplayMetrics().heightPixels;
        int limit = Math.min(width, height);
        return Math.min(dp(210), Math.max(dp(140),
                Math.round(limit * (roundLayout ? 0.48f : 0.54f))));
    }

    private int pageInset() {
        if (!roundLayout) return dp(10);
        int width = activity.getResources().getDisplayMetrics().widthPixels;
        return RoundLayoutMetrics.componentInset(
                width, RoundLayoutMetrics.PAGE_HORIZONTAL_RATIO, dp(10));
    }

    private int dp(int value) {
        return UiComponents.dp(activity, value, scale);
    }

    private void addTop(LinearLayout parent, View child, int marginDp) {
        LinearLayout.LayoutParams params = child.getLayoutParams() instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) child.getLayoutParams()
                : new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(marginDp);
        parent.addView(child, params);
    }
}
