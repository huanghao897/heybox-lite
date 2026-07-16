package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class AccessStatusTest {
    @Test
    public void parsesBannedResponse() throws Exception {
        AccessStatus status = AccessStatus.from(new JSONObject()
                .put("banned", true)
                .put("message", "已停用"));

        assertTrue(status.banned);
        assertEquals("已停用", status.message);
    }

    @Test
    public void defaultsToAllowed() {
        assertFalse(AccessStatus.from(null).banned);
    }
}
