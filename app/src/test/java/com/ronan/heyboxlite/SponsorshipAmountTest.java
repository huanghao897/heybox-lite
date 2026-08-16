package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SponsorshipAmountTest {
    @Test
    public void parsesWholeAndDecimalYuan() {
        assertEquals(500, SponsorshipAmount.parseCents("5", 1, 100_000_000));
        assertEquals(1234, SponsorshipAmount.parseCents("12.34", 1, 100_000_000));
        assertEquals(120, SponsorshipAmount.parseCents("1.2", 1, 100_000_000));
    }

    @Test
    public void rejectsMalformedOrOutOfRangeAmounts() {
        assertEquals(-1, SponsorshipAmount.parseCents("", 1, 100_000_000));
        assertEquals(-1, SponsorshipAmount.parseCents("1.234", 1, 100_000_000));
        assertEquals(-1, SponsorshipAmount.parseCents("0", 1, 100_000_000));
        assertEquals(-1, SponsorshipAmount.parseCents("1000000.01", 1, 100_000_000));
    }

    @Test
    public void formatsAndValidatesProtocolAmounts() {
        assertEquals("5.00", SponsorshipAmount.formatYuan(500));
        assertEquals("12.34", SponsorshipAmount.formatYuan(1234));
        assertTrue(SponsorshipAmount.validCents(1));
        assertFalse(SponsorshipAmount.validCents(0));
    }
}
