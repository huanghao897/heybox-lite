package com.ronan.heyboxlite;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

public class GameCardViewWiringTest {
    @Test
    public void cardUpdatesKeepTheExistingViewTreeAndUseStableImageLoading() throws Exception {
        String source = new String(Files.readAllBytes(sourceFile().toPath()),
                StandardCharsets.UTF_8);

        assertFalse(source.contains("removeAllViews()"));
        assertTrue(source.contains("ImageLoader.intoMeasuredStable"));
        assertFalse(source.contains("ImageLoader.intoMeasuredRevealStable"));
    }

    private static File sourceFile() {
        File direct = new File("src/main/java/com/ronan/heyboxlite/GameCardView.java");
        if (direct.isFile()) return direct;
        return new File("app/src/main/java/com/ronan/heyboxlite/GameCardView.java");
    }
}
