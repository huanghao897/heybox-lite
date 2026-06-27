package com.max.xiaoheihe.utils;

import android.os.Handler;

import com.meituan.robust.ChangeQuickRedirect;

import java.util.ArrayList;
import java.util.List;

public class q0 {
    public static ChangeQuickRedirect changeQuickRedirect;
    private static volatile q0 r;

    private volatile okhttp3.f0 b;
    private List<f> c;
    private List<okhttp3.g0> d;
    private d e;
    private Object f;
    private String a = "init";
    private int g;
    private boolean h;
    private String i;
    private long j;
    private final Handler k = new Handler();
    private final g l = new g();

    public interface d {
        void handleMessage(String message, e callback);
    }

    public interface e {
        void a(boolean handled);
    }

    public interface f {
        void c3(String payload, String type);
        void q3();
    }

    public class g implements okhttp3.u {
        public static ChangeQuickRedirect changeQuickRedirect;
        private final ArrayList<Long> a = new ArrayList<>();
        private final Handler c = new Handler();
        private String d;

        public g() {}

        public void d(Long timestamp, String wsId) {
            a.add(timestamp == null ? 0L : timestamp);
            d = wsId;
        }

        public void e() {
            c.removeCallbacksAndMessages(null);
        }

        @Override public okhttp3.c0 intercept(okhttp3.u.a chain) throws Exception {
            return chain == null ? new okhttp3.c0() : chain.proceed(chain.request());
        }
    }

    public class h extends okhttp3.g0 {
        public static ChangeQuickRedirect changeQuickRedirect;

        public h() {}

        public void m(String message) {
            if (e != null) e.handleMessage(message, handled -> {});
        }
    }

    private q0() {}

    public static q0 C() {
        if (r == null) {
            synchronized (q0.class) {
                if (r == null) r = new q0();
            }
        }
        return r;
    }

    public void A(okhttp3.f0 webSocket, okhttp3.c0 response) {}
    public Object D() { return f; }
    public String E() { return i; }
    public okhttp3.f0 H() { return b; }
    public void I() {}
    public boolean K() { return h; }
    public void M() {}
    public void N(okhttp3.g0 listener) {
        if (d != null) d.remove(listener);
    }
    public void O(f listener) {
        if (c != null) c.remove(listener);
    }
    public void Q(boolean value) { h = value; }
    public void R(d handler) { e = handler; }
    public void S(Object status) { f = status; }
    public void u(okhttp3.g0 listener) {
        if (d == null) d = new ArrayList<>();
        d.add(listener);
    }
    public void v(f listener) {
        if (c == null) c = new ArrayList<>();
        c.add(listener);
    }
    public void x() {
        if (c != null) c.clear();
    }
    public void y() {
        k.removeCallbacksAndMessages(null);
        if (b != null) b.close(1000, null);
    }
    public void z(okhttp3.f0 webSocket, String text) {}
}
