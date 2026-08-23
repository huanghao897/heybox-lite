package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ContentNavigationHistoryTest {
    @Test
    public void restoresNestedRoutesInReverseOrder() {
        ContentNavigationHistory<String> history = new ContentNavigationHistory<>();
        history.push("post-a");
        history.push("post-b");

        assertEquals("post-b", history.peek());
        assertEquals("post-b", history.pop());
        assertEquals("post-a", history.pop());
        assertTrue(history.isEmpty());
    }

    @Test
    public void drainClearsRetainedRoutes() {
        ContentNavigationHistory<String> history = new ContentNavigationHistory<>();
        history.push("post-a");

        assertEquals(1, history.drain().size());
        assertNull(history.pop());
    }
}
