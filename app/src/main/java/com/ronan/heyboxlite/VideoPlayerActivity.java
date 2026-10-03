package com.ronan.heyboxlite;

import android.graphics.Color;
import android.os.Bundle;
import android.view.SurfaceView;

import androidx.activity.ComponentActivity;

public final class VideoPlayerActivity extends ComponentActivity
        implements VideoPlaybackController.Listener {
    static final String EXTRA_URL = "video_url";
    static final String EXTRA_COVER = "video_cover";
    static final String EXTRA_TITLE = "video_title";

    private SurfaceView surface;
    private VideoPlaybackController playback;
    private ComposeVideoPlayerState playerState;
    private ComposeVideoPlayerBridge composePlayer;
    private VideoData video;
    private boolean destroyed;
    private boolean externalAvailable;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ImageLoader.init(this);
        SessionStore session = new SessionStore(this);
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

        this.playerState = new ComposeVideoPlayerState(this.video.title,
                this.video.cover, !session.noImage());
        this.playback = new VideoPlaybackController(this, this.video.url, this,
                session.videoAutoplay(), session.videoLoop(), session.videoMuted());
        this.composePlayer = new ComposeVideoPlayerBridge(this, session, tokens,
                this.playerState, new ComposeVideoPlayerActions() {
            @Override public void onBack() {
                if (!destroyed) finish();
            }

            @Override public void onTogglePlayback() {
                if (!destroyed) playback.togglePlayback();
            }

            @Override public void onSeekTo(int positionMs) {
                if (!destroyed) playback.seekTo(positionMs);
            }

            @Override public void onRetry() {
                if (destroyed) return;
                playerState.retrying();
                playback.retry();
            }

            @Override public void onExternalPlayer() {
                if (destroyed) return;
                if (!externalAvailable || !VideoPlayerLauncher.openExternal(
                        VideoPlayerActivity.this, video)) {
                    playerState.setError("未安装可用的外部播放器", false);
                }
            }

            @Override public void onDoubleTap() {
                if (!destroyed && playback.isPlaying()) playback.pause();
            }

            @Override public boolean onFastForwardStart() {
                return !destroyed && playback.beginFastForward();
            }

            @Override public void onFastForwardEnd() {
                playback.endFastForward();
            }

            @Override public void onSurfaceAvailable(SurfaceView view) {
                attachSurface(view);
            }

            @Override public void onSurfaceReleased(SurfaceView view) {
                detachSurface(view);
            }
        });
        setContentView(this.composePlayer.getView());
    }

    @Override protected void onResume() {
        super.onResume();
        if (this.composePlayer != null) this.composePlayer.resume();
    }

    @Override protected void onPause() {
        if (this.composePlayer != null) this.composePlayer.pause();
        if (!this.destroyed && this.playback != null) this.playback.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (this.composePlayer != null) this.composePlayer.pause();
        this.destroyed = true;
        if (this.playback != null) this.playback.release();
        if (this.surface != null) detachSurface(this.surface);
        if (this.composePlayer != null) this.composePlayer.close();
        super.onDestroy();
    }

    @Override public void onPrepared(int durationMs) {
        if (!this.destroyed) this.playerState.setPrepared(durationMs);
    }

    @Override public void onVideoSizeChanged(int width, int height) {
        if (!this.destroyed) this.playerState.setVideoSize(width, height);
    }

    @Override public void onProgress(int positionMs, int durationMs) {
        if (!this.destroyed) this.playerState.setProgress(positionMs, durationMs);
    }

    @Override public void onPlayingChanged(boolean playing) {
        if (!this.destroyed) this.playerState.setPlaying(playing);
    }

    @Override public void onBufferingChanged(boolean buffering) {
        if (!this.destroyed) this.playerState.setBuffering(buffering);
    }

    @Override public void onCompleted() {
        if (!this.destroyed) this.playerState.setCompleted();
    }

    @Override public void onError(String message) {
        if (!this.destroyed) this.playerState.setError(message, this.externalAvailable);
    }

    private void attachSurface(SurfaceView view) {
        if (this.destroyed || this.surface == view) return;
        if (this.surface != null) detachSurface(this.surface);
        this.surface = view;
        view.setKeepScreenOn(true);
        this.playback.attach(view.getHolder());
    }

    private void detachSurface(SurfaceView view) {
        view.setKeepScreenOn(false);
        view.getHolder().removeCallback(this.playback);
        if (this.surface != view) return;
        if (!this.destroyed) this.playback.surfaceDestroyed(view.getHolder());
        this.surface = null;
    }

    private VideoData readVideo() {
        String url = getIntent().getStringExtra(EXTRA_URL);
        if (url == null || url.trim().isEmpty()) return null;
        String cover = getIntent().getStringExtra(EXTRA_COVER);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        return VideoData.create(url, cover, title);
    }

    private static int parseColor(String value, int fallback) {
        try {
            return value == null || value.trim().isEmpty() ? fallback : Color.parseColor(value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
