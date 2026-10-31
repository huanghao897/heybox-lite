package com.ronan.heyboxlite;

import static org.junit.Assert.*;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.time.Duration;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, manifest = Config.NONE,
        qualifiers = "w240dp-h320dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeDetailNavigatorTest {
    private MainActivity activity;
    private NativeDetailPage page;
    private NativeDetailNavigator navigation;
    private View feed;

    @Before public void setUp() throws Exception {
        Motions.setLevel(MotionLevel.OFF);
        // Attach a context without starting MainActivity's live account/startup services.
        activity = Robolectric.buildActivity(MainActivity.class).get();
        activity.session = new SessionStore(activity);
        activity.localCache = new LocalCache(activity);
        activity.themeTokens = ThemeTokens.of(false, 0xff111111, 0xff555555);
        activity.content = new FrameLayout(activity);
        activity.shellRoot = new LinearLayout(activity);
        activity.shellBar = new LinearLayout(activity);
        activity.leading = new TextView(activity);
        activity.title = new TextView(activity);
        activity.action = new TextView(activity);
        activity.feedPage = new FeedPage(activity, activity.session, null,
                activity.localCache, activity.themeTokens, null, false, null);
        Field legacy = MainActivity.class.getDeclaredField("legacyUi");
        legacy.setAccessible(true);
        legacy.set(activity, new MainActivityLegacyUi(activity));
        ConnectivityManager connectivity = (ConnectivityManager)
                activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        Shadows.shadowOf(connectivity).setActiveNetworkInfo(null);
        page = activity.nativeDetail;
        navigation = activity.detailNavigation;
        MainDetailFeatures features = page.initialize(null);
        activity.commentController = features.commentController;
        feed = new View(activity);
        activity.content.addView(feed);
        activity.screen = "feed";
    }

    @After public void tearDown() {
        activity.handler.removeCallbacksAndMessages(null);
        navigation.reset();
        page.close();
        activity.screenSnapshots.releaseAll();
        activity.fullScreenSnapshots.releaseAll();
        Motions.setLevel(MotionLevel.REDUCED);
    }

    @Test public void nestedPostsRestoreOriginalPagesAndPositionsBackToFeed() throws Exception {
        verifyNestedReturn();
    }

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    public void roundScreenKeepsTheSameNestedReturnOwnership() throws Exception {
        verifyNestedReturn();
    }

    private void verifyNestedReturn() throws Exception {
        View parent = openDocument("parent", 71, 109, true);
        DetailPager parentPager = page.pager;
        ScrollView parentArticle = page.articleScroll;
        ScrollView parentComments = page.commentScroll;
        assertTrue(navigation.prepareUserSpace());
        assertReleased();
        assertSame(parent, navigation.previousRoot());
        View author = showUserPage();

        View child = openDocument("child", 43, 85, true);
        DetailPager childPager = page.pager;
        assertTrue(navigation.prepareUserSpace());
        View secondAuthor = showUserPage();
        openDocument("grandchild", 27, 36, false);

        navigation.returnFromDetail(true);
        assertSame(secondAuthor, activity.content.getChildAt(0));
        assertEquals("user_space", activity.screen);
        assertReleased();
        navigation.returnFromUserSpace();
        assertSame(child, activity.content.getChildAt(0));
        assertSame(childPager, page.pager);
        assertEquals("child", page.linkId);
        assertEquals(43, page.articleScroll.getScrollY());
        assertEquals(85, page.commentScroll.getScrollY());
        assertTrue(page.pager.showingComments());

        activity.onBackPressed();
        assertSame(childPager, page.pager);
        assertFalse(page.pager.showingComments());
        assertEquals("detail", activity.screen);
        activity.onBackPressed();
        assertSame(author, activity.content.getChildAt(0));
        assertReleased();

        navigation.returnFromUserSpace();
        assertSame(parent, activity.content.getChildAt(0));
        assertSame(parentPager, page.pager);
        assertSame(parentArticle, page.articleScroll);
        assertSame(parentComments, page.commentScroll);
        assertEquals(71, page.articleScroll.getScrollY());
        assertEquals(109, page.commentScroll.getScrollY());
        assertTrue(page.pager.showingComments());
        assertEquals("parent", page.linkId);
        assertEquals("parent-auth", page.authCode);
        assertEquals("parent-diagnostics", page.diagnostics);
        assertEquals("parent", page.body.getString("owner"));

        activity.onBackPressed();
        activity.onBackPressed();
        assertEquals("feed", activity.screen);
        assertSame(feed, activity.content.getChildAt(0));
        assertFalse(navigation.hasHistory());
        assertReleased();
    }

    @Test public void adoptedPreviewIsNotReattachedAndRestoresTheNativeOwner() throws Exception {
        View parent = openDocument("parent", 61, 89, true);
        DetailPager originalPager = page.pager;
        assertTrue(navigation.prepareUserSpace());
        showUserPage();
        activity.content.removeAllViews();
        activity.content.addView(parent);

        activity.adoptShellPreview("detail");

        assertEquals(1, activity.content.getChildCount());
        assertSame(parent, activity.content.getChildAt(0));
        assertSame(originalPager, page.pager);
        assertTrue(page.pager.showingComments());
        assertEquals(61, page.articleScroll.getScrollY());
        assertEquals(89, page.commentScroll.getScrollY());
        assertEquals("detail", activity.screen);
        assertFalse(navigation.hasHistory());
    }

    @Test public void nativeBackButtonReturnsWithoutRecursingThroughItsHost() throws Exception {
        openDocument("parent", 31, 45, false);

        assertTrue(page.detailBackButton().performClick());

        assertEquals("feed", activity.screen);
        assertSame(feed, activity.content.getChildAt(0));
        assertReleased();
    }

    @Test public void committedPagerGestureReturnsWithoutASecondPageTransition() throws Exception {
        openDocument("parent", 31, 45, false);
        DetailPager pager = page.pager;
        touch(pager, MotionEvent.ACTION_DOWN, 16, 0);
        touch(pager, MotionEvent.ACTION_MOVE, pager.getWidth() / 2, 150);
        touch(pager, MotionEvent.ACTION_MOVE, pager.getWidth() - 8, 300);
        assertTrue("The pager must move before committing the return", pager.getScrollX() < 0);
        touch(pager, MotionEvent.ACTION_UP, pager.getWidth() - 8, 400);

        assertEquals("feed", activity.screen);
        assertEquals(1, activity.content.getChildCount());
        assertSame(feed, activity.content.getChildAt(0));
        assertFalse(activity.pendingBackTransition);
        assertFalse(activity.pageTransitions.isRunning());
        assertReleased();
    }

    @Test public void incompleteDetailDoesNotPushOrDiscardItsActiveState() throws Exception {
        navigation.open(item("loading"));
        assertFalse(navigation.prepareUserSpace());
        assertFalse(navigation.hasHistory());
        assertEquals("detail", activity.screen);
        assertEquals("loading", page.linkId);
        assertSame(feed, navigation.returnView());
    }

    @Test public void resettingDropsHistoryTargetsAndAllAccountOwnedDetailData() throws Exception {
        View parent = openDocument("parent", 50, 80, true);
        DetailPager oldPager = page.pager;
        assertTrue(navigation.prepareUserSpace());
        showUserPage();
        assertNotNull(oldPager.getParent());

        navigation.reset();
        page.reset();

        assertFalse(navigation.hasHistory());
        assertNull(navigation.previousRoot());
        assertNull(navigation.returnView());
        assertNull(oldPager.takeReturnView());
        assertNull(feed.getParent());
        assertReleased();
        assertFalse(navigation.restorePrevious(false));
        assertNotSame(parent, activity.content.getChildAt(0));
    }

    @Test public void queuedLoadsCannotPaintOverAUserPageAfterSuspension() throws Exception {
        openDocument("parent", 25, 60, false);
        assertTrue(navigation.prepareUserSpace());
        View author = showUserPage();

        Shadows.shadowOf(activity.getMainLooper()).idleFor(Duration.ofMillis(300));

        assertEquals("user_space", activity.screen);
        assertSame(author, activity.content.getChildAt(0));
        assertReleased();
        assertTrue(navigation.hasHistory());
    }

    @Test public void reloadingDoesNotKeepThePreviousPagerOrWriteContext() throws Exception {
        openDocument("parent", 25, 60, true);

        navigation.open(item("replacement"));

        assertEquals("replacement", page.linkId);
        assertNull(page.body);
        assertNull(page.pager);
        assertNull(page.articleScroll);
        assertNull(page.commentScroll);
        assertEquals("", page.authCode);
        assertEquals("", page.diagnostics);
        assertSame(feed, navigation.returnView());
    }

    private View openDocument(String id, int articleY, int commentY,
                              boolean commentsVisible) throws Exception {
        navigation.open(item(id));
        page.body = new JSONObject().put("owner", id);
        page.authCode = id + "-auth";
        page.diagnostics = id + "-diagnostics";
        page.articleScroll = scroll();
        page.commentScroll = scroll();
        page.pager = new DetailPager(activity, value -> value, false, new DetailPager.Listener() {
            @Override public boolean canSwipeBack() { return true; }
            @Override public void onPageChanged() { page.updateTitle(); }
            @Override public void onReturn() { navigation.returnFromDetail(true); }
        });
        page.pager.setPages(new View(activity), page.articleScroll, page.commentScroll);
        page.pager.setReturnView(navigation.returnView());
        FrameLayout root = new FrameLayout(activity);
        root.addView(page.pager, new FrameLayout.LayoutParams(-1, -1));
        activity.content.removeAllViews();
        activity.content.addView(root, new FrameLayout.LayoutParams(-1, -1));
        int width = activity.getResources().getDisplayMetrics().widthPixels;
        int height = activity.getResources().getDisplayMetrics().heightPixels;
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        page.articleScroll.scrollTo(0, articleY);
        page.commentScroll.scrollTo(0, commentY);
        assertEquals(articleY, page.articleScroll.getScrollY());
        assertEquals(commentY, page.commentScroll.getScrollY());
        if (commentsVisible) page.pager.showComments(false);
        page.loader.markRendered();
        return root;
    }

    private View showUserPage() {
        activity.screen = "user_space";
        View author = new View(activity);
        activity.content.removeAllViews();
        activity.content.addView(author);
        return author;
    }

    private ScrollView scroll() {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFocusable(false);
        View content = new View(activity);
        content.setMinimumHeight(1600);
        scroll.addView(content, new ScrollView.LayoutParams(-1, 1600));
        return scroll;
    }

    private static FeedItem item(String id) throws Exception {
        return FeedItem.from(new JSONObject().put("linkid", id));
    }

    private static void touch(DetailPager pager, int action, int x, long time) {
        MotionEvent event = MotionEvent.obtain(0, time, action, x, 100, 0);
        try {
            pager.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    private void assertReleased() {
        assertNull(page.item);
        assertNull(page.body);
        assertNull(page.pager);
        assertNull(page.articleScroll);
        assertNull(page.commentScroll);
        assertEquals("", page.linkId);
        assertEquals("", page.hsrc);
        assertEquals("", page.authCode);
        assertEquals("", page.diagnostics);
    }
}
