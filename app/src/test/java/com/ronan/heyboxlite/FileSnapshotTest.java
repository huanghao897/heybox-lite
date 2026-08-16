package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

public class FileSnapshotTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void sortingUsesCapturedTimestamps() throws Exception {
        File older = temporaryFolder.newFile("older.img");
        File newer = temporaryFolder.newFile("newer.img");
        long now = System.currentTimeMillis();
        long olderTime = now - 4_000L;
        long newerTime = now - 2_000L;
        older.setLastModified(olderTime);
        newer.setLastModified(newerTime);

        List<FileSnapshot> snapshots = FileSnapshot.captureFiles(
                new File[]{newer, older});
        older.setLastModified(now);
        FileSnapshot.sortOldestFirst(snapshots);

        assertEquals(older, snapshots.get(0).file);
        assertEquals(olderTime, snapshots.get(0).lastModified);
        assertEquals(newer, snapshots.get(1).file);
    }
}
