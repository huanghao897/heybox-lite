package com.ronan.heyboxlite;

import android.app.ApplicationExitInfo;
import org.junit.Test;
import static org.junit.Assert.*;

public class CrashExitHistoryTest {
    @Test public void collectsAbnormalSystemExitsWithoutDoubleReportingJavaCrashes() {
        assertTrue(CrashExitHistory.reportable(ApplicationExitInfo.REASON_CRASH_NATIVE));
        assertTrue(CrashExitHistory.reportable(ApplicationExitInfo.REASON_ANR));
        assertTrue(CrashExitHistory.reportable(ApplicationExitInfo.REASON_LOW_MEMORY));
        assertTrue(CrashExitHistory.reportable(ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE));
        assertFalse(CrashExitHistory.reportable(ApplicationExitInfo.REASON_CRASH));
        assertFalse(CrashExitHistory.reportable(ApplicationExitInfo.REASON_EXIT_SELF));
        assertFalse(CrashExitHistory.reportable(ApplicationExitInfo.REASON_USER_REQUESTED));
        assertFalse(CrashExitHistory.reportable(ApplicationExitInfo.REASON_USER_STOPPED));
    }
}
