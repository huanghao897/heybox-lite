package com.ronan.heyboxlite;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves supported official content routes without executing arbitrary app protocols. */
final class OfficialContentLink {
    enum Kind { POST, USER }

    private static final Set<String> HOSTS = new HashSet<>(Arrays.asList(
            "api.xiaoheihe.cn", "web.xiaoheihe.cn", "h5.xiaoheihe.cn",
            "www.xiaoheihe.cn", "xiaoheihe.cn"));
    private static final Pattern POST_PATH = Pattern.compile(
            "/(?:app/)?bbs/link/([0-9A-Za-z]+)/?");
    private static final Pattern USER_PATH = Pattern.compile(
            "/(?:app/)?user/profile/([0-9]+)/?");
    private static final Pattern COMMUNITY_POST_PATH = Pattern.compile(
            "/community/(?:[^/]+/list|post/(?:video|article|topimg))/([0-9A-Za-z]+)/?");

    final Kind kind;
    final String id;
    final String hsrc;

    private OfficialContentLink(Kind kind, String id, String hsrc) {
        this.kind = kind;
        this.id = id;
        this.hsrc = hsrc;
    }

    static OfficialContentLink parse(String source) {
        if (source == null || source.length() > 16384) return null;
        String value = source.trim();
        if (value.startsWith("heybox://") || value.startsWith("xhh://")) {
            return protocol(value);
        }
        if (value.startsWith("//")) value = "https:" + value;
        // Raw JSON is legal in official open_inapp fragments, but not in java.net.URI.
        int hash = value.indexOf('#');
        String base = hash < 0 ? value : value.substring(0, hash);
        URI uri;
        try {
            uri = new URI(base);
        } catch (URISyntaxException malformed) {
            return null;
        }
        if (!webOrigin(uri)) return null;
        String path = uri.getRawPath();
        if (("/open_inapp".equals(path) || "/open_inapp/".equals(path)) && hash >= 0) {
            return protocol(value.substring(hash + 1));
        }
        Matcher post = POST_PATH.matcher(path == null ? "" : path);
        if (post.matches()) return post(post.group(1), query(uri, "h_src"));
        Matcher communityPost = COMMUNITY_POST_PATH.matcher(path == null ? "" : path);
        if (communityPost.matches()) return post(communityPost.group(1), query(uri, "h_src"));
        Matcher user = USER_PATH.matcher(path == null ? "" : path);
        if (user.matches()) return user(user.group(1));
        if ("/bbs/app/api/web/share".equals(path) || "/bbs/app/api/web/share/".equals(path)) {
            return post(query(uri, "link_id"), query(uri, "h_src"));
        }
        return null;
    }

    private static boolean webOrigin(URI uri) {
        if (uri.getRawUserInfo() != null || uri.getHost() == null) return false;
        String scheme = uri.getScheme();
        int expectedPort;
        if ("https".equalsIgnoreCase(scheme)) expectedPort = 443;
        else if ("http".equalsIgnoreCase(scheme)) expectedPort = 80;
        else return false;
        return (uri.getPort() == -1 || uri.getPort() == expectedPort)
                && HOSTS.contains(uri.getHost().toLowerCase(java.util.Locale.ROOT));
    }

    private static OfficialContentLink protocol(String value) {
        String encoded;
        if (value.startsWith("heybox://")) encoded = value.substring(9);
        else if (value.startsWith("xhh://")) encoded = value.substring(6);
        else return null;
        for (int attempt = 0; attempt < 2 && !encoded.startsWith("{"); attempt++) {
            encoded = decode(encoded);
            if (encoded.isEmpty()) return null;
        }
        try {
            JSONObject route = new JSONObject(encoded);
            switch (route.optString("protocol_type")) {
                case "openUser":
                    return user(idField(route, "user_id"));
                case "openLink":
                    JSONObject link = route.optJSONObject("link");
                    return link == null ? null : post(idField(link, "linkid"), link.optString("h_src"));
                default:
                    return null;
            }
        } catch (JSONException malformed) {
            return null;
        }
    }

    private static OfficialContentLink post(String id, String hsrc) {
        return id.matches("[0-9A-Za-z]{1,128}")
                ? new OfficialContentLink(Kind.POST, id, hsrc) : null;
    }

    private static OfficialContentLink user(String id) {
        return id.matches("[0-9]{1,32}")
                ? new OfficialContentLink(Kind.USER, id, "") : null;
    }

    private static String idField(JSONObject object, String key) {
        Object value = object.opt(key);
        return value instanceof String || value instanceof Number ? value.toString() : "";
    }

    private static String query(URI uri, String key) {
        String query = uri.getRawQuery();
        if (query == null) return "";
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0 && key.equals(decode(pair.substring(0, equals)))) {
                return decode(pair.substring(equals + 1));
            }
        }
        return "";
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (IllegalArgumentException malformed) {
            return "";
        } catch (UnsupportedEncodingException unavailable) {
            throw new AssertionError(unavailable);
        }
    }
}
