package com.ronan.heyboxlite;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, manifest = Config.NONE,
        qualifiers = "w240dp-h320dp-mdpi", shadows = NativeDetailTestFixture.RecordingApi.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeCommentOwnershipTest {
    @Test public void lateSuccessClearsOnlyTheOriginalDraftAndCannotReloadAnotherPost() throws Exception {
        try (NativeDetailTestFixture f = loggedIn()) {
            f.open("comment-A");
            Editor first = editor(f, null, " A draft ".trim());
            first.send.performClick();
            assertEquals("comment-A", f.requests.posts.get(0).params.get("link_id"));
            f.open("comment-B");
            Editor second = editor(f, null, "B unsent");
            DetailPager pager = f.page.pager;
            int loads = f.requests.gets.size();

            f.requests.posts.get(0).callback.onSuccess(new JSONObject());

            assertEquals("", f.session.commentDraft("comment-A:root"));
            assertEquals("B unsent", f.session.commentDraft("comment-B:root"));
            assertEquals("B unsent", second.input.getText().toString());
            assertTrue(second.dialog.isShowing());
            assertSame(pager, f.page.pager);
            assertEquals(loads, f.requests.gets.size());
            first.dialog.dismiss();
            second.dialog.dismiss();
        }
    }

    @Test public void editingWhileSendingPreservesTheNewDraftAndEditor() throws Exception {
        try (NativeDetailTestFixture f = loggedIn()) {
            f.open("revised");
            Editor editor = editor(f, null, "sent");
            editor.send.performClick();
            editor.input.setText("new draft");
            int loads = f.requests.gets.size();

            f.requests.posts.get(0).callback.onSuccess(new JSONObject());

            assertEquals("new draft", f.session.commentDraft("revised:root"));
            assertTrue(editor.dialog.isShowing());
            assertTrue(editor.send.isEnabled());
            assertEquals(loads, f.requests.gets.size());
            editor.dialog.dismiss();
        }
    }

    @Test public void editingAwayAndBackToTheSameTextStillChangesTheRevision() throws Exception {
        try (NativeDetailTestFixture f = loggedIn()) {
            f.open("same-text");
            Editor editor = editor(f, null, "sent");
            editor.send.performClick();
            editor.input.setText("temporary edit");
            editor.input.setText("sent");
            f.requests.posts.get(0).callback.onSuccess(new JSONObject());
            assertEquals("sent", f.session.commentDraft("same-text:root"));
            assertTrue(editor.dialog.isShowing());
            editor.dialog.dismiss();
        }
    }

    @Test public void reopeningTheSamePostWithTheSameFeedItemIsANewEntry() throws Exception {
        try (NativeDetailTestFixture f = loggedIn()) {
            f.open("reopen");
            FeedItem sameItem = f.page.item;
            Editor old = editor(f, null, "sent");
            old.send.performClick();
            old.dialog.dismiss();
            f.navigation.open(sameItem);
            f.advance(24);
            Editor next = editor(f, null, "sent");
            DetailPager pager = f.page.pager;
            int loads = f.requests.gets.size();

            f.requests.posts.get(0).callback.onSuccess(new JSONObject());

            assertEquals("sent", f.session.commentDraft("reopen:root"));
            assertSame(pager, f.page.pager);
            assertEquals(loads, f.requests.gets.size());
            assertTrue(next.dialog.isShowing());
            next.dialog.dismiss();
        }
    }

    @Test public void aNewReplyEditorIsNotDismissedOrReloadedByTheRootAcknowledgement() throws Exception {
        try (NativeDetailTestFixture f = loggedIn()) {
            f.open("reply-owner");
            Editor root = editor(f, null, "sent");
            root.send.performClick();
            root.dialog.dismiss();
            Editor reply = editor(f, new JSONObject().put("commentid", "reply-7"), "new reply");
            int loads = f.requests.gets.size();
            f.requests.posts.get(0).callback.onSuccess(new JSONObject());
            assertEquals("", f.session.commentDraft("reply-owner:root"));
            assertEquals("new reply", f.session.commentDraft("reply-owner:reply-7"));
            assertEquals(loads, f.requests.gets.size());
            assertTrue(reply.dialog.isShowing());
            reply.dialog.dismiss();
        }
    }

    @Test public void unchangedCurrentSubmissionClearsDraftAndReloadsOnlyItsEntry() throws Exception {
        try (NativeDetailTestFixture f = loggedIn()) {
            f.open("current");
            Editor editor = editor(f, null, "sent");
            editor.send.performClick();
            int loads = f.requests.gets.size();
            f.requests.posts.get(0).callback.onSuccess(new JSONObject());
            assertEquals("", f.session.commentDraft("current:root"));
            assertFalse(editor.dialog.isShowing());
            assertEquals("current", f.page.linkId);
            assertEquals(loads + 1, f.requests.gets.size());
        }
    }

    @Test public void lateFailureDoesNotEnableOrDismissANewerEditor() throws Exception {
        try (NativeDetailTestFixture f = loggedIn()) {
            f.open("error-owner");
            Editor old = editor(f, null, "sent");
            old.send.performClick();
            old.dialog.dismiss();
            Editor next = editor(f, null, "new");
            f.requests.posts.get(0).callback.onError("fixture error");
            assertFalse(old.send.isEnabled());
            assertTrue(next.send.isEnabled());
            assertTrue(next.dialog.isShowing());
            assertEquals("new", f.session.commentDraft("error-owner:root"));
            next.dialog.dismiss();
        }
    }

    private NativeDetailTestFixture loggedIn() throws Exception {
        NativeDetailTestFixture f = new NativeDetailTestFixture();
        f.activity.getSharedPreferences(SecureStrings.preferencesName(), Context.MODE_PRIVATE).edit()
                .putString(SecureStrings.encryptedCookieKey(), new LegacyCookieCrypto(f.activity)
                        .encrypt("heybox_id=42; pkey=fixture-key")).commit();
        f.session.saveLogin(new JSONObject().put("heyboxid", "42"));
        assertTrue(f.session.isLoggedIn());
        return f;
    }

    private Editor editor(NativeDetailTestFixture f, JSONObject reply, String draft) {
        f.features.commentController.showDialog(reply);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        View root = dialog.getWindow().getDecorView();
        EditText input = NativeDetailTestFixture.find(root, EditText.class);
        assertNotNull(input);
        input.setText(draft);
        return new Editor(dialog, input, NativeDetailTestFixture.text(root, "发送"));
    }

    private static final class Editor {
        final AlertDialog dialog;
        final EditText input;
        final TextView send;
        Editor(AlertDialog dialog, EditText input, TextView send) {
            this.dialog = dialog;
            this.input = input;
            this.send = send;
        }
    }
}
