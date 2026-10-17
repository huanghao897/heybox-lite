package com.ronan.heyboxlite;

import android.app.Application;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class UpdateCheckerNotesTest {
    @Test public void arrayChangelogBecomesReadableLines() throws Exception {
        JSONObject payload = new JSONObject().put("changelog",
                new JSONArray().put(" first change ").put("").put("second change"));

        assertEquals("first change\nsecond change", UpdateChecker.notes(payload));
    }

    @Test public void stringChangelogKeepsItsText() throws Exception {
        JSONObject payload = new JSONObject().put("changelog", "first change\nsecond change");

        assertEquals("first change\nsecond change", UpdateChecker.notes(payload));
    }

    @Test public void emptyOrNullChangelogUsesLegacyNotes() throws Exception {
        for (Object changelog : new Object[]{JSONObject.NULL, "null", "", new JSONArray()}) {
            JSONObject payload = new JSONObject().put("changelog", changelog).put("notes", "fallback");

            assertEquals("fallback", UpdateChecker.notes(payload));
        }
    }

    @Test public void unrecognizedChangelogDoesNotBecomeJsonText() throws Exception {
        JSONObject payload = new JSONObject().put("changelog", new JSONObject().put("unused", true))
                .put("body", "fallback body");

        assertEquals("fallback body", UpdateChecker.notes(payload));
    }
}
