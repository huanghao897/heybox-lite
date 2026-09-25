package com.ronan.heyboxlite;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DiagnosticSourceTest {
    @Test public void automaticReportRetainsSourceAfterQueueRestart() {
        for (DiagnosticSource source : DiagnosticSource.values()) {
            String report = "heybox Lite crash report\n" + source.reportHeader()
                    + "version: 2.15\nerror: example";
            assertEquals(source, DiagnosticSource.fromReport(report));
        }
    }

    @Test public void manualReportContainingHistoricalCrashRemainsManual() {
        String report = "heybox Lite diagnostics\n"
                + DiagnosticSource.MANUAL.reportHeader()
                + "version: 2.15\nlast crash:\nheybox Lite crash report\n"
                + DiagnosticSource.AUTO_CRASH.reportHeader();
        assertEquals(DiagnosticSource.MANUAL, DiagnosticSource.fromReport(report));
    }

    @Test public void unmarkedOldReportsAreNotGuessedFromStackContents() {
        assertEquals(DiagnosticSource.UNKNOWN, DiagnosticSource.fromReport(
                "heybox Lite diagnostics\nversion: 2.15\nlast crash:\n"
                        + DiagnosticSource.AUTO_CRASH.reportHeader()));
        assertEquals(DiagnosticSource.UNKNOWN, DiagnosticSource.fromReport(""));
        assertEquals(DiagnosticSource.UNKNOWN, DiagnosticSource.fromReport(null));
    }
}
