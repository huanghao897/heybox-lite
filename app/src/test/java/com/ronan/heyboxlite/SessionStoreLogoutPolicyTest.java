package com.ronan.heyboxlite;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SessionStoreLogoutPolicyTest {
    @Test
    public void logoutRemovesAccountStateAndKeepsUserSettings() {
        assertTrue(SessionStore.removesOnLogout(SecureStrings.cookieKey()));
        assertTrue(SessionStore.removesOnLogout(SecureStrings.encryptedCookieKey()));
        assertTrue(SessionStore.removesOnLogout(SecureStrings.userId()));
        assertTrue(SessionStore.removesOnLogout("user_name"));
        assertTrue(SessionStore.removesOnLogout("avatar"));
        assertTrue(SessionStore.removesOnLogout("comment_draft_123"));
        assertTrue(SessionStore.removesOnLogout("presence_identity_uploaded_123"));

        assertFalse(SessionStore.removesOnLogout(SecureStrings.deviceId()));
        assertFalse(SessionStore.removesOnLogout("round_screen"));
        assertFalse(SessionStore.removesOnLogout("motion_level"));
        assertFalse(SessionStore.removesOnLogout("remember_detail_scroll"));
        assertFalse(SessionStore.removesOnLogout("search_history"));
    }

    @Test
    public void logoutPurgesLegacyLocalSignInSecrets() {
        assertTrue(SessionStore.removesOnLogout("signin_mobile_pkey"));
        assertTrue(SessionStore.removesOnLogout("signin_replay_cookie"));
        assertTrue(SessionStore.removesOnLogout("last_sign_success_date"));
    }
}
