package com.ronan.heyboxlite

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * The watch UI has a single type scale. This guards the Compose sources against
 * a new one-off font size creeping back in.
 */
class ComposeTypeScaleTest {
    /** Sizes the scale is built from; keep in sync with [WatchType]. */
    private val steps = setOf(11f, 12f, 13f, 15f, 17f, 22f)

    /** Pinned corner keeps its rotated 7.5sp label; reading typography follows its own settings. */
    private val exempt = setOf("ComposeCommentPinnedCorner.kt", "ComposeReadingStyle.kt")

    @Test
    fun everyFontSizeUsesAStepOfTheWatchScale() {
        val numberInFontSize = Pattern.compile(
            """fontSize\s*=\s*(?:\(?\s*(?:if\s*\([^)]*\)\s*)?)?\(?\s*(\d+(?:\.\d+)?)""")
        val watchSpLiteral = Pattern.compile("""watchSp\(\s*(\d+(?:\.\d+)?)f?\s*\)""")
        val sourceRoot = File("src/main/kotlin/com/ronan/heyboxlite")
        assertTrue("Kotlin sources are missing: ${sourceRoot.absolutePath}", sourceRoot.isDirectory)
        val offenders = mutableListOf<String>()
        sourceRoot.listFiles { file -> file.isFile && file.name.endsWith(".kt") }
            .orEmpty()
            .filter { it.name !in exempt }
            .sortedBy { it.name }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    val sizes = mutableListOf<Float>()
                    numberInFontSize.matcher(line).let { matcher ->
                        if (matcher.find()) sizes += matcher.group(1)!!.toFloat()
                    }
                    watchSpLiteral.matcher(line).let { matcher ->
                        while (matcher.find()) sizes += matcher.group(1)!!.toFloat()
                    }
                    sizes.filter { it !in steps }.forEach { size ->
                        offenders += "${file.name}:${index + 1} uses ${size}sp: ${line.trim()}"
                    }
                }
            }
        assertTrue(
            "Font sizes must be a WatchType step (${steps.sorted().joinToString("/")}):\n"
                + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}
