package com.ronan.heyboxlite

import android.app.Application
import android.widget.FrameLayout
import android.view.View
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ComposeNavigationClearanceUiTest : ComposeRoundLayoutHarness() {
    @Test fun squareFeedFooterCanScrollAboveTheEnlargedIsland() = verifyClearance(profile = false)
    @Test fun squareProfileSettingsCanScrollAboveTheEnlargedIsland() = verifyClearance(profile = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192FeedFooterStaysReachable() = verifyClearance(profile = false)

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227ProfileSettingsStaysReachable() = verifyClearance(profile = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun roundMinimumUiFeedStillClearsTheIsland() = verifyClearance(profile = false, uiPercent = 50)

    @Test fun squareMinimumUiProfileStillClearsTheIsland() = verifyClearance(profile = true, uiPercent = 50)

    private fun verifyClearance(profile: Boolean, uiPercent: Int = 100) {
        var services = layoutServices()
        services.session.setUiScale(uiPercent)
        services = services.copy(theme = services.theme.copy(uiScale = services.session.uiScale() / 100f))
        var actions = 0
        val posts = (0..15).map { index ->
            FeedItem.from(JSONObject().put("linkid", "$index").put("title", "Post $index"))
        }
        val listState = LazyListState()
        showLayout(services) {
            if (profile) {
                ComposeProfileScreen(services, ComposeProfileState()) { actions++ }
            } else {
                ComposeFeedScreen(posts, false, false, false, services, {}, {}, { actions++ }, {},
                    { _, _ -> }, listState, observeLoadMore = false)
            }
        }
        lateinit var island: View
        compose.runOnIdle {
            val parent = compose.activity.findViewById<FrameLayout>(android.R.id.content)
            val dock = BottomNavigationController(compose.activity, services.session, ThemeTokens.of(true, 0, 0),
                services.theme.roundScreen, parent)
            dock.addItem("主页", "feed", R.drawable.ic_nav_home) {}
            dock.addItem("我的", "profile", R.drawable.ic_nav_profile) {}
            dock.select(if (profile) "profile" else "feed")
            dock.setVisible(true, false, false)
            island = parent.getChildAt(parent.childCount - 1)
        }
        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        val label = if (profile) "设置" else "上滑加载更多"
        val target = compose.onNodeWithText(label)
        compose.runOnIdle {
            val node = target.fetchSemanticsNode()
            val bottom = node.positionInWindow.y + node.size.height
            val location = IntArray(2).also(island::getLocationInWindow)
            assertTrue("$label must end above the actual native navigation island: $bottom / ${location[1]}",
                bottom < location[1])
            captureMembershipUi(compose.activity, "dock-clearance-${if (profile) "profile" else "feed"}-$uiPercent")
        }
        assertTouchable(target, label)
        target.performClick()
        compose.runOnIdle { assertEquals(1, actions) }
    }
}
