package com.ronan.heyboxlite

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

private const val PREVIEW_REPORT = "error: java.lang.IllegalStateException\n\n" +
    "java.lang.IllegalStateException: local preview\n" +
    "\tat example.Reader.open(Reader.kt:42)\n\tat example.Screen.render(Screen.kt:18)"

@Preview(name = "Crash square", widthDp = 240, heightDp = 320, showBackground = true)
@Composable
private fun CrashRecoverySquarePreview() = CrashRecoveryPreview(round = false)

@Preview(name = "Crash round", widthDp = 192, heightDp = 192, showBackground = true,
    device = "spec:width=192dp,height=192dp,dpi=160,isRound=true,chinSize=0dp")
@Composable
private fun CrashRecoveryRoundPreview() = CrashRecoveryPreview(round = true)

@Preview(name = "Crash large text", widthDp = 192, heightDp = 192, fontScale = 1.3f,
    device = "spec:width=192dp,height=192dp,dpi=160,isRound=true,chinSize=0dp")
@Composable
private fun CrashRecoveryLargePreview() = CrashRecoveryPreview(round = true, textScale = 1.8f)

@Preview(name = "Crash saving", widthDp = 240, heightDp = 320)
@Composable
private fun CrashRecoverySavingPreview() = CrashRecoveryPreview(round = false,
    status = "\u6b63\u5728\u4fdd\u5b58", saving = true)

@Preview(name = "Crash save failed", widthDp = 240, heightDp = 320)
@Composable
private fun CrashRecoveryFailurePreview() = CrashRecoveryPreview(round = false,
    status = "\u4fdd\u5b58\u5931\u8d25\uff0c\u65e5\u5fd7\u4ecd\u4fdd\u7559\u5728\u672c\u673a")

@Composable
private fun CrashRecoveryPreview(
    round: Boolean,
    textScale: Float = 1f,
    status: String? = null,
    saving: Boolean = false,
) {
    val state = ComposeCrashRecoveryState.fromReport(PREVIEW_REPORT)
    HeyboxComposeTheme(crashRecoveryTheme(true, 1f, textScale, round)) {
        ComposeCrashRecoveryScreen(state.copy(status = status ?: state.status, saving = saving), {}, {}, {})
    }
}
