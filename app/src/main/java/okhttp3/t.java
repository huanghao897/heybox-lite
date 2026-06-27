package okhttp3;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class t {
    public static final Companion INSTANCE = new Companion();
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
    public static final String f124261l = " \"':;<=>@[]^`{}|/\\?#";
    public static final String f124262m = " \"':;<=>@[]^`{}|/\\?#";
    public static final String f124263n = " \"<>^`{}|/\\?#";
    public static final String f124264o = "[]";
    public static final String f124265p = " \"'<>#";
    public static final String f124266q = " \"'<>#&=";
    public static final String f124267r = " !\"#$&'(),/:;<=>?@[]\\^`{|}~";
    public static final String f124268s = "\\^`{|}";
    public static final String f124269t = " \"':;<=>@[]^`{}|/\\?#&!$(),~";
    public static final String f124270u = "";

    private final boolean a;
    private final String b;
    private final String c;
    private final String d;
    private final String e;
    private final int f;
    private final List<String> g;
    private final List<String> h;
    private final String i;
    private final String j;

    private t(a builder) {
        this(emptyDefault(builder.a, "https"), builder.b, builder.c,
                emptyDefault(builder.d, "api.xiaoheihe.cn"), builder.e,
                new ArrayList<>(builder.f),
                builder.g == null ? null : new ArrayList<>(builder.g),
                builder.h, builder.toString());
    }

    public t(String scheme, String username, String password, String host, int port,
             List<String> pathSegments, List<String> queryNamesAndValues,
             String fragment, String url) {
        this.b = emptyDefault(scheme, "https");
        this.c = username == null ? "" : username;
        this.d = password == null ? "" : password;
        this.e = emptyDefault(host, "api.xiaoheihe.cn");
        this.f = port;
        this.g = Collections.unmodifiableList(pathSegments == null
                ? defaultPathSegments() : new ArrayList<>(pathSegments));
        this.h = queryNamesAndValues == null
                ? null : Collections.unmodifiableList(new ArrayList<>(queryNamesAndValues));
        this.i = fragment;
        this.j = url == null || url.isEmpty() ? buildUrl(this.b, this.e, this.f, this.g, this.h, this.i) : url;
        this.a = "https".equals(this.b);
    }

    public a H() {
        return new a(this);
    }

    public String x() {
        return encodedPath(g);
    }

    public String F() {
        return e;
    }

    public Map<String, String> queryMap() {
        return toMap(h);
    }

    @Override public String toString() {
        return j;
    }

    public String getScheme() { return b; }
    public String getHost() { return e; }
    public int getPort() { return f; }
    public boolean getIsHttps() { return a; }
    public String getUsername() { return c; }
    public String getPassword() { return d; }
    public String A() { return c; }
    public String a() { return v(); }
    public String b() { return w(); }
    public String c() { return x(); }
    public List<String> d() { return y(); }
    public String e() { return z(); }
    public String f() { return A(); }
    public String g() { return i; }
    public String h() { return e; }
    public String i() { return d; }
    public List<String> j() { return g; }
    public int k() { return M(); }
    public int l() { return f; }
    public String m() { return O(); }
    public Set<String> n() { return R(); }
    public int o() { return U(); }
    public String p() { return b; }
    public boolean G() { return a; }
    public List<String> L() { return g; }
    public int M() { return g.size(); }
    public String O() { return queryString(h); }
    public String P(String name) {
        if (name == null || h == null) return null;
        for (int index = 0; index + 1 < h.size(); index += 2) {
            if (name.equals(h.get(index))) return h.get(index + 1);
        }
        return null;
    }
    public String Q(int index) {
        if (h == null) throw new IndexOutOfBoundsException();
        int pairIndex = index * 2;
        if (pairIndex < 0 || pairIndex >= h.size()) throw new IndexOutOfBoundsException();
        String name = h.get(pairIndex);
        return name == null ? "" : name;
    }
    public Set<String> R() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (h != null) {
            for (int index = 0; index + 1 < h.size(); index += 2) {
                String name = h.get(index);
                if (name != null) names.add(name);
            }
        }
        return Collections.unmodifiableSet(names);
    }
    public String S(int index) {
        if (h == null) throw new IndexOutOfBoundsException();
        int pairIndex = index * 2 + 1;
        if (pairIndex < 0 || pairIndex >= h.size()) throw new IndexOutOfBoundsException();
        return h.get(pairIndex);
    }
    public List<String> T(String name) {
        ArrayList<String> values = new ArrayList<>();
        if (name != null && h != null) {
            for (int index = 0; index + 1 < h.size(); index += 2) {
                if (name.equals(h.get(index))) values.add(h.get(index + 1));
            }
        }
        return Collections.unmodifiableList(values);
    }
    public int U() { return h == null ? 0 : h.size() / 2; }
    public String v() { return i; }
    public String w() { return d; }
    public List<String> y() { return g; }
    public String z() { return O(); }
    public String getUrl() { return j; }
    public static int u(String scheme) { return INSTANCE.g(scheme); }

    private static Map<String, String> toMap(List<String> pairs) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        if (pairs == null) return out;
        for (int i = 0; i + 1 < pairs.size(); i += 2) {
            String key = percentDecode(pairs.get(i), true);
            if (key != null && !key.isEmpty()) {
                out.put(key, percentDecode(pairs.get(i + 1), true));
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static List<String> defaultPathSegments() {
        ArrayList<String> out = new ArrayList<>();
        out.add("");
        return out;
    }

    private static String encodedPath(List<String> segments) {
        if (segments == null || segments.isEmpty()) return "/";
        StringBuilder out = new StringBuilder();
        for (String segment : segments) {
            out.append('/');
            if (segment != null) out.append(segment);
        }
        return out.length() == 0 ? "/" : out.toString();
    }

    private static String emptyDefault(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static String queryString(List<String> pairs) {
        if (pairs == null || pairs.isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        boolean first = true;
        for (int i = 0; i + 1 < pairs.size(); i += 2) {
            String key = pairs.get(i);
            if (key == null || key.isEmpty()) continue;
            if (!first) out.append('&');
            first = false;
            out.append(key);
            String value = pairs.get(i + 1);
            if (value != null) out.append('=').append(value);
        }
        return out.toString();
    }

    private static String buildUrl(String scheme, String host, int port,
                                   List<String> pathSegments, List<String> query,
                                   String fragment) {
        StringBuilder out = new StringBuilder();
        out.append(emptyDefault(scheme, "https")).append("://").append(cleanHost(host));
        if (port > 0) out.append(':').append(port);
        out.append(encodedPath(pathSegments));
        String queryText = queryString(query);
        if (queryText != null && !queryText.isEmpty()) out.append('?').append(queryText);
        if (fragment != null && !fragment.isEmpty()) out.append('#').append(fragment);
        return out.toString();
    }

    private static String cleanScheme(String value) {
        String clean = value == null ? "" : value.trim().toLowerCase(Locale.US);
        if ("http".equals(clean) || "https".equals(clean)) return clean;
        return "https";
    }

    private static String cleanHost(String value) {
        String clean = value == null ? "" : value.trim();
        return clean.isEmpty() ? "api.xiaoheihe.cn" : clean;
    }

    private static String canonicalize(String value, String encodeSet,
                                       boolean alreadyEncoded, boolean plusIsSpace) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length();) {
            int codePoint = value.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            boolean keepPercent = codePoint == '%' && alreadyEncoded
                    && isPercentEncoded(value, i);
            boolean encode = codePoint < 0x20 || codePoint == 0x7f
                    || codePoint >= 0x80
                    || contains(encodeSet, codePoint)
                    || (codePoint == '%' && !keepPercent)
                    || (codePoint == '+' && plusIsSpace && !alreadyEncoded);
            if (codePoint == '+' && plusIsSpace) {
                out.append(alreadyEncoded ? "+" : "%2B");
            } else if (encode && !keepPercent) {
                appendEncoded(out, value.substring(i, i + charCount));
            } else {
                out.append(value, i, i + charCount);
            }
            i += charCount;
        }
        return out.toString();
    }

    private static boolean contains(String text, int codePoint) {
        return text != null && codePoint < 128 && text.indexOf((char) codePoint) >= 0;
    }

    private static void appendEncoded(StringBuilder out, String text) {
        try {
            byte[] bytes = text.getBytes("UTF-8");
            for (byte b : bytes) {
                int value = b & 0xff;
                out.append('%').append(HEX[(value >> 4) & 0x0f]).append(HEX[value & 0x0f]);
            }
        } catch (Exception ignored) {
            for (int i = 0; i < text.length(); i++) {
                int value = text.charAt(i);
                out.append('%').append(HEX[(value >> 4) & 0x0f]).append(HEX[value & 0x0f]);
            }
        }
    }

    private static boolean isPercentEncoded(String value, int index) {
        return index + 2 < value.length()
                && hex(value.charAt(index + 1)) >= 0
                && hex(value.charAt(index + 2)) >= 0;
    }

    private static int hex(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }

    private static String percentDecode(String value, boolean plusIsSpace) {
        if (value == null) return null;
        StringBuilder out = new StringBuilder(value.length());
        ByteArrayOutputStream bytes = null;
        for (int i = 0; i < value.length();) {
            char c = value.charAt(i);
            if (c == '%' && i + 2 < value.length()) {
                int hi = hex(value.charAt(i + 1));
                int lo = hex(value.charAt(i + 2));
                if (hi >= 0 && lo >= 0) {
                    if (bytes == null) bytes = new ByteArrayOutputStream();
                    bytes.write((hi << 4) + lo);
                    i += 3;
                    continue;
                }
            }
            flushDecodedBytes(out, bytes);
            bytes = null;
            out.append(c == '+' && plusIsSpace ? ' ' : c);
            i++;
        }
        flushDecodedBytes(out, bytes);
        return out.toString();
    }

    private static void flushDecodedBytes(StringBuilder out, ByteArrayOutputStream bytes) {
        if (bytes == null || bytes.size() == 0) return;
        try {
            out.append(bytes.toString("UTF-8"));
        } catch (Exception ignored) {
            out.append(bytes.toString());
        }
    }

    public static final class Companion {
        private Companion() {}

        public int g(String scheme) {
            return "http".equalsIgnoreCase(scheme) ? 80 : 443;
        }

        public String e(String input, int start, int end, String encodeSet,
                        boolean alreadyEncoded, boolean strict, boolean plusIsSpace,
                        boolean unicodeAllowed, Charset charset) {
            if (input == null) return "";
            int from = Math.max(0, Math.min(start, input.length()));
            int to = Math.max(from, Math.min(end, input.length()));
            return canonicalize(input.substring(from, to), encodeSet,
                    alreadyEncoded, plusIsSpace);
        }

        public String m(String input, int start, int end, boolean plusIsSpace) {
            if (input == null) return "";
            int from = Math.max(0, Math.min(start, input.length()));
            int to = Math.max(from, Math.min(end, input.length()));
            return percentDecode(input.substring(from, to), plusIsSpace);
        }

        public t h(String url) {
            return new t.a().l(url).h();
        }

        public t l(String url) {
            try {
                return h(url);
            } catch (Throwable ignored) {
                return null;
            }
        }

        public static String f(Companion companion, String input, int start, int end,
                               String encodeSet, boolean alreadyEncoded, boolean strict,
                               boolean plusIsSpace, boolean unicodeAllowed, Charset charset,
                               int mask, Object marker) {
            Companion target = companion == null ? INSTANCE : companion;
            int realStart = (mask & 1) != 0 ? 0 : start;
            int realEnd = (mask & 2) != 0 ? (input == null ? 0 : input.length()) : end;
            boolean realAlreadyEncoded = (mask & 8) != 0 ? false : alreadyEncoded;
            boolean realStrict = (mask & 16) != 0 ? false : strict;
            boolean realPlusIsSpace = (mask & 32) != 0 ? false : plusIsSpace;
            boolean realUnicodeAllowed = (mask & 64) != 0 ? false : unicodeAllowed;
            Charset realCharset = (mask & 128) != 0 ? null : charset;
            return target.e(input, realStart, realEnd,
                    encodeSet == null ? "" : encodeSet,
                    realAlreadyEncoded, realStrict, realPlusIsSpace,
                    realUnicodeAllowed, realCharset);
        }

        public static String n(Companion companion, String input, int start, int end,
                               boolean plusIsSpace, int mask, Object marker) {
            Companion target = companion == null ? INSTANCE : companion;
            int realStart = (mask & 1) != 0 ? 0 : start;
            int realEnd = (mask & 2) != 0 ? (input == null ? 0 : input.length()) : end;
            boolean realPlusIsSpace = (mask & 4) != 0 ? false : plusIsSpace;
            return target.m(input, realStart, realEnd, realPlusIsSpace);
        }
    }

    public static final class a {
        public static final String f124283i = "Invalid URL host";
        public static final Companion INSTANCE = new Companion();

        // Field names intentionally mirror the official obfuscated OkHttp builder.
        String a;
        String b = "";
        String c = "";
        String d;
        int e = -1;
        final List<String> f = new ArrayList<>();
        List<String> g;
        String h;

        public a() {
            f.add("");
        }

        public a(t source) {
            this();
            if (source == null) return;
            this.a = source.b;
            this.b = source.c;
            this.c = source.d;
            this.d = source.e;
            this.e = source.f;
            this.f.clear();
            this.f.addAll(source.g);
            this.g = source.h == null ? null : new ArrayList<>(source.h);
            this.h = source.i;
        }

        public a M(String value) {
            this.a = cleanScheme(value);
            return this;
        }

        public a x(String value) {
            this.d = cleanHost(value);
            return this;
        }

        public a D(int value) {
            this.e = value;
            return this;
        }

        public a l(String value) {
            setPath(value);
            return this;
        }

        public a F(String value) {
            setQuery(value);
            return this;
        }

        public a m(String value) {
            setQuery(value);
            return this;
        }

        public a c(String key, String value) {
            addEncodedQuery(key, value);
            return this;
        }

        public a g(String key, String value) {
            addQuery(key, value);
            return this;
        }

        public a R(String key, String value) { return c(key, value); }
        public a W(String key, String value) { return g(key, value); }
        public a a(String value) { addPathSegment(value); return this; }
        public t b() { return h(); }
        public a b(String value) { addPathSegments(value); return this; }
        public a b(String key, String value) { return g(key, value); }
        public a d(String value) { addPathSegment(value); return this; }
        public a e(String value) { addPathSegments(value); return this; }
        public a j(String value) { this.h = value; return this; }
        public a o(String value) { this.h = value; return this; }
        public a n(String value) { this.b = value == null ? "" : value; return this; }
        public a Y(String value) { this.b = value == null ? "" : value; return this; }
        public a k(String value) { this.c = value == null ? "" : value; return this; }
        public a B(String value) { this.c = value == null ? "" : value; return this; }
        public a G() { return this; }
        public a I(String key) { removeQuery(key); return this; }
        public a J(String key) { removeQuery(key); return this; }
        public a K(int index) {
            if (index >= 0 && index < f.size()) f.remove(index);
            if (f.isEmpty()) f.add("");
            return this;
        }
        public a P(int index, String value) {
            if (index >= 0 && index < f.size()) f.set(index, value == null ? "" : value);
            return this;
        }
        public a U(int index, String value) { return P(index, value); }

        public void X(String value) { this.a = cleanScheme(value); }
        public void S(String value) { this.b = value == null ? "" : value; }
        public void O(String value) { this.c = value == null ? "" : value; }
        public void T(String value) { this.d = cleanHost(value); }
        public void V(int value) { this.e = value; }
        public void Q(List<String> value) {
            this.g = value == null ? null : new ArrayList<>(value);
        }
        public void N(String value) { this.h = value; }

        public String getScheme() { return a; }
        public String getEncodedUsername() { return b; }
        public String getEncodedPassword() { return c; }
        public String getHost() { return d; }
        public int getPort() { return e; }
        public List<String> r() { return f; }
        public List<String> s() { return g; }
        public String getEncodedFragment() { return h; }

        public t h() {
            if (a == null || a.isEmpty()) a = "https";
            if (d == null || d.isEmpty()) d = "api.xiaoheihe.cn";
            if (f.isEmpty()) f.add("");
            return new t(this);
        }

        public Map<String, String> queryMap() {
            return toMap(g);
        }

        @Override public String toString() {
            StringBuilder out = new StringBuilder();
            out.append(emptyDefault(a, "https")).append("://").append(cleanHost(d));
            if (e > 0) out.append(':').append(e);
            out.append(encodedPath(f));
            if (g != null && !g.isEmpty()) {
                boolean first = true;
                for (int i = 0; i + 1 < g.size(); i += 2) {
                    String key = g.get(i);
                    if (key == null || key.isEmpty()) continue;
                    out.append(first ? '?' : '&');
                    first = false;
                    out.append(key);
                    String value = g.get(i + 1);
                    if (value != null) out.append('=').append(value);
                }
            }
            if (h != null && !h.isEmpty()) out.append('#').append(h);
            return out.toString();
        }

        private void setPath(String value) {
            f.clear();
            String clean = value == null ? "" : value.trim();
            while (clean.startsWith("/")) clean = clean.substring(1);
            if (clean.isEmpty()) {
                f.add("");
                return;
            }
            String[] parts = clean.split("/", -1);
            for (String part : parts) f.add(part == null ? "" : part);
        }

        private void addPathSegment(String value) {
            if (f.size() == 1 && f.get(0).isEmpty()) f.clear();
            f.add(value == null ? "" : value);
        }

        private void addPathSegments(String value) {
            String clean = value == null ? "" : value;
            for (String part : clean.split("/")) {
                if (!part.isEmpty()) addPathSegment(part);
            }
        }

        private void setQuery(String value) {
            if (value == null || value.isEmpty()) {
                g = null;
                return;
            }
            g = new ArrayList<>();
            String[] pairs = value.split("&");
            for (String pair : pairs) {
                int eq = pair.indexOf('=');
                if (eq >= 0) {
                    addEncodedQuery(pair.substring(0, eq), pair.substring(eq + 1));
                } else {
                    addEncodedQuery(pair, null);
                }
            }
        }

        private void addQuery(String key, String value) {
            if (key == null || key.isEmpty()) return;
            if (g == null) g = new ArrayList<>();
            g.add(canonicalize(key, f124267r, false, true));
            g.add(value == null ? null : canonicalize(value, f124267r, false, true));
        }

        private void addEncodedQuery(String key, String value) {
            if (key == null || key.isEmpty()) return;
            if (g == null) g = new ArrayList<>();
            g.add(canonicalize(key, f124266q, true, true));
            g.add(value == null ? null : canonicalize(value, f124266q, true, true));
        }

        private void removeQuery(String key) {
            if (key == null || g == null) return;
            for (int i = g.size() - 2; i >= 0; i -= 2) {
                if (key.equals(g.get(i))) {
                    g.remove(i + 1);
                    g.remove(i);
                }
            }
            if (g.isEmpty()) g = null;
        }

        public static final class Companion {
            private Companion() {}

            public int e(String input, int pos, int limit) {
                try {
                    int value = Integer.parseInt(slice(input, pos, limit));
                    return value >= 1 && value <= 65535 ? value : -1;
                } catch (Throwable ignored) {
                    return -1;
                }
            }

            public int f(String input, int pos, int limit) {
                if (input == null) return limit;
                int end = Math.max(0, Math.min(limit, input.length()));
                int index = Math.max(0, Math.min(pos, end));
                while (index < end) {
                    char ch = input.charAt(index);
                    if (ch == ':') return index;
                    if (ch == '[') {
                        while (index < end && input.charAt(index) != ']') index++;
                    }
                    index++;
                }
                return end;
            }

            public int g(String input, int pos, int limit) {
                if (input == null) return -1;
                int end = Math.max(0, Math.min(limit, input.length()));
                int index = Math.max(0, Math.min(pos, end));
                if (index >= end || !isAlpha(input.charAt(index))) return -1;
                while (++index < end) {
                    char ch = input.charAt(index);
                    if (ch == ':') return index;
                    if (!(isAlpha(ch) || (ch >= '0' && ch <= '9') || ch == '+' || ch == '-' || ch == '.')) {
                        return -1;
                    }
                }
                return -1;
            }

            public int h(String input, int pos, int limit) {
                if (input == null) return 0;
                int end = Math.max(0, Math.min(limit, input.length()));
                int index = Math.max(0, Math.min(pos, end));
                int slashCount = 0;
                while (index < end) {
                    char ch = input.charAt(index);
                    if (ch == '\\' || ch == '/') {
                        slashCount++;
                        index++;
                    } else {
                        break;
                    }
                }
                return slashCount;
            }

            private static String slice(String input, int pos, int limit) {
                if (input == null) return "";
                int from = Math.max(0, Math.min(pos, input.length()));
                int to = Math.max(from, Math.min(limit, input.length()));
                return input.substring(from, to);
            }

            private static boolean isAlpha(char ch) {
                return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
            }
        }
    }
}
