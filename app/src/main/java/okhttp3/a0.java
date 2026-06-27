package okhttp3;

import java.util.LinkedHashMap;
import java.util.Map;

public final class a0 {
    private final t a;
    private final LinkedHashMap<String, String> b;

    public a0() {
        this(new a());
    }

    private a0(a builder) {
        this.a = builder.a == null ? new t.a().M("https").x("api.xiaoheihe.cn").h() : builder.a;
        this.b = new LinkedHashMap<>(builder.b);
    }

    public String i(String name) {
        if (name == null) return null;
        return b.get(name);
    }

    public t q() {
        return a;
    }

    public a n() {
        return new a(this);
    }

    public <T> T p(Class<T> ignored) {
        return null;
    }

    public static final class a {
        private t a;
        private final LinkedHashMap<String, String> b = new LinkedHashMap<>();

        public a() {}

        public a(a0 source) {
            if (source == null) return;
            this.a = source.a;
            this.b.putAll(source.b);
        }

        public a a(String name, String value) {
            if (name != null && !name.isEmpty()) b.put(name, value == null ? "" : value);
            return this;
        }

        public a n(String name, String value) {
            return a(name, value);
        }

        public a b(String name, String value) {
            return a(name, value);
        }

        public a D(t url) {
            this.a = url;
            return this;
        }

        public a B(String url) {
            this.a = new t.a().M("https").x("api.xiaoheihe.cn").l(url).h();
            return this;
        }

        public a0 b() {
            return new a0(this);
        }
    }
}
