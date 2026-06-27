package com.max.xiaoheihe.bean.account;

import com.meituan.robust.ChangeQuickRedirect;

public final class User {
    public static ChangeQuickRedirect changeQuickRedirect;

    private final String pkey;
    private final AccountDetailObj detail;

    public User(String pkey, String userId) {
        this.pkey = pkey == null ? "" : pkey;
        this.detail = new AccountDetailObj(userId);
    }

    public String getPkey() {
        return pkey;
    }

    public boolean isLoginFlag() {
        return true;
    }

    public AccountDetailObj getAccount_detail() {
        return detail;
    }
}
