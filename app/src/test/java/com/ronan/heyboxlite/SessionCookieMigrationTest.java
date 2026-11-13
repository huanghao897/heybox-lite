package com.ronan.heyboxlite;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, instrumentedPackages = "com.ronan.heyboxlite",
        shadows = SessionCookieMigrationTest.Crypto.class)
public class SessionCookieMigrationTest {
    private SharedPreferences prefs;
    private SessionStore session;
    private String cookie;

    @Before public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        prefs = context.getSharedPreferences(SecureStrings.preferencesName(), Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        Crypto.failEncrypt = false;
        cookie = SecureStrings.userHeyboxId() + "=42; " + SecureStrings.userPkey() + "=fixture-key";
        prefs.edit().putString(SecureStrings.cookieKey(), cookie).commit();
        session = new SessionStore(context);
    }

    @Test public void failedMigrationRetainsTheOnlyCredentialAndCanRetryInTheSameStore() {
        Crypto.failEncrypt = true;

        assertEquals("", session.getCookie());
        assertEquals(cookie, prefs.getString(SecureStrings.cookieKey(), ""));
        assertFalse(prefs.contains(SecureStrings.encryptedCookieKey()));

        Crypto.failEncrypt = false;
        assertEquals("42", SessionCookieCodec.userId(session.getCookie()));
        assertFalse(prefs.contains(SecureStrings.cookieKey()));
        assertTrue(prefs.contains(SecureStrings.encryptedCookieKey()));
    }

    @Test public void partialResponseCannotReplaceLegacyCredentialsWhileMigrationIsUnavailable() {
        Crypto.failEncrypt = true;
        long generation = session.sessionGeneration();

        try {
            session.mergeCookies(Collections.singletonList("extra=new"), generation);
            fail("A response must not claim that unavailable credentials were stored");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("安全读取"));
        }
        assertEquals(cookie, prefs.getString(SecureStrings.cookieKey(), ""));
        assertFalse(prefs.contains(SecureStrings.encryptedCookieKey()));

        Crypto.failEncrypt = false;
        assertEquals("42", SessionCookieCodec.userId(session.getCookie()));
        assertFalse(session.getCookie().contains("extra=new"));
    }

    @Test public void failedReplacementPreservesAnExistingEncryptedCredential() {
        String original = session.getCookie();
        String encrypted = prefs.getString(SecureStrings.encryptedCookieKey(), "");
        Crypto.failEncrypt = true;

        session.saveCookie("replacement=fixture");

        assertEquals(encrypted, prefs.getString(SecureStrings.encryptedCookieKey(), ""));
        assertEquals(original, session.getCookie());
    }

    @Test public void permanentlyDamagedCookieCanBeReplacedByFreshLogin() throws Exception {
        prefs.edit().remove(SecureStrings.cookieKey())
                .putString(SecureStrings.encryptedCookieKey(), "damaged-tag").commit();
        long generation = session.sessionGeneration();

        long current = session.mergeCookies(java.util.Arrays.asList(
                SecureStrings.userHeyboxId() + "=84",
                SecureStrings.userPkey() + "=new-fixture"), generation);

        assertTrue(current > generation);
        assertTrue(session.completeLogin(new org.json.JSONObject().put("heyboxid", "84")));
        assertEquals("84", session.userId());
        assertTrue(session.getCookie().contains("new-fixture"));
        assertFalse(prefs.getString(SecureStrings.encryptedCookieKey(), "").equals("damaged-tag"));
        assertTrue(new SessionStore(RuntimeEnvironment.getApplication()).isLoggedIn());
    }

    @Test public void temporaryFailureRetainsCiphertextAndDoesNotCompleteLogin() throws Exception {
        prefs.edit().remove(SecureStrings.cookieKey())
                .putString(SecureStrings.encryptedCookieKey(), "unavailable-key").commit();
        long generation = session.sessionGeneration();
        assertFalse(session.completeLogin(new org.json.JSONObject().put("heyboxid", "84")));
        assertEquals(generation, session.sessionGeneration());
        assertEquals("unavailable-key", prefs.getString(SecureStrings.encryptedCookieKey(), ""));
        assertEquals("", prefs.getString(SecureStrings.userId(), ""));
    }

    @Test public void newLoginEncryptionFailureIsReportedWithoutCompletingLogin() throws Exception {
        prefs.edit().clear().commit();
        Crypto.failEncrypt = true;
        long generation = session.sessionGeneration();
        try {
            session.mergeCookies(java.util.Arrays.asList(SecureStrings.userHeyboxId() + "=84",
                    SecureStrings.userPkey() + "=new-fixture"), generation);
            fail("Encryption failure must reach the request error boundary");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("安全保存"));
        }
        assertFalse(session.completeLogin(new org.json.JSONObject().put("heyboxid", "84")));
        assertEquals(generation, session.sessionGeneration());
    }

    @Implements(value = ModernCookieCrypto.class, isInAndroidSdk = false)
    public static final class Crypto {
        static boolean failEncrypt;

        @Implementation protected static String encrypt(String value) throws Exception {
            if (failEncrypt) throw new ModernCookieCrypto.KeyUnavailableException("fixture unavailable");
            return "fixture:" + value;
        }

        @Implementation protected static String decrypt(String value) throws Exception {
            if ("damaged-tag".equals(value)) throw new javax.crypto.AEADBadTagException("fixture");
            if ("unavailable-key".equals(value)) {
                throw new ModernCookieCrypto.KeyUnavailableException("fixture");
            }
            return value.substring("fixture:".length());
        }
    }
}
