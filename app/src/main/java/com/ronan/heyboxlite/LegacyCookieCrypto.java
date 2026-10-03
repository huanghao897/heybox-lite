package com.ronan.heyboxlite;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.provider.Settings;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Keeps the pre-KeyStore cookie format isolated for old Android migrations. */
final class LegacyCookieCrypto {
    static final String PREFIX = "L1:";

    private final Context context;

    LegacyCookieCrypto(Context context) {
        this.context = context;
    }

    String encrypt(String value) throws Exception {
        byte[] keys = keyMaterial();
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(slice(keys, 0, 16), "AES"), new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(value.getBytes("UTF-8"));
        byte[] body = join(iv, encrypted);
        byte[] mac = hmac(slice(keys, 16, 32), body);
        return PREFIX + Base64.encodeToString(join(body, mac), Base64.NO_WRAP);
    }

    String decrypt(String value) throws Exception {
        byte[] packed = Base64.decode(value.substring(PREFIX.length()), Base64.NO_WRAP);
        if (packed.length < 49) throw new IllegalArgumentException("Invalid session");
        byte[] body = slice(packed, 0, packed.length - 32);
        byte[] expected = slice(packed, packed.length - 32, packed.length);
        byte[] keys = keyMaterial();
        if (!MessageDigest.isEqual(expected, hmac(slice(keys, 16, 32), body))) {
            throw new SecurityException("Session integrity check failed");
        }
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(slice(keys, 0, 16), "AES"),
                new IvParameterSpec(slice(body, 0, 16)));
        return new String(cipher.doFinal(slice(body, 16, body.length)), "UTF-8");
    }

    @SuppressLint("PackageManagerGetSignatures")
    private byte[] keyMaterial() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(context.getPackageName().getBytes("UTF-8"));
        digest.update((byte) 0x6d);
        digest.update((byte) 0x31);
        String androidId = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId != null) digest.update(androidId.getBytes("UTF-8"));
        PackageInfo info = context.getPackageManager().getPackageInfo(
                context.getPackageName(), PackageManager.GET_SIGNATURES);
        Signature[] signatures = info.signatures;
        if (signatures != null && signatures.length > 0) {
            digest.update(signatures[0].toByteArray());
        }
        return digest.digest();
    }

    private static byte[] hmac(byte[] key, byte[] value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value);
    }

    private static byte[] slice(byte[] source, int start, int end) {
        byte[] result = new byte[end - start];
        System.arraycopy(source, start, result, 0, result.length);
        return result;
    }

    private static byte[] join(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
