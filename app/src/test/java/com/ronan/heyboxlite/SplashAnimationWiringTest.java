package com.ronan.heyboxlite;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

/** Protects the shared splash scene from regressions into the old typewriter UI. */
public class SplashAnimationWiringTest {
    @Test
    public void activityUsesSharedSceneAndSingleTransition() throws Exception {
        String source = readMain("SplashActivity.java");

        assertTrue(source.contains("new SplashSceneView"));
        assertTrue(source.contains("scene.fadeOut(this::openMain)"));
        assertTrue(source.contains("overridePendingTransition(0, 0)"));
        assertTrue(source.contains("getWindow().setBackgroundDrawable"));
        assertFalse(source.contains("animateText"));
        assertFalse(source.contains("splash_logo"));
    }

    @Test
    public void mainActivityInitialWindowMatchesThemeSurface() throws Exception {
        String source = readMain("MainActivity.java");

        assertTrue(source.contains("getWindow().setBackgroundDrawable"));
        assertTrue(source.contains("Color.rgb(244, 244, 246)"));
    }

    @Test
    public void previewUsesSameSceneAndNoTypingCursor() throws Exception {
        String settings = readMain("AppSettingsPage.java");
        String scene = readMain("SplashSceneView.java");

        assertTrue(settings.contains("new SplashSceneView"));
        assertTrue(settings.contains("overlay.playEntrance()"));
        assertTrue(scene.contains("about_app_mark"));
        assertTrue(scene.contains("Motions.full()"));
        assertTrue(scene.contains("long messageDelay"));
        assertTrue(scene.contains("setStartDelay(messageDelay)"));
        assertFalse(scene.contains("MONOSPACE"));
        assertFalse(scene.contains("substring("));
    }

    private static String readMain(String name) throws Exception {
        File direct = new File("src/main/java/com/ronan/heyboxlite", name);
        File module = new File("app/src/main/java/com/ronan/heyboxlite", name);
        File source = direct.isFile() ? direct : module;
        return new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8);
    }
}
