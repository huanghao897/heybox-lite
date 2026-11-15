package com.ronan.heyboxlite

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TutorialStateTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun offeringOrLeavingNeverMarksATutorialCompleted() {
        val store = TutorialStore(context)
        val state = TutorialState(store)
        state.offer(TutorialTopic.HOME)
        assertEquals(TutorialTopic.HOME, state.topic)
        state.leave(TutorialTopic.HOME)
        assertNull(state.topic)
        assertFalse(store.completed(TutorialTopic.HOME))
        state.offer(TutorialTopic.HOME)
        assertEquals(TutorialTopic.HOME, state.topic)
    }

    @Test fun completingPersistsOnlyTheSelectedTopicAcrossOwners() {
        val state = TutorialState(TutorialStore(context))
        state.offer(TutorialTopic.HOME)
        state.complete()
        val next = TutorialState(TutorialStore(context))
        next.offer(TutorialTopic.HOME)
        assertNull(next.topic)
        next.offer(TutorialTopic.DETAIL)
        assertEquals(TutorialTopic.DETAIL, next.topic)
    }

    @Test fun laterDefersWithinTheVisitButDoesNotPretendItWasRead() {
        val store = TutorialStore(context)
        val state = TutorialState(store)
        state.offer(TutorialTopic.HOME)
        assertTrue(state.dismiss())
        assertFalse(state.dismiss())
        state.offer(TutorialTopic.HOME)
        assertNull(state.topic)
        assertFalse(store.completed(TutorialTopic.HOME))
        val restarted = TutorialState(store)
        restarted.offer(TutorialTopic.HOME)
        assertEquals(TutorialTopic.HOME, restarted.topic)
    }

    @Test fun replayWorksForCompletedAndDeferredTopicsWithoutResettingOtherProgress() {
        val store = TutorialStore(context)
        store.complete(TutorialTopic.HOME)
        val state = TutorialState(store)
        state.offer(TutorialTopic.DETAIL)
        state.dismiss()
        state.replay(TutorialTopic.DETAIL)
        state.leave(TutorialTopic.DETAIL)
        assertEquals(TutorialTopic.DETAIL, state.topic)
        state.dismiss()
        state.replay(TutorialTopic.HOME)
        assertEquals(TutorialTopic.HOME, state.topic)
        assertTrue(store.completed(TutorialTopic.HOME))
        assertFalse(store.completed(TutorialTopic.DETAIL))
    }

    @Test fun automaticOffersCannotReplaceTheTutorialBeingRead() {
        val state = TutorialState(TutorialStore(context))
        state.offer(TutorialTopic.HOME)
        state.offer(TutorialTopic.CHECKIN)
        state.leave(TutorialTopic.CHECKIN)
        assertEquals(TutorialTopic.HOME, state.topic)
    }

    @Test fun tutorialsUseTheirOwnVersionNotTheAppVersionOrLoginState() {
        context.getSharedPreferences("usage_tutorials", Context.MODE_PRIVATE).edit()
            .putInt(TutorialTopic.HOME.key, 0).putInt(TutorialTopic.DETAIL.key, 5).commit()
        val store = TutorialStore(context)
        assertFalse(store.completed(TutorialTopic.HOME))
        assertTrue(store.completed(TutorialTopic.DETAIL))
        assertNull(TutorialCatalog.routeTopic("checkin_page_membership"))
        assertEquals(TutorialTopic.DETAIL, TutorialCatalog.routeTopic("detail?entry=2"))
    }

    @Test fun crownCommandsOnlyBelongToTheVisibleTutorial() {
        val state = TutorialState(TutorialStore(context))
        assertFalse(state.scrollRotary(48))
        state.offer(TutorialTopic.HOME)
        assertTrue(state.scrollRotary(48))
        assertEquals(48, state.rotary!!.distance)
        state.dismiss()
        state.offer(TutorialTopic.DETAIL)
        assertNull(state.rotary)
    }

    @Test fun contentExplainsBothAccountsAndTheActualPagerDirection() {
        val detail = TutorialCatalog.steps(TutorialTopic.DETAIL).joinToString { it.body }
        assertTrue(detail.contains("向左滑"))
        assertTrue(detail.contains("长按"))
        assertTrue(detail.contains("双击"))
        assertTrue(TutorialCatalog.steps(TutorialTopic.DETAIL, false).any { it.body.contains("你已关闭") })
        val login = TutorialCatalog.steps(TutorialTopic.SERVICE_LOGIN).joinToString { it.body }
        val register = TutorialCatalog.steps(TutorialTopic.SERVICE_REGISTER).joinToString { it.body }
        assertTrue(login.contains("不是小黑盒登录"))
        assertTrue(register.contains("扫码登录"))
        assertTrue(register.contains("手机号登录小黑盒"))
    }
}
