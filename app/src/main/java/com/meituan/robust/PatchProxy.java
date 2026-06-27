package com.meituan.robust;

public final class PatchProxy {
    private PatchProxy() {}

    public static PatchProxyResult proxy(Object[] args, Object target,
                                         ChangeQuickRedirect redirect,
                                         boolean isStatic, int methodId,
                                         Class[] argTypes, Class returnType) {
        PatchProxyResult result = new PatchProxyResult();
        result.isSupported = false;
        return result;
    }
}
