package com.ronan.heyboxlite;

import android.app.Application;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, manifest = Config.NONE)
public class PostInteractionStateTest {
    private SessionStore session;
    private PostInteractionState owner;

    @Before public void setUp() throws Exception {
        session = new SessionStore(RuntimeEnvironment.getApplication());
        session.saveLogin(new JSONObject().put("heyboxid", "42"));
        owner = new PostInteractionState(session);
    }

    @Test public void pendingAndConfirmedValuesSurviveReplacementObjects() throws Exception {
        FeedItem preview = post("post", "author");
        PostInteractionState.Operation<Boolean> favorite = owner.beginFavorite(preview, true);
        PostInteractionState.Operation<PostInteractionState.Like> like = owner.beginLike(preview, true);
        PostInteractionState.Operation<Boolean> follow = owner.beginFollow("author", false, true);
        FeedItem full = post("post", "author");
        owner.apply(full);
        assertTrue(full.favorited && full.liked && full.following && full.followPending);
        assertEquals(11, full.likes);
        assertTrue(owner.complete(favorite, true));
        assertTrue(owner.complete(like, true));
        assertTrue(owner.complete(follow, true));
        FeedItem later = post("post", "author");
        owner.apply(later);
        assertTrue(later.favorited && later.liked && later.following);
        assertFalse(later.followPending);
        assertEquals(11, later.likes);
    }

    @Test public void failuresRestoreBaselineAcrossReplacementsAndAuthorSiblings() throws Exception {
        FeedItem preview = post("post", "author");
        PostInteractionState.Operation<Boolean> favorite = owner.beginFavorite(preview, true);
        PostInteractionState.Operation<PostInteractionState.Like> like = owner.beginLike(preview, true);
        PostInteractionState.Operation<Boolean> follow = owner.beginFollow("author", false, true);
        FeedItem full = post("post", "author");
        FeedItem sibling = post("sibling", "author");
        owner.applyAll(Arrays.asList(full, sibling));
        assertTrue(sibling.following && sibling.followPending);
        owner.complete(favorite, false);
        owner.complete(like, false);
        owner.complete(follow, false);
        owner.applyAll(Arrays.asList(full, sibling));
        assertFalse(full.favorited || full.liked || full.following || full.followPending);
        assertFalse(sibling.following || sibling.followPending);
        assertEquals(10, full.likes);
    }

    @Test public void newerSuccessRejectsLateSuccessFailureAndDuplicateCallbacks() throws Exception {
        FeedItem item = post("post", "author");
        PostInteractionState.Operation<Boolean> first = owner.beginFavorite(item, true);
        PostInteractionState.Operation<Boolean> second = owner.beginFavorite(item, false);
        assertTrue(owner.complete(second, true));
        assertFalse(owner.complete(first, true));
        assertFalse(owner.complete(first, false));
        assertFalse(owner.complete(second, false));
        owner.apply(item);
        assertFalse(item.favorited);
    }

    @Test public void olderSuccessCannotOverrideNewerPendingAndItsFailureRestoresConfirmedValue() throws Exception {
        FeedItem item = post("post", "author");
        PostInteractionState.Operation<Boolean> first = owner.beginFavorite(item, true);
        PostInteractionState.Operation<Boolean> second = owner.beginFavorite(item, false);
        owner.complete(first, true);
        owner.apply(item);
        assertFalse(item.favorited);
        owner.complete(second, false);
        owner.apply(item);
        assertTrue(item.favorited);
    }

    @Test public void twoFailedTogglesDoNotRollbackToTheFirstOptimisticValue() throws Exception {
        FeedItem item = post("post", "author");
        PostInteractionState.Operation<PostInteractionState.Like> first = owner.beginLike(item, true);
        PostInteractionState.Operation<PostInteractionState.Like> second = owner.beginLike(item, false);
        owner.complete(second, false);
        owner.apply(item);
        assertTrue(item.liked);
        owner.complete(first, false);
        owner.apply(item);
        assertFalse(item.liked);
        assertEquals(10, item.likes);
    }

    @Test public void followVersionsAreSharedByAuthorButPostActionsRemainIndependent() throws Exception {
        FeedItem first = post("one", "author");
        FeedItem second = post("two", "author");
        FeedItem stranger = post("three", "other");
        PostInteractionState.Operation<Boolean> follow = owner.beginFollow("author", false, true);
        PostInteractionState.Operation<Boolean> unfollow = owner.beginFollow("author", true, false);
        owner.beginFavorite(first, true);
        owner.complete(unfollow, true);
        assertFalse(owner.complete(follow, true));
        owner.applyAll(Arrays.asList(first, second, stranger));
        assertTrue(first.favorited);
        assertFalse(second.favorited);
        assertFalse(first.following || second.following || stranger.following);
        assertFalse(first.followPending || second.followPending);
    }

    @Test public void accountSwitchDiscardsPendingAndConfirmedOverrides() throws Exception {
        FeedItem item = post("post", "author");
        PostInteractionState.Operation<Boolean> favorite = owner.beginFavorite(item, true);
        PostInteractionState.Operation<Boolean> follow = owner.beginFollow("author", false, true);
        owner.complete(follow, true);
        session.saveLogin(new JSONObject().put("heyboxid", "77"));
        assertFalse(owner.complete(favorite, true));
        assertNull(owner.favorite(item.id));
        assertNull(owner.following(item.authorId));
        FeedItem replacement = post("post", "author");
        owner.apply(replacement);
        assertFalse(replacement.favorited || replacement.following || replacement.followPending);
    }

    @Test public void sameAccountReauthenticationAndExplicitResetAlsoInvalidateCallbacks() throws Exception {
        FeedItem item = post("post", "author");
        PostInteractionState.Operation<PostInteractionState.Like> old = owner.beginLike(item, true);
        session.saveLogin(new JSONObject().put("heyboxid", "42"));
        assertFalse(owner.complete(old, false));
        assertNull(owner.like(item.id));
        PostInteractionState.Operation<Boolean> next = owner.beginFavorite(item, true);
        owner.invalidateSession();
        assertFalse(owner.complete(next, true));
        assertNull(owner.favorite(item.id));
    }

    @Test public void untouchedFieldsKeepTheLatestServerState() throws Exception {
        FeedItem item = post("post", "author");
        item.likes = 999;
        item.liked = true;
        item.favorited = true;
        item.following = true;
        owner.apply(item);
        assertTrue(item.liked && item.favorited && item.following);
        assertEquals(999, item.likes);
        owner.complete(owner.beginFavorite(item, false), true);
        item.likes = 1200;
        owner.apply(item);
        assertEquals(1200, item.likes);
        assertFalse(item.favorited);
    }

    private FeedItem post(String id, String author) throws Exception {
        return FeedItem.from(new JSONObject().put("linkid", id).put("link_award_num", 10)
                .put("user", new JSONObject().put("userid", author)));
    }
}
