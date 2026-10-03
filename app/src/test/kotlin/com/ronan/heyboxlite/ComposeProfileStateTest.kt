package com.ronan.heyboxlite

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeProfileStateTest {
    private val local = ComposeProfileState.cachedIdentity(true, "42", "Local name", "local-avatar")

    @Test
    fun cachedIdentityShowsAccountBeforeAResponse() {
        assertTrue(local.loggedIn)
        assertEquals("42", local.userId)
        assertEquals("Local name", local.name)
        assertEquals("local-avatar", local.avatar)
        assertFalse(local.hasProfile)
    }

    @Test
    fun guestNeverKeepsSavedAccountIdentity() {
        val guest = ComposeProfileState.cachedIdentity(false, "42", "Saved name", "saved-avatar")

        assertEquals(ComposeProfileState(), guest)
        assertSame(guest, guest.withProfile(body("account_detail", account()), guest))
    }

    @Test
    fun officialAccountDetailRestoresIdentityStatsAndTrimmedSignature() {
        val updated = local.withProfile(body("account_detail", account()), local)

        assertEquals("42", updated.userId)
        assertEquals("Remote name", updated.name)
        assertEquals("remote-avatar", updated.avatar)
        assertTrue(updated.hasProfile)
        assertEquals(19, updated.follows)
        assertEquals(3, updated.fans)
        assertEquals(34, updated.likes)
        assertEquals("Remote signature", updated.signature)
    }

    @Test
    fun nestedUserNestedProfileAndRootUserUseTheSameParser() {
        val expected = local.withProfile(body("account_detail", account()), local)
        val bodies = listOf(
            body("user", account()),
            body("profile", account()),
            JSONObject().put("user", account()),
        )

        bodies.forEach { assertEquals(expected, local.withProfile(it, local)) }
    }

    @Test
    fun blankRemoteIdentityFallsBackToCurrentSession() {
        val currentLocal = local.copy(name = "Updated local", avatar = "updated-local-avatar")
        val account = JSONObject()
            .put("username", "   ")
            .put("avatar", JSONObject.NULL)
            .put("signature", " \n ")
        val updated = local.withProfile(body("account_detail", account), currentLocal)

        assertEquals("Updated local", updated.name)
        assertEquals("updated-local-avatar", updated.avatar)
        assertEquals("", updated.signature)
        assertTrue(updated.hasProfile)
        assertEquals(0, updated.follows)
        assertEquals(0, updated.fans)
        assertEquals(0, updated.likes)
    }

    @Test
    fun missingBodyOrAccountPreservesCachedDetails() {
        val cached = local.withProfile(body("account_detail", account()), local)

        assertSame(cached, cached.withProfile(null, local))
        assertSame(cached, cached.withProfile(JSONObject(), local))
        assertSame(cached, cached.withProfile(JSONObject().put("result", JSONObject()), local))
    }

    @Test
    fun receivedLikesPreferOfficialFieldEvenWhenItIsZero() {
        val account = JSONObject()
            .put("like_num", 99)
            .put("bbs_info", JSONObject().put("be_favoured_num", 0))
        val updated = local.withProfile(body("account_detail", account), local)

        assertEquals(0, updated.likes)
    }

    @Test
    fun responseCannotMergeIntoADifferentAccountOrGuest() {
        val other = ComposeProfileState.cachedIdentity(true, "77", "Other name", "other-avatar")
        val body = body("account_detail", account())

        assertSame(other, local.withProfile(body, other))
        assertEquals(ComposeProfileState(), local.withProfile(body, ComposeProfileState()))
    }

    private fun body(key: String, account: JSONObject): JSONObject =
        JSONObject().put("result", JSONObject().put(key, account))

    private fun account(): JSONObject = JSONObject()
        .put("username", "Remote name")
        .put("avatar", "remote-avatar")
        .put("signature", "  Remote signature  ")
        .put("bbs_info", JSONObject()
            .put("follow_num", 19)
            .put("fan_num", 3)
            .put("be_favoured_num", 34))
}
