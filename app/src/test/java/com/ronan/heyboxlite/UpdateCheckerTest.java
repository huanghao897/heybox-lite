package com.ronan.heyboxlite;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdateCheckerTest {
    @Test public void newerVersionCodeWinsOverStaleBooleanFlag() throws Exception {
        JSONObject payload = new JSONObject()
                .put("version_code", BuildConfig.VERSION_CODE + 1)
                .put("hasUpdate", false);

        assertTrue(UpdateVersionPolicy.isUpdateAvailable(
                payload, "2.16", "2.16", BuildConfig.VERSION_CODE));
    }

    @Test public void newerVersionNameWinsWhenServerOmitsVersionCode() throws Exception {
        JSONObject payload = new JSONObject()
                .put("version_name", "2.17")
                .put("updateAvailable", false);

        assertTrue(UpdateVersionPolicy.isUpdateAvailable(payload,
                UpdateVersionPolicy.latestVersion(payload), "2.16", BuildConfig.VERSION_CODE));
    }

    @Test public void sameVersionCanStillDeliverAnApprovedTestBuild() throws Exception {
        JSONObject payload = new JSONObject()
                .put("versionCode", BuildConfig.VERSION_CODE)
                .put("hasUpdate", true);

        assertTrue(UpdateVersionPolicy.isUpdateAvailable(
                payload, "2.16", "2.16", BuildConfig.VERSION_CODE));
    }

    @Test public void sameVersionWithNoUpdateRemainsCurrent() throws Exception {
        JSONObject payload = new JSONObject()
                .put("versionCode", BuildConfig.VERSION_CODE)
                .put("hasUpdate", false);

        assertFalse(UpdateVersionPolicy.isUpdateAvailable(
                payload, "2.16", "2.16", BuildConfig.VERSION_CODE));
    }

    @Test public void versionAliasesAreReadFromServerMetadata() throws Exception {
        JSONObject payload = new JSONObject().put("latest_version", "v2.17-beta");

        assertEquals("2.17", UpdateVersionPolicy.latestVersion(payload));
    }
}
