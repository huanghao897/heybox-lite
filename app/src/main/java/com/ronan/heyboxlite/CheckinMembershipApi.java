package com.ronan.heyboxlite;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

/** Membership operations use only the existing device-authorized Lite transport. */
final class CheckinMembershipApi {
    interface AccountRequests {
        <T> void request(CheckinCenterClient.Operation operation, String method, String path,
                         String token, JSONObject body, CheckinCenterClient.Parser<T> parser,
                         CheckinCenterClient.Callback<T> callback);
    }

    private final AccountRequests requests;

    CheckinMembershipApi(CheckinCenterClient client) {
        this(client::accountRequest);
    }

    CheckinMembershipApi(AccountRequests requests) {
        this.requests = requests;
    }

    void getMembershipCatalog(String deviceToken,
                              CheckinCenterClient.Callback<CheckinBilling.Membership> callback) {
        CheckinCenterClient.Operation operation = CheckinCenterClient.Operation.BILLING_CATALOG;
        request(operation, "GET", "/billing/catalog", deviceToken, null,
                value -> parseMembership(value, operation), callback);
    }

    void getPurchaseHistory(String deviceToken,
                            CheckinCenterClient.Callback<List<CheckinBilling.OrderRecord>> callback) {
        request(CheckinCenterClient.Operation.BILLING_HISTORY, "GET", "/billing/orders",
                deviceToken, null, CheckinBilling::parseOrderRecords, callback);
    }

    void redeemMembership(String deviceToken, String code,
                          CheckinCenterClient.Callback<CheckinBilling.Membership> callback) {
        CheckinCenterClient.Operation operation = CheckinCenterClient.Operation.BILLING_REDEEM;
        String normalized = normalizeRedemptionCode(code);
        if (normalized.isEmpty()) {
            fail(callback, new CheckinCenterClient.ApiError(operation, 422, "兑换码格式无效"));
            return;
        }
        try {
            request(operation, "POST", "/billing/redeem", deviceToken,
                    new JSONObject().put("code", normalized),
                    value -> parseMembership(value, operation), callback);
        } catch (JSONException error) {
            fail(callback, CheckinCenterClient.protocolError(operation));
        }
    }

    void createMembershipOrder(String deviceToken, String productSku,
                               CheckinCenterClient.Callback<CheckinBilling.Order> callback) {
        CheckinCenterClient.Operation operation = CheckinCenterClient.Operation.BILLING_CREATE;
        String sku = productSku == null ? "" : productSku.trim();
        if (sku.isEmpty() || sku.length() > 128 || hasControl(sku)) {
            fail(callback, new CheckinCenterClient.ApiError(operation, 422, "请选择有效套餐"));
            return;
        }
        try {
            request(operation, "POST", "/billing/orders", deviceToken,
                    new JSONObject().put("product_sku", sku),
                    value -> CheckinBilling.parseOrder(value, operation), callback);
        } catch (JSONException error) {
            fail(callback, CheckinCenterClient.protocolError(operation));
        }
    }

    void createBillingOrder(String deviceToken, int amountCents,
                            CheckinCenterClient.Callback<CheckinBilling.Order> callback) {
        CheckinCenterClient.Operation operation = CheckinCenterClient.Operation.BILLING_CREATE;
        if (!SponsorshipAmount.validCents(amountCents)) {
            fail(callback, new CheckinCenterClient.ApiError(operation, 422, "赞助金额无效"));
            return;
        }
        try {
            request(operation, "POST", "/billing/orders", deviceToken,
                    new JSONObject().put("amount_cents", amountCents),
                    value -> CheckinBilling.parseOrder(value, operation), callback);
        } catch (JSONException error) {
            fail(callback, CheckinCenterClient.protocolError(operation));
        }
    }

    void getBillingOrder(String deviceToken, String orderId,
                         CheckinCenterClient.Callback<CheckinBilling.Order> callback) {
        CheckinCenterClient.Operation operation = CheckinCenterClient.Operation.BILLING_STATUS;
        if (!CheckinBilling.validOrderId(orderId)) {
            fail(callback, CheckinCenterClient.protocolError(operation));
            return;
        }
        request(operation, "GET", "/billing/orders/" + orderId, deviceToken, null,
                CheckinBilling::parseOrder, callback);
    }

    void submitBillingClaim(String deviceToken, String orderId, String paymentReference,
                            CheckinCenterClient.Callback<CheckinBilling.Review> callback) {
        CheckinCenterClient.Operation operation = CheckinCenterClient.Operation.BILLING_CLAIM;
        if (!CheckinBilling.validOrderId(orderId)
                || !CheckinBilling.validPaymentReference(paymentReference)) {
            fail(callback, new CheckinCenterClient.ApiError(operation, 422,
                    "支付订单号格式不正确"));
            return;
        }
        try {
            request(operation, "POST", "/billing/orders/" + orderId + "/claim", deviceToken,
                    new JSONObject().put("payment_reference", paymentReference.trim()),
                    CheckinBilling::parseClaim, callback);
        } catch (JSONException error) {
            fail(callback, CheckinCenterClient.protocolError(operation));
        }
    }

    static String normalizeRedemptionCode(String code) {
        if (code == null || code.length() > 128 || hasControl(code)) return "";
        for (int index = 0; index < code.length(); index++) {
            if (code.charAt(index) > 0x7f) return "";
        }
        String compact = code.trim().toUpperCase(Locale.ROOT).replace("-", "").replace(" ", "");
        if (compact.startsWith("HBX")) compact = compact.substring(3);
        return compact.matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{16}") ? compact : "";
    }

    private static CheckinBilling.Membership parseMembership(
            JSONObject value, CheckinCenterClient.Operation operation)
            throws CheckinCenterClient.ApiError {
        String mode = value.optString("billing_mode", "");
        if (!"free".equals(mode) && !"paid".equals(mode)) {
            throw CheckinCenterClient.protocolError(operation);
        }
        return CheckinBilling.parseMembership(value);
    }

    private <T> void request(CheckinCenterClient.Operation operation, String method, String path,
                             String deviceToken, JSONObject body,
                             CheckinCenterClient.Parser<T> parser,
                             CheckinCenterClient.Callback<T> callback) {
        try {
            String token = CheckinCenterClient.requirePrefix(deviceToken, "ccdevice1_", operation);
            requests.request(operation, method, path, token, body, parser, callback);
        } catch (CheckinCenterClient.ApiError error) {
            fail(callback, error);
        }
    }

    private static boolean hasControl(String value) {
        for (int index = 0; index < value.length(); index++) {
            char item = value.charAt(index);
            if (item < 0x20 || item == 0x7f) return true;
        }
        return false;
    }

    private static <T> void fail(CheckinCenterClient.Callback<T> callback,
                                 CheckinCenterClient.ApiError error) {
        if (callback != null) callback.onError(error);
    }
}
