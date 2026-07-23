package com.ronan.heyboxlite;

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
}
