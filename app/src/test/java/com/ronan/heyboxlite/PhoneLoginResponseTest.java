package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

public class PhoneLoginResponseTest {
    @Test
    public void parsesOfficialUserResult() throws Exception {
        JSONObject account = new JSONObject()
                .put("userid", "65779689")
                .put("username", "tester")
                .put("avartar", "https://example.com/avatar.png");
        JSONObject body = new JSONObject().put("result", new JSONObject()
                .put("pkey", "mobile-pkey")
                .put("account_detail", account));

        PhoneLoginResponse response = PhoneLoginResponse.parse(body);

        assertEquals("65779689", response.userId);
        assertEquals("mobile-pkey", response.pkey);
        assertEquals("tester", response.userName);
        assertEquals("https://example.com/avatar.png", response.avatar);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsResultWithoutPkey() throws Exception {
        PhoneLoginResponse.parse(new JSONObject().put("result", new JSONObject()
                .put("account_detail", new JSONObject().put("userid", "65779689"))));
    }

    @Test
    public void readsServerCooldownWithinBounds() throws Exception {
        JSONObject body = new JSONObject().put("result",
                new JSONObject().put("remain_time", "75"));

        assertEquals(75, PhoneLoginResponse.retryAfterSeconds(body));
        assertEquals(60, PhoneLoginResponse.retryAfterSeconds(new JSONObject()));
    }

    @Test
    public void loginEndpointsMatchOfficialPaths() {
        assertEquals("/account/get_login_code/", EndpointProvider.mobileLoginCode());
        assertEquals("/account/login_code/", EndpointProvider.mobileLogin());
    }
}
