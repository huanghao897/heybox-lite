package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

public final class VideoPlayerActivity extends Activity
        implements VideoPlaybackController.Listener {
    static final String EXTRA_URL = "video_url";
    static final String EXTRA_COVER = "video_cover";
    static final String EXTRA_TITLE = "video_title";

    private SurfaceView surface;
    private ImageView cover;
    private VideoPlaybackController playback;
    private VideoPlayerControls controls;
    private FrameLayout playerRoot;
    private VideoData video;
    private boolean destroyed;
    private boolean externalAvailable;
    private boolean fastForwarding;
    private SessionStore session;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ImageLoader.init(this);
        this.session = new SessionStore(this);
        Motions.setLevel(session.motionLevel());
        Compat.colorSystemBars(getWindow(), Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(Compat.fullscreenFlags());

        this.video = readVideo();
        if (this.video == null || !this.video.playable()) {
            finish();
            return;
        }
        this.externalAvailable = session.videoExternalFallback()
                && VideoPlayerLauncher.canOpenExternal(this, this.video);
        ThemeTokens tokens = ThemeTokens.of(session.darkMode(),
                parseColor(session.primaryColor(), session.darkMode() ? Color.WHITE
                        : Color.rgb(20, 21, 23)),
                parseColor(session.secondaryColor(), session.darkMode()
                        ? Color.rgb(196, 198, 201) : Color.rgb(87, 91, 96)));

        FrameLayout root = new FrameLayout(this);
        this.playerRoot = root;
        root.setBackgroundColor(Color.BLACK);

        this.surface = new SurfaceView(this);
        this.surface.setKeepScreenOn(true);
        GestureDetector gestures = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override public boolean onDown(android.view.MotionEvent event) {
                        return true;
                    }

                    @Override public boolean onSingleTapConfirmed(android.view.MotionEvent event) {
                        if (controls != null) controls.toggleControls();
                        return true;
                    }

                    @Override public boolean onDoubleTap(android.view.MotionEvent event) {
                        if (playback != null && playback.isPlaying()) playback.pause();
                        return true;
                    }

                    @Override public void onLongPress(android.view.MotionEvent event) {
                        if (!session.videoLongPressFastForward() || playback == null) return;
                        if (playback.beginFastForward()) {
                            fastForwarding = true;
                            if (controls != null) controls.setFastForwarding(true);
                        }
                    }
                });
        this.surface.setOnTouchListener((view, event) -> {
            boolean handled = gestures.onTouchEvent(event);
            int action = event.getActionMasked();
            if ((action == android.view.MotionEvent.ACTION_UP
                    || action == android.view.MotionEvent.ACTION_CANCEL)
                    && fastForwarding) {
                fastForwarding = false;
                playback.endFastForward();
                if (controls != null) controls.setFastForwarding(false);
            }
            return handled;
        });
        root.addView(this.surface, new FrameLayout.LayoutParams(-1, -1));

        this.cover = new ImageView(this);
        this.cover.setScaleType(session.videoDisplayMode() == 1
                ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        root.addView(this.cover, new FrameLayout.LayoutParams(-1, -1));
        if (!session.noImage() && !this.video.cover.isEmpty()) {
            ImageLoader.intoMeasuredRevealStable(this.cover, this.video.cover, coverTarget(), null);
        } else {
            this.cover.setVisibility(View.INVISIBLE);
        }

        this.controls = new VideoPlayerControls(this, session, tokens,
                session.usesRoundLayout(), this.video.title, new VideoPlayerControls.Listener() {
            @Override public void onBack() { finish(); }
            @Override public void onTogglePlayback() { playback.togglePlayback(); }
            @Override public void onSeekTo(int positionMs) { playback.seekTo(positionMs); }
            @Override public void onRetry() {
                controls.setError("正在重新连接", externalAvailable);
                playback.retry();
            }
            @Override public void onExternalPlayer() {
                if (!externalAvailable || !VideoPlayerLauncher.openExternal(
                        VideoPlayerActivity.this, video)) {
                    controls.setError("未安装可用的外部播放器", false);
                }
            }
        });
        root.addView(this.controls, new FrameLayout.LayoutParams(-1, -1));

        this.playback = new VideoPlaybackController(this, this.video.url, this,
                session.videoAutoplay(), session.videoLoop(), session.videoMuted());
        this.playback.attach(this.surface.getHolder());
        setContentView(root);
    }

    @Override protected void onPause() {
        super.onPause();
        if (!this.destroyed && this.playback != null) {
            if (this.fastForwarding) {
                this.fastForwarding = false;
                this.playback.endFastForward();
                if (this.controls != null) this.controls.setFastForwarding(false);
            }
            this.playback.pause();
        }
    }

    @Override protected void onDestroy() {
        this.destroyed = true;
        if (this.playback != null) this.playback.release();
        super.onDestroy();
    }

    @Override public void onPrepared(int durationMs) {
        this.cover.setVisibility(View.INVISIBLE);
        this.controls.setPrepared(durationMs);
    }

    @Override public void onVideoSizeChanged(int width, int height) {
        if (width <= 0 || height <= 0 || this.playerRoot == null) return;
        this.playerRoot.post(() -> applyVideoBounds(width, height));
    }

    @Override public void onProgress(int positionMs, int durationMs) {
        this.controls.setProgress(positionMs, durationMs);
    }

    @Override public void onPlayingChanged(boolean playing) {
        this.controls.setPlaying(playing);
    }

    @Override public void onBufferingChanged(boolean buffering) {
        this.controls.setBuffering(buffering);
    }

    @Override public void onCompleted() {
        this.fastForwarding = false;
        this.controls.setFastForwarding(false);
        this.controls.setCompleted();
    }

    @Override public void onError(String message) {
        this.fastForwarding = false;
        this.controls.setFastForwarding(false);
        this.cover.setVisibility(View.VISIBLE);
        this.controls.setError(message, this.externalAvailable);
    }

    private VideoData readVideo() {
        String url = getIntent().getStringExtra(EXTRA_URL);
        if (url == null || url.trim().isEmpty()) return null;
        String cover = getIntent().getStringExtra(EXTRA_COVER);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        return VideoData.create(url, cover, title);
    }

    private int coverTarget() {
        int width = getResources().getDisplayMetrics().widthPixels;
        return Math.max(360, Math.min(900, width));
    }

    private void applyVideoBounds(int videoWidth, int videoHeight) {
        if (this.playerRoot == null || this.surface == null || this.cover == null) return;
        int rootWidth = this.playerRoot.getWidth();
        int rootHeight = this.playerRoot.getHeight();
        if (rootWidth <= 0 || rootHeight <= 0) return;
        if (this.session.videoDisplayMode() == 1) {
            setVideoLayout(-1, -1);
            return;
        }
        float videoRatio = videoWidth / (float) videoHeight;
        float rootRatio = rootWidth / (float) rootHeight;
        int width;
        int height;
        if (videoRatio > rootRatio) {
            width = rootWidth;
            height = Math.round(rootWidth / videoRatio);
        } else {
            height = rootHeight;
            width = Math.round(rootHeight * videoRatio);
        }
        setVideoLayout(width, height);
    }

    private void setVideoLayout(int width, int height) {
        FrameLayout.LayoutParams surfaceParams =
                (FrameLayout.LayoutParams) this.surface.getLayoutParams();
        surfaceParams.width = width;
        surfaceParams.height = height;
        surfaceParams.gravity = Gravity.CENTER;
        this.surface.setLayoutParams(surfaceParams);

        FrameLayout.LayoutParams coverParams =
                (FrameLayout.LayoutParams) this.cover.getLayoutParams();
        coverParams.width = width;
        coverParams.height = height;
        coverParams.gravity = Gravity.CENTER;
        this.cover.setLayoutParams(coverParams);
    }

    private static int parseColor(String value, int fallback) {
        try {
            return value == null || value.trim().isEmpty() ? fallback : Color.parseColor(value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
