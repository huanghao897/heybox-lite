package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class CheckinResponseParserTest {
    private static final String DEVICE_TOKEN =
            "ccdevice1_abcdefghijklmnopqrstuvwxyz123456";

    @Test
    public void parsesPairingStartAndRejectsUntrustedVerificationUri() throws Exception {
        JSONObject response = new JSONObject()
                .put("device_code", "ccpair1_pending-device")
                .put("user_code", "ABCD-EFGH")
                .put("verification_uri",
                        "https://heyboxlite.xyz/checkin/lite/pair?code=ABCD-EFGH")
                .put("expires_in", 600)
                .put("interval", 3)
                .put("registration_open", true)
                .put("registration_email_required", true);

        CheckinCenterClient.PairingStart result =
                CheckinResponseParser.parsePairingStart(response);

        assertEquals("ccpair1_pending-device", result.deviceCode);
        assertEquals("ABCD-EFGH", result.userCode);
        assertEquals(600, result.expiresInSeconds);
        assertEquals(3, result.intervalSeconds);
        assertTrue(result.registrationOpen);
        assertTrue(result.registrationEmailRequired);

        response.put("verification_uri", "https://example.com/checkin/lite/pair");
        CheckinCenterClient.ApiError error = assertThrows(CheckinCenterClient.ApiError.class,
                () -> CheckinResponseParser.parsePairingStart(response));
        assertEquals(CheckinCenterClient.Operation.PAIR_START, error.operation);
        assertEquals("签到服务地址不受信任", error.getMessage());
    }

    @Test
    public void parsesRegistrationEmailSessionAndEnforcesChallengeContract() throws Exception {
        JSONObject response = new JSONObject()
                .put("challenge_id", "email_challenge_1234567890")
                .put("retry_after", 60)
                .put("expires_in", 600);

        CheckinCenterClient.RegistrationEmailSession result =
                CheckinResponseParser.parseRegistrationEmailSession(response);

        assertEquals("email_challenge_1234567890", result.challengeId);
        assertEquals(60, result.retryAfterSeconds);
        assertEquals(600, result.expiresInSeconds);

        response.put("challenge_id", "short");
        CheckinCenterClient.ApiError error = assertThrows(CheckinCenterClient.ApiError.class,
                () -> CheckinResponseParser.parseRegistrationEmailSession(response));
        assertEquals(CheckinCenterClient.Operation.REGISTRATION_EMAIL, error.operation);
        assertEquals(CheckinCenterClient.ErrorKind.PROTOCOL, error.kind);
    }

    @Test
    public void pairingPollAcceptsPendingAndValidatedAuthorizedStates() throws Exception {
        CheckinCenterClient.PairingPoll pending = CheckinResponseParser.parsePairingPoll(
                new JSONObject().put("state", "pending"));
        assertEquals("pending", pending.state);
        assertEquals("", pending.deviceToken);
        assertFalse(pending.authorized());

        CheckinCenterClient.PairingPoll authorized = CheckinResponseParser.parsePairingPoll(
                new JSONObject().put("state", "authorized")
                        .put("device_token", DEVICE_TOKEN));
        assertTrue(authorized.authorized());
        assertEquals(DEVICE_TOKEN, authorized.deviceToken);

        CheckinCenterClient.ApiError error = assertThrows(CheckinCenterClient.ApiError.class,
                () -> CheckinResponseParser.parsePairingPoll(new JSONObject()
                        .put("state", "authorized").put("device_token", "ccdevice1_bad")));
        assertEquals(CheckinCenterClient.Operation.PAIR_POLL, error.operation);
    }

    @Test
    public void parsesConnectedAccountAndSmsSessionWithOperationSpecificValidation()
            throws Exception {
        CheckinCenterClient.ConnectedAccount account =
                CheckinResponseParser.parseConnectedAccount(new JSONObject()
                        .put("state", "connected")
                        .put("display_name", "Player")
                        .put("external_id_masked", "18******42")
                        .put("task_enabled", true),
                        CheckinCenterClient.Operation.SMS_SUBMIT);
        assertEquals("Player", account.displayName);
        assertEquals("18******42", account.externalIdMasked);
        assertTrue(account.taskEnabled);

        CheckinCenterClient.SmsSession session = CheckinResponseParser.parseSmsSession(
                new JSONObject().put("session_id", "sms-session")
                        .put("retry_after", 2).put("expires_in", 120));
        assertEquals("sms-session", session.sessionId);
        assertEquals(2, session.retryAfterSeconds);
        assertEquals(120, session.expiresInSeconds);

        CheckinCenterClient.ApiError smsError = assertThrows(CheckinCenterClient.ApiError.class,
                () -> CheckinResponseParser.parseSmsSession(new JSONObject()
                        .put("session_id", "sms-session")
                        .put("retry_after", 0).put("expires_in", 120)));
        assertEquals(CheckinCenterClient.Operation.SMS_SEND, smsError.operation);

        CheckinCenterClient.ApiError error = assertThrows(CheckinCenterClient.ApiError.class,
                () -> CheckinResponseParser.parseConnectedAccount(new JSONObject()
                        .put("state", "pending"),
                        CheckinCenterClient.Operation.PASSWORD_LOGIN));
        assertEquals(CheckinCenterClient.Operation.PASSWORD_LOGIN, error.operation);
    }

    @Test
    public void parsesStatusTaskSharingAndLastRunWithoutMovingModels() throws Exception {
        JSONObject task = new JSONObject()
                .put("enabled", true)
                .put("sign", true)
                .put("schedule_time", "08:30")
                .put("offset_minutes", 15)
                .put("window_start", "08:30")
                .put("window_end", "09:00")
                .put("platform_blocked", false)
                .put("sign_blocked", false)
                .put("share_actions_available", true)
                .put("share_post", true)
                .put("share_game", false)
                .put("share_review", true);
        CheckinCenterClient.Status status = CheckinResponseParser.parseStatus(new JSONObject()
                .put("account", new JSONObject().put("state", "connected")
                        .put("display_name", JSONObject.NULL)
                        .put("external_id_masked", "18******42"))
                .put("task", task)
                .put("last_run", new JSONObject().put("id", 7L).put("status", "success")
                        .put("summary", "done").put("started_at", "08:30")
                        .put("finished_at", "08:31")
                        .put("check_in", new JSONObject().put("checked_in", true)
                                .put("newly_signed", true).put("coin_delta", 12)))
                .put("billing_mode", "free"));

        assertEquals("connected", status.account.state);
        assertEquals("", status.account.displayName);
        assertEquals("18******42", status.account.externalIdMasked);
        assertTrue(status.task.active());
        assertEquals(15, status.task.offsetMinutes);
        assertTrue(status.task.sharing.available);
        assertTrue(status.task.sharing.post);
        assertFalse(status.task.sharing.game);
        assertTrue(status.task.sharing.review);
        assertNotNull(status.lastRun);
        assertEquals(7L, status.lastRun.id);
        assertTrue(status.lastRun.checkIn.checkedIn);
        assertEquals(12, status.lastRun.checkIn.coinDelta);
    }

    @Test
    public void parsesRunResultAndUsesSentinelsForMissingCheckinData() throws Exception {
        CheckinCenterClient.RunResult result = CheckinResponseParser.parseRunResult(
                new JSONObject().put("status", "already_signed").put("summary", "No change")
                        .put("run_id", 42L));
        assertEquals("already_signed", result.status);
        assertEquals("No change", result.summary);
        assertEquals(42L, result.runId);
        assertFalse(result.checkIn.checkedIn);
        assertEquals(-1, result.checkIn.coinDelta);

        CheckinCenterClient.ApiError error = assertThrows(CheckinCenterClient.ApiError.class,
                () -> CheckinResponseParser.parseRunResult(new JSONObject()));
        assertEquals(CheckinCenterClient.Operation.RUN_NOW, error.operation);
    }

    @Test
    public void checkinResultBoundsOptionalNumbersAndNullInput() throws Exception {
        CheckinCenterClient.CheckinResult result = CheckinResponseParser.parseCheckinResult(
                new JSONObject().put("checked_in", true).put("newly_signed", false)
                        .put("coin_delta", 12).put("experience_delta", 1_000_001)
                        .put("streak_days", JSONObject.NULL));

        assertTrue(result.checkedIn);
        assertFalse(result.newlySigned);
        assertEquals(12, result.coinDelta);
        assertEquals(-1, result.experienceDelta);
        assertEquals(-1, result.streakDays);

        CheckinCenterClient.CheckinResult empty = CheckinResponseParser.parseCheckinResult(null);
        assertFalse(empty.checkedIn);
        assertEquals(-1, empty.coinDelta);
        assertEquals(-1, empty.experienceDelta);
        assertEquals(-1, empty.streakDays);
    }
}
