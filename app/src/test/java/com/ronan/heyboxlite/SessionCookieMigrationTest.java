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

        assertEquals(generation, session.mergeCookies(Collections.singletonList("extra=new"), generation));
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

    @Implements(value = ModernCookieCrypto.class, isInAndroidSdk = false)
    public static final class Crypto {
        static boolean failEncrypt;

        @Implementation protected static String encrypt(String value) throws Exception {
            if (failEncrypt) throw new ModernCookieCrypto.KeyUnavailableException("fixture unavailable");
            return "fixture:" + value;
        }

        @Implementation protected static String decrypt(String value) {
            return value.substring("fixture:".length());
        }
    }
}
