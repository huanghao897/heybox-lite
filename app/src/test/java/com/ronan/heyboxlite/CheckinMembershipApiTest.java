package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

public class CheckinMembershipApiTest {
    private static final String TOKEN = "ccdevice1_abcdefghijklmnopqrstuvwxyz0123456789ABCD";
    private static final String ORDER_ID = "HBAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Test
    public void catalogUsesDeviceTokenAndReturnsTheServerMembership() throws Exception {
        RecordingRequests requests = new RecordingRequests(catalogResponse(false));
        Result<CheckinBilling.Membership> result = new Result<>();

        new CheckinMembershipApi(requests).getMembershipCatalog(TOKEN, result);

        assertRequest(requests, CheckinCenterClient.Operation.BILLING_CATALOG,
                "GET", "/billing/catalog");
        assertNull(requests.body);
        assertNull(result.error);
        assertFalse(result.value.entitled);
        assertEquals("afdian", result.value.checkoutProvider);
        assertEquals(1, result.value.products.size());
        assertEquals("heybox_custom", result.value.products.get(0).sku);
        assertEquals(1887, result.value.products.get(0).amountCents);
        assertEquals(93, result.value.products.get(0).durationDays);
    }

    @Test
    public void purchaseHistoryUsesOwnAccountWithoutQueryParametersOrBody() throws Exception {
        JSONObject record = orderResponse().put("product_name", "Custom plan")
                .put("status", "paid").put("created_at", "2026-10-03T01:00:00Z")
                .put("expires_at", "2026-10-03T01:15:00Z").put("duration_days", 93);
        RecordingRequests requests = new RecordingRequests(
                new JSONObject().put("items", new JSONArray().put(record)));
        Result<List<CheckinBilling.OrderRecord>> result = new Result<>();

        new CheckinMembershipApi(requests).getPurchaseHistory(TOKEN, result);

        assertRequest(requests, CheckinCenterClient.Operation.BILLING_HISTORY,
                "GET", "/billing/orders");
        assertNull(requests.body);
        assertNull(result.error);
        assertEquals(1, result.value.size());
        assertEquals(ORDER_ID, result.value.get(0).orderId);
        assertEquals("heybox_custom", result.value.get(0).productSku);
        assertEquals("Custom plan", result.value.get(0).productName);
        assertEquals(1887, result.value.get(0).amountCents);
        assertEquals(1889, result.value.get(0).payableAmountCents);
        assertEquals("CNY", result.value.get(0).currency);
        assertEquals("paid", result.value.get(0).status);
        assertEquals(93, result.value.get(0).durationDays);
    }

    @Test
    public void redeemSendsOnlyNormalizedCodeAndReadsEntitlementFromResponse() throws Exception {
        RecordingRequests requests = new RecordingRequests(catalogResponse(true));
        Result<CheckinBilling.Membership> result = new Result<>();

        new CheckinMembershipApi(requests).redeemMembership(
                TOKEN, " hbx-abcd-efgh-jklm-npqr ", result);

        assertRequest(requests, CheckinCenterClient.Operation.BILLING_REDEEM,
                "POST", "/billing/redeem");
        assertOnlyBodyKey(requests.body, "code", "ABCDEFGHJKLMNPQR");
        assertNull(result.error);
        assertTrue(result.value.entitled);
        assertEquals("2027-01-04T01:00:00Z", result.value.expiresAt);
        assertEquals(93, result.value.products.get(0).durationDays);

        requests.response = catalogResponse(false);
        new CheckinMembershipApi(requests).redeemMembership(TOKEN, "ABCDEFGHJKLMNPQR", result);
        assertFalse(result.value.entitled);
    }

    @Test
    public void membershipOrderSendsOnlySkuNotAmountProviderOrAuthorization() throws Exception {
        RecordingRequests requests = new RecordingRequests(orderResponse());
        Result<CheckinBilling.Order> result = new Result<>();

        new CheckinMembershipApi(requests).createMembershipOrder(TOKEN, "heybox_custom", result);

        assertRequest(requests, CheckinCenterClient.Operation.BILLING_CREATE,
                "POST", "/billing/orders");
        assertOnlyBodyKey(requests.body, "product_sku", "heybox_custom");
        assertNull(result.error);
        assertEquals(1887, result.value.amountCents);
        assertEquals(1889, result.value.payableAmountCents);
        assertEquals("heybox_custom", result.value.productSku);
        assertTrue(result.value.checkoutUrl.startsWith("https://afdian.com/"));
        assertEquals(1, requests.calls);
    }

    @Test
    public void legacySponsorshipAndClaimRequestsRemainCompatible() throws Exception {
        RecordingRequests requests = new RecordingRequests(orderResponse());
        CheckinMembershipApi api = new CheckinMembershipApi(requests);
        Result<CheckinBilling.Order> order = new Result<>();
        api.createBillingOrder(TOKEN, 543, order);
        assertRequest(requests, CheckinCenterClient.Operation.BILLING_CREATE,
                "POST", "/billing/orders");
        assertEquals(1, requests.body.length());
        assertEquals(543, requests.body.getInt("amount_cents"));
        assertFalse(requests.body.has("provider"));
        assertFalse(requests.body.toString().contains(TOKEN));

        api.getBillingOrder(TOKEN, ORDER_ID, order);
        assertRequest(requests, CheckinCenterClient.Operation.BILLING_STATUS,
                "GET", "/billing/orders/" + ORDER_ID);
        assertNull(requests.body);

        requests.response = new JSONObject().put("review", new JSONObject()
                .put("claim_id", "claim").put("payment_reference", "420000000000")
                .put("status", "pending").put("reason", ""));
        Result<CheckinBilling.Review> review = new Result<>();
        api.submitBillingClaim(TOKEN, ORDER_ID, "420000000000", review);
        assertRequest(requests, CheckinCenterClient.Operation.BILLING_CLAIM,
                "POST", "/billing/orders/" + ORDER_ID + "/claim");
        assertOnlyBodyKey(requests.body, "payment_reference", "420000000000");
        assertNull(review.error);
        assertTrue(review.value.pending());
    }

    @Test
    public void redemptionNormalizationMatchesServerAlphabetAndOptionalPrefix() {
        assertEquals("ABCDEFGHJKLMNPQR",
                CheckinMembershipApi.normalizeRedemptionCode("HBX-ABCD-EFGH-JKLM-NPQR"));
        assertEquals("ABCDEFGHJKLMNPQR",
                CheckinMembershipApi.normalizeRedemptionCode("abcd efgh jklm npqr"));
        assertEquals("2345678923456789",
                CheckinMembershipApi.normalizeRedemptionCode("hbx-2345-6789-2345-6789"));
        String[] invalid = {null, "", "HBX-short", "ABCDEFGHJKLMNPQ0", "ABCDEFGHJKLMNPQ1",
                "ABCDEFGHJKLMNPQI", "ABCDEFGHJKLMNPQO", "ABCDEFGHJKLMNPQRX",
                "ABCDEFGH\tJKLMNPQR", "ABCDEFGHJKLMNPQR\n", "ABCDEFGHJKLMNPQ\u00e9"};
        for (String code : invalid) assertEquals("", CheckinMembershipApi.normalizeRedemptionCode(code));
    }

    @Test
    public void invalidCodeAndSkuFailBeforeAnyRequestAndDoNotEchoSecrets() throws Exception {
        RecordingRequests requests = new RecordingRequests(new JSONObject());
        CheckinMembershipApi api = new CheckinMembershipApi(requests);
        Result<CheckinBilling.Membership> membership = new Result<>();
        api.redeemMembership(TOKEN, "secret-invalid-code", membership);
        assertEquals(CheckinCenterClient.Operation.BILLING_REDEEM, membership.error.operation);
        assertEquals(422, membership.error.statusCode);
        assertFalse(membership.error.getMessage().contains("secret-invalid-code"));

        Result<CheckinBilling.Order> order = new Result<>();
        String[] invalidSkus = {null, "", " ", "sku\nunsafe", new String(new char[129]).replace('\0', 'x')};
        for (String sku : invalidSkus) {
            api.createMembershipOrder(TOKEN, sku, order);
            assertEquals(CheckinCenterClient.Operation.BILLING_CREATE, order.error.operation);
            assertEquals(422, order.error.statusCode);
        }
        assertEquals(0, requests.calls);
    }

    @Test
    public void allNewOperationsRejectMissingOrMalformedDeviceTokens() throws Exception {
        RecordingRequests requests = new RecordingRequests(catalogResponse(false));
        CheckinMembershipApi api = new CheckinMembershipApi(requests);
        String[] invalidTokens = {null, "", "pkey=private-cookie", "ccdevice1_short",
                "ccdevice1_abcdefghijklmnopqrstuvwxyz0123456789\r\nprivate"};
        for (String token : invalidTokens) {
            Result<CheckinBilling.Membership> membership = new Result<>();
            api.getMembershipCatalog(token, membership);
            assertError(membership.error, CheckinCenterClient.Operation.BILLING_CATALOG);
            api.redeemMembership(token, "ABCDEFGHJKLMNPQR", membership);
            assertError(membership.error, CheckinCenterClient.Operation.BILLING_REDEEM);
            Result<List<CheckinBilling.OrderRecord>> records = new Result<>();
            api.getPurchaseHistory(token, records);
            assertError(records.error, CheckinCenterClient.Operation.BILLING_HISTORY);
            Result<CheckinBilling.Order> order = new Result<>();
            api.createMembershipOrder(token, "heybox_custom", order);
            assertError(order.error, CheckinCenterClient.Operation.BILLING_CREATE);
        }
        assertEquals(0, requests.calls);
    }

    @Test
    public void authorizationFailureIsPassedThroughWithoutRetryOrMembershipMutation() throws Exception {
        RecordingRequests requests = new RecordingRequests(catalogResponse(true));
        requests.failure = new CheckinCenterClient.ApiError(
                CheckinCenterClient.Operation.BILLING_REDEEM, 401, "invalid authorization");
        Result<CheckinBilling.Membership> result = new Result<>();

        new CheckinMembershipApi(requests).redeemMembership(TOKEN, "ABCDEFGHJKLMNPQR", result);

        assertEquals(requests.failure, result.error);
        assertTrue(result.error.authorizationInvalid());
        assertNull(result.value);
        assertEquals(1, requests.calls);
    }

    @Test
    public void malformedMembershipResponseDoesNotBecomeFreeEntitlement() throws Exception {
        RecordingRequests requests = new RecordingRequests(new JSONObject());
        CheckinMembershipApi api = new CheckinMembershipApi(requests);
        Result<CheckinBilling.Membership> result = new Result<>();
        api.getMembershipCatalog(TOKEN, result);
        assertError(result.error, CheckinCenterClient.Operation.BILLING_CATALOG);
        assertNull(result.value);

        api.redeemMembership(TOKEN, "ABCDEFGHJKLMNPQR", result);
        assertError(result.error, CheckinCenterClient.Operation.BILLING_REDEEM);
        requests.response = new JSONObject().put("billing_mode", "paid")
                .put("checkout_available", true);
        api.getMembershipCatalog(TOKEN, result);
        assertNull(result.error);
        assertFalse(result.value.entitled);
    }

    @Test
    public void unsafeCheckoutIsRejectedAsProtocolFailureNotFetched() throws Exception {
        RecordingRequests requests = new RecordingRequests(orderResponse()
                .put("checkout_url", "https://untrusted.example/private-checkout"));
        Result<CheckinBilling.Order> result = new Result<>();

        new CheckinMembershipApi(requests).createMembershipOrder(TOKEN, "heybox_custom", result);

        assertError(result.error, CheckinCenterClient.Operation.BILLING_CREATE);
        assertFalse(result.error.getMessage().contains("private-checkout"));
        assertNull(result.value);
        assertEquals(1, requests.calls);
        assertEquals("/billing/orders", requests.path);
    }

    private static void assertRequest(RecordingRequests requests,
                                      CheckinCenterClient.Operation operation,
                                      String method, String path) {
        assertEquals(operation, requests.operation);
        assertEquals(method, requests.method);
        assertEquals(path, requests.path);
        assertEquals(TOKEN, requests.token);
        assertFalse(requests.path.contains(TOKEN));
        assertFalse(requests.path.contains("?"));
    }

    private static void assertOnlyBodyKey(JSONObject body, String key, String value)
            throws Exception {
        assertEquals(1, body.length());
        assertEquals(value, body.getString(key));
        assertFalse(body.has("amount_cents"));
        assertFalse(body.has("amount"));
        assertFalse(body.has("provider"));
        assertFalse(body.has("checkout_provider"));
        assertFalse(body.has("token"));
        assertFalse(body.has("device_token"));
        assertFalse(body.has("Authorization"));
        assertFalse(body.has("cookie"));
        assertFalse(body.has("pkey"));
        assertFalse(body.toString().contains(TOKEN));
    }

    private static void assertError(CheckinCenterClient.ApiError error,
                                    CheckinCenterClient.Operation operation) {
        assertNotNull(error);
        assertEquals(operation, error.operation);
        assertEquals(CheckinCenterClient.ErrorKind.PROTOCOL, error.kind);
        assertFalse(error.getMessage().contains(TOKEN));
    }

    private static JSONObject catalogResponse(boolean entitled) throws Exception {
        return new JSONObject().put("billing_mode", "paid").put("entitled", entitled)
                .put("subscription_required", true).put("checkout_available", true)
                .put("checkout_provider", "afdian").put("expires_at", "2027-01-04T01:00:00Z")
                .put("products", new JSONArray().put(new JSONObject().put("sku", "heybox_custom")
                        .put("name", "Custom plan").put("amount_cents", 1887)
                        .put("currency", "CNY").put("duration_days", 93).put("active", true)));
    }

    private static JSONObject orderResponse() throws Exception {
        return new JSONObject().put("order_id", ORDER_ID).put("provider", "afdian")
                .put("product_sku", "heybox_custom").put("amount_cents", 1887)
                .put("payable_amount_cents", 1889).put("currency", "CNY").put("status", "pending")
                .put("checkout_url", "https://afdian.com/order/create?custom_order_id=" + ORDER_ID);
    }

    private static final class Result<T> implements CheckinCenterClient.Callback<T> {
        T value;
        CheckinCenterClient.ApiError error;

        @Override
        public void onSuccess(T value) {
            this.value = value;
            this.error = null;
        }

        @Override
        public void onError(CheckinCenterClient.ApiError error) {
            this.error = error;
        }
    }

    private static final class RecordingRequests implements CheckinMembershipApi.AccountRequests {
        CheckinCenterClient.Operation operation;
        String method;
        String path;
        String token;
        JSONObject body;
        JSONObject response;
        CheckinCenterClient.ApiError failure;
        int calls;

        RecordingRequests(JSONObject response) {
            this.response = response;
        }

        @Override
        public <T> void request(CheckinCenterClient.Operation operation, String method, String path,
                                String token, JSONObject body, CheckinCenterClient.Parser<T> parser,
                                CheckinCenterClient.Callback<T> callback) {
            this.operation = operation;
            this.method = method;
            this.path = path;
            this.token = token;
            this.body = body;
            calls++;
            try {
                if (failure != null) throw failure;
                if (callback != null) callback.onSuccess(parser.parse(response));
            } catch (CheckinCenterClient.ApiError error) {
                if (callback != null) callback.onError(error);
            } catch (JSONException error) {
                if (callback != null) callback.onError(CheckinCenterClient.protocolError(operation));
            }
        }
    }
}
