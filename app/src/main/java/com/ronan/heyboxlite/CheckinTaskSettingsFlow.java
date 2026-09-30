package com.ronan.heyboxlite;

final class CheckinTaskSettingsFlow {
    interface Host {
        boolean active();
        boolean paired();
        CheckinCenterClient.Status status();
        void setControlsEnabled(boolean enabled);
        void taskUpdated(CheckinCenterClient.Task task, boolean renderPage);
        void reloadStatus();
        void authorizationFailed(String message);
        void showMessage(String message);
        void render();
    }

    private final CheckinCenterCoordinator coordinator;
    private final Host host;
    private boolean requestInFlight;
    private boolean closed;

    CheckinTaskSettingsFlow(CheckinCenterCoordinator coordinator, Host host) {
        this.coordinator = coordinator;
        this.host = host;
    }

    boolean requestInFlight() {
        return requestInFlight;
    }

    void save(boolean enabled, String scheduleTime, int offsetMinutes) {
        save(enabled, scheduleTime, offsetMinutes, null, null);
    }

    void share(String action, boolean enabled) {
        if (host.status() == null) return;
        CheckinCenterClient.Task task = host.status().task;
        if (!task.sharing.available) return;
        save(task.enabled, CheckinTaskSettingsView.normalizedTime(task), task.offsetMinutes,
                action, enabled);
    }

    private void save(boolean enabled, String scheduleTime, int offsetMinutes,
                      String shareAction, Boolean shareEnabled) {
        if (closed || requestInFlight || host.status() == null) return;
        requestInFlight = true;
        host.setControlsEnabled(false);
        coordinator.updateTaskSettings(enabled, scheduleTime, offsetMinutes,
                shareAction, shareEnabled,
                new CheckinCenterClient.Callback<CheckinCenterClient.Task>() {
                    @Override
                    public void onSuccess(CheckinCenterClient.Task value) {
                        if (closed) return;
                        requestInFlight = false;
                        if (!host.paired() || host.status() == null) {
                            if (host.active()) host.reloadStatus();
                            return;
                        }
                        host.taskUpdated(value, host.active());
                    }

                    @Override
                    public void onError(CheckinCenterClient.ApiError error) {
                        if (closed) return;
                        requestInFlight = false;
                        if (!host.active()) return;
                        if (error.authorizationInvalid()) {
                            host.authorizationFailed(error.getMessage());
                            return;
                        }
                        host.showMessage(error.getMessage());
                        host.render();
                    }
                });
    }

    void close() {
        closed = true;
    }
}
