package com.openzen.heyboxcommunity;

import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class HeyboxSigner {
    private static final String DICT = "AB45STUVWZEFGJ6CH01D237IXYPQRKLMN89";
    private static final String WEB_DICT = "JKMNPQRTX1234OABCDFG56789H";
    private static final String PLAIN_DICT = "BCDFGHJKMNPQRTVWXY23456789";
    private static final String ANDROID_DICT = "2345JKMNPQRT6789BCDFGHVWXY";
    private static final String ANDROID_NONCE_DICT =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    enum Algorithm {
        NONE,
        LEGACY,
        WEB,
        ANDROID,
        PLAIN,
        OLD_MD5,
        OLD_MD5_NONCE,
        PLAIN_NONCE,
        LEGACY_KEY,
        WEB_KEY,
        ANDROID_KEY,
        PLAIN_KEY
    }

    private HeyboxSigner() {}

    static Map<String, String> sign(String path) throws Exception {
        return sign(path, Algorithm.LEGACY);
    }

    static Map<String, String> sign(String path, Algorithm algorithm) throws Exception {
        if (algorithm == Algorithm.NONE) return new HashMap<>();
        if (algorithm == Algorithm.LEGACY_KEY) return renameHkey(sign(path, Algorithm.LEGACY));
        if (algorithm == Algorithm.WEB_KEY) return renameHkey(signWeb(path));
        if (algorithm == Algorithm.ANDROID_KEY) return renameHkey(signAndroid(path));
        if (algorithm == Algorithm.PLAIN_KEY) return renameHkey(signPlain(path));
        if (algorithm == Algorithm.WEB) return signWeb(path);
        if (algorithm == Algorithm.ANDROID) return signAndroid(path);
        if (algorithm == Algorithm.PLAIN) return signPlain(path);
        if (algorithm == Algorithm.PLAIN_NONCE) return signPlainNonce(path);
        if (algorithm == Algorithm.OLD_MD5) return signOldMd5(path);
        if (algorithm == Algorithm.OLD_MD5_NONCE) return signOldMd5Nonce(path);
        long now = nowSeconds();
        String nonce = createNonce(now);
        String signature = buildSignature(path, now + 1, nonce);
        Map<String, String> values = new HashMap<>();
        applySignatureParams(values, now, nonce, signature);
        return values;
    }

    private static Map<String, String> renameHkey(Map<String, String> values) {
        String hkey = values.remove(SecureStrings.hkey());
        if (hkey != null && !hkey.isEmpty()) values.put(SecureStrings.keyParam(), hkey);
        return values;
    }

    private static long nowSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    private static String createNonce(long time) throws Exception {
        byte[] entropy = new byte[16];
        RANDOM.nextBytes(entropy);
        StringBuilder seed = new StringBuilder().append(time);
        for (byte value : entropy) seed.append(value & 0xff);
        return md5Hex(seed.toString()).toUpperCase(Locale.US);
    }

    private static void applySignatureParams(Map<String, String> params, long time,
                                             String nonce, String signature) {
        params.put(SecureStrings.time(), String.valueOf(time));
        params.put(SecureStrings.nonce(), nonce);
        params.put(SecureStrings.hkey(), signature);
    }

    private static Map<String, String> signWeb(String path) throws Exception {
        long now = nowSeconds();
        String nonce = createNonce(now);
        Map<String, String> values = new HashMap<>();
        applySignatureParams(values, now, nonce, buildWebSignature(path, now, nonce));
        return values;
    }

    private static Map<String, String> signAndroid(String path) throws Exception {
        long now = nowSeconds();
        String nonce = createAndroidNonce();
        Map<String, String> values = new HashMap<>();
        applySignatureParams(values, now, nonce, buildAndroidSignature(path, now, nonce));
        return values;
    }

    private static Map<String, String> signPlain(String path) throws Exception {
        long now = nowSeconds();
        Map<String, String> values = new HashMap<>();
        values.put(SecureStrings.time(), String.valueOf(now));
        values.put(SecureStrings.hkey(), buildPlainSignature(path, now));
        return values;
    }

    private static Map<String, String> signPlainNonce(String path) throws Exception {
        long now = nowSeconds();
        String nonce = createNonce(now);
        Map<String, String> values = signPlain(path);
        values.put(SecureStrings.nonce(), nonce);
        return values;
    }

    private static Map<String, String> signOldMd5(String path) throws Exception {
        long now = nowSeconds();
        String normalized = normalizeNoTrailingSlash(path);
        String first = md5Hex(normalized + "/" + oldMd5Salt()
                + SecureStrings.time() + "=" + now);
        String signature = md5Hex(first.replace("a", "app").replace("0", "app"));
        Map<String, String> values = new HashMap<>();
        values.put(SecureStrings.time(), String.valueOf(now));
        values.put(SecureStrings.hkey(), signature.substring(0, Math.min(10, signature.length())));
        return values;
    }

    private static Map<String, String> signOldMd5Nonce(String path) throws Exception {
        long now = nowSeconds();
        String nonce = createNonce(now);
        String normalized = normalizeNoTrailingSlash(path);
        String first = md5Hex(normalized + "/" + oldMd5Salt()
                + SecureStrings.time() + "=" + now);
        String signature = md5Hex(first.replace("a", "app").replace("0", "app"));
        Map<String, String> values = new HashMap<>();
        values.put(SecureStrings.time(), String.valueOf(now));
        values.put(SecureStrings.nonce(), nonce);
        values.put(SecureStrings.hkey(), signature.substring(0, Math.min(10, signature.length())));
        return values;
    }

    private static String oldMd5Salt() {
        return new String(new char[] {'b', 'f', 'h', 'd', 'k', 'u', 'd'});
    }

    private static String createAndroidNonce() {
        StringBuilder nonce = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            nonce.append(ANDROID_NONCE_DICT.charAt(RANDOM.nextInt(ANDROID_NONCE_DICT.length())));
        }
        return nonce.toString();
    }

    private static String buildWebSignature(String path, long timestamp, String nonce)
            throws Exception {
        String normalized = normalize(path);
        String nonceHash = md5Hex(digitsOnly(nonce + WEB_DICT)).toLowerCase(Locale.US);
        String rnd = digitsOnly(md5Hex(String.valueOf(timestamp + 1) + normalized + nonceHash));
        if (rnd.length() > 9) rnd = rnd.substring(0, 9);
        while (rnd.length() < 9) rnd += "0";
        long seed = Long.parseLong(rnd);
        String key = keyFromSeed(seed, WEB_DICT);
        return key + checksumSuffix(key);
    }

    private static String buildAndroidSignature(String path, long timestamp, String nonce)
            throws Exception {
        String normalized = normalize(path);
        long signedTime = timestamp + digitsOnly(nonce).length();
        byte[] digest = sha1Hmac(base64(normalized), signedTime);
        int rndPos = digest[19] & 0x0f;
        long seed = unsignedInt(digest, rndPos) & 0x7fffffffL;
        String key = keyFromSeed(seed, ANDROID_DICT + nonce.toUpperCase(Locale.US));
        return key + checksumSuffix(key);
    }

    static String androidSignatureFor(String path, String timestamp, String nonce)
            throws Exception {
        return buildAndroidSignature(path, Long.parseLong(timestamp), nonce);
    }

    private static String buildPlainSignature(String path, long timestamp) throws Exception {
        String normalized = normalize(path);
        byte[] digest = sha1Hmac(base64(normalized), timestamp + 1);
        int rndPos = digest[19] & 0x0f;
        long seed = unsignedInt(digest, rndPos) & 0x7fffffffL;
        String key = keyFromSeed(seed, PLAIN_DICT);
        return key + checksumSuffix(key);
    }

    private static String keyFromSeed(long seed, String dict) {
        StringBuilder key = new StringBuilder(5);
        for (int i = 0; i < 5; i++) {
            int index = (int) (seed % dict.length());
            seed /= dict.length();
            key.append(dict.charAt(index));
        }
        return key.toString();
    }

    private static String checksumSuffix(String key) {
        int start = Math.max(0, key.length() - 4);
        int[] code = new int[4];
        for (int i = 0; i < code.length; i++) {
            code[i] = key.charAt(start + i);
        }
        int sum =
                (g(code[0]) ^ y(code[1]) ^ s(code[2]) ^ q(code[3])) +
                (q(code[0]) ^ g(code[1]) ^ y(code[2]) ^ s(code[3])) +
                (s(code[0]) ^ q(code[1]) ^ g(code[2]) ^ y(code[3])) +
                (y(code[0]) ^ s(code[1]) ^ q(code[2]) ^ g(code[3]));
        return String.format(Locale.US, "%02d", sum % 100);
    }

    private static String digitsOnly(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= '0' && ch <= '9') out.append(ch);
        }
        return out.toString();
    }

    private static String base64(String value) {
        try {
            return Base64.encodeToString(value.getBytes("UTF-8"), Base64.NO_WRAP);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static byte[] sha1Hmac(String secret, long value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA1"));
        byte[] bytes = new byte[8];
        for (int i = 7; i >= 0; i--) {
            bytes[i] = (byte) (value & 0xff);
            value >>>= 8;
        }
        return mac.doFinal(bytes);
    }

    private static long unsignedInt(byte[] value, int offset) {
        return ((long) (value[offset] & 0xff) << 24)
                | ((long) (value[offset + 1] & 0xff) << 16)
                | ((long) (value[offset + 2] & 0xff) << 8)
                | (long) (value[offset + 3] & 0xff);
    }

    private static String buildSignature(String path, long signedTime, String nonce)
            throws Exception {
        String input = buildSignatureInput(path, signedTime, nonce);
        String digest = md5Hex(input.substring(0, Math.min(20, input.length())));
        return finishSignature(digest);
    }

    private static String buildSignatureInput(String path, long signedTime, String nonce) {
        String a = map(String.valueOf(signedTime), DICT.substring(0, DICT.length() - 2));
        String b = map(normalize(path), DICT);
        String c = map(nonce, DICT);
        StringBuilder mixed = new StringBuilder(a.length() + b.length() + c.length());
        int max = Math.max(a.length(), Math.max(b.length(), c.length()));
        for (int i = 0; i < max; i++) {
            if (i < a.length()) mixed.append(a.charAt(i));
            if (i < b.length()) mixed.append(b.charAt(i));
            if (i < c.length()) mixed.append(c.charAt(i));
        }
        return mixed.toString();
    }

    private static String finishSignature(String digest) {
        int[] code = new int[6];
        for (int i = 0; i < code.length; i++) {
            code[i] = digest.charAt(digest.length() - code.length + i);
        }
        int x0 = code[0], x1 = code[1], x2 = code[2], x3 = code[3];
        code[0] = g(x0) ^ y(x1) ^ s(x2) ^ q(x3);
        code[1] = q(x0) ^ g(x1) ^ y(x2) ^ s(x3);
        code[2] = s(x0) ^ q(x1) ^ g(x2) ^ y(x3);
        code[3] = y(x0) ^ s(x1) ^ q(x2) ^ g(x3);
        int sum = 0;
        for (int value : code) sum += value;
        String checksum = String.format(Locale.US, "%02d", sum % 100);
        return map(digest.substring(0, 5), DICT.substring(0, DICT.length() - 4)) + checksum;
    }

    private static String normalize(String path) {
        String[] pieces = path.split("/");
        StringBuilder value = new StringBuilder("/");
        for (String piece : pieces) {
            if (!piece.isEmpty()) value.append(piece).append('/');
        }
        return value.toString();
    }

    private static String normalizeNoTrailingSlash(String path) {
        String normalized = normalize(path);
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String map(String value, String alphabet) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            result.append(alphabet.charAt(value.charAt(i) % alphabet.length()));
        }
        return result.toString();
    }

    private static int v(int value) {
        return (value & 0x80) != 0 ? ((value << 1) ^ 27) & 0xff : value << 1;
    }

    private static int q(int value) { return v(value) ^ value; }
    private static int s(int value) { return q(v(value)); }
    private static int y(int value) { return s(q(v(value))); }
    private static int g(int value) { return y(value) ^ s(value) ^ q(value); }

    private static String md5Hex(String value) throws Exception {
        byte[] bytes = MessageDigest.getInstance("MD5")
                .digest(value.getBytes("UTF-8"));
        StringBuilder result = new StringBuilder(32);
        for (byte b : bytes) result.append(String.format(Locale.US, "%02x", b & 0xff));
        return result.toString();
    }
}
