package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Map;
import org.junit.Test;

public class OfficialRequestParamsTest {
    @Test
    public void feedUsesOfficialCursorContract() {
        Map<String, String> params = OfficialRequestParams.feed(
                0, 1, "cursor-2", false, "11,12", "icon");

        assertEquals("0", params.get("pull"));
        assertEquals("1", params.get("last_pull"));
        assertEquals("cursor-2", params.get("lastval"));
        assertEquals("11,12", params.get("unexposed"));
        assertEquals("0", params.get("is_first"));
        assertEquals("icon", params.get("refresh_type"));
        assertEquals("2", params.get("list_ver"));
        assertFalse(params.containsKey("offset"));
    }

    @Test
    public void feedOmitsNullableOfficialFields() {
        Map<String, String> params = OfficialRequestParams.feed(1, null, "", true);

        assertFalse(params.containsKey("last_pull"));
        assertFalse(params.containsKey("lastval"));
        assertFalse(params.containsKey("unexposed"));
        assertFalse(params.containsKey("refresh_type"));
    }

    @Test
    public void feedAllowsOfficialLoadMoreWithoutCursor() {
        Map<String, String> params = OfficialRequestParams.feed(0, 1, "", false);

        assertEquals("0", params.get("pull"));
        assertEquals("1", params.get("last_pull"));
        assertEquals("0", params.get("is_first"));
        assertEquals("2", params.get("list_ver"));
        assertFalse(params.containsKey("lastval"));
        assertFalse(params.containsKey("offset"));
    }

    @Test
    public void detailOmitsLegacyIndexParameter() {
        Map<String, String> params = OfficialRequestParams.detail("42", "feed");

        assertEquals("42", params.get("link_id"));
        assertEquals("feed", params.get("h_src"));
        assertEquals("1", params.get("page"));
        assertEquals("20", params.get("limit"));
        assertEquals("1", params.get("is_first"));
        assertEquals("0", params.get("owner_only"));
        assertFalse(params.containsKey("index"));
    }

    @Test
    public void subCommentsUseOnlyCursorParameters() {
        Map<String, String> params = OfficialRequestParams.subComments("100", "101", "detail");

        assertEquals("100", params.get("root_comment_id"));
        assertEquals("101", params.get("lastval"));
        assertFalse(params.containsKey("offset"));
        assertFalse(params.containsKey("page"));
        assertFalse(params.containsKey("limit"));
        assertFalse(params.containsKey("comment_id"));
    }

    @Test
    public void topLevelCommentOmitsReplyIdentifiersAndRecommendationState() {
        Map<String, String> body = OfficialRequestParams.createComment("42", "text", null, null);

        assertFalse(body.containsKey("root_id"));
        assertFalse(body.containsKey("reply_id"));
        assertFalse(body.containsKey("recommend_state"));
    }

    @Test
    public void favoriteAndFollowOmitCompatibilityAliases() {
        Map<String, String> favorite = OfficialRequestParams.favorite("42", true);
        Map<String, String> follow = OfficialRequestParams.follow("7");

        assertFalse(favorite.containsKey("folder_id"));
        assertFalse(follow.containsKey("follows"));
    }

    @Test
    public void writeStatesMatchOfficialUiActions() {
        assertEquals("1", OfficialRequestParams.linkLike("42", true).get("award_type"));
        assertEquals("0", OfficialRequestParams.linkLike("42", false).get("award_type"));
        assertEquals("1", OfficialRequestParams.favorite("42", true).get("favour_type"));
        assertEquals("2", OfficialRequestParams.favorite("42", false).get("favour_type"));
        assertEquals("1", OfficialRequestParams.commentLike("8", true).get("support_type"));
        assertEquals("2", OfficialRequestParams.commentLike("8", false).get("support_type"));
    }

    @Test
    public void endpointPathsMatchOfficialRetrofitDeclarations() {
        assertEquals("/bbs/app/feeds", EndpointProvider.feeds());
        assertEquals("/bbs/app/link/tree/v2", EndpointProvider.linkTreeV2());
        assertEquals("/bbs/app/comment/sub/comments", EndpointProvider.subComments());
        assertEquals("/bbs/app/api/general/search/v1", EndpointProvider.search());
        assertEquals("/bbs/app/profile/user/link/list", EndpointProvider.profileUserLinks());
        assertEquals("/bbs/app/profile/history/visit", EndpointProvider.history());
        assertEquals("/bbs/app/profile/fav/tab_list", EndpointProvider.favoriteTabs());
        assertEquals("/bbs/app/profile/fav/folder/v2/links", EndpointProvider.favoriteLinks());
        assertEquals("/bbs/app/api/emojis/list", EndpointProvider.emojis());
        assertEquals("/bbs/app/profile/award/link", EndpointProvider.awardLink());
        assertEquals("/bbs/app/link/favour", EndpointProvider.favourLink());
        assertEquals("/bbs/app/profile/follow/user", EndpointProvider.followUser());
        assertEquals("/bbs/app/profile/follow/user/cancel", EndpointProvider.unfollowUser());
        assertEquals("/bbs/app/comment/support", EndpointProvider.supportComment());
        assertEquals("/bbs/app/comment/create", EndpointProvider.createComment());
        assertEquals("/rc/box_data/callback", EndpointProvider.boxDataCallback());
        assertEquals("/task/sign_v3/sign", EndpointProvider.taskSignV3());
    }

    @Test
    public void historyUsesOfficialLinkTypeWithoutUserOrDesktopFields() {
        Map<String, String> params = OfficialRequestParams.history(0, 30);

        assertEquals("link", params.get("type"));
        assertFalse(params.containsKey(SecureStrings.userid()));
        assertFalse(params.containsKey("x_os_type"));
        assertFalse(params.containsKey("device_info"));
    }

    @Test
    public void favoritesUseDefaultFolderWithoutCompatibilityAliases() {
        Map<String, String> params = OfficialRequestParams.favorites(null, 0, 30);

        assertEquals("0", params.get("offset"));
        assertEquals("30", params.get("limit"));
        assertEquals("1", params.get("enable_new_style_collect"));
        assertFalse(params.containsKey("folder_id"));
        assertFalse(params.containsKey("folderid"));
        assertFalse(params.containsKey("fav_folder_id"));
    }

    @Test
    public void profileLinksIncludeUserHeaderOnFirstPage() {
        Map<String, String> firstPage = OfficialRequestParams.profileLinks("7", 0, 20);
        Map<String, String> nextPage = OfficialRequestParams.profileLinks("7", 20, 20);

        assertEquals("7", firstPage.get(SecureStrings.userid()));
        assertEquals("0", firstPage.get("no_banner"));
        assertEquals("1", nextPage.get("no_banner"));
    }
}
