package okhttp3;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class z {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public z() {}

    public ExecutorService a() { return executor; }
    public n n() { return n.a; }
    public f0 a(a0 request, g0 listener) { return new f0(request, listener); }
    public f0 b(a0 request, g0 listener) { return new f0(request, listener); }
    public f0 c(a0 request, g0 listener) { return new f0(request, listener); }
    public f0 I(a0 request, g0 listener) { return new f0(request, listener); }

    public static final class a {
        public a() {}

        public a b(long timeout, TimeUnit unit) { return this; }
        public a d(long timeout, TimeUnit unit) { return this; }
        public a f(long timeout, TimeUnit unit) { return this; }
        public a a(u interceptor) { return this; }
        public a c(u interceptor) { return this; }
        public z e() { return new z(); }
        public z b() { return new z(); }
    }
}
