package com.ronan.heyboxlite;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

public class DetailScrollPositionWiringTest {
    @Test
    public void savedScrollIsRestoredBeforeTheFirstDraw() throws Exception {
        String source = read();

        assertTrue(source.contains("DetailScrollRestorer.beforeFirstDraw"));
        assertFalse(source.contains("postDelayed(\n                    () -> detail.articleScroll.scrollTo"));
    }

    @Test
    public void restorerUsesThePreDrawBoundary() throws Exception {
        String source = read("DetailScrollRestorer.java");

        assertTrue(source.contains("addOnPreDrawListener"));
        assertTrue(source.contains("scroll.scrollTo(0, targetY)"));
    }

    private static String read() throws Exception {
        return read("MainActivity.java");
    }

    private static String read(String name) throws Exception {
        File direct = new File("src/main/java/com/ronan/heyboxlite/" + name);
        File file = direct.isFile() ? direct
                : new File("app/src/main/java/com/ronan/heyboxlite/" + name);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
