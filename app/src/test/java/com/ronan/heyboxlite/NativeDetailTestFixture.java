package com.ronan.heyboxlite;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowNetworkInfo;

/** Real native page/navigator owners with an offline recording transport. */
final class NativeDetailTestFixture implements AutoCloseable {
    final MainActivity activity = Robolectric.buildActivity(MainActivity.class).get();
    final NativeDetailPage page = activity.nativeDetail;
    final NativeDetailNavigator navigation = activity.detailNavigation;
    final SessionStore session = new SessionStore(activity);
    final ApiClient api = new ApiClient(session);
    final RecordingApi requests = Shadow.extract(api);
    final MainDetailFeatures features;
    final View feed = new View(activity);
    private final int motion = Motions.level();

    NativeDetailTestFixture() throws Exception {
        Motions.setLevel(MotionLevel.OFF);
        activity.session = session;
        activity.api = api;
        activity.localCache = new LocalCache(activity);
        activity.themeTokens = ThemeTokens.of(false, 0xff111111, 0xff555555);
        activity.writeActions = new WriteActionClient(api, session, null, null);
        activity.content = new FrameLayout(activity);
        activity.shellRoot = new LinearLayout(activity);
        activity.shellBar = new LinearLayout(activity);
        activity.leading = new TextView(activity);
        activity.title = new TextView(activity);
        activity.action = new TextView(activity);
        activity.feedPage = new FeedPage(activity, session, api, activity.localCache,
                activity.themeTokens, null, false, null);
        Field legacy = MainActivity.class.getDeclaredField("legacyUi");
        legacy.setAccessible(true);
        legacy.set(activity, new MainActivityLegacyUi(activity));
        activity.postActions = new PostActionController(activity, session, activity.writeActions,
                activity.localCache, activity.themeTokens, false, new PostActionController.Host() {
                    @Override public String currentLinkId() { return page.linkId; }
                    @Override public String currentLinkHsrc() { return page.hsrc; }
                    @Override public FeedItem currentDetailItem() { return page.item; }
                    @Override public List<FeedItem> feedItems() { return Collections.emptyList(); }
                    @Override public List<FeedItem> searchItems() { return Collections.emptyList(); }
                    @Override public void feedChanged() { }
                    @Override public void showToast(String message) { }
                });
        DetailContentRenderer renderer = new DetailContentRenderer(activity, session,
                activity.themeTokens, false, (source, urls, index) -> { });
        features = page.initialize(renderer);
        activity.commentController = features.commentController;
        activity.detailActionBar = features.actionBar;
        activity.setContentView(activity.content);
        activity.content.addView(feed);
        activity.screen = "feed";
        ConnectivityManager connectivity = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        Shadows.shadowOf(connectivity).setActiveNetworkInfo(ShadowNetworkInfo.newInstance(
                NetworkInfo.DetailedState.CONNECTED, ConnectivityManager.TYPE_WIFI, 0, true, true));
    }

    void open(String id) throws Exception {
        navigation.open(item(id));
        advance(24);
    }

    void advance(long millis) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));
        layout();
    }

    void layout() {
        int width = activity.getResources().getDisplayMetrics().widthPixels;
        int height = activity.getResources().getDisplayMetrics().heightPixels;
        activity.content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        activity.content.layout(0, 0, width, height);
        activity.content.getViewTreeObserver().dispatchOnPreDraw();
    }

    View authorPage() {
        activity.screen = "user_space";
        activity.content.removeAllViews();
        View author = new View(activity);
        activity.content.addView(author);
        return author;
    }

    static FeedItem item(String id) throws Exception {
        return FeedItem.from(new JSONObject().put("linkid", id).put("title", id)
                .put("text", longText("preview")).put("user", new JSONObject()
                        .put("userid", "author-7").put("username", "Author")));
    }

    static JSONObject body(String id, String text) throws Exception {
        JSONArray comments = new JSONArray();
        for (int i = 0; i < 12; i++) comments.put(new JSONObject().put("commentid", "c" + i)
                .put("text", longText("comment")).put("user", new JSONObject().put("username", "Reader")));
        return new JSONObject().put("result", new JSONObject().put("link", item(id).toJson()
                .put("text", longText(text)).put("auth_code", id + "-auth")).put("comments", comments));
    }

    static String longText(String prefix) {
        StringBuilder text = new StringBuilder(prefix);
        for (int i = 0; i < 25; i++) text.append("\n\nA paragraph long enough to keep both scroll offsets meaningful.");
        return text.toString();
    }

    static <T extends View> T find(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                T result = find(group.getChildAt(i), type);
                if (result != null) return result;
            }
        }
        return null;
    }

    static TextView text(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView result = text(group.getChildAt(i), text);
                if (result != null) return result;
            }
        }
        return null;
    }

    @Override public void close() {
        navigation.reset();
        page.close();
        activity.pageTransitions.finishNow();
        activity.handler.removeCallbacksAndMessages(null);
        activity.writeActions.close();
        api.close();
        activity.screenSnapshots.releaseAll();
        activity.fullScreenSnapshots.releaseAll();
        Motions.setLevel(motion);
    }

    static final class Request {
        final String path;
        final Map<String, String> params;
        final ApiClient.Callback callback;
        Request(String path, Map<String, String> params, ApiClient.Callback callback) {
            this.path = path;
            this.params = new LinkedHashMap<>(params);
            this.callback = callback;
        }
    }

    @Implements(value = ApiClient.class, isInAndroidSdk = false)
    public static class RecordingApi {
        final List<Request> gets = new ArrayList<>();
        final List<Request> posts = new ArrayList<>();
        final List<Request> games = new ArrayList<>();
        @Implementation protected void get(String path, Map<String, String> params, ApiClient.Callback callback) {
            if (EndpointProvider.emojis().equals(path)) {
                callback.onSuccess(new JSONObject());
                return;
            }
            gets.add(new Request(path, params, callback));
        }
        @Implementation protected void postForm(String path, Map<String, String> query,
                                                Map<String, String> body, ApiClient.Callback callback) {
            posts.add(new Request(path, body, callback));
        }
        @Implementation protected void getSigned(String path, Map<String, String> query,
                                                 HeyboxSigner.Algorithm algorithm,
                                                 ApiClient.RequestProfile profile, ApiClient.Callback callback) {
            games.add(new Request(path, query, callback));
        }
    }
}
