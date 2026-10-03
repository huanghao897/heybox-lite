package com.ronan.heyboxlite

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.runtime.MutableState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.TestName
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi",
    shadows = [ComposeCrashRecoveryIoShadows.Uploads::class, ComposeCrashRecoveryIoShadows.Exporter::class],
    instrumentedPackages = ["com.ronan.heyboxlite"])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeCrashRecoveryActivityTest {
    @get:Rule(order = 0) val fixtures = object : ExternalResource() {
        override fun before() {
            ComposeCrashRecoveryIoShadows.Uploads.calls.set(0)
            ComposeCrashRecoveryIoShadows.Exporter.reset()
            val app = ApplicationProvider.getApplicationContext<Application>()
            SessionStore(app).apply {
                setRoundScreen(false)
                setDarkMode(true)
                setUiScale(100)
                setTextScale(100)
            }
            shadowOf(app).denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            CrashReporter.store(app).enqueue(ComposeCrashRecoveryFixtures.report)
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<CrashRecoveryActivity>()
    @get:Rule(order = 2) val testName = TestName()
    private val ui = ComposeCrashRecoveryUiAssertions(compose, { compose.activity },
        { "activity-" + testName.methodName })
    @get:Rule(order = 3) val failure = object : TestWatcher() {
        override fun failed(error: Throwable, description: Description) {
            runCatching { ui.capture("failure") }.exceptionOrNull()?.let(error::addSuppressed)
        }
    }

    @After fun releaseLocalExporter() {
        ComposeCrashRecoveryIoShadows.Exporter.gate.countDown()
        if (ComposeCrashRecoveryIoShadows.Exporter.calls.get() > 0) {
            assertTrue("Local export worker must finish before teardown",
                ComposeCrashRecoveryIoShadows.Exporter.completed.await(5, TimeUnit.SECONDS))
        }
    }

    @Test fun realActivityMountsOnlyComposeWithNoMainActivityOrLiteApplication() {
        compose.onNodeWithTag("crash-screen").assertIsDisplayed()
        val activity = compose.activity
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        assertEquals(1, content.childCount)
        assertTrue(content.getChildAt(0) is ComposeView)
        assertFalse(descendants(content).any { it is TextView })
        assertEquals(Application::class.java, activity.application.javaClass)
        assertFalse(CrashReporter.isMainProcess(activity))
        assertEquals(1, ComposeCrashRecoveryIoShadows.Uploads.calls.get())
        assertNull(shadowOf(activity).nextStartedActivity)
        assertNull(shadowOf(activity).lastShownDialogId)
        ui.capture("mounted")
    }

    @Test fun manifestStillDeclaresAnUnexportedIndependentCrashProcess() {
        val activity = compose.activity
        val info = activity.packageManager.getActivityInfo(ComponentName(activity,
            CrashRecoveryActivity::class.java), 0)
        assertEquals(activity.packageName + ":crash", info.processName)
        assertFalse(info.exported)
        assertEquals("java.lang.IllegalStateException",
            ComposeCrashRecoveryState.fromReport(CrashReporter.latestCrashReport(activity)).summary)
    }

    @Test fun restartTouchLaunchesSplashWithACleanTaskAndKeepsTheReport() {
        val activity = compose.activity
        ui.touch("crash-restart", "\u91cd\u542f\u5e94\u7528")
        val intent = requireNotNull(shadowOf(activity).nextStartedActivity)
        assertEquals(SplashActivity::class.java.name, requireNotNull(intent.component).className)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            intent.flags and (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertTrue(activity.isFinishing)
        assertEquals(ComposeCrashRecoveryFixtures.report, CrashReporter.latestCrashReport(activity))
    }

    @Test fun exitTouchFinishesWithoutLaunchingTheMainApp() {
        val activity = compose.activity
        ui.touch("crash-exit", "\u9000\u51fa")
        assertTrue(activity.isFinishing)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test fun legacyPermissionDenialOrCancellationLeavesLogAndAllowsRetry() {
        ui.touch("crash-save")
        val activity = compose.activity
        val request = requireNotNull(shadowOf(activity).lastRequestedPermission)
        assertEquals(9141, request.requestCode)
        assertArrayEquals(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), request.requestedPermissions)
        ui.reach("crash-save").assertIsNotEnabled()
        assertEquals(0, ComposeCrashRecoveryIoShadows.Exporter.calls.get())
        compose.runOnIdle { activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, intArrayOf()) }
        ui.assertLabelComplete("\u672a\u83b7\u5b58\u50a8\u6743\u9650\uff0c\u65e5\u5fd7\u4ecd\u4fdd\u7559\u5728\u672c\u673a")
        ui.reach("crash-save").assertIsEnabled()
        assertEquals(ComposeCrashRecoveryFixtures.report, CrashReporter.latestCrashReport(activity))
    }

    @Test fun legacyPermissionGrantContinuesTheExportWithTheCompleteReport() {
        ui.touch("crash-save")
        val activity = compose.activity
        val request = requireNotNull(shadowOf(activity).lastRequestedPermission)
        val app = activity.application
        ComposeCrashRecoveryIoShadows.Exporter.result = "Download/heyboxlite/offline-fixture.txt"
        compose.runOnIdle {
            shadowOf(app).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions,
                intArrayOf(PackageManager.PERMISSION_GRANTED))
        }
        awaitStatus("\u5df2\u4fdd\u5b58\u81f3 Download/heyboxlite")
        ui.reach("crash-save").assertIsEnabled()
        assertEquals(1, ComposeCrashRecoveryIoShadows.Exporter.calls.get())
        assertEquals(ComposeCrashRecoveryFixtures.report, ComposeCrashRecoveryIoShadows.Exporter.report)
        assertTrue(requireNotNull(ComposeCrashRecoveryIoShadows.Exporter.name)
            .matches(Regex("heybox-lite-crash-[0-9]{8}-[0-9]{6}\\.txt")))
        ui.capture("local-export-result")
    }

    @Test fun savingAndFailureAreRealActivityStatesAndDuplicateTouchDoesNotExportTwice() {
        compose.runOnIdle {
            shadowOf(compose.activity.application).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            ComposeCrashRecoveryIoShadows.Exporter.gate = CountDownLatch(1)
        }
        ui.touch("crash-save")
        awaitStatus("\u6b63\u5728\u4fdd\u5b58")
        ui.reach("crash-save").assertIsNotEnabled()
        ui.touch("crash-save")
        ui.capture("saving")
        ComposeCrashRecoveryIoShadows.Exporter.gate.countDown()
        awaitStatus("\u4fdd\u5b58\u5931\u8d25\uff0c\u65e5\u5fd7\u4ecd\u4fdd\u7559\u5728\u672c\u673a")
        ui.reach("crash-save").assertIsEnabled()
        assertEquals(1, ComposeCrashRecoveryIoShadows.Exporter.calls.get())
        assertEquals(ComposeCrashRecoveryFixtures.report, ComposeCrashRecoveryIoShadows.Exporter.report)
        assertEquals(ComposeCrashRecoveryFixtures.report, CrashReporter.latestCrashReport(compose.activity))
        ui.capture("failure-retained-log")
    }

    @Test fun activityRecreationRestoresFailureStatusAndMountsAFreshComposeSurface() {
        val old = compose.activity
        ui.touch("crash-save")
        compose.runOnIdle {
            old.onRequestPermissionsResult(9141, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), intArrayOf())
        }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertNotSame(old, compose.activity)
        compose.onNodeWithTag("crash-screen").assertIsDisplayed()
        compose.onNodeWithText("\u672a\u83b7\u5b58\u50a8\u6743\u9650\uff0c\u65e5\u5fd7\u4ecd\u4fdd\u7559\u5728\u672c\u673a").assertExists()
        ui.reach("crash-save").assertIsEnabled()
        assertEquals(2, ComposeCrashRecoveryIoShadows.Uploads.calls.get())
        ui.capture("recreated")
    }

    @Test fun recreationWhileAwaitingPermissionCannotLeaveSavePermanentlyDisabled() {
        ui.touch("crash-save")
        ui.reach("crash-save").assertIsNotEnabled()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        ui.reach("crash-save").assertIsEnabled()
        ui.touch("crash-save")
        assertEquals(9141, requireNotNull(shadowOf(compose.activity).lastRequestedPermission).requestCode)
        assertEquals(0, ComposeCrashRecoveryIoShadows.Exporter.calls.get())
    }

    @Test @Config(sdk = [29])
    fun android29ExportsWithoutRequestingLegacyStoragePermission() {
        ComposeCrashRecoveryIoShadows.Exporter.result = "Download/heyboxlite/offline-fixture.txt"
        ui.touch("crash-save")
        awaitStatus("\u5df2\u4fdd\u5b58\u81f3 Download/heyboxlite")
        assertNull(shadowOf(compose.activity).lastRequestedPermission)
        assertEquals(1, ComposeCrashRecoveryIoShadows.Exporter.calls.get())
    }

    @Test fun statusRecompositionAndSaveDoNotRescheduleAutomaticUpload() {
        val activity = compose.activity
        val host = recoveryHost(activity)
        assertEquals(1, ComposeCrashRecoveryIoShadows.Uploads.calls.get())
        compose.runOnIdle { host.status("offline status fixture", false) }
        compose.onNodeWithText("offline status fixture").assertExists()
        ui.touch("crash-save")
        val request = requireNotNull(shadowOf(activity).lastRequestedPermission)
        compose.runOnIdle {
            activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, intArrayOf())
        }
        ui.reach("crash-save").assertIsEnabled()
        assertEquals(1, ComposeCrashRecoveryIoShadows.Uploads.calls.get())
        assertEquals(ComposeCrashRecoveryFixtures.report, CrashReporter.latestCrashReport(activity))
    }

    @Test fun destroyedHostDropsPermissionResultsStatusUpdatesAndStaleRestartActions() {
        val activity = compose.activity
        val host = recoveryHost(activity)
        val surface = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ComposeView
        val worker = exportWorker(activity)
        val staleRestart = requireNotNull(ui.reach("crash-restart").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action)
        ui.touch("crash-save")
        val request = requireNotNull(shadowOf(activity).lastRequestedPermission)
        assertEquals("android.content.pm.action.REQUEST_PERMISSIONS",
            requireNotNull(shadowOf(activity).nextStartedActivity).action)
        assertNull(shadowOf(activity).nextStartedActivity)
        val before = hostState(host)
        compose.activityRule.scenario.close()
        assertTrue(worker.isShutdown)
        assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS))
        assertFalse(surface.hasComposition)
        assertNull(field(activity, "view"))
        compose.runOnUiThread {
            activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, intArrayOf())
            activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions,
                intArrayOf(PackageManager.PERMISSION_GRANTED))
            host.status("late status fixture", false)
            staleRestart()
            host.close()
        }
        assertEquals(before, hostState(host))
        assertEquals(0, ComposeCrashRecoveryIoShadows.Exporter.calls.get())
        assertEquals(1, ComposeCrashRecoveryIoShadows.Uploads.calls.get())
        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals(ComposeCrashRecoveryFixtures.report, CrashReporter.latestCrashReport(activity))
    }

    @Test @Config(sdk = [29])
    fun recreationLetsTheOldExportFinishWithoutUpdatingEitherClosedOrReplacementUi() {
        val oldActivity = compose.activity
        val oldHost = recoveryHost(oldActivity)
        val oldSurface = oldActivity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ComposeView
        val oldWorker = exportWorker(oldActivity)
        ComposeCrashRecoveryIoShadows.Exporter.gate = CountDownLatch(1)
        ComposeCrashRecoveryIoShadows.Exporter.result = "Download/heyboxlite/offline-fixture.txt"
        ui.touch("crash-save")
        awaitStatus("\u6b63\u5728\u4fdd\u5b58")
        compose.waitUntil(5_000) { ComposeCrashRecoveryIoShadows.Exporter.calls.get() == 1 }
        val oldStatus = hostState(oldHost)
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertTrue(oldWorker.isShutdown)
        assertFalse(oldWorker.isTerminated)
        assertFalse(oldSurface.hasComposition)
        val newHost = recoveryHost(compose.activity)
        val newStatus = hostState(newHost)
        assertFalse(newStatus.saving)
        ui.reach("crash-save").assertIsEnabled()
        ComposeCrashRecoveryIoShadows.Exporter.gate.countDown()
        assertTrue(ComposeCrashRecoveryIoShadows.Exporter.completed.await(5, TimeUnit.SECONDS))
        assertTrue(oldWorker.awaitTermination(5, TimeUnit.SECONDS))
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        assertEquals(oldStatus, hostState(oldHost))
        assertEquals(newStatus, hostState(newHost))
        assertEquals(1, ComposeCrashRecoveryIoShadows.Exporter.calls.get())
        assertEquals(ComposeCrashRecoveryFixtures.report, ComposeCrashRecoveryIoShadows.Exporter.report)
        assertEquals(2, ComposeCrashRecoveryIoShadows.Uploads.calls.get())
        assertEquals(ComposeCrashRecoveryFixtures.report, CrashReporter.latestCrashReport(compose.activity))
        assertNull(shadowOf(compose.activity).nextStartedActivity)
        ui.capture("recreated-after-export-finished")
    }

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun realSmallRoundActivityKeepsEveryActionReachableWhileWaitingForPermission() {
        ui.assertContentInDisplay()
        ui.touch("crash-save")
        ui.reach("crash-save").assertIsNotEnabled()
        ui.assertLabelComplete("\u7b49\u5f85\u5b58\u50a8\u6743\u9650")
        ui.reach("crash-restart").assertIsEnabled()
        ui.assertFullyVisible("crash-restart")
        ui.reach("crash-exit").assertIsEnabled()
        ui.assertFullyVisible("crash-exit")
        assertEquals(1, ComposeCrashRecoveryIoShadows.Uploads.calls.get())
        assertEquals(0, ComposeCrashRecoveryIoShadows.Exporter.calls.get())
        ui.capture("round-permission-pending")
        ui.touch("crash-exit", "\u9000\u51fa")
    }

    private fun recoveryHost(activity: CrashRecoveryActivity) = field(activity, "view") as ComposeCrashRecoveryHost
    private fun exportWorker(activity: CrashRecoveryActivity) = field(activity, "files") as ExecutorService
    @Suppress("UNCHECKED_CAST")
    private fun hostState(host: ComposeCrashRecoveryHost) =
        (field(host, "state") as MutableState<ComposeCrashRecoveryState>).value
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(owner)

    private fun awaitStatus(value: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(value)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(value).assertExists()
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
