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
        String source = read("ImageViewerActivity.java");

        assertTrue(source.contains("backdrop.setAlpha(0.0f)"));
        assertTrue(source.contains("backdrop.animate().alpha(1.0f)"));
        assertFalse(source.contains("backdrop.setAlpha(sharedPreviewReady ? 0.0f : 1.0f)"));
    }

    @Test
    public void nonBitmapDrawablesCanProvideAnOpeningPreview() throws Exception {
        String source = read("ImageViewerLauncher.java");

        assertTrue(source.contains("private static Bitmap previewBitmap"));
        assertTrue(source.contains("drawable.draw(new Canvas(preview))"));
    }

    @Test
    public void automaticCommentOriginalLoadingDoesNotFlashTheSpinner() throws Exception {
        String source = read("ImageViewerActivity.java");

        assertTrue(source.contains("long delay = Motions.off() ? 0L"));
        assertTrue(source.contains("spinner.setVisibility(View.GONE)"));
        assertTrue(source.contains("if (!automatic) spinner.setVisibility(View.GONE)"));
        assertTrue(source.contains("if (!automatic) {\n            spinner.setAlpha(1f)"));
    }

    @Test
    public void originalBitmapReplacementFitsBeforeTheNextFrame() throws Exception {
        String source = read("ImageViewerActivity.java");

        assertTrue(source.contains("private void applyLoadedBitmap(ZoomImageView view, Bitmap bitmap)"));
        assertTrue(source.contains("view.setImageBitmap(bitmap);\n        if (view.getWidth() > 0"));
        assertFalse(source.contains("view.animate().cancel();\n            view.setImageBitmap(bitmap)"));
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

    private static String readResource(String name) throws Exception {
        File direct = new File("src/main/res/" + name);
        File file = direct.isFile() ? direct : new File("app/src/main/res/" + name);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
