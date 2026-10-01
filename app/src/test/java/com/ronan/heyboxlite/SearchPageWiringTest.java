package com.ronan.heyboxlite;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

public class SearchPageWiringTest {
    @Test
    public void searchPageKeepsOnlyTheCompactFixedSearchSurfaceAboveResults() throws Exception {
        String source = new String(Files.readAllBytes(sourceFile().toPath()),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("UiComponents.searchSurface"));
        assertTrue(source.contains("int searchTop = roundLayout ? subpageTopPadding : dp(4)"));
        assertTrue(source.contains("list.setPadding(0, this.searchBars.contentTop"));
        assertFalse(source.contains("headerParams"));
    }

    private static File sourceFile() {
        File direct = new File("src/main/java/com/ronan/heyboxlite/SearchPage.java");
        if (direct.isFile()) return direct;
        return new File("app/src/main/java/com/ronan/heyboxlite/SearchPage.java");
    }
}
