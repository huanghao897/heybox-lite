package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class CheckinBilling {
    private CheckinBilling() {}

    static final class Membership {
        final String mode;
        final boolean required;
        final boolean entitled;
        final boolean admin;
        final String expiresAt;
        final boolean checkoutAvailable;
        final boolean voluntarySponsorship;
        final Plan plan;
        final List<Product> products;
        final String checkoutProvider;

        Membership(String mode, boolean required, boolean entitled, boolean admin,
                   String expiresAt, boolean checkoutAvailable,
                   boolean voluntarySponsorship, Plan plan) {
            this(mode, required, entitled, admin, expiresAt, checkoutAvailable,
                    voluntarySponsorship, plan, Collections.emptyList(), "");
        }

        Membership(String mode, boolean required, boolean entitled, boolean admin,
                   String expiresAt, boolean checkoutAvailable,
                   boolean voluntarySponsorship, Plan plan, List<Product> products,
                   String checkoutProvider) {
            this.mode = mode;
            this.required = required;
            this.entitled = entitled;
            this.admin = admin;
            this.expiresAt = expiresAt;
            this.checkoutAvailable = checkoutAvailable;
            this.voluntarySponsorship = voluntarySponsorship;
            this.plan = plan;
            this.products = Collections.unmodifiableList(new ArrayList<>(products));
            this.checkoutProvider = checkoutProvider;
        }

        static Membership freeDefault() {
            return new Membership("free", false, true, false, "", false,
                    true, Plan.empty());
        }
    }

    static final class Product {
        final String sku;
        final String name;
        final int amountCents;
        final String currency;
        final int durationDays;
        final boolean active;

        Product(String sku, String name, int amountCents, String currency,
                int durationDays, boolean active) {
            this.sku = sku;
            this.name = name;
            this.amountCents = amountCents;
            this.currency = currency;
            this.durationDays = durationDays;
            this.active = active;
        }
    }

    static final class Plan {
        final String name;
        final int amountCents;
        final String currency;
        final int durationDays;
        final boolean variableAmount;
        final int minimumAmountCents;
        final int maximumAmountCents;

        Plan(String name, int amountCents, String currency, int durationDays,
             boolean variableAmount, int minimumAmountCents, int maximumAmountCents) {
            this.name = name;
            this.amountCents = amountCents;
            this.currency = currency;
            this.durationDays = durationDays;
            this.variableAmount = variableAmount;
            this.minimumAmountCents = minimumAmountCents;
            this.maximumAmountCents = maximumAmountCents;
        }

        static Plan empty() {
            return new Plan("服务器自愿赞助", 500, "CNY", 0,
                    true, 1, 100_000_000);
        }
    }

    static final class Review {
        final String claimId;
        final String paymentReference;
        final String status;
        final String reason;

        Review(String claimId, String paymentReference, String status, String reason) {
            this.claimId = claimId;
            this.paymentReference = paymentReference;
            this.status = status;
            this.reason = reason;
        }

        boolean pending() {
            return "pending".equals(status);
        }

    }

    private static boolean sameReview(Review left, Review right) {
        if (left == right) return true;
        return left != null && right != null
                && left.claimId.equals(right.claimId)
                && left.paymentReference.equals(right.paymentReference)
                && left.status.equals(right.status)
                && left.reason.equals(right.reason);
    }

    static final class Order {
        final String id;
        final String provider;
        final int amountCents;
        final int payableAmountCents;
        final String currency;
        final String status;
        final boolean qrReady;
        final boolean manualReview;
        final String expiresAt;
        final Review review;
        final String checkoutUrl;
        final String productSku;
        final String productName;
        final int durationDays;
        final String createdAt;

        Order(String id, String provider, int amountCents, int payableAmountCents,
              String currency, String status, boolean qrReady, boolean manualReview,
              String expiresAt, Review review) {
            this(id, provider, amountCents, payableAmountCents, currency, status,
                    qrReady, manualReview, expiresAt, review, "", "", "", 0, "");
        }

        Order(String id, String provider, int amountCents, int payableAmountCents,
              String currency, String status, boolean qrReady, boolean manualReview,
              String expiresAt, Review review, String checkoutUrl, String productSku,
              String productName, int durationDays, String createdAt) {
            this.id = id;
            this.provider = provider;
            this.amountCents = amountCents;
            this.payableAmountCents = payableAmountCents;
            this.currency = currency;
            this.status = status;
            this.qrReady = qrReady;
            this.manualReview = manualReview;
            this.expiresAt = expiresAt;
            this.review = review;
            this.checkoutUrl = checkoutUrl;
            this.productSku = productSku;
            this.productName = productName;
            this.durationDays = durationDays;
            this.createdAt = createdAt;
        }

        boolean pending() {
            return "pending".equals(status);
        }

        boolean sameUiState(Order other) {
            return other != null && id.equals(other.id)
                    && provider.equals(other.provider)
                    && amountCents == other.amountCents
                    && payableAmountCents == other.payableAmountCents
                    && currency.equals(other.currency)
                    && status.equals(other.status)
                    && qrReady == other.qrReady
                    && manualReview == other.manualReview
                    && expiresAt.equals(other.expiresAt)
                    && checkoutUrl.equals(other.checkoutUrl)
                    && productSku.equals(other.productSku)
                    && productName.equals(other.productName)
                    && durationDays == other.durationDays
                    && createdAt.equals(other.createdAt)
                    && sameReview(review, other.review);
        }
    }

    static final class OrderRecord {
        final String orderId;
        final String productSku;
        final String productName;
        final int amountCents;
        final int payableAmountCents;
        final String currency;
        final String status;
        final String createdAt;
        final String expiresAt;
        final int durationDays;

        OrderRecord(String orderId, String productSku, String productName, int amountCents,
                    int payableAmountCents, String currency, String status, String createdAt,
                    String expiresAt, int durationDays) {
            this.orderId = orderId;
            this.productSku = productSku;
            this.productName = productName;
            this.amountCents = amountCents;
            this.payableAmountCents = payableAmountCents;
            this.currency = currency;
            this.status = status;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
            this.durationDays = durationDays;
        }
    }

    static Membership parseMembership(JSONObject value) {
        JSONObject plan = value.optJSONObject("plan");
        String mode = value.optString("billing_mode", "free");
        boolean required = value.optBoolean("subscription_required", "paid".equals(mode));
        Plan parsedPlan = plan == null ? Plan.empty() : new Plan(
                plan.optString("name", "服务器自愿赞助"),
                boundedAmount(plan.optInt("amount_cents", 500), 500),
                plan.optString("currency", "CNY"),
                boundedInt(plan.optInt("duration_days", 0)),
                plan.optBoolean("variable_amount", true),
                boundedAmount(plan.optInt("minimum_amount_cents", 1), 1),
                boundedAmount(plan.optInt("maximum_amount_cents", 100_000_000),
                        100_000_000));
        return new Membership(
                mode,
                required,
                value.optBoolean("entitled", "free".equals(mode) && !required),
                value.optBoolean("is_admin", false),
                nullable(value, "expires_at"),
                value.optBoolean("checkout_available", false),
                value.optBoolean("voluntary_sponsorship", "free".equals(mode)),
                parsedPlan, parseProducts(value.optJSONArray("products")),
                nullable(value, "checkout_provider"));
    }

    static Order parseOrder(JSONObject value) throws CheckinCenterClient.ApiError {
        return parseOrder(value, CheckinCenterClient.Operation.BILLING_STATUS);
    }

    static Order parseOrder(JSONObject value, CheckinCenterClient.Operation operation)
            throws CheckinCenterClient.ApiError {
        String id = value.optString("order_id", "");
        if (!validOrderId(id)) throw CheckinCenterClient.protocolError(operation);
        String checkoutUrl = nullable(value, "checkout_url");
        if (!checkoutUrl.isEmpty() && !isTrustedCheckoutUrl(checkoutUrl)) {
            throw CheckinCenterClient.protocolError(operation);
        }
        return new Order(id, value.optString("provider", ""),
                boundedAmount(value.optInt("amount_cents", 0), 0),
                boundedAmount(value.optInt("payable_amount_cents", 0), 0),
                value.optString("currency", "CNY"), value.optString("status", ""),
                value.optBoolean("qr_ready", false), value.optBoolean("manual_review", false),
                nullable(value, "expires_at"), parseReview(value.optJSONObject("review")),
                checkoutUrl, nullable(value, "product_sku"), nullable(value, "product_name"),
                boundedInt(value.optInt("duration_days", 0)), nullable(value, "created_at"));
    }

    static List<OrderRecord> parseOrderRecords(JSONObject value)
            throws CheckinCenterClient.ApiError {
        CheckinCenterClient.Operation operation = CheckinCenterClient.Operation.BILLING_HISTORY;
        JSONArray items = value.optJSONArray("items");
        if (items == null) throw CheckinCenterClient.protocolError(operation);
        List<OrderRecord> records = new ArrayList<>();
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.optJSONObject(index);
            if (item == null || !validOrderId(item.optString("order_id", ""))) {
                throw CheckinCenterClient.protocolError(operation);
            }
            records.add(new OrderRecord(item.optString("order_id"),
                    nullable(item, "product_sku"), nullable(item, "product_name"),
                    boundedAmount(item.optInt("amount_cents", 0), 0),
                    boundedAmount(item.optInt("payable_amount_cents", 0), 0),
                    item.optString("currency", "CNY"), item.optString("status", ""),
                    nullable(item, "created_at"), nullable(item, "expires_at"),
                    boundedInt(item.optInt("duration_days", 0))));
        }
        return Collections.unmodifiableList(records);
    }

    static boolean isTrustedCheckoutUrl(String value) {
        if (value == null || value.isEmpty() || value.length() > 4096) return false;
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            boolean trustedHost = "afdian.com".equals(host) || "afdian.net".equals(host)
                    || CheckinCenterClient.TRUSTED_HOST.equals(host);
            String authority = uri.getRawAuthority();
            // Validate only; checkout links are QR data, never network destinations.
            return "https".equals(uri.getScheme()) && trustedHost
                    && uri.getRawUserInfo() == null && uri.getRawFragment() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && (host.equalsIgnoreCase(authority)
                    || (host + ":443").equalsIgnoreCase(authority));
        } catch (URISyntaxException error) {
            return false;
        }
    }

    private static List<Product> parseProducts(JSONArray values) {
        List<Product> products = new ArrayList<>();
        if (values == null) return products;
        for (int index = 0; index < values.length(); index++) {
            JSONObject item = values.optJSONObject(index);
            if (item == null) continue;
            String sku = nullable(item, "sku");
            String name = nullable(item, "name");
            String currency = nullable(item, "currency");
            long amount = item.optLong("amount_cents", -1);
            long days = item.optLong("duration_days", -1);
            if (sku.isEmpty() || name.isEmpty() || currency.isEmpty()
                    || amount < 0 || amount > 100_000_000 || days < 0 || days > 1_000_000) {
                continue;
            }
            products.add(new Product(sku, name, (int) amount, currency, (int) days,
                    item.optBoolean("active", false)));
        }
        return products;
    }

    static Review parseClaim(JSONObject value) throws CheckinCenterClient.ApiError {
        Review review = parseReview(value.optJSONObject("review"));
        if (review == null) throw new CheckinCenterClient.ApiError(
                CheckinCenterClient.Operation.BILLING_CLAIM, 0, "赞助审核响应异常");
        return review;
    }

    static boolean validOrderId(String value) {
        return value != null && value.matches("HB[A-F0-9]{30}");
    }

    static boolean validPaymentReference(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{6,80}");
    }

    private static Review parseReview(JSONObject value) {
        if (value == null) return null;
        return new Review(value.optString("claim_id", ""),
                value.optString("payment_reference", ""), value.optString("status", ""),
                value.optString("reason", ""));
    }

    private static String nullable(JSONObject value, String key) {
        return value.isNull(key) ? "" : value.optString(key, "");
    }

    private static int boundedInt(int value) {
        return value >= 0 && value <= 1_000_000 ? value : 0;
    }

    private static int boundedAmount(int value, int fallback) {
        return value >= 0 && value <= 100_000_000 ? value : fallback;
    }
}
