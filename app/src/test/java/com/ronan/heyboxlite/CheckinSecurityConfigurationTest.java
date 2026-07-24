package com.ronan.heyboxlite;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;

public class CheckinSecurityConfigurationTest {
    @Test
    public void networkConfigDisablesCleartextAndUsesSystemTrustForProductionDomain()
            throws Exception {
        String xml = readResource("xml/network_security_config.xml");

        assertFalse(xml.contains("cleartextTrafficPermitted=\"true\""));
        assertTrue(xml.contains("<domain includeSubdomains=\"false\">heyboxlite.xyz</domain>"));
        assertTrue(xml.contains("<certificates src=\"system\""));
        assertFalse(xml.contains("8.138.134.236"));
        assertFalse(xml.contains("103.236.54.97"));
        assertFalse(xml.contains("@raw/checkin_server_ca"));
        assertFalse(xml.contains("src=\"user\""));
    }

    @Test
    public void localSignInRemainsDisabledAndUnreferencedByActivity() throws Exception {
        String activity = readSource("MainActivity.java");
        String legacy = readSource("SignInManager.java");
        String client = readSource("CheckinCenterClient.java");

        assertTrue(activity.contains("SIGN_IN_ENABLED = false"));
        assertTrue(legacy.contains("ENABLED = false"));
        assertFalse(activity.contains("new SignInManager"));
        assertFalse(activity.contains(".autoSignInIfNeeded("));
        assertFalse(activity.contains(".signIn("));
        assertFalse(client.contains("/task/sign_v3/"));
    }

    @Test
    public void checkinClientContainsNoTlsBypassOrSecretLogging() throws Exception {
        String client = readSource("CheckinCenterClient.java");
        String coordinator = readSource("CheckinCenterCoordinator.java");
        String page = readSource("CheckinCenterPage.java");
        String store = readSource("CheckinCenterStore.java");
        String captcha = readSource("CheckinCaptchaActivity.java");

        assertFalse(client.contains("http://"));
        assertFalse(client.contains("TrustManager"));
        assertFalse(client.contains("HostnameVerifier"));
        assertFalse(client.contains("setSSLSocketFactory"));
        assertFalse(client.contains("Log."));
        assertFalse(coordinator.contains("Log."));
        assertFalse(page.contains("android.webkit"));
        assertFalse(page.contains("WebView"));
        assertFalse(captcha.contains("handler.proceed()"));
        assertFalse(captcha.contains("Log."));
        assertTrue(captcha.contains("handler.cancel()"));
        assertTrue(captcha.contains("FLAG_SECURE"));
        assertTrue(captcha.contains("if (dataDirectoryConfigured) return;"));
        assertTrue(captcha.contains("showRetry(result == null"));
        assertTrue(store.contains("ModernCookieCrypto.encrypt(token)"));
        assertFalse(store.contains("putString(DEVICE_TOKEN, token)"));
    }

    private static String readResource(String relative) throws Exception {
        return new String(Files.readAllBytes(resolve("src/main/res/" + relative).toPath()),
                StandardCharsets.UTF_8);
    }

    private static String readSource(String name) throws Exception {
        return new String(Files.readAllBytes(
                resolve("src/main/java/com/ronan/heyboxlite/" + name).toPath()),
                StandardCharsets.UTF_8);
    }

    private static File resolve(String moduleRelative) {
        File direct = new File(moduleRelative);
        if (direct.isFile()) return direct;
        File fromRoot = new File("app", moduleRelative);
        if (fromRoot.isFile()) return fromRoot;
        throw new IllegalStateException("Missing test fixture: " + moduleRelative);
    }
}
