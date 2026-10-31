package com.ronan.heyboxlite;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

public class MainActivityWiringTest {
    @Test
    public void nativeDetailPageImplementsItsOwnFeatureCallbacks() throws Exception {
        String source = new String(Files.readAllBytes(sourceFile().toPath()),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("implements MainDetailFeatures.Host"));
        assertTrue(source.contains("activity.runWithPressFeedback(back, activity::returnFromDetail)"));
        assertFalse(source.contains("return detailBackButton();"));
    }

    private static File sourceFile() {
        File direct = new File("src/main/java/com/ronan/heyboxlite/NativeDetailPage.java");
        if (direct.isFile()) return direct;
        return new File("app/src/main/java/com/ronan/heyboxlite/NativeDetailPage.java");
    }
}
