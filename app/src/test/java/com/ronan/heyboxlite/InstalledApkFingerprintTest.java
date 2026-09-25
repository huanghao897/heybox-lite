package com.ronan.heyboxlite;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class InstalledApkFingerprintTest {
    @Rule public TemporaryFolder files = new TemporaryFolder();

    @Test public void hashesInstalledBytesAndInvalidatesCacheAfterReplacement() throws Exception {
        File apk = files.newFile("installed.apk");
        write(apk, "abc");
        String digest = InstalledApkFingerprint.read(apk);
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", digest);
        assertEquals(digest, InstalledApkFingerprint.read(apk));
        write(apk, "replacement");
        assertNotEquals(digest, InstalledApkFingerprint.read(apk));
    }

    @Test(expected = IOException.class)
    public void missingInstalledFileIsNotMistakenForSuccessfulInstallation() throws Exception {
        InstalledApkFingerprint.read(new File(files.getRoot(), "missing.apk"));
    }

    private static void write(File file, String value) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
