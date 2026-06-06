package com.openzen.heyboxcommunity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class HeyboxSigner {
    private static final String DICT = "AB45STUVWZEFGJ6CH01D237IXYPQRKLMN89";
    private static final SecureRandom RANDOM = new SecureRandom();

    private HeyboxSigner() {}

    static Map<String, String> sign(String path) throws Exception {
        long now = nowSeconds();
        String nonce = createNonce(now);
        String signature = buildSignature(path, now + 1, nonce);
        Map<String, String> values = new HashMap<>();
        applySignatureParams(values, now, nonce, signature);
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
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(32);
        for (byte b : bytes) result.append(String.format(Locale.US, "%02x", b & 0xff));
        return result.toString();
    }
}
