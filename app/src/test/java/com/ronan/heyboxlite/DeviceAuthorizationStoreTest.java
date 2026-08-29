package com.ronan.heyboxlite;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DeviceAuthorizationStoreTest {
    @Test
    public void acceptsOnlyServerDeviceTokens() {
        assertTrue(DeviceAuthorizationStore.validToken(
                "hblite_device_abcdefghijklmnopqrstuvwxyz0123456789ABCDE"));
        assertFalse(DeviceAuthorizationStore.validToken(
                "ccdevice1_abcdefghijklmnopqrstuvwxyz0123456789ABCDE"));
        assertFalse(DeviceAuthorizationStore.validToken("hblite_device_short"));
        assertFalse(DeviceAuthorizationStore.validToken(
                "hblite_device_abcdefghijklmnopqrstuvwxyz0123456789ABCD!"));
    }
}
