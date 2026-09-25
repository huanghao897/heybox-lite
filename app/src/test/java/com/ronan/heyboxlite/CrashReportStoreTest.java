package com.ronan.heyboxlite;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class CrashReportStoreTest {
    @Rule public final TemporaryFolder folder = new TemporaryFolder();

    @Test public void survivesRestartAndAcknowledgesOnlySuccessfulDelivery() throws Exception {
        File dir = folder.newFolder();
        CrashReportStore first = new CrashReportStore(dir);
        first.enqueue("error: sample\nCookie: pkey=secret\nrecent events: loading");
        assertFalse(first.drain(report -> false));
        CrashReportStore restarted = new CrashReportStore(dir);
        assertEquals(1, restarted.pending().size());
        assertTrue(restarted.drain(report -> {
            assertFalse(report.contains("secret"));
            assertTrue(report.contains("error: sample"));
            return true;
        }));
        assertTrue(restarted.pending().isEmpty());
        assertTrue(restarted.latest().contains("error: sample"));
    }

    @Test public void networkFailureDoesNotDeleteQueuedReport() throws Exception {
        CrashReportStore store = new CrashReportStore(folder.newFolder());
        store.enqueue("error: test");
        try {
            store.drain(report -> { throw new IOException("offline"); });
            fail("Expected network failure");
        } catch (IOException expected) {
            assertEquals(1, store.pending().size());
        }
    }

    @Test public void reportCountAndBytesAreBounded() throws Exception {
        CrashReportStore store = new CrashReportStore(folder.newFolder());
        StringBuilder large = new StringBuilder("error: test\n");
        for (int i = 0; i < 30_000; i++) large.append("中文");
        for (int i = 0; i < 12; i++) store.enqueue(large.toString());
        assertEquals(CrashReportStore.MAX_REPORTS, store.pending().size());
        assertTrue(store.latest().getBytes(StandardCharsets.UTF_8).length <= 28 * 1024);
        assertFalse(store.latest().contains("\uFFFD"));
    }

    @Test public void concurrentRecoveryCannotUploadSameQueueTwice() throws Exception {
        File dir = folder.newFolder();
        CrashReportStore store = new CrashReportStore(dir);
        CrashReportStore second = new CrashReportStore(dir);
        store.enqueue("error: sample");
        AtomicInteger count = new AtomicInteger();
        assertTrue(store.drain(report -> {
            count.incrementAndGet();
            assertFalse(second.drain(other -> { count.incrementAndGet(); return true; }));
            return true;
        }));
        assertEquals(1, count.get());
    }
}
