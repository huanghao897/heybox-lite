package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.URLEncoder;

public class CheckinCenterClientTest {
    @Test
    public void pairingResponseAcceptsOnlyPinnedHttpsHostAndPath() {
        assertTrue(CheckinCenterClient.isTrustedPairingUri(
                "https://8.138.134.236/checkin/lite/pair?code=ABCD-EFGH"));
        assertTrue(CheckinCenterClient.isTrustedPairingUri(
                "https://8.138.134.236/checkin/login?next=%2Flite%2Fpair"));
        assertFalse(CheckinCenterClient.isTrustedPairingUri(
                "http://8.138.134.236/checkin/lite/pair?code=ABCD-EFGH"));
        assertFalse(CheckinCenterClient.isTrustedPairingUri(
                "https://8.138.134.237/checkin/lite/pair?code=ABCD-EFGH"));
        assertFalse(CheckinCenterClient.isTrustedPairingUri(
                "https://8.138.134.236:444/checkin/lite/pair?code=ABCD-EFGH"));
        assertFalse(CheckinCenterClient.isTrustedPairingUri(
                "https://8.138.134.236/admin"));
        assertFalse(CheckinCenterClient.isTrustedPairingUri(
                "https://user@8.138.134.236/checkin/lite/pair"));
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
                "https://8.138.134.236/checkin/lite/captcha?appid=2076842290"));
        assertFalse(CheckinCaptchaContract.isTrustedPageUri(
                "http://8.138.134.236/checkin/lite/captcha?appid=2076842290"));
        assertFalse(CheckinCaptchaContract.isTrustedPageUri(
                "https://8.138.134.237/checkin/lite/captcha?appid=2076842290"));
        assertFalse(CheckinCaptchaContract.isTrustedPageUri(
                "https://8.138.134.236/checkin/lite/captcha?appid=2076842290&next=x"));
        assertFalse(CheckinCaptchaContract.isTrustedPageUri(
                "https://user@8.138.134.236/checkin/lite/captcha?appid=2076842290"));
    }

    @Test
    public void captchaChallengeCarriesOnlyTrustedVerificationPage() {
        String response = "{\"error\":\"captcha_required\","
                + "\"verification_uri\":"
                + "\"https://8.138.134.236/checkin/lite/captcha?appid=2076842290\"}";
        CheckinCenterClient.ApiError valid = CheckinCenterClient.statusError(
                CheckinCenterClient.Operation.SMS_SEND, 409, response);
        CheckinCenterClient.ApiError invalid = CheckinCenterClient.statusError(
                CheckinCenterClient.Operation.SMS_SEND, 409,
                response.replace("8.138.134.236", "example.com"));

        assertTrue(valid.captchaRequired());
        assertEquals("https://8.138.134.236/checkin/lite/captcha?appid=2076842290",
                valid.captchaUri);
        assertFalse(invalid.captchaRequired());
        assertEquals("", invalid.captchaUri);
    }

    @Test
    public void captchaPromptParsesShortLivedProof() throws Exception {
        String page = "https://8.138.134.236/checkin/lite/captcha?appid=2076842290";
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
    public void signingOperationsWaitForServerSideSigner() {
        assertEquals(25_000, CheckinCenterClient.readTimeoutMillis(
                CheckinCenterClient.Operation.PAIR_START));
        assertEquals(25_000, CheckinCenterClient.readTimeoutMillis(
                CheckinCenterClient.Operation.STATUS));
        assertTrue(CheckinCenterClient.readTimeoutMillis(
                CheckinCenterClient.Operation.CREDENTIAL_SYNC) >= 120_000);
        assertTrue(CheckinCenterClient.readTimeoutMillis(
                CheckinCenterClient.Operation.RUN_NOW) >= 120_000);
        assertTrue(CheckinCenterClient.readTimeoutMillis(
                CheckinCenterClient.Operation.SMS_SEND) >= 120_000);
        assertTrue(CheckinCenterClient.readTimeoutMillis(
                CheckinCenterClient.Operation.SMS_SUBMIT) >= 120_000);
    }

    @Test
    public void serverErrorDetailsUseOnlyKnownDiagnosticCodes() {
        assertEquals("payload_invalid", CheckinCenterClient.serverErrorCode(
                "{\"error\":\"credential payload is invalid\"}"));
        assertEquals("credentials_rejected", CheckinCenterClient.serverErrorCode(
                "{\"error\":\"Xiaoheihe rejected the credentials\"}"));
        assertEquals("captcha_required", CheckinCenterClient.serverErrorCode(
                "{\"error\":\"captcha_required\"}"));
        assertEquals("", CheckinCenterClient.serverErrorCode(
                "{\"error\":\"Cookie: pkey=private-value\"}"));
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
}
