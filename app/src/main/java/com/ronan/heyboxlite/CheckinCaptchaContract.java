package com.ronan.heyboxlite;

import org.json.JSONObject;

import java.net.URI;
import java.net.URLDecoder;

final class CheckinCaptchaContract {
    static final String PROMPT_PREFIX = "heyboxlite-captcha:";

    static final class Result {
        final boolean successful;
        final String ticket;
        final String randstr;
        final String diagnosticCode;

        private Result(boolean successful, String ticket, String randstr,
                       String diagnosticCode) {
            this.successful = successful;
            this.ticket = ticket;
            this.randstr = randstr;
            this.diagnosticCode = diagnosticCode;
        }

        static Result success(String ticket, String randstr) {
            return new Result(true, ticket, randstr, "");
        }

        static Result failure(String diagnosticCode) {
            return new Result(false, "", "", diagnosticCode);
        }
    }

    private CheckinCaptchaContract() {}

    static boolean isTrustedPageUri(String value) {
        try {
            URI uri = new URI(value == null ? "" : value.trim());
            return "https".equals(uri.getScheme())
                    && "heyboxlite.xyz".equals(uri.getHost())
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getUserInfo() == null
                    && uri.getFragment() == null
                    && "/checkin/lite/captcha".equals(uri.getRawPath())
                    && uri.getRawQuery() != null
                    && uri.getRawQuery().matches("appid=[0-9]{5,20}");
        } catch (Exception ignored) {
            return false;
        }
    }

    static Result parsePrompt(String pageUrl, String message) {
        if (!isTrustedPageUri(pageUrl)
                || message == null
                || !message.startsWith(PROMPT_PREFIX)) {
            return null;
        }
        try {
            String encoded = message.substring(PROMPT_PREFIX.length());
            JSONObject value = new JSONObject(URLDecoder.decode(encoded, "UTF-8"));
            int ret = value.optInt("ret", 1);
            if (ret != 0) {
                int errorCode = value.optInt("error_code", -1);
                return Result.failure(providerFailureCode(value, ret, errorCode));
            }
            String ticket = value.optString("ticket", "").trim();
            String randstr = value.optString("randstr", "").trim();
            return CheckinCenterClient.captchaProofValid(ticket, randstr)
                    && !ticket.isEmpty()
                    ? Result.success(ticket, randstr)
                    : Result.failure("invalid_proof");
        } catch (Exception ignored) {
            return Result.failure("invalid_result");
        }
    }

    private static String providerFailureCode(JSONObject value, int ret, int errorCode) {
        String stage = diagnosticMarker(value.optString("loader_stage", ""));
        String reason = diagnosticMarker(value.optString("error_reason", ""));
        String source = diagnosticMarker(value.optString("sdk_source", ""));
        if (stage.isEmpty() && reason.isEmpty() && source.isEmpty()) {
            return errorCode < 0
                    ? "provider_" + ret
                    : "provider_" + ret + "_" + errorCode;
        }

        StringBuilder code = new StringBuilder("captcha");
        appendMarker(code, reason);
        appendMarker(code, stage);
        appendMarker(code, source);
        if (errorCode >= 0) appendMarker(code, String.valueOf(errorCode));
        return code.length() <= 48 ? code.toString() : code.substring(0, 48);
    }

    private static void appendMarker(StringBuilder target, String marker) {
        if (!marker.isEmpty()) target.append('_').append(marker);
    }

    private static String diagnosticMarker(String value) {
        if (value == null || !value.matches("[a-zA-Z0-9:._-]{1,64}")) return "";
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }
}
