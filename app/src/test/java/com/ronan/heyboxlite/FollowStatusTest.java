package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class FollowStatusTest {
    @Test
    public void parsesBooleanNumericAndMutualStates() throws Exception {
        assertEquals(1, FollowStatus.value(new JSONObject().put("is_following", true)));
        assertEquals(0, FollowStatus.value(new JSONObject().put("follow_status", "false")));
        assertEquals(3, FollowStatus.value(new JSONObject().put("follow_state", "mutual")));
        assertTrue(FollowStatus.follows(new JSONObject().put("follow_status", 3), null));
        assertFalse(FollowStatus.follows(new JSONObject().put("follow_status", 2), null));
    }

    @Test
    public void primaryValueWinsAndFallbackFillsMissingState() throws Exception {
        JSONObject fallback = new JSONObject().put("is_follow", 1);
        assertEquals(1, FollowStatus.resolve(new JSONObject(), fallback));
        assertEquals(0, FollowStatus.resolve(
                new JSONObject().put("follow_status", 0), fallback));
    }
}
