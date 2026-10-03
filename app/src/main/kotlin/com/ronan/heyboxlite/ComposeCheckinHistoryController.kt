package com.ronan.heyboxlite

/** History remains readable without paid entitlement; it only needs a paired device. */
internal class ComposeCheckinHistoryController(
    private val request: (CheckinCenterClient.Callback<CheckinHistory>) -> Unit,
    private val state: () -> ComposeCheckinUiState,
    private val update: (ComposeCheckinUiState) -> Unit,
    private val active: () -> Boolean,
    private val generation: () -> Int,
    private val onAuthorizationLost: (String) -> Unit,
) {
    private var requestSerial = 0

    fun load() {
        val current = state()
        val status = current.status ?: return
        if (!active() || !current.paired || !status.account.state.equals("connected", true)) return
        val serial = ++requestSerial
        val ownerGeneration = generation()
        update(current.copy(historyLoading = true, historyError = ""))
        request(object : CheckinCenterClient.Callback<CheckinHistory> {
            override fun onSuccess(value: CheckinHistory) {
                if (accept(serial, ownerGeneration)) {
                    update(state().copy(history = value, historyLoading = false, historyError = ""))
                }
            }
            override fun onError(error: CheckinCenterClient.ApiError) {
                if (!accept(serial, ownerGeneration)) return
                if (error.authorizationInvalid()) {
                    onAuthorizationLost(error.message ?: "签到服务连接已失效")
                } else if (error.statusCode == 404 && status.lastRun != null) {
                    update(state().copy(history = CheckinHistory.fromLastRun(status.lastRun),
                        historyLoading = false, historyError = ""))
                } else {
                    update(state().copy(historyLoading = false,
                        historyError = error.message?.ifEmpty { "暂时无法读取记录" } ?: "暂时无法读取记录"))
                }
            }
        })
    }

    fun close() { requestSerial++ }
    private fun accept(serial: Int, owner: Int) = active() && requestSerial == serial && generation() == owner
}
