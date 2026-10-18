package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The dock used to be a LinearLayout; it is Compose now, so these tests verify
 * the rendered capsule instead of per-ImageView widget state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w192dp-h192dp-round-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeBottomNavigationUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @After fun restoreMotion() { Motions.setLevel(MotionLevel.REDUCED) }

    @Test fun round192NormalScaleRendersAndAcceptsPaddedTargetTaps() = verifyDock(192, 192, true, 100)

    @Test fun round192MinimumScaleRendersAndAcceptsPaddedTargetTaps() =
        verifyDock(192, 192, true, SessionStore.MIN_UI_SCALE)

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227NormalScaleRendersAndAcceptsPaddedTargetTaps() = verifyDock(227, 227, true, 100)

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227MinimumScaleRendersAndAcceptsPaddedTargetTaps() =
        verifyDock(227, 227, true, SessionStore.MIN_UI_SCALE)

    @Test @Config(qualifiers = "w240dp-h320dp-mdpi")
    fun square240x320NormalScaleRendersAndAcceptsPaddedTargetTaps() = verifyDock(240, 320, false, 100)

    @Test @Config(qualifiers = "w240dp-h320dp-mdpi")
    fun square240x320MinimumScaleRendersAndAcceptsPaddedTargetTaps() =
        verifyDock(240, 320, false, SessionStore.MIN_UI_SCALE)

    @Test @Config(qualifiers = "w192dp-h192dp-round-xhdpi")
    fun denseRound192NormalScaleUsesPhysicalPixels() = verifyDock(192, 192, true, 100)

    @Test @Config(qualifiers = "w192dp-h192dp-round-xhdpi")
    fun denseRound192MinimumScaleUsesPhysicalPixels() =
        verifyDock(192, 192, true, SessionStore.MIN_UI_SCALE)

    @Test @Config(qualifiers = "w240dp-h320dp-xhdpi")
    fun mountedIslandUpdatesGeometryAndIconsAfterDisplaySettingsChange() {
        val activity = compose.activity
        shadowOf(activity.packageManager).setSystemFeature("android.hardware.type.watch", true)
        val session = SessionStore(activity).apply { setUiScale(100); setRoundScreen(false) }
        val tokens = ThemeTokens.of(true, 0, 0)
        val root = FrameLayout(activity)
        compose.setContent { AndroidView(factory = { root }, modifier = Modifier.fillMaxSize()) }
        lateinit var navigation: BottomNavigationController
        compose.runOnIdle {
            navigation = BottomNavigationController(activity, session, tokens, false, root)
            navigation.addItem("Feed", "feed", R.drawable.ic_nav_home) {}
            navigation.setVisible(true, false, false)
        }
        compose.waitForIdle()
        val originalWidth = navigation.view.width
        compose.runOnIdle {
            session.setUiScale(60)
            session.setRoundScreen(true)
            navigation.applyTheme(tokens, session.usesRoundLayout())
        }
        compose.waitForIdle()
        val metrics = activity.resources.displayMetrics
        val scale = session.uiScale() / 100f
        val expected = ResponsiveDock.fromScreen(metrics.widthPixels, metrics.heightPixels, true, scale)
        assertTrue(navigation.view.width < originalWidth)
        assertEquals(expected.width, navigation.view.width)
        assertEquals(expected.height, navigation.view.height)
        assertEquals(expected.marginBottom, (navigation.view.layoutParams as FrameLayout.LayoutParams).bottomMargin)
        assertEquals(expected.iconSize, navigation.state.dimensions.iconSize)
        assertEquals(scale, navigation.state.theme.uiScale, 0f)
        assertTrue(navigation.state.theme.roundScreen)
        compose.onNodeWithTag("bottom-nav-feed").assertIsDisplayed().assertHasClickAction()
    }

    private fun verifyDock(widthDp: Int, heightDp: Int, round: Boolean, configuredScale: Int) {
        val activity = compose.activity
        shadowOf(activity.packageManager).setSystemFeature("android.hardware.type.watch", true)
        val session = SessionStore(activity).apply { setUiScale(configuredScale) }
        assertEquals(round, session.usesRoundLayout())
        assertTrue(RoundLayoutMetrics.isWatchDisplay(activity))
        Motions.setLevel(MotionLevel.OFF)
        val tokens = ThemeTokens.of(true, 0, 0)
        val root = FrameLayout(activity).apply { setBackgroundColor(tokens.background) }
        val clicks = mutableListOf<String>()
        // Hosting the shell inside the rule's composition keeps a single owner tree for
        // both the dock ComposeView and the semantics queries below.
        compose.setContent { AndroidView(factory = { root }, modifier = Modifier.fillMaxSize()) }
        lateinit var navigation: BottomNavigationController
        compose.runOnIdle {
            navigation = BottomNavigationController(activity, session, tokens, round, root)
            navigation.addItem("Feed", "feed", R.drawable.ic_nav_home) { clicks += "feed" }
            navigation.addItem("Profile", "profile", R.drawable.ic_nav_profile) {
                clicks += "profile"
            }
        }
        compose.waitForIdle()
        val dock: View = root.getChildAt(0)
        assertEquals(View.GONE, dock.visibility)
        compose.runOnIdle {
            navigation.select("feed")
            navigation.setVisible(true, false, false)
        }
        compose.waitForIdle()

        val density = activity.resources.displayMetrics.density
        val width = Math.round(widthDp * density)
        val height = Math.round(heightDp * density)
        assertEquals(width, root.width)
        assertEquals(height, root.height)
        val expected = ResponsiveDock.fromScreen(width, height, round, session.uiScale() / 100f)
        val frame = bounds(root, dock)
        assertEquals(expected.width, frame.width())
        assertEquals(expected.height, frame.height())
        assertEquals(expected.marginBottom, height - frame.bottom)
        assertEquals((width - expected.width) / 2, frame.left)
        assertEquals(expected.height + expected.marginBottom +
            UiComponents.dp(activity, 8, session.uiScale() / 100f), navigation.bottomSafeSpace())
        assertTrue(navigation.bottomSafeSpace() > height - frame.top)
        if (round) {
            assertInCircle(frame, width, height)
            assertTrue("The enlarged dock must stay close to the bottom",
                height - frame.bottom <= ceil(width * 0.04f))
        }

        compose.onNodeWithTag("bottom-nav-feed").assertIsDisplayed().assertIsSelected()
            .assertHasClickAction()
        compose.onNodeWithTag("bottom-nav-profile").assertIsDisplayed().assertIsNotSelected()

        val screenshot = (if (round) "round" else "square") + "-" + widthDp + "x" + heightDp +
            "-ui" + configuredScale + "-" + width + "x" + height + "px"
        capture(root, tokens, round, "$screenshot-feed")

        var previous: Rect? = null
        val iconSize = expected.iconSize.toFloat()
        for (key in listOf("feed", "profile")) {
            val node = compose.onNodeWithTag("bottom-nav-$key")
            val target = dockTarget(root, dock, key)
            assertTrue(target.width() > expected.iconSize)
            assertTrue(target.height() > expected.iconSize)
            if (previous != null) assertFalse("Navigation targets cannot overlap",
                Rect.intersects(previous, target))
            previous = target
            if (round) assertInCircle(target, width, height)
            for (corner in listOf(Offset(0.5f, 0.5f),
                Offset(target.width() - 0.5f, 0.5f),
                Offset(0.5f, target.height() - 0.5f),
                Offset(target.width() - 0.5f, target.height() - 0.5f))) {
                val before = clicks.size
                node.performTouchInput { click(corner) }
                compose.waitForIdle()
                assertEquals("One action per physical tap", before + 1, clicks.size)
                assertEquals(key, clicks[before])
            }
            val localTarget = Rect(target).apply { offset(-frame.left, -frame.top) }
            assertGlyphInk(dock, localTarget, iconSize, key, active = key == "feed")
        }
        assertEquals(8, clicks.size)

        compose.runOnIdle { navigation.select("profile") }
        compose.waitForIdle()
        compose.onNodeWithTag("bottom-nav-profile").assertIsSelected()
        compose.onNodeWithTag("bottom-nav-feed").assertIsNotSelected()
        capture(root, tokens, round, "$screenshot-profile")

        compose.runOnIdle { navigation.setVisible(false, false, false) }
        compose.waitForIdle()
        assertEquals(View.GONE, dock.visibility)
        val before = clicks.size
        dispatch(root, MotionEvent.ACTION_DOWN, frame.exactCenterX(), frame.exactCenterY())
        dispatch(root, MotionEvent.ACTION_UP, frame.exactCenterX(), frame.exactCenterY())
        shadowOf(android.os.Looper.getMainLooper()).idle()
        compose.waitForIdle()
        assertEquals("A hidden island must not retain active touch targets", before, clicks.size)

        compose.runOnIdle {
            navigation.setVisible(true, false, false)
            navigation.finishMotion()
        }
        assertEquals(1.0f, dock.alpha, 0.0f)
        assertEquals(0.0f, dock.translationY, 0.0f)
    }

    /** Compose bounds are root-relative; offset by the dock view so both share shell coordinates. */
    private fun dockTarget(root: View, dock: View, key: String): Rect {
        val origin = bounds(root, dock)
        val bounds = compose.onNodeWithTag("bottom-nav-$key").fetchSemanticsNode().boundsInRoot
        return Rect(origin.left + bounds.left.roundToInt(), origin.top + bounds.top.roundToInt(),
            origin.left + bounds.right.roundToInt(), origin.top + bounds.bottom.roundToInt())
    }

    private fun assertGlyphInk(dock: View, target: Rect, iconSize: Float, key: String,
                              active: Boolean) {
        val center = Offset(target.exactCenterX(), target.exactCenterY())
        val half = iconSize / 2f
        val bitmap = render(dock)
        try {
            val ink = inkCoverage(bitmap, center, half)
            assertTrue("Icon $key must paint meaningful scaled ink: $ink", ink >= 0.08)
            if (!active) {
                // The outline glyph must remain distinct from a filled selection surface.
                assertTrue("Inactive icon $key must stay subdued: $ink", ink < 0.45)
            }
            val surface = bitmap.getPixel((center.x - half * 1.6f).toInt(), center.y.toInt())
            val flat = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            try {
                flat.eraseColor(surface)
                assertEquals("Selection fill alone must not count as icon ink", 0.0,
                    inkCoverage(flat, center, half), 0.001)
            } finally {
                flat.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun inkCoverage(bitmap: Bitmap, center: Offset, half: Float): Double {
        var ink = 0
        var area = 0
        for (y in (center.y - half).toInt() until (center.y + half).toInt()) {
            for (x in (center.x - half).toInt() until (center.x + half).toInt()) {
                area++
                // Icons render at 99/132 gray on a ~33 surface: count clearly brighter pixels.
                if (Color.red(bitmap.getPixel(x, y)) > 70) ink++
            }
        }
        return if (area == 0) 0.0 else ink.toDouble() / area
    }

    private fun capture(root: FrameLayout, tokens: ThemeTokens, round: Boolean, name: String) {
        val bitmap = render(root)
        try {
            val file = File("build/outputs/ui-regression/dock-$name.png")
            assertTrue(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
            FileOutputStream(file).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            println("Compose dock screenshot: " + file.absolutePath)
            val frame = bounds(root, root.getChildAt(0))
            val paletteChroma = listOf(tokens.background, tokens.dockSurface(), tokens.text, tokens.subtle,
                tokens.navSelection()).maxOf { color ->
                maxOf(Color.red(color), Color.green(color), Color.blue(color)) -
                    minOf(Color.red(color), Color.green(color), Color.blue(color))
            }
            var painted = 0
            for (y in 0 until bitmap.height) {
                for (x in 0 until bitmap.width) {
                    val pixel = bitmap.getPixel(x, y)
                    if (pixel == tokens.background) continue
                    val red = Color.red(pixel)
                    val green = Color.green(pixel)
                    val blue = Color.blue(pixel)
                    assertTrue("Dock must retain the neutral black/gray palette",
                        maxOf(red, green, blue) - minOf(red, green, blue) <= paletteChroma + 1)
                    if (frame.contains(x, y)) painted++
                    if (round) {
                        val dx = x + 0.5 - bitmap.width / 2.0
                        val dy = y + 0.5 - bitmap.height / 2.0
                        val radius = bitmap.width / 2.0
                        assertTrue("Rendering crosses the circular edge: $x,$y",
                            hypot(dx, dy) <= radius)
                    }
                }
            }
            assertTrue("Compose capsule rendering must not be blank",
                painted > frame.width() * frame.height() / 2)
        } finally {
            bitmap.recycle()
        }
    }

    private fun render(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun bounds(root: View, view: View): Rect {
        val location = IntArray(2).also(view::getLocationInWindow)
        val origin = IntArray(2).also(root::getLocationInWindow)
        val left = location[0] - origin[0]
        val top = location[1] - origin[1]
        return Rect(left, top, left + view.width, top + view.height)
    }

    private fun assertInCircle(rect: Rect, width: Int, height: Int) {
        val radius = minOf(width, height) / 2.0
        for (x in intArrayOf(rect.left, rect.right)) {
            for (y in intArrayOf(rect.top, rect.bottom)) {
                val dx = x - width / 2.0
                val dy = y - height / 2.0
                assertTrue("Hit bounds outside circular display: $rect",
                    hypot(dx, dy) <= radius)
            }
        }
    }

    private fun dispatch(root: FrameLayout, action: Int, x: Float, y: Float): Boolean {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x, y, 0)
        return try {
            root.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }
}
