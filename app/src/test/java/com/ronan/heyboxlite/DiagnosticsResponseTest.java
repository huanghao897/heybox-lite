package com.ronan.heyboxlite;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DiagnosticsResponseTest {
    @Test public void requiresExplicitServerAcknowledgement() throws Exception {
        assertTrue(accepted("{\"ok\":true}"));
        assertFalse(accepted("{\"ok\":false}"));
        assertFalse(accepted("{\"ok\":\"true\"}"));
        assertFalse(accepted("{}"));
        assertFalse(accepted(""));
        assertFalse(accepted("<html>service unavailable</html>"));
    }

    @Test public void oversizedReplyCannotAcknowledgeReport() throws Exception {
        StringBuilder reply = new StringBuilder("{\"ok\":true,\"unexpected\":\"");
        for (int i = 0; i < 5000; i++) reply.append('x');
        reply.append("\"}");
        assertFalse(accepted(reply.toString()));
    }

    @Test public void interruptedResponseRemainsRetryable() {
        try {
            DiagnosticsClient.acceptedResponse(new InputStream() {
                @Override public int read() throws IOException {
                    throw new IOException("disconnected");
                }
            });
            fail("Expected transport failure");
        } catch (IOException expected) {
            // The caller keeps the report pending on transport failure.
        }
    }

    private boolean accepted(String response) throws IOException {
        return DiagnosticsClient.acceptedResponse(new ByteArrayInputStream(
                response.getBytes(StandardCharsets.UTF_8)));
    }
}
