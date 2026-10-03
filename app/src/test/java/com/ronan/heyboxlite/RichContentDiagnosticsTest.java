package com.ronan.heyboxlite;

import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.junit.Test;

public class RichContentDiagnosticsTest {
    @Test
    public void facadeReportsEmptySourceWithoutFrameworkDependencies() {
        String output = RichContent.diagnostics(null, new JSONArray());

        assertTrue(output.contains("RichContent diagnostics"));
        assertTrue(output.contains("source: null"));
        assertTrue(output.contains("fallbackImages: 0"));
    }
}
