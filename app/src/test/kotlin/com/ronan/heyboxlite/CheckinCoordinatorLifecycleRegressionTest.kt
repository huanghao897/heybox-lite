package com.ronan.heyboxlite

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class,
    instrumentedPackages = ["com.ronan.heyboxlite"],
    shadows = [CheckinFlowRequests::class, CheckinFlowCrypto::class])
@LooperMode(LooperMode.Mode.PAUSED)
class CheckinCoordinatorLifecycleRegressionTest {
    @Test fun queuedStatusRetryStopsWithActivityAndResumesOnceWithoutRestartingRequest() {
        CheckinFlowFixture().use { f ->
            f.owner.refreshStatus()
            CheckinFlowRequests.statuses.last().onError(retry(CheckinCenterClient.Operation.STATUS))
            f.background()
            f.advance(60)
            assertEquals(2, CheckinFlowRequests.statuses.size)
            f.foreground()
            f.advance(0)
            assertEquals(3, CheckinFlowRequests.statuses.size)
            f.foregroundAgain()
            f.advance(60)
            assertEquals(3, CheckinFlowRequests.statuses.size)
        }
    }

    @Test fun inFlightWriteResultWhilePausedIsDeliveredOnceAndResumeDoesNotReplayWrite() {
        CheckinFlowFixture().use { f ->
            var delivered = 0
            f.coordinator.runNow(f.owner.flowLifecycle.requests,
                object : CheckinCenterClient.Callback<CheckinCenterClient.RunResult> {
                    override fun onSuccess(value: CheckinCenterClient.RunResult?) { delivered++ }
                    override fun onError(error: CheckinCenterClient.ApiError) { fail(error.message) }
                })
            f.background()
            CheckinFlowRequests.runs.single().onSuccess(null)
            assertEquals(1, delivered)
            f.foreground()
            f.advance(60)
            assertEquals(1, CheckinFlowRequests.runs.size)
            assertEquals(1, delivered)
        }
    }

    @Test fun retryableWriteFailureWhilePausedWaitsAndKeepsExistingRetryBudget() {
        CheckinFlowFixture().use { f ->
            val result = object : CheckinCenterClient.Callback<CheckinCenterClient.RunResult> {
                override fun onSuccess(value: CheckinCenterClient.RunResult?) = Unit
                override fun onError(error: CheckinCenterClient.ApiError) = Unit
            }
            f.coordinator.runNow(f.owner.flowLifecycle.requests, result)
            f.background()
            CheckinFlowRequests.runs.single().onError(retry(CheckinCenterClient.Operation.RUN_NOW))
            f.advance(60)
            assertEquals(1, CheckinFlowRequests.runs.size)
            f.foreground()
            f.advance(0)
            assertEquals(2, CheckinFlowRequests.runs.size)
            CheckinFlowRequests.runs.last().onError(retry(CheckinCenterClient.Operation.RUN_NOW))
            f.advance(4)
            assertEquals(3, CheckinFlowRequests.runs.size)
            CheckinFlowRequests.runs.last().onError(retry(CheckinCenterClient.Operation.RUN_NOW))
            f.advance(60)
            assertEquals(3, CheckinFlowRequests.runs.size)
        }
    }

    @Test fun closePageCancelsQueuedRetryAndRejectsLateResultWithoutClosingSharedCoordinator() {
        CheckinFlowFixture().use { f ->
            f.owner.refreshStatus()
            val callback = CheckinFlowRequests.statuses.last()
            callback.onError(retry(CheckinCenterClient.Operation.STATUS))
            f.owner.close()
            val state = f.owner.uiState
            callback.onSuccess(CheckinFlowFixture.status())
            f.advance(60)
            assertEquals(2, CheckinFlowRequests.statuses.size)
            assertEquals(state, f.owner.uiState)
            f.coordinator.getStatus(object : CheckinCenterClient.Callback<CheckinCenterClient.Status> {
                override fun onSuccess(value: CheckinCenterClient.Status) = Unit
                override fun onError(error: CheckinCenterClient.ApiError) = Unit
            })
            assertEquals(3, CheckinFlowRequests.statuses.size)
        }
    }

    private fun CheckinFlowFixture.foregroundAgain() {
        background()
        foreground()
    }
    private fun retry(operation: CheckinCenterClient.Operation) = CheckinFlowFixture.error(operation, 503)
}
