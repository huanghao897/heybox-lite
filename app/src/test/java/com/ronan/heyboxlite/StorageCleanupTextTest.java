package com.ronan.heyboxlite;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static com.ronan.heyboxlite.StorageTestFiles.*;
import static org.junit.Assert.*;

public class StorageCleanupTextTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void reportsConfirmedDeletionAndRealRemainderOnPartialFailure() throws Exception {
        StorageTestFiles files = new StorageTestFiles(temporary.newFolder());
        File kept = files.image("https://example.com/kept.jpg", "thumb", 100);
        files.image("https://example.com/deleted.jpg", "thumb", 200);
        DisposableStorage storage = new DisposableStorage(files.roots, START,
                file -> !file.equals(kept) && file.delete());

        String text = StorageCleanupText.format(storage.clear(NOW));

        assertTrue(text.contains("200 B"));
        assertTrue(text.contains("100 B"));
        assertFalse(text.contains("300 B"));
        assertTrue(text.contains("\u90e8\u5206\u5931\u8d25"));
    }

    @Test public void exposesIncompleteInventoryInsteadOfClaimingFullSuccess() throws Exception {
        StorageTestFiles files = new StorageTestFiles(temporary.newFolder());
        files.manifestText("broken");

        String text = StorageCleanupText.format(files.storage().clear(NOW));

        assertTrue(text.contains("\u7edf\u8ba1\u4e0d\u5b8c\u6574"));
    }

    @Test public void formatsSmallDiskAmountsWithoutRoundingThemToZeroMegabytes() {
        assertEquals("0 B", StorageCleanupText.size(0));
        assertEquals("999 B", StorageCleanupText.size(999));
        assertEquals("1.0 KB", StorageCleanupText.size(1024));
        assertEquals("1.0 MB", StorageCleanupText.size(1048576));
    }
}
