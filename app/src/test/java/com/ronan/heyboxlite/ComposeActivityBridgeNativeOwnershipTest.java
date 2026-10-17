package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, manifest = Config.NONE)
public class ComposeActivityBridgeNativeOwnershipTest {
    @Test
    public void hiddenComposeSurfaceLeavesTheNativeCommentsBackActionToItsPager() {
        MainActivity activity = nativeDetailActivity();
        DetailPager pager = new DetailPager(activity, value -> value, false,
                new DetailPager.Listener() {
                    @Override public boolean canSwipeBack() { return true; }
                    @Override public void onPageChanged() { }
                    @Override public void onReturn() { }
                });
        pager.setPages(new View(activity), new View(activity), new View(activity));
        activity.detailPager = pager;
        pager.showComments(false);
        assertTrue(pager.showingComments());

        activity.onBackPressed();

        assertFalse(pager.showingComments());
        assertSame(pager, activity.detailPager);
        assertEquals("detail", activity.screen);
        assertEquals(View.INVISIBLE, activity.composeLayer.getVisibility());
    }

    @Test
    public void hiddenComposeSurfaceDoesNotReplaceTheNativeUserPage() {
        MainActivity activity = nativeDetailActivity();
        activity.screen = "user_space";

        assertFalse(activity.composeBridge.showUserSpace("42", "Author", ""));
        assertFalse(activity.composeBridge.handleBack());

        assertEquals("user_space", activity.screen);
        assertEquals(View.INVISIBLE, activity.composeLayer.getVisibility());
    }

    private static MainActivity nativeDetailActivity() {
        // Attach an Activity context without starting live startup services. These tests
        // exercise the bridge/native fallback ownership, not MainActivity.onCreate().
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).get();
        activity.composeLayer = new FrameLayout(activity);
        activity.composeLayer.setVisibility(View.INVISIBLE);
        activity.composeBridge = new ComposeActivityBridge(activity);
        activity.screen = "detail";
        return activity;
    }
}
