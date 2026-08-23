package com.ronan.heyboxlite;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

final class CheckinPairingFlow {
    interface Host {
        boolean pairingVisible();
        void pairingStarting();
        void pairingStarted();
        void pairingConnected();
        void updatePairingCountdown(long remainingSeconds);
        void showError(String message);
        void showMessage(String message);
    }

    private static final long SECOND_MS = 1_000L;

    private final CheckinCenterCoordinator coordinator;
    private final Host host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable pollTask = this::poll;
    private final Runnable countdownTask = this::updateCountdown;
    private Session session;
    private boolean resumed;
    private boolean closed;
    private int requestGeneration;

    CheckinPairingFlow(CheckinCenterCoordinator coordinator, Host host) {
        this.coordinator = coordinator;
        this.host = host;
    }

    void start() {
        if (!coordinator.supported()) {
            host.showError("小黑盒自动签到需要 Android 7.0 或更高版本");
            return;
        }
        cancel();
        int generation = ++requestGeneration;
        host.pairingStarting();
        coordinator.startPairing(new CheckinCenterClient.Callback<CheckinCenterClient.PairingStart>() {
            @Override
            public void onSuccess(CheckinCenterClient.PairingStart value) {
                if (closed || generation != requestGeneration) return;
                long lifetimeSeconds = Math.min(600L, value.expiresInSeconds);
                session = new Session(value,
                        SystemClock.elapsedRealtime() + lifetimeSeconds * SECOND_MS);
                host.pairingStarted();
                schedulePoll(value.intervalSeconds * SECOND_MS);
                scheduleCountdown(0L);
            }

            @Override
            public void onError(CheckinCenterClient.ApiError error) {
                if (closed || generation != requestGeneration) return;
                host.showError(error.getMessage());
            }
        });
    }

    void resume() {
        resumed = true;
        if (session == null || !host.pairingVisible()) return;
        schedulePoll(0L);
        scheduleCountdown(0L);
    }

    void pause() {
        resumed = false;
        handler.removeCallbacks(pollTask);
        handler.removeCallbacks(countdownTask);
    }

    void cancel() {
        requestGeneration++;
        handler.removeCallbacks(pollTask);
        handler.removeCallbacks(countdownTask);
        session = null;
    }

    void close() {
        closed = true;
        resumed = false;
        cancel();
        handler.removeCallbacksAndMessages(null);
    }

    CheckinCenterClient.PairingStart startValue() {
        return session == null ? null : session.start;
    }

    boolean expired() {
        return session == null || session.expired();
    }

    void pollNow() {
        schedulePoll(0L);
    }

    void bindCountdown() {
        updateCountdownText();
    }

    private void poll() {
        Session current = session;
        if (!canRun(current)) return;
        if (current.expired()) {
            cancel();
            host.showError("配对已过期，请重新连接");
            return;
        }
        coordinator.pollPairing(current.start.deviceCode,
                new CheckinCenterClient.Callback<CheckinCenterClient.PairingPoll>() {
                    @Override
                    public void onSuccess(CheckinCenterClient.PairingPoll value) {
                        if (closed || session != current) return;
                        current.retries = 0;
                        if (!value.authorized()) {
                            schedulePoll(current.start.intervalSeconds * SECOND_MS);
                            return;
                        }
                        if (!coordinator.authorize(value.deviceToken)) {
                            cancel();
                            host.showError("无法安全保存签到服务连接，请检查系统安全组件");
                            return;
                        }
                        cancel();
                        host.showMessage("签到服务已连接");
                        host.pairingConnected();
                    }

                    @Override
                    public void onError(CheckinCenterClient.ApiError error) {
                        if (closed || session != current) return;
                        if (CheckinRetryPolicy.shouldRetry(error, current.retries)
                                && !current.expired()) {
                            long delay = CheckinRetryPolicy.delayMillis(error,
                                    current.retries++,
                                    current.start.intervalSeconds * SECOND_MS);
                            schedulePoll(delay);
                            return;
                        }
                        cancel();
                        host.showError(error.getMessage());
                    }
                });
    }

    private boolean canRun(Session value) {
        return !closed && resumed && value != null && host.pairingVisible();
    }

    private void schedulePoll(long delayMillis) {
        handler.removeCallbacks(pollTask);
        if (canRun(session)) {
            handler.postDelayed(pollTask, Math.max(0L, delayMillis));
        }
    }

    private void scheduleCountdown(long delayMillis) {
        handler.removeCallbacks(countdownTask);
        if (canRun(session)) {
            handler.postDelayed(countdownTask, Math.max(0L, delayMillis));
        }
    }

    private void updateCountdown() {
        if (!canRun(session)) return;
        long remaining = updateCountdownText();
        if (remaining > 0L) scheduleCountdown(SECOND_MS);
    }

    private long updateCountdownText() {
        Session current = session;
        if (current == null) return 0L;
        long remaining = Math.max(0L,
                current.expiresAtElapsed - SystemClock.elapsedRealtime());
        host.updatePairingCountdown((remaining + 999L) / SECOND_MS);
        return remaining;
    }

    private static final class Session {
        final CheckinCenterClient.PairingStart start;
        final long expiresAtElapsed;
        int retries;

        Session(CheckinCenterClient.PairingStart start, long expiresAtElapsed) {
            this.start = start;
            this.expiresAtElapsed = expiresAtElapsed;
        }

        boolean expired() {
            return SystemClock.elapsedRealtime() >= expiresAtElapsed;
        }
    }
}
