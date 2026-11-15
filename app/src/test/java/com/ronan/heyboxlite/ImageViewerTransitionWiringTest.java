package com.ronan.heyboxlite;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

public class ImageViewerTransitionWiringTest {
    @Test
    public void viewerWaitsForTheFirstBitmapBeforePaintingTheBackdrop() throws Exception {
        String source = readCompose("ComposeImageViewer.kt");
        assertTrue(source.contains("if (ready && !selection.closing)"));
        assertTrue(source.contains("enter.animateTo(1f"));
        assertTrue(source.contains("alpha = enter.value * exit.value"));
    }

    @Test
    public void nonBitmapDrawablesCanProvideAnOpeningPreview() throws Exception {
        String source = read("ImageViewerLauncher.java");

        assertTrue(source.contains("private static Bitmap previewBitmap"));
        assertTrue(source.contains("drawable.draw(new Canvas(preview))"));
    }

    @Test
    public void automaticCommentOriginalLoadingDoesNotFlashTheSpinner() throws Exception {
        String source = readCompose("ComposeImageViewer.kt");
        assertTrue(source.contains("if (bitmap == null && state.loading)"));
        assertTrue(source.contains("enter.value == 1f"));
        assertTrue(source.contains("state.enterCompleted(immediateOriginal, playGif)"));
    }

    @Test
    public void originalBitmapReplacementFitsBeforeTheNextFrame() throws Exception {
        String source = readCompose("ComposeViewerTransform.kt");
        assertTrue(source.contains("fitted = Size"));
        assertTrue(source.contains("pan = bounded(pan)"));
        assertFalse(readCompose("ComposeImageViewer.kt").contains("ZoomImageView"));
    }

    @Test
    public void zoomedImagesClaimHorizontalGesturesFromWearDismiss() throws Exception {
        String imageSource = read("ZoomImageView.java");
        String styleSource = readResource("values-v20/styles.xml");

        assertTrue(imageSource.contains("canScrollHorizontally(int direction)"));
        assertTrue(imageSource.contains("return isZoomed();"));
        assertTrue(styleSource.contains("android:windowSwipeToDismiss"));
        assertTrue(styleSource.contains(">false</item>"));
    }

    private static String read(String name) throws Exception {
        File direct = new File("src/main/java/com/ronan/heyboxlite/" + name);
        File file = direct.isFile() ? direct
                : new File("app/src/main/java/com/ronan/heyboxlite/" + name);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static String readCompose(String name) throws Exception {
        File direct = new File("src/main/kotlin/com/ronan/heyboxlite/" + name);
        File file = direct.isFile() ? direct
                : new File("app/src/main/kotlin/com/ronan/heyboxlite/" + name);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static String readResource(String name) throws Exception {
        File direct = new File("src/main/res/" + name);
        File file = direct.isFile() ? direct : new File("app/src/main/res/" + name);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
