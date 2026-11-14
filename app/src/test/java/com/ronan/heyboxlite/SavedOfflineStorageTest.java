package com.ronan.heyboxlite;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;

import static com.ronan.heyboxlite.StorageTestFiles.*;
import static org.junit.Assert.*;

public class SavedOfflineStorageTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private StorageTestFiles files;

    @Before public void setUp() throws Exception {
        files = new StorageTestFiles(temporary.newFolder());
    }

    @Test public void missingManifestDoesNotPinOrdinaryBrowsingContent() {
        SavedOfflineStorage saved = SavedOfflineStorage.read(files.files);

        assertTrue(saved.readable());
        assertFalse(saved.keepsDetail("123.json"));
        assertFalse(saved.keepsImage(imageName("https://example.com/a.jpg", "thumb")));
    }

    @Test public void normalizesSourcesAndPreservesAllVariantsIncludingTemporaryWrites() throws Exception {
        String original = "https://example.com/saved.jpg";
        files.manifest("saved/id", "http://example.com/saved.jpg?thumb=260");

        SavedOfflineStorage saved = SavedOfflineStorage.read(files.files);

        assertTrue(saved.readable());
        assertTrue(saved.keepsDetail("saved_id.json"));
        assertTrue(saved.keepsImage(imageName(original, original)));
        assertTrue(saved.keepsImage(imageName(original, original + "?thumb=260")));
        assertTrue(saved.keepsImage(imageName(original, original + "?thumb=520") + ".tmp"));
        assertFalse(saved.keepsImage(imageName("https://example.com/unpinned.jpg", "thumb")));
    }

    @Test public void includesEntryImagesAndItemCoverImages() throws Exception {
        files.manifestText("[{\"item\":{\"link_id\":\"123\","
                + "\"image\":\"https://example.com/cover.jpg\","
                + "\"imgs\":[\"https://example.com/item.jpg\"]},"
                + "\"images\":[\"https://example.com/body.jpg\"]}]");

        SavedOfflineStorage saved = SavedOfflineStorage.read(files.files);

        assertTrue(saved.readable());
        for (String name : Arrays.asList("cover", "item", "body")) {
            assertTrue(saved.keepsImage(imageName("https://example.com/" + name + ".jpg", "thumb")));
        }
    }

    @Test public void corruptOrUnexpectedManifestPreservesAllOfflineFiles() throws Exception {
        File image = files.image("https://example.com/a.jpg", "thumb", 10);
        File detail = files.put(files.files, "offline-cache/details/123.json", 20, OLD);
        for (String text : Arrays.asList("", "not json", "{}", "[null]", "[{}]",
                "[{\"item\":{}}]", "[{\"item\":{\"linkid\":\"\"}}]",
                "[{\"item\":{\"linkid\":null}}]",
                "[{\"item\":{\"linkid\":\"123\"},\"images\":{}}]",
                "[{\"item\":{\"linkid\":\"123\"},\"images\":[null]}]")) {
            files.manifestText(text);
            SavedOfflineStorage saved = SavedOfflineStorage.read(files.files);
            assertFalse(text, saved.readable());
            DisposableStorage.ClearResult result = files.storage().clear(NOW);
            assertEquals(text, 0L, result.reclaimedBytes);
            assertFalse(text, result.after.complete);
            assertTrue(image.exists());
            assertTrue(detail.exists());
        }
    }

    @Test public void unreadableManifestDirectoryFailsClosed() throws Exception {
        File manifest = files.manifestText("[]");
        assertTrue(manifest.delete());
        assertTrue(manifest.mkdir());

        SavedOfflineStorage saved = SavedOfflineStorage.read(files.files);

        assertFalse(saved.readable());
        assertTrue(saved.keepsDetail("unknown.json"));
        assertTrue(saved.keepsImage(imageName("https://example.com/a.jpg", "thumb")));
    }

    @Test public void oversizedManifestFailsClosedWithoutUnboundedRead() throws Exception {
        files.put(files.files, "offline-cache/saved-lists/watch-later.json", 2 * 1024 * 1024 + 1, OLD);

        SavedOfflineStorage saved = SavedOfflineStorage.read(files.files);

        assertFalse(saved.readable());
        assertTrue(saved.keepsDetail("123.json"));
    }

    @Test public void matchesImageDiskCacheUrlNormalizationAndSha256() {
        assertEquals("https://example.com/a.jpg", SavedOfflineStorage.originalUrl("//example.com/a.jpg?x=1"));
        assertEquals("https://example.com/a.jpg", SavedOfflineStorage.originalUrl("HTTP://example.com/a.jpg"));
        assertEquals("", SavedOfflineStorage.originalUrl("file:///private/a.jpg"));
        assertEquals("", SavedOfflineStorage.originalUrl(null));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                SavedOfflineStorage.hash("abc"));
    }
}
