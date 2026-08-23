package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.net.URLEncoder;

public class CheckinCenterClientTest {
    @Test
    public void pairingResponseAcceptsOnlyPinnedHttpsHostAndPath() {
        assertTrue(CheckinCenterClient.isTrustedPairingUri(
                "https://heyboxlite.xyz/checkin/lite/pair?code=ABCD-EFGH"));
        assertTrue(CheckinCenterClient.isTrustedPairingUri(
                "https://heyboxlite.xyz/checkin/login?next=%2Flite%2Fpair"));
        assertFalse(CheckinCenterClient.isTrustedPairingUri(
                "http://heyboxlite.xyz/checkin/lite/pair?code=ABCD-EFGH"));
        assertFalse(CheckinCenterClient.isTrustedPairingUri(
                "https://api.heyboxlite.xyz/checkin/lite/pair?code=ABCD-EFGH"));
        assertFalse(CheckinCenterClient.isTrustedPairingUri(
                "https://heyboxlite.xyz:444/checkin/lite/pair?code=ABCD-EFGH"));
        assertFalse(CheckinCenterClient.isTrustedPairingUri(
                "https://heyboxlite.xyz/admin"));
        assertFalse(CheckinCenterClient.isTrustedPairingUri(
                "https://user@heyboxlite.xyz/checkin/lite/pair"));
    }

    @Test
    public void retryPolicyIsBoundedAndBacksOff() {
        CheckinCenterClient.ApiError rateLimited = new CheckinCenterClient.ApiError(
                CheckinCenterClient.Operation.STATUS, 429, "limited");
        CheckinCenterClient.ApiError unauthorized = new CheckinCenterClient.ApiError(
                CheckinCenterClient.Operation.STATUS, 401, "invalid");

        assertTrue(CheckinRetryPolicy.shouldRetry(rateLimited, 0));
        assertTrue(CheckinRetryPolicy.shouldRetry(rateLimited, 1));
        assertFalse(CheckinRetryPolicy.shouldRetry(rateLimited, 2));
        assertFalse(CheckinRetryPolicy.shouldRetry(unauthorized, 0));
        assertTrue(CheckinRetryPolicy.delayMillis(rateLimited, 1, 0L)
                > CheckinRetryPolicy.delayMillis(rateLimited, 0, 0L));
    }

    @Test
    public void captchaPageAcceptsOnlyPinnedHttpsEndpoint() {
        assertTrue(CheckinCaptchaContract.isTrustedPageUri(
                "https://heyboxlite.xyz/checkin/lite/captcha?appid=2076842290"));
        assertFalse(CheckinCaptchaContract.isTrustedPageUri(
                "http://heyboxlite.xyz/checkin/lite/captcha?appid=2076842290"));
        assertFalse(CheckinCaptchaContract.isTrustedPageUri(
                "https://api.heyboxlite.xyz/checkin/lite/captcha?appid=2076842290"));
        assertFalse(CheckinCaptchaContract.isTrustedPageUri(
                "https://heyboxlite.xyz/checkin/lite/captcha?appid=2076842290&next=x"));
        assertFalse(CheckinCaptchaContract.isTrustedPageUri(
                "https://user@heyboxlite.xyz/checkin/lite/captcha?appid=2076842290"));
    }

    @Test
    public void captchaChallengeCarriesOnlyTrustedVerificationPage() {
        String response = "{\"error\":\"captcha_required\","
                + "\"verification_uri\":"
                + "\"https://heyboxlite.xyz/checkin/lite/captcha?appid=2076842290\"}";
        CheckinCenterClient.ApiError valid = CheckinCenterClient.statusError(
                CheckinCenterClient.Operation.SMS_SEND, 409, response);
        CheckinCenterClient.ApiError invalid = CheckinCenterClient.statusError(
                CheckinCenterClient.Operation.SMS_SEND, 409,
                response.replace("heyboxlite.xyz", "example.com"));

        assertTrue(valid.captchaRequired());
        assertEquals("https://heyboxlite.xyz/checkin/lite/captcha?appid=2076842290",
                valid.captchaUri);
        assertFalse(invalid.captchaRequired());
        assertEquals("", invalid.captchaUri);
    }

    @Test
    public void captchaPromptParsesShortLivedProof() throws Exception {
        String page = "https://heyboxlite.xyz/checkin/lite/captcha?appid=2076842290";
        String payload = "{\"ret\":0,\"ticket\":\"captcha-ticket\","
                + "\"randstr\":\"captcha-randstr\"}";
        String prompt = CheckinCaptchaContract.PROMPT_PREFIX
                + URLEncoder.encode(payload, "UTF-8");

        CheckinCaptchaContract.Result result =
                CheckinCaptchaContract.parsePrompt(page, prompt);

        assertNotNull(result);
        assertTrue(result.successful);
        assertEquals("captcha-ticket", result.ticket);
        assertEquals("captcha-randstr", result.randstr);
        assertFalse(CheckinCenterClient.captchaProofValid("ticket-only", ""));
    }

    @Test
    public void captchaPromptPreservesSafeProviderFailureCode() throws Exception {
        String page = "https://heyboxlite.xyz/checkin/lite/captcha?appid=2076842290";
        String payload = "{\"ret\":2,\"error_code\":1001}";
        String prompt = CheckinCaptchaContract.PROMPT_PREFIX
                + URLEncoder.encode(payload, "UTF-8");

        CheckinCaptchaContract.Result result =
                CheckinCaptchaContract.parsePrompt(page, prompt);

        assertNotNull(result);
        assertFalse(result.successful);
        assertEquals("provider_2_1001", result.diagnosticCode);
    }

    @Test
    public void captchaPromptIncludesSanitizedLoaderDiagnostics() throws Exception {
        String page = "https://heyboxlite.xyz/checkin/lite/captcha?appid=2076842290";
        String payload = "{\"ret\":1,\"error_code\":1001,"
                + "\"loader_stage\":\"entry:all\","
                + "\"error_reason\":\"network_or_policy\","
                + "\"sdk_source\":\"qcloud\"}";
        String prompt = CheckinCaptchaContract.PROMPT_PREFIX
                + URLEncoder.encode(payload, "UTF-8");

        CheckinCaptchaContract.Result result =
                CheckinCaptchaContract.parsePrompt(page, prompt);

        assertNotNull(result);
        assertFalse(result.successful);
        assertEquals("captcha_network_or_policy_entry_all_qcloud_1001",
                result.diagnosticCode);
    }

    @Test
    public void signingOperationsWaitForServerSideSigner() {
        assertEquals(25_000, CheckinCenterClient.readTimeoutMillis(
                CheckinCenterClient.Operation.PAIR_START));
        assertEquals(25_000, CheckinCenterClient.readTimeoutMillis(
                CheckinCenterClient.Operation.STATUS));
        assertEquals(25_000, CheckinCenterClient.readTimeoutMillis(
                CheckinCenterClient.Operation.PAIR_REGISTER));
        assertEquals(25_000, CheckinCenterClient.readTimeoutMillis(
                CheckinCenterClient.Operation.REGISTRATION_EMAIL));
        assertTrue(CheckinCenterClient.readTimeoutMillis(
                CheckinCenterClient.Operation.RUN_NOW) >= 120_000);
        assertTrue(CheckinCenterClient.readTimeoutMillis(
                CheckinCenterClient.Operation.SMS_SEND) >= 120_000);
        assertTrue(CheckinCenterClient.readTimeoutMillis(
                CheckinCenterClient.Operation.SMS_SUBMIT) >= 120_000);
        assertTrue(CheckinCenterClient.readTimeoutMillis(
                CheckinCenterClient.Operation.PASSWORD_LOGIN) >= 120_000);
        assertEquals(25_000, CheckinCenterClient.readTimeoutMillis(
                CheckinCenterClient.Operation.TASK_SETTINGS));
    }

    @Test
    public void serverErrorDetailsUseOnlyKnownDiagnosticCodes() {
        assertEquals("captcha_required", CheckinCenterClient.serverErrorCode(
                "{\"error\":\"captcha_required\"}"));
        assertEquals("", CheckinCenterClient.serverErrorCode(
                "{\"error\":\"Cookie: pkey=private-value\"}"));
    }

    @Test
    public void serviceRegistrationPasswordMatchesServerPolicy() {
        assertTrue(CheckinCenterClient.validServicePassword("Watch-Account-92!"));
        assertTrue(CheckinCenterClient.validServicePassword("lowercase-1234"));
        assertFalse(CheckinCenterClient.validServicePassword("alllowercasepassword"));
        assertFalse(CheckinCenterClient.validServicePassword("Short-1!"));
        assertFalse(CheckinCenterClient.validServicePassword(" Watch-Account-92!"));
    }

    @Test
    public void pairingResponseEnablesRequiredRegistrationEmail() throws Exception {
        JSONObject response = new JSONObject()
                .put("device_code", "ccpair1_pending-device")
                .put("user_code", "ABCD-EFGH")
                .put("verification_uri",
                        "https://heyboxlite.xyz/checkin/lite/pair?code=ABCD-EFGH")
                .put("expires_in", 600)
                .put("interval", 3)
                .put("registration_open", true)
                .put("registration_email_required", true);

        CheckinCenterClient.PairingStart start =
                CheckinCenterClient.parsePairingStart(response);

        assertTrue(start.registrationOpen);
        assertTrue(start.registrationEmailRequired);
        response.remove("registration_email_required");
        assertFalse(CheckinCenterClient.parsePairingStart(response)
                .registrationEmailRequired);
    }

    @Test
    public void registrationEmailSessionUsesServerCooldownAndExpiry() throws Exception {
        JSONObject response = new JSONObject()
                .put("challenge_id", "email_challenge_1234567890")
                .put("retry_after", 60)
                .put("expires_in", 600);

        CheckinCenterClient.RegistrationEmailSession session =
                CheckinCenterClient.parseRegistrationEmailSession(response);

        assertEquals("email_challenge_1234567890", session.challengeId);
        assertEquals(60, session.retryAfterSeconds);
        assertEquals(600, session.expiresInSeconds);
        response.put("challenge_id", "short");
        assertThrows(CheckinCenterClient.ApiError.class,
                () -> CheckinCenterClient.parseRegistrationEmailSession(response));
    }

    @Test
    public void registrationEmailValidationMatchesServerContract() {
        assertTrue(CheckinCenterClient.validRegistrationEmail("user@example.com"));
        assertTrue(CheckinCenterClient.validRegistrationEmail(" user@example.com "));
        assertFalse(CheckinCenterClient.validRegistrationEmail("user@example"));
        assertFalse(CheckinCenterClient.validRegistrationEmail("user @example.com"));
        assertFalse(CheckinCenterClient.validRegistrationEmail(".user@example.com"));
        assertFalse(CheckinCenterClient.validRegistrationEmail("user..name@example.com"));
        assertFalse(CheckinCenterClient.validRegistrationEmail("user@-example.com"));
        assertTrue(CheckinCenterClient.validRegistrationEmailCode("012345"));
        assertFalse(CheckinCenterClient.validRegistrationEmailCode("12345"));
        assertFalse(CheckinCenterClient.validRegistrationEmailCode("12A456"));
        assertTrue(CheckinCenterClient.validRegistrationChallengeId(
                "email_challenge_1234567890"));
        assertFalse(CheckinCenterClient.validRegistrationChallengeId("short"));
    }

    @Test
    public void registrationEmailErrorsPreserveSafeCooldownAndMessage() {
        CheckinCenterClient.ApiError limited = CheckinCenterClient.statusError(
                CheckinCenterClient.Operation.REGISTRATION_EMAIL, 429,
                "{\"error\":\"registration email limit reached\",\"retry_after\":47}");
        CheckinCenterClient.ApiError invalidCode = CheckinCenterClient.statusError(
                CheckinCenterClient.Operation.PAIR_REGISTER, 422,
                "{\"error\":\"registration email code is invalid\"}");

        assertEquals(47, limited.retryAfterSeconds);
        assertEquals("邮箱验证码发送过于频繁，请稍后重试", limited.getMessage());
        assertEquals("邮箱验证码错误或已过期", invalidCode.getMessage());
        assertEquals(0, CheckinCenterClient.serverRetryAfterSeconds(
                "{\"retry_after\":7200}"));
    }

    @Test
    public void missingSmsRouteIsNotReportedAsMissingTask() {
        CheckinCenterClient.ApiError sms = CheckinCenterClient.statusError(
                CheckinCenterClient.Operation.SMS_SEND, 404, "{\"detail\":\"Not Found\"}");
        CheckinCenterClient.ApiError status = CheckinCenterClient.statusError(
                CheckinCenterClient.Operation.STATUS, 404, "{\"detail\":\"Not Found\"}");

        assertEquals("服务器暂未支持手机号登录，请稍后重试", sms.getMessage());
        assertEquals("签到任务尚未配置", status.getMessage());
    }

    @Test
    public void passwordAndTaskErrorsUseSpecificMessages() {
        CheckinCenterClient.ApiError password = CheckinCenterClient.statusError(
                CheckinCenterClient.Operation.PASSWORD_LOGIN, 422, "{}");
        CheckinCenterClient.ApiError settings = CheckinCenterClient.statusError(
                CheckinCenterClient.Operation.TASK_SETTINGS, 422, "{}");

        assertEquals("手机号或密码错误，登录失败", password.getMessage());
        assertEquals("签到时间或随机偏移无效", settings.getMessage());
    }

    @Test
    public void sponsorshipStatusAndOrderResponsesParseCompactly() throws Exception {
        JSONObject status = new JSONObject()
                .put("billing_mode", "free")
                .put("subscription_required", false)
                .put("entitled", true)
                .put("is_admin", false)
                .put("expires_at", JSONObject.NULL)
                .put("voluntary_sponsorship", true)
                .put("checkout_available", true)
                .put("plan", new JSONObject()
                        .put("name", "服务器自愿赞助")
                        .put("amount_cents", 500)
                        .put("currency", "CNY")
                        .put("duration_days", 0)
                        .put("variable_amount", true)
                        .put("minimum_amount_cents", 1)
                        .put("maximum_amount_cents", 100_000_000));
        CheckinBilling.Membership membership = CheckinBilling.parseMembership(status);
        assertEquals("free", membership.mode);
        assertEquals(500, membership.plan.amountCents);
        assertTrue(membership.checkoutAvailable);
        assertTrue(membership.voluntarySponsorship);
        assertTrue(membership.plan.variableAmount);

        CheckinBilling.Order order = CheckinBilling.parseOrder(new JSONObject()
                .put("order_id", "HBAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
                .put("provider", "monitor_wechat")
                .put("amount_cents", 500)
                .put("payable_amount_cents", 500)
                .put("status", "pending")
                .put("qr_ready", true)
                .put("manual_review", true)
                .put("expires_at", "2026-08-14T01:00:00.000Z"));
        assertTrue(order.pending());
        assertTrue(order.manualReview);
        CheckinBilling.Order unchanged = new CheckinBilling.Order(
                order.id, order.provider, order.amountCents, order.payableAmountCents,
                order.currency, order.status, order.qrReady, order.manualReview,
                order.expiresAt, order.review);
        CheckinBilling.Order paid = new CheckinBilling.Order(
                order.id, order.provider, order.amountCents, order.payableAmountCents,
                order.currency, "paid", order.qrReady, order.manualReview,
                order.expiresAt, order.review);
        assertTrue(order.sameUiState(unchanged));
        assertFalse(order.sameUiState(paid));
        assertTrue(CheckinBilling.validPaymentReference("42000000000000000000"));
        assertFalse(CheckinBilling.validPaymentReference("short"));
    }

    @Test
    public void obsoleteSubscriptionErrorsDoNotRequestPayment() {
        CheckinCenterClient.ApiError error = CheckinCenterClient.statusError(
                CheckinCenterClient.Operation.RUN_NOW, 402,
                "{\"code\":\"subscription_required\"}");
        assertEquals("签到服务状态异常，请稍后重试", error.getMessage());
    }
}
