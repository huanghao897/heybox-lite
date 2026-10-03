package com.ronan.heyboxlite

import android.os.SystemClock

/** Focused adapter for the in-memory pairing and polling flow. */
internal class ComposeCheckinPairingHost(
    private val owner: ComposeCheckinController,
) : CheckinPairingFlow.Host {
    override fun pairingVisible(): Boolean = owner.isActive() &&
        owner.uiState.route == ComposeCheckinRoute.PAIRING

    override fun pairingStarting() {
        owner.uiState = owner.uiState.copy(
            route = ComposeCheckinRoute.PAIRING,
            stage = ComposeCheckinStage.SYNCING,
            paired = false,
            pairing = null,
            pairingRemainingSeconds = 0L,
            errorMessage = "",
        )
    }

    override fun pairingStarted() {
        owner.uiState = owner.uiState.copy(
            route = ComposeCheckinRoute.PAIRING,
            stage = ComposeCheckinStage.SYNCING,
            pairing = owner.pairingFlow.startValue(),
            pairingRemainingSeconds = 0L,
            errorMessage = "",
        )
    }

    override fun pairingConnected() {
        owner.serviceAccount.pause()
        owner.uiState = owner.uiState.copy(
            route = ComposeCheckinRoute.CENTER,
            stage = ComposeCheckinStage.SYNCING,
            paired = true,
            pairing = null,
            errorMessage = "",
        )
        owner.refreshStatus()
    }

    override fun updatePairingCountdown(remainingSeconds: Long) {
        if (remainingSeconds <= 0L && pairingVisible()) {
            owner.pairingFlow.cancel()
            owner.serviceAccount.pause()
            owner.setCenterState(clearError = true)
            owner.uiState = owner.uiState.copy(errorMessage = "配对已过期，请重新连接")
            return
        }
        owner.uiState = owner.uiState.copy(
            pairingRemainingSeconds = remainingSeconds.coerceAtLeast(0L),
        )
    }

    override fun showError(message: String) {
        owner.showControllerError(message)
    }

    override fun showMessage(message: String) {
        owner.services.toast.show(message)
    }
}

/** Adapter scoped only to the native Heybox SMS/password login flow. */
internal class ComposeCheckinMobileLoginHost(
    private val owner: ComposeCheckinController,
) : CheckinMobileLoginFlow.Host {
    override fun active(): Boolean = owner.isActive() &&
        owner.uiState.route == ComposeCheckinRoute.MOBILE_LOGIN

    override fun phone(): String = owner.uiState.mobilePhone

    override fun code(): String = owner.uiState.mobileCode

    override fun password(): String = owner.uiState.mobilePassword

    override fun renderModeChanged() {
        owner.uiState = owner.uiState.copy(mobileMode = owner.mobileLogin.mode())
    }

    override fun setControls(
        enabled: Boolean,
        mode: CheckinMobileLoginFlow.Mode,
        hasSession: Boolean,
        retryAtElapsed: Long,
    ) {
        owner.uiState = owner.uiState.copy(
            mobileControlsEnabled = enabled,
            mobileMode = mode,
            mobileHasSession = hasSession,
            mobileSmsButtonEnabled = enabled && !hasSession,
        )
    }

    override fun setStatus(message: String, status: CheckinMobileLoginFlow.Status) {
        val kind = when (status) {
            CheckinMobileLoginFlow.Status.NORMAL -> ComposeCheckinStatusKind.NORMAL
            CheckinMobileLoginFlow.Status.ACCENT -> ComposeCheckinStatusKind.ACCENT
            CheckinMobileLoginFlow.Status.ERROR -> ComposeCheckinStatusKind.ERROR
        }
        if (status == CheckinMobileLoginFlow.Status.ERROR && !owner.coordinator.paired()) {
            owner.handleAuthorizationLost(message)
        } else {
            owner.uiState = owner.uiState.copy(
                mobileStatus = message,
                mobileStatusKind = kind,
            )
        }
    }

    override fun updateSmsButton(label: String, enabled: Boolean) {
        owner.uiState = owner.uiState.copy(
            mobileSmsButton = label,
            mobileSmsButtonEnabled = enabled,
        )
    }

    override fun clearCode() {
        owner.uiState = owner.uiState.copy(mobileCode = "")
    }

    override fun clearPassword() {
        owner.uiState = owner.uiState.copy(mobilePassword = "")
    }

    override fun focusCode() {
        owner.uiState = owner.uiState.copy(focusTarget = "mobile_code")
    }

    override fun openCaptcha(uri: String) {
        owner.openCaptcha(uri)
    }

    override fun showMessage(message: String) {
        owner.services.toast.show(message)
    }

    override fun connected() {
        owner.mobileLogin.pause()
        owner.uiState = owner.uiState.copy(
            route = ComposeCheckinRoute.CENTER,
            stage = ComposeCheckinStage.SYNCING,
            mobileStatus = "",
            mobileCode = "",
            mobilePassword = "",
            errorMessage = "",
        )
        owner.refreshStatus()
    }

}

/** Adapter scoped only to service-account login, registration, and email challenge UI. */
internal class ComposeCheckinServiceAccountHost(
    private val owner: ComposeCheckinController,
) : CheckinServiceAccountFlow.Host {
    override fun pairingVisible(): Boolean = owner.isActive() &&
        owner.uiState.route == ComposeCheckinRoute.PAIRING

    override fun username(): String = owner.uiState.serviceUsername

    override fun password(): String = owner.uiState.servicePassword

    override fun passwordConfirmation(): String = owner.uiState.servicePasswordConfirmation

    override fun email(): String = owner.uiState.serviceEmail

    override fun emailCode(): String = owner.uiState.serviceEmailCode

    override fun renderModeChanged() {
        owner.uiState = owner.uiState.copy(serviceMode = owner.serviceAccount.mode())
    }

    override fun setControlsEnabled(enabled: Boolean, emailRetryAtElapsed: Long) {
        owner.uiState = owner.uiState.copy(
            serviceControlsEnabled = enabled,
            serviceEmailButtonEnabled = enabled &&
                SystemClock.elapsedRealtime() >= emailRetryAtElapsed,
        )
    }

    override fun setStatus(message: String, status: CheckinServiceAccountFlow.Status) {
        val kind = when (status) {
            CheckinServiceAccountFlow.Status.NORMAL -> ComposeCheckinStatusKind.NORMAL
            CheckinServiceAccountFlow.Status.ACCENT -> ComposeCheckinStatusKind.ACCENT
            CheckinServiceAccountFlow.Status.ERROR -> ComposeCheckinStatusKind.ERROR
        }
        owner.uiState = owner.uiState.copy(
            serviceStatus = message,
            serviceStatusKind = kind,
        )
    }

    override fun updateEmailButton(label: String, enabled: Boolean) {
        owner.uiState = owner.uiState.copy(
            serviceEmailButton = label,
            serviceEmailButtonEnabled = enabled,
        )
    }

    override fun clearPasswords() {
        owner.uiState = owner.uiState.copy(
            servicePassword = "",
            servicePasswordConfirmation = "",
        )
    }

    override fun clearEmailCode() {
        owner.uiState = owner.uiState.copy(serviceEmailCode = "")
    }

    override fun focusEmailCode() {
        owner.uiState = owner.uiState.copy(focusTarget = "service_email_code")
    }

    override fun focusPassword() {
        owner.uiState = owner.uiState.copy(focusTarget = "service_password")
    }

    override fun showError(message: String) {
        owner.showControllerError(message)
    }

    override fun pairingApproved() {
        owner.pairingFlow.pollNow()
    }
}

/** Adapter scoped to task-setting writes and their authorization/error rendering. */
internal class ComposeCheckinTaskSettingsHost(
    private val owner: ComposeCheckinController,
) : CheckinTaskSettingsFlow.Host {
    override fun active(): Boolean = owner.isActive() &&
        owner.uiState.route == ComposeCheckinRoute.TASK_SETTINGS

    override fun paired(): Boolean = owner.coordinator.paired()

    override fun status(): CheckinCenterClient.Status? = owner.uiState.status

    override fun setControlsEnabled(enabled: Boolean) {
        owner.uiState = owner.uiState.copy(taskSaving = !enabled)
    }

    override fun taskUpdated(task: CheckinCenterClient.Task, renderPage: Boolean) {
        val current = owner.uiState.status
        if (current == null) {
            if (renderPage) owner.refreshStatus()
            return
        }
        owner.uiState = owner.uiState.copy(
            status = CheckinCenterClient.Status(
                current.account,
                task,
                current.lastRun,
                current.membership,
            ),
            taskSaving = false,
            errorMessage = "",
        )
    }

    override fun reloadStatus() {
        owner.refreshStatus()
    }

    override fun authorizationFailed(message: String) {
        owner.handleAuthorizationLost(message)
    }

    override fun showMessage(message: String) {
        owner.services.toast.show(message)
    }

    override fun render() {
        owner.uiState = owner.uiState.copy(
            taskSaving = owner.taskSettings.requestInFlight(),
        )
    }
}
