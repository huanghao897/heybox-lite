package com.ronan.heyboxlite

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w227dp-h227dp-round-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ComposeCheckinAccountGuideUiTest : ComposeRoundLayoutHarness() {
    @Test fun serviceRegisterGuideRemainsVisibleAfterCompletionAndCanReplay() {
        val store = TutorialStore(compose.activity)
        store.complete(TutorialTopic.SERVICE_REGISTER)
        val state = TutorialState(store)
        show(theme()) {
            CompositionLocalProvider(LocalTutorialState provides state) {
                WatchPage("注册", null) { ComposeCheckinAccountGuide(TutorialTopic.SERVICE_REGISTER) }
                ComposeTutorialOverlay(state)
            }
        }
        reach("第 1 步 · 注册签到服务账号")
        assertFits("第 1 步 · 注册签到服务账号")
        reach("查看操作教程").performClick()
        text("创建签到服务账号").assertIsDisplayed()
        assertEquals(TutorialTopic.SERVICE_REGISTER, state.topic)
    }

    @Test fun officialLoginIsExplicitlyTheSecondAndSeparateAccountStep() {
        show(theme()) { WatchPage("登录", null) { ComposeCheckinAccountGuide(TutorialTopic.HEYBOX_LOGIN) } }
        reach("第 2 步 · 登录小黑盒账号")
        assertFits("第 2 步 · 登录小黑盒账号")
        val message = "填写要签到的小黑盒手机号及验证码或密码，不是签到服务账号。"
        reach(message)
        assertFits(message)
    }
}
