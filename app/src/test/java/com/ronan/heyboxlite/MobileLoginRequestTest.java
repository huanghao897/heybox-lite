package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MobileLoginRequestTest {
    @Test
    public void nativeSignerKeepsOfficialLoginPathTrailingSlash() {
        assertEquals("/account/get_login_code/",
                OfficialNativeSigner.normalizeInterceptorPath("/account/get_login_code/"));
        assertEquals("/account/login_code/",
                OfficialNativeSigner.normalizeInterceptorPath("account/login_code/"));
    }

    @Test
    public void loginSigningIncludesQueryButExcludesEncryptedPhoneBody() {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("code", "123456");
        query.put("is_new_device", "1");
        Map<String, String> body = new LinkedHashMap<>();
        body.put("phone_num", "encrypted-value");

        Map<String, String> signed = ApiClient.nativeSignParams(
                "POST", ApiClient.RequestProfile.OFFICIAL_MOBILE_LOGIN, query, body);

        assertEquals("123456", signed.get("code"));
        assertEquals("1", signed.get("is_new_device"));
        assertFalse(signed.containsKey("phone_num"));
    }

    @Test
    public void mobileLoginPathDetectionIgnoresOnlyCanonicalTrailingSlash() {
        assertTrue(ApiClient.isMobileLoginPath("/account/get_login_code/"));
        assertTrue(ApiClient.isMobileLoginPath("/account/login_code"));
        assertFalse(ApiClient.isMobileLoginPath("/task/sign_v3/sign"));
    }
}
