package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Locale;
import org.junit.Test;

public class CheckinSecurityConfigurationTest {
    private static final String CA_SHA256 =
            "06ca37aee10c5280d2eef95d35cd640425ce8772451b67afc90bd5327722c010";

    @Test
    public void networkConfigDisablesCleartextAndTrustsOnlyPrivateCaForServer()
            throws Exception {
        String xml = readResource("xml/network_security_config.xml");

        assertFalse(xml.contains("cleartextTrafficPermitted=\"true\""));
        assertTrue(xml.contains("<domain includeSubdomains=\"false\">8.138.134.236</domain>"));
        assertTrue(xml.contains("<certificates src=\"@raw/checkin_server_ca\""));
        assertFalse(xml.contains("103.236.54.97"));
        assertFalse(xml.contains("src=\"user\""));
    }

    @Test
    public void bundledPrivateCaMatchesDocumentedFingerprint() throws Exception {
        byte[] pem = readResource("raw/checkin_server_ca.crt").getBytes(StandardCharsets.US_ASCII);
        Certificate certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(pem));
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        StringBuilder value = new StringBuilder(digest.length * 2);
        for (byte item : digest) value.append(String.format(Locale.US, "%02x", item & 0xff));

        assertEquals(CA_SHA256, value.toString());
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
        String store = readSource("CheckinCenterStore.java");

        assertFalse(client.contains("http://"));
        assertFalse(client.contains("TrustManager"));
        assertFalse(client.contains("HostnameVerifier"));
        assertFalse(client.contains("setSSLSocketFactory"));
        assertFalse(client.contains("Log."));
        assertFalse(coordinator.contains("Log."));
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
