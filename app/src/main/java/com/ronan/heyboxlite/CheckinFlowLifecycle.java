package com.ronan.heyboxlite;

/** Route visibility controls scheduling; it never invalidates in-flight results. */
final class CheckinFlowLifecycle {
    interface Visible { boolean get(); }
    final CheckinRequestScope requests = new CheckinRequestScope();
    private final CheckinPairingFlow pairing;
    private final CheckinMobileLoginFlow mobile;
    private final CheckinServiceAccountFlow service;
    private final Visible pairingVisible;
    private final Visible mobileVisible;
    private boolean resumed;

    CheckinFlowLifecycle(CheckinPairingFlow pairing, CheckinMobileLoginFlow mobile,
                         CheckinServiceAccountFlow service, Visible pairingVisible,
                         Visible mobileVisible) {
        this.pairing = pairing;
        this.mobile = mobile;
        this.service = service;
        this.pairingVisible = pairingVisible;
        this.mobileVisible = mobileVisible;
        requests.pause();
    }

    void resume() {
        resumed = true;
        requests.resume();
        routeChanged();
    }

    void routeChanged() {
        if (resumed && pairingVisible.get()) {
            pairing.resume();
            service.resume();
        } else {
            pairing.pause();
            service.pause();
        }
        if (resumed && mobileVisible.get()) mobile.resume();
        else mobile.pause();
    }

    void pause() {
        resumed = false;
        requests.pause();
        pairing.pause();
        mobile.pause();
        service.pause();
    }

    void close() {
        pause();
        requests.close();
        mobile.close();
        pairing.close();
        service.close();
    }
}
