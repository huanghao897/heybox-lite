package com.ronan.heyboxlite

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeProfileControllerTest {
    @Test
    fun creationOnlySeedsLocalIdentityAndDoesNotRequest() {
        val fixture = Fixture()

        assertEquals(fixture.identity, fixture.controller.state.value)
        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun refreshUsesOriginalEndpointAndOnlyOnePost() {
        val fixture = Fixture()
        fixture.controller.refresh()
        val request = fixture.requests.single()

        assertEquals("/bbs/app/profile/user/link/list", request.path)
        assertEquals(OfficialRequestParams.profileLinks("42", 0, 1), request.params)
        assertEquals("42", request.params[SecureStrings.userid()])
        assertEquals("0", request.params["offset"])
        assertEquals("1", request.params["limit"])
        assertEquals(fixture.identity, fixture.controller.state.value)
    }

    @Test
    fun concurrentRefreshesShareOneRequestAndLaterEntryCanRefresh() {
        val fixture = Fixture()
        repeat(3) { fixture.controller.refresh() }
        assertEquals(1, fixture.requests.size)

        fixture.requests.single().callback.onSuccess(profile())
        val cached = fixture.controller.state.value
        fixture.controller.refresh()

        assertEquals(2, fixture.requests.size)
        assertSame(cached, fixture.controller.state.value)
    }

    @Test
    fun failureIsSilentKeepsCachedDetailsAndAllowsRetry() {
        val fixture = Fixture()
        fixture.controller.refresh()
        fixture.requests.single().callback.onSuccess(profile())
        val cached = fixture.controller.state.value
        fixture.controller.refresh()
        fixture.requests.last().callback.onError("offline")

        assertSame(cached, fixture.controller.state.value)
        fixture.controller.refresh()
        assertEquals(3, fixture.requests.size)
    }

    @Test
    fun missingProfileKeepsLocalIdentityAndDoesNotAutomaticallyRetry() {
        val fixture = Fixture()
        fixture.controller.refresh()
        fixture.requests.single().callback.onSuccess(JSONObject())

        assertEquals(fixture.identity, fixture.controller.state.value)
        assertEquals(1, fixture.requests.size)
        assertFalse(fixture.controller.state.value.hasProfile)
    }

    @Test
    fun invalidateReseedsLocalIdentityWithoutStartingARequest() {
        val fixture = Fixture()
        fixture.controller.refresh()
        fixture.requests.single().callback.onSuccess(profile())
        fixture.identity = fixture.identity.copy(name = "Updated local", avatar = "updated-avatar")
        fixture.controller.invalidate()

        assertEquals(fixture.identity, fixture.controller.state.value)
        assertFalse(fixture.controller.state.value.hasProfile)
        assertEquals(1, fixture.requests.size)
    }

    @Test
    fun missingResponseIdentityUsesSessionAsItIsWhenTheResponseArrives() {
        val fixture = Fixture()
        fixture.controller.refresh()
        fixture.identity = fixture.identity.copy(name = "Updated local", avatar = "updated-avatar")
        fixture.requests.single().callback.onSuccess(
            JSONObject().put("result", JSONObject().put("account_detail", JSONObject())),
        )

        assertEquals("Updated local", fixture.controller.state.value.name)
        assertEquals("updated-avatar", fixture.controller.state.value.avatar)
        assertTrue(fixture.controller.state.value.hasProfile)
        assertEquals(1, fixture.requests.size)
    }

    @Test
    fun invalidateRejectsOldCallbacksWithoutReleasingNewRequest() {
        val fixture = Fixture()
        fixture.controller.refresh()
        val old = fixture.requests.single()
        fixture.controller.invalidate()
        fixture.controller.refresh()
        val current = fixture.requests.last()
        old.callback.onSuccess(profile("Old response"))
        old.callback.onError("old failure")
        fixture.controller.refresh()

        assertEquals(2, fixture.requests.size)
        assertEquals(fixture.identity, fixture.controller.state.value)
        current.callback.onSuccess(profile("Current response"))
        assertEquals("Current response", fixture.controller.state.value.name)
    }

    @Test
    fun accountSwitchImmediatelyClearsDetailsAndRejectsPreviousResponse() {
        val fixture = Fixture()
        fixture.controller.refresh()
        fixture.requests.single().callback.onSuccess(profile())
        fixture.controller.refresh()
        val old = fixture.requests.last()
        fixture.identity = ComposeProfileState.cachedIdentity(true, "77", "Other name", "other-avatar")
        fixture.controller.refresh()

        assertEquals(fixture.identity, fixture.controller.state.value)
        assertEquals("77", fixture.requests.last().params[SecureStrings.userid()])
        old.callback.onSuccess(profile("Previous account"))
        assertEquals(fixture.identity, fixture.controller.state.value)
        fixture.requests.last().callback.onSuccess(profile("New account"))
        assertEquals("77", fixture.controller.state.value.userId)
        assertEquals("New account", fixture.controller.state.value.name)
    }

    @Test
    fun logoutRejectsAnInFlightResponseEvenWithoutARefresh() {
        val fixture = Fixture()
        fixture.controller.refresh()
        fixture.identity = ComposeProfileState()
        fixture.requests.single().callback.onSuccess(profile())
        fixture.controller.refresh()

        assertEquals(ComposeProfileState(), fixture.controller.state.value)
        assertEquals(1, fixture.requests.size)
    }

    @Test
    fun guestAndMissingUserIdNeverRequest() {
        val fixture = Fixture()
        fixture.identity = ComposeProfileState()
        fixture.controller.invalidate()
        fixture.controller.refresh()
        fixture.identity = ComposeProfileState(loggedIn = true, name = "Missing ID")
        fixture.controller.refresh()

        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun closeRejectsCallbacksAndCannotRestartRequests() {
        val fixture = Fixture()
        fixture.controller.refresh()
        val pending = fixture.requests.single()
        val cached = fixture.controller.state.value
        fixture.controller.close()
        pending.callback.onSuccess(profile())
        pending.callback.onError("late failure")
        fixture.controller.invalidate()
        fixture.controller.refresh()

        assertSame(cached, fixture.controller.state.value)
        assertEquals(1, fixture.requests.size)
    }

    private class Fixture {
        var identity = ComposeProfileState.cachedIdentity(true, "42", "Local name", "local-avatar")
        val requests = ArrayList<Request>()
        val controller = ComposeProfileController(
            readIdentity = { identity },
            requestProfile = { path, params, callback -> requests.add(Request(path, params, callback)) },
        )
    }

    private data class Request(
        val path: String,
        val params: Map<String, String>,
        val callback: ApiClient.Callback,
    )

    private fun profile(name: String = "Remote name"): JSONObject = JSONObject().put(
        "result", JSONObject().put("account_detail", JSONObject()
            .put("username", name)
            .put("avatar", "remote-avatar")
            .put("signature", "Remote signature")
            .put("bbs_info", JSONObject()
                .put("follow_num", 19)
                .put("fan_num", 3)
                .put("be_favoured_num", 34))),
    )
}
