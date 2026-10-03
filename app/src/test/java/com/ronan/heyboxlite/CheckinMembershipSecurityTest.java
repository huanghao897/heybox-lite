package com.ronan.heyboxlite;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

public class CheckinMembershipSecurityTest {
    @Test
    public void membershipUsesPinnedAccountTransportNotTheHeyboxCookieClient() throws Exception {
        String api = source("CheckinMembershipApi.java");
        assertTrue(api.contains("client::accountRequest"));
        assertTrue(api.contains("CheckinCenterClient.requirePrefix(deviceToken, \"ccdevice1_\""));
        String[] forbidden = {"SessionStore", "ApiClient", "Cookie", "cookie", "HeaderProvider",
                "URL(", "openConnection", "WebView", "Intent", "SharedPreferences",
                "Log.", "eventLogger", "System.out", "printStackTrace"};
        for (String text : forbidden) assertFalse(text, api.contains(text));

        String transport = source("CheckinCenterTransport.java");
        assertTrue(transport.contains("requireTrustedUri(CheckinCenterClient.API_BASE + path"));
        assertTrue(transport.contains("setRequestProperty(\"Authorization\", \"Bearer \" + token)"));
        assertTrue(transport.contains("connection.setInstanceFollowRedirects(false)"));
        assertTrue(transport.contains("status >= 300 && status < 400"));
        assertFalse(transport.contains("Cookie"));
        assertFalse(transport.contains("SessionStore"));
        assertFalse(transport.contains("HeaderProvider"));
    }

    @Test
    public void allMembershipCoordinatorOperationsUseAuthorizationAwareCallbacks() throws Exception {
        String coordinator = source("CheckinCenterCoordinator.java");
        String[] methods = {"getMembershipCatalog", "getPurchaseHistory", "redeemMembership",
                "createMembershipOrder"};
        for (String method : methods) {
            String body = methodBody(coordinator, "void " + method + "(");
            assertTrue(method, body.contains("store.deviceToken()"));
            assertTrue(method, body.contains("token.isEmpty()"));
            assertTrue(method, body.contains("authorizationAware(callback)"));
        }
        assertTrue(coordinator.contains("handleAuthorizationError(error)"));
        assertFalse(coordinator.contains("SessionStore"));
        assertFalse(coordinator.contains("Log."));
    }

    @Test
    public void checkoutIsMemoryOnlyDataAndLegacyBillingEntryPointsDelegate() throws Exception {
        String billing = source("CheckinBilling.java");
        assertTrue(billing.contains("static boolean isTrustedCheckoutUrl"));
        String[] forbidden = {"openConnection", "toURL(", "WebView", "Intent", "SharedPreferences",
                "Log.", "eventLogger", "System.out", "printStackTrace"};
        for (String text : forbidden) assertFalse(text, billing.contains(text));

        String client = source("CheckinCenterClient.java");
        assertTrue(methodBody(client, "void createBillingOrder(")
                .contains("new CheckinMembershipApi(this).createBillingOrder"));
        assertTrue(methodBody(client, "void getBillingOrder(")
                .contains("new CheckinMembershipApi(this).getBillingOrder"));
        assertTrue(methodBody(client, "void submitBillingClaim(")
                .contains("new CheckinMembershipApi(this).submitBillingClaim"));
        assertTrue(client.split("\n", -1).length <= 918);
    }

    private static String source(String name) throws Exception {
        File path = new File("src/main/java/com/ronan/heyboxlite/" + name);
        if (!path.isFile()) path = new File("app", path.getPath());
        return new String(Files.readAllBytes(path.toPath()), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("Missing method: " + signature, start >= 0);
        int opening = source.indexOf('{', start);
        int depth = 1;
        int end = opening + 1;
        while (end < source.length() && depth > 0) {
            char character = source.charAt(end++);
            if (character == '{') depth++;
            if (character == '}') depth--;
        }
        assertTrue("Unclosed method: " + signature, depth == 0);
        return source.substring(opening, end);
    }
}
