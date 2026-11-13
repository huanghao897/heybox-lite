package com.ronan.heyboxlite;

import static org.junit.Assert.*;

import android.app.Application;
import android.view.View;
import android.widget.ScrollView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, manifest = Config.NONE,
        qualifiers = "w240dp-h320dp-mdpi", shadows = NativeDetailTestFixture.RecordingApi.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeDetailRecoveryTest {
    @Test public void inFlightParentRestartsAndPublishesIntoItsOriginalPagerAndScrollOwners() throws Exception {
        verifyInFlight();
    }

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    public void roundInFlightParentKeepsBothPositionsAndCommentPageSelection() throws Exception {
        verifyInFlight();
    }

    private void verifyInFlight() throws Exception {
        try (NativeDetailTestFixture f = new NativeDetailTestFixture()) {
            f.open("inflight-parent");
            assertTrue(f.page.body.optBoolean("_progressive_preview"));
            Retained retained = position(f);
            ApiClient.Callback old = f.requests.gets.get(0).callback;
            assertTrue(f.navigation.prepareUserSpace());
            View author = f.authorPage();
            old.onSuccess(NativeDetailTestFixture.body("inflight-parent", "stale"));
            f.advance(100);
            assertSame(author, f.activity.content.getChildAt(0));
            assertNull(f.page.pager);

            f.navigation.returnFromUserSpace();

            retained.assertOwners(f);
            assertEquals(2, f.requests.gets.size());
            assertEquals(EndpointProvider.linkTree(), f.requests.gets.get(1).path);
            old.onError("late failure");
            f.requests.gets.get(1).callback.onSuccess(NativeDetailTestFixture.body("inflight-parent", "fresh"));
            f.advance(300);
            retained.assertOwners(f);
            assertTrue(link(f).getString("text").startsWith("fresh"));
            assertEquals("inflight-parent-auth", f.page.authCode);
            assertFalse(f.page.loader.hasPendingRender());
            assertEquals(2, f.requests.gets.size());
        }
    }

    @Test public void pendingBodyIsRescheduledWithoutAnExtraNetworkRequest() throws Exception {
        try (NativeDetailTestFixture f = new NativeDetailTestFixture()) {
            f.open("pending-parent");
            Retained retained = position(f);
            f.requests.gets.get(0).callback.onSuccess(NativeDetailTestFixture.body("pending-parent", "pending full"));
            assertTrue(f.page.loader.hasPendingRender());
            assertTrue(f.navigation.prepareUserSpace());
            View author = f.authorPage();
            f.advance(100);
            assertSame(author, f.activity.content.getChildAt(0));

            f.navigation.returnFromUserSpace();
            assertTrue(f.page.loader.hasPendingRender());
            f.advance(300);

            retained.assertOwners(f);
            assertTrue(link(f).getString("text").startsWith("pending full"));
            assertFalse(f.page.loader.hasPendingRender());
            assertEquals(1, f.requests.gets.size());
        }
    }

    @Test public void webEnrichmentRestartsOnlyTheWebStageAndRejectsItsOldCallback() throws Exception {
        try (NativeDetailTestFixture f = new NativeDetailTestFixture()) {
            FeedItem article = FeedItem.from(NativeDetailTestFixture.item("web-parent").toJson().put("is_article", 1));
            f.navigation.open(article);
            f.advance(24);
            f.requests.gets.get(0).callback.onSuccess(NativeDetailTestFixture.body("web-parent", "article"));
            assertEquals(2, f.requests.gets.size());
            ApiClient.Callback oldWeb = f.requests.gets.get(1).callback;
            f.advance(200);
            Retained retained = position(f);
            assertTrue(f.navigation.prepareUserSpace());
            f.authorPage();
            f.navigation.returnFromUserSpace();
            assertEquals(3, f.requests.gets.size());
            assertEquals(EndpointProvider.linkWebView(), f.requests.gets.get(2).path);

            oldWeb.onSuccess(webGame("99", "stale game"));
            f.advance(30);
            assertEquals(0, ArticleGameCards.count(link(f)));
            f.requests.gets.get(2).callback.onSuccess(webGame("70", "current game"));
            f.advance(300);

            retained.assertOwners(f);
            assertEquals(1, ArticleGameCards.count(link(f)));
            assertNotNull(NativeDetailTestFixture.find(f.page.articleScroll, GameCardView.class));
            assertEquals(3, f.requests.gets.size());
        }
    }

    @Test public void nestedChildCannotConsumeOrBlockItsParentsPendingRender() throws Exception {
        try (NativeDetailTestFixture f = new NativeDetailTestFixture()) {
            f.open("parent-pending");
            Retained retained = position(f);
            f.requests.gets.get(0).callback.onSuccess(NativeDetailTestFixture.body("parent-pending", "parent full"));
            assertTrue(f.navigation.prepareUserSpace());
            View author = f.authorPage();
            f.open("child-active");
            f.requests.gets.get(1).callback.onSuccess(NativeDetailTestFixture.body("child-active", "child full"));
            f.advance(300);
            assertEquals("child-active", f.page.linkId);
            f.navigation.returnFromDetail(true);
            assertSame(author, f.activity.content.getChildAt(0));
            f.navigation.returnFromUserSpace();
            f.advance(300);
            retained.assertOwners(f);
            assertEquals("parent-pending", f.page.linkId);
            assertTrue(link(f).getString("text").startsWith("parent full"));
        }
    }

    @Test public void discardingRetainedHistoryCannotReviveItsRequestOrDocument() throws Exception {
        try (NativeDetailTestFixture f = new NativeDetailTestFixture()) {
            f.open("discarded");
            ApiClient.Callback old = f.requests.gets.get(0).callback;
            assertTrue(f.navigation.prepareUserSpace());
            View author = f.authorPage();
            f.navigation.reset();
            f.page.reset();
            old.onSuccess(NativeDetailTestFixture.body("discarded", "late"));
            f.advance(300);
            assertFalse(f.navigation.restorePrevious(false));
            assertSame(author, f.activity.content.getChildAt(0));
            assertNull(f.page.body);
            assertNull(f.page.pager);
            assertFalse(f.page.loader.hasPendingRender());
        }
    }

    private static Retained position(NativeDetailTestFixture f) {
        f.page.articleScroll.getChildAt(0).setMinimumHeight(2400);
        f.page.commentScroll.getChildAt(0).setMinimumHeight(2400);
        f.layout();
        f.page.articleScroll.scrollTo(0, 71);
        f.page.commentScroll.scrollTo(0, 109);
        f.page.pager.showComments(false);
        Retained retained = new Retained(f);
        retained.assertOwners(f);
        return retained;
    }

    private static JSONObject link(NativeDetailTestFixture f) throws Exception {
        return f.page.body.getJSONObject("result").getJSONObject("link");
    }

    private static JSONObject webGame(String id, String name) throws Exception {
        return new JSONObject().put("result", new JSONObject().put("game", new JSONArray()
                .put(new JSONObject().put("steam_appid", id).put("name", name)
                        .put("price", new JSONObject().put("current", "7")))));
    }

    private static final class Retained {
        final View root;
        final DetailPager pager;
        final ScrollView article;
        final ScrollView comments;
        Retained(NativeDetailTestFixture f) {
            root = f.activity.content.getChildAt(0);
            pager = f.page.pager;
            article = f.page.articleScroll;
            comments = f.page.commentScroll;
        }
        void assertOwners(NativeDetailTestFixture f) {
            assertSame(root, f.activity.content.getChildAt(0));
            assertSame(pager, f.page.pager);
            assertSame(article, f.page.articleScroll);
            assertSame(comments, f.page.commentScroll);
            assertEquals(71, article.getScrollY());
            assertEquals(109, comments.getScrollY());
            assertTrue(pager.showingComments());
        }
    }
}
