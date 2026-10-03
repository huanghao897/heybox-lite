package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class CheckinBillingTest {
    private static final String ORDER_ID = "HBAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String CHECKOUT = "https://afdian.com/order/create?plan_id=plan"
            + "&custom_order_id=" + ORDER_ID;

    @Test
    public void membershipParsesServerProductsWithoutReplacingTheirPriceOrPeriod() throws Exception {
        JSONObject response = new JSONObject().put("billing_mode", "paid")
                .put("subscription_required", true).put("entitled", false)
                .put("checkout_available", true).put("checkout_provider", "afdian")
                .put("plan", new JSONObject().put("name", "Legacy plan")
                        .put("amount_cents", 500).put("duration_days", 30))
                .put("products", new JSONArray()
                        .put(product("heybox_quarterly", "Quarterly", 1388, "CNY", 92, true))
                        .put(product("heybox_yearly", "Annual", 5200, "CNY", 366, true))
                        .put(product("retired", "Retired", 321, "USD", 17, false)));

        CheckinBilling.Membership membership = CheckinBilling.parseMembership(response);

        assertEquals("afdian", membership.checkoutProvider);
        assertEquals(500, membership.plan.amountCents);
        assertEquals(30, membership.plan.durationDays);
        assertEquals(3, membership.products.size());
        CheckinBilling.Product quarterly = membership.products.get(0);
        assertEquals("heybox_quarterly", quarterly.sku);
        assertEquals("Quarterly", quarterly.name);
        assertEquals(1388, quarterly.amountCents);
        assertEquals("CNY", quarterly.currency);
        assertEquals(92, quarterly.durationDays);
        assertTrue(quarterly.active);
        assertEquals(5200, membership.products.get(1).amountCents);
        assertEquals(366, membership.products.get(1).durationDays);
        assertFalse(membership.products.get(2).active);
        assertEquals("USD", membership.products.get(2).currency);
        assertThrows(UnsupportedOperationException.class, () -> membership.products.clear());
    }

    @Test
    public void paidModeNeverInfersEntitlementFromCheckoutAdminOrExpiry() throws Exception {
        JSONObject response = new JSONObject().put("billing_mode", "paid")
                .put("checkout_available", true).put("checkout_provider", "afdian")
                .put("is_admin", true).put("expires_at", "2099-01-01T00:00:00Z");

        assertTrue(CheckinBilling.parseMembership(response).required);
        assertFalse(CheckinBilling.parseMembership(response).entitled);
        response.put("entitled", JSONObject.NULL);
        assertFalse(CheckinBilling.parseMembership(response).entitled);
        response.put("entitled", "unexpected");
        assertFalse(CheckinBilling.parseMembership(response).entitled);
        response.put("entitled", false);
        assertFalse(CheckinBilling.parseMembership(response).entitled);
        response.put("entitled", true);
        assertTrue(CheckinBilling.parseMembership(response).entitled);
    }

    @Test
    public void freeAndLegacyMembershipDefaultsRemainCompatible() throws Exception {
        CheckinBilling.Membership parsed = CheckinBilling.parseMembership(new JSONObject());
        assertTrue(parsed.entitled);
        assertFalse(parsed.required);
        assertEquals("", parsed.checkoutProvider);
        assertTrue(parsed.products.isEmpty());
        assertEquals(500, parsed.plan.amountCents);
        assertTrue(parsed.plan.variableAmount);

        CheckinBilling.Plan plan = new CheckinBilling.Plan("Legacy", 222, "CNY", 13,
                false, 1, 1000);
        CheckinBilling.Membership legacy = new CheckinBilling.Membership("paid", true,
                false, false, "", true, false, plan);
        assertEquals(plan, legacy.plan);
        assertTrue(legacy.products.isEmpty());
        assertEquals("", legacy.checkoutProvider);
        assertTrue(CheckinBilling.Membership.freeDefault().entitled);
        assertFalse(CheckinBilling.parseMembership(new JSONObject()
                .put("billing_mode", "free").put("subscription_required", true)).entitled);
        assertEquals("", CheckinBilling.parseMembership(new JSONObject()
                .put("checkout_provider", JSONObject.NULL)).checkoutProvider);
    }

    @Test
    public void membershipDefensivelyCopiesProductsAndDoesNotSynthesizeMissingProducts() {
        List<CheckinBilling.Product> products = new ArrayList<>();
        products.add(new CheckinBilling.Product("server_sku", "Server plan", 1432,
                "CNY", 43, true));
        CheckinBilling.Membership membership = new CheckinBilling.Membership("paid", true,
                false, false, "", true, false, CheckinBilling.Plan.empty(), products, "afdian");
        products.clear();
        assertEquals(1, membership.products.size());
        assertTrue(CheckinBilling.parseMembership(new JSONObject()).products.isEmpty());
    }

    @Test
    public void malformedProductsCannotBecomePurchasableZeroPricePlans() throws Exception {
        JSONArray products = new JSONArray().put(JSONObject.NULL).put("not a product")
                .put(product("negative", "Negative", -1, "CNY", 30, true))
                .put(product("overflow", "Overflow", 500, "CNY", 30, true)
                        .put("amount_cents", 4_294_967_796L))
                .put(product("missing_amount", "Missing", 500, "CNY", 30, true)
                        .put("amount_cents", JSONObject.NULL))
                .put(product("bad_days", "Invalid days", 500, "CNY", -1, true))
                .put(product("missing_sku", "Missing SKU", 500, "CNY", 30, true)
                        .put("sku", JSONObject.NULL))
                .put(product("valid", "Valid", 1534, "CNY", 91, true));

        CheckinBilling.Membership membership = CheckinBilling.parseMembership(
                new JSONObject().put("billing_mode", "paid").put("products", products));

        assertEquals(1, membership.products.size());
        assertEquals("valid", membership.products.get(0).sku);
        JSONObject missingActive = product("safe", "Safe", 500, "CNY", 30, true);
        missingActive.remove("active");
        assertFalse(CheckinBilling.parseMembership(new JSONObject()
                .put("products", new JSONArray().put(missingActive))).products.get(0).active);
    }

    @Test
    public void orderParsesCheckoutAndAllAdditiveFields() throws Exception {
        CheckinBilling.Order order = CheckinBilling.parseOrder(orderResponse()
                .put("checkout_url", CHECKOUT).put("product_sku", "heybox_quarterly")
                .put("product_name", "Quarterly").put("duration_days", 92)
                .put("created_at", "2026-10-03T01:00:00Z"));

        assertEquals(ORDER_ID, order.id);
        assertEquals("afdian", order.provider);
        assertEquals(1388, order.amountCents);
        assertEquals(1388, order.payableAmountCents);
        assertEquals("CNY", order.currency);
        assertTrue(order.pending());
        assertTrue(order.qrReady);
        assertFalse(order.manualReview);
        assertEquals("2026-10-03T01:15:00Z", order.expiresAt);
        assertEquals(CHECKOUT, order.checkoutUrl);
        assertEquals("heybox_quarterly", order.productSku);
        assertEquals("Quarterly", order.productName);
        assertEquals(92, order.durationDays);
        assertEquals("2026-10-03T01:00:00Z", order.createdAt);
        assertNull(order.review);
    }

    @Test
    public void existingOrderShapeDoesNotGuessMissingNameOrDuration() throws Exception {
        CheckinBilling.Order parsed = CheckinBilling.parseOrder(orderResponse()
                .put("checkout_url", CHECKOUT).put("product_sku", "heybox_monthly"));
        assertEquals("", parsed.productName);
        assertEquals(0, parsed.durationDays);
        CheckinBilling.Order legacy = new CheckinBilling.Order(ORDER_ID, "monitor_wechat",
                500, 501, "CNY", "pending", true, true, "expiry", null);
        assertEquals("", legacy.checkoutUrl);
        assertEquals("", legacy.productSku);
        assertEquals("", legacy.productName);
        assertEquals(0, legacy.durationDays);
        assertEquals("", legacy.createdAt);
        assertEquals("", CheckinBilling.parseOrder(orderResponse()
                .put("checkout_url", JSONObject.NULL)).checkoutUrl);
    }

    @Test
    public void orderUiEqualityIncludesCheckoutProductPeriodAndCreation() throws Exception {
        JSONObject response = orderResponse().put("checkout_url", CHECKOUT)
                .put("product_sku", "heybox_quarterly").put("product_name", "Quarterly")
                .put("duration_days", 92).put("created_at", "created");
        CheckinBilling.Order original = CheckinBilling.parseOrder(response);
        assertTrue(original.sameUiState(CheckinBilling.parseOrder(response)));
        assertChanged(original, response, "checkout_url", CHECKOUT + "&variant=2");
        assertChanged(original, response, "product_sku", "heybox_yearly");
        assertChanged(original, response, "product_name", "Annual");
        assertChanged(original, response, "duration_days", 366);
        assertChanged(original, response, "created_at", "different");
    }

    @Test
    public void checkoutAcceptsOnlyExactHttpsHostsAndStandardPort() {
        assertTrue(CheckinBilling.isTrustedCheckoutUrl(CHECKOUT));
        assertTrue(CheckinBilling.isTrustedCheckoutUrl("https://afdian.net/order/create"));
        assertTrue(CheckinBilling.isTrustedCheckoutUrl("https://afdian.com:443/order/create"));
        assertTrue(CheckinBilling.isTrustedCheckoutUrl("https://AFDIAN.COM/order/create"));
        assertTrue(CheckinBilling.isTrustedCheckoutUrl("https://heyboxlite.xyz/checkin/store"));
        String[] rejected = {null, "", "http://afdian.com/order/create",
                "//afdian.com/order/create", "/order/create", "https://example.com/order/create",
                "https://afdian.com.example.com/order/create", "https://www.afdian.com/order/create",
                "https://api.heyboxlite.xyz/checkin/store", "https://afdian.com./order/create",
                "https://user@afdian.com/order/create", "https://user:password@afdian.net/",
                "https://afdian.com@evil.example/", "https://afdian.com:444/order/create",
                "https://afdian.com:80/order/create", "https://afdian.com:/order/create",
                "https://afdian.com:0443/order/create", "https://afdian.com/order/create#next",
                "https://afdian.com/%", "https://afdian.com/order/create\n",
                " https://afdian.com/order/create", "https://afdian.com\\@evil.example/"};
        for (String url : rejected) assertFalse(String.valueOf(url),
                CheckinBilling.isTrustedCheckoutUrl(url));
    }

    @Test
    public void unsafeCheckoutMakesOrderParsingFailWithoutEchoingTheUrl() throws Exception {
        String unsafe = "https://user:private-secret@afdian.com/order/create";
        JSONObject response = orderResponse().put("checkout_url", unsafe);
        CheckinCenterClient.ApiError error = assertThrows(CheckinCenterClient.ApiError.class,
                () -> CheckinBilling.parseOrder(response, CheckinCenterClient.Operation.BILLING_CREATE));
        assertEquals(CheckinCenterClient.Operation.BILLING_CREATE, error.operation);
        assertEquals(CheckinCenterClient.ErrorKind.PROTOCOL, error.kind);
        assertFalse(error.getMessage().contains("private-secret"));
        assertFalse(error.getMessage().contains(unsafe));
    }

    @Test
    public void purchaseRecordsUseAllServerFieldsAndKeepOrderExpirySeparate() throws Exception {
        JSONObject first = orderResponse().put("product_sku", "heybox_quarterly")
                .put("product_name", "Quarterly").put("payable_amount_cents", 1391)
                .put("duration_days", 92).put("created_at", "2026-10-03T01:00:00Z")
                .put("status", "paid");
        JSONObject second = new JSONObject(first.toString())
                .put("order_id", "HBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
                .put("product_sku", "retired").put("product_name", "Retired")
                .put("duration_days", 17).put("status", "expired");
        List<CheckinBilling.OrderRecord> records = CheckinBilling.parseOrderRecords(
                new JSONObject().put("items", new JSONArray().put(first).put(second)));

        CheckinBilling.OrderRecord record = records.get(0);
        assertEquals(ORDER_ID, record.orderId);
        assertEquals("heybox_quarterly", record.productSku);
        assertEquals("Quarterly", record.productName);
        assertEquals(1388, record.amountCents);
        assertEquals(1391, record.payableAmountCents);
        assertEquals("CNY", record.currency);
        assertEquals("paid", record.status);
        assertEquals("2026-10-03T01:00:00Z", record.createdAt);
        assertEquals("2026-10-03T01:15:00Z", record.expiresAt);
        assertEquals(92, record.durationDays);
        assertEquals("retired", records.get(1).productSku);
        assertEquals(17, records.get(1).durationDays);
        assertEquals("expired", records.get(1).status);
        assertThrows(UnsupportedOperationException.class, () -> records.clear());
    }

    @Test
    public void purchaseHistoryRejectsMalformedShapeInsteadOfPretendingItIsEmpty() throws Exception {
        assertTrue(CheckinBilling.parseOrderRecords(
                new JSONObject().put("items", new JSONArray())).isEmpty());
        assertThrows(CheckinCenterClient.ApiError.class,
                () -> CheckinBilling.parseOrderRecords(new JSONObject()));
        JSONObject malformed = new JSONObject().put("items", new JSONArray().put("bad item"));
        CheckinCenterClient.ApiError error = assertThrows(CheckinCenterClient.ApiError.class,
                () -> CheckinBilling.parseOrderRecords(malformed));
        assertEquals(CheckinCenterClient.Operation.BILLING_HISTORY, error.operation);
        assertThrows(CheckinCenterClient.ApiError.class,
                () -> CheckinBilling.parseOrderRecords(new JSONObject().put("items", new JSONArray()
                        .put(orderResponse().put("order_id", "../other-account")))));
    }

    private static JSONObject product(String sku, String name, int amount, String currency,
                                      int days, boolean active) throws Exception {
        return new JSONObject().put("sku", sku).put("name", name).put("amount_cents", amount)
                .put("currency", currency).put("duration_days", days).put("active", active);
    }

    private static JSONObject orderResponse() throws Exception {
        return new JSONObject().put("order_id", ORDER_ID).put("provider", "afdian")
                .put("amount_cents", 1388).put("payable_amount_cents", 1388)
                .put("currency", "CNY").put("status", "pending").put("qr_ready", true)
                .put("manual_review", false).put("expires_at", "2026-10-03T01:15:00Z");
    }

    private static void assertChanged(CheckinBilling.Order original, JSONObject response,
                                      String key, Object changed) throws Exception {
        JSONObject copy = new JSONObject(response.toString()).put(key, changed);
        assertFalse(original.sameUiState(CheckinBilling.parseOrder(copy)));
    }
}
