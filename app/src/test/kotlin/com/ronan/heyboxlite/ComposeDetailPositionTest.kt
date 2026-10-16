package com.ronan.heyboxlite

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ComposeDetailPositionTest {
    @Test fun accountAndPostPositionsAreIndependentAndSurviveANewRead() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ComposeDetailPosition.save(context, "account-a", "post", ComposeDetailPosition(7, 18))
        ComposeDetailPosition.save(context, "account-b", "post", ComposeDetailPosition(3, 9))
        ComposeDetailPosition.save(context, "account-a", "other", ComposeDetailPosition(4, 2))
        assertEquals(ComposeDetailPosition(7, 18), ComposeDetailPosition.restore(context, "account-a", "post"))
        assertEquals(ComposeDetailPosition(3, 9), ComposeDetailPosition.restore(context, "account-b", "post"))
        assertEquals(ComposeDetailPosition(4, 2), ComposeDetailPosition.restore(context, "account-a", "other"))
        assertFalse(ComposeDetailPosition.restore(context, "guest", "post").hasProgress)
        assertTrue(ComposeDetailPosition.restore(context, "account-a", "post").hasProgress)
    }
}
