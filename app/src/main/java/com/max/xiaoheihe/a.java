package com.max.xiaoheihe;

public final class a {
    public static final boolean f78322a = false;
    public static final String f78323b = "com.max.xiaoheihe";
    public static final String f78324c = "release";
    public static final String f78325d = "xiaoheiheHeybox";
    public static final String f78326e = "xiaoheihe";
    public static final String f78327f = "heybox";
    public static String f78328g = "1112";
    public static String f78329h = "1.3.391";
    public static final String f78330i = "";
    public static final String f78331j = "null";

    private a() {}

    public static void a(String version, String build) {
        if (version != null && !version.trim().isEmpty()) f78329h = version.trim();
        if (build != null && !build.trim().isEmpty()) f78328g = build.trim();
    }
}
