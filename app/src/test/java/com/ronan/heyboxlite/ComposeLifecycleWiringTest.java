package com.ronan.heyboxlite;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

/** Keeps the Compose host on an Activity that installs the Android view owners. */
public class ComposeLifecycleWiringTest {
    @Test
    public void mainActivityProvidesComposeLifecycleOwner() throws Exception {
        String source = new String(Files.readAllBytes(sourceFile().toPath()),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("import androidx.activity.ComponentActivity;"));
        assertTrue(source.contains("MainActivity extends ComponentActivity"));
    }

    private static File sourceFile() {
        File direct = new File("src/main/java/com/ronan/heyboxlite/MainActivity.java");
        if (direct.isFile()) return direct;
        return new File("app/src/main/java/com/ronan/heyboxlite/MainActivity.java");
    }
}
