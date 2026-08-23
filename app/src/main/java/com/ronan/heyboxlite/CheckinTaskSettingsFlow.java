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
        if (closed || requestInFlight || host.status() == null) return;
        requestInFlight = true;
        host.setControlsEnabled(false);
        coordinator.updateTaskSettings(enabled, scheduleTime, offsetMinutes,
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
