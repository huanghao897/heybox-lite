package com.ronan.heyboxlite;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

/** Regression checks for native/Compose back-stack ownership and touch cleanup. */
public class GestureOwnershipWiringTest {
    @Test
    public void hiddenComposeSurfaceDoesNotConsumeNativeBackEvents() throws Exception {
        String bridge = source("java/com/ronan/heyboxlite/ComposeActivityBridge.java");
        assertTrue(bridge.contains("legacyDetailActive && \"detail\".equals(activity.screen)"));
        assertTrue(bridge.contains("activity.composeLayer.getVisibility() != View.VISIBLE"));
        assertTrue(bridge.contains("if (!isComposeSurfaceVisible()) return false;"));
        assertFalse(bridge.contains("if (legacyDetailActive) {\n            returnFromLegacyDetail();"));
    }

    @Test
    public void restoredNativeDetailsReleaseComposeOwnership() throws Exception {
        String activity = source("java/com/ronan/heyboxlite/MainActivity.java");
        assertTrue(activity.contains("this.composeBridge.clearLegacyDetailOwnership();"));
    }

    @Test
    public void gestureContainersReleaseParentInterception() throws Exception {
        String detail = source("java/com/ronan/heyboxlite/DetailPager.java");
        String images = source("java/com/ronan/heyboxlite/ImagePagerCore.java");
        String shell = source("java/com/ronan/heyboxlite/BackSwipeFrameLayout.java");
        assertTrue(detail.contains("requestParentTouch(false);"));
        assertTrue(images.contains("requestParentTouch(false);"));
        assertTrue(shell.contains("requestParentTouch(false);"));
        assertTrue(shell.contains("Child controls (sliders, pagers and zoomable images) own the stream"));
    }

    private static String source(String path) throws Exception {
        File direct = new File("src/main/" + path);
        File file = direct.isFile() ? direct : new File("app/src/main/" + path);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
