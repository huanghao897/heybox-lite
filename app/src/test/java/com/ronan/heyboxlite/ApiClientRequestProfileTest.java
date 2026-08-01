package com.ronan.heyboxlite;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ApiClientRequestProfileTest {
    @Test
    public void mergedProfileUsesNativeSignerWithoutNativeUrlOverride() {
        ApiClient.RequestProfile profile =
                ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_MERGED;

        assertTrue(profile.usesOfficialNativeClient());
        assertFalse(profile.forcesFallbackSigner());
        assertFalse(profile.usesNativeUrlOverride());
        assertFalse(profile.includesClientKeys());
    }

    @Test
    public void fallbackKeysProfileKeepsAllNativeFlags() {
        ApiClient.RequestProfile profile =
                ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_FALLBACK_KEYS;

        assertTrue(profile.usesOfficialNativeClient());
        assertTrue(profile.forcesFallbackSigner());
        assertTrue(profile.usesNativeUrlOverride());
        assertTrue(profile.includesClientKeys());
    }

    @Test
    public void sparseClientUsesClientKeysWithoutNativeSigner() {
        ApiClient.RequestProfile profile = ApiClient.RequestProfile.OFFICIAL_SPARSE_CLIENT;

        assertFalse(profile.usesOfficialNativeClient());
        assertFalse(profile.forcesFallbackSigner());
        assertFalse(profile.usesNativeUrlOverride());
        assertTrue(profile.includesClientKeys());
    }
}
