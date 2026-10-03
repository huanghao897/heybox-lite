package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Map;

public class SessionCookieCodecTest {
    @Test
    public void normalizeAddsExpectedAuthenticationAliases() {
        String normalized = SessionCookieCodec.normalize(
                SecureStrings.userPkey() + "=secret; "
                        + SecureStrings.userHeyboxId() + "=42; extra=value");

        Map<String, String> values = SessionCookieCodec.parse(normalized);
        assertEquals("secret", values.get(SecureStrings.xPkey()));
        assertEquals("secret", values.get(com.max.xiaoheihe.utils.p0.M()));
        assertEquals("42", values.get(SecureStrings.xHeyboxId()));
        assertEquals("value", values.get("extra"));
    }

    @Test
    public void userIdAndLogKeysNeverExposeCookieValues() {
        String cookie = SecureStrings.xHeyboxId() + "=42; "
                + SecureStrings.xXhhTokenId() + "=sensitive-cookie-value";

        assertEquals("42", SessionCookieCodec.userId(cookie));
        String keys = SessionCookieCodec.authKeysForLog(cookie);
        assertTrue(keys.contains(SecureStrings.xHeyboxId()));
        assertTrue(keys.contains(SecureStrings.xXhhTokenId()));
        assertTrue(!keys.contains("42"));
        assertTrue(!keys.contains("sensitive-cookie-value"));
    }
}
