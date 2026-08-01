package com.ronan.heyboxlite;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

final class QrLoginPage {
    interface Host {
        void prepareLoginChrome();

        void showPage(View page);

        void onLoginComplete();

        void showFeed();

        int pageHorizontalPadding();

        int pageTopPadding();
    }

    private final Activity activity;
    private final SessionStore session;
    private final QrLoginController controller;
    private final ThemeTokens tokens;
    private final boolean roundLayout;
    private final Host host;

    private ImageView qrImage;
    private TextView statusView;

    QrLoginPage(Activity activity, SessionStore session, ApiClient api, Handler handler,
                ThemeTokens tokens, boolean roundLayout, Host host) {
        this.activity = activity;
        this.session = session;
        this.controller = new QrLoginController(api, handler);
        this.tokens = tokens;
        this.roundLayout = roundLayout;
        this.host = host;
    }

    void show() {
        stop();
        this.host.prepareLoginChrome();

        LinearLayout page = new LinearLayout(this.activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setBackgroundColor(this.tokens.background);
        int horizontal = this.roundLayout ? this.host.pageHorizontalPadding() : dp(12);
        int top = this.roundLayout ? this.host.pageTopPadding() : dp(12);
        page.setPadding(horizontal, top, horizontal, dp(12));

        TextView heading = text("heybox Lite", 20.0f, this.tokens.text);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        page.addView(heading, new LinearLayout.LayoutParams(-1, dp(34)));

        this.statusView = text("正在获取二维码", 12.0f, this.tokens.muted);
        this.statusView.setGravity(Gravity.CENTER);
        page.addView(this.statusView, new LinearLayout.LayoutParams(-1, dp(28)));

        int qrSize = Math.round(
                this.activity.getResources().getDisplayMetrics().widthPixels * 0.54f);
        this.qrImage = new ImageView(this.activity);
        this.qrImage.setPadding(dp(7), dp(7), dp(7), dp(7));
        this.qrImage.setBackgroundColor(0xffffffff);
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(qrSize, qrSize);
        qrParams.topMargin = dp(4);
        page.addView(this.qrImage, qrParams);

        TextView hint = text("请使用小黑盒 App 扫码", 12.0f, this.tokens.accent);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, dp(34));
        hintParams.topMargin = dp(4);
        page.addView(hint, hintParams);

        Button retry = commandButton("重新获取", R.drawable.ic_refresh);
        retry.setOnClickListener(view -> requestQr());
        page.addView(retry, new LinearLayout.LayoutParams(dp(150), dp(38)));

        Button guest = commandButton("游客浏览", R.drawable.ic_home);
        guest.setOnClickListener(view -> this.host.showFeed());
        LinearLayout.LayoutParams guestParams = new LinearLayout.LayoutParams(dp(150), dp(38));
        guestParams.topMargin = dp(7);
        page.addView(guest, guestParams);

        this.host.showPage(page);
        requestQr();
    }

    void stop() {
        this.controller.stop();
    }

    void pause() {
        this.controller.pause();
    }

    void resume() {
        this.controller.resume();
    }

    private void requestQr() {
        this.controller.stop();
        this.qrImage.setImageDrawable(null);
        setStatus("正在获取二维码", this.tokens.muted);
        this.controller.start(new QrLoginController.Listener() {
            @Override
            public void onQrReady(String url) {
                try {
                    int size = Math.round(activity.getResources()
                            .getDisplayMetrics().widthPixels * 0.5f);
                    qrImage.setImageBitmap(QrCode.create(url, size));
                    setStatus("等待扫码", tokens.accent);
                } catch (com.google.zxing.WriterException | RuntimeException error) {
                    setStatus("二维码生成失败", tokens.primary);
                    controller.stop();
                }
            }

            @Override
            public void onStatus(String status) {
                int color = status.startsWith("网络") ? tokens.muted
                        : "等待扫码".equals(status) ? tokens.accent : tokens.primary;
                setStatus(status, color);
            }

            @Override
            public void onLogin(JSONObject result) {
                session.saveLogin(result);
                host.onLoginComplete();
            }

            @Override
            public void onError(String message) {
                setStatus("获取失败：" + message, tokens.primary);
            }
        });
    }

    private void setStatus(String value, int color) {
        this.statusView.setText(value);
        this.statusView.setTextColor(color);
    }

    @SuppressLint("ClickableViewAccessibility")
    private Button commandButton(String value, int iconResource) {
        Button button = new Button(this.activity);
        button.setText(value);
        button.setTextSize(sp(12.0f));
        button.setTextColor(this.tokens.accent);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(40));
        button.setMinimumHeight(dp(40));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setStateListAnimator(null);
        }
        Compat.setBackground(button, null);
        button.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                UiComponents.press(view);
            }
            return false;
        });
        Drawable icon = Compat.tintedDrawable(
                this.activity, iconResource, this.tokens.accent);
        if (icon != null) {
            icon.setBounds(0, 0, dp(15), dp(15));
            button.setCompoundDrawables(icon, null, null, null);
            button.setCompoundDrawablePadding(dp(5));
        }
        return button;
    }

    private TextView text(String value, float size, int color) {
        TextView view = UiComponents.label(
                this.activity, value, size, color, this.session.textScale() / 100.0f);
        view.setTypeface(Typeface.DEFAULT);
        return view;
    }

    private int dp(int value) {
        return UiComponents.dp(this.activity, value, this.session.uiScale() / 100.0f);
    }

    private float sp(float value) {
        return value * this.session.textScale() / 100.0f;
    }
}
