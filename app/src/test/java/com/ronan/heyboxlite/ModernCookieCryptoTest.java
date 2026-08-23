package com.ronan.heyboxlite;

import static org.junit.Assert.assertThrows;

import java.security.GeneralSecurityException;

import org.junit.Test;

public class ModernCookieCryptoTest {
    @Test
    public void rejectsTruncatedPayload() {
        assertThrows(GeneralSecurityException.class,
                () -> ModernCookieCrypto.validatePacked(new byte[]{12, 1, 2}));
    }

    @Test
    public void rejectsInvalidIvLength() {
        byte[] payload = new byte[1 + 11 + 16];
        payload[0] = 11;
        assertThrows(GeneralSecurityException.class,
                () -> ModernCookieCrypto.validatePacked(payload));
    }

    @Test
    public void rejectsPayloadWithoutAuthenticationTag() {
        byte[] payload = new byte[1 + 12 + 15];
        payload[0] = 12;
        assertThrows(GeneralSecurityException.class,
                () -> ModernCookieCrypto.validatePacked(payload));
    }
}
