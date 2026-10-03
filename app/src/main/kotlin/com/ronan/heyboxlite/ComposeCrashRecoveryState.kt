package com.ronan.heyboxlite

internal data class ComposeCrashRecoveryState(
    val summary: String,
    val stack: String,
    val status: String = "\u65e5\u5fd7\u5df2\u4fdd\u5b58\uff0c\u5c06\u81ea\u52a8\u4e0a\u62a5",
    val saving: Boolean = false,
) {
    companion object {
        fun fromReport(report: String?): ComposeCrashRecoveryState = ComposeCrashRecoveryState(
            summary = CrashText.summary(report),
            stack = ComposeCrashRecoveryText.stackPreview(report.orEmpty()),
        )
    }
}

internal object ComposeCrashRecoveryText {
    const val STACK_CHARACTER_LIMIT = 1200
    const val STACK_LINE_LIMIT = 12
    private const val SCAN_LIMIT = 16 * 1024

    fun stackPreview(report: String): String {
        val lines = safePrefix(report, SCAN_LIMIT).lineSequence().iterator()
        val candidates = ArrayList<String>(STACK_LINE_LIMIT + 1)
        var afterError = false
        while (lines.hasNext()) {
            val line = lines.next().trimEnd('\r')
            if (line.startsWith("error: ")) {
                afterError = true
                continue
            }
            if (line.startsWith("recent events:")) break
            val stackLine = line.trimStart()
            if (!afterError && !stackLine.startsWith("at ") && !stackLine.startsWith("Caused by:")) continue
            if (line.isBlank()) continue
            candidates.add(line)
            if (candidates.size > STACK_LINE_LIMIT) break
        }
        val preview = candidates.take(STACK_LINE_LIMIT).joinToString("\n")
        val truncated = report.length > SCAN_LIMIT || candidates.size > STACK_LINE_LIMIT ||
            preview.length > STACK_CHARACTER_LIMIT
        if (!truncated || preview.isEmpty()) return preview
        // Reserve both one line and four characters for the truncation marker.
        val body = candidates.take(STACK_LINE_LIMIT - 1).joinToString("\n")
        return safePrefix(body, STACK_CHARACTER_LIMIT - 4).trimEnd('\n', '\r') + "\n..."
    }

    private fun safePrefix(value: String, limit: Int): String {
        var end = minOf(value.length, limit)
        if (end in 1 until value.length && value[end - 1].isHighSurrogate() && value[end].isLowSurrogate()) end--
        return value.take(end)
    }
}
