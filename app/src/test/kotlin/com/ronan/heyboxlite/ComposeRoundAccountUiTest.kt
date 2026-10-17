package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ComposeRoundAccountUiTest : ComposeRoundLayoutHarness() {
    @Test fun squareAccountKeepsItsIdentityReadable() = verifyIdentity()
    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192AccountKeepsItsIdentityReadable() = verifyIdentity()
    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227LargeTextAccountKeepsItsFullId() = verifyIdentity(textScale = 1.4f)
    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun forcedRoundAccountKeepsItsIdentityReadable() = verifyIdentity(forceRound = true)

    private fun verifyIdentity(forceRound: Boolean = false, textScale: Float = 1f) {
        val services = layoutServices(forceRound, textScale = textScale)
        val name = "\u6d4b\u8bd5\u7528\u6237"
        val id = "ID 12345678901"
        showLayout(services) {
            WatchPage("\u6211\u7684", null, showPrimaryHeader = true) {
                WatchCard {
                    ComposeAccountIdentity(name, id) {
                        Box(Modifier.size(watchDp(46)).background(services.theme.muted, CircleShape))
                    }
                }
            }
        }
        assertPaintedLabelInsideDisplay("\u6211\u7684")
        listOf(name, id).forEach {
            circleReach(it)
            assertPaintedLabelInsideDisplay(it)
        }
    }

    @Test fun squareLoginQrRemainsSquareAndScannable() = verifyQr()
    @Test @Config(qualifiers = "w160dp-h240dp-mdpi")
    fun narrowSquareLoginQrRemainsSquareAndScannable() = verifyQr()
    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192LoginQrRemainsInsideTheDisplayAndScannable() = verifyQr()
    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227LoginQrRemainsInsideTheDisplayAndScannable() = verifyQr()
    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun forcedRoundLoginQrUsesCircularGeometry() = verifyQr(forceRound = true)

    private fun verifyQr(forceRound: Boolean = false) {
        val services = layoutServices(forceRound)
        val payload = "https://fixture.invalid/qr-login"
        val qr = QrCode.create(payload, 620)
        var guests = 0
        var retries = 0
        try {
            showLayout(services) {
                ComposeLoginContent(qr, "\u7b49\u5f85\u626b\u7801", {}, { guests++ }, { retries++ })
            }
            val frame = circleReach(compose.onNodeWithTag("login-qr-frame"))
            assertNoTopToolbar()
            text("\u767b\u5f55\u5c0f\u9ed1\u76d2").assertDoesNotExist()
            assertControlInsideDisplay(frame, "login QR frame")
            val image = compose.onNodeWithContentDescription("\u767b\u5f55\u4e8c\u7ef4\u7801")
            val rect = bounds(image)
            assertEquals(rect.width, rect.height, 0.5f)
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                val capture = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                try {
                    view.draw(Canvas(capture))
                    val width = rect.width.toInt()
                    val height = rect.height.toInt()
                    val pixels = IntArray(width * height)
                    capture.getPixels(pixels, 0, width, rect.left.toInt(), rect.top.toInt(), width, height)
                    val binary = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels)))
                    assertEquals(payload, MultiFormatReader().decode(binary).text)
                } finally { capture.recycle() }
            }
            touchReachable("\u91cd\u65b0\u83b7\u53d6")
            touchReachable("\u6e38\u5ba2\u6d4f\u89c8")
            compose.runOnIdle {
                assertEquals(1, guests)
                assertEquals(1, retries)
            }
        } finally { qr.recycle() }
    }
}
