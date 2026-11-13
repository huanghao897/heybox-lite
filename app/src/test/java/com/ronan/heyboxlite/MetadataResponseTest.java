package com.ronan.heyboxlite;

import android.app.Application;
import android.os.SystemClock;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class MetadataResponseTest {
    @Test public void acceptsExactByteLimitAndClosesInput() throws Exception {
        Response response = new Response(new byte[MetadataResponse.MAX_BYTES]);
        assertEquals(MetadataResponse.MAX_BYTES, read(response).length());
        assertTrue(response.closed);
    }

    @Test public void oversizedDeclaredLengthIsRejectedBeforeAllocatingOrReading() throws Exception {
        Response response = new Response(new byte[0]);
        response.length = String.valueOf(Long.MAX_VALUE);
        expectLimit(response);
        assertFalse(response.opened);
    }

    @Test public void missingAndFalseLengthsStillEnforceActualBytes() throws Exception {
        for (String length : new String[] {null, "1", "-1", "invalid"}) {
            Response response = new Response(new byte[MetadataResponse.MAX_BYTES + 1]);
            response.length = length;
            expectLimit(response);
            assertTrue(response.closed);
        }
    }

    @Test public void utf8LimitCountsBytesRatherThanCharacters() throws Exception {
        char[] chars = new char[MetadataResponse.MAX_BYTES / 2];
        Arrays.fill(chars, '游');
        expectLimit(new Response(new String(chars).getBytes(StandardCharsets.UTF_8)));
    }

    @Test public void gzipBombIsBoundedAfterDecompression() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(new byte[MetadataResponse.MAX_BYTES + 1]);
        }
        Response response = new Response(bytes.toByteArray());
        response.encoding = "gzip";
        response.length = String.valueOf(bytes.size());
        expectLimit(response);
        assertTrue(response.closed);
    }

    @Test public void deadlineRejectsFastDripBeforeUnboundedAccumulation() throws Exception {
        Response response = new Response(new byte[0]);
        response.drip = true;
        try {
            read(response);
            fail("A response must have a total read deadline");
        } catch (java.io.InterruptedIOException expected) {
            assertTrue(response.closed);
            assertTrue(response.bytesRead < MetadataResponse.MAX_BYTES);
        }
    }

    @Test public void errorBodiesUseTheSameLimit() throws Exception {
        Response response = new Response(new byte[MetadataResponse.MAX_BYTES + 1]);
        try {
            MetadataResponse.read(response, 503, SystemClock.elapsedRealtime());
            fail("Error responses must also be bounded");
        } catch (IOException expected) {
            assertTrue(response.closed);
        }
    }

    private static String read(Response response) throws IOException {
        return MetadataResponse.read(response, 200, SystemClock.elapsedRealtime());
    }

    private static void expectLimit(Response response) throws Exception {
        try {
            read(response);
            fail("Oversized metadata must be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("大小限制"));
        }
    }

    private static final class Response extends HttpURLConnection {
        final byte[] bytes;
        String length;
        String encoding;
        boolean opened;
        boolean closed;
        boolean drip;
        int bytesRead;

        Response(byte[] bytes) throws Exception {
            super(new URL("https://heyboxlite.xyz/fixture"));
            this.bytes = bytes;
        }

        @Override public String getHeaderField(String name) { return length; }
        @Override public String getContentEncoding() { return encoding; }
        @Override public InputStream getErrorStream() { return getInputStream(); }
        @Override public InputStream getInputStream() {
            opened = true;
            if (drip) return new InputStream() {
                @Override public int read() {
                    SystemClock.sleep(1000);
                    bytesRead++;
                    return 'x';
                }
                @Override public int read(byte[] buffer, int offset, int count) {
                    buffer[offset] = (byte) read();
                    return 1;
                }
                @Override public void close() { closed = true; }
            };
            return new ByteArrayInputStream(bytes) {
                @Override public void close() { closed = true; }
            };
        }
        @Override public void connect() {}
        @Override public void disconnect() {}
        @Override public boolean usingProxy() { return false; }
    }
}
