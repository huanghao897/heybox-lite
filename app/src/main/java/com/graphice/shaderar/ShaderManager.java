package com.graphice.shaderar;

import android.content.Context;

import com.openzen.heyboxcommunity.NativeLibraryLoader;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.ZipFile;

public final class ShaderManager {
    public static final a a = new a(null);
    private static boolean b;
    private static Integer c;
    private static boolean loaded;

    private ShaderManager() {}

    public static synchronized void load(Context context) {
        if (loaded) return;
        NativeLibraryLoader.load(context, "glesv3_1");
        loaded = true;
    }

    public static final class a {
        private a() {}

        public a(Object ignored) {}

        public static void e() {}

        public static void j() {}

        public String a(Context context) {
            try {
                if (context != null
                        && "com.max.xiaoheihe".equals(context.getPackageName())) {
                    return "9afe429f337b89932b0abe53a47c69da";
                }
                return I(String.valueOf(new ZipFile(context.getPackageCodePath())
                        .getEntry("classes.dex").getCrc()));
            } catch (Throwable ignored) {
                return "";
            }
        }

        public String b(Context context, String value) {
            return ShaderManager.getBufSize(context, value);
        }

        public String c(Context context, String value) {
            return ShaderManager.getChunkFlag(context, value);
        }

        public boolean d() {
            return ShaderManager.b;
        }

        public String f(Context context, String chunk, String time, String userId) {
            return ShaderManager.getIdxOffset(context, chunk, time, userId);
        }

        public int g(int value) {
            return ShaderManager.getMemPtr(value);
        }

        public String h(Context context, String nonce, boolean rnd) {
            return ShaderManager.getObjType(context, nonce, rnd);
        }

        public Integer i() {
            return ShaderManager.c;
        }

        public void k(String value, String time) {
            ShaderManager.setBlendMode(value, time);
        }

        public void l(String value, String nonce) {
            ShaderManager.setBuf(value, nonce);
        }

        public void m(String value, String nonce) {
            ShaderManager.setCaseFlag(value, nonce);
        }

        public void n(String value, String nonce) {
            ShaderManager.setCtxDword(value, nonce);
        }

        public void o(String value, String nonce) {
            ShaderManager.setDLen(value, nonce);
        }

        public void p(String value, String nonce) {
            ShaderManager.setDepRel(value, nonce);
        }

        public void q(String value, String nonce) {
            ShaderManager.setEmbCache(value, nonce);
        }

        public void r(String value, String nonce) {
            ShaderManager.setEntType(value, nonce);
        }

        public void s(String value, String nonce) {
            ShaderManager.setGramLen(value, nonce);
        }

        public void t(boolean ready) {
            ShaderManager.b = ready;
        }

        public void u(byte[] value) {
            ShaderManager.setHdrField(value);
        }

        public void v(String value, String nonce) {
            ShaderManager.setITtl(value, nonce);
        }

        public void w(String value, String nonce) {
            ShaderManager.setIdxValue(value, nonce);
        }

        public void x(String value, String nonce) {
            ShaderManager.setMEntry(value, nonce);
        }

        public void y(String value, String nonce) {
            ShaderManager.setMacAddr(value, nonce);
        }

        public void z(String path, boolean enabled) {
            ShaderManager.setParseDepth(path, enabled);
        }

        public void A(String value, String nonce) {
            ShaderManager.setPtrOffset(value, nonce);
        }

        public void B(String value, String nonce) {
            ShaderManager.setSecAttr(value, nonce);
        }

        public void C() {
            ShaderManager.setSecLevel();
        }

        public void D(String value, String nonce) {
            ShaderManager.setTCipher(value, nonce);
        }

        public void E(String value, String nonce) {
            ShaderManager.setTokenHash(value, nonce);
        }

        public void F(Integer value) {
            ShaderManager.c = value;
        }

        public void G(String value, String nonce) {
            ShaderManager.setViewport(value, nonce);
        }

        public void H(String value, String nonce) {
            ShaderManager.setVocabSize(value, nonce);
        }

        public String I(String input) {
            try {
                MessageDigest digest = MessageDigest.getInstance("MD5");
                byte[] bytes = digest.digest(input.getBytes("UTF-8"));
                StringBuilder out = new StringBuilder(bytes.length * 2);
                for (byte value : bytes) {
                    int part = value & 0xff;
                    if (part < 16) out.append('0');
                    out.append(Integer.toHexString(part));
                }
                return out.toString();
            } catch (UnsupportedEncodingException | NoSuchAlgorithmException ignored) {
                return "";
            }
        }
    }

    public static String e(Context context) {
        return a.a(context);
    }

    public static boolean f() {
        return a.d();
    }

    public static Integer g() {
        return a.i();
    }

    public static void h(boolean ready) {
        a.t(ready);
    }

    public static void i(Integer version) {
        a.F(version);
    }

    public static String j(String input) {
        return a.I(input);
    }

    public static native String getBufSize(Context context, String value);

    public static native String getChunkFlag(Context context, String value);

    public static native String getIdxOffset(Context context, String chunk, String time, String userId);

    public static native int getMemPtr(int value);

    public static native String getObjType(Context context, String nonce, boolean rnd);

    public static native void setBlendMode(String value, String time);

    public static native void setBuf(String value, String nonce);

    public static native void setCaseFlag(String value, String nonce);

    public static native void setCtxDword(String value, String nonce);

    public static native void setDLen(String value, String nonce);

    public static native void setDepRel(String value, String nonce);

    public static native void setEmbCache(String value, String nonce);

    public static native void setEntType(String value, String nonce);

    public static native void setGramLen(String value, String nonce);

    public static native void setHdrField(byte[] value);

    public static native void setITtl(String value, String nonce);

    public static native void setIdxValue(String value, String nonce);

    public static native void setMEntry(String value, String nonce);

    public static native void setMacAddr(String value, String nonce);

    public static native void setParseDepth(String path, boolean enabled);

    public static native void setPtrOffset(String value, String nonce);

    public static native void setSecAttr(String value, String nonce);

    public static native void setSecLevel();

    public static native void setTCipher(String value, String nonce);

    public static native void setTokenHash(String value, String nonce);

    public static native void setViewport(String value, String nonce);

    public static native void setVocabSize(String value, String nonce);
}
