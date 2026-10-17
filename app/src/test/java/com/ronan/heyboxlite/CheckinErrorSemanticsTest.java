package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CheckinErrorSemanticsTest {
    @Test
    public void pairingPasswordFailureDoesNotInvalidateAnExistingDevice() {
        CheckinCenterClient.ApiError credentials = error(
                CheckinCenterClient.Operation.PAIR_APPROVE, 401, "pairing_credentials_invalid");
        CheckinCenterClient.ApiError device = error(
                CheckinCenterClient.Operation.STATUS, 401, "device_authorization_invalid");

        assertFalse(credentials.authorizationInvalid());
        assertEquals("签到服务账号或密码错误", credentials.getMessage());
        assertTrue(device.authorizationInvalid());
    }

    @Test
    public void membershipAndFeatureRestrictionsKeepDeviceAuthorization() {
        CheckinCenterClient.ApiError membership = error(
                CheckinCenterClient.Operation.RUN_NOW, 402, "subscription_required");
        CheckinCenterClient.ApiError sharing = error(
                CheckinCenterClient.Operation.TASK_SETTINGS, 403, "share_tasks_unavailable");

        assertFalse(membership.authorizationInvalid());
        assertFalse(sharing.authorizationInvalid());
        assertEquals("请开通或续费小黑盒签到会员", membership.getMessage());
        assertEquals("分享任务暂未开放", sharing.getMessage());
    }

    @Test
    public void orderFailuresAreDistinctFromMissingRoutesAndTemporaryQrFailures() {
        CheckinCenterClient.ApiError missing = error(
                CheckinCenterClient.Operation.BILLING_STATUS, 404, "order_not_found");
        CheckinCenterClient.ApiError route = CheckinCenterClient.statusError(
                CheckinCenterClient.Operation.BILLING_STATUS, 404, "{\"detail\":\"Not Found\"}");
        CheckinCenterClient.ApiError expired = error(
                CheckinCenterClient.Operation.BILLING_QR, 410, "order_expired");
        CheckinCenterClient.ApiError unavailable = error(
                CheckinCenterClient.Operation.BILLING_QR, 503, "payment_qr_unavailable");

        assertEquals("order_not_found", missing.diagnosticCode);
        assertEquals("", route.diagnosticCode);
        assertEquals("支付订单已过期，请重新生成", expired.getMessage());
        assertEquals("支付码暂不可用，请稍后重试", unavailable.getMessage());
        assertTrue(unavailable.retryable());
        assertFalse(missing.authorizationInvalid());
        assertFalse(expired.authorizationInvalid());
    }

    @Test
    public void legacyAmbiguousQrFailureDoesNotClaimTheOrderHasExpired() {
        CheckinCenterClient.ApiError error = CheckinCenterClient.statusError(
                CheckinCenterClient.Operation.BILLING_QR, 410, "Order QR code is unavailable");

        assertEquals("", error.diagnosticCode);
        assertEquals("支付码暂不可用，请刷新订单状态", error.getMessage());
    }

    @Test
    public void businessCheckoutRestrictionIsDistinctFromAChannelOutage() {
        CheckinCenterClient.ApiError restriction = error(
                CheckinCenterClient.Operation.BILLING_CREATE, 409, "checkout_unavailable");
        CheckinCenterClient.ApiError channel = error(
                CheckinCenterClient.Operation.BILLING_CREATE, 503, "payment_channel_unavailable");

        assertEquals("当前套餐或订单暂不可购买，请返回会员页检查", restriction.getMessage());
        assertEquals("支付渠道暂不可用，请稍后重试", channel.getMessage());
        assertFalse(restriction.retryable());
        assertTrue(channel.retryable());
    }

    @Test
    public void taskConflictIsNotReportedAsInvalidAuthorizationOrPaymentFailure() {
        CheckinCenterClient.ApiError running = error(
                CheckinCenterClient.Operation.RUN_NOW, 409, "task_already_running");
        CheckinCenterClient.ApiError missing = error(
                CheckinCenterClient.Operation.RUN_NOW, 404, "task_not_configured");

        assertEquals("已有签到任务正在执行，请稍后刷新状态", running.getMessage());
        assertEquals("签到任务尚未配置", missing.getMessage());
        assertFalse(running.authorizationInvalid());
        assertFalse(running.retryable());
    }

    @Test
    public void structuredCodesAreAllowlistedAndLegacyMessagesRemainCompatible() {
        assertEquals("", CheckinCenterClient.serverErrorCode(
                "{\"code\":\"private_diagnostic\",\"error\":\"private details\"}"));
        assertEquals("order_not_found", CheckinCenterClient.serverErrorCode(
                "{\"error\":\"订单不存在\"}"));
        CheckinCenterClient.ApiError limited = CheckinCenterClient.statusError(
                CheckinCenterClient.Operation.BILLING_STATUS, 429,
                "{\"code\":\"rate_limited\",\"retry_after\":3590}");

        assertEquals("rate_limited", limited.diagnosticCode);
        assertEquals(3_590_000L, CheckinRetryPolicy.delayMillis(limited, 0, 0));
    }

    private static CheckinCenterClient.ApiError error(CheckinCenterClient.Operation operation,
                                                       int status, String code) {
        return CheckinCenterClient.statusError(operation, status, "{\"code\":\"" + code + "\"}");
    }
}
