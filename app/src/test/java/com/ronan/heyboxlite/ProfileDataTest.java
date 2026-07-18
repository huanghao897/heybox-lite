package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class ProfileDataTest {
    @Test
    public void userLinksEndpointMatchesOfficialDynamicList() {
        assertEquals("/bbs/app/profile/user/link/list", EndpointProvider.profileUserLinks());
    }

    @Test
    public void profileResponseReadsOfficialReceivedLikeField() throws Exception {
        JSONObject account = new JSONObject()
                .put("bbs_info", new JSONObject()
                        .put("follow_num", 19)
                        .put("fan_num", 3)
                        .put("be_favoured_num", 34));
        JSONObject body = new JSONObject().put("result",
                new JSONObject().put("account_detail", account));

        assertSame(account, ProfileData.user(body));
        assertEquals(19, ProfileData.followCount(account));
        assertEquals(3, ProfileData.fanCount(account));
        assertEquals(34, ProfileData.likeCount(account));
    }

    @Test
    public void dynamicResponseUsesRootUserAndPostLinks() throws Exception {
        JSONObject user = new JSONObject().put("awarded_num", 42);
        JSONArray posts = new JSONArray().put(new JSONObject().put("linkid", "100"));
        JSONObject body = new JSONObject().put("user", user).put("post_links", posts);

        assertSame(user, ProfileData.user(body));
        assertSame(posts, ProfileData.posts(body));
        assertEquals(42, ProfileData.likeCount(user));
    }

    @Test
    public void achievementArrayIsLastResortForLikes() throws Exception {
        JSONObject user = new JSONObject().put("achieve", new JSONArray()
                .put(new JSONObject().put("key", "award").put("value", 27)));

        assertEquals(27, ProfileData.likeCount(user));
    }
}
