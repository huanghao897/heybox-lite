package com.ronan.heyboxlite;

import android.content.Context;
import android.util.Base64;

import com.max.xiaoheihe.utils.NDKTools;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

final class MobileLoginCrypto {
    private static final String RSA_KEY_FRAGMENT =
            "Vg7AXtrTolNtWsa8HiB0tI0YClYaQ\nlOXm4UxLeSxQwSFETwIDAQAB";
    private static final String RSA_KEY_SALT = "578080";

    private MobileLoginCrypto() {}

    static String encrypt(Context context, String normalizedPhone) throws Exception {
        if (context == null || PhoneNumber.normalizeChineseMobile(normalizedPhone).isEmpty()) {
            throw new IllegalArgumentException("invalid phone number");
        }
        Context app = context.getApplicationContext();
        Context officialContext = new OfficialContext(app == null ? context : app);
        com.max.xiaoheihe.app.HeyBoxApplication.init(officialContext);
        NDKTools.load(officialContext);
        String encodedKey = NDKTools.getrsakey(
                officialContext, RSA_KEY_FRAGMENT, RSA_KEY_SALT);
        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalStateException("official RSA key is unavailable");
        }
        PublicKey key = KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(Base64.decode(encodedKey, Base64.DEFAULT)));
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(normalizedPhone.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(encrypted, Base64.DEFAULT);
    }
}
