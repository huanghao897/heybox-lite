package com.ronan.heyboxlite;

import org.json.JSONException;
import org.json.JSONObject;

/** Converts checked-in API JSON into the client-facing response models. */
final class CheckinResponseParser {
    private CheckinResponseParser() {}

    static CheckinCenterClient.PairingStart parsePairingStart(JSONObject value)
            throws JSONException, CheckinCenterClient.ApiError {
        String deviceCode = required(value, "device_code",
                CheckinCenterClient.Operation.PAIR_START);
        String userCode = required(value, "user_code",
                CheckinCenterClient.Operation.PAIR_START);
        String verificationUri = required(value, "verification_uri",
                CheckinCenterClient.Operation.PAIR_START);
        CheckinCenterClient.requireTrustedPairingUri(verificationUri);
        int expiresIn = positive(value.optInt("expires_in"),
                CheckinCenterClient.Operation.PAIR_START);
        int interval = positive(value.optInt("interval"),
                CheckinCenterClient.Operation.PAIR_START);
        return new CheckinCenterClient.PairingStart(deviceCode, userCode, expiresIn, interval,
                value.optBoolean("registration_open", false),
                value.optBoolean("registration_email_required", false));
    }

    static CheckinCenterClient.RegistrationEmailSession parseRegistrationEmailSession(
            JSONObject value) throws CheckinCenterClient.ApiError {
        CheckinCenterClient.Operation operation =
                CheckinCenterClient.Operation.REGISTRATION_EMAIL;
        String challengeId = required(value, "challenge_id", operation);
        if (!CheckinCenterClient.validRegistrationChallengeId(challengeId)) {
            throw CheckinCenterClient.protocolError(operation);
        }
        int retryAfter = positive(value.optInt("retry_after"), operation);
        int expiresIn = positive(value.optInt("expires_in"), operation);
        return new CheckinCenterClient.RegistrationEmailSession(
                challengeId, retryAfter, expiresIn);
    }

    static CheckinCenterClient.PairingPoll parsePairingPoll(JSONObject value)
            throws CheckinCenterClient.ApiError {
        String state = value.optString("state", "");
        if ("pending".equals(state)) {
            return new CheckinCenterClient.PairingPoll(state, "");
        }
        if (!"authorized".equals(state)) {
            throw CheckinCenterClient.protocolError(CheckinCenterClient.Operation.PAIR_POLL);
        }
        String token = value.optString("device_token", "");
        CheckinCenterClient.requirePrefix(token, "ccdevice1_",
                CheckinCenterClient.Operation.PAIR_POLL);
        return new CheckinCenterClient.PairingPoll(state, token);
    }

    static CheckinCenterClient.ConnectedAccount parseConnectedAccount(JSONObject value,
                                                                        CheckinCenterClient.Operation operation)
            throws CheckinCenterClient.ApiError {
        if (!"connected".equals(value.optString("state", ""))) {
            throw CheckinCenterClient.protocolError(operation);
        }
        return new CheckinCenterClient.ConnectedAccount(value.optString("display_name", ""),
                value.optString("external_id_masked", ""),
                value.optBoolean("task_enabled", false));
    }

    static CheckinCenterClient.SmsSession parseSmsSession(JSONObject value)
            throws CheckinCenterClient.ApiError {
        CheckinCenterClient.Operation operation = CheckinCenterClient.Operation.SMS_SEND;
        String sessionId = required(value, "session_id", operation);
        if (sessionId.length() > 128) throw CheckinCenterClient.protocolError(operation);
        int retryAfter = positive(value.optInt("retry_after"), operation);
        int expiresIn = positive(value.optInt("expires_in"), operation);
        return new CheckinCenterClient.SmsSession(sessionId, retryAfter, expiresIn);
    }

    static CheckinCenterClient.Status parseStatus(JSONObject value)
            throws CheckinCenterClient.ApiError {
        CheckinCenterClient.Operation operation = CheckinCenterClient.Operation.STATUS;
        JSONObject accountJson = value.optJSONObject("account");
        JSONObject taskJson = value.optJSONObject("task");
        if (accountJson == null || taskJson == null) {
            throw CheckinCenterClient.protocolError(operation);
        }
        CheckinCenterClient.Account account = new CheckinCenterClient.Account(
                accountJson.optString("state", ""),
                nullableString(accountJson, "display_name"),
                nullableString(accountJson, "external_id_masked"));
        CheckinCenterClient.Task task = parseTask(taskJson);
        JSONObject runJson = value.optJSONObject("last_run");
        CheckinCenterClient.LastRun run = runJson == null ? null
                : new CheckinCenterClient.LastRun(runJson.optLong("id", 0L),
                runJson.optString("status", ""), runJson.optString("summary", ""),
                runJson.optString("started_at", ""), runJson.optString("finished_at", ""),
                parseCheckinResult(runJson.optJSONObject("check_in")));
        return new CheckinCenterClient.Status(account, task, run,
                CheckinBilling.parseMembership(value));
    }

    static CheckinCenterClient.Task parseTask(JSONObject taskJson) {
        return new CheckinCenterClient.Task(taskJson.optBoolean("enabled", false),
                taskJson.optBoolean("sign", false), taskJson.optString("schedule_time", ""),
                taskJson.optInt("offset_minutes", 0), taskJson.optString("window_start", ""),
                taskJson.optString("window_end", ""),
                taskJson.optBoolean("platform_blocked", false),
                taskJson.optBoolean("sign_blocked", false), CheckinSharing.parse(taskJson));
    }

    static CheckinCenterClient.RunResult parseRunResult(JSONObject value)
            throws CheckinCenterClient.ApiError {
        CheckinCenterClient.Operation operation = CheckinCenterClient.Operation.RUN_NOW;
        String status = value.optString("status", "");
        if (status.isEmpty()) throw CheckinCenterClient.protocolError(operation);
        return new CheckinCenterClient.RunResult(status, value.optString("summary", ""),
                value.optLong("run_id", 0L), parseCheckinResult(value.optJSONObject("check_in")));
    }

    static CheckinCenterClient.CheckinResult parseCheckinResult(JSONObject value) {
        if (value == null) {
            return new CheckinCenterClient.CheckinResult(false, false, -1, -1, -1);
        }
        return new CheckinCenterClient.CheckinResult(value.optBoolean("checked_in", false),
                value.optBoolean("newly_signed", false), optionalInt(value, "coin_delta"),
                optionalInt(value, "experience_delta"), optionalInt(value, "streak_days"));
    }

    private static String required(JSONObject value, String key,
                                   CheckinCenterClient.Operation operation)
            throws CheckinCenterClient.ApiError {
        String result = value.optString(key, "").trim();
        if (result.isEmpty()) throw CheckinCenterClient.protocolError(operation);
        return result;
    }

    private static int positive(int value, CheckinCenterClient.Operation operation)
            throws CheckinCenterClient.ApiError {
        if (value <= 0) throw CheckinCenterClient.protocolError(operation);
        return value;
    }

    private static int optionalInt(JSONObject value, String key) {
        if (value.isNull(key) || !value.has(key)) return -1;
        int result = value.optInt(key, -1);
        return result >= 0 && result <= 1_000_000 ? result : -1;
    }

    private static String nullableString(JSONObject value, String key) {
        return value.isNull(key) ? "" : value.optString(key, "");
    }
}
