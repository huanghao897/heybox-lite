package com.ronan.heyboxlite

import android.app.Application
import androidx.compose.foundation.lazy.LazyListState
import java.io.Closeable
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, shadows = [DetailLoadCompletionApiShadow::class])
class ComposeRouteResourcesTest {
    @Test fun nestedParentsKeepTheirOwnLiveResourcesUntilPopped() {
        Fixture().use { f ->
            val navigation = ComposeNavigationState()
            navigation.showDetailLoading(post("parent"))
            val parentRoute = navigation.presentationRoute()
            val parentList = f.resources.listState(parentRoute, navigation, "")
            val authorRoute = "user_space?user=author&name=Author"
            navigation.navigate(authorRoute)
            val author = f.resources.userSpace(composeUserSpaceRoute(authorRoute))
            navigation.showDetailLoading(post("child"))
            val childRoute = navigation.presentationRoute()
            val childList = f.resources.listState(childRoute, navigation, "")

            f.resources.retain(navigation.retainedContentRoutes())

            assertTrue(navigation.retainedContentRoutes().containsAll(listOf(parentRoute, authorRoute, childRoute)))
            assertSame(parentList, f.resources.listState(parentRoute, navigation, ""))
            assertSame(childList, f.resources.listState(childRoute, navigation, ""))
            assertSame(author, f.resources.userSpace(composeUserSpaceRoute(authorRoute)))
            assertEquals(1, f.requests.size)
        }
    }

    @Test fun leavingTheContentStackClosesAuthorsAndIgnoresTheirLateResponses() {
        Fixture().use { f ->
            val route = composeUserSpaceRoute("user_space?user=author&name=Author")
            val author = f.resources.userSpace(route)
            val pending = f.requests.single().callback

            f.resources.retain(setOf("feed"))
            pending.onSuccess(JSONObject().put("result", JSONObject()
                .put("user", JSONObject().put("username", "Late data"))))

            assertEquals("Author", author.state.value.name)
            assertTrue(author.state.value.loading)
            assertNotSame(author, f.resources.userSpace(route))
            assertEquals(2, f.requests.size)
        }
    }

    @Test fun removedDetailPositionsAreNotRetainedBeyondTheirReturnStack() {
        Fixture().use { f ->
            val navigation = ComposeNavigationState()
            navigation.showDetailLoading(post("parent"))
            val parent = navigation.detail.value!!
            val route = navigation.presentationRoute()
            val old = f.resources.listState(route, navigation, "")
            assertTrue(navigation.restoreSavedDetailIfNeeded("feed"))

            f.resources.retain(navigation.retainedContentRoutes())
            navigation.detail.value = parent
            navigation.setRoute("detail")

            assertNotSame(old, f.resources.listState(route, navigation, ""))
            assertSame(f.feed, f.resources.listState("feed", navigation, ""))
        }
    }

    @Test fun clearingTheAccountClosesEveryRetainedAuthorButPreservesFeedOwnership() {
        Fixture().use { f ->
            val route = composeUserSpaceRoute("user_space?user=author")
            val old = f.resources.userSpace(route)
            f.resources.clear()
            assertNotSame(old, f.resources.userSpace(route))
            assertSame(f.feed, f.resources.listState("feed", ComposeNavigationState(), ""))
        }
    }

    private class Fixture : Closeable {
        private val context = RuntimeEnvironment.getApplication()
        private val api = ApiClient(SessionStore(context))
        val requests = Shadow.extract<DetailLoadCompletionApiShadow>(api).requests
        val feed = LazyListState()
        val resources = ComposeRouteResources(api, LocalCache(context), feed)

        override fun close() {
            resources.clear()
            api.close()
        }
    }

    private fun post(id: String) = FeedItem.from(JSONObject().put("linkid", id))
}
