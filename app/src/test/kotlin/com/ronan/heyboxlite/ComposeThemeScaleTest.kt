package com.ronan.heyboxlite

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w192dp-h192dp-round-mdpi")
class ComposeThemeScaleTest {
    @Test fun configuredMinimumRetainsTheSameEffectiveScaleAsNativeViews() {
        val session = SessionStore(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        session.setUiScale(50)
        session.setTextScale(50)
        val theme = composeThemeState(ThemeTokens.of(true, 0, 0), session.uiScale() / 100f,
            session.textScale() / 100f, session.usesRoundLayout())

        assertTrue(theme.roundScreen)
        assertTrue(session.uiScale() < 50)
        assertTrue(session.textScale() < 50)
        assertEquals(session.uiScale() / 100f, theme.uiScale, 0f)
        assertEquals(session.textScale() / 100f, theme.textScale, 0f)
    }

    @Test @Config(qualifiers = "w240dp-h320dp-mdpi")
    fun squareDeviceOnlyChangesLayoutAfterTheUserEnablesRoundMode() {
        val session = SessionStore(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        session.setRoundScreen(false)
        assertEquals(false, session.usesRoundLayout())
        session.setRoundScreen(true)
        val theme = composeThemeState(ThemeTokens.of(true, 0, 0), session.uiScale() / 100f,
            session.textScale() / 100f, session.usesRoundLayout())
        assertTrue(theme.roundScreen)
        assertEquals(session.uiScale() / 100f, theme.uiScale, 0f)
    }
}
