package com.ronan.heyboxlite;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static com.ronan.heyboxlite.StorageTestFiles.*;
import static org.junit.Assert.*;

public class StorageFileLeasesTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private StorageTestFiles files;

    @Before public void setUp() throws Exception {
        files = new StorageTestFiles(temporary.newFolder());
    }

    @Test public void originalAndThumbnailFilesAreDisposableWithoutAnActiveLease() throws Exception {
        String original = "https://example.com/long.jpg";
        File exact = files.image(original, original, 10);
        File thumbnail = files.image(original, original + "?thumb=260", 20);

        DisposableStorage.ClearResult result = files.storage().clear(NOW);

        assertEquals(30L, result.reclaimedBytes);
        assertEquals(0L, result.after.deferredBytes);
        assertFalse(exact.exists());
        assertFalse(thumbnail.exists());
    }

    @Test public void sourceLeasePreservesFileUntilDecoderHasClosed() throws Exception {
        String original = "https://example.com/long.jpg";
        File exact = files.image(original, original, 10);
        StorageFileLeases.Lease lease = StorageFileLeases.acquire(exact);
        try {
            assertEquals(10L, files.storage().snapshot(NOW).deferredBytes);
            assertEquals(0L, files.storage().clear(NOW).reclaimedBytes);
            assertTrue(exact.isFile());
        } finally {
            lease.close();
        }

        assertEquals(10L, files.storage().clear(NOW).reclaimedBytes);
        assertFalse(exact.exists());
    }

    @Test public void leaseAcquiredAfterSnapshotStillBlocksDeletion() throws Exception {
        File image = files.image("https://example.com/a.jpg", "thumb", 10);
        DisposableStorage storage = files.storage();
        DisposableStorage.Snapshot before = storage.snapshot(NOW);

        try (StorageFileLeases.Lease ignored = StorageFileLeases.acquire(image)) {
            DisposableStorage.ClearResult result = storage.remove(before, NOW, 0L);
            assertEquals(0L, result.reclaimedBytes);
            assertEquals(10L, result.after.deferredBytes);
            assertTrue(image.exists());
        }
    }

    @Test public void multipleViewersReleaseIndependentlyAndCloseIsIdempotent() throws Exception {
        File image = files.image("https://example.com/a.jpg", "thumb", 10);
        StorageFileLeases.Lease first = StorageFileLeases.acquire(image);
        StorageFileLeases.Lease second = StorageFileLeases.acquire(image);
        try {
            first.close();
            first.close();
            assertTrue(StorageFileLeases.inUse(image));
            assertEquals(0L, files.storage().clear(NOW).reclaimedBytes);
        } finally {
            first.close();
            second.close();
        }
        assertFalse(StorageFileLeases.inUse(image));
        assertEquals(10L, files.storage().clear(NOW).reclaimedBytes);
    }

    @Test public void expiredRegionFileWithLiveLeaseCannotBeUnlinked() throws Exception {
        File region = files.put(files.cache, "long-image-123.img", 10, OLD);
        try (StorageFileLeases.Lease ignored = StorageFileLeases.acquire(region)) {
            assertEquals(0L, files.storage().clear(NOW).reclaimedBytes);
            assertTrue(region.isFile());
        }
        assertEquals(10L, files.storage().clear(NOW).reclaimedBytes);
    }

    @Test public void canonicalAliasesShareTheSameLease() throws Exception {
        File image = files.image("https://example.com/a.jpg", "thumb", 10);
        File alias = new File(image.getParentFile(), "../images/" + image.getName());
        try (StorageFileLeases.Lease ignored = StorageFileLeases.acquire(alias)) {
            assertTrue(StorageFileLeases.inUse(image));
        }
        assertFalse(StorageFileLeases.inUse(image));
    }

}
