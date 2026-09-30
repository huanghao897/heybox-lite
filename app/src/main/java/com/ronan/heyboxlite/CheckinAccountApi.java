package com.ronan.heyboxlite;

import org.json.JSONException;
import org.json.JSONObject;

/** Extra account operations share the pinned transport, not its business logic. */
final class CheckinAccountApi {
    private final CheckinCenterClient client;

    CheckinAccountApi(CheckinCenterClient client) { this.client = client; }

    void history(String token, CheckinCenterClient.Callback<CheckinHistory> callback) {
        client.accountRequest(CheckinCenterClient.Operation.HISTORY, "GET", "/history/heybox",
                token, null, CheckinHistory::parse, callback);
    }

    void sendRecoveryCode(String userCode, String email,
                          CheckinCenterClient.Callback<
                                  CheckinCenterClient.RegistrationEmailSession> callback) {
        CheckinCenterClient.Operation operation = CheckinCenterClient.Operation.RECOVERY_EMAIL;
        try {
            JSONObject body = recoveryBase(userCode, email);
            client.accountRequest(operation, "POST", "/password-reset/code", "", body,
                    value -> new CheckinCenterClient.RegistrationEmailSession(
                            value.getString("challenge_id"),
                            Math.max(1, value.getInt("retry_after")),
                            Math.max(1, value.getInt("expires_in"))), callback);
        } catch (JSONException error) {
            callback.onError(new CheckinCenterClient.ApiError(operation, 422,
                    "请检查邮箱和配对状态"));
        }
    }

    void completeRecovery(String userCode, String email, String challenge, String code,
                          String password, String confirmation,
                          CheckinCenterClient.Callback<Boolean> callback) {
        CheckinCenterClient.Operation operation = CheckinCenterClient.Operation.RECOVERY_COMPLETE;
        try {
            JSONObject body = recoveryBase(userCode, email);
            if (!CheckinCenterClient.validRegistrationChallengeId(challenge)
                    || !CheckinCenterClient.validRegistrationEmailCode(code)
                    || !CheckinServiceValidation.password(password)
                    || !password.equals(confirmation)) throw new JSONException("invalid input");
            body.put("challenge_id", challenge);
            body.put("code", code);
            body.put("new_password", password);
            body.put("confirm_password", confirmation);
            client.accountRequest(operation, "POST", "/password-reset/complete", "", body,
                    value -> {
                        if (!"password_reset".equals(value.optString("state"))) {
                            throw new JSONException("invalid state");
                        }
                        return true;
                    }, callback);
        } catch (JSONException error) {
            callback.onError(new CheckinCenterClient.ApiError(operation, 422,
                    "请检查验证码；两次密码须一致且至少 12 位、包含三类字符"));
        }
    }

    private static JSONObject recoveryBase(String userCode, String email) throws JSONException {
        if (userCode == null || !userCode.matches("[A-Z0-9]{4}-[A-Z0-9]{4}")
                || !CheckinServiceValidation.email(email)) throw new JSONException("invalid input");
        return new JSONObject().put("user_code", userCode).put("email", email.trim());
    }
}
