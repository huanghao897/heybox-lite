package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CheckinCenterClientTest {
    @Test
    public void pairingWebViewAcceptsOnlyPinnedHttpsHostAndPath() {
        assertTrue(CheckinCenterClient.isTrustedWebUri(
                "https://8.138.134.236/checkin/lite/pair?code=ABCD-EFGH"));
        assertTrue(CheckinCenterClient.isTrustedWebUri(
                "https://8.138.134.236/checkin/login?next=%2Flite%2Fpair"));
        assertFalse(CheckinCenterClient.isTrustedWebUri(
                "http://8.138.134.236/checkin/lite/pair?code=ABCD-EFGH"));
        assertFalse(CheckinCenterClient.isTrustedWebUri(
                "https://8.138.134.237/checkin/lite/pair?code=ABCD-EFGH"));
        assertFalse(CheckinCenterClient.isTrustedWebUri(
                "https://8.138.134.236:444/checkin/lite/pair?code=ABCD-EFGH"));
        assertFalse(CheckinCenterClient.isTrustedWebUri(
                "https://8.138.134.236/admin"));
        assertFalse(CheckinCenterClient.isTrustedWebUri(
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
    public void signingOperationsWaitForServerSideSigner() {
        assertEquals(25_000, CheckinCenterClient.readTimeoutMillis(
                CheckinCenterClient.Operation.PAIR_START));
        assertEquals(25_000, CheckinCenterClient.readTimeoutMillis(
                CheckinCenterClient.Operation.STATUS));
        assertTrue(CheckinCenterClient.readTimeoutMillis(
                CheckinCenterClient.Operation.CREDENTIAL_SYNC) >= 120_000);
        assertTrue(CheckinCenterClient.readTimeoutMillis(
                CheckinCenterClient.Operation.RUN_NOW) >= 120_000);
    }

    @Test
    public void serverErrorDetailsUseOnlyKnownDiagnosticCodes() {
        assertEquals("payload_invalid", CheckinCenterClient.serverErrorCode(
                "{\"error\":\"credential payload is invalid\"}"));
        assertEquals("credentials_rejected", CheckinCenterClient.serverErrorCode(
                "{\"error\":\"Xiaoheihe rejected the credentials\"}"));
        assertEquals("", CheckinCenterClient.serverErrorCode(
                "{\"error\":\"Cookie: pkey=private-value\"}"));
    }
}
