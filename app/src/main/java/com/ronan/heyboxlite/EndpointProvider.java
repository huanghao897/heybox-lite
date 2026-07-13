package com.ronan.heyboxlite;

final class EndpointProvider {
    private static final int KEY = 83;

    private EndpointProvider() {}

    static String feeds() { return path(124,49,49,32,124,50,35,35,124,53,54,54,55,32); }
    static String linkTree() { return path(124,49,49,32,124,50,35,35,124,63,58,61,56,124,39,33,54,54); }
    static String linkTreeV2() { return path(124,49,49,32,124,50,35,35,124,63,58,61,56,124,39,33,54,54,124,37,97); }
    static String qrUrl() { return path(124,50,48,48,60,38,61,39,124,52,54,39,12,34,33,48,60,55,54,12,38,33,63,124); }
    static String qrState() { return path(124,50,48,48,60,38,61,39,124,34,33,12,32,39,50,39,54,124); }
    static String subComments() { return path(124,49,49,32,124,50,35,35,124,48,60,62,62,54,61,39,124,32,38,49,124,48,60,62,62,54,61,39,32); }
    static String profile() { return path(124,49,49,32,124,50,35,35,124,35,33,60,53,58,63,54,124,38,32,54,33,124,35,33,60,53,58,63,54); }
    static String profileEvents() { return path(124,49,49,32,124,50,35,35,124,35,33,60,53,58,63,54,124,54,37,54,61,39,32); }
    static String history() { return path(124,49,49,32,124,50,35,35,124,35,33,60,53,58,63,54,124,59,58,32,39,60,33,42,124,37,58,32,58,39); }
    static String favoriteTabs() { return path(124,49,49,32,124,50,35,35,124,35,33,60,53,58,63,54,124,53,50,37,124,39,50,49,12,63,58,32,39); }
    static String favoriteFolders() { return path(124,49,49,32,124,50,35,35,124,35,33,60,53,58,63,54,124,53,50,37,124,53,60,63,55,54,33,32); }
    static String favoriteLinks() { return path(124,49,49,32,124,50,35,35,124,35,33,60,53,58,63,54,124,53,50,37,124,53,60,63,55,54,33,124,37,97,124,63,58,61,56,32); }
    static String favoriteList() { return path(124,49,49,32,124,50,35,35,124,35,33,60,53,58,63,54,124,53,50,37,60,38,33,124,63,58,32,39); }
    static String emojis() { return path(124,49,49,32,124,50,35,35,124,50,35,58,124,54,62,60,57,58,32,124,63,58,32,39); }
    static String search() { return path(124,49,49,32,124,50,35,35,124,50,35,58,124,52,54,61,54,33,50,63,124,32,54,50,33,48,59,124,37,98); }
    static String linkLikeCombo() { return path(124,49,49,32,124,50,35,35,124,63,58,61,56,124,63,58,56,54,124,48,60,62,49,60); }
    static String awardLink() { return path(124,49,49,32,124,50,35,35,124,35,33,60,53,58,63,54,124,50,36,50,33,55,124,63,58,61,56); }
    static String favourLink() { return path(124,49,49,32,124,50,35,35,124,63,58,61,56,124,53,50,37,60,38,33); }
    static String followUser() { return path(124,49,49,32,124,50,35,35,124,35,33,60,53,58,63,54,124,53,60,63,63,60,36,124,38,32,54,33); }
    static String unfollowUser() { return path(124,49,49,32,124,50,35,35,124,35,33,60,53,58,63,54,124,53,60,63,63,60,36,124,38,32,54,33,124,48,50,61,48,54,63); }
    static String supportComment() { return path(124,49,49,32,124,50,35,35,124,48,60,62,62,54,61,39,124,32,38,35,35,60,33,39); }
    static String createComment() { return path(124,49,49,32,124,50,35,35,124,48,60,62,62,54,61,39,124,48,33,54,50,39,54); }
    static String taskSignV3() { return path(124,39,50,32,56,124,32,58,52,61,12,37,96,124,32,58,52,61); }
    static String taskSignV3State() { return path(124,39,50,32,56,124,32,58,52,61,12,37,96,124,52,54,39,12,32,58,52,61,12,32,39,50,39,54); }
    static String taskSignV2() { return path(124,39,50,32,56,124,32,58,52,61,12,37,97,124,32,58,52,61); }
    static String taskSign() { return path(124,39,50,32,56,124,32,58,52,61,124); }
    static String taskListV2() { return path(124,39,50,32,56,124,63,58,32,39,12,37,97,124); }
    static String taskListV2NoSlash() { return path(124,39,50,32,56,124,63,58,32,39,12,37,97); }
    static String getuiFix() { return path(124,50,48,48,60,38,61,39,124,52,54,39,38,58,124,53,58,43); }
    static String boxDataCallback() { return path(124,33,48,124,49,60,43,12,55,50,39,50,124,48,50,63,63,49,50,48,56); }
    static String oldPkeyLogin() { return path(124,50,48,48,60,38,61,39,124,62,50,43,124,60,63,55,12,35,56,54,42,12,63,60,52,58,61,124); }

    static String baseUrl() {
        int[] values = {104,116,116,112,115,58,47,47,97,112,105,46,120,105,97,111,
                104,101,105,104,101,46,99,110};
        StringBuilder result = new StringBuilder(values.length);
        for (int value : values) result.append((char) value);
        return result.toString();
    }

    private static String path(int... values) {
        char[] result = new char[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (char) (values[i] ^ KEY);
        return new String(result);
    }
}
