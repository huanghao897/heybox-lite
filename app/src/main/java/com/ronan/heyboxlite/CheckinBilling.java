package com.ronan.heyboxlite;

import org.json.JSONObject;

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

        Membership(String mode, boolean required, boolean entitled, boolean admin,
                   String expiresAt, boolean checkoutAvailable,
                   boolean voluntarySponsorship, Plan plan) {
            this.mode = mode;
            this.required = required;
            this.entitled = entitled;
            this.admin = admin;
            this.expiresAt = expiresAt;
            this.checkoutAvailable = checkoutAvailable;
            this.voluntarySponsorship = voluntarySponsorship;
            this.plan = plan;
        }

        static Membership freeDefault() {
            return new Membership("free", false, true, false, "", false,
                    true, Plan.empty());
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

        Order(String id, String provider, int amountCents, int payableAmountCents,
              String currency, String status, boolean qrReady, boolean manualReview,
              String expiresAt, Review review) {
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
                    && sameReview(review, other.review);
        }
    }

    static Membership parseMembership(JSONObject value) {
        JSONObject plan = value.optJSONObject("plan");
        String mode = value.optString("billing_mode", "free");
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
                value.optBoolean("subscription_required", false),
                value.optBoolean("entitled", true),
                value.optBoolean("is_admin", false),
                nullable(value, "expires_at"),
                value.optBoolean("checkout_available", false),
                value.optBoolean("voluntary_sponsorship", "free".equals(mode)),
                parsedPlan);
    }

    static Order parseOrder(JSONObject value) throws CheckinCenterClient.ApiError {
        String id = value.optString("order_id", "");
        if (!validOrderId(id)) throw new CheckinCenterClient.ApiError(
                CheckinCenterClient.Operation.BILLING_STATUS, 0, "赞助记录响应异常");
        return new Order(id, value.optString("provider", ""),
                boundedAmount(value.optInt("amount_cents", 0), 0),
                boundedAmount(value.optInt("payable_amount_cents", 0), 0),
                value.optString("currency", "CNY"), value.optString("status", ""),
                value.optBoolean("qr_ready", false), value.optBoolean("manual_review", false),
                value.optString("expires_at", ""), parseReview(value.optJSONObject("review")));
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
