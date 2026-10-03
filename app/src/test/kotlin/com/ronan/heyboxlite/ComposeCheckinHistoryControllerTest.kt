package com.ronan.heyboxlite

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ComposeCheckinHistoryControllerTest {
    @Test fun readableHistoryIsIndependentOfPaidEntitlementAndKeepsKnownRewards() {
        val fixture = Fixture()
        fixture.controller.load()
        fixture.requests.single().onSuccess(history())
        assertFalse(fixture.state.historyLoading)
        val entry = fixture.state.history!!.entries.single()
        assertEquals("签到成功", checkinResultTitle(entry))
        assertEquals("盒币 +12 · 经验 +5", entry.rewardLabel())
        assertEquals(2, checkinTasks(entry).count { it.kind == CheckinResultKind.SUCCESS })
        assertEquals(CheckinResultKind.UNKNOWN, checkinTasks(entry)[2].kind)
        assertEquals(CheckinResultKind.FAILURE, checkinTasks(entry)[3].kind)
    }

    @Test fun staleHistoryCannotOverwriteNewAccountOrNewerHistory() {
        val fixture = Fixture()
        fixture.controller.load()
        val old = fixture.requests.single()
        fixture.generation++
        old.onSuccess(history())
        assertNull(fixture.state.history)
        fixture.controller.load()
        val current = fixture.requests.last()
        old.onError(error(503))
        assertTrue(fixture.state.historyError.isEmpty())
        current.onSuccess(history())
        val records = fixture.state.history
        fixture.active = false
        current.onSuccess(CheckinHistory.parse(JSONObject().put("items", JSONArray())))
        assertSame(records, fixture.state.history)
    }

    @Test fun retryErrorKeepsEarlierRecordsAndOnly401ClearsAuthorization() {
        val fixture = Fixture()
        fixture.controller.load()
        fixture.requests.single().onSuccess(history())
        val records = fixture.state.history
        fixture.controller.load()
        fixture.requests.last().onError(error(503))
        assertSame(records, fixture.state.history)
        assertFalse(fixture.state.historyLoading)
        assertTrue(fixture.lost.isEmpty())
        fixture.controller.load()
        fixture.requests.last().onError(error(401))
        assertEquals(1, fixture.lost.size)
    }

    @Test fun closingAndUnpairedDevicesDoNotReadHistory() {
        val fixture = Fixture()
        fixture.state = fixture.state.copy(paired = false)
        fixture.controller.load()
        assertTrue(fixture.requests.isEmpty())
        fixture.state = fixture.state.copy(paired = true)
        fixture.controller.load()
        fixture.controller.close()
        fixture.requests.last().onSuccess(history())
        assertNull(fixture.state.history)
    }

    @Test fun internalNavigationHasCorrectParentsAndEveryPageHasALiveIdentity() {
        for (page in ComposeCheckinRoute.entries) {
            val key = ComposeCheckinNavigation.key(page)
            assertEquals(page, ComposeCheckinNavigation.page(key))
            assertTrue(ComposeSwipePresentation.isLiveRoute(key))
        }
        assertEquals(ComposeCheckinRoute.MEMBERSHIP, ComposeCheckinNavigation.parent(ComposeCheckinRoute.CHECKOUT))
        assertEquals(ComposeCheckinRoute.HISTORY, ComposeCheckinNavigation.parent(ComposeCheckinRoute.HISTORY_DETAIL))
        assertNull(ComposeCheckinNavigation.parent(ComposeCheckinRoute.CENTER))
    }

    private class Fixture {
        var state = ComposeCheckinUiState(paired = true, status = CheckinCenterClient.Status(
            CheckinCenterClient.Account("connected", "User", "****9689"),
            CheckinCenterClient.Task(true, true, "05:30", 90, "04:00", "07:00", false, false, CheckinSharing.parse(JSONObject())),
            null, CheckinBilling.Membership("paid", true, false, false, "", true, false, CheckinBilling.Plan.empty())))
        var generation = 0
        var active = true
        val requests = ArrayList<CheckinCenterClient.Callback<CheckinHistory>>()
        val lost = ArrayList<String>()
        val controller = ComposeCheckinHistoryController(requests::add, { state }, { state = it },
            { active }, { generation }, lost::add)
    }

    private fun error(code: Int) = CheckinCenterClient.ApiError(CheckinCenterClient.Operation.HISTORY, code, "Unavailable")
    private fun history(): CheckinHistory = CheckinHistory.parse(JSONObject().put("items", JSONArray().put(
        JSONObject().put("id", 3).put("status", "ok").put("started_at", "2026-10-03T05:30:00Z")
            .put("check_in", JSONObject().put("checked_in", true).put("coin_delta", 12).put("experience_delta", 5))
            .put("details", JSONArray().put(JSONObject().put("label", "分享帖子").put("value", "已完成"))
                .put(JSONObject().put("label", "分享游戏评价").put("value", "未完成"))))))
}
