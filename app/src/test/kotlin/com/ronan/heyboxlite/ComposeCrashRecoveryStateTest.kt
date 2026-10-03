package com.ronan.heyboxlite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeCrashRecoveryStateTest {
    @Test fun shortSummaryKeepsTheRecordedErrorWithoutReportMetadata() {
        val state = ComposeCrashRecoveryState.fromReport(ComposeCrashRecoveryFixtures.report)
        assertEquals("java.lang.IllegalStateException", state.summary)
        assertFalse(state.summary.contains("version:"))
        assertFalse(state.saving)
    }

    @Test fun longSummaryIsBoundedByTheLegacy160CharacterLimitAndMarker() {
        val error = "X".repeat(4000)
        val state = ComposeCrashRecoveryState.fromReport("error: $error\n\tat local.Frame.run(Frame.kt:1)")
        assertEquals("X".repeat(160) + "...", state.summary)
        assertEquals(163, state.summary.length)
    }

    @Test fun emptyAndHeaderlessReportsKeepTheLegacyFallbacks() {
        assertEquals("\u5f02\u5e38\u4fe1\u606f\u5df2\u4fdd\u5b58\u5728\u672c\u673a",
            ComposeCrashRecoveryState.fromReport(null).summary)
        assertEquals("", ComposeCrashRecoveryState.fromReport("").stack)
        assertEquals("\u4e0a\u6b21\u8fd0\u884c\u610f\u5916\u7ed3\u675f",
            ComposeCrashRecoveryState.fromReport("unstructured local report").summary)
    }

    @Test fun previewRetainsFramesButNotRecentEventsOrMetadata() {
        val state = ComposeCrashRecoveryState.fromReport(ComposeCrashRecoveryFixtures.report)
        assertTrue(state.stack.contains("local.Reader.open(Reader.kt:42)"))
        assertFalse(state.stack.contains("version:"))
        assertFalse(state.stack.contains("recent events:"))
        assertFalse(state.stack.contains("LOCAL_BREADCRUMB_NOT_STACK"))
    }

    @Test fun twelveShortLinesFitExactlyWithoutATruncationMarker() {
        val report = "error: LocalError\n" + (1..12).joinToString("\n") { "\tat local.Frame.$it" }
        val stack = ComposeCrashRecoveryState.fromReport(report).stack
        assertEquals(12, stack.lines().size)
        assertTrue(stack.endsWith("local.Frame.12"))
        assertFalse(stack.endsWith("..."))
    }

    @Test fun longStackIncludesItsMarkerInsideBothLineAndCharacterBudgets() {
        val stack = ComposeCrashRecoveryState.fromReport(ComposeCrashRecoveryFixtures.longReport).stack
        assertTrue(stack.lines().size <= ComposeCrashRecoveryText.STACK_LINE_LIMIT)
        assertTrue(stack.length <= ComposeCrashRecoveryText.STACK_CHARACTER_LIMIT)
        assertTrue(stack.endsWith("\n..."))
        assertFalse(stack.contains("Frame.60"))
    }

    @Test fun singleHugeLineDoesNotSplitASurrogateAtTheCharacterLimit() {
        val frame = "\tat " + "a".repeat(1191) + "\uD83D\uDE00" + "tail".repeat(1000)
        val stack = ComposeCrashRecoveryText.stackPreview("error: LocalError\n$frame")
        assertTrue(stack.length <= ComposeCrashRecoveryText.STACK_CHARACTER_LIMIT)
        assertTrue(stack.endsWith("\n..."))
        val content = stack.removeSuffix("\n...")
        assertFalse(content.last().isHighSurrogate())
        assertFalse(content.contains('\uFFFD'))
    }

    @Test fun headerlessFramesAreStillAvailableWithoutTreatingMetadataAsAStack() {
        val stack = ComposeCrashRecoveryText.stackPreview(
            "version: local\nprocess: local\n\tat local.Frame.run(Frame.kt:1)\nCaused by: LocalCause")
        assertEquals("\tat local.Frame.run(Frame.kt:1)\nCaused by: LocalCause", stack)
    }

    @Test fun previewDoesNotMutateTheCompleteReportUsedForExport() {
        val report = ComposeCrashRecoveryFixtures.longReport
        val original = report.toCharArray()
        ComposeCrashRecoveryState.fromReport(report)
        assertEquals(String(original), report)
        assertTrue(report.contains("Frame.60"))
    }
}

internal object ComposeCrashRecoveryFixtures {
    const val report = "heybox Lite crash report\nversion: local fixture\n" +
        "process: local\nerror: java.lang.IllegalStateException\n\n" +
        "java.lang.IllegalStateException: offline fixture\n" +
        "\tat local.Reader.open(Reader.kt:42)\n\tat local.Screen.render(Screen.kt:18)\n" +
        "recent events:\nLOCAL_BREADCRUMB_NOT_STACK"
    val longReport = "error: java.lang.IllegalStateException\n\n" +
        (1..60).joinToString("\n") { "\tat local.Frame.$it(Frame.kt:$it)" }
}
