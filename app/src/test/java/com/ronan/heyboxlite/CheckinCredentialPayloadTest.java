package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class CheckinCredentialPayloadTest {
    @Test
    public void payloadMatchesDocumentedContractWithoutSignedFields() throws Exception {
        CheckinCredentialPayload payload = sample("pkey=value; x_xhh_tokenid=token");
        JSONObject root = payload.json;
        JSONObject credentials = root.getJSONObject("credentials");
        JSONObject device = root.getJSONObject("device");
        JSONObject app = root.getJSONObject("app");

        assertEquals(1, root.getInt("schema"));
        assertEquals(4, credentials.length());
        assertEquals("123456", credentials.getString("heybox_id"));
        assertEquals("pkey-value", credentials.getString("pkey"));
        assertEquals("token-value", credentials.getString("x_xhh_tokenid"));
        assertEquals(6, device.length());
        assertEquals("device-123", device.getString("device_id"));
        assertEquals("360", device.getString("screen_width_dp"));
        assertEquals("Asia/Shanghai", device.getString("time_zone"));
        assertEquals("2.0.6", app.getString("version"));
        assertEquals(209, app.getInt("version_code"));
        assertFalse(root.has("hkey"));
        assertFalse(root.has("nonce"));
        assertFalse(root.has("_rnd"));
        assertFalse(root.has("_time"));
    }

    @Test
    public void fingerprintIsLocalDeterministicAndChangesWithCookie() throws Exception {
        CheckinCredentialPayload first = sample("cookie-a");
        CheckinCredentialPayload same = sample("cookie-a");
        CheckinCredentialPayload changed = sample("cookie-b");

        assertEquals(first.fingerprint, same.fingerprint);
        assertNotEquals(first.fingerprint, changed.fingerprint);
        assertTrue(first.fingerprint.matches("[0-9a-f]{64}"));
        assertFalse(first.json.toString().contains(first.fingerprint));
    }

    @Test(expected = CheckinCredentialPayload.InvalidCredentials.class)
    public void missingPkeyIsRejectedBeforeNetwork() throws Exception {
        CheckinCredentialPayload.create("123456", "", "", "cookie",
                "device-123", "Pixel 8", "14", "heybox", "360",
                "Asia/Shanghai", "2.0.6", 209);
    }

    private static CheckinCredentialPayload sample(String cookie) throws Exception {
        return CheckinCredentialPayload.create("123456", "pkey-value", "token-value",
                cookie, "device-123", "Pixel 8", "14", "heybox", "360",
                "Asia/Shanghai", "2.0.6", 209);
    }
}
