package com.ronan.heyboxlite;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.concurrent.CountDownLatch;

import javax.crypto.AEADBadTagException;

final class DeviceAuthorizationTestFixtures {
    static final String TOKEN = "hblite_device_abcdefghijklmnopqrstuvwxyz0123456789ABCDE";
    static final String NEXT_TOKEN = "hblite_device_ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcde";

    private DeviceAuthorizationTestFixtures() {}

    static final class Response extends HttpURLConnection {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final CountDownLatch finished = new CountDownLatch(1);
        final int status;
        final String body;
        Runnable onResponse = () -> {};
        IOException failure;
        boolean disconnected;

        Response(int status, String body) throws Exception {
            super(new URL("https://heyboxlite.xyz/fixture"));
            this.status = status;
            this.body = body;
        }

        @Override public int getResponseCode() throws IOException {
            onResponse.run();
            if (failure != null) throw failure;
            return status;
        }

        @Override public InputStream getInputStream() {
            return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        }

        @Override public OutputStream getOutputStream() { return output; }
        @Override public void connect() {}
        @Override public boolean usingProxy() { return false; }
        @Override public void disconnect() {
            disconnected = true;
            finished.countDown();
        }
    }

    @Implements(value = ModernCookieCrypto.class, isInAndroidSdk = false)
    public static final class Crypto {
        static int decryptFailures;
        static int decryptCalls;
        static boolean encryptFails;
        static boolean invalidPayload;
        static boolean invalidTag;

        static void reset() {
            decryptFailures = 0;
            decryptCalls = 0;
            encryptFails = false;
            invalidPayload = false;
            invalidTag = false;
        }

        @Implementation protected static String encrypt(String value) throws Exception {
            if (encryptFails) throw new GeneralSecurityException("fixture encryption unavailable");
            return "fixture:" + new StringBuilder(value).reverse();
        }

        @Implementation protected static String decrypt(String value) throws Exception {
            decryptCalls++;
            if (invalidPayload) throw new ModernCookieCrypto.InvalidPayloadException("fixture invalid");
            if (invalidTag) throw new AEADBadTagException("fixture invalid tag");
            if (decryptFailures > 0) {
                decryptFailures--;
                throw new ModernCookieCrypto.KeyUnavailableException("fixture key unavailable");
            }
            return new StringBuilder(value.substring("fixture:".length())).reverse().toString();
        }
    }
}
