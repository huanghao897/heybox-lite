package com.max.xiaoheihe.router.serviceimpl;

import com.meituan.robust.ChangeQuickRedirect;

import okhttp3.a0;
import okhttp3.t;

public final class k {
    public static ChangeQuickRedirect changeQuickRedirect;

    private boolean f(String path) {
        return "/bbs/app/feeds/".equals(path)
                || "/bbs/app/topic/feeds/".equals(path)
                || "/bbs/app/waterfall/feeds/".equals(path);
    }

    public String a(boolean includeClientKeys, a0 request) {
        StringBuilder out = new StringBuilder();
        com.max.xiaoheihe.bean.account.User user = com.max.xiaoheihe.utils.m0.o();
        append(out, com.max.xiaoheihe.utils.p0.M(), user.getPkey());
        if (includeClientKeys) append(out, "x_pkey", user.getPkey());
        append(out, "x_xhh_tokenid", com.max.xiaoheihe.utils.i.f());
        if (request != null) appendRaw(out, request.i("Cookie"));
        if (includeClientKeys && user.isLoginFlag()) {
            append(out, "x_heybox_id", user.getAccount_detail().getUserid());
        }
        return out.toString();
    }

    public native void b(t.a builder, String path);

    public boolean c() {
        return false;
    }

    public String d() {
        return "Heybox";
    }

    public int e(Throwable error) {
        return 0;
    }

    private static void append(StringBuilder out, String key, String value) {
        if (key == null || key.isEmpty() || value == null || value.isEmpty()) return;
        appendRaw(out, key + "=" + value);
    }

    private static void appendRaw(StringBuilder out, String value) {
        if (value == null || value.isEmpty()) return;
        if (out.length() > 0) out.append(';');
        out.append(value);
    }
}
