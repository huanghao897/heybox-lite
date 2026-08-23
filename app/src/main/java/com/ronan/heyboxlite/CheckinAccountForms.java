package com.ronan.heyboxlite;

import android.app.Activity;
import android.text.InputFilter;
import android.text.InputType;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class CheckinAccountForms {
    interface Actions {
        void selectMobileMode(CheckinMobileLoginFlow.Mode mode);
        void sendSms();
        void submitSms();
        void submitPassword();
        void selectServiceMode(CheckinServiceAccountFlow.Mode mode);
        void sendRegistrationEmail();
        void submitServiceAccount();
    }

    private final Activity activity;
    private final ThemeTokens tokens;
    private final CheckinCenterUi ui;
    private final SettingsUi settingsUi;
    private final boolean roundLayout;
    private final float scale;
    private final int fieldHeight;
    private EditText serviceUsername;
    private EditText servicePassword;
    private EditText servicePasswordConfirmation;
    private EditText serviceEmail;
    private EditText serviceEmailCode;
    private Button serviceSubmit;
    private Button emailSend;
    private TextView serviceStatus;
    private TextView pairingCountdown;
    private EditText mobilePhone;
    private EditText mobileCode;
    private EditText mobilePassword;
    private Button smsSend;
    private Button smsSubmit;
    private Button passwordSubmit;
    private TextView mobileStatus;

    CheckinAccountForms(Activity activity, SessionStore session, ThemeTokens tokens,
                        CheckinCenterUi ui,
                        SettingsUi settingsUi, boolean roundLayout) {
        this.activity = activity;
        this.tokens = tokens;
        this.ui = ui;
        this.settingsUi = settingsUi;
        this.roundLayout = roundLayout;
        this.scale = session.uiScale() / 100.0f;
        this.fieldHeight = dp(roundLayout ? 38 : 42);
    }

    View mobile(CheckinMobileLoginFlow flow, Actions actions) {
        clearMobile();
        ScrollView scroll = page("手机号登录");
        LinearLayout page = (LinearLayout) scroll.getChildAt(0);
        LinearLayout card = ui.card();
        card.addView(ui.statusHeader(R.drawable.il_person, "登录小黑盒",
                "仅用于服务器自动签到", tokens.text, false));
        addModes(card,
                segment(roundLayout ? "验证码" : "短信验证码",
                        flow.mode() == CheckinMobileLoginFlow.Mode.SMS,
                        () -> actions.selectMobileMode(CheckinMobileLoginFlow.Mode.SMS)),
                segment("密码", flow.mode() == CheckinMobileLoginFlow.Mode.PASSWORD,
                        () -> actions.selectMobileMode(CheckinMobileLoginFlow.Mode.PASSWORD)));

        mobilePhone = ui.input("+86 手机号", InputType.TYPE_CLASS_PHONE);
        mobilePhone.setText(flow.rememberedPhone());
        if (flow.mode() == CheckinMobileLoginFlow.Mode.SMS) {
            LinearLayout phoneRow = new LinearLayout(activity);
            phoneRow.setGravity(Gravity.CENTER_VERTICAL);
            phoneRow.addView(mobilePhone, new LinearLayout.LayoutParams(0, fieldHeight, 1f));
            smsSend = ui.compactActionButton("发送验证码");
            smsSend.setOnClickListener(view -> runPressed(view, actions::sendSms));
            LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(
                    dp(roundLayout ? 104 : 112), fieldHeight);
            sendParams.leftMargin = dp(6);
            phoneRow.addView(smsSend, sendParams);
            ui.addTop(card, phoneRow, 11);

            mobileCode = ui.input("短信验证码",
                    InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            ui.addTop(card, mobileCode, 7);
            smsSubmit = ui.primaryButton("登录并连接");
            smsSubmit.setOnClickListener(view -> runPressed(view, actions::submitSms));
            ui.addTop(card, smsSubmit, 9);
            mobileStatus = ui.body("验证码由小黑盒发送", tokens.muted);
        } else {
            ui.addTop(card, mobilePhone, 11);
            mobilePassword = ui.input("小黑盒登录密码",
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            ui.addTop(card, mobilePassword, 7);
            passwordSubmit = ui.primaryButton("登录并连接");
            passwordSubmit.setOnClickListener(view -> runPressed(view, actions::submitPassword));
            ui.addTop(card, passwordSubmit, 9);
            mobileStatus = ui.body("密码仅用于本次登录", tokens.muted);
        }
        mobileStatus.setLineSpacing(0f, 1.14f);
        ui.addTop(card, mobileStatus, 8);
        page.addView(card);
        return scroll;
    }

    View pairing(CheckinCenterClient.PairingStart pairing,
                 CheckinServiceAccountFlow flow, Actions actions) {
        clearPairing();
        ScrollView scroll = page("连接签到服务");
        LinearLayout page = (LinearLayout) scroll.getChildAt(0);
        LinearLayout card = ui.card();
        card.addView(ui.statusHeader(R.drawable.il_qr,
                flow.mode() == CheckinServiceAccountFlow.Mode.LOGIN
                        ? "登录签到服务" : "注册签到服务",
                "配对码  " + pairing.userCode, tokens.text, false));
        pairingCountdown = ui.body("", tokens.muted);
        ui.addTop(card, pairingCountdown, 8);

        if (pairing.registrationOpen) {
            addModes(card,
                    segment(roundLayout ? "登录" : "已有账号",
                            flow.mode() == CheckinServiceAccountFlow.Mode.LOGIN,
                            () -> actions.selectServiceMode(
                                    CheckinServiceAccountFlow.Mode.LOGIN)),
                    segment(roundLayout ? "注册" : "注册账号",
                            flow.mode() == CheckinServiceAccountFlow.Mode.REGISTER,
                            () -> actions.selectServiceMode(
                                    CheckinServiceAccountFlow.Mode.REGISTER)));
        }

        serviceUsername = ui.input("签到服务账号", InputType.TYPE_CLASS_TEXT);
        serviceUsername.setText(flow.rememberedUsername());
        ui.addTop(card, serviceUsername, 10);
        servicePassword = ui.input("密码",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        ui.addTop(card, servicePassword, 7);
        if (flow.mode() == CheckinServiceAccountFlow.Mode.REGISTER) {
            servicePasswordConfirmation = ui.input("再次输入密码",
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            ui.addTop(card, servicePasswordConfirmation, 7);
            TextView passwordRule = ui.body(
                    "至少 12 位，并包含大小写字母、数字、符号中的三类", tokens.muted);
            passwordRule.setLineSpacing(0f, 1.12f);
            ui.addTop(card, passwordRule, 6);
            if (pairing.registrationEmailRequired) addEmailFields(card, flow, actions);
        }

        serviceSubmit = ui.primaryButton(
                flow.mode() == CheckinServiceAccountFlow.Mode.LOGIN
                        ? "登录并连接" : "注册并连接");
        serviceSubmit.setOnClickListener(view ->
                runPressed(view, actions::submitServiceAccount));
        ui.addTop(card, serviceSubmit, 10);
        serviceStatus = ui.body("账号密码不会保存在 Lite 中", tokens.muted);
        serviceStatus.setLineSpacing(0f, 1.14f);
        ui.addTop(card, serviceStatus, 8);
        page.addView(card);
        return scroll;
    }

    String mobilePhone() { return text(mobilePhone); }
    String mobileCode() { return text(mobileCode); }
    String mobilePassword() { return text(mobilePassword); }
    String serviceUsername() { return text(serviceUsername); }
    String servicePassword() { return text(servicePassword); }
    String servicePasswordConfirmation() { return text(servicePasswordConfirmation); }
    String serviceEmail() { return text(serviceEmail); }
    String serviceEmailCode() { return text(serviceEmailCode); }

    void setMobileControls(boolean enabled, CheckinMobileLoginFlow.Mode mode,
                           boolean hasSession, long retryAtElapsed) {
        if (mobilePhone == null) return;
        if (mode == CheckinMobileLoginFlow.Mode.PASSWORD) {
            mobilePhone.setEnabled(enabled);
            if (mobilePassword != null) mobilePassword.setEnabled(enabled);
            if (passwordSubmit != null) passwordSubmit.setEnabled(enabled);
            return;
        }
        if (mobileCode == null || smsSend == null || smsSubmit == null) return;
        mobilePhone.setEnabled(enabled && !hasSession);
        mobileCode.setEnabled(enabled && hasSession);
        smsSubmit.setEnabled(enabled && hasSession);
        smsSend.setEnabled(enabled && (!hasSession
                || SystemClock.elapsedRealtime() >= retryAtElapsed));
    }

    void setMobileStatus(String message, int color) {
        setStatus(mobileStatus, message, "请求失败，请稍后重试", color);
    }

    void updateSmsButton(String label, boolean enabled) {
        if (smsSend == null) return;
        smsSend.setText(label);
        smsSend.setEnabled(enabled);
    }

    void setPairingControls(boolean enabled, long emailRetryAtElapsed) {
        setEnabled(serviceUsername, enabled);
        setEnabled(servicePassword, enabled);
        setEnabled(servicePasswordConfirmation, enabled);
        setEnabled(serviceEmail, enabled);
        setEnabled(serviceEmailCode, enabled);
        setEnabled(serviceSubmit, enabled);
        if (emailSend != null) {
            emailSend.setEnabled(enabled
                    && SystemClock.elapsedRealtime() >= emailRetryAtElapsed);
        }
    }

    void setPairingStatus(String message, int color) {
        setStatus(serviceStatus, message, "连接失败，请稍后重试", color);
    }

    void updateEmailButton(String label, boolean enabled) {
        if (emailSend == null) return;
        emailSend.setText(label);
        emailSend.setEnabled(enabled);
    }

    void updatePairingCountdown(long remainingSeconds) {
        if (pairingCountdown == null) return;
        pairingCountdown.setText(String.format(java.util.Locale.getDefault(),
                "有效时间 %d:%02d", remainingSeconds / 60L,
                remainingSeconds % 60L));
    }

    void clearMobileCode() { clear(mobileCode); }
    void clearMobilePassword() { clear(mobilePassword); }
    void focusMobileCode() { focus(mobileCode); }
    void clearEmailCode() { clear(serviceEmailCode); }
    void focusEmailCode() { focus(serviceEmailCode); }
    void focusServicePassword() { focus(servicePassword); }

    void clearServicePasswords() {
        clear(servicePassword);
        clear(servicePasswordConfirmation);
    }

    void clearMobile() {
        clear(mobilePhone);
        clear(mobileCode);
        clear(mobilePassword);
        mobilePhone = null;
        mobileCode = null;
        mobilePassword = null;
        smsSend = null;
        smsSubmit = null;
        passwordSubmit = null;
        mobileStatus = null;
    }

    void clearPairing() {
        clearServicePasswords();
        serviceUsername = null;
        servicePassword = null;
        servicePasswordConfirmation = null;
        serviceEmail = null;
        serviceEmailCode = null;
        serviceSubmit = null;
        emailSend = null;
        serviceStatus = null;
        pairingCountdown = null;
    }

    private ScrollView page(String title) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout page = ui.column(tokens.background);
        ui.applyPageInsets(page, true);
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        page.addView(settingsUi.topCard(title));
        return scroll;
    }

    private void addEmailFields(LinearLayout card, CheckinServiceAccountFlow flow,
                                Actions actions) {
        TextView notice = ui.body("邮箱验证 · 验证码 10 分钟内有效", tokens.muted);
        notice.setLineSpacing(0f, 1.12f);
        ui.addTop(card, notice, 11);
        serviceEmail = ui.input("邮箱地址",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        serviceEmail.setText(flow.rememberedEmail());
        ui.addTop(card, serviceEmail, 7);
        emailSend = ui.compactActionButton("发送邮箱验证码");
        emailSend.setOnClickListener(view ->
                runPressed(view, actions::sendRegistrationEmail));
        ui.addTop(card, emailSend, 7);
        serviceEmailCode = ui.input("邮箱验证码", InputType.TYPE_CLASS_NUMBER);
        serviceEmailCode.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        ui.addTop(card, serviceEmailCode, 7);
    }

    private void addModes(LinearLayout card, Button first, Button second) {
        LinearLayout modes = new LinearLayout(activity);
        modes.setPadding(dp(3), dp(3), dp(3), dp(3));
        Compat.setBackground(modes, UiComponents.round(
                activity, tokens.panelElevated, 10, scale));
        LinearLayout.LayoutParams firstParams = new LinearLayout.LayoutParams(0, dp(34), 1f);
        firstParams.rightMargin = dp(2);
        modes.addView(first, firstParams);
        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(0, dp(34), 1f);
        secondParams.leftMargin = dp(2);
        modes.addView(second, secondParams);
        ui.addTop(card, modes, 10);
    }

    private Button segment(String label, boolean selected, Runnable action) {
        Button button = ui.segmentButton(label, selected);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private void runPressed(View view, Runnable action) {
        UiComponents.press(view);
        action.run();
    }

    private void setStatus(TextView view, String message, String fallback, int color) {
        if (view == null) return;
        view.setText(message == null ? fallback : message);
        view.setTextColor(color);
    }

    private static void setEnabled(View view, boolean enabled) {
        if (view != null) view.setEnabled(enabled);
    }

    private static void clear(EditText view) {
        if (view != null) view.setText("");
    }

    private static void focus(EditText view) {
        if (view != null) view.requestFocus();
    }

    private static String text(EditText view) {
        return view == null ? "" : view.getText().toString();
    }

    private int dp(int value) {
        return UiComponents.dp(activity, value, scale);
    }
}
