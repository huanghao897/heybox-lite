package com.ronan.heyboxlite;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

/** Regression checks for native/Compose back-stack ownership and touch cleanup. */
public class GestureOwnershipWiringTest {
    @Test
    public void hiddenComposeSurfaceDoesNotConsumeNativeBackEvents() throws Exception {
        String bridge = source("java/com/ronan/heyboxlite/ComposeActivityBridge.java");
        assertTrue(bridge.contains("legacyDetailActive && \"detail\".equals(activity.screen)"));
        assertTrue(bridge.contains("activity.composeLayer.getVisibility() != View.VISIBLE"));
        assertTrue(bridge.contains("if (!isComposeSurfaceVisible()) return false;"));
        assertFalse(bridge.contains("if (legacyDetailActive) {\n            returnFromLegacyDetail();"));
    }

    @Test
    public void restoredNativeDetailsReleaseComposeOwnership() throws Exception {
        String activity = source("java/com/ronan/heyboxlite/MainActivity.java");
        assertTrue(activity.contains("this.composeBridge.clearLegacyDetailOwnership();"));
    }

    @Test
    public void gestureContainersReleaseParentInterception() throws Exception {
        String detail = source("java/com/ronan/heyboxlite/DetailPager.java");
        String images = source("java/com/ronan/heyboxlite/ImagePagerCore.java");
        String shell = source("java/com/ronan/heyboxlite/BackSwipeFrameLayout.java");
        assertTrue(detail.contains("requestParentTouch(false);"));
        assertTrue(images.contains("requestParentTouch(false);"));
        assertTrue(shell.contains("requestParentTouch(false);"));
        assertTrue(shell.contains("Child controls (sliders, pagers and zoomable images) own the stream"));
    }

    @Test
    public void composeBackSwipeDoesNotOwnEveryHorizontalControl() throws Exception {
        String swipe = source("kotlin/com/ronan/heyboxlite/ComposeBackSwipe.kt");
        String policy = source("kotlin/com/ronan/heyboxlite/ComposeSwipePolicy.kt");
        assertTrue(swipe.contains("PointerEventPass.Initial"));
        assertTrue(swipe.contains("event.changes.size != 1"));
        assertTrue(policy.contains("startX <= edgePx"));
    }

    @Test
    public void externalComposeUserPageKeepsItsParentRoute() throws Exception {
        String bridge = source("java/com/ronan/heyboxlite/ComposeActivityBridge.java");
        String navigation = source("kotlin/com/ronan/heyboxlite/ComposeNavigationState.kt");
        assertTrue(bridge.contains("showExternalUserSpace(route)"));
        assertTrue(navigation.contains("userSpaceReturnRoute.value = currentRouteSpec()"));
    }

    @Test
    public void composeDetailCapturesReturnLayerBeforeHidingCompose() throws Exception {
        String bridge = source("java/com/ronan/heyboxlite/ComposeActivityBridge.java");
        String activity = source("java/com/ronan/heyboxlite/MainActivity.java");
        assertTrue(bridge.contains("activity.captureComposeReturnSnapshot();"));
        assertTrue(bridge.indexOf("captureReturnFallback(legacyReturnRoute)")
                < bridge.indexOf("hideComposeSurface()"));
        assertTrue(bridge.contains("!activity.composeAppHost.hasLiveReturnPreview(route)"));
        assertTrue(activity.contains("!this.composeBridge.isLegacyDetailActive()"));
    }

    @Test
    public void nativeDetailReturnRestoresComposeEvenWhenRouteIsUnchanged() throws Exception {
        String bridge = source("java/com/ronan/heyboxlite/ComposeActivityBridge.java");
        assertTrue(bridge.contains("boolean composeVisible = isComposeSurfaceVisible();"));
        assertTrue(bridge.contains("|| !composeVisible"));
    }

    @Test
    public void feedSearchAndScrollStateBelongToTheScrollableFeed() throws Exception {
        String feed = source("kotlin/com/ronan/heyboxlite/ComposeFeed.kt");
        String host = source("kotlin/com/ronan/heyboxlite/ComposeAppHost.kt");
        assertTrue(feed.contains("item(key = \"feed-search\")"));
        assertTrue(host.contains("internal val feedListState = LazyListState()"));
        assertTrue(host.contains("mutableMapOf(\"feed\" to feedListState)"));
        assertTrue(host.contains("SaveableStateProvider(page)"));
    }

    @Test
    public void topLevelSwipeUsesLivePreviewsWithoutPagingSideEffects() throws Exception {
        String swipe = source("kotlin/com/ronan/heyboxlite/ComposeSwipeContainer.kt");
        String screens = source("kotlin/com/ronan/heyboxlite/ComposeRouteScreen.kt");
        assertTrue(swipe.contains("key(page)"));
        assertTrue(swipe.contains("takeUnless(ComposeSwipePresentation::isLiveRoute)"));
        assertTrue(screens.contains("observeLoadMore = active"));
    }

    @Test
    public void profileIdentityAndCompactSwitchesAreExplicit() throws Exception {
        String profile = source("kotlin/com/ronan/heyboxlite/ComposeProfileScreen.kt");
        String switches = source("kotlin/com/ronan/heyboxlite/ComposeWatchComponents.kt");
        assertTrue(profile.contains("if (loggedIn && userId.isNotBlank()) \"ID $userId\""));
        assertTrue(switches.contains("Modifier.size(width = watchDp(36), height = watchDp(22))"));
        assertTrue(switches.contains("role = Role.Switch"));
    }

    @Test
    public void nativeDetailUsesLiveReturnContentAndClearsStaleNativeLayers() throws Exception {
        String bridge = source("java/com/ronan/heyboxlite/ComposeActivityBridge.java");
        String factory = source("java/com/ronan/heyboxlite/DetailReturnPreviewFactory.java");
        String host = source("kotlin/com/ronan/heyboxlite/ComposeAppHost.kt");
        assertTrue(factory.indexOf("if (livePreview != null) return livePreview;")
                < factory.indexOf("new ImageView(activity)"));
        assertTrue(bridge.contains("activity.content.removeAllViews();"));
        assertTrue(bridge.contains("createReturnPreview(legacyReturnRoute)"));
        int openStart = host.indexOf("internal fun openDetail(item: FeedItem)");
        int openEnd = host.indexOf("internal fun detailAction", openStart);
        assertFalse(host.substring(openStart, openEnd).contains("navigation.showDetailLoading(item)"));
        assertTrue(bridge.contains("if (activity.composeAppHost != null) activity.composeAppHost.close();"));
    }

    private static String source(String path) throws Exception {
        File direct = new File("src/main/" + path);
        File file = direct.isFile() ? direct : new File("app/src/main/" + path);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
