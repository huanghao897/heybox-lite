package com.max.xiaoheihe.bean.account;

import com.meituan.robust.ChangeQuickRedirect;

public final class AccountDetailObj {
    public static ChangeQuickRedirect changeQuickRedirect;

    private final String userid;

    public AccountDetailObj(String userid) {
        this.userid = userid == null ? "" : userid;
    }

    public String getUserid() {
        return userid;
    }
}
