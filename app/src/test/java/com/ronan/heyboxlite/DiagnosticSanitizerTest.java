package com.ronan.heyboxlite;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class DiagnosticSanitizerTest {
    @Test
    public void redactsSensitiveHeadersAndStructuredFields() {
        String input = "Authorization: Bearer ccdevice1_secret-value-1234567890\n"
                + "{\"cookie\":\"pkey=private; x_xhh_tokenid=token\","
                + "\"phone\":\"+86 13800000000\",\"code\":\"654321\","
                + "\"nonce\":\"nonce-value\"}";

        String output = DiagnosticSanitizer.redact(input);

        assertFalse(output.contains("ccdevice1_secret"));
        assertFalse(output.contains("private"));
        assertFalse(output.contains("13800000000"));
        assertFalse(output.contains("654321"));
        assertFalse(output.contains("nonce-value"));
        assertTrue(output.contains("<redacted>"));
    }

    @Test
    public void uploadReportStaysWithinJsonSafeBudget() {
        StringBuilder input = new StringBuilder();
        for (int index = 0; index < 80_000; index++) input.append('\\');

        byte[] output = DiagnosticSanitizer.forUpload(input.toString())
                .getBytes(StandardCharsets.UTF_8);

        assertTrue(output.length <= 28 * 1024);
    }
}
