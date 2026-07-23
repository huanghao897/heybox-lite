package com.ronan.heyboxlite;

import java.nio.charset.Charset;
import java.util.regex.Pattern;

final class DiagnosticSanitizer {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final int MAX_UPLOAD_BYTES = 28 * 1024;
    private static final Pattern SECRET_LINE = Pattern.compile(
            "(?im)^\\s*(?:cookie|set-cookie|authorization)\\s*[:=].*$");
    private static final Pattern SECRET_FIELD = Pattern.compile(
            "(?i)([\\\"']?(?:cookie|set-cookie|authorization)[\\\"']?"
                    + "\\s*[:=]\\s*)[^\\r\\n]+");
    private static final Pattern SECRET_VALUE = Pattern.compile(
            "(?i)([\\\"']?(?:pkey|user_pkey|x_pkey|x_xhh_tokenid|device_token|"
                    + "access_token|refresh_token|hkey|nonce)[\\\"']?\\s*[:=]\\s*[\\\"']?)"
                    + "[^\\s;,}&\\]\\\"']+");
    private static final Pattern BEARER = Pattern.compile(
            "(?i)(bearer\\s+)[a-z0-9._~-]{16,}");
    private static final Pattern PHONE = Pattern.compile(
            "(?i)([\\\"']?(?:phone|phone_num|phone_number|mobile|手机号)[\\\"']?"
                    + "\\s*[:=]\\s*[\\\"']?)\\+?[0-9 -]{6,20}");
    private static final Pattern SMS_CODE = Pattern.compile(
            "(?i)([\\\"']?(?:code|sms_code|verify_code|verification_code|验证码)"
                    + "[\\\"']?\\s*[:=]\\s*[\\\"']?)[0-9]{4,8}");

    private DiagnosticSanitizer() {}

    static String redact(String value) {
        String text = value == null ? "" : value;
        text = SECRET_LINE.matcher(text).replaceAll("<redacted header>");
        text = SECRET_FIELD.matcher(text).replaceAll("$1<redacted>");
        text = SECRET_VALUE.matcher(text).replaceAll("$1<redacted>");
        text = BEARER.matcher(text).replaceAll("$1<redacted>");
        text = PHONE.matcher(text).replaceAll("$1<redacted>");
        return SMS_CODE.matcher(text).replaceAll("$1<redacted>");
    }

    static String forUpload(String value) {
        String redacted = redact(value);
        if (redacted.getBytes(UTF_8).length <= MAX_UPLOAD_BYTES) return redacted;
        String marker = "\n... diagnostic report truncated ...\n";
        int prefixLength = Math.min(2_000, redacted.length());
        String prefix = redacted.substring(0, prefixLength);
        int suffixStart = Math.max(prefixLength, redacted.length() - 16_000);
        String result = prefix + marker + redacted.substring(suffixStart);
        while (result.getBytes(UTF_8).length > MAX_UPLOAD_BYTES && suffixStart < redacted.length()) {
            suffixStart = Math.min(redacted.length(), suffixStart + 512);
            result = prefix + marker + redacted.substring(suffixStart);
        }
        return result;
    }
}
