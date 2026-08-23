package com.ronan.heyboxlite;

import android.annotation.TargetApi;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyStore;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

@TargetApi(Build.VERSION_CODES.M)
final class ModernCookieCrypto {
    private static final int MIN_IV_BYTES = 12;
    private static final int MAX_IV_BYTES = 32;
    private static final int GCM_TAG_BYTES = 16;

    private ModernCookieCrypto() {}

    static String encrypt(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(value.getBytes("UTF-8"));
        byte[] iv = cipher.getIV();
        byte[] packed = new byte[1 + iv.length + encrypted.length];
        packed[0] = (byte) iv.length;
        System.arraycopy(iv, 0, packed, 1, iv.length);
        System.arraycopy(encrypted, 0, packed, 1 + iv.length, encrypted.length);
        return Base64.encodeToString(packed, Base64.NO_WRAP);
    }

    static String decrypt(String value) throws Exception {
        if (value == null || value.trim().isEmpty()) {
            throw new GeneralSecurityException("Encrypted value is empty");
        }
        final byte[] packed;
        try {
            packed = Base64.decode(value, Base64.NO_WRAP);
        } catch (IllegalArgumentException error) {
            throw new GeneralSecurityException("Encrypted value is not valid Base64", error);
        }
        validatePacked(packed);
        int ivLength = packed[0] & 0xff;
        byte[] iv = new byte[ivLength];
        byte[] encrypted = new byte[packed.length - 1 - ivLength];
        System.arraycopy(packed, 1, iv, 0, ivLength);
        System.arraycopy(packed, 1 + ivLength, encrypted, 0, encrypted.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), "UTF-8");
    }

    static void validatePacked(byte[] packed) throws GeneralSecurityException {
        if (packed == null || packed.length < 1 + MIN_IV_BYTES + GCM_TAG_BYTES) {
            throw new GeneralSecurityException("Encrypted value is truncated");
        }
        int ivLength = packed[0] & 0xff;
        if (ivLength < MIN_IV_BYTES || ivLength > MAX_IV_BYTES) {
            throw new GeneralSecurityException("Encrypted value has an invalid IV length");
        }
        if (packed.length < 1 + ivLength + GCM_TAG_BYTES) {
            throw new GeneralSecurityException("Encrypted value has no authentication tag");
        }
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        String alias = SecureStrings.keyAlias();
        java.security.Key existing = store.getKey(alias, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }
}
