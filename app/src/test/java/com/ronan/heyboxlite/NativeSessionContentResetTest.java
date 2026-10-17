package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.view.View;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, manifest = Config.NONE)
public class NativeSessionContentResetTest {
    @Test
    public void changingSessionsReleasesPendingFeedAndDiscardsNativeAccountContent() throws Exception {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).get();
        FeedHost host = new FeedHost();
        activity.feedPage = new FeedPage(activity, null, null, null, null, null, false, host);
        activity.searchPage = new SearchPage(activity, null, null, null, null, null);
        FeedItem oldPost = FeedItem.from(new JSONObject().put("linkid", "old-account-post"));
        activity.feedPage.items().add(oldPost);
        activity.searchPage.items().add(oldPost);
        activity.feedPage.paging().recordResponseCursor(0, "old-cursor");
        int oldRefresh = activity.feedPage.paging().begin(true);
        activity.currentDetailItem = oldPost;
        activity.currentDetailBody = new JSONObject();
        activity.currentLinkId = oldPost.id;
        activity.currentLinkHsrc = "old-hsrc";
        activity.currentAuthCode = "old-auth-code";
        activity.retainedPages.put("saved", new View(activity));

        activity.invalidateNativeSessionContent();

        assertTrue(activity.feedPage.items().isEmpty());
        assertFalse(activity.feedPage.paging().refreshing());
        assertFalse(activity.feedPage.paging().accepts(oldRefresh));
        assertTrue(activity.feedPage.paging().firstRequest());
        assertEquals("", activity.feedPage.paging().lastval());
        assertFalse(host.busy);
        int newRefresh = activity.feedPage.paging().begin(true);
        assertTrue(newRefresh > oldRefresh);
        activity.feedPage.paging().finish(true, oldRefresh);
        assertTrue(activity.feedPage.paging().refreshing());
        assertTrue(activity.searchPage.items().isEmpty());
        assertNull(activity.searchPage.listView());
        assertNull(activity.currentDetailItem);
        assertNull(activity.currentDetailBody);
        assertEquals("", activity.currentLinkId);
        assertEquals("", activity.currentLinkHsrc);
        assertEquals("", activity.currentAuthCode);
        assertTrue(activity.retainedPages.isEmpty());
    }

    private static final class FeedHost implements FeedPage.Host {
        boolean busy = true;
        @Override public void prepareFeedChrome() { }
        @Override public boolean isFeedActive() { return false; }
        @Override public void showPage(View page) { }
        @Override public void showSearch() { }
        @Override public void openDetail(FeedItem item) { }
        @Override public void showMessage(String message) { }
        @Override public void showToast(String message) { }
        @Override public void setRefreshBusy(boolean busy) { this.busy = busy; }
        @Override public int pageTopPadding() { return 0; }
        @Override public int roundHeaderTopPadding() { return 0; }
        @Override public int roundHeaderInset() { return 0; }
        @Override public int roundCardInset() { return 0; }
        @Override public int roundSearchInset() { return 0; }
    }
}
