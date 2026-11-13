package com.ronan.heyboxlite;

import android.app.Activity;

/** Captures the original flow attempt instead of routing results to the current page. */
final class NativeCheckinCaptcha {
    private final Activity activity;
    private final CheckinMobileLoginFlow flow;
    private final boolean roundLayout;
    private ComposeCheckinCaptchaCallbacks callbacks;

    NativeCheckinCaptcha(Activity activity, CheckinMobileLoginFlow flow, boolean roundLayout) {
        this.activity = activity;
        this.flow = flow;
        this.roundLayout = roundLayout;
    }

    void open(String uri, int attempt) {
        callbacks = new ComposeCheckinCaptchaCallbacks() {
            @Override public void onCaptchaResult(String ticket, String randstr) {
                flow.onCaptchaResult(attempt, ticket, randstr);
            }
            @Override public void onCaptchaCancelled(String message) {
                flow.onCaptchaCancelled(attempt, message);
            }
        };
        ComposeCheckinCaptchaController.launch(activity, uri, roundLayout, callbacks);
    }

    void close() {
        if (callbacks != null) ComposeCheckinCaptchaController.clear(callbacks);
        callbacks = null;
    }
}
