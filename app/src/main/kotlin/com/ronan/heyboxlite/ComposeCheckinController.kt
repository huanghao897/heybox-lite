package com.ronan.heyboxlite

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Owns Compose-facing state while the existing flows continue to own request rules,
 * credential persistence, retry policy, and in-memory pairing/session expiry.
 */
internal class ComposeCheckinController(
    internal val services: ComposeServices,
    internal val coordinator: CheckinCenterCoordinator,
) {

    private var statusGeneration = 0
    internal var closed = false
    private var started = false

    var uiState by mutableStateOf(
        ComposeCheckinUiState(
            stage = if (coordinator.paired()) {
                ComposeCheckinStage.SYNCING
            } else {
                ComposeCheckinStage.UNPAIRED
            },
            paired = coordinator.paired(),
            supported = coordinator.supported(),
        ),
    )
        internal set

    private val captchaCallbacks = object : ComposeCheckinCaptchaCallbacks {
        override fun onCaptchaResult(ticket: String, randstr: String) {
            if (closed) return
            mobileLogin.onCaptchaResult(ticket, randstr)
        }

        override fun onCaptchaCancelled(message: String) {
            if (closed) return
            mobileLogin.onCaptchaCancelled(message)
        }
    }

    internal val pairingHost = ComposeCheckinPairingHost(this)
    internal val mobileLoginHost = ComposeCheckinMobileLoginHost(this)
    internal val serviceAccountHost = ComposeCheckinServiceAccountHost(this)
    internal val taskSettingsHost = ComposeCheckinTaskSettingsHost(this)
    internal val pairingFlow = CheckinPairingFlow(coordinator, pairingHost)
    internal val mobileLogin = CheckinMobileLoginFlow(coordinator, mobileLoginHost)
    internal val serviceAccount = CheckinServiceAccountFlow(
        coordinator,
        pairingFlow,
        serviceAccountHost,
    )
    internal val taskSettings = CheckinTaskSettingsFlow(coordinator, taskSettingsHost)
    private val billingController = ComposeCheckinBillingController(
        services = services,
        coordinator = coordinator,
        state = { uiState },
        setState = { uiState = it },
        active = { isActive() },
        onAuthorizationLost = ::handleAuthorizationLost,
        onClosePage = ::closeSponsorship,
    )

    fun start() {
        if (closed || started) return
        started = true
        pairingFlow.resume()
        if (coordinator.paired()) refreshStatus()
    }

    fun close() {
        if (closed) return
        closed = true
        billingController.stop()
        ComposeCheckinCaptchaController.clear(captchaCallbacks)
        mobileLogin.close()
        serviceAccount.close()
        pairingFlow.close()
        taskSettings.close()
    }

    fun navigateBack(onBack: () -> Unit) {
        if (closed) return
        when (uiState.route) {
            ComposeCheckinRoute.PAIRING -> cancelPairing()
            ComposeCheckinRoute.MOBILE_LOGIN -> {
                mobileLogin.pause()
                setCenterState(clearError = true)
            }
            ComposeCheckinRoute.TASK_SETTINGS -> setCenterState(clearError = true)
            ComposeCheckinRoute.SPONSORSHIP -> closeSponsorship()
            ComposeCheckinRoute.CENTER -> onBack()
        }
    }

    fun refreshStatus() {
        if (closed) return
        if (!coordinator.paired()) {
            setCenterState(clearError = false)
            return
        }
        val generation = ++statusGeneration
        uiState = uiState.copy(
            route = ComposeCheckinRoute.CENTER,
            stage = ComposeCheckinStage.SYNCING,
            paired = true,
            status = null,
            history = null,
            historyLoading = false,
            historyError = "",
            errorMessage = "",
        )
        coordinator.getStatus(object : CheckinCenterClient.Callback<CheckinCenterClient.Status> {
            override fun onSuccess(value: CheckinCenterClient.Status) {
                if (!isActive() || generation != statusGeneration) return
                val connected = value.account.state.equals("connected", ignoreCase = true)
                uiState = uiState.copy(
                    stage = ComposeCheckinStage.CONNECTED,
                    paired = true,
                    status = value,
                    history = null,
                    historyLoading = connected,
                    historyError = "",
                    errorMessage = "",
                    billingMembership = value.membership,
                )
                if (connected) loadHistory(value, generation)
            }

            override fun onError(error: CheckinCenterClient.ApiError) {
                if (!isActive() || generation != statusGeneration) return
                handleApiError(error)
            }
        })
    }

    fun refreshHistory() {
        val value = uiState.status ?: return
        if (!coordinator.paired() ||
            !value.account.state.equals("connected", ignoreCase = true)
        ) return
        loadHistory(value, statusGeneration)
    }

    fun openPairing() {
        if (closed) return
        if (!coordinator.supported()) {
            showControllerError("小黑盒自动签到需要 Android 7.0 或更高版本")
            return
        }
        resetServiceForm()
        pairingFlow.start()
        serviceAccount.resume()
    }

    fun cancelPairing() {
        pairingFlow.cancel()
        serviceAccount.pause()
        resetServiceForm()
        setCenterState(clearError = true)
    }

    fun openMobileLogin() {
        if (closed) return
        if (!coordinator.paired()) {
            showControllerError("请先连接签到服务")
            return
        }
        mobileLogin.start()
        uiState = uiState.copy(
            route = ComposeCheckinRoute.MOBILE_LOGIN,
            errorMessage = "",
            mobilePhone = "",
            mobileCode = "",
            mobilePassword = "",
            mobileStatus = "验证码由小黑盒发送",
            mobileStatusKind = ComposeCheckinStatusKind.NONE,
            mobileMode = CheckinMobileLoginFlow.Mode.SMS,
            mobileHasSession = false,
            mobileSmsButton = "发送验证码",
            mobileSmsButtonEnabled = true,
        )
        mobileLogin.resume()
    }

    fun openTaskSettings() {
        if (uiState.status != null && coordinator.paired()) {
            uiState = uiState.copy(route = ComposeCheckinRoute.TASK_SETTINGS)
        }
    }

    fun runNow() {
        val current = uiState.status ?: return
        if (uiState.stage == ComposeCheckinStage.RUNNING || !current.task.active()) return
        uiState = uiState.copy(stage = ComposeCheckinStage.RUNNING, errorMessage = "")
        coordinator.runNow(object : CheckinCenterClient.Callback<CheckinCenterClient.RunResult> {
            override fun onSuccess(value: CheckinCenterClient.RunResult) {
                if (!isActive()) return
                services.toast.show(runMessage(value))
                refreshStatus()
            }

            override fun onError(error: CheckinCenterClient.ApiError) {
                if (!isActive()) return
                handleApiError(error)
            }
        })
    }

    fun requestRevoke() {
        if (uiState.stage == ComposeCheckinStage.RUNNING) return
        uiState = uiState.copy(showRevokeConfirm = true)
    }

    fun dismissRevoke() {
        uiState = uiState.copy(showRevokeConfirm = false)
    }

    fun revokeDevice() {
        if (closed) return
        uiState = uiState.copy(
            showRevokeConfirm = false,
            stage = ComposeCheckinStage.SYNCING,
            errorMessage = "",
        )
        coordinator.revokeDevice(object : CheckinCenterClient.Callback<Boolean> {
            override fun onSuccess(value: Boolean) {
                if (!isActive()) return
                pairingFlow.cancel()
                statusGeneration++
                setCenterState(clearError = true)
                services.toast.show("已撤销此设备")
            }

            override fun onError(error: CheckinCenterClient.ApiError) {
                if (!isActive()) return
                handleApiError(error)
            }
        })
    }

    fun setServiceUsername(value: String) {
        uiState = uiState.copy(serviceUsername = value)
    }

    fun setServicePassword(value: String) {
        uiState = uiState.copy(servicePassword = value)
    }

    fun setServicePasswordConfirmation(value: String) {
        uiState = uiState.copy(servicePasswordConfirmation = value)
    }

    fun setServiceEmail(value: String) {
        uiState = uiState.copy(serviceEmail = value)
    }

    fun setServiceEmailCode(value: String) {
        uiState = uiState.copy(serviceEmailCode = value)
    }

    fun switchServiceMode(mode: CheckinServiceAccountFlow.Mode) {
        serviceAccount.switchMode(mode)
    }

    fun submitServiceAccount() {
        serviceAccount.submit()
    }

    fun sendRegistrationEmail() {
        serviceAccount.sendRegistrationEmail()
    }

    fun setMobilePhone(value: String) {
        uiState = uiState.copy(mobilePhone = value)
    }

    fun setMobileCode(value: String) {
        uiState = uiState.copy(mobileCode = value)
    }

    fun setMobilePassword(value: String) {
        uiState = uiState.copy(mobilePassword = value)
    }

    fun switchMobileMode(mode: CheckinMobileLoginFlow.Mode) {
        mobileLogin.switchMode(mode)
    }

    fun sendSmsCode() {
        mobileLogin.sendSmsCode()
    }

    fun submitSmsCode() {
        mobileLogin.submitSmsCode()
    }

    fun submitPasswordLogin() {
        mobileLogin.loginWithPassword()
    }

    fun consumeFocusTarget() {
        if (uiState.focusTarget.isNotEmpty()) uiState = uiState.copy(focusTarget = "")
    }

    fun setTaskEnabled(enabled: Boolean) {
        val task = uiState.status?.task ?: return
        taskSettings.save(enabled, CheckinTaskSettingsView.normalizedTime(task), task.offsetMinutes)
    }

    fun setTaskTime(time: String) {
        val task = uiState.status?.task ?: return
        taskSettings.save(task.enabled, time, task.offsetMinutes)
    }

    fun adjustTaskOffset(delta: Int) {
        val task = uiState.status?.task ?: return
        val next = (task.offsetMinutes + delta).coerceIn(0, 720)
        if (next == task.offsetMinutes) return
        taskSettings.save(task.enabled, CheckinTaskSettingsView.normalizedTime(task), next)
    }

    fun setShare(action: String, enabled: Boolean) {
        taskSettings.share(action, enabled)
    }

    fun setBillingAmount(value: String) = billingController.setAmount(value)

    fun setBillingPaymentReference(value: String) = billingController.setPaymentReference(value)

    fun openSponsorship(membership: CheckinBilling.Membership?) =
        billingController.open(membership)

    fun closeSponsorship() {
        billingController.stop()
        setCenterState(clearError = true)
    }

    fun finishOrRegenerateBilling() = billingController.finishOrRegenerate()

    fun createBillingOrder() = billingController.createOrder()

    fun loadBillingQr() = billingController.loadQr()

    fun submitBillingClaim() = billingController.submitClaim()

    private fun loadHistory(value: CheckinCenterClient.Status, generation: Int) {
        if (!isActive() || !coordinator.paired()) return
        uiState = uiState.copy(historyLoading = true, historyError = "")
        coordinator.getHistory(object : CheckinCenterClient.Callback<CheckinHistory> {
            override fun onSuccess(result: CheckinHistory) {
                if (!isActive() || generation != statusGeneration || uiState.status !== value) return
                uiState = uiState.copy(history = result, historyLoading = false, historyError = "")
            }

            override fun onError(error: CheckinCenterClient.ApiError) {
                if (!isActive() || generation != statusGeneration || uiState.status !== value) return
                if (error.authorizationInvalid()) {
                    handleAuthorizationLost(errorMessage(error))
                } else if (error.operation == CheckinCenterClient.Operation.HISTORY &&
                    error.statusCode == 404 && value.lastRun != null
                ) {
                    uiState = uiState.copy(
                        history = CheckinHistory.fromLastRun(value.lastRun),
                        historyLoading = false,
                        historyError = "",
                    )
                } else {
                    uiState = uiState.copy(
                        historyLoading = false,
                        historyError = errorMessage(error),
                    )
                }
            }
        })
    }

    internal fun isActive(): Boolean = !closed

    internal fun setCenterState(clearError: Boolean) {
        val paired = coordinator.paired()
        billingController.stop()
        uiState = uiState.copy(
            route = ComposeCheckinRoute.CENTER,
            stage = if (paired) ComposeCheckinStage.SYNCING else ComposeCheckinStage.UNPAIRED,
            paired = paired,
            status = if (paired) uiState.status else null,
            history = if (paired) uiState.history else null,
            historyLoading = false,
            historyError = "",
            errorMessage = if (clearError) "" else uiState.errorMessage,
            pairing = null,
            showRevokeConfirm = false,
        )
        if (paired && uiState.status == null && started) refreshStatus()
    }

    private fun resetServiceForm() {
        serviceAccount.reset()
        uiState = uiState.copy(
            serviceMode = CheckinServiceAccountFlow.Mode.LOGIN,
            serviceUsername = "",
            servicePassword = "",
            servicePasswordConfirmation = "",
            serviceEmail = "",
            serviceEmailCode = "",
            serviceStatus = "",
            serviceStatusKind = ComposeCheckinStatusKind.NONE,
            serviceControlsEnabled = true,
            serviceEmailButton = "发送邮箱验证码",
            serviceEmailButtonEnabled = true,
        )
    }

    private fun handleApiError(error: CheckinCenterClient.ApiError) {
        val message = errorMessage(error)
        if (error.authorizationInvalid()) {
            handleAuthorizationLost(message)
            return
        }
        val paired = coordinator.paired()
        uiState = uiState.copy(
            route = ComposeCheckinRoute.CENTER,
            stage = if (paired) ComposeCheckinStage.ERROR else ComposeCheckinStage.UNPAIRED,
            paired = paired,
            errorMessage = message,
            historyLoading = false,
        )
    }

    internal fun handleAuthorizationLost(message: String) {
        pairingFlow.cancel()
        mobileLogin.pause()
        serviceAccount.pause()
        billingController.stop()
        statusGeneration++
        uiState = uiState.copy(
            route = ComposeCheckinRoute.CENTER,
            stage = ComposeCheckinStage.UNPAIRED,
            paired = false,
            status = null,
            history = null,
            historyLoading = false,
            historyError = "",
            errorMessage = message.ifEmpty { "签到服务连接已失效，请重新连接" },
            pairing = null,
        )
    }

    internal fun showControllerError(message: String) {
        if (coordinator.paired()) {
            uiState = uiState.copy(
                route = ComposeCheckinRoute.CENTER,
                stage = ComposeCheckinStage.ERROR,
                paired = true,
                errorMessage = message,
            )
        } else {
            setCenterState(clearError = true)
            uiState = uiState.copy(errorMessage = message)
        }
    }

    private fun errorMessage(error: CheckinCenterClient.ApiError?): String =
        error?.message?.takeIf { it.isNotEmpty() } ?: "签到服务请求失败"

    internal fun openCaptcha(uri: String) {
        ComposeCheckinCaptchaController.launch(
            services.activity,
            uri,
            services.theme.roundScreen,
            captchaCallbacks,
        )
    }

    private fun runMessage(result: CheckinCenterClient.RunResult): String {
        val checkIn = result.checkIn
        if (checkIn != null && checkIn.checkedIn) {
            val reward = rewardLabel(checkIn)
            return if (reward.isEmpty()) "已签到" else "已签到，获得 $reward"
        }
        return when (result.status.lowercase()) {
            "ok" -> "小黑盒签到任务已完成"
            "skipped" -> "今日没有需要执行的签到任务"
            else -> "签到任务已返回结果"
        }
    }

    private fun rewardLabel(result: CheckinCenterClient.CheckinResult): String {
        val parts = mutableListOf<String>()
        if (result.coinDelta >= 0) parts += "${result.coinDelta} 盒币"
        if (result.experienceDelta >= 0) parts += "${result.experienceDelta} 经验"
        return parts.joinToString("、")
    }

}
