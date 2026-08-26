package com.ronan.heyboxlite;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

final class VideoPlayerControls extends FrameLayout {
    interface Listener {
        void onBack();
        void onTogglePlayback();
        void onSeekTo(int positionMs);
        void onRetry();
        void onExternalPlayer();
    }

    private final SessionStore session;
    private final ThemeTokens tokens;
    private final boolean roundLayout;
    private final float scale;
    private final Listener listener;
    private final LinearLayout topBar;
    private final LinearLayout bottomBar;
    private ImageButton play;
    private SeekBar seekBar;
    private TextView currentTime;
    private TextView totalTime;
    private TextView speedHint;
    private final ProgressBar loading;
    private final TextView errorText;
    private final LinearLayout errorActions;
    private boolean userSeeking;
    private boolean controlsVisible = true;
    private boolean playing;

    VideoPlayerControls(android.content.Context context, SessionStore session,
                        ThemeTokens tokens, boolean roundLayout, String title,
                        Listener listener) {
        super(context);
        this.session = session;
        this.tokens = tokens;
        this.roundLayout = roundLayout;
        this.scale = session.uiScale() / 100.0f;
        this.listener = listener;
        setClickable(false);

        this.topBar = topBar(title);
        this.bottomBar = bottomBar();
        addView(this.topBar, matchParams());
        LayoutParams bottomParams = new LayoutParams(-1, dp(54), Gravity.BOTTOM);
        bottomParams.leftMargin = chromeInset();
        bottomParams.rightMargin = chromeInset();
        bottomParams.bottomMargin = chromeInset();
        addView(this.bottomBar, bottomParams);

        this.loading = new ProgressBar(context);
        Compat.tint(this.loading, tokens.accent);
        LayoutParams loadingParams = new LayoutParams(dp(28), dp(28), Gravity.CENTER);
        addView(this.loading, loadingParams);

        this.speedHint = text("2×", 16.0f, Color.WHITE);
        this.speedHint.setGravity(Gravity.CENTER);
        Compat.setBackground(this.speedHint, UiComponents.round(context,
                Color.argb(210, 20, 20, 22), 18, this.scale));
        this.speedHint.setVisibility(GONE);
        LayoutParams speedParams = new LayoutParams(dp(64), dp(38), Gravity.CENTER);
        addView(this.speedHint, speedParams);

        this.errorText = text("", 12.0f, tokens.text);
        this.errorText.setGravity(Gravity.CENTER);
        this.errorText.setVisibility(GONE);
        LayoutParams errorParams = new LayoutParams(-1, -2, Gravity.CENTER);
        errorParams.leftMargin = chromeInset() + dp(12);
        errorParams.rightMargin = chromeInset() + dp(12);
        errorParams.bottomMargin = dp(26);
        addView(this.errorText, errorParams);

        this.errorActions = errorActions();
        this.errorActions.setVisibility(GONE);
        LayoutParams actionParams = new LayoutParams(-1, dp(40), Gravity.CENTER);
        actionParams.leftMargin = chromeInset();
        actionParams.rightMargin = chromeInset();
        actionParams.topMargin = dp(58);
        addView(this.errorActions, actionParams);
    }

    void setPrepared(int durationMs) {
        this.seekBar.setMax(Math.max(1, durationMs));
        this.totalTime.setText(formatTime(durationMs));
        this.errorText.setVisibility(GONE);
        this.errorActions.setVisibility(GONE);
        this.loading.setVisibility(GONE);
        this.playing = false;
        setPlayIcon(R.drawable.ic_play, "播放");
        setFastForwarding(false);
        setControlsVisible(true);
    }

    void setProgress(int positionMs, int durationMs) {
        if (!this.userSeeking) this.seekBar.setProgress(Math.max(0, positionMs));
        this.currentTime.setText(formatTime(positionMs));
        if (durationMs > 0) {
            this.seekBar.setMax(durationMs);
            this.totalTime.setText(formatTime(durationMs));
        }
    }

    void setPlaying(boolean playing) {
        this.playing = playing;
        setPlayIcon(playing ? R.drawable.ic_pause : R.drawable.ic_play,
                playing ? "暂停" : "播放");
        if (playing) scheduleHide();
        else setControlsVisible(true);
    }

    void setBuffering(boolean buffering) {
        this.loading.setVisibility(buffering ? VISIBLE : GONE);
    }

    void setCompleted() {
        this.playing = false;
        setPlayIcon(R.drawable.ic_replay, "重新播放");
        setControlsVisible(true);
    }

    void setFastForwarding(boolean fastForwarding) {
        this.speedHint.setVisibility(fastForwarding ? VISIBLE : GONE);
        if (fastForwarding) setControlsVisible(true);
    }

    void setError(String message, boolean externalAvailable) {
        this.loading.setVisibility(GONE);
        this.errorText.setText(message == null || message.isEmpty()
                ? "视频播放失败" : message);
        this.errorText.setVisibility(VISIBLE);
        this.errorActions.setVisibility(VISIBLE);
        this.errorActions.getChildAt(1).setVisibility(externalAvailable ? VISIBLE : GONE);
        setControlsVisible(true);
    }

    void toggleControls() {
        setControlsVisible(!this.controlsVisible);
    }

    private LinearLayout topBar(String title) {
        LinearLayout bar = new LinearLayout(getContext());
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(chromeInset(), dp(5), chromeInset(), dp(5));
        Compat.setBackground(bar, UiComponents.round(getContext(),
                Color.argb(188, 20, 20, 22), 12, this.scale));

        ImageButton back = iconButton(R.drawable.ic_arrow_back, "返回播放器");
        back.setOnClickListener(view -> this.listener.onBack());
        bar.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));

        String value = title == null || title.isEmpty() ? "视频" : title;
        value = RichContent.plainText(value);
        if (value.isEmpty()) value = "视频";
        TextView name = text("", 13.0f, this.tokens.text);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        OfficialEmojiFallback.load(getContext());
        EmojiRenderer.set(name, value, this.session.darkMode());
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        nameParams.leftMargin = dp(5);
        bar.addView(name, nameParams);
        return bar;
    }

    private LinearLayout bottomBar() {
        LinearLayout bar = new LinearLayout(getContext());
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), 0, dp(8), 0);
        Compat.setBackground(bar, UiComponents.round(getContext(),
                Color.argb(188, 20, 20, 22), 12, this.scale));

        this.play = iconButton(R.drawable.ic_play, "播放");
        Compat.setBackground(this.play, UiComponents.round(getContext(),
                Color.argb(42, 255, 255, 255), 18, this.scale));
        this.play.setOnClickListener(view -> {
            this.listener.onTogglePlayback();
            scheduleHide();
        });
        bar.addView(this.play, new LinearLayout.LayoutParams(dp(36), dp(36)));

        this.currentTime = text("00:00", 9.5f, Color.WHITE);
        this.currentTime.setGravity(Gravity.CENTER);
        bar.addView(this.currentTime, new LinearLayout.LayoutParams(dp(38), -2));

        this.seekBar = new SeekBar(getContext());
        this.seekBar.setMax(1);
        Compat.tint(this.seekBar, this.tokens.accent, Color.WHITE);
        this.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) currentTime.setText(formatTime(progress));
            }

            @Override public void onStartTrackingTouch(SeekBar bar) {
                userSeeking = true;
                setControlsVisible(true);
            }

            @Override public void onStopTrackingTouch(SeekBar bar) {
                userSeeking = false;
                listener.onSeekTo(bar.getProgress());
                scheduleHide();
            }
        });
        bar.addView(this.seekBar, new LinearLayout.LayoutParams(0, dp(36), 1.0f));

        this.totalTime = text("00:00", 9.5f, Color.WHITE);
        this.totalTime.setGravity(Gravity.CENTER);
        bar.addView(this.totalTime, new LinearLayout.LayoutParams(dp(38), -2));
        return bar;
    }

    private LinearLayout errorActions() {
        LinearLayout actions = new LinearLayout(getContext());
        actions.setGravity(Gravity.CENTER);
        TextView retry = command("重试");
        retry.setOnClickListener(view -> this.listener.onRetry());
        actions.addView(retry, new LinearLayout.LayoutParams(0, -1, 1.0f));
        TextView external = command("凉腕播放器");
        external.setOnClickListener(view -> this.listener.onExternalPlayer());
        LinearLayout.LayoutParams externalParams = new LinearLayout.LayoutParams(0, -1, 1.0f);
        externalParams.leftMargin = dp(8);
        actions.addView(external, externalParams);
        return actions;
    }

    private TextView command(String value) {
        TextView view = text(value, 11.0f, this.tokens.text);
        view.setGravity(Gravity.CENTER);
        Compat.setBackground(view, UiComponents.ghostButton(getContext(), this.tokens, this.scale));
        return view;
    }

    private ImageButton iconButton(int resource, String description) {
        ImageButton button = new ImageButton(getContext());
        button.setContentDescription(description);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        Compat.setBackground(button, null);
        if (resource != 0) {
            Drawable drawable = Compat.tintedDrawable(getContext(), resource, Color.WHITE);
            if (drawable != null) button.setImageDrawable(drawable);
        }
        return button;
    }

    private void setPlayIcon(int resource, String description) {
        this.play.setContentDescription(description);
        Drawable drawable = Compat.tintedDrawable(getContext(), resource, Color.WHITE);
        if (drawable != null) this.play.setImageDrawable(drawable);
    }

    private void setControlsVisible(boolean visible) {
        this.controlsVisible = visible;
        float alpha = visible ? 1.0f : 0.0f;
        if (Motions.off()) {
            this.topBar.setAlpha(alpha);
            this.bottomBar.setAlpha(alpha);
        } else {
            this.topBar.animate().alpha(alpha).setDuration(MotionSpec.PRESS_OUT_MS).start();
            this.bottomBar.animate().alpha(alpha).setDuration(MotionSpec.PRESS_OUT_MS).start();
        }
        this.topBar.setClickable(visible);
        this.bottomBar.setClickable(visible);
    }

    private void scheduleHide() {
        removeCallbacks(this::hideIfPlaying);
        postDelayed(this::hideIfPlaying, 3500L);
    }

    private void hideIfPlaying() {
        if (this.playing) setControlsVisible(false);
    }

    private int chromeInset() {
        int width = getResources().getDisplayMetrics().widthPixels;
        return this.roundLayout ? RoundLayoutMetrics.componentInset(width, 0.12f, dp(8))
                : dp(8);
    }

    private LayoutParams matchParams() {
        LayoutParams params = new LayoutParams(-1, dp(48), Gravity.TOP);
        params.leftMargin = chromeInset();
        params.rightMargin = chromeInset();
        params.topMargin = chromeInset();
        return params;
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size * this.session.textScale() / 100.0f);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        Compat.setLetterSpacing(view, 0);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density * this.scale);
    }

    private static String formatTime(int millis) {
        int totalSeconds = Math.max(0, millis / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        if (minutes >= 60) {
            int hours = minutes / 60;
            minutes %= 60;
            return String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds);
    }
}
