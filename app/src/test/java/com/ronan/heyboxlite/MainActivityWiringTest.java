package com.ronan.heyboxlite;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

public class MainActivityWiringTest {
    @Test
    public void detailBackButtonDelegatesToOuterActivity() throws Exception {
        String source = new String(Files.readAllBytes(sourceFile().toPath()),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("return MainActivity.this.detailBackButton();"));
        assertFalse(source.contains("return detailBackButton();"));
    }

    private static File sourceFile() {
        File direct = new File("src/main/java/com/ronan/heyboxlite/MainActivity.java");
        if (direct.isFile()) return direct;
        return new File("app/src/main/java/com/ronan/heyboxlite/MainActivity.java");
    }
}
