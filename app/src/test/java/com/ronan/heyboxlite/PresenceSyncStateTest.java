package com.ronan.heyboxlite;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PresenceSyncStateTest {
    @Test public void queuedUpdateUsesCompletedHeartbeat() {
        PresenceSyncState state = new PresenceSyncState();
        assertTrue(state.shouldSend(0, "account", false, false));
        state.record(100, "account", true, "");
        assertFalse(state.shouldSend(101, "account", true, false));
        assertTrue(state.ready("account", true));
        assertFalse(state.ready("account", false));
    }

    @Test public void failedRegistrationIsNeverReportedAsReadyAndRetryIsBounded() {
        PresenceSyncState state = new PresenceSyncState();
        state.record(100, "account", false, "authorization failed");
        assertFalse(state.ready("account", false));
        assertFalse(state.ready("account", true));
        assertFalse(state.shouldSend(101, "account", false, false));
        assertTrue(state.shouldSend(100 + PresenceSyncState.RETRY_MS, "account", false, false));
    }

    @Test public void normalHeartbeatStaysAtTenMinutes() {
        PresenceSyncState state = new PresenceSyncState();
        state.record(0, "account", true, "");
        assertFalse(state.shouldSend(PresenceSyncState.INTERVAL_MS - 1, "account", true, false));
        assertTrue(state.shouldSend(PresenceSyncState.INTERVAL_MS, "account", true, false));
    }

    @Test public void accountChangeMissingTokenAndExplicitRetryResync() {
        PresenceSyncState state = new PresenceSyncState();
        state.record(0, "old", true, "");
        assertFalse(state.ready("new", true));
        assertTrue(state.shouldSend(1, "new", true, false));
        assertTrue(state.shouldSend(1, "old", false, false));
        assertTrue(state.shouldSend(1, "old", true, true));
        assertTrue(state.shouldSend(1, "", true, false));
    }

    @Test public void queuedChecksRetainFailureReasonUntilSuccessfulRegistration() {
        PresenceSyncState state = new PresenceSyncState();
        state.record(100, "account", false, "authorization expired");
        assertFalse(state.shouldSend(101, "account", false, false));
        assertEquals("authorization expired", state.updateWarning("account", false));
        assertFalse(state.updateWarning("another", true).equals("authorization expired"));

        state.record(200, "account", true, "");
        assertEquals("", state.updateWarning("account", true));
        assertFalse(state.updateWarning("account", false).isEmpty());
    }
}
