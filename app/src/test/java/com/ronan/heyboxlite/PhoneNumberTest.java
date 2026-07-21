package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PhoneNumberTest {
    @Test
    public void normalizesChineseMobileNumber() {
        assertEquals("+8613812345678",
                PhoneNumber.normalizeChineseMobile("138 1234 5678"));
        assertEquals("+8613812345678",
                PhoneNumber.normalizeChineseMobile("+86-138-1234-5678"));
        assertEquals("+8613812345678",
                PhoneNumber.normalizeChineseMobile("8613812345678"));
    }

    @Test
    public void rejectsInvalidMobileNumber() {
        assertEquals("", PhoneNumber.normalizeChineseMobile("12345"));
        assertEquals("", PhoneNumber.normalizeChineseMobile("02812345678"));
    }

    @Test
    public void validatesVerificationCode() {
        assertTrue(PhoneNumber.isVerificationCode("123456"));
        assertFalse(PhoneNumber.isVerificationCode("12a456"));
        assertFalse(PhoneNumber.isVerificationCode("123"));
    }
}
