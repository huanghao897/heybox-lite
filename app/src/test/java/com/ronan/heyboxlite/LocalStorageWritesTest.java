package com.ronan.heyboxlite;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class LocalStorageWritesTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void preservesUtf8OverwriteNullAndParentCreationBehaviorWithoutLeakingLeases() throws Exception {
        File file = new File(temporary.getRoot(), "nested/details/123.json");
        LocalStorageWrites.write(file, "{\"text\":\"\u6587\"}");
        assertEquals("{\"text\":\"\u6587\"}", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        assertFalse(StorageFileLeases.inUse(file));
        LocalStorageWrites.write(file, "{}");
        assertEquals(2L, file.length());
        LocalStorageWrites.write(file, null);
        assertEquals(0L, file.length());
        assertFalse(StorageFileLeases.inUse(file));
    }

    @Test public void onlyWatchLaterWritesInvalidateSavedReferenceRevision() throws Exception {
        File manifest = new File(temporary.getRoot(), "watch-later.json");
        long before = LocalStorageWrites.savedRevision();
        LocalStorageWrites.write(new File(temporary.getRoot(), "detail.json"), "{}");
        assertEquals(before, LocalStorageWrites.savedRevision());
        LocalStorageWrites.write(manifest, "[]");
        assertEquals(before + 2L, LocalStorageWrites.savedRevision());
        assertFalse(StorageFileLeases.inUse(manifest));
    }

    @Test public void failedWriteReleasesLeaseAndInvalidatesPotentiallyChangedManifest() throws Exception {
        File directory = temporary.newFolder("watch-later.json");
        long before = LocalStorageWrites.savedRevision();
        LocalStorageWrites.write(directory, "[]");
        assertEquals(before + 2L, LocalStorageWrites.savedRevision());
        assertTrue(directory.isDirectory());
        assertFalse(StorageFileLeases.inUse(directory));
    }
}
