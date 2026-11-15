package com.ronan.heyboxlite;

import android.app.Application;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.net.URLEncoder;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class OfficialContentLinkTest {
    @Test public void postWebRoutesKeepThePostIdAndSource() {
        OfficialContentLink link = OfficialContentLink.parse(
                "https://h5.xiaoheihe.cn/app/bbs/link/Ab123?heybox_id=42&h_src=comment%2Bshare");
        assertNotNull(link);
        assertEquals(OfficialContentLink.Kind.POST, link.kind);
        assertEquals("Ab123", link.id);
        assertEquals("comment+share", link.hsrc);
        assertEquals("987", OfficialContentLink.parse(
                "//www.xiaoheihe.cn/app/bbs/link/987/").id);
        assertEquals("Ab123", OfficialContentLink.parse(
                "https://www.xiaoheihe.cn/bbs/link/Ab123").id);
    }

    @Test public void officialApiShareUsesLinkIdRatherThanTheSharersAccount() {
        OfficialContentLink link = OfficialContentLink.parse(
                "https://api.xiaoheihe.cn/bbs/app/api/web/share?heybox_id=42&link_id=987&h_src=comment");
        assertEquals(OfficialContentLink.Kind.POST, link.kind);
        assertEquals("987", link.id);
        assertEquals("comment", link.hsrc);
    }

    @Test public void legacyAndCurrentCommunityPostRoutesKeepTheActualPostId() {
        for (String path : new String[] {"/community/1067/list/Ab123",
                "/community/post/article/Ab123", "/community/post/video/Ab123",
                "/community/post/topimg/Ab123/"}) {
            OfficialContentLink link = OfficialContentLink.parse("https://www.xiaoheihe.cn" + path
                    + "?h_src=comment&heybox_id=42");
            assertEquals(OfficialContentLink.Kind.POST, link.kind);
            assertEquals("Ab123", link.id);
            assertEquals("comment", link.hsrc);
        }
    }

    @Test public void officialOpenUserWrapperMatchesTheNativeMentionGenerator() throws Exception {
        String route = "{\"protocol_type\":\"openUser\",\"user_id\":\"42\"}";
        OfficialContentLink link = OfficialContentLink.parse(
                "https://api.xiaoheihe.cn/open_inapp/#heybox://" + URLEncoder.encode(route, "UTF-8"));
        assertEquals(OfficialContentLink.Kind.USER, link.kind);
        assertEquals("42", link.id);
        assertEquals("42", OfficialContentLink.parse(
                "https://web.xiaoheihe.cn/open_inapp#heybox://" + route).id);
    }

    @Test public void openLinkReadsOnlyTheNestedOfficialLinkObject() throws Exception {
        String json = new JSONObject().put("protocol_type", "openLink").put("link",
                new JSONObject().put("linkid", "Ab123").put("h_src", "comment+reply")).toString();
        OfficialContentLink link = OfficialContentLink.parse("heybox://" + URLEncoder.encode(json, "UTF-8"));
        assertEquals(OfficialContentLink.Kind.POST, link.kind);
        assertEquals("Ab123", link.id);
        assertEquals("comment+reply", link.hsrc);
        assertEquals("Ab123", OfficialContentLink.parse("xhh://" + json).id);
    }

    @Test public void doubleEncodedOfficialProtocolsAreSupported() throws Exception {
        String json = "{\"protocol_type\":\"openUser\",\"user_id\":42}";
        String encoded = URLEncoder.encode(URLEncoder.encode(json, "UTF-8"), "UTF-8");
        assertEquals("42", OfficialContentLink.parse("heybox://" + encoded).id);
    }

    @Test public void userWebRouteIsAProfileNotAPost() {
        for (String prefix : new String[] {"/app", ""}) {
            OfficialContentLink link = OfficialContentLink.parse(
                    "https://www.xiaoheihe.cn" + prefix + "/user/profile/42");
            assertEquals(OfficialContentLink.Kind.USER, link.kind);
            assertEquals("42", link.id);
        }
    }

    @Test public void unrelatedOrSpoofedWebsitesNeverBecomeInternalRoutes() {
        for (String value : new String[] {
                "https://example.invalid/app/bbs/link/42",
                "https://www.xiaoheihe.cn.example.invalid/app/bbs/link/42",
                "https://www.xiaoheihe.cn@evil.invalid/app/bbs/link/42",
                "https://attacker@www.xiaoheihe.cn/app/bbs/link/42",
                "https://www.xiaoheihe.cn:8443/app/bbs/link/42",
                "https://www.xiaoheihe.cn/lottery?link_id=42&user_id=43",
                "https://evil.invalid/open_inapp/#heybox://{\"protocol_type\":\"openUser\",\"user_id\":\"42\"}",
                "https://www.xiaoheihe.cn/app/bbs/link/42/other",
                "https://www.xiaoheihe.cn/app/bbs/link/%2E%2E",
                "https://www.xiaoheihe.cn/app/bbs/link/42%2F43"}) {
            assertNull(value, OfficialContentLink.parse(value));
        }
    }

    @Test public void invalidAndUnsupportedProtocolsAreNotExecuted() {
        for (String value : new String[] {
                "javascript:alert(1)", "intent://42", "file:///42", "content://42", "",
                "heybox://%not-encoded", "heybox://{}", "heybox://[]",
                "heybox://{\"protocol_type\":\"openUser\",\"user_id\":\"../42\"}",
                "heybox://{\"protocol_type\":\"openUser\",\"user_id\":null}",
                "heybox://{\"protocol_type\":\"openLink\",\"linkid\":\"42\"}",
                "heybox://{\"protocol_type\":\"openLink\",\"link\":{}}",
                "heybox://{\"protocol_type\":\"openLink\",\"link\":{\"linkid\":null}}",
                "heybox://{\"protocol_type\":\"openLink\",\"link\":{\"linkid\":true}}",
                "heybox://{\"protocol_type\":\"openGameDetail\",\"app_id\":\"42\"}",
                "heybox://{\"protocol_type\":\"openUserInfo\",\"user_id\":\"42\"}"}) {
            assertNull(value, OfficialContentLink.parse(value));
        }
        assertNull(OfficialContentLink.parse(null));
        assertNull(OfficialContentLink.parse(new String(new char[16385])));
    }
}
