package com.ronan.heyboxlite;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import static com.ronan.heyboxlite.DeviceAuthorizationTestFixtures.NEXT_TOKEN;
import static com.ronan.heyboxlite.DeviceAuthorizationTestFixtures.TOKEN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class,
        shadows = DeviceAuthorizationTestFixtures.Crypto.class,
        instrumentedPackages = "com.ronan.heyboxlite")
public class DeviceAuthorizationStoreTest {
    private Context context;
    private SharedPreferences preferences;
    private DeviceAuthorizationStore store;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        preferences = context.getSharedPreferences("heybox_server_device", Context.MODE_PRIVATE);
        DeviceAuthorizationTestFixtures.Crypto.reset();
        store = new DeviceAuthorizationStore(context);
        store.clear();
    }

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

    @Test public void storesOnlyCryptoOutputAndSharesVerifiedCacheAcrossStores() throws Exception {
        store.save(TOKEN);
        String encrypted = preferences.getString("device_token_encrypted", "");
        assertTrue(encrypted.startsWith("HBLITE1:fixture:"));
        assertFalse(encrypted.contains(TOKEN));
        assertEquals(TOKEN, new DeviceAuthorizationStore(context).token());
        assertEquals(0, DeviceAuthorizationTestFixtures.Crypto.decryptCalls);
    }

    @Test public void temporaryKeyFailureDoesNotDeleteSavedAuthorization() throws Exception {
        putUncachedToken(TOKEN);
        String encrypted = preferences.getString("device_token_encrypted", "");
        DeviceAuthorizationTestFixtures.Crypto.decryptFailures = 1;

        assertEquals("", store.token());
        assertEquals(encrypted, preferences.getString("device_token_encrypted", ""));
        assertEquals(TOKEN, new DeviceAuthorizationStore(context).token());
        assertEquals(2, DeviceAuthorizationTestFixtures.Crypto.decryptCalls);
    }

    @Test public void malformedPayloadIsRemoved() throws Exception {
        putUncachedToken(TOKEN);
        DeviceAuthorizationTestFixtures.Crypto.invalidPayload = true;
        assertEquals("", store.token());
        assertFalse(preferences.contains("device_token_encrypted"));
    }

    @Test public void failedAuthenticationTagIsRemoved() throws Exception {
        putUncachedToken(TOKEN);
        DeviceAuthorizationTestFixtures.Crypto.invalidTag = true;
        assertEquals("", store.token());
        assertFalse(preferences.contains("device_token_encrypted"));
    }

    @Test public void invalidDecryptedTokenIsRemoved() throws Exception {
        putUncachedToken("not-a-server-token");
        assertEquals("", store.token());
        assertFalse(preferences.contains("device_token_encrypted"));
    }

    @Test public void failedEncryptionDoesNotReplaceWorkingAuthorization() throws Exception {
        store.save(TOKEN);
        String encrypted = preferences.getString("device_token_encrypted", "");
        DeviceAuthorizationTestFixtures.Crypto.encryptFails = true;
        try {
            store.save(NEXT_TOKEN);
            fail("Encryption failure must be reported");
        } catch (java.security.GeneralSecurityException expected) {
            assertEquals(TOKEN, store.token());
            assertEquals(encrypted, preferences.getString("device_token_encrypted", ""));
        }
    }

    @Test public void oldUnauthorizedRequestCannotClearNewAuthorization() throws Exception {
        store.save(TOKEN);
        DeviceAuthorizationStore anotherStore = new DeviceAuthorizationStore(context);
        anotherStore.save(NEXT_TOKEN);

        assertFalse(store.clear(TOKEN));
        assertEquals(NEXT_TOKEN, store.token());
        assertTrue(store.clear(NEXT_TOKEN));
        assertEquals("", anotherStore.token());
    }

    @Test public void externalPreferenceChangeInvalidatesProcessCache() throws Exception {
        store.save(TOKEN);
        putUncachedToken(NEXT_TOKEN);
        assertEquals(NEXT_TOKEN, store.token());
        preferences.edit().clear().commit();
        assertEquals("", store.token());
    }

    @Test public void legacySdkBranchKeepsOnlyTheProcessToken() throws Exception {
        int originalSdk = Build.VERSION.SDK_INT;
        try {
            ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT", 22);
            store.save(TOKEN);
            assertEquals(TOKEN, new DeviceAuthorizationStore(context).token());
            assertFalse(preferences.contains("device_token_encrypted"));
            store.clear();
            assertEquals("", store.token());
        } finally {
            ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT", originalSdk);
        }
    }

    private void putUncachedToken(String token) throws Exception {
        preferences.edit().putString("device_token_encrypted", "HBLITE1:"
                + DeviceAuthorizationTestFixtures.Crypto.encrypt(token)).commit();
    }
}
