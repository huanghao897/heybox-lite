package com.ronan.heyboxlite;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Build;
import android.view.SurfaceHolder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

final class VideoPlaybackController implements SurfaceHolder.Callback {
    interface Listener {
        void onPrepared(int durationMs);
        void onVideoSizeChanged(int width, int height);
        void onProgress(int positionMs, int durationMs);
        void onPlayingChanged(boolean playing);
        void onBufferingChanged(boolean buffering);
        void onCompleted();
        void onError(String message);
    }

    private static final long PROGRESS_INTERVAL_MS = 250L;
    private final Context context;
    private final String url;
    private final Map<String, String> headers;
    private final Listener listener;
    private final boolean autoplay;
    private final boolean loop;
    private final boolean muted;
    private final android.os.Handler handler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private MediaPlayer player;
    private SurfaceHolder surface;
    private boolean surfaceReady;
    private boolean prepared;
    private boolean pendingPlay = true;
    private boolean released;
    private int pendingSeekMs;
    private int generation;
    private boolean fastForwarding;
    private boolean wasPlayingBeforeFastForward;
    private boolean nativeFastForward;
    private float playbackSpeedBeforeFastForward = 1.0f;
    private final Runnable fastForwardTick = this::runFallbackFastForward;

    VideoPlaybackController(Context context, String url, Listener listener,
                            boolean autoplay, boolean loop, boolean muted) {
        this.context = context;
        this.url = url;
        this.listener = listener;
        this.headers = defaultHeaders();
        this.autoplay = autoplay;
        this.loop = loop;
        this.muted = muted;
        this.pendingPlay = autoplay;
    }

    void attach(SurfaceHolder holder) {
        this.surface = holder;
        holder.addCallback(this);
    }

    void togglePlayback() {
        if (this.player == null || !this.prepared) {
            this.pendingPlay = true;
            prepare();
            return;
        }
        if (this.player.isPlaying()) {
            pause();
        } else {
            play();
        }
    }

    void play() {
        this.pendingPlay = true;
        if (this.player == null || !this.prepared) {
            prepare();
            return;
        }
        this.player.start();
        this.listener.onPlayingChanged(true);
        scheduleProgress();
    }

    void pause() {
        this.pendingPlay = false;
        if (this.player == null || !this.prepared || !this.player.isPlaying()) return;
        this.player.pause();
        this.listener.onPlayingChanged(false);
        reportProgress();
    }

    void seekTo(int positionMs) {
        this.pendingSeekMs = Math.max(0, positionMs);
        if (this.player != null && this.prepared) {
            this.player.seekTo(this.pendingSeekMs);
            reportProgress();
        }
    }

    void seekBy(int deltaMs) {
        int duration = this.player == null ? 0 : safeDuration(this.player);
        int target = currentPosition() + deltaMs;
        if (duration > 0) target = Math.min(duration, target);
        seekTo(Math.max(0, target));
    }

    boolean beginFastForward() {
        if (this.released || this.player == null || !this.prepared) return false;
        if (this.fastForwarding) return true;

        this.fastForwarding = true;
        this.wasPlayingBeforeFastForward = this.player.isPlaying();
        if (!this.wasPlayingBeforeFastForward) {
            try {
                this.player.start();
            } catch (IllegalStateException error) {
                this.fastForwarding = false;
                return false;
            }
            this.listener.onPlayingChanged(true);
            scheduleProgress();
        }

        this.nativeFastForward = setPlaybackSpeed(2.0f);
        if (!this.nativeFastForward) scheduleFallbackFastForward();
        return true;
    }

    void endFastForward() {
        if (!this.fastForwarding) return;
        this.fastForwarding = false;
        this.handler.removeCallbacks(this.fastForwardTick);
        if (this.nativeFastForward) setPlaybackSpeed(this.playbackSpeedBeforeFastForward);
        this.nativeFastForward = false;
        if (!this.wasPlayingBeforeFastForward) pause();
    }

    void retry() {
        this.pendingPlay = this.autoplay;
        prepare();
    }

    boolean isPlaying() {
        return this.player != null && this.prepared && this.player.isPlaying();
    }

    int currentPositionForUi() {
        return currentPosition();
    }

    void release() {
        this.released = true;
        this.generation++;
        this.handler.removeCallbacksAndMessages(null);
        releasePlayer();
        this.surface = null;
        this.surfaceReady = false;
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        this.surface = holder;
        this.surfaceReady = true;
        prepare();
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format,
                                         int width, int height) {
        this.surface = holder;
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        this.surfaceReady = false;
        this.pendingSeekMs = currentPosition();
        releasePlayer();
    }

    private void prepare() {
        if (this.released || !this.surfaceReady || this.url == null || this.url.isEmpty()) return;
        releasePlayer();
        int token = ++this.generation;
        try {
            MediaPlayer next = new MediaPlayer();
            this.player = next;
            next.setAudioStreamType(AudioManager.STREAM_MUSIC);
            next.setVolume(this.muted ? 0.0f : 1.0f, this.muted ? 0.0f : 1.0f);
            next.setDisplay(this.surface);
            next.setOnPreparedListener(mediaPlayer -> {
                if (!isCurrent(mediaPlayer, token)) return;
                this.prepared = true;
                int duration = safeDuration(mediaPlayer);
                if (this.pendingSeekMs > 0) mediaPlayer.seekTo(this.pendingSeekMs);
                this.listener.onVideoSizeChanged(
                        safeVideoWidth(mediaPlayer), safeVideoHeight(mediaPlayer));
                this.listener.onPrepared(duration);
                if (this.pendingPlay) {
                    mediaPlayer.start();
                    this.listener.onPlayingChanged(true);
                    scheduleProgress();
                } else {
                    this.listener.onPlayingChanged(false);
                    reportProgress();
                }
            });
            next.setOnCompletionListener(mediaPlayer -> {
                if (!isCurrent(mediaPlayer, token)) return;
                this.fastForwarding = false;
                this.nativeFastForward = false;
                this.handler.removeCallbacks(this.fastForwardTick);
                if (this.loop) {
                    this.pendingPlay = true;
                    mediaPlayer.seekTo(0);
                    mediaPlayer.start();
                    this.listener.onPlayingChanged(true);
                    scheduleProgress();
                    return;
                }
                this.pendingPlay = false;
                this.listener.onPlayingChanged(false);
                this.listener.onCompleted();
                reportProgress();
            });
            next.setOnInfoListener((mediaPlayer, what, extra) -> {
                if (!isCurrent(mediaPlayer, token)) return false;
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                    this.listener.onBufferingChanged(true);
                } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                    this.listener.onBufferingChanged(false);
                }
                return false;
            });
            next.setOnErrorListener((mediaPlayer, what, extra) -> {
                if (!isCurrent(mediaPlayer, token)) return true;
                this.prepared = false;
                this.listener.onPlayingChanged(false);
                this.listener.onBufferingChanged(false);
                this.listener.onError("播放器无法打开此视频");
                releasePlayer();
                return true;
            });
            next.setDataSource(this.context, Uri.parse(this.url), this.headers);
            this.listener.onBufferingChanged(true);
            next.prepareAsync();
        } catch (IOException | RuntimeException error) {
            this.prepared = false;
            this.listener.onBufferingChanged(false);
            this.listener.onError("视频地址暂时无法播放");
            releasePlayer();
        }
    }

    private boolean isCurrent(MediaPlayer candidate, int token) {
        return !this.released && this.player == candidate && this.generation == token;
    }

    private void releasePlayer() {
        this.fastForwarding = false;
        this.nativeFastForward = false;
        this.handler.removeCallbacks(this.fastForwardTick);
        MediaPlayer current = this.player;
        this.player = null;
        this.prepared = false;
        if (current == null) return;
        try {
            current.setOnPreparedListener(null);
            current.setOnCompletionListener(null);
            current.setOnErrorListener(null);
            current.setOnInfoListener(null);
            current.reset();
            current.release();
        } catch (IllegalStateException ignored) {
            current.release();
        }
    }

    private void scheduleProgress() {
        this.handler.removeCallbacksAndMessages(null);
        this.handler.postDelayed(this::progressTick, PROGRESS_INTERVAL_MS);
    }

    private void progressTick() {
        if (this.released || this.player == null || !this.prepared) return;
        reportProgress();
        if (isPlaying()) scheduleProgress();
    }

    private void reportProgress() {
        if (this.player == null || !this.prepared) return;
        this.listener.onProgress(currentPosition(), safeDuration(this.player));
    }

    private boolean setPlaybackSpeed(float speed) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || this.player == null
                || !this.prepared) return false;
        try {
            PlaybackParams params = this.player.getPlaybackParams();
            if (speed > 1.0f) this.playbackSpeedBeforeFastForward = params.getSpeed();
            params.setSpeed(speed);
            this.player.setPlaybackParams(params);
            return true;
        } catch (IllegalArgumentException | IllegalStateException error) {
            return false;
        }
    }

    private void scheduleFallbackFastForward() {
        this.handler.removeCallbacks(this.fastForwardTick);
        this.handler.postDelayed(this.fastForwardTick, 125L);
    }

    private void runFallbackFastForward() {
        if (!this.fastForwarding || this.released || this.player == null || !this.prepared) {
            return;
        }
        int duration = safeDuration(this.player);
        int target = currentPosition() + 125;
        if (duration > 0) target = Math.min(duration, target);
        seekTo(target);
        if (duration <= 0 || target < duration) {
            this.handler.postDelayed(this.fastForwardTick, 125L);
        }
    }

    private int currentPosition() {
        if (this.player == null || !this.prepared) return this.pendingSeekMs;
        try {
            return Math.max(0, this.player.getCurrentPosition());
        } catch (IllegalStateException ignored) {
            return this.pendingSeekMs;
        }
    }

    private static int safeDuration(MediaPlayer mediaPlayer) {
        try {
            return Math.max(0, mediaPlayer.getDuration());
        } catch (IllegalStateException ignored) {
            return 0;
        }
    }

    private static int safeVideoWidth(MediaPlayer mediaPlayer) {
        try {
            return Math.max(0, mediaPlayer.getVideoWidth());
        } catch (IllegalStateException ignored) {
            return 0;
        }
    }

    private static int safeVideoHeight(MediaPlayer mediaPlayer) {
        try {
            return Math.max(0, mediaPlayer.getVideoHeight());
        } catch (IllegalStateException ignored) {
            return 0;
        }
    }

    private static Map<String, String> defaultHeaders() {
        Map<String, String> values = new HashMap<>();
        values.put("Referer", "http://api.maxjia.com/");
        values.put("User-Agent", "Mozilla/5.0 AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/41.0.2272.118 Safari/537.36 ApiMaxJia/1.0");
        return values;
    }
}
