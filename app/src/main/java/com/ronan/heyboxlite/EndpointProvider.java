package com.ronan.heyboxlite;

final class EndpointProvider {
    private EndpointProvider() {}

    static String feeds() { return "/bbs/app/feeds"; }
    static String linkTreeV2() { return "/bbs/app/link/tree/v2"; }
    static String qrUrl() { return "/account/get_qrcode_url/"; }
    static String qrState() { return "/account/qr_state/"; }
    static String subComments() { return "/bbs/app/comment/sub/comments"; }
    static String profileUserLinks() { return "/bbs/app/profile/user/link/list"; }
    static String history() { return "/bbs/app/profile/history/visit"; }
    static String favoriteTabs() { return "/bbs/app/profile/fav/tab_list"; }
    static String favoriteLinks() { return "/bbs/app/profile/fav/folder/v2/links"; }
    static String emojis() { return "/bbs/app/api/emojis/list"; }
    static String search() { return "/bbs/app/api/general/search/v1"; }
    static String awardLink() { return "/bbs/app/profile/award/link"; }
    static String favourLink() { return "/bbs/app/link/favour"; }
    static String followUser() { return "/bbs/app/profile/follow/user"; }
    static String unfollowUser() { return "/bbs/app/profile/follow/user/cancel"; }
    static String supportComment() { return "/bbs/app/comment/support"; }
    static String createComment() { return "/bbs/app/comment/create"; }
    static String boxDataCallback() { return "/rc/box_data/callback"; }

    static String baseUrl() { return BuildConfig.HEYBOX_API_BASE_URL; }
}
