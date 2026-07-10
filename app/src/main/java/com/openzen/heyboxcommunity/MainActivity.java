package com.openzen.heyboxcommunity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.openzen.heyboxcommunity.AnnouncementChecker;
import com.openzen.heyboxcommunity.ApiClient;
import com.openzen.heyboxcommunity.HeyboxSigner;
import com.openzen.heyboxcommunity.RichContent;
import com.openzen.heyboxcommunity.SignInManager;
import com.openzen.heyboxcommunity.UpdateChecker;
import com.openzen.heyboxcommunity.WriteTokenProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

/* JADX INFO: loaded from: MainActivity.class */
public final class MainActivity extends Activity {
    private static final int REPLY_PREVIEW_COUNT = 2;
    private static final int REPLY_PAGE_SIZE = 5;
    private static final int NAV_BAR_HEIGHT_DP = 30;
    private static final int NAV_ICON_SIZE_DP = 20;
    private static final String TRANSITION_OVERLAY_TAG = "shell_transition_overlay";
    private static final String WELCOME_ANNOUNCEMENT_ID = "welcome-heybox-lite-1.77";
    private static final int MAX_WRITE_FALLBACK_ATTEMPTS = 2;
    private static final long WRITE_FALLBACK_DELAY_MS = 1300L;
    private static final long OFFLINE_MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L;
    private static final String[] THEME_NAMES = {"默认蓝", "红色", "粉色", "紫色", "绿色", "青色", "橙色", "黄色", "灰色", "深蓝", "黑金", "薄荷绿"};
    private static final int[][] THEME_COLORS = {new int[]{-14386760, -9193242}, new int[]{-3982790, -1083529}, new int[]{-2597743, -1006399}, new int[]{-9022795, -4744481}, new int[]{-14185897, -9320552}, new int[]{-15299695, -9713717}, new int[]{-2921692, -1007516}, new int[]{-3958250, -995480}, new int[]{-7894890, -5327686}, new int[]{-15253642, -10646588}, new int[]{-15263977, -3102658}, new int[]{-13530253, -7808833}};
    private static final String TITLE_FAVORITES = "我的收藏";
    private static final String MSG_OFFLINE_CACHE = "已显示离线缓存";
    private static final String MSG_EMPTY_CONTENT = "这里暂时没有内容";
    private static final String MSG_FAVORITES_UNAVAILABLE = "我的收藏暂不可用\n小黑盒接口未返回可访问的收藏夹，这通常是账号权限或当前网页接口限制。\n已保留现有缓存，可稍后再试。";
    private int BG;
    private int PANEL;
    private int TEXT;
    private int MUTED;
    private int PRIMARY;
    private int SECONDARY;
    private ThemeTokens themeTokens;
    private SessionStore session;
    private ApiClient api;
    private WriteTokenProvider writeTokenProvider;
    private SignInManager signInManager;
    private LinearLayout shellRoot;
    private LinearLayout shellBar;
    private FrameLayout content;
    private LinearLayout bottom;
    private boolean bottomVisible;
    private int bottomNavAnimSerial;
    private boolean bottomNavShowPending;
    private TextView title;
    private TextView leading;
    private TextView action;
    private FeedAdapter feedAdapter;
    private ListView feedListView;
    private ListView cachedFeedListView;
    private View cachedFeedContainer;
    private View cachedProfileContainer;
    private boolean cachedProfileLoggedIn;
    private TextView feedFooter;
    private ScrollView detailScroll;
    private ScrollView detailCommentScroll;
    private DetailPager detailPager;
    private boolean shellAnimating;
    private LocalCache localCache;
    private String qrKey;
    private boolean pollingQr;
    private boolean feedLoadingMore;
    private boolean feedRefreshing;
    private boolean feedNoMore;
    private boolean feedLoadMoreFailed;
    private boolean feedColdLaunchPending = true;
    private boolean suppressNextFeedCacheFallback;
    private int feedOffset;
    private int feedRequestSerial;
    private int feedResetSerial;
    private int feedFirstVisible;
    private int feedFirstTop;
    private FeedItem currentDetailItem;
    private FeedItem userSpaceReturnItem;
    private String userSpaceReturnScreen = "feed";
    private String pendingDetailReturn = "";
    private int detailRequestToken;
    private long lastManualSignClickAt;
    private long lastWriteSubmitAt;
    private long writeBlockedUntilAt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable qrPollTask = new Runnable() { // from class: com.openzen.heyboxcommunity.MainActivity.1
        @Override // java.lang.Runnable
        public void run() {
            MainActivity.this.pollQr();
        }
    };
    private final List<FeedItem> feed = new ArrayList();
    private String cachedProfileUserId = "";
    private final Map<View, Integer> searchBarHeights = new HashMap();
    private final Map<View, Boolean> searchBarStates = new HashMap();
    private boolean pendingBackTransition;
    private String lastSearchKeyword = "";
    private final List<FeedItem> searchResultItems = new ArrayList();
    private int searchOffset;
    private boolean searchEndReached;
    private boolean searchLoadingMore;
    private int searchSession;
    private ListView searchListView;
    private int searchListPosition;
    private int searchListTopOffset;
    private int searchStaleRounds;
    private final Map<String, LikeState> linkLikeOverrides = new HashMap();
    private final Map<String, Bitmap> screenSnapshots = new HashMap();
    private final Map<String, Bitmap> fullScreenSnapshots = new HashMap();
    private String screen = "feed";
    private String detailReturn = "feed";
    private View detailReturnView;
    private String detailReturnTitle = "";
    private String currentLinkId = "";
    private String currentLinkHsrc = "";
    private String currentAuthCode = "";
    private String lastDetailDiagnostics = "";
    private JSONObject currentDetailBody;

    /* JADX INFO: loaded from: MainActivity$IntListener.class */
    private interface IntListener {
        void onChanged(int i);
    }

    /* JADX INFO: loaded from: MainActivity$SavedListFallback.class */
    private interface SavedListFallback {
        boolean onFallback(String str);
    }

    /* JADX INFO: loaded from: MainActivity$ToggleListener.class */
    private interface ToggleListener {
        void onChanged(boolean z);
    }

    /* JADX INFO: loaded from: MainActivity$WriteRequest.class */
    private interface WriteRequest {
        void start(ApiClient.Callback callback);
    }

    /* JADX INFO: loaded from: MainActivity$WriteStep.class */
    private static class WriteStep {
        final String name;
        final WriteRequest request;

        WriteStep(String name, WriteRequest request) {
            this.name = name;
            this.request = request;
        }
    }

    /* JADX INFO: loaded from: MainActivity$LikeState.class */
    private static final class LikeState {
        final boolean liked;
        final int likes;

        LikeState(boolean liked, int likes) {
            this.liked = liked;
            this.likes = Math.max(0, likes);
        }
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        CrashReporter.install(this);
        NativeLibraryLoader.init(this);
        if (!AppIntegrityCheck.isTrusted(this)) {
            TextView blocked = new TextView(this);
            blocked.setText("应用签名校验失败，请安装官方构建版本。");
            blocked.setTextColor(-1);
            blocked.setTextSize(16.0f);
            blocked.setGravity(17);
            blocked.setPadding(32, 32, 32, 32);
            blocked.setBackgroundColor(Color.rgb(14, 15, 16));
            setContentView(blocked);
            return;
        }
        this.session = new SessionStore(this);
        this.localCache = new LocalCache(this);
        ImageLoader.init(this);
        if (this.session.autoOfflineCleanup()) {
            pruneOfflineCache(null);
        }
        applyPalette();
        Compat.colorSystemBars(getWindow(), this.BG);
        getWindow().getDecorView().setSystemUiVisibility(Compat.fullscreenFlags());
        this.api = new ApiClient(this.session, message -> {
            if (this.localCache != null) {
                this.localCache.log(message);
            }
        });
        Motions.setLevel(this.session.motionLevel());
        this.writeTokenProvider = new WriteTokenProvider(this, this.session, this.api);
        this.signInManager = new SignInManager(this.session, this.api, this.writeTokenProvider, message2 -> {
            if (this.localCache != null) {
                this.localCache.log(message2);
            }
        });
        buildShell();
        if (this.session.isLoggedIn()) {
            EmojiStore.load(this.api, () -> {
                if (!"feed".equals(this.screen) || this.feedAdapter == null) {
                    return;
                }
                this.feedAdapter.notifyDataSetChanged();
            });
        }
        showFeed();
        PresenceReporter.ping(this.session);
        if (this.session.autoUpdateCheck()) {
            this.handler.postDelayed(this::checkUpdateOnLaunch, 650L);
        }
        this.handler.postDelayed(this::checkAnnouncementOnLaunch, 950L);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (event != null && event.getActionMasked() == MotionEvent.ACTION_SCROLL) {
            float axis = event.getAxisValue(MotionEvent.AXIS_SCROLL);
            if (axis == 0.0f) axis = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (axis != 0.0f && scrollWithCrown(-Math.round(axis * dp(44)))) return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }

    private boolean scrollWithCrown(int distance) {
        if (distance == 0) return false;
        View target;
        if ("detail".equals(this.screen)) {
            target = this.detailPager != null && this.detailPager.showingComments()
                    ? this.detailCommentScroll : this.detailScroll;
        } else if ("feed".equals(this.screen)) {
            target = this.feedListView;
        } else if ("search".equals(this.screen)) {
            target = this.searchListView;
        } else {
            target = findScrollableView(this.content, distance > 0 ? 1 : -1);
        }
        if (target instanceof ScrollView) {
            ((ScrollView) target).smoothScrollBy(0, distance);
            return true;
        }
        if (target instanceof AbsListView) {
            ((AbsListView) target).smoothScrollBy(distance, 90);
            return true;
        }
        return false;
    }

    private View findScrollableView(View view, int direction) {
        if (view == null || view.getVisibility() != View.VISIBLE) return null;
        if ((view instanceof ScrollView || view instanceof AbsListView)
                && (view.canScrollVertically(direction) || view.canScrollVertically(-direction))) {
            return view;
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            View target = findScrollableView(group.getChildAt(i), direction);
            if (target != null) return target;
        }
        return null;
    }

    private void autoSignInOnLaunch() {
        if (isFinishing() || this.signInManager == null || !this.session.isLoggedIn()) {
            return;
        }
        this.signInManager.autoSignInIfNeeded(result -> {
            if (!isFinishing() && "profile".equals(this.screen)) {
                showProfile();
            }
        });
    }

    private void checkUpdateOnLaunch() {
        UpdateChecker.check(appVersion(), new UpdateChecker.Callback() { // from class: com.openzen.heyboxcommunity.MainActivity.2
            @Override // com.openzen.heyboxcommunity.UpdateChecker.Callback
            public void onResult(UpdateChecker.Result result) {
                if (!result.updateAvailable || MainActivity.this.isFinishing()) {
                    return;
                }
                if (result.title != null || result.notes != null) {
                    MainActivity.this.showUpdateDialog(result);
                } else {
                    String target = result.downloadUrl.isEmpty() ? result.releaseUrl : result.downloadUrl;
                    MainActivity.this.showLiteDialog("发现新版本 " + result.version, "heybox Lite 有新版本可用，是否前往下载", "下载", () -> {
                        MainActivity.this.openUpdateUrl(target);
                    }, "稍后", null, null, null);
                }
            }

            @Override // com.openzen.heyboxcommunity.UpdateChecker.Callback
            public void onError(String message) {
            }
        });
    }

    private void checkAnnouncementOnLaunch() {
        AnnouncementChecker.Item welcome = welcomeAnnouncement();
        if (shouldShowWelcomeAnnouncement(welcome)) {
            markAnnouncementSeen(welcome);
            showAnnouncementDialog(welcome);
        } else {
            AnnouncementChecker.load(new AnnouncementChecker.Callback() { // from class: com.openzen.heyboxcommunity.MainActivity.3
                @Override // com.openzen.heyboxcommunity.AnnouncementChecker.Callback
                public void onResult(List<AnnouncementChecker.Item> items) {
                    AnnouncementChecker.Item item;
                    if (MainActivity.this.isFinishing() || (item = MainActivity.this.firstUnseenAnnouncement(items)) == null) {
                        return;
                    }
                    MainActivity.this.showAnnouncementDialog(item);
                }

                @Override // com.openzen.heyboxcommunity.AnnouncementChecker.Callback
                public void onError(String message) {
                }
            });
        }
    }

    private boolean shouldShowWelcomeAnnouncement(AnnouncementChecker.Item item) {
        if (item == null || TextUtils.isEmpty(item.id) || this.session == null || this.session.isAnnouncementSeen(item.id)) {
            return false;
        }
        String previous = this.session.lastAnnouncementId();
        if (!TextUtils.isEmpty(previous) && !item.id.equals(previous)) {
            this.session.markAnnouncementSeen(item.id);
            return false;
        }
        return true;
    }

    private List<AnnouncementChecker.Item> withWelcomeAnnouncement(List<AnnouncementChecker.Item> items) {
        List<AnnouncementChecker.Item> result = new ArrayList<>();
        result.add(welcomeAnnouncement());
        if (items != null) {
            for (AnnouncementChecker.Item item : items) {
                if (item != null && !WELCOME_ANNOUNCEMENT_ID.equals(item.id)) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    private AnnouncementChecker.Item welcomeAnnouncement() {
        return new AnnouncementChecker.Item(WELCOME_ANNOUNCEMENT_ID, "欢迎使用 heybox Lite", "欢迎使用 heybox Lite。这里会放版本公告和重要提醒。\n\n遇到 bug 或有建议，可以加入交流群：781941517。\n\n本项目仅用于学习、研究与个人使用，请在遵守平台规则的前提下使用", "normal", appVersion(), true);
    }

    private AnnouncementChecker.Item firstUnseenAnnouncement(List<AnnouncementChecker.Item> items) {
        if (items == null) {
            return null;
        }
        for (AnnouncementChecker.Item item : items) {
            if (item != null && item.enabled && (!item.title.isEmpty() || !item.content.isEmpty())) {
                if (item.id.isEmpty() || this.session == null || !this.session.isAnnouncementSeen(item.id)) {
                    return item;
                }
            }
        }
        return null;
    }

    private void showAnnouncementDialog(AnnouncementChecker.Item item) {
        if (item == null || isFinishing()) {
            return;
        }
        String titleValue = TextUtils.isEmpty(item.title) ? "公告" : item.title;
        String message = item.content == null ? "" : item.content;
        Runnable positive = () -> {
            markAnnouncementSeen(item);
        };
        String neutralText = null;
        Runnable neutral = null;
        if (WELCOME_ANNOUNCEMENT_ID.equals(item.id)) {
            neutralText = "群二维码";
            neutral = () -> {
                markAnnouncementSeen(item);
                showFeedbackGroupQr();
            };
        }
        showLiteDialog(titleValue, message, "知道了", positive, null, null, neutralText, neutral);
    }

    private void markAnnouncementSeen(AnnouncementChecker.Item item) {
        if (this.session != null && item != null && !TextUtils.isEmpty(item.id)) {
            this.session.markAnnouncementSeen(item.id);
        }
    }

    private void showUpdateDialog(UpdateChecker.Result result) {
        String strLimitUpdateNotes;
        String releaseTitle = TextUtils.isEmpty(result.title) ? "" : result.title.trim();
        if (TextUtils.isEmpty(result.notes)) {
            strLimitUpdateNotes = "暂无更新内容说明";
        } else {
            strLimitUpdateNotes = limitUpdateNotes(result.notes.trim());
        }
        String notes = strLimitUpdateNotes;
        StringBuilder message = new StringBuilder();
        message.append("当前版本：").append(appVersion()).append('\n');
        message.append("最新版本：").append(result.version);
        if (!TextUtils.isEmpty(releaseTitle)) {
            message.append('\n').append("发布标题：").append(releaseTitle);
        }
        message.append("\n\n更新内容：\n").append(notes);
        String target = TextUtils.isEmpty(result.downloadUrl) ? result.releaseUrl : result.downloadUrl;
        showLiteDialog("发现新版本 " + result.version, message.toString(), "下载", () -> {
            openUpdateUrl(target);
        }, "稍后", null, null, null);
    }

    private void showLiteDialog(String titleValue, String message, String positiveText, Runnable positiveAction, String negativeText, Runnable negativeAction, String neutralText, Runnable neutralAction) {
        if (isFinishing()) {
            return;
        }
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(dp(16), dp(14), dp(16), dp(14));
        int dialogBg = this.session.darkMode() ? Color.rgb(34, 34, 34) : Color.rgb(248, 248, 248);
        int border = this.session.darkMode() ? Color.rgb(72, 72, 72) : Color.rgb(215, 215, 215);
        Compat.setBackground(linearLayout, roundStroke(dialogBg, 14, border, 1));
        TextView titleView = text(titleValue, 16.0f, this.TEXT);
        titleView.setTypeface(appRegularTypeface(), 1);
        titleView.setLineSpacing(0.0f, 1.08f);
        linearLayout.addView(titleView, new LinearLayout.LayoutParams(-1, -2));
        View accent = new View(this);
        Compat.setBackground(accent, round(this.PRIMARY, 1));
        LinearLayout.LayoutParams accentParams = new LinearLayout.LayoutParams(dp(34), dp(REPLY_PREVIEW_COUNT));
        accentParams.topMargin = dp(7);
        linearLayout.addView(accent, accentParams);
        MaxHeightScrollView scroll = new MaxHeightScrollView(this);
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(1);
        TextView body = text(message, 13.0f, this.TEXT);
        body.setLineSpacing(dp(REPLY_PREVIEW_COUNT), 1.08f);
        body.setPadding(0, dp(12), 0, dp(REPLY_PREVIEW_COUNT));
        int maxBodyHeight = Math.max(dp(96), Math.min(getResources().getDisplayMetrics().heightPixels - dp(230), dp(330)));
        scroll.setMaxHeight(maxBodyHeight);
        scroll.addView(body, new FrameLayout.LayoutParams(-1, -2));
        linearLayout.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog[] holder = new AlertDialog[1];
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(1);
        actions.setPadding(0, dp(8), 0, 0);
        if (!TextUtils.isEmpty(positiveText)) {
            actions.addView(dialogAction(positiveText, true, holder, positiveAction), new LinearLayout.LayoutParams(-1, dp(38)));
        }
        if (!TextUtils.isEmpty(neutralText)) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(36));
            params.topMargin = dp(7);
            actions.addView(dialogAction(neutralText, false, holder, neutralAction), params);
        }
        if (!TextUtils.isEmpty(negativeText)) {
            LinearLayout.LayoutParams params2 = new LinearLayout.LayoutParams(-1, dp(36));
            params2.topMargin = dp(7);
            actions.addView(dialogAction(negativeText, false, holder, negativeAction), params2);
        }
        if (actions.getChildCount() > 0) {
            linearLayout.addView(actions);
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setView(linearLayout).create();
        holder[0] = dialog;
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0));
            dialog.getWindow().setDimAmount(this.session.darkMode() ? 0.46f : 0.32f);
            int width = getResources().getDisplayMetrics().widthPixels;
            dialog.getWindow().setLayout(Math.max(dp(220), Math.min(width - dp(28), dp(360))), -2);
        }
        Motions.dialogIn(linearLayout);
    }

    /* JADX INFO: loaded from: MainActivity$MaxHeightScrollView.class */
    private static final class MaxHeightScrollView extends ScrollView {
        private int maxHeight;

        MaxHeightScrollView(Context context) {
            super(context);
        }

        void setMaxHeight(int maxHeight) {
            this.maxHeight = Math.max(0, maxHeight);
            requestLayout();
        }

        @Override // android.widget.ScrollView, android.widget.FrameLayout, android.view.View
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (this.maxHeight > 0) {
                heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(this.maxHeight, Integer.MIN_VALUE);
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    private TextView dialogAction(String label, boolean primary, AlertDialog[] holder, Runnable action) {
        int iRgb;
        int fill = primary ? this.PRIMARY : 0;
        if (primary) {
            iRgb = this.PRIMARY;
        } else {
            iRgb = this.session.darkMode() ? Color.rgb(82, 82, 82) : Color.rgb(198, 198, 198);
        }
        int stroke = iRgb;
        int color = primary ? contrast(this.PRIMARY) : this.TEXT;
        TextView view = text(label, 13.0f, color);
        view.setTypeface(appRegularTypeface(), primary ? 1 : 0);
        view.setGravity(17);
        view.setPadding(dp(10), 0, dp(10), 0);
        Compat.setBackground(view, roundStroke(fill, 18, stroke, 1));
        view.setOnClickListener(v -> {
            runWithPressFeedback(view, () -> {
                AlertDialog dialog = holder == null ? null : holder[0];
                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                }
                if (action != null) {
                    action.run();
                }
            });
        });
        return view;
    }

    private String limitUpdateNotes(String notes) {
        return notes.length() <= 1800 ? notes : notes.substring(0, 1800) + "\n\n内容较长，完整更新日志请打开更新页面查看。";
    }

    private void buildShell() {
        LinearLayout linearLayoutVertical = vertical(this.BG);
        this.shellRoot = linearLayoutVertical;
        applyScreenInsets(linearLayoutVertical);
        LinearLayout bar = new LinearLayout(this);
        this.shellBar = bar;
        bar.setGravity(16);
        int sidePadding = this.session.roundScreen() ? dp(9) : dp(4);
        bar.setPadding(sidePadding, 0, Math.max(sidePadding, dp(6)), 0);
        bar.setBackgroundColor(this.BG);
        bar.setVisibility(8);
        linearLayoutVertical.addView(bar, new LinearLayout.LayoutParams(-1, 0));
        this.leading = icon("");
        setIcon(this.leading, R.drawable.ic_arrow_back, this.TEXT, NAV_ICON_SIZE_DP);
        this.leading.setVisibility(4);
        bar.addView(this.leading, new LinearLayout.LayoutParams(dp(36), dp(38)));
        this.title = text("heybox Lite", 15.0f, this.TEXT);
        this.title.setTypeface(appRegularTypeface(), 1);
        this.title.setGravity(16);
        this.title.setOnClickListener(view -> {
            if (canHeaderBack()) {
                onBackPressed();
            }
        });
        bar.addView(this.title, new LinearLayout.LayoutParams(0, dp(38), 1.0f));
        this.action = icon("");
        setIcon(this.action, R.drawable.ic_refresh, this.TEXT, 19);
        bar.addView(this.action, new LinearLayout.LayoutParams(dp(38), dp(38)));
        FrameLayout body = new FrameLayout(this);
        linearLayoutVertical.addView(body, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        this.content = new BackSwipeFrameLayout(this);
        body.addView(this.content, match());
        this.bottom = new LinearLayout(this);
        this.bottom.setGravity(17);
        this.bottom.setPadding(dp(10), 0, dp(10), 0);
        if (Build.VERSION.SDK_INT >= 21) this.bottom.setElevation(dp(10));
        int navFill = this.session.darkMode() ? Color.argb(210, 28, 29, 31) : Color.argb(218, 255, 255, 255);
        Compat.setBackground(this.bottom, round(navFill, 22));
        this.bottom.setVisibility(8);
        this.bottom.setAlpha(0.0f);
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(dp(156), dp(42), 81);
        bottomParams.setMargins(0, 0, 0, dp(7));
        body.addView(this.bottom, bottomParams);
        addNav("社区", "feed", R.drawable.ic_home, this::onFeedNavClick);
        addNav("我的", "profile", R.drawable.ic_person, () -> {
            showTopLevel(1);
        });
        setContentView(linearLayoutVertical);
    }

    private void applyScreenInsets(View view) {
        if (view == null || this.session == null) {
            return;
        }
        int[] insets = screenInsets();
        view.setPadding(insets[0], insets[1], insets[REPLY_PREVIEW_COUNT], insets[3]);
    }

    private int[] screenInsets() {
        DisplayMetrics metrics = new DisplayMetrics();
        try {
            if (Build.VERSION.SDK_INT >= 17) {
                getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
            } else {
                getWindowManager().getDefaultDisplay().getMetrics(metrics);
            }
        } catch (Exception e) {
            metrics = getResources().getDisplayMetrics();
        }
        int horizontal = this.session.screenPaddingHPercent();
        int vertical = this.session.screenPaddingVPercent();
        if (this.session.roundScreen()) {
            horizontal = Math.max(horizontal, REPLY_PAGE_SIZE);
            vertical = Math.max(vertical, 3);
        }
        int horizontal2 = Math.max(0, Math.min(NAV_BAR_HEIGHT_DP, horizontal));
        int vertical2 = Math.max(0, Math.min(NAV_BAR_HEIGHT_DP, vertical));
        int leftRight = (metrics.widthPixels * horizontal2) / 100;
        int top = (metrics.heightPixels * vertical2) / 100;
        int bottom = top;
        if (this.session.roundScreen()) {
            bottom += Math.round(metrics.heightPixels * 0.03f);
        }
        return new int[]{leftRight, top, leftRight, bottom};
    }

    private void updateClock(TextView clock) {
        clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        this.handler.postDelayed(() -> {
            if (!isFinishing()) {
                updateClock(clock);
            }
        }, 30000L);
    }

    private void applyPalette() {
        this.PRIMARY = parseThemeColor(this.session.primaryColor(), this.session.darkMode() ? -1 : Color.rgb(NAV_ICON_SIZE_DP, 21, 23));
        this.SECONDARY = parseThemeColor(this.session.secondaryColor(), this.session.darkMode() ? Color.rgb(196, 198, 201) : Color.rgb(87, 91, 96));
        this.themeTokens = ThemeTokens.of(this.session.darkMode(), this.PRIMARY, this.SECONDARY);
        this.BG = this.themeTokens.background;
        this.PANEL = this.themeTokens.panel;
        this.TEXT = this.themeTokens.text;
        this.MUTED = this.themeTokens.muted;
    }

    private int parseThemeColor(String value, int fallback) {
        try {
            return value.isEmpty() ? fallback : Color.parseColor(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private void addNav(String label, String key, int drawable, Runnable click) {
        ImageView item = new ImageView(this);
        item.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        item.setPadding(0, dp(4), 0, dp(4));
        item.setAdjustViewBounds(false);
        item.setImageDrawable(navIcon(drawable, this.MUTED));
        item.setColorFilter(this.MUTED);
        item.setContentDescription(label);
        item.setTag(key);
        item.setOnClickListener(view -> {
            runWithPressFeedback(item, click);
        });
        this.bottom.addView(item, new LinearLayout.LayoutParams(0, dp(42), 1.0f));
    }

    private void setBottomNavVisible(boolean visible) {
        setBottomNavVisible(visible, true);
    }

    private void setBottomNavVisible(boolean visible, boolean animate) {
        if (this.bottom == null) {
            return;
        }
        if (visible) {
            if (this.bottomNavShowPending) {
                return;
            }
            if (this.bottomVisible && this.bottom.getVisibility() == 0
                    && this.bottom.getAlpha() > 0.98f
                    && Math.abs(this.bottom.getTranslationY()) < 1.0f) {
                return;
            }
        }
        int serial = ++this.bottomNavAnimSerial;
        boolean wasHidden = this.bottom.getVisibility() != 0;
        this.bottom.animate().cancel();
        if (visible) {
            this.bottomVisible = true;
            if (this.shellAnimating && animate) {
                this.bottomNavShowPending = false;
                this.bottom.setVisibility(0);
                this.bottom.setAlpha(1.0f);
                this.bottom.setTranslationY(0.0f);
                return;
            }
            this.bottomNavShowPending = false;
            this.bottom.setVisibility(0);
            if (animate) {
                if (wasHidden || this.bottom.getAlpha() <= 0.0f) {
                    this.bottom.setAlpha(0.0f);
                    this.bottom.setTranslationY(dp(22));
                }
                this.bottom.animate()
                        .alpha(1.0f)
                        .translationY(0.0f)
                        .setDuration(170L)
                        .setInterpolator(MotionSpec.EASE_OUT)
                        .start();
            } else {
                this.bottom.setAlpha(1.0f);
                this.bottom.setTranslationY(0.0f);
            }
            return;
        }
        this.bottomNavShowPending = false;
        if (!this.bottomVisible && this.bottom.getVisibility() != 0) {
            return;
        }
        this.bottomVisible = false;
        if (animate) {
            this.bottom.animate()
                    .alpha(0.0f)
                    .translationY(dp(22))
                    .setDuration(120L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            this.bottom.postDelayed(() -> {
                if (serial == MainActivity.this.bottomNavAnimSerial && !MainActivity.this.bottomVisible && MainActivity.this.bottom != null) {
                    MainActivity.this.bottom.setVisibility(8);
                    MainActivity.this.bottom.setTranslationY(0.0f);
                }
            }, 140L);
        } else {
            this.bottom.setAlpha(0.0f);
            this.bottom.setTranslationY(0.0f);
            this.bottom.setVisibility(8);
        }
    }

    private void addBottomNavSafeSpace(LinearLayout page) {
        if (page == null) {
            return;
        }
        View spacer = new View(this);
        page.addView(spacer, new LinearLayout.LayoutParams(-1, dp(76)));
    }

    private Drawable navIcon(int drawable, int color) {
        Drawable icon = Compat.tintedDrawable(this, drawable, color);
        if (icon != null) {
            icon.setBounds(0, 0, dp(NAV_ICON_SIZE_DP), dp(NAV_ICON_SIZE_DP));
        }
        return icon;
    }

    private boolean canHeaderBack() {
        return "detail".equals(this.screen) || "user_space".equals(this.screen) || "search".equals(this.screen) || "saved".equals(this.screen) || "announcement_board".equals(this.screen) || "settings_home".equals(this.screen) || "display_preview".equals(this.screen) || "display_settings".equals(this.screen) || "startup_settings".equals(this.screen) || "app_settings".equals(this.screen) || "about".equals(this.screen);
    }

    private int topLevelIndex() {
        if ("feed".equals(this.screen)) {
            return 0;
        }
        return "profile".equals(this.screen) ? 1 : -1;
    }

    private void showTopLevel(int index) {
        if ("feed".equals(this.screen) && index != 0) {
            saveFeedScroll();
        }
        if (index != 0) {
            if (index == 1) {
                showProfile();
                return;
            }
            return;
        }
        showFeed();
    }

    private void onFeedNavClick() {
        if (!"feed".equals(this.screen)) {
            showTopLevel(0);
            return;
        }
        if (this.feedListView != null) {
            this.feedFirstVisible = 0;
            this.feedFirstTop = 0;
            this.feedListView.smoothScrollToPosition(0);
            this.feedListView.postDelayed(() -> {
                if (this.feedListView != null) {
                    this.feedListView.setSelection(0);
                }
            }, 160L);
        }
        if (this.feedListView instanceof PullRefreshListView) {
            ((PullRefreshListView) this.feedListView).setRefreshing(true);
        }
        if (!loadFeed(true) && (this.feedListView instanceof PullRefreshListView)) {
            ((PullRefreshListView) this.feedListView).setRefreshing(false);
        }
    }

    private String screenKeyForTopLevel(int index) {
        return index == 0 ? "feed" : index == 1 ? "profile" : "";
    }

    private String backTargetScreenKey() {
        return "detail".equals(this.screen) ? this.detailReturn : "user_space".equals(this.screen) ? "detail" : "search".equals(this.screen) ? "feed" : "saved".equals(this.screen) ? "profile" : "announcement_board".equals(this.screen) ? "about" : "display_preview".equals(this.screen) ? "display_settings" : ("display_settings".equals(this.screen) || "startup_settings".equals(this.screen) || "app_settings".equals(this.screen) || "about".equals(this.screen)) ? "settings_home" : "settings_home".equals(this.screen) ? "profile" : "feed";
    }

    private boolean canDetailSwipeBack() {
        return "detail".equals(this.screen);
    }

    private void captureShellSnapshot(String key, View view) {
        captureSnapshot(this.screenSnapshots, 8, key, view);
    }

    private void captureFullScreenSnapshot(String key) {
        captureSnapshot(this.fullScreenSnapshots, 4, key, this.shellRoot);
    }

    private void captureSnapshot(Map<String, Bitmap> target, int maxCount, String key, View view) {
        if (key == null || key.isEmpty() || view == null) {
            return;
        }
        int width = view.getWidth();
        int height = view.getHeight();
        if (width <= 1 || height <= 1) {
            return;
        }
        try {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(this.BG);
            view.draw(canvas);
            Bitmap old = target.put(key, bitmap);
            if (old != null && old != bitmap && !old.isRecycled()) {
                old.recycle();
            }
            trimSnapshots(target, maxCount);
        } catch (Throwable th) {
        }
    }

    private Bitmap screenSnapshot(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        return this.screenSnapshots.get(key);
    }

    private Bitmap fullScreenSnapshot(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        return this.fullScreenSnapshots.get(key);
    }

    private void trimScreenSnapshots() {
        trimSnapshots(this.screenSnapshots, 8);
    }

    private void trimSnapshots(Map<String, Bitmap> target, int maxCount) {
        if (target.size() <= maxCount) {
            return;
        }
        Iterator<String> iterator = target.keySet().iterator();
        if (iterator.hasNext()) {
            String key = iterator.next();
            Bitmap bitmap = target.get(key);
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            iterator.remove();
        }
    }

    private ImageView installFullScreenTransitionOverlay(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return null;
        }
        ViewGroup root = getWindow() == null ? null : (ViewGroup) getWindow().getDecorView().findViewById(android.R.id.content);
        if (root == null) {
            return null;
        }
        ImageView overlay = new ImageView(this);
        overlay.setTag(TRANSITION_OVERLAY_TAG);
        overlay.setBackgroundColor(this.BG);
        overlay.setScaleType(ImageView.ScaleType.FIT_XY);
        overlay.setImageBitmap(bitmap);
        overlay.setAlpha(1.0f);
        root.addView(overlay, new ViewGroup.LayoutParams(-1, -1));
        overlay.bringToFront();
        return overlay;
    }

    private void fadeFullScreenTransitionOverlay(ImageView overlay) {
        if (overlay == null) {
            return;
        }
        overlay.post(() -> {
            overlay.postDelayed(() -> {
                overlay.animate().alpha(0.0f).setStartDelay(40L).setDuration(210L).setInterpolator(new DecelerateInterpolator()).setListener(new AnimatorListenerAdapter() { // from class: com.openzen.heyboxcommunity.MainActivity.4
                    @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
                    public void onAnimationEnd(Animator animation) {
                        if (overlay.getParent() instanceof ViewGroup) {
                            ((ViewGroup) overlay.getParent()).removeView(overlay);
                        }
                    }
                });
            }, 32L);
        });
    }

    private void removeFullScreenTransitionOverlayAfterLayout(ImageView overlay) {
        if (overlay == null) {
            return;
        }
        overlay.post(() -> {
            overlay.postDelayed(() -> {
                if (overlay.getParent() instanceof ViewGroup) {
                    ((ViewGroup) overlay.getParent()).removeView(overlay);
                }
            }, 48L);
        });
    }

    private void showShellTransitionOverlay(Bitmap bitmap) {
    }

    private void activate(String key) {
        stopQrPolling();
        this.screen = key;
        if (this.shellBar != null) {
            this.shellBar.setVisibility(8);
        }
        setBottomNavVisible(true);
        this.leading.setVisibility(4);
        int activeColor = this.TEXT;
        int inactiveColor = this.MUTED;
        for (int i = 0; i < this.bottom.getChildCount(); i++) {
            View item = this.bottom.getChildAt(i);
            boolean active = key.equals(item.getTag());
            item.setAlpha(active ? 1.0f : 0.62f);
            if (item instanceof ImageView) {
                ((ImageView) item).setColorFilter(active ? activeColor : inactiveColor);
            } else if (item instanceof TextView) {
                TextView textItem = (TextView) item;
                textItem.setTextColor(active ? activeColor : inactiveColor);
                textItem.setTypeface(appRegularTypeface(), active ? 1 : 0);
            }
        }
    }

    private void animateShellChange(Runnable change, int direction) {
        if (this.shellAnimating) {
            return;
        }
        View oldChild = (this.content == null || this.content.getChildCount() == 0) ? null : this.content.getChildAt(0);
        int width = this.content == null ? 0 : Math.max(1, this.content.getWidth());
        if (oldChild == null || width <= 1) {
            change.run();
            return;
        }
        this.shellAnimating = true;
        Bitmap oldBitmap = null;
        try {
            oldBitmap = Bitmap.createBitmap(oldChild.getWidth(), oldChild.getHeight(), Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(oldBitmap);
            canvas.drawColor(this.BG);
            oldChild.draw(canvas);
        } catch (Throwable th) {
        }
        change.run();
        final View newChild = this.content.getChildCount() == 0 ? null : this.content.getChildAt(0);
        if (newChild == null || oldBitmap == null || oldBitmap.isRecycled()) {
            if (newChild != null) {
                newChild.setTranslationX(0.0f);
                newChild.setAlpha(1.0f);
            }
            this.shellAnimating = false;
            return;
        }
        final ImageView oldOverlay = new ImageView(this);
        oldOverlay.setTag(TRANSITION_OVERLAY_TAG);
        oldOverlay.setBackgroundColor(this.BG);
        oldOverlay.setScaleType(ImageView.ScaleType.FIT_XY);
        oldOverlay.setImageBitmap(oldBitmap);
        this.content.addView(oldOverlay, match());
        oldOverlay.bringToFront();
        newChild.setTranslationX(direction * width);
        newChild.setAlpha(1.0f);
        ValueAnimator animator = ValueAnimator.ofFloat(0.0f, 1.0f);
        animator.setDuration(190L);
        animator.setInterpolator(MotionSpec.EASE_OUT);
        animator.addUpdateListener(value -> {
            float progress = ((Float) value.getAnimatedValue()).floatValue();
            oldOverlay.setTranslationX((-direction) * width * progress);
            newChild.setTranslationX(direction * width * (1.0f - progress));
        });
        animator.addListener(new AnimatorListenerAdapter() { // from class: com.openzen.heyboxcommunity.MainActivity.5
            @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
            public void onAnimationEnd(Animator animation) {
                newChild.setTranslationX(0.0f);
                if (oldOverlay.getParent() instanceof ViewGroup) {
                    ((ViewGroup) oldOverlay.getParent()).removeView(oldOverlay);
                }
                MainActivity.this.shellAnimating = false;
            }
        });
        animator.start();
    }

    private void animateShellView(final View view, float fromX, final float toX, float fromAlpha, final float toAlpha, int duration, final Runnable end) {
        if (view == null) {
            if (end != null) {
                end.run();
                return;
            }
            return;
        }
        view.setTranslationX(fromX);
        view.setAlpha(fromAlpha);
        ValueAnimator animator = ValueAnimator.ofFloat(0.0f, 1.0f);
        animator.setDuration(duration);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(value -> {
            float progress = ((Float) value.getAnimatedValue()).floatValue();
            view.setTranslationX(fromX + ((toX - fromX) * progress));
            view.setAlpha(fromAlpha + ((toAlpha - fromAlpha) * progress));
        });
        animator.addListener(new AnimatorListenerAdapter() { // from class: com.openzen.heyboxcommunity.MainActivity.6
            @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
            public void onAnimationEnd(Animator animation) {
                view.setTranslationX(toX);
                view.setAlpha(toAlpha);
                if (end != null) {
                    end.run();
                }
            }
        });
        animator.start();
    }

    private void navigateTopLevelSwipe(int direction) {
        int next;
        int index = topLevelIndex();
        if (index >= 0 && (next = index + direction) >= 0 && next <= 1) {
            animateShellChange(() -> {
                showTopLevel(next);
            }, direction);
        }
    }

    private void backWithShellAnimation() {
        if (!canHeaderBack() || "detail".equals(this.screen)) {
            return;
        }
        animateShellChange(this::onBackPressed, -1);
    }

    /* JADX INFO: loaded from: MainActivity$BackSwipeFrameLayout.class */
    private final class BackSwipeFrameLayout extends FrameLayout {
        private static final int MODE_NONE = 0;
        private static final int MODE_TOP_LEVEL = 1;
        private static final int MODE_BACK = 2;
        private final int touchSlop;
        private float startX;
        private float startY;
        private long startTime;
        private boolean tracking;
        private boolean dragging;
        private int gestureMode;
        private int gestureDirection;
        private View dragChild;
        private View previewChild;
        private String displayedScreenKey;
        private ValueAnimator swipeAnimator;

        BackSwipeFrameLayout(Context context) {
            super(context);
            this.gestureMode = 0;
            this.displayedScreenKey = "";
            this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        @Override // android.view.ViewGroup
        public void addView(View child, int index, ViewGroup.LayoutParams params) {
            super.addView(child, index, params);
            if (child != this.previewChild) {
                this.displayedScreenKey = MainActivity.this.screen;
            }
        }

        @Override // android.view.ViewGroup
        public void removeAllViews() {
            MainActivity.this.captureShellSnapshot(this.displayedScreenKey, currentShellChild());
            MainActivity.this.captureFullScreenSnapshot(this.displayedScreenKey);
            super.removeAllViews();
            this.previewChild = null;
        }

        @Override // android.view.ViewGroup
        public boolean onInterceptTouchEvent(MotionEvent event) {
            if (!canUseShellSwipe()) {
                return false;
            }
            switch (event.getActionMasked()) {
                case 0:
                    cancelSwipeAnimator();
                    this.startX = event.getX();
                    this.startY = event.getY();
                    this.startTime = event.getEventTime();
                    this.tracking = true;
                    this.dragging = false;
                    this.gestureMode = 0;
                    this.dragChild = null;
                    return false;
                case MODE_TOP_LEVEL /* 1 */:
                case 3:
                    this.tracking = false;
                    this.dragging = false;
                    return false;
                case MODE_BACK /* 2 */:
                    if (!this.tracking) {
                        return false;
                    }
                    float dx = event.getX() - this.startX;
                    float dy = event.getY() - this.startY;
                    if (startShellDragIfReady(dx, dy)) {
                        dragShellTo(dx);
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    }
                    return false;
                default:
                    return false;
            }
        }

        @Override // android.view.View
        public boolean onTouchEvent(MotionEvent event) {
            if (!this.dragging && !canUseShellSwipe()) {
                return false;
            }
            switch (event.getActionMasked()) {
                case 0:
                    cancelSwipeAnimator();
                    this.startX = event.getX();
                    this.startY = event.getY();
                    this.startTime = event.getEventTime();
                    this.tracking = true;
                    this.dragging = false;
                    this.gestureMode = 0;
                    this.dragChild = null;
                    break;
                case MODE_TOP_LEVEL /* 1 */:
                    if (this.dragging) {
                        finishShellSwipe(event);
                    } else {
                        performClick();
                    }
                    this.tracking = false;
                    this.dragging = false;
                    break;
                case MODE_BACK /* 2 */:
                    float dx = event.getX() - this.startX;
                    float dy = event.getY() - this.startY;
                    if (this.dragging || startShellDragIfReady(dx, dy)) {
                        dragShellTo(dx);
                    }
                    break;
                case 3:
                    if (this.dragging) {
                        settleShellDrag(0.0f, this::resetShellDrag);
                    }
                    this.tracking = false;
                    this.dragging = false;
                    break;
            }
            return true;
        }

        private boolean canUseShellSwipe() {
            return (MainActivity.this.shellAnimating || "detail".equals(MainActivity.this.screen) || "login".equals(MainActivity.this.screen)) ? false : true;
        }

        private boolean hasShellSwipeTarget(float dx) {
            int index = MainActivity.this.topLevelIndex();
            if (index < 0) {
                return MainActivity.this.session.shellBackSwipe() && MainActivity.this.canHeaderBack() && dx > 0.0f;
            }
            int next = index + (dx < 0.0f ? MODE_TOP_LEVEL : -1);
            return next >= 0 && next <= 1;
        }

        private boolean startShellDragIfReady(float dx, float dy) {
            if (!this.tracking) {
                return false;
            }
            if (Math.abs(dy) > this.touchSlop * MODE_BACK && Math.abs(dy) > Math.abs(dx)) {
                this.tracking = false;
                return false;
            }
            if (Math.abs(dx) <= this.touchSlop * MODE_BACK || Math.abs(dx) <= Math.abs(dy) * 1.18f || !hasShellSwipeTarget(dx)) {
                return false;
            }
            int index = MainActivity.this.topLevelIndex();
            this.gestureMode = index >= 0 ? MODE_TOP_LEVEL : MODE_BACK;
            int i = (index < 0 || dx >= 0.0f) ? -1 : MODE_TOP_LEVEL;
            this.gestureDirection = i;
            this.dragChild = currentShellChild();
            if (this.dragChild == null) {
                return false;
            }
            installShellPreview(targetScreenKeyForGesture());
            cancelSwipeAnimator();
            MainActivity.this.shellAnimating = true;
            this.dragging = true;
            return true;
        }

        private View currentShellChild() {
            for (int i = getChildCount() - MODE_TOP_LEVEL; i >= 0; i--) {
                View child = getChildAt(i);
                if (child != this.previewChild && !MainActivity.TRANSITION_OVERLAY_TAG.equals(child.getTag())) {
                    return child;
                }
            }
            return null;
        }

        private String targetScreenKeyForGesture() {
            if (this.gestureMode == MODE_TOP_LEVEL) {
                return MainActivity.this.screenKeyForTopLevel(MainActivity.this.topLevelIndex() + this.gestureDirection);
            }
            return MainActivity.this.backTargetScreenKey();
        }

        private void installShellPreview(String targetKey) {
            removeShellPreview();
            if (this.gestureMode == MODE_BACK && "settings_home".equals(targetKey)) {
                this.previewChild = MainActivity.this.buildSettingsHomeContent();
            } else {
                ImageView image = new ImageView(getContext());
                image.setBackgroundColor(MainActivity.this.themeTokens == null ? MainActivity.this.PANEL : MainActivity.this.themeTokens.panel);
                image.setScaleType(ImageView.ScaleType.FIT_XY);
                Bitmap bitmap = MainActivity.this.screenSnapshot(targetKey);
                if (bitmap != null && !bitmap.isRecycled()) {
                    image.setImageBitmap(bitmap);
                }
                this.previewChild = image;
            }
            this.previewChild.setTranslationX(this.gestureDirection * Math.max(MODE_TOP_LEVEL, getWidth()));
            super.addView(this.previewChild, 0, new FrameLayout.LayoutParams(-1, -1));
        }

        private void removeShellPreview() {
            if (this.previewChild != null) {
                super.removeView(this.previewChild);
                this.previewChild = null;
            }
        }

        private void dragShellTo(float dx) {
            if (this.dragChild == null) {
                return;
            }
            int width = Math.max(MODE_TOP_LEVEL, getWidth());
            float exitSign = -this.gestureDirection;
            float offset = exitSign < 0.0f ? Math.max(-width, Math.min(0.0f, dx)) : Math.max(0.0f, Math.min(width, dx));
            Math.min(1.0f, Math.abs(offset) / width);
            this.dragChild.setTranslationX(offset);
            this.dragChild.setAlpha(1.0f);
            if (this.previewChild != null) {
                this.previewChild.setTranslationX((this.gestureDirection * width) + offset);
                this.previewChild.setAlpha(1.0f);
            }
        }

        private void finishShellSwipe(MotionEvent event) {
            float dx = event.getX() - this.startX;
            float dy = event.getY() - this.startY;
            long duration = Math.max(1L, event.getEventTime() - this.startTime);
            float velocity = (dx * 1000.0f) / duration;
            float offset = this.dragChild == null ? 0.0f : this.dragChild.getTranslationX();
            boolean enoughDistance = Math.abs(offset) > Math.max((float) MainActivity.this.dp(52), ((float) getWidth()) * 0.24f);
            boolean enoughVelocity = ((float) (-this.gestureDirection)) * velocity > ((float) MainActivity.this.dp(300));
            if (Math.abs(dx) <= Math.abs(dy) * 1.1f || (!enoughDistance && !enoughVelocity)) {
                settleShellDrag(0.0f, this::resetShellDrag);
            } else {
                settleShellDrag((-this.gestureDirection) * Math.max(MODE_TOP_LEVEL, getWidth()), this::completeShellSwipe);
            }
        }

        private void settleShellDrag(float targetX, final Runnable end) {
            if (this.dragChild == null) {
                resetShellDrag();
                if (end != null) {
                    end.run();
                    return;
                }
                return;
            }
            cancelSwipeAnimator();
            float fromX = this.dragChild.getTranslationX();
            if (Math.abs(fromX - targetX) < MainActivity.this.dp(MODE_TOP_LEVEL)) {
                this.dragChild.setTranslationX(targetX);
                if (end != null) {
                    end.run();
                    return;
                }
                return;
            }
            int distance = Math.round(Math.abs(fromX - targetX));
            int duration = Math.max(120, Math.min(260, distance / 3));
            this.swipeAnimator = ValueAnimator.ofFloat(fromX, targetX);
            this.swipeAnimator.setDuration(duration);
            this.swipeAnimator.setInterpolator(new DecelerateInterpolator());
            this.swipeAnimator.addUpdateListener(value -> {
                float x = ((Float) value.getAnimatedValue()).floatValue();
                int width = Math.max(MODE_TOP_LEVEL, getWidth());
                Math.min(1.0f, Math.abs(x) / width);
                if (this.dragChild != null) {
                    this.dragChild.setTranslationX(x);
                    this.dragChild.setAlpha(1.0f);
                }
                if (this.previewChild != null) {
                    this.previewChild.setTranslationX((this.gestureDirection * width) + x);
                    this.previewChild.setAlpha(1.0f);
                }
            });
            this.swipeAnimator.addListener(new AnimatorListenerAdapter() { // from class: com.openzen.heyboxcommunity.MainActivity.BackSwipeFrameLayout.1
                @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
                public void onAnimationEnd(Animator animation) {
                    BackSwipeFrameLayout.this.swipeAnimator = null;
                    if (end != null) {
                        end.run();
                    }
                }
            });
            this.swipeAnimator.start();
        }

        private void completeShellSwipe() {
            Runnable change;
            int direction = this.gestureDirection;
            int mode = this.gestureMode;
            String targetKey = targetScreenKeyForGesture();
            if (mode == MODE_BACK && "settings_home".equals(targetKey) && this.previewChild != null && !(this.previewChild instanceof ImageView)) {
                completeSettingsHomeSwipe();
                return;
            }
            ImageView rebuildGuard = mode == MODE_BACK ? MainActivity.this.installFullScreenTransitionOverlay(MainActivity.this.fullScreenSnapshot(targetKey)) : null;
            if (this.dragChild != null) {
                this.dragChild.setTranslationX(0.0f);
                this.dragChild.setAlpha(1.0f);
            }
            if (mode == MODE_TOP_LEVEL) {
                int next = MainActivity.this.topLevelIndex() + direction;
                change = () -> {
                    MainActivity.this.showTopLevel(next);
                };
            } else {
                MainActivity mainActivity = MainActivity.this;
                change = mainActivity::onBackPressed;
            }
            change.run();
            View newChild = currentShellChild();
            if (newChild == null) {
                resetShellDrag();
                MainActivity.this.removeFullScreenTransitionOverlayAfterLayout(rebuildGuard);
                return;
            }
            this.dragChild = newChild;
            newChild.setTranslationX(0.0f);
            newChild.setAlpha(1.0f);
            resetShellDrag();
            MainActivity.this.removeFullScreenTransitionOverlayAfterLayout(rebuildGuard);
        }

        private void completeSettingsHomeSwipe() {
            View oldChild = this.dragChild;
            View target = this.previewChild;
            if (target == null) {
                resetShellDrag();
                MainActivity.this.showSettingsHome();
                return;
            }
            target.setTranslationX(0.0f);
            target.setAlpha(1.0f);
            if (oldChild != null && oldChild.getParent() == this) {
                super.removeView(oldChild);
            }
            MainActivity.this.screen = "settings_home";
            MainActivity.this.setBottomNavVisible(false, false);
            MainActivity.this.leading.setVisibility(0);
            MainActivity.this.leading.setOnClickListener(view -> {
                MainActivity.this.showProfile();
            });
            MainActivity.this.title.setText("设置中心");
            MainActivity.this.action.setVisibility(4);
            this.displayedScreenKey = "settings_home";
            this.previewChild = null;
            this.dragChild = null;
            this.gestureMode = 0;
            this.gestureDirection = 0;
            this.dragging = false;
            MainActivity.this.shellAnimating = false;
        }

        private void resetShellDrag() {
            cancelSwipeAnimator();
            if (this.dragChild != null) {
                this.dragChild.setTranslationX(0.0f);
                this.dragChild.setAlpha(1.0f);
            }
            removeShellPreview();
            this.dragChild = null;
            this.gestureMode = 0;
            this.gestureDirection = 0;
            this.dragging = false;
            MainActivity.this.shellAnimating = false;
        }

        private void cancelSwipeAnimator() {
            if (this.swipeAnimator != null) {
                this.swipeAnimator.removeAllListeners();
                this.swipeAnimator.cancel();
                this.swipeAnimator = null;
            }
        }

        @Override // android.view.View
        public boolean performClick() {
            return super.performClick();
        }
    }

    /* JADX INFO: loaded from: MainActivity$PullRefreshListView.class */
    private final class PullRefreshListView extends ListView {
        private final int touchSlop;
        private final TextView refreshHeader;
        private final int triggerHeight;
        private float startX;
        private float startY;
        private boolean trackingPull;
        private boolean pulling;
        private boolean refreshing;
        private Runnable refreshAction;

        PullRefreshListView(Context context) {
            super(context);
            this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
            this.triggerHeight = MainActivity.this.dp(58);
            this.refreshHeader = MainActivity.this.text("下拉刷新", 12.0f, MainActivity.this.MUTED);
            this.refreshHeader.setGravity(17);
            this.refreshHeader.setAlpha(0.0f);
            this.refreshHeader.setBackgroundColor(MainActivity.this.BG);
            addHeaderView(this.refreshHeader, null, false);
            setHeaderHeight(0);
        }

        void setPullRefreshAction(Runnable action) {
            this.refreshAction = action;
        }

        void setRefreshing(boolean value) {
            this.refreshing = value;
            this.refreshHeader.animate().cancel();
            if (value) {
                this.refreshHeader.setText("正在刷新...");
                this.refreshHeader.setTextColor(MainActivity.this.SECONDARY);
                setHeaderHeight(Math.max(this.triggerHeight, headerHeight()));
                this.refreshHeader.setAlpha(1.0f);
                return;
            }
            this.refreshHeader.setText("下拉刷新");
            this.refreshHeader.setTextColor(MainActivity.this.MUTED);
            if (!this.pulling) {
                animateHeaderTo(0);
            }
        }

        @Override // android.widget.AbsListView, android.view.View
        public boolean onTouchEvent(MotionEvent event) {
            if (!this.pulling && !this.refreshing && event.getActionMasked() == MainActivity.REPLY_PREVIEW_COUNT && isAtTop() && event.getY() - this.startY > this.touchSlop) {
                this.trackingPull = true;
            }
            switch (event.getActionMasked()) {
                case 0:
                    this.startX = event.getX();
                    this.startY = event.getY();
                    this.trackingPull = isAtTop() && !this.refreshing;
                    this.pulling = false;
                    break;
                case 1:
                case 3:
                    if (this.pulling) {
                        boolean shouldRefresh = headerHeight() >= this.triggerHeight;
                        this.pulling = false;
                        this.trackingPull = false;
                        if (shouldRefresh) {
                            setRefreshing(true);
                            if (this.refreshAction != null) {
                                this.refreshAction.run();
                                return true;
                            }
                            return true;
                        }
                        animateHeaderTo(0);
                        return true;
                    }
                    this.trackingPull = false;
                    break;
                case MainActivity.REPLY_PREVIEW_COUNT /* 2 */:
                    float dx = event.getX() - this.startX;
                    float dy = event.getY() - this.startY;
                    if (Math.abs(dx) > Math.abs(dy) * 1.15f) {
                        this.trackingPull = false;
                    } else if ((this.trackingPull || this.pulling) && dy > this.touchSlop * 0.55f && isAtTop() && !this.refreshing) {
                        this.pulling = true;
                        getParent().requestDisallowInterceptTouchEvent(true);
                        int height = Math.min(this.triggerHeight + MainActivity.this.dp(28), Math.round((dy - (this.touchSlop * 0.55f)) * 0.62f));
                        setHeaderHeight(Math.max(0, height));
                        this.refreshHeader.setText(height >= this.triggerHeight ? "松开刷新" : "下拉刷新");
                        this.refreshHeader.setTextColor(height >= this.triggerHeight ? MainActivity.this.SECONDARY : MainActivity.this.MUTED);
                        this.refreshHeader.setAlpha(Math.min(1.0f, height / this.triggerHeight));
                        return true;
                    }
                    break;
            }
            return super.onTouchEvent(event);
        }

        @Override // android.widget.AbsListView, android.view.ViewGroup
        public boolean onInterceptTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case 0:
                    this.startX = event.getX();
                    this.startY = event.getY();
                    this.trackingPull = isAtTop() && !this.refreshing;
                    this.pulling = false;
                    break;
                case MainActivity.REPLY_PREVIEW_COUNT /* 2 */:
                    if (!this.refreshing) {
                        float dx = event.getX() - this.startX;
                        float dy = event.getY() - this.startY;
                        if (isAtTop() && dy > this.touchSlop * 0.75f && Math.abs(dy) > Math.abs(dx) * 1.12f) {
                            this.trackingPull = true;
                            return true;
                        }
                    }
                    break;
            }
            return super.onInterceptTouchEvent(event);
        }

        private boolean isAtTop() {
            if (getChildCount() != 0 && canScrollVertically(-1)) {
                return getFirstVisiblePosition() <= 1 && getChildAt(0) != null && getChildAt(0).getTop() >= getPaddingTop();
            }
            return true;
        }

        private int headerHeight() {
            ViewGroup.LayoutParams params = this.refreshHeader.getLayoutParams();
            if (params == null) {
                return 0;
            }
            return params.height;
        }

        private void setHeaderHeight(int height) {
            ViewGroup.LayoutParams params = this.refreshHeader.getLayoutParams();
            if (params == null) {
                params = new AbsListView.LayoutParams(-1, height);
            }
            if (Math.abs(params.height - height) < MainActivity.this.dp(MainActivity.REPLY_PREVIEW_COUNT)) {
                return;
            }
            params.height = Math.max(0, height);
            this.refreshHeader.setLayoutParams(params);
        }

        private void animateHeaderTo(int target) {
            int start = headerHeight();
            if (start == target) {
                return;
            }
            ValueAnimator animator = ValueAnimator.ofInt(start, target);
            animator.setDuration(160L);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(value -> {
                int height = ((Integer) value.getAnimatedValue()).intValue();
                setHeaderHeight(height);
                this.refreshHeader.setAlpha(this.triggerHeight <= 0 ? 0.0f : Math.min(1.0f, height / this.triggerHeight));
            });
            animator.start();
        }
    }

    private void showLogin() {
        stopQrPolling();
        this.screen = "login";
        if (this.shellBar != null) {
            this.shellBar.setVisibility(8);
        }
        setBottomNavVisible(false);
        this.leading.setVisibility(4);
        this.action.setVisibility(4);
        this.title.setText("扫码登录");
        this.content.removeAllViews();
        LinearLayout page = vertical(this.BG);
        page.setGravity(1);
        page.setPadding(dp(12), dp(12), dp(12), dp(12));
        TextView heading = text("heybox Lite", 20.0f, this.TEXT);
        heading.setTypeface(appRegularTypeface(), 1);
        heading.setGravity(17);
        page.addView(heading, new LinearLayout.LayoutParams(-1, dp(34)));
        TextView status = text("正在获取二维码", 12.0f, this.MUTED);
        status.setGravity(17);
        status.setTag("qr_status");
        page.addView(status, new LinearLayout.LayoutParams(-1, dp(28)));
        int qrSize = Math.round(getResources().getDisplayMetrics().widthPixels * 0.54f);
        ImageView qr = new ImageView(this);
        qr.setTag("qr_image");
        qr.setPadding(dp(7), dp(7), dp(7), dp(7));
        qr.setBackgroundColor(-1);
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(qrSize, qrSize);
        qrParams.topMargin = dp(4);
        page.addView(qr, qrParams);
        TextView hint = text("请使用小黑盒 App 扫码", 12.0f, this.SECONDARY);
        hint.setGravity(17);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, dp(34));
        hintParams.topMargin = dp(4);
        page.addView(hint, hintParams);
        Button retry = button("重新获取", R.drawable.ic_refresh);
        retry.setOnClickListener(view -> {
            requestQr();
        });
        page.addView(retry, new LinearLayout.LayoutParams(dp(150), dp(38)));
        Button guest = button("游客浏览", R.drawable.ic_home);
        guest.setOnClickListener(view2 -> {
            showFeed();
        });
        LinearLayout.LayoutParams guestParams = new LinearLayout.LayoutParams(dp(150), dp(38));
        guestParams.topMargin = dp(7);
        page.addView(guest, guestParams);
        this.content.addView(page, match());
        requestQr();
    }

    private void requestQr() {
        stopQrPolling();
        ImageView oldImage = (ImageView) this.content.findViewWithTag("qr_image");
        if (oldImage != null) {
            oldImage.setImageDrawable(null);
        }
        setQrStatus("正在获取二维码", this.MUTED);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app", "web");
        params.put(SecureStrings.heyboxId(), "");
        this.api.get(EndpointProvider.qrUrl(), params, new ApiClient.Callback() { // from class: com.openzen.heyboxcommunity.MainActivity.7
            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onSuccess(JSONObject body) {
                JSONObject result = body.optJSONObject("result");
                String url = result == null ? "" : result.optString("qr_url");
                if (url.isEmpty()) {
                    MainActivity.this.setQrStatus("二维码获取失败", MainActivity.this.PRIMARY);
                    return;
                }
                MainActivity.this.qrKey = Uri.parse(url).getQueryParameter("qr");
                if (MainActivity.this.qrKey == null || MainActivity.this.qrKey.isEmpty()) {
                    MainActivity.this.qrKey = result.optString("qr");
                }
                try {
                    int size = Math.round(MainActivity.this.getResources().getDisplayMetrics().widthPixels * 0.5f);
                    ImageView image = (ImageView) MainActivity.this.content.findViewWithTag("qr_image");
                    if (image != null) {
                        image.setImageBitmap(QrCode.create(url, size));
                    }
                    MainActivity.this.setQrStatus("等待扫码", MainActivity.this.SECONDARY);
                    MainActivity.this.pollingQr = true;
                    MainActivity.this.handler.postDelayed(MainActivity.this.qrPollTask, 900L);
                } catch (Exception e) {
                    MainActivity.this.setQrStatus("二维码生成失败", MainActivity.this.PRIMARY);
                }
            }

            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onError(String message) {
                MainActivity.this.setQrStatus("获取失败：" + MainActivity.this.qrErrorMessage(message), MainActivity.this.PRIMARY);
            }
        });
    }

    private String qrErrorMessage(String message) {
        if (message == null || message.isEmpty()) {
            return "请稍后重试";
        }
        if (message.contains("HTTP 403")) {
            return "请求过于频繁，请稍后重试";
        }
        String compact = message.replace('\n', ' ').trim();
        return compact.length() > 28 ? compact.substring(0, 28) + "" : compact;
    }

    private void pollQr() {
        if (!this.pollingQr || this.qrKey == null || this.qrKey.isEmpty()) {
            return;
        }
        Map<String, String> params = new HashMap<>();
        params.put("qr", this.qrKey);
        params.put("app", "web");
        this.api.get(EndpointProvider.qrState(), params, new ApiClient.Callback() { // from class: com.openzen.heyboxcommunity.MainActivity.8
            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onSuccess(JSONObject body) {
                if (MainActivity.this.pollingQr) {
                    JSONObject result = body.optJSONObject("result");
                    String state = result == null ? "" : result.optString("error");
                    if ("ok".equals(state)) {
                        MainActivity.this.session.saveLogin(result);
                        MainActivity.this.pollingQr = false;
                        MainActivity.this.feed.clear();
                        MainActivity.this.toast("登录成功");
                        EmojiStore.load(MainActivity.this.api, () -> {
                        });
                        MainActivity.this.showFeed();
                        return;
                    }
                    if ("ready".equals(state)) {
                        MainActivity.this.setQrStatus("已扫码，请在手机确认", MainActivity.this.PRIMARY);
                    } else {
                        if ("cancel".equals(state)) {
                            MainActivity.this.pollingQr = false;
                            MainActivity.this.setQrStatus("登录已取消，请重新获取", MainActivity.this.PRIMARY);
                            return;
                        }
                        MainActivity.this.setQrStatus("等待扫码", MainActivity.this.SECONDARY);
                    }
                    MainActivity.this.handler.postDelayed(MainActivity.this.qrPollTask, 1300L);
                }
            }

            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onError(String message) {
                if (MainActivity.this.pollingQr) {
                    MainActivity.this.setQrStatus("网络波动，正在重试", MainActivity.this.MUTED);
                    MainActivity.this.handler.postDelayed(MainActivity.this.qrPollTask, 2200L);
                }
            }
        });
    }

    private void setQrStatus(String value, int color) {
        TextView view = (TextView) this.content.findViewWithTag("qr_status");
        if (view != null) {
            view.setText(value);
            view.setTextColor(color);
        }
    }

    private void stopQrPolling() {
        this.pollingQr = false;
        this.handler.removeCallbacks(this.qrPollTask);
    }

    private void showFeed() {
        // 底部导航从“我的”切回社区：feed 在左侧，用返回方向的转场
        if ("profile".equals(this.screen)) {
            this.pendingBackTransition = true;
        }
        activate("feed");
        this.title.setText("社区");
        this.action.setText("");
        setIcon(this.action, R.drawable.ic_refresh, this.TEXT, 19);
        this.action.setVisibility(4);
        this.action.setOnClickListener(view -> {
            loadFeed(true);
        });
        final boolean coldLaunchRefresh = this.feedColdLaunchPending;
        this.feedColdLaunchPending = false;
        if (coldLaunchRefresh) {
            this.feed.clear();
            this.feedOffset = 0;
            this.feedNoMore = false;
            this.feedLoadMoreFailed = false;
            this.suppressNextFeedCacheFallback = true;
            this.feedFirstVisible = 0;
            this.feedFirstTop = 0;
            this.cachedFeedContainer = null;
            this.cachedFeedListView = null;
        }
        if (!coldLaunchRefresh && this.cachedFeedContainer != null && this.feedListView == this.cachedFeedListView && this.cachedFeedContainer.getParent() == null) {
            transitionTo(this.cachedFeedContainer);
            restoreFeedScroll();
            updateFeedFooter();
            return;
        }
        if (!coldLaunchRefresh && this.feed.isEmpty()) {
            List<FeedItem> cached = filterItems(this.localCache.feedItems());
            if (!cached.isEmpty()) {
                this.feed.addAll(cached);
                this.feedOffset = this.feed.size();
                this.localCache.log("feed restored from offline cache: " + this.feed.size());
            }
        }
        PullRefreshListView list = new PullRefreshListView(this);
        this.feedListView = list;
        this.cachedFeedListView = list;
        list.setBackgroundColor(this.BG);
        list.setDivider(new ColorDrawable(0));
        list.setDividerHeight(dp(REPLY_PREVIEW_COUNT));
        list.setOverScrollMode(REPLY_PREVIEW_COUNT);
        list.setSelector(new ColorDrawable(this.session.darkMode() ? Color.rgb(50, 50, 50) : Color.rgb(225, 228, 232)));
        list.setPullRefreshAction(() -> {
            if (!loadFeed(true)) {
                list.setRefreshing(false);
            }
        });
        list.addHeaderView(feedTopBar(), null, false);
        this.feedFooter = feedFooterView();
        list.addFooterView(this.feedFooter, null, false);
        this.feedAdapter = new FeedAdapter(this, this.feed, this.session.noImage(), this.session.uiScale() / 100.0f, this.session.textScale() / 100.0f, this.session.darkMode(), this.PRIMARY, this.SECONDARY, this::showDetail, this::toggleFeedLike);
        list.setAdapter((ListAdapter) this.feedAdapter);
        list.setOnScrollListener(new AbsListView.OnScrollListener() { // from class: com.openzen.heyboxcommunity.MainActivity.9
            @Override // android.widget.AbsListView.OnScrollListener
            public void onScrollStateChanged(AbsListView view2, int state) {
            }

            @Override // android.widget.AbsListView.OnScrollListener
            public void onScroll(AbsListView view2, int first, int visible, int total) {
                if (total > 0 && first + visible >= total - MainActivity.REPLY_PREVIEW_COUNT && !MainActivity.this.feedNoMore && !MainActivity.this.feedLoadMoreFailed) {
                    MainActivity.this.loadFeed(false);
                }
            }
        });
        updateFeedFooter();
        this.cachedFeedContainer = list;
        transitionTo(list);
        if (!coldLaunchRefresh) {
            restoreFeedScroll();
        }
        if (coldLaunchRefresh || this.feed.isEmpty()) {
            loadFeed(true);
        }
    }

    private View feedTopBar() {
        LinearLayout wrap = vertical(this.BG);
        wrap.setPadding(dp(7), dp(7), dp(7), dp(4));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(16);
        row.setPadding(dp(8), 0, dp(REPLY_PAGE_SIZE), 0);
        ThemeTokens tokens = this.themeTokens == null ? ThemeTokens.of(this.session.darkMode(), this.PRIMARY, this.SECONDARY) : this.themeTokens;
        Compat.setBackground(row, roundStroke(tokens.panel, 18, tokens.hairline, 1));
        wrap.addView(row, new LinearLayout.LayoutParams(-1, dp(42)));
        TextView search = text("搜索帖子、作者或关键词", 12.0f, this.MUTED);
        search.setGravity(16);
        search.setSingleLine(true);
        setLeftIcon(search, R.drawable.ic_search, this.MUTED, 16);
        search.setOnClickListener(view -> {
            showSearch();
        });
        row.addView(search, new LinearLayout.LayoutParams(0, -1, 1.0f));
        return wrap;
    }

    private TextView feedFooterView() {
        TextView footer = text("", 12.0f, this.MUTED);
        footer.setGravity(17);
        footer.setPadding(dp(8), dp(10), dp(8), dp(16));
        footer.setOnClickListener(view -> {
            if (this.feedLoadMoreFailed) {
                loadFeed(false);
            }
        });
        return footer;
    }

    private void updateFeedFooter() {
        if (this.feedFooter == null) {
            return;
        }
        this.feedFooter.setVisibility((!this.feed.isEmpty() || this.feedLoadingMore || this.feedLoadMoreFailed || this.feedNoMore) ? 0 : 8);
        this.feedFooter.setEnabled(this.feedLoadMoreFailed);
        if (this.feedLoadingMore) {
            this.feedFooter.setText("正在加载更多...");
            this.feedFooter.setTextColor(this.MUTED);
        } else if (this.feedLoadMoreFailed) {
            this.feedFooter.setText("加载失败，点按重试");
            this.feedFooter.setTextColor(this.SECONDARY);
        } else if (this.feedNoMore) {
            this.feedFooter.setText("没有更多了");
            this.feedFooter.setTextColor(this.MUTED);
        } else {
            this.feedFooter.setText("上滑加载更多");
            this.feedFooter.setTextColor(this.MUTED);
        }
    }

    private boolean loadFeed(final boolean reset) {
        if (reset) {
            if (this.feedRefreshing) {
                return false;
            }
            this.feedRefreshing = true;
            this.feedNoMore = false;
            this.feedLoadMoreFailed = false;
        } else {
            if (this.feedNoMore || this.feedRefreshing || this.feedLoadingMore) {
                return false;
            }
            this.feedLoadingMore = true;
            this.feedLoadMoreFailed = false;
        }
        updateFeedFooter();
        final int requestSerial = this.feedRequestSerial + 1;
        this.feedRequestSerial = requestSerial;
        if (reset) {
            this.feedResetSerial = requestSerial;
        }
        final int previousOffset = this.feedOffset;
        final List<FeedItem> previous = reset ? new ArrayList<>(this.feed) : null;
        final boolean suppressCacheFallback = reset && this.suppressNextFeedCacheFallback;
        if (reset) {
            this.suppressNextFeedCacheFallback = false;
        }
        if (reset) {
            this.feedOffset = 0;
            setFeedRefreshBusy(true);
            if (this.feed.isEmpty()) {
                showLoading();
            }
        }
        Map<String, String> params = new HashMap<>();
        params.put("offset", String.valueOf(this.feedOffset));
        params.put("pull", reset ? "1" : "0");
        this.api.get(EndpointProvider.feeds(), params, new ApiClient.Callback() { // from class: com.openzen.heyboxcommunity.MainActivity.10
            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onSuccess(JSONObject body) {
                if (reset) {
                    MainActivity.this.feedRefreshing = false;
                } else {
                    MainActivity.this.feedLoadingMore = false;
                }
                if (reset || requestSerial >= MainActivity.this.feedResetSerial) {
                    MainActivity.this.hideLoading();
                    JSONObject result = body.optJSONObject("result");
                    JSONArray links = result == null ? null : result.optJSONArray("links");
                    List<FeedItem> fresh = new ArrayList<>();
                    int returned = 0;
                    if (links != null) {
                        for (int i = 0; i < links.length(); i++) {
                            JSONObject item = links.optJSONObject(i);
                            if (item != null) {
                                FeedItem parsed = FeedItem.from(item);
                                if (!MainActivity.this.isBlocked(parsed)) {
                                    fresh.add(parsed);
                                }
                                returned++;
                            }
                        }
                    }
                    if (reset && fresh.isEmpty() && previous != null && !previous.isEmpty()) {
                        MainActivity.this.feedOffset = Math.max(previousOffset, MainActivity.this.feed.size());
                        MainActivity.this.toast("没有获取到新内容，已保留原列表");
                    } else if (!reset && returned == 0) {
                        MainActivity.this.feedNoMore = true;
                    } else {
                        if (reset) {
                            MainActivity.this.feed.clear();
                        }
                        MainActivity.this.appendUnique(MainActivity.this.feed, fresh);
                        MainActivity.this.localCache.saveFeed(MainActivity.this.feed);
                    }
                    if (!reset && returned > 0 && fresh.isEmpty()) {
                        MainActivity.this.toast("本页内容已被关键词过滤");
                    }
                    if (returned > 0) {
                        MainActivity.this.feedOffset += Math.max(returned, MainActivity.NAV_BAR_HEIGHT_DP);
                    }
                    MainActivity.this.setFeedRefreshBusy(false);
                    MainActivity.this.updateFeedFooter();
                    if (MainActivity.this.feedAdapter != null) {
                        MainActivity.this.feedAdapter.notifyDataSetChanged();
                    }
                    if (MainActivity.this.feed.isEmpty()) {
                        MainActivity.this.showMessage("暂时没有获取到社区内容");
                    }
                }
            }

            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onError(String message) {
                if (reset) {
                    MainActivity.this.feedRefreshing = false;
                } else {
                    MainActivity.this.feedLoadingMore = false;
                }
                if (reset || requestSerial >= MainActivity.this.feedResetSerial) {
                    MainActivity.this.hideLoading();
                    MainActivity.this.setFeedRefreshBusy(false);
                    MainActivity.this.localCache.log((reset ? "feed refresh failed: " : "feed load more failed: ") + message);
                    if (reset && previous != null && MainActivity.this.feed.isEmpty()) {
                        MainActivity.this.feed.addAll(previous);
                    }
                    if (reset && !MainActivity.this.feed.isEmpty()) {
                        MainActivity.this.feedOffset = Math.max(previousOffset, MainActivity.this.feed.size());
                    }
                    if (reset && MainActivity.this.feed.isEmpty() && !suppressCacheFallback) {
                        List<FeedItem> cached = MainActivity.this.filterItems(MainActivity.this.localCache.feedItems());
                        if (!cached.isEmpty()) {
                            MainActivity.this.feed.addAll(cached);
                            MainActivity.this.toast(MainActivity.MSG_OFFLINE_CACHE);
                        }
                    } else if (!reset) {
                        MainActivity.this.feedLoadMoreFailed = true;
                    }
                    MainActivity.this.updateFeedFooter();
                    if (MainActivity.this.feedAdapter != null) {
                        MainActivity.this.feedAdapter.notifyDataSetChanged();
                    }
                    if (!MainActivity.this.feed.isEmpty()) {
                        MainActivity.this.toast("刷新失败，已保留原内容");
                    } else {
                        MainActivity.this.showMessage("加载失败\n" + message);
                    }
                }
            }
        });
        return true;
    }

    private void showSearch() {
        showSearch(false);
    }

    private void showSearch(boolean restoreResults) {
        ensureEmojiCatalog(() -> {
        });
        activate("search");
        this.title.setText("搜索");
        setBottomNavVisible(false);
        this.leading.setVisibility(0);
        this.leading.setOnClickListener(view -> {
            this.pendingBackTransition = true;
            showFeed();
        });
        this.action.setVisibility(4);
        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.setBackgroundColor(this.BG);
        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setGravity(16);
        EditText input = new EditText(this);
        input.setHint("搜索帖子");
        input.setHintTextColor(this.MUTED);
        input.setTextColor(this.TEXT);
        input.setSingleLine(true);
        input.setTextSize(sp(13.0f));
        Compat.tint(input, this.PRIMARY);
        searchBar.addView(input, new LinearLayout.LayoutParams(0, dp(42), 1.0f));
        Button submit = button("搜索", R.drawable.ic_search);
        LinearLayout.LayoutParams submitParams = new LinearLayout.LayoutParams(dp(82), dp(38));
        submitParams.leftMargin = dp(REPLY_PAGE_SIZE);
        searchBar.addView(submit, submitParams);
        LinearLayout recent = vertical(this.BG);
        FrameLayout results = new FrameLayout(this);
        results.setTag(searchBar);
        frameLayout.addView(results, match());
        TextView hint = text("输入关键词搜索社区帖子", 13.0f, this.MUTED);
        hint.setGravity(17);
        results.addView(hint, match());
        FrameLayout.LayoutParams recentParams = new FrameLayout.LayoutParams(-1, -2);
        recentParams.leftMargin = dp(7);
        recentParams.rightMargin = dp(7);
        recentParams.topMargin = dp(54);
        frameLayout.addView(recent, recentParams);
        FrameLayout.LayoutParams searchParams = new FrameLayout.LayoutParams(-1, dp(42), 48);
        searchParams.leftMargin = dp(7);
        searchParams.rightMargin = dp(7);
        searchParams.topMargin = dp(6);
        frameLayout.addView(searchBar, searchParams);
        prepareSearchBar(searchBar, dp(42));
        Runnable search = () -> {
            String keyword = input.getText().toString().trim();
            if (keyword.isEmpty()) {
                toast("请输入搜索关键词");
                return;
            }
            this.session.addSearchHistory(keyword);
            recent.setVisibility(8);
            performSearch(keyword, results, searchBar);
        };
        submit.setOnClickListener(view2 -> {
            search.run();
        });
        input.setOnEditorActionListener((view3, actionId, event) -> {
            search.run();
            return true;
        });
        renderSearchHistory(recent, input, results);
        transitionTo(frameLayout);
        // 从详情返回时恢复上一次的搜索词和已加载结果，避免整页重来
        if (restoreResults && !this.lastSearchKeyword.isEmpty() && !this.searchResultItems.isEmpty()) {
            input.setText(this.lastSearchKeyword);
            input.setSelection(input.length());
            recent.setVisibility(8);
            this.searchSession++;
            this.searchLoadingMore = false;
            renderSearchResultList(results, searchBar, "");
            if (this.searchListView != null && this.searchListPosition > 0) {
                this.searchListView.setSelectionFromTop(this.searchListPosition, this.searchListTopOffset);
            }
        }
    }

    private void renderSearchHistory(LinearLayout parent, EditText input, FrameLayout results) {
        parent.removeAllViews();
        List<String> history = this.session.searchHistory();
        if (history.isEmpty()) {
            return;
        }
        LinearLayout header = new LinearLayout(this);
        header.setGravity(16);
        TextView label = text("最近搜索", 11.0f, this.MUTED);
        label.setTypeface(appRegularTypeface(), 1);
        header.addView(label, new LinearLayout.LayoutParams(0, dp(NAV_BAR_HEIGHT_DP), 1.0f));
        TextView clear = text("清空", 10.0f, this.SECONDARY);
        clear.setGravity(17);
        clear.setOnClickListener(view -> {
            this.session.clearSearchHistory();
            parent.removeAllViews();
        });
        header.addView(clear, new LinearLayout.LayoutParams(dp(48), dp(NAV_BAR_HEIGHT_DP)));
        parent.addView(header);
        LinearLayout rows = vertical(this.BG);
        int visibleCount = Math.min(4, history.size());
        for (int i = 0; i < visibleCount; i++) {
            String value = history.get(i);
            TextView item = text(value, 12.0f, this.TEXT);
            item.setGravity(16);
            item.setPadding(dp(9), 0, dp(9), 0);
            Compat.setBackground(item, round(blend(this.PANEL, this.SECONDARY, this.session.darkMode() ? 0.16f : 0.09f), 7));
            item.setOnClickListener(view2 -> {
                input.setText(value);
                input.setSelection(input.length());
                this.session.addSearchHistory(value);
                parent.setVisibility(8);
                performSearch(value, results, (View) results.getTag());
            });
            addTop(rows, item, 4);
            item.getLayoutParams().height = dp(34);
        }
        parent.addView(rows);
    }

    /** 官方接口按 offset/limit 分页（page 参数会被忽略导致每页都返回第一页）。 */
    private Map<String, String> searchParams(String keyword, int offset) {
        Map<String, String> params = new HashMap<>();
        params.put("q", keyword);
        params.put("keyword", keyword);
        params.put("search_type", "link");
        params.put("offset", String.valueOf(offset));
        params.put("limit", "20");
        return params;
    }

    private int appendSearchResults(List<FeedItem> incoming) {
        Set<String> seen = new HashSet<>();
        for (FeedItem existing : this.searchResultItems) {
            seen.add(existing.id);
        }
        int added = 0;
        for (FeedItem item : incoming) {
            if (item.id != null && !item.id.isEmpty() && seen.add(item.id)) {
                this.searchResultItems.add(item);
                added++;
            }
        }
        return added;
    }

    private void performSearch(String keyword, final FrameLayout results, final View searchBar) {
        results.setTag(searchBar);
        results.removeAllViews();
        setSearchBarVisible(searchBar, true);
        LoadingSpinnerView progress = new LoadingSpinnerView(this);
        progress.setColor(this.PRIMARY);
        results.addView(progress, new FrameLayout.LayoutParams(dp(38), dp(38), 17));
        final int session = ++this.searchSession;
        this.lastSearchKeyword = keyword;
        this.searchOffset = 0;
        this.searchEndReached = false;
        this.searchLoadingMore = false;
        this.searchStaleRounds = 0;
        this.api.get(EndpointProvider.search(), searchParams(keyword, 0), new ApiClient.Callback() { // from class: com.openzen.heyboxcommunity.MainActivity.11
            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onSuccess(JSONObject body) {
                if ("search".equals(MainActivity.this.screen) && session == MainActivity.this.searchSession) {
                    List<FeedItem> items = new ArrayList<>();
                    MainActivity.this.collectFeedItems(body, items, new HashMap(), 0);
                    List<FeedItem> items2 = MainActivity.this.filterItems(items);
                    MainActivity.this.searchResultItems.clear();
                    MainActivity.this.appendSearchResults(items2);
                    // offset 按服务器实际返回条数推进，页大小不固定也不会跳帖
                    MainActivity.this.searchOffset = items.size();
                    if (items.isEmpty()) {
                        MainActivity.this.searchEndReached = true;
                    }
                    MainActivity.this.renderSearchResultList(results, searchBar, "没有找到相关帖子");
                }
            }

            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onError(String message) {
                if ("search".equals(MainActivity.this.screen) && session == MainActivity.this.searchSession) {
                    MainActivity.this.localCache.log("search failed: " + message);
                    MainActivity.this.setSearchBarVisible(searchBar, true);
                    results.removeAllViews();
                    TextView error = MainActivity.this.text("搜索失败\n" + message, 13.0f, MainActivity.this.MUTED);
                    error.setGravity(17);
                    results.addView(error, MainActivity.this.match());
                }
            }
        });
    }

    private void renderSearchResultList(FrameLayout parent, View searchBar, String emptyText) {
        parent.removeAllViews();
        if (this.searchResultItems.isEmpty()) {
            setSearchBarVisible(searchBar, true);
            TextView empty = text(emptyText, 13.0f, this.MUTED);
            empty.setGravity(17);
            parent.addView(empty, match());
            return;
        }
        ListView list = new ListView(this);
        this.searchListView = list;
        list.setBackgroundColor(this.BG);
        list.setDivider(new ColorDrawable(0));
        list.setDividerHeight(dp(REPLY_PREVIEW_COUNT));
        // 搜索栏常驻在顶部，列表内容从其下方开始，滚动不再隐藏搜索栏（避免“闪一下像刷新”）
        list.setPadding(0, dp(54), 0, dp(4));
        list.setClipToPadding(false);
        final TextView footer = text(this.searchEndReached ? "没有更多了" : "上滑加载更多", 11.5f, this.MUTED);
        footer.setGravity(17);
        footer.setPadding(0, dp(10), 0, dp(12));
        list.addFooterView(footer, null, false);
        final FeedAdapter adapter = new FeedAdapter(this, this.searchResultItems, this.session.noImage(),
                this.session.uiScale() / 100.0f, this.session.textScale() / 100.0f, this.session.darkMode(),
                this.PRIMARY, this.SECONDARY, this::showDetail, this::toggleFeedLike);
        list.setAdapter((ListAdapter) adapter);
        footer.setOnClickListener(view -> {
            loadMoreSearchResults(adapter, footer);
        });
        list.setOnScrollListener(new AbsListView.OnScrollListener() { // from class: com.openzen.heyboxcommunity.MainActivity.12
            @Override // android.widget.AbsListView.OnScrollListener
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override // android.widget.AbsListView.OnScrollListener
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (totalItemCount > 0 && firstVisibleItem + visibleItemCount >= totalItemCount - REPLY_PREVIEW_COUNT) {
                    MainActivity.this.loadMoreSearchResults(adapter, footer);
                }
            }
        });
        parent.addView(list, match());
    }

    private void loadMoreSearchResults(final FeedAdapter adapter, final TextView footer) {
        if (this.searchLoadingMore || this.searchEndReached || this.lastSearchKeyword.isEmpty()) {
            return;
        }
        this.searchLoadingMore = true;
        footer.setText("正在加载更多…");
        final int session = this.searchSession;
        this.api.get(EndpointProvider.search(), searchParams(this.lastSearchKeyword, this.searchOffset), new ApiClient.Callback() { // from class: com.openzen.heyboxcommunity.MainActivity.31
            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onSuccess(JSONObject body) {
                if (session != MainActivity.this.searchSession) {
                    return;
                }
                MainActivity.this.searchLoadingMore = false;
                List<FeedItem> items = new ArrayList<>();
                MainActivity.this.collectFeedItems(body, items, new HashMap(), 0);
                if (items.isEmpty()) {
                    // 服务器返回空页才是真的到底；只是去重/过滤掉不算
                    MainActivity.this.searchEndReached = true;
                    footer.setText("没有更多了");
                    return;
                }
                MainActivity.this.searchOffset += items.size();
                int added = MainActivity.this.appendSearchResults(MainActivity.this.filterItems(items));
                if (added > 0) {
                    MainActivity.this.searchStaleRounds = 0;
                    footer.setText("上滑加载更多");
                    adapter.notifyDataSetChanged();
                    return;
                }
                // 连续几页都是重复内容说明接口在兜圈子，视为到底，避免无限请求
                MainActivity.this.searchStaleRounds++;
                if (MainActivity.this.searchStaleRounds >= 3) {
                    MainActivity.this.searchEndReached = true;
                    footer.setText("没有更多了");
                } else {
                    footer.setText("上滑加载更多");
                }
            }

            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onError(String message) {
                if (session != MainActivity.this.searchSession) {
                    return;
                }
                MainActivity.this.searchLoadingMore = false;
                footer.setText("加载失败，点击重试");
            }
        });
    }

    private void setSearchBarVisible(View searchBar, boolean visible) {
        if (searchBar == null) {
            return;
        }
        Boolean state = this.searchBarStates.get(searchBar);
        if (state == null || state.booleanValue() != visible) {
            this.searchBarStates.put(searchBar, Boolean.valueOf(visible));
            searchBar.animate().cancel();
            if (visible) {
                searchBar.setVisibility(0);
                searchBar.setEnabled(true);
                setChildrenEnabled(searchBar, true);
                searchBar.animate().alpha(1.0f).translationY(0.0f).setDuration(120L).start();
                return;
            }
            clearFocusTree(searchBar);
            searchBar.setEnabled(false);
            setChildrenEnabled(searchBar, false);
            searchBar.animate().alpha(0.0f).translationY(-dp(12)).setDuration(110L).start();
            this.handler.postDelayed(() -> {
                Boolean current = this.searchBarStates.get(searchBar);
                if (current == null || current.booleanValue()) {
                    return;
                }
                searchBar.setVisibility(8);
            }, 120L);
        }
    }

    private void prepareSearchBar(View searchBar, int height) {
        this.searchBarHeights.put(searchBar, Integer.valueOf(height));
        this.searchBarStates.put(searchBar, true);
        searchBar.setVisibility(0);
        searchBar.setAlpha(1.0f);
        searchBar.setTranslationY(0.0f);
    }

    private void clearFocusTree(View view) {
        view.clearFocus();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                clearFocusTree(group.getChildAt(i));
            }
        }
    }

    private void setChildrenEnabled(View view, boolean enabled) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                child.setEnabled(enabled);
                setChildrenEnabled(child, enabled);
            }
        }
    }

    private void collectFeedItems(Object node, List<FeedItem> output, Map<String, Boolean> ids, int depth) {
        if (node == null || depth > 7) {
            return;
        }
        if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                collectFeedItems(array.opt(i), output, ids, depth + 1);
            }
            return;
        }
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            String id = object.optString("linkid", object.optString("link_id"));
            String titleValue = object.optString("title");
            if (!id.isEmpty() && (!titleValue.isEmpty() || object.has("text") || object.has("description") || object.has("content"))) {
                if (!ids.containsKey(id)) {
                    ids.put(id, true);
                    output.add(FeedItem.from(object));
                    return;
                }
                return;
            }
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                collectFeedItems(object.opt(keys.next()), output, ids, depth + 1);
            }
        }
    }

    private void appendUnique(List<FeedItem> target, List<FeedItem> incoming) {
        Map<String, Boolean> ids = new HashMap<>();
        Iterator<FeedItem> it = target.iterator();
        while (it.hasNext()) {
            ids.put(it.next().id, true);
        }
        for (FeedItem item : incoming) {
            if (item != null && !ids.containsKey(item.id)) {
                target.add(item);
                ids.put(item.id, true);
            }
        }
    }

    private List<FeedItem> filterItems(List<FeedItem> items) {
        List<FeedItem> filtered = new ArrayList<>();
        if (items == null) {
            return filtered;
        }
        for (FeedItem item : items) {
            if (!isBlocked(item)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private boolean isBlocked(FeedItem item) {
        if (item == null) {
            return false;
        }
        List<String> keywords = this.session.blockKeywordList();
        if (keywords.isEmpty()) {
            return false;
        }
        String haystack = (item.title + "\n" + item.description + "\n" + item.author).toLowerCase(Locale.US);
        for (String keyword : keywords) {
            if (!keyword.isEmpty() && haystack.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private void setFeedRefreshBusy(boolean busy) {
        if (!"feed".equals(this.screen) || this.action == null) {
            return;
        }
        this.action.setEnabled(!busy);
        this.action.setAlpha(busy ? 0.45f : 1.0f);
        if (this.feedListView instanceof PullRefreshListView) {
            ((PullRefreshListView) this.feedListView).setRefreshing(busy);
        }
    }

    private void saveFeedScroll() {
        if (this.feedListView == null) {
            return;
        }
        this.feedFirstVisible = Math.max(0, this.feedListView.getFirstVisiblePosition());
        View first = this.feedListView.getChildAt(0);
        this.feedFirstTop = first == null ? 0 : first.getTop();
    }

    private void restoreFeedScroll() {
        if (this.feedListView == null || this.feed.isEmpty()) {
            return;
        }
        int maxPosition = Math.max(0, (this.feed.size() + this.feedListView.getHeaderViewsCount()) - 1);
        int position = Math.max(0, Math.min(this.feedFirstVisible, maxPosition));
        int top = this.feedFirstTop;
        this.feedListView.post(() -> {
            this.feedListView.setSelectionFromTop(position, top);
            this.feedListView.post(() -> {
                this.feedListView.setSelectionFromTop(position, top);
            });
        });
    }

    private void invalidateFeedView() {
        this.cachedFeedListView = null;
        this.cachedFeedContainer = null;
        this.feedListView = null;
        this.feedAdapter = null;
        this.feedFooter = null;
    }

    private void showDetail(final FeedItem item) {
        this.pendingBackTransition = false;
        PageTransitionController.finishNow();
        saveCurrentDetailProgress();
        stopQrPolling();
        ensureEmojiCatalog(() -> {
        });
        if ("feed".equals(this.screen)) {
            saveFeedScroll();
        }
        if ("search".equals(this.screen) && this.searchListView != null) {
            this.searchListPosition = this.searchListView.getFirstVisiblePosition();
            View firstChild = this.searchListView.getChildCount() > 0 ? this.searchListView.getChildAt(0) : null;
            // setSelectionFromTop 的 y 不含 paddingTop，这里要减掉，否则恢复时会往下偏一个搜索栏
            this.searchListTopOffset = firstChild == null ? 0
                    : firstChild.getTop() - this.searchListView.getPaddingTop();
        }
        View sourceChild = (this.content == null || this.content.getChildCount() == 0) ? null : this.content.getChildAt(0);
        captureShellSnapshot(this.screen, sourceChild);
        captureFullScreenSnapshot(this.screen);
        if (!"detail".equals(this.screen)) {
            this.detailReturn = this.pendingDetailReturn.isEmpty() ? this.screen : this.pendingDetailReturn;
            this.pendingDetailReturn = "";
            this.detailReturnTitle = this.title == null ? "" : this.title.getText().toString();
            this.detailReturnView = shouldKeepDetailReturnView(this.screen) && sourceChild != null
                    ? sourceChild : null;
        }
        this.screen = "detail";
        this.currentLinkId = item.id;
        this.currentLinkHsrc = item.hsrc;
        this.currentAuthCode = "";
        this.currentDetailItem = item;
        this.currentDetailBody = null;
        if (this.shellBar != null) {
            this.shellBar.setVisibility(8);
        }
        setBottomNavVisible(false);
        this.leading.setVisibility(0);
        this.leading.setOnClickListener(view -> {
            returnFromDetailSmooth();
        });
        this.title.setText("正文");
        this.action.setVisibility(4);
        this.content.removeAllViews();
        showLoading();
        final int requestToken = this.detailRequestToken + 1;
        this.detailRequestToken = requestToken;
        if (!isNetworkConnected()) {
            hideLoading();
            handleDetailFailure(item, "当前无网络");
            return;
        }
        this.api.get(EndpointProvider.linkTree(), detailParams(item), new ApiClient.Callback() { // from class: com.openzen.heyboxcommunity.MainActivity.13
            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onSuccess(JSONObject body) {
                if (MainActivity.this.isCurrentDetailRequest(item, requestToken)) {
                    MainActivity.this.hideLoading();
                    String blocked = MainActivity.this.detailBlockedMessage(body);
                    if (!blocked.isEmpty()) {
                        MainActivity.this.requestDetailV2(item, requestToken, blocked);
                        return;
                    }
                    if (!MainActivity.this.hasDetailLink(body)) {
                        MainActivity.this.requestDetailV2(item, requestToken, "详情数据为空");
                        if (MainActivity.this.isCurrentDetailRequest(item, requestToken)) {
                            return;
                        }
                        MainActivity.this.handleDetailFailure(item, "详情数据为空");
                        return;
                    }
                    MainActivity.this.cacheDetailAndRender(item, body);
                }
            }

            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onError(String message) {
                if (MainActivity.this.isCurrentDetailRequest(item, requestToken)) {
                    MainActivity.this.hideLoading();
                    MainActivity.this.requestDetailV2(item, requestToken, message);
                    if (MainActivity.this.isCurrentDetailRequest(item, requestToken)) {
                        return;
                    }
                    MainActivity.this.handleDetailFailure(item, message);
                }
            }
        });
    }

    private void requestDetailV2(final FeedItem item, final int requestToken, final String fallbackMessage) {
        if (isCurrentDetailRequest(item, requestToken)) {
            showLoading();
            this.api.get(EndpointProvider.linkTreeV2(), detailParams(item), new ApiClient.Callback() { // from class: com.openzen.heyboxcommunity.MainActivity.14
                @Override // com.openzen.heyboxcommunity.ApiClient.Callback
                public void onSuccess(JSONObject body) {
                    if (MainActivity.this.isCurrentDetailRequest(item, requestToken)) {
                        MainActivity.this.hideLoading();
                        String blocked = MainActivity.this.detailBlockedMessage(body);
                        if (!blocked.isEmpty()) {
                            MainActivity.this.handleDetailFailure(item, blocked);
                        } else if (!MainActivity.this.hasDetailLink(body)) {
                            MainActivity.this.handleDetailFailure(item, MainActivity.first(fallbackMessage, "详情数据为空"));
                        } else {
                            MainActivity.this.cacheDetailAndRender(item, body);
                        }
                    }
                }

                @Override // com.openzen.heyboxcommunity.ApiClient.Callback
                public void onError(String message) {
                    if (MainActivity.this.isCurrentDetailRequest(item, requestToken)) {
                        MainActivity.this.hideLoading();
                        MainActivity.this.handleDetailFailure(item, MainActivity.first(message, fallbackMessage));
                    }
                }
            });
        }
    }

    private boolean isCurrentDetailRequest(FeedItem item, int requestToken) {
        return "detail".equals(this.screen) && item != null && item.id.equals(this.currentLinkId) && requestToken == this.detailRequestToken;
    }

    private void cacheDetailAndRender(FeedItem item, JSONObject body) {
        this.localCache.saveDetail(item.id, body);
        if (this.localCache.isWatchLater(item.id)) {
            refreshWatchLaterOffline(item, body, null);
        }
        renderDetail(body, item);
    }

    private Map<String, String> detailParams(FeedItem item) {
        Map<String, String> params = new HashMap<>();
        params.put("link_id", item.id);
        params.put("page", "1");
        params.put("limit", "20");
        params.put("index", "1");
        params.put("is_first", "1");
        params.put("owner_only", "0");
        if (item.hsrc != null && !item.hsrc.isEmpty()) {
            params.put("h_src", item.hsrc);
        }
        return params;
    }

    private boolean isNetworkConnected() {
        try {
            ConnectivityManager manager = (ConnectivityManager)
                    getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = manager == null ? null : manager.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception ignored) {
            return true;
        }
    }

    private void handleDetailFailure(FeedItem item, String message) {
        this.localCache.log("detail failed " + item.id + ": " + message);
        JSONObject cached = this.localCache.detail(item.id);
        if (cached != null && detailBlockedMessage(cached).isEmpty() && hasDetailLink(cached)) {
            toast(MSG_OFFLINE_CACHE);
            renderDetail(cached, item);
        } else if (!renderFallbackDetail(item, message)) {
            showMessage("详情加载失败\n" + message);
        }
    }

    private boolean renderFallbackDetail(FeedItem item, String reason) {
        if (item == null) {
            return false;
        }
        try {
            String notice = fallbackDetailNotice(reason);
            JSONObject link = item.toJson();
            if (link.optString("title").isEmpty()) {
                link.put("title", "帖子摘要暂不可用");
            }
            if (link.optString("description").isEmpty() && link.optString("text").isEmpty()) {
                link.put("description", notice + " 当前列表没有返回正文摘要，登录后可查看完整详情");
            }
            JSONObject result = new JSONObject();
            result.put("link", link);
            result.put("comments", new JSONArray());
            JSONObject body = new JSONObject();
            body.put("result", result);
            body.put("_fallback_notice", notice);
            renderDetail(body, item);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String fallbackDetailNotice(String reason) {
        if (reason == null) {
            reason = "";
        }
        if (reason.contains("验证") || reason.contains("captcha") || reason.contains("403") || reason.contains("限制")) {
            return "游客模式：完整详情需要验证，已显示首页摘要";
        }
        return "详情接口暂不可用，已显示首页摘要";
    }

    private String detailBlockedMessage(JSONObject body) {
        if (body == null) {
            return "详情数据为空";
        }
        String direct = detailBlockedMessageFrom(body);
        if (!direct.isEmpty()) {
            return direct;
        }
        JSONObject result = body.optJSONObject("result");
        return result == null ? "" : detailBlockedMessageFrom(result);
    }

    private boolean hasDetailLink(JSONObject body) {
        JSONObject result;
        return (body == null || (result = body.optJSONObject("result")) == null || result.optJSONObject("link") == null) ? false : true;
    }

    private String detailBlockedMessageFrom(JSONObject object) {
        String status = object.optString("status");
        String code = object.optString("code");
        String message = first(object.optString("msg"), object.optString("message"));
        return (isVerificationStatus(status) || isVerificationStatus(code)) ? first(message, status, code, "需要完成验证后才能继续") : isVerificationText(message) ? message : "";
    }

    private boolean isVerificationStatus(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.US);
        return lower.contains("captcha") || lower.contains("verify") || lower.contains("verification") || lower.contains("name_verify") || lower.contains("need_alipay_verify") || lower.contains("need_bind_phone") || lower.contains("need_phone_code");
    }

    private boolean isVerificationText(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.US);
        return lower.contains("captcha") || lower.contains("verify") || value.contains("需要完成验证") || value.contains("验证") || value.contains("接口限制") || value.contains("请求过于频繁");
    }

    private void renderDetail(JSONObject body, FeedItem fallback) {
        this.currentDetailBody = body;
        JSONObject result = body.optJSONObject("result");
        JSONObject link = result == null ? null : result.optJSONObject("link");
        String[] strArr = new String[3];
        strArr[0] = hsrc(link);
        strArr[1] = fallback == null ? "" : fallback.hsrc;
        strArr[REPLY_PREVIEW_COUNT] = this.currentLinkHsrc;
        this.currentLinkHsrc = first(strArr);
        String authCode = link == null ? "" : link.optString("auth_code");
        if (authCode.isEmpty() && result != null) {
            authCode = result.optString("auth_code");
        }
        if (authCode.isEmpty()) {
            authCode = body.optString("auth_code");
        }
        if (!authCode.isEmpty()) {
            this.currentAuthCode = authCode;
        }
        DetailPager pager = new DetailPager(this);
        pager.setBackgroundColor(this.BG);
        this.detailPager = pager;
        ScrollView articleScroll = new ScrollView(this);
        articleScroll.setBackgroundColor(this.BG);
        LinearLayout page = vertical(this.BG);
        int pagePadding = Math.max(dp(10), dp(this.session.pagePadding()));
        page.setPadding(pagePadding, dp(8), pagePadding, dp(18));
        articleScroll.addView(page);
        LinearLayout article = detailArticleSurface();
        JSONObject user = link == null ? null : link.optJSONObject("user");
        String author = user == null ? fallback.author : user.optString("username", fallback.author);
        String heading = link == null ? fallback.title : link.optString("title", fallback.title);
        TextView headline = text("", 19.0f, this.TEXT);
        EmojiRenderer.set(headline, RichContent.plainText(heading), this.session.darkMode());
        headline.setTypeface(appRegularTypeface(), 1);
        headline.setLineSpacing(dp(REPLY_PREVIEW_COUNT), 1.08f);
        article.addView(headline);
        addAuthorHeader(article, link, user, author);
        String notice = body.optString("_fallback_notice");
        if (!notice.isEmpty()) {
            TextView fallbackNotice = text(notice, 11.0f, this.SECONDARY);
            fallbackNotice.setLineSpacing(0.0f, 1.16f);
            GradientDrawable noticeBg = round(blend(this.PANEL, this.SECONDARY, this.session.darkMode() ? 0.22f : 0.12f), 7);
            noticeBg.setStroke(dp(1), blend(this.SECONDARY, this.TEXT, this.session.darkMode() ? 0.2f : 0.12f));
            fallbackNotice.setPadding(dp(8), dp(6), dp(8), dp(6));
            Compat.setBackground(fallbackNotice, noticeBg);
            addTop(article, fallbackNotice, 7);
        }
        JSONArray fallbackImages = link == null ? null : link.optJSONArray("imgs");
        this.lastDetailDiagnostics = buildDetailDiagnostics(body, fallback, link, fallbackImages);
        this.localCache.log("detail diagnostics captured link=" + (fallback == null ? "" : fallback.id) + " title=" + compactLogText(heading, 48));
        addRichContent(article, link, fallback.description, fallbackImages);
        addDetailActions(article, fallback, link);
        page.addView(article);
        addDetailCommentSection(page, result == null ? null : result.optJSONArray("comments"));
        ScrollView commentScroll = new ScrollView(this);
        commentScroll.setBackgroundColor(this.BG);
        LinearLayout commentPage = vertical(this.BG);
        commentPage.setPadding(pagePadding, dp(8), pagePadding, dp(18));
        commentScroll.addView(commentPage);
        addDetailCommentSection(commentPage, result == null ? null : result.optJSONArray("comments"));
        pager.setPages(articleScroll, commentScroll);
        this.content.addView(pager, match());
        this.detailScroll = articleScroll;
        this.detailCommentScroll = commentScroll;
        int savedScroll = this.session.rememberDetailScroll() ? this.localCache.scroll(this.currentLinkId) : 0;
        if (savedScroll > 0) {
            articleScroll.postDelayed(() -> {
                articleScroll.scrollTo(0, savedScroll);
            }, 80L);
        }
    }

    private void addDetailCommentSection(LinearLayout page, JSONArray commentArray) {
        View divider = new View(this);
        divider.setBackgroundColor(this.themeTokens == null ? blend(this.PANEL, this.SECONDARY, 0.18f) : this.themeTokens.hairline);
        addTop(page, divider, 10);
        divider.getLayoutParams().height = dp(1);
        TextView commentTitle = text("评论", 14.0f, this.TEXT);
        commentTitle.setTypeface(appRegularTypeface(), 1);
        commentTitle.setPadding(dp(REPLY_PREVIEW_COUNT), dp(10), dp(REPLY_PREVIEW_COUNT), dp(REPLY_PAGE_SIZE));
        page.addView(commentTitle);
        LinearLayout comments = vertical(this.BG);
        comments.setBackgroundColor(0);
        page.addView(comments);
        int count = addComments(comments, commentArray);
        if (count == 0) {
            TextView empty = text("暂无评论", 13.0f, this.MUTED);
            empty.setPadding(dp(4), dp(8), dp(4), dp(12));
            comments.addView(empty);
        }
    }

    /* JADX INFO: loaded from: MainActivity$DetailPager.class */
    private final class DetailPager extends FrameLayout {
        private static final int PAGE_ARTICLE = 0;
        private static final int PAGE_COMMENTS = 1;
        private final int touchSlop;
        private float startX;
        private float startY;
        private long startTime;
        private int startScrollX;
        private int currentPage;
        private boolean dragging;
        private boolean returning;
        private ImageView returnPreview;
        private ValueAnimator settleAnimator;

        DetailPager(Context context) {
            super(context);
            this.currentPage = 0;
            this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        void setPages(View article, View comments) {
            removeAllViews();
            this.returnPreview = new ImageView(getContext());
            this.returnPreview.setBackgroundColor(MainActivity.this.themeTokens == null ? MainActivity.this.PANEL : MainActivity.this.themeTokens.panel);
            this.returnPreview.setScaleType(ImageView.ScaleType.FIT_XY);
            Bitmap bitmap = MainActivity.this.screenSnapshot(MainActivity.this.backTargetScreenKey());
            if (bitmap != null && !bitmap.isRecycled()) {
                this.returnPreview.setImageBitmap(bitmap);
            }
            addView(this.returnPreview, new FrameLayout.LayoutParams(-1, -1));
            addView(article, new FrameLayout.LayoutParams(-1, -1));
            addView(comments, new FrameLayout.LayoutParams(-1, -1));
            this.currentPage = 0;
            post(() -> {
                scrollTo(pageScrollX(this.currentPage), 0);
            });
        }

        boolean showingComments() {
            return this.currentPage == PAGE_COMMENTS;
        }

        void showArticle(boolean animate) {
            settleToPage(0, animate);
        }

        @Override // android.widget.FrameLayout, android.view.ViewGroup, android.view.View
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int width = right - left;
            int height = bottom - top;
            if (this.returnPreview != null) {
                this.returnPreview.layout(-width, 0, 0, height);
            }
            if (getChildCount() > PAGE_COMMENTS) {
                getChildAt(PAGE_COMMENTS).layout(0, 0, width, height);
            }
            if (getChildCount() > MainActivity.REPLY_PREVIEW_COUNT) {
                getChildAt(MainActivity.REPLY_PREVIEW_COUNT).layout(width, 0, width * MainActivity.REPLY_PREVIEW_COUNT, height);
            }
            if (changed) {
                post(() -> {
                    scrollTo(pageScrollX(this.currentPage), 0);
                });
            }
        }

        @Override // android.view.ViewGroup
        public boolean onInterceptTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case 0:
                    cancelSettle();
                    this.startX = event.getX();
                    this.startY = event.getY();
                    this.startTime = event.getEventTime();
                    this.startScrollX = getScrollX();
                    this.dragging = false;
                    this.returning = false;
                    break;
                case PAGE_COMMENTS /* 1 */:
                case 3:
                    this.dragging = false;
                    this.returning = false;
                    break;
                case MainActivity.REPLY_PREVIEW_COUNT /* 2 */:
                    float dx = event.getX() - this.startX;
                    float dy = event.getY() - this.startY;
                    if ((this.currentPage != 0 || dx <= 0.0f || MainActivity.this.canDetailSwipeBack()) && Math.abs(dx) > this.touchSlop * MainActivity.REPLY_PREVIEW_COUNT && Math.abs(dx) > Math.abs(dy) * 1.15f) {
                        this.dragging = true;
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    break;
            }
            return this.dragging;
        }

        @Override // android.view.View
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case 0:
                    cancelSettle();
                    this.startX = event.getX();
                    this.startY = event.getY();
                    this.startTime = event.getEventTime();
                    this.startScrollX = getScrollX();
                    this.dragging = true;
                    this.returning = false;
                    break;
                case PAGE_COMMENTS /* 1 */:
                    finishHorizontalDrag(event);
                    performClick();
                    break;
                case MainActivity.REPLY_PREVIEW_COUNT /* 2 */:
                    dragTo(event.getX() - this.startX);
                    break;
                case 3:
                    settleToPage(this.currentPage, true);
                    this.dragging = false;
                    this.returning = false;
                    break;
            }
            return true;
        }

        private void dragTo(float dx) {
            int width = Math.max(PAGE_COMMENTS, getWidth());
            int min = (this.currentPage == 0 && MainActivity.this.canDetailSwipeBack()) ? -width : 0;
            int next = Math.max(min, Math.min(width, this.startScrollX - Math.round(dx)));
            scrollTo(next, 0);
        }

        private void finishHorizontalDrag(MotionEvent event) {
            int width = Math.max(PAGE_COMMENTS, getWidth());
            float dx = event.getX() - this.startX;
            long duration = Math.max(1L, event.getEventTime() - this.startTime);
            float velocity = (dx * 1000.0f) / duration;
            if (this.currentPage == 0 && getScrollX() < 0) {
                boolean enoughDistance = ((float) Math.abs(getScrollX())) > Math.max((float) MainActivity.this.dp(52), ((float) width) * 0.24f);
                boolean enoughVelocity = velocity > ((float) MainActivity.this.dp(320));
                this.dragging = false;
                if (!enoughDistance && !enoughVelocity) {
                    settleToPage(0, true);
                    return;
                } else {
                    settleToReturn();
                    return;
                }
            }
            int target = getScrollX() > width / MainActivity.REPLY_PREVIEW_COUNT ? PAGE_COMMENTS : 0;
            if (Math.abs(velocity) > MainActivity.this.dp(360)) {
                target = velocity < 0.0f ? PAGE_COMMENTS : 0;
            }
            this.dragging = false;
            settleToPage(target, true);
        }

        private void settleToPage(int page, boolean animate) {
            cancelSettle();
            this.currentPage = page;
            MainActivity.this.updateDetailPagerTitle();
            int destination = pageScrollX(page);
            if (!animate) {
                scrollTo(destination, 0);
                return;
            }
            int fromX = getScrollX();
            int distance = Math.abs(destination - fromX);
            if (distance < MainActivity.this.dp(MainActivity.REPLY_PREVIEW_COUNT)) {
                scrollTo(destination, 0);
                return;
            }
            int duration = Math.max(160, Math.min(300, distance / 3));
            this.settleAnimator = ValueAnimator.ofInt(fromX, destination);
            this.settleAnimator.setDuration(duration);
            this.settleAnimator.setInterpolator(new DecelerateInterpolator());
            this.settleAnimator.addUpdateListener(value -> {
                scrollTo(((Integer) value.getAnimatedValue()).intValue(), 0);
            });
            this.settleAnimator.start();
        }

        private int pageScrollX(int page) {
            if (page == PAGE_COMMENTS) {
                return Math.max(0, getWidth());
            }
            return 0;
        }

        private void settleToReturn() {
            cancelSettle();
            this.returning = true;
            int fromX = getScrollX();
            int destination = -Math.max(PAGE_COMMENTS, getWidth());
            int distance = Math.abs(destination - fromX);
            int duration = Math.max(150, Math.min(280, distance / 3));
            this.settleAnimator = ValueAnimator.ofInt(fromX, destination);
            this.settleAnimator.setDuration(duration);
            this.settleAnimator.setInterpolator(new DecelerateInterpolator());
            this.settleAnimator.addUpdateListener(value -> {
                scrollTo(((Integer) value.getAnimatedValue()).intValue(), 0);
            });
            this.settleAnimator.addListener(new AnimatorListenerAdapter() { // from class: com.openzen.heyboxcommunity.MainActivity.DetailPager.1
                @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
                public void onAnimationEnd(Animator animation) {
                    DetailPager.this.settleAnimator = null;
                    if (DetailPager.this.returning) {
                        DetailPager.this.returning = false;
                        MainActivity.this.returnFromDetailSmooth();
                    }
                }
            });
            this.settleAnimator.start();
        }

        private void cancelSettle() {
            if (this.settleAnimator != null) {
                this.returning = false;
                this.settleAnimator.cancel();
                this.settleAnimator = null;
            }
        }

        @Override // android.view.View
        public boolean performClick() {
            return super.performClick();
        }
    }

    private void updateDetailPagerTitle() {
        if (!"detail".equals(this.screen) || this.title == null || this.detailPager == null) {
            return;
        }
        this.title.setText(this.detailPager.showingComments() ? "评论" : "正文");
    }

    private void addDetailActions(LinearLayout article, FeedItem item, JSONObject link) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(16);
        TextView like = actionPill("", R.drawable.ic_thumb_up);
        LikeState state = linkLikeState(link, item);
        boolean liked = state.liked;
        int likes = state.likes;
        item.liked = liked;
        item.likes = likes;
        updateFeedLike(item.id, liked, likes);
        updateLinkLikeView(like, liked, likes);
        like.setOnClickListener(view -> {
            toggleLinkLike(item, like);
        });
        row.addView(like, new LinearLayout.LayoutParams(0, dp(36), 1.0f));
        TextView favorite = actionPill("", R.drawable.ic_bookmark);
        boolean favored = linkFavored(link);
        updateFavoriteView(favorite, favored);
        LinearLayout.LayoutParams favoriteParams = new LinearLayout.LayoutParams(0, dp(36), 1.0f);
        favoriteParams.leftMargin = dp(6);
        row.addView(favorite, favoriteParams);
        favorite.setOnClickListener(view2 -> {
            toggleFavorite(item, favorite);
        });
        TextView watchLater = actionPill("", R.drawable.ic_history);
        updateWatchLaterView(watchLater, this.localCache.isWatchLater(item.id), false);
        watchLater.setOnClickListener(view3 -> toggleWatchLater(item, watchLater));
        TextView comment = actionPill("评论", R.drawable.ic_comment);
        LinearLayout.LayoutParams commentParams = new LinearLayout.LayoutParams(0, dp(36), 1.0f);
        commentParams.leftMargin = dp(6);
        row.addView(comment, commentParams);
        comment.setOnClickListener(view4 -> {
            showCommentDialog(null);
        });
        addTop(article, row, 10);
        addTop(article, watchLater, 6);
        watchLater.getLayoutParams().height = dp(34);
    }

    private void toggleWatchLater(FeedItem item, TextView button) {
        if (item == null || item.id.isEmpty()) return;
        if (this.localCache.isWatchLater(item.id)) {
            this.localCache.removeWatchLater(item.id);
            updateWatchLaterView(button, false, false);
            toast("已从稍后看移除");
            return;
        }
        this.localCache.addWatchLater(item);
        updateWatchLaterView(button, true, true);
        JSONObject body = this.currentDetailBody != null
                ? this.currentDetailBody : this.localCache.detail(item.id);
        if (body == null) {
            List<String> images = detailImageUrls(item, null);
            this.localCache.updateWatchLater(item, images);
            ImageLoader.prefetchOffline(this, images, 260, bytes -> {
                if (!isFinishing() && this.localCache.isWatchLater(item.id)) {
                    updateWatchLaterView(button, true, false);
                }
            });
        } else {
            refreshWatchLaterOffline(item, body, () -> {
                if (!isFinishing() && this.localCache.isWatchLater(item.id)) {
                    updateWatchLaterView(button, true, false);
                }
            });
        }
        toast("已加入稍后看");
    }

    private void updateWatchLaterView(TextView view, boolean saved, boolean loading) {
        if (view == null) return;
        view.setText(loading ? "缓存中" : saved ? "已缓存" : "稍后看");
        int background = saved
                ? activeActionBackground(this.SECONDARY)
                : blend(this.PANEL, this.SECONDARY, this.session.darkMode() ? 0.2f : 0.1f);
        int foreground = saved ? contrast(background) : this.TEXT;
        updatePill(view, background, foreground, R.drawable.ic_history);
    }

    private void refreshWatchLaterOffline(FeedItem item, JSONObject body, Runnable complete) {
        List<String> images = detailImageUrls(item, body);
        this.localCache.updateWatchLater(item, images);
        if (!TextUtils.isEmpty(item.image)) {
            ImageLoader.prefetchOffline(this, Collections.singletonList(item.image), 260, null);
        }
        ImageLoader.prefetchOffline(this, images, detailImageTargetPx(), bytes -> {
            if (complete != null) complete.run();
        });
    }

    private List<String> detailImageUrls(FeedItem item, JSONObject body) {
        Set<String> values = new HashSet<>();
        if (item != null) {
            if (!TextUtils.isEmpty(item.image)) values.add(item.image);
            Collections.addAll(values, item.images);
        }
        JSONObject result = body == null ? null : body.optJSONObject("result");
        JSONObject link = result == null ? null : result.optJSONObject("link");
        JSONArray fallbackImages = link == null ? null : link.optJSONArray("imgs");
        for (RichContent.Block block : RichContent.parse(link, fallbackImages)) {
            if (block.image && !TextUtils.isEmpty(block.value)) values.add(block.value);
            if (values.size() >= 48) break;
        }
        return new ArrayList<>(values);
    }

    private LinearLayout detailArticleSurface() {
        LinearLayout article = vertical(this.BG);
        article.setPadding(dp(4), dp(8), dp(4), dp(12));
        return article;
    }

    private TextView actionPill(String label, int icon) {
        TextView view = text(label, 12.0f, this.TEXT);
        view.setGravity(17);
        view.setTypeface(appRegularTypeface(), 1);
        view.setPadding(dp(7), 0, dp(7), 0);
        updatePill(view, blend(this.PANEL, this.SECONDARY, this.session.darkMode() ? 0.2f : 0.1f), this.TEXT, icon);
        return view;
    }

    private void updateLinkLikeView(TextView view, boolean liked, int likes) {
        int iBlend;
        if (liked) {
            iBlend = activeActionBackground(this.PRIMARY);
        } else {
            iBlend = blend(this.PANEL, this.SECONDARY, this.session.darkMode() ? 0.2f : 0.1f);
        }
        int bg = iBlend;
        int fg = liked ? contrast(bg) : this.TEXT;
        view.setText(String.valueOf(Math.max(0, likes)));
        updatePill(view, bg, fg, R.drawable.ic_thumb_up);
    }

    private void updateFavoriteView(TextView view, boolean favored) {
        int iBlend;
        view.setTag(Boolean.valueOf(favored));
        if (favored) {
            iBlend = blend(this.PANEL, this.SECONDARY, this.session.darkMode() ? 0.48f : 0.22f);
        } else {
            iBlend = blend(this.PANEL, this.SECONDARY, this.session.darkMode() ? 0.2f : 0.1f);
        }
        int bg = iBlend;
        int fg = favored ? this.SECONDARY : this.TEXT;
        view.setText(favored ? "已收藏" : "收藏");
        updatePill(view, bg, fg, R.drawable.ic_bookmark);
    }

    private void updatePill(TextView view, int bg, int fg, int icon) {
        view.setTextColor(fg);
        GradientDrawable drawable = round(bg, 10);
        drawable.setStroke(dp(1), blend(bg, fg, 0.2f));
        Compat.setBackground(view, drawable);
        setLeftIcon(view, icon, fg, 15);
    }

    private boolean linkLiked(JSONObject link, FeedItem fallback) {
        LikeState override = linkLikeOverride(link, fallback);
        if (override != null) {
            return override.liked;
        }
        if (link == null) {
            return fallback != null && fallback.liked;
        }
        if (truthy(link, "is_award_link", "is_award", "liked", "is_liked", "has_award")) {
            return true;
        }
        return truthy(link, "award_state", "like_state");
    }

    private int linkLikes(JSONObject link, FeedItem fallback) {
        LikeState override = linkLikeOverride(link, fallback);
        if (override != null) {
            return override.likes;
        }
        int fallbackLikes = fallback == null ? 0 : fallback.likes;
        return link == null ? fallbackLikes : firstInt(link, fallbackLikes, "link_award_num", "like_num", "award_num", "award_count", "like_count", "liked_num", "total_award_num", "up_num", "up");
    }

    private LikeState linkLikeState(JSONObject link, FeedItem fallback) {
        LikeState override = linkLikeOverride(link, fallback);
        return override != null ? override : new LikeState(linkLiked(link, fallback), linkLikes(link, fallback));
    }

    private LikeState linkLikeOverride(JSONObject link, FeedItem fallback) {
        String[] strArr = new String[4];
        strArr[0] = fallback == null ? "" : fallback.id;
        strArr[1] = link == null ? "" : link.optString("linkid");
        strArr[REPLY_PREVIEW_COUNT] = link == null ? "" : link.optString("link_id");
        strArr[3] = this.currentLinkId;
        String id = first(strArr);
        if (id.isEmpty()) {
            return null;
        }
        return this.linkLikeOverrides.get(id);
    }

    private void rememberLinkLike(String linkId, boolean liked, int likes) {
        if (linkId == null || linkId.isEmpty()) {
            return;
        }
        this.linkLikeOverrides.put(linkId, new LikeState(liked, likes));
    }

    private boolean linkFavored(JSONObject link) {
        if (link == null) {
            return false;
        }
        return truthy(link, "is_favour", "is_favor", "is_fav", "favored", "has_favour", "has_favor");
    }

    private boolean truthy(JSONObject source, String... keys) {
        if (source == null) {
            return false;
        }
        for (String key : keys) {
            if (source.has(key)) {
                Object value = source.opt(key);
                if (value instanceof Boolean) {
                    return ((Boolean) value).booleanValue();
                }
                if (value instanceof Number) {
                    return ((Number) value).intValue() == 1;
                }
                String text = String.valueOf(value).trim();
                if ("1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text)) {
                    return true;
                }
                if ("0".equals(text) || "2".equals(text) || "false".equalsIgnoreCase(text) || "no".equalsIgnoreCase(text)) {
                    return false;
                }
            }
        }
        return false;
    }

    private String hsrc(JSONObject link) {
        if (link == null) {
            return "";
        }
        String value = first(link.optString("h_src"), link.optString("hsrc"));
        if (!value.isEmpty()) {
            return value;
        }
        String shareUrl = link.optString("share_url");
        if (shareUrl.isEmpty()) {
            return "";
        }
        try {
            String value2 = Uri.parse(shareUrl).getQueryParameter("h_src");
            return value2 == null ? "" : value2;
        } catch (Exception e) {
            return "";
        }
    }

    private int firstInt(JSONObject source, int fallback, String... keys) {
        if (source == null) {
            return fallback;
        }
        for (String key : keys) {
            if (source.has(key)) {
                return source.optInt(key, fallback);
            }
        }
        return fallback;
    }

    private boolean requireLogin(String actionName) {
        if (this.session != null && this.session.isLoggedIn()) {
            return true;
        }
        toast("游客模式可浏览，登录后可" + actionName);
        return false;
    }

    private boolean allowWriteAction(String actionName) {
        long now = System.currentTimeMillis();
        if (now < this.writeBlockedUntilAt) {
            toast("官方风控暂时限制" + actionName + "，稍后再试");
            return false;
        }
        if (now - this.lastWriteSubmitAt < 2000L) {
            toast("操作太频繁了，稍等一下");
            return false;
        }
        this.lastWriteSubmitAt = now;
        return true;
    }

    private void noteWriteRiskControl() {
        this.writeBlockedUntilAt = Math.max(this.writeBlockedUntilAt, System.currentTimeMillis() + 600000L);
    }

    private String writeErrorMessage(String actionName, String message) {
        if (isRiskControlWriteError(message)) {
            return "官方风控暂时限制" + actionName + "，先停一会儿再试";
        }
        return message;
    }

    private void toggleLinkLike(final FeedItem item, final TextView view) {
        if (!requireLogin("点赞") || !allowWriteAction("点赞") || item == null || item.id.isEmpty()) {
            return;
        }
        final boolean beforeLiked = item.liked;
        final int beforeLikes = Math.max(0, item.likes);
        final boolean nextLiked = !beforeLiked;
        final int nextLikes = Math.max(0, beforeLikes + (nextLiked ? 1 : -1));
        item.liked = nextLiked;
        item.likes = nextLikes;
        rememberLinkLike(item.id, nextLiked, nextLikes);
        updateLinkLikeView(view, nextLiked, nextLikes);
        updateFeedLike(item.id, nextLiked, nextLikes);
        postLinkLike(item, nextLiked, new ApiClient.Callback() { // from class: com.openzen.heyboxcommunity.MainActivity.15
            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onSuccess(JSONObject body) {
                MainActivity.this.rememberLinkLike(item.id, nextLiked, nextLikes);
                MainActivity.this.updateFeedLike(item.id, nextLiked, nextLikes);
                MainActivity.this.localCache.log("link like ok " + item.id + ": " + beforeLikes + " -> " + nextLikes);
            }

            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onError(String message) {
                item.liked = beforeLiked;
                item.likes = beforeLikes;
                MainActivity.this.rememberLinkLike(item.id, beforeLiked, beforeLikes);
                MainActivity.this.updateLinkLikeView(view, beforeLiked, beforeLikes);
                MainActivity.this.updateFeedLike(item.id, beforeLiked, beforeLikes);
                MainActivity.this.toast("点赞失败" + MainActivity.this.writeErrorMessage("点赞", message));
            }
        });
    }

    private void toggleFeedLike(final FeedItem item) {
        if (!requireLogin("点赞") || !allowWriteAction("点赞") || item == null || item.id.isEmpty()) {
            return;
        }
        final boolean beforeLiked = item.liked;
        final int beforeLikes = Math.max(0, item.likes);
        final boolean nextLiked = !beforeLiked;
        final int nextLikes = Math.max(0, beforeLikes + (nextLiked ? 1 : -1));
        item.liked = nextLiked;
        item.likes = nextLikes;
        rememberLinkLike(item.id, nextLiked, nextLikes);
        updateFeedLike(item.id, nextLiked, nextLikes);
        postLinkLike(item, nextLiked, new ApiClient.Callback() { // from class: com.openzen.heyboxcommunity.MainActivity.16
            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onSuccess(JSONObject body) {
                MainActivity.this.rememberLinkLike(item.id, nextLiked, nextLikes);
                MainActivity.this.updateFeedLike(item.id, nextLiked, nextLikes);
                MainActivity.this.localCache.log("feed like ok " + item.id + ": " + beforeLikes + " -> " + nextLikes);
            }

            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onError(String message) {
                item.liked = beforeLiked;
                item.likes = beforeLikes;
                MainActivity.this.rememberLinkLike(item.id, beforeLiked, beforeLikes);
                MainActivity.this.updateFeedLike(item.id, beforeLiked, beforeLikes);
                MainActivity.this.toast("点赞失败" + MainActivity.this.writeErrorMessage("点赞", message));
            }
        });
    }

    private void toggleFavorite(FeedItem item, final TextView view) {
        if (!requireLogin("收藏") || !allowWriteAction("收藏") || item == null || item.id.isEmpty()) {
            return;
        }
        Object tag = view.getTag();
        final boolean favored = (tag instanceof Boolean) && ((Boolean) tag).booleanValue();
        final boolean nextFavored = !favored;
        updateFavoriteView(view, nextFavored);
        view.setEnabled(false);
        postFavorite(item, nextFavored, new ApiClient.Callback() { // from class: com.openzen.heyboxcommunity.MainActivity.17
            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onSuccess(JSONObject body) {
                view.setEnabled(true);
                MainActivity.this.toast(nextFavored ? "已收藏" : "已取消收藏");
            }

            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onError(String message) {
                view.setEnabled(true);
                MainActivity.this.updateFavoriteView(view, favored);
                MainActivity.this.toast("收藏操作失败" + MainActivity.this.writeErrorMessage("收藏", message));
            }
        });
    }

    private void postLinkLike(FeedItem item, boolean nextLiked, ApiClient.Callback callback) {
        runWriteFallback(new WriteStep[]{writeStep("like-web", cb -> {
            this.api.postForm(EndpointProvider.awardLink(), Collections.emptyMap(), awardBody(item.id, nextLiked), cb);
        }), writeStep("like-web-hsrc", cb2 -> {
            this.api.postForm(EndpointProvider.awardLink(), queryHsrc(item), awardBody(item.id, nextLiked), cb2);
        })}, callback);
    }

    private void postFavorite(FeedItem item, boolean nextFavored, ApiClient.Callback callback) {
        runWriteFallback(new WriteStep[]{writeStep("favorite-web", cb -> {
            this.api.postForm(EndpointProvider.favourLink(), Collections.emptyMap(), favouriteBody(item.id, nextFavored ? "1" : "2", false), cb);
        }), writeStep("favorite-web-folder", cb2 -> {
            this.api.postForm(EndpointProvider.favourLink(), Collections.emptyMap(), favouriteBody(item.id, nextFavored ? "1" : "2", true), cb2);
        }), writeStep("favorite-web-hsrc", cb3 -> {
            this.api.postForm(EndpointProvider.favourLink(), queryHsrc(item), favouriteBody(item.id, nextFavored ? "1" : "2", false), cb3);
        }), writeStep("favorite-web-newsid", cb4 -> {
            this.api.postForm(EndpointProvider.favourLink(), queryHsrc(item), favouriteBody(item.id, nextFavored ? "1" : "2", true, true), cb4);
        }), writeStep("favorite-web-no-hsrc", cb5 -> {
            this.api.postForm(EndpointProvider.favourLink(), Collections.emptyMap(), favouriteBody(item.id, nextFavored ? "1" : "2", true), cb5);
        })}, callback);
    }

    private Map<String, String> awardBody(String linkId, boolean liked) {
        Map<String, String> body = new HashMap<>();
        body.put("link_id", linkId);
        body.put("award_type", liked ? "1" : "0");
        return body;
    }

    private Map<String, String> linkIdBody(String linkId) {
        Map<String, String> body = new HashMap<>();
        body.put("link_id", linkId);
        return body;
    }

    private Map<String, String> favouriteBody(String linkId, String type, boolean includeFolder) {
        return favouriteBody(linkId, type, includeFolder, false);
    }

    private Map<String, String> favouriteBody(String linkId, String type, boolean includeFolder, boolean includeNewsId) {
        Map<String, String> body = linkIdBody(linkId);
        body.put("favour_type", type);
        if (!this.session.userId().isEmpty()) {
            body.put(SecureStrings.userid(), this.session.userId());
        }
        if (includeNewsId) {
            body.put("newsid", linkId);
        }
        if (includeFolder) {
            body.put("folder_id", "");
        }
        return body;
    }

    private Map<String, String> favouriteBodyOfficial(String linkId, boolean favored) {
        Map<String, String> body = linkIdBody(linkId);
        body.put("newsid", "");
        body.put("favour_type", favored ? "1" : "2");
        body.put("folder_id", "");
        return body;
    }

    private Map<String, String> queryHsrc(FeedItem item) {
        Map<String, String> query = new HashMap<>();
        String value = item == null ? "" : item.hsrc;
        if (value.isEmpty() && item != null && item.id.equals(this.currentLinkId)) {
            value = this.currentLinkHsrc;
        }
        if (value.isEmpty() && item == null) {
            value = this.currentLinkHsrc;
        }
        if (!value.isEmpty()) {
            query.put("h_src", value);
        }
        return query;
    }

    private Map<String, String> commentCreateQuery() {
        Map<String, String> query = queryHsrc(this.currentDetailItem);
        if (!this.currentAuthCode.isEmpty()) {
            query.put("auth_code", this.currentAuthCode);
        }
        return query;
    }

    private WriteStep writeStep(String name, WriteRequest request) {
        return new WriteStep(name, request);
    }

    private void runWriteFallback(WriteStep[] requests, ApiClient.Callback callback) {
        if (this.localCache != null) {
            this.localCache.log("write cookie keys: " + this.session.authCookieKeysForLog());
        }
        runWriteFallback(requests, 0, "", "", false, callback);
    }

    private void runWriteFallback(final WriteStep[] requests, final int index, String lastError, final String importantError, final boolean tokenRetried, final ApiClient.Callback callback) {
        if (index >= requests.length || index >= MAX_WRITE_FALLBACK_ATTEMPTS) {
            String message = !importantError.isEmpty() ? importantError : lastError;
            callback.onError((message == null || message.isEmpty()) ? "接口未返回可用结果" : message);
            return;
        }
        WriteStep step = requests[index];
        final String label = (step == null || step.name == null || step.name.isEmpty()) ? "step-" + index : step.name;
        if (this.localCache != null) {
            this.localCache.log("write fallback " + index + " start: " + label);
        }
        if (step == null || step.request == null) {
            runWriteFallback(requests, index + 1, lastError, importantError, tokenRetried, callback);
        } else {
            step.request.start(new ApiClient.Callback() { // from class: com.openzen.heyboxcommunity.MainActivity.18
                @Override // com.openzen.heyboxcommunity.ApiClient.Callback
                public void onSuccess(JSONObject body) {
                    if (MainActivity.this.localCache != null) {
                        MainActivity.this.localCache.log("write fallback " + index + " ok: " + label);
                    }
                    callback.onSuccess(body);
                }

                @Override // com.openzen.heyboxcommunity.ApiClient.Callback
                public void onError(final String message2) {
                    if (MainActivity.this.localCache != null) {
                        MainActivity.this.localCache.log("write fallback " + index + " failed: " + label + ": " + message2);
                    }
                    if (MainActivity.this.isRiskControlWriteError(message2)) {
                        MainActivity.this.noteWriteRiskControl();
                        if (MainActivity.this.localCache != null) {
                            MainActivity.this.localCache.log("write risk control stop fallback: " + label);
                        }
                        callback.onError(message2);
                        return;
                    }
                    boolean tokenError = MainActivity.this.isTokenWriteError(message2);
                    boolean loginError = MainActivity.this.isLoginWriteError(message2);
                    String nextImportant = importantError;
                    if (MainActivity.this.isParameterWriteError(message2) || loginError) {
                        nextImportant = message2;
                    }
                    final String retryImportant = nextImportant;
                    if ((tokenError || loginError) && !tokenRetried && MainActivity.this.writeTokenProvider != null) {
                        if (MainActivity.this.localCache != null) {
                            MainActivity.this.localCache.log("write auth failed, refreshing token then retrying chain");
                        }
                        MainActivity.this.writeTokenProvider.refresh(new WriteTokenProvider.Callback() { // from class: com.openzen.heyboxcommunity.MainActivity.18.1
                            @Override // com.openzen.heyboxcommunity.WriteTokenProvider.Callback
                            public void onReady() {
                                MainActivity.this.scheduleWriteFallback(requests, 0, "", "", true, callback);
                            }

                            @Override // com.openzen.heyboxcommunity.WriteTokenProvider.Callback
                            public void onError(String tokenMessage) {
                                if (MainActivity.this.localCache != null) {
                                    MainActivity.this.localCache.log("write token refresh failed, continue fallback: " + tokenMessage);
                                }
                                MainActivity.this.scheduleWriteFallback(requests, index + 1, message2, retryImportant, true, callback);
                            }
                        });
                    } else if (tokenError) {
                        if (MainActivity.this.localCache != null) {
                            MainActivity.this.localCache.log("write token error after refresh, continue fallback");
                        }
                        MainActivity.this.scheduleWriteFallback(requests, index + 1, message2, nextImportant, tokenRetried, callback);
                    } else {
                        if (loginError) {
                            if (MainActivity.this.localCache != null) {
                                MainActivity.this.localCache.log("write login error after token retry, continue fallback");
                            }
                            MainActivity.this.scheduleWriteFallback(requests, index + 1, message2, nextImportant, tokenRetried, callback);
                            return;
                        }
                        MainActivity.this.scheduleWriteFallback(requests, index + 1, message2, nextImportant, tokenRetried, callback);
                    }
                }
            });
        }
    }

    private void scheduleWriteFallback(final WriteStep[] requests, final int nextIndex, final String lastError, final String importantError, final boolean tokenRetried, final ApiClient.Callback callback) {
        if (nextIndex >= requests.length || nextIndex >= MAX_WRITE_FALLBACK_ATTEMPTS) {
            String message = !importantError.isEmpty() ? importantError : lastError;
            callback.onError((message == null || message.isEmpty()) ? "接口未返回可用结果" : message);
            return;
        }
        if (this.localCache != null) {
            this.localCache.log("write fallback delay next=" + nextIndex + " ms=" + WRITE_FALLBACK_DELAY_MS);
        }
        this.handler.postDelayed(() -> MainActivity.this.runWriteFallback(requests, nextIndex, lastError, importantError, tokenRetried, callback), WRITE_FALLBACK_DELAY_MS);
    }

    private boolean isTokenWriteError(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.US);
        return lower.contains("lack_token") || lower.contains("x_xhh_tokenid") || lower.contains("tokenid") || lower.contains("token") || message.contains("令牌");
    }

    private boolean isLoginWriteError(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.US);
        return lower.contains("login") || lower.contains("relogin") || message.contains("登录") || message.contains("重新登录");
    }

    private boolean isRequestValidationError(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.US);
        return message.contains("验证参数") || message.contains("参数") || message.contains("非法请求") || lower.contains("param") || lower.contains("sign") || isParameterWriteError(message);
    }

    private String authExpiredMessageIfNeeded(String message, String source) {
        if (!isLoginWriteError(message)) {
            return message;
        }
        if (this.session != null && this.session.hasOfficialProviderAuth()) {
            if (this.localCache != null) {
                this.localCache.log("auth login error but official provider auth exists source=" + source + " cookieKeys=" + this.session.authCookieKeysForLog());
            }
            return message;
        }
        if (this.session != null && this.session.isLoggedIn() && this.localCache != null) {
            this.localCache.log("auth login error kept session source=" + source + " cookieKeys=" + this.session.authCookieKeysForLog());
            return "登录可能已失效，请重新扫码登录";
        }
        return "登录可能已失效，请重新扫码登录";
    }

    private boolean isParameterWriteError(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.US);
        return message.contains("验证参数") || message.contains("参数") || message.contains("非法请求") || lower.contains("param") || lower.contains("sign");
    }

    private boolean isRiskControlWriteError(String message) {
        if (message == null) {
            return false;
        }
        return message.contains("无法使用该功能")
                || message.contains("有风险")
                || message.contains("风险")
                || message.contains("风控");
    }

    private void updateFeedLike(String linkId, boolean liked, int likes) {
        if (linkId == null || linkId.isEmpty()) {
            return;
        }
        Iterator<FeedItem> it = this.feed.iterator();
        while (true) {
            if (!it.hasNext()) {
                break;
            }
            FeedItem candidate = it.next();
            if (linkId.equals(candidate.id)) {
                candidate.liked = liked;
                candidate.likes = likes;
                break;
            }
        }
        this.localCache.saveFeed(this.feed);
        if (this.feedAdapter != null) {
            this.feedAdapter.notifyDataSetChanged();
        }
    }

    private void addAuthorHeader(LinearLayout article, JSONObject link, JSONObject user, String fallbackAuthor) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setGravity(16);
        ImageView avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Compat.setBackground(avatar, round(this.session.darkMode() ? Color.rgb(50, 53, 56) : Color.rgb(226, 229, 232), 18));
        Compat.clipToOutline(avatar);
        linearLayout.addView(avatar, new LinearLayout.LayoutParams(dp(36), dp(36)));
        String avatarUrl = user == null ? "" : user.optString("avatar", user.optString("avartar"));
        if (!this.session.noImage() && !avatarUrl.isEmpty()) {
            ImageLoader.intoPlain(avatar, avatarUrl, 96);
        }
        LinearLayout linearLayoutVertical = vertical(0);
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setGravity(16);
        String nameValue = user == null ? fallbackAuthor : first(user.optString("username"), user.optString("nickname"), user.optString("name"), fallbackAuthor);
        TextView name = text(nameValue.isEmpty() ? "小黑盒用户" : nameValue, 13.0f, this.TEXT);
        name.setTypeface(appRegularTypeface(), 1);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setMaxWidth(dp(168));
        nameRow.addView(name, new LinearLayout.LayoutParams(-2, -2));
        int level = userLevel(user);
        if (level > 0) {
            TextView badge = text("Lv." + level, 8.0f, -1);
            badge.setGravity(17);
            badge.setTypeface(appRegularTypeface(), 1);
            Compat.setBackground(badge, round(Color.rgb(210, 72, 218), 3));
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(32), dp(15));
            badgeParams.leftMargin = dp(REPLY_PAGE_SIZE);
            nameRow.addView(badge, badgeParams);
        }
        linearLayoutVertical.addView(nameRow);
        long createdAt = link == null ? 0L
                : link.optLong("create_at", link.optLong("create_time", link.optLong("createtime")));
        String published = createdAt > 0 ? commentDisplayTime(createdAt) : "";
        String signature = user == null ? "" : first(user.optString("signature"), user.optString("desc"));
        String authorMeta = published.isEmpty() ? signature
                : (signature.isEmpty() ? published : published + " · " + signature);
        if (!authorMeta.isEmpty()) {
            TextView desc = text(authorMeta, 10.0f, this.MUTED);
            desc.setSingleLine(true);
            desc.setEllipsize(TextUtils.TruncateAt.END);
            addTop(linearLayoutVertical, desc, 1);
        }
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        copyParams.leftMargin = dp(9);
        linearLayout.addView(linearLayoutVertical, copyParams);
        int followBg = this.session.darkMode() ? blend(this.PANEL, this.TEXT, 0.12f) : blend(this.PANEL, this.TEXT, 0.06f);
        TextView follow = text("+ 关注", 11.0f, this.TEXT);
        follow.setGravity(17);
        follow.setTypeface(appRegularTypeface(), 1);
        Compat.setBackground(follow, round(followBg, REPLY_PAGE_SIZE));
        String targetUserId = authorUserId(link, user);
        View.OnClickListener openUser = view -> showUserSpace(targetUserId, nameValue, avatarUrl);
        avatar.setOnClickListener(openUser);
        linearLayoutVertical.setOnClickListener(openUser);
        updateFollowView(follow, isFollowing(link, user));
        linearLayout.addView(follow, new LinearLayout.LayoutParams(dp(62), dp(NAV_BAR_HEIGHT_DP)));
        follow.setOnClickListener(view -> {
            toggleFollow(follow, link, user, targetUserId);
        });
        addTop(article, linearLayout, article.getChildCount() == 0 ? 0 : 14);
    }

    private void updateFollowView(TextView follow, boolean following) {
        int iBlend;
        int iBlend2;
        follow.setTag(Boolean.valueOf(following));
        if (following) {
            iBlend = activeFollowBackground();
        } else {
            iBlend = blend(this.PANEL, this.TEXT, this.session.darkMode() ? 0.12f : 0.06f);
        }
        int bg = iBlend;
        int fg = following ? contrast(bg) : this.TEXT;
        follow.setText(following ? "已关注" : "+ 关注");
        follow.setTextColor(fg);
        GradientDrawable drawable = round(bg, 7);
        int iDp = dp(1);
        if (following) {
            iBlend2 = blend(bg, fg, 0.26f);
        } else {
            iBlend2 = blend(bg, this.SECONDARY, this.session.darkMode() ? 0.32f : 0.18f);
        }
        drawable.setStroke(iDp, iBlend2);
        Compat.setBackground(follow, drawable);
    }

    private void showUserSpace(String userId, String fallbackName, String fallbackAvatar) {
        if (TextUtils.isEmpty(userId)) {
            toast("没有获取到用户 ID");
            return;
        }
        if ("detail".equals(this.screen)) {
            this.userSpaceReturnItem = this.currentDetailItem;
            this.userSpaceReturnScreen = this.detailReturn;
        }
        activate("user_space");
        this.title.setText("动态");
        this.action.setVisibility(4);
        this.leading.setOnClickListener(view -> {
            returnFromUserSpace();
        });
        this.content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = vertical(this.BG);
        page.setPadding(0, 0, 0, dp(12));
        scroll.addView(page);
        LinearLayout profile = userSpaceHeader(fallbackName, userId, fallbackAvatar, null);
        page.addView(profile);
        final List<FeedItem> userItems = new ArrayList<>();
        final boolean[] articlesOnly = {false};
        TextView status = text("正在加载动态", 12.0f, this.MUTED);
        status.setPadding(dp(16), dp(10), dp(16), dp(4));
        LinearLayout events = vertical(this.BG);
        Runnable render = () -> renderUserEvents(userItems, events, status, articlesOnly[0]);
        LinearLayout tabs = userSpaceTabs(articlesOnly, render);
        page.addView(tabs);
        page.addView(status);
        page.addView(events);
        this.content.addView(scroll, match());
        loadUserProfile(userId, fallbackName, fallbackAvatar, profile);
        loadUserEvents(userId, userItems, events, status, articlesOnly);
    }

    private LinearLayout userSpaceHeader(String nameValue, String userId, String avatarUrl, JSONObject user) {
        LinearLayout profile = vertical(this.PANEL);
        profile.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(16);
        ImageView avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Compat.setBackground(avatar, round(this.session.darkMode() ? Color.rgb(46, 46, 46) : Color.rgb(235, 235, 235), 34));
        Compat.clipToOutline(avatar);
        top.addView(avatar, new LinearLayout.LayoutParams(dp(74), dp(74)));
        String resolvedAvatar = first(user == null ? "" : user.optString("avatar", user.optString("avartar")), avatarUrl);
        if (!this.session.noImage() && !resolvedAvatar.isEmpty()) {
            ImageLoader.intoPlain(avatar, resolvedAvatar, 160);
        }
        LinearLayout copy = vertical(0);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        copyParams.leftMargin = dp(14);
        top.addView(copy, copyParams);
        String resolvedName = first(user == null ? "" : user.optString("username", user.optString("nickname", user.optString("name"))), nameValue, "小黑盒用户");
        TextView name = text(resolvedName, 20.0f, this.TEXT);
        name.setTypeface(appRegularTypeface(), 1);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(name);
        String signature = user == null ? "" : first(user.optString("signature"), user.optString("desc"));
        if (!signature.isEmpty()) {
            TextView desc = text(signature, 12.0f, this.MUTED);
            desc.setLineSpacing(0.0f, 1.18f);
            addTop(copy, desc, 7);
        } else {
            addTop(copy, text("ID " + userId, 11.0f, this.MUTED), 7);
        }
        profile.addView(top);
        JSONObject bbs = user == null ? null : user.optJSONObject("bbs_info");
        LinearLayout stats = new LinearLayout(this);
        stats.setGravity(17);
        addUserStat(stats, firstJsonInt(user, bbs, "follow_num", "following_num", "attention_num"), "关注");
        addUserStat(stats, firstJsonInt(user, bbs, "fan_num", "fans_num", "follower_num"), "粉丝");
        addUserStat(stats, awardAndFavoriteCount(user, bbs), "获赞与收藏");
        addTop(profile, stats, 16);
        View divider = new View(this);
        divider.setBackgroundColor(this.themeTokens == null ? this.MUTED : this.themeTokens.hairline);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, 1);
        dividerParams.topMargin = dp(14);
        profile.addView(divider, dividerParams);
        return profile;
    }

    private LinearLayout userSpaceTabs(boolean[] articlesOnly, Runnable render) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(16), dp(10), dp(16), dp(8));
        row.setBackgroundColor(this.PANEL);
        TextView dynamic = userSpaceTab("动态", !articlesOnly[0]);
        TextView articles = userSpaceTab("投稿", articlesOnly[0]);
        dynamic.setOnClickListener(view -> {
            articlesOnly[0] = false;
            updateUserSpaceTabs(row, false);
            render.run();
        });
        articles.setOnClickListener(view -> {
            articlesOnly[0] = true;
            updateUserSpaceTabs(row, true);
            render.run();
        });
        row.addView(dynamic, new LinearLayout.LayoutParams(0, dp(42), 1.0f));
        row.addView(articles, new LinearLayout.LayoutParams(0, dp(42), 1.0f));
        return row;
    }

    private TextView userSpaceTab(String label, boolean active) {
        TextView view = text(label, 14.0f, active ? this.TEXT : this.MUTED);
        view.setGravity(17);
        view.setTypeface(appRegularTypeface(), active ? 1 : 0);
        Compat.setBackground(view, round(active
                ? (this.session.darkMode() ? Color.rgb(34, 34, 34) : Color.WHITE)
                : (this.session.darkMode() ? Color.rgb(24, 24, 24) : Color.rgb(246, 247, 248)), 4));
        return view;
    }

    private void updateUserSpaceTabs(LinearLayout row, boolean articlesOnly) {
        for (int i = 0; i < row.getChildCount(); i++) {
            TextView tab = (TextView) row.getChildAt(i);
            boolean active = articlesOnly ? i == 1 : i == 0;
            tab.setTextColor(active ? this.TEXT : this.MUTED);
            tab.setTypeface(appRegularTypeface(), active ? 1 : 0);
            Compat.setBackground(tab, round(active
                    ? (this.session.darkMode() ? Color.rgb(34, 34, 34) : Color.WHITE)
                    : (this.session.darkMode() ? Color.rgb(24, 24, 24) : Color.rgb(246, 247, 248)), 4));
        }
    }

    private void addUserStat(LinearLayout row, int value, String label) {
        LinearLayout item = vertical(0);
        item.setGravity(17);
        TextView number = text(String.valueOf(Math.max(0, value)), 20.0f, this.TEXT);
        number.setGravity(17);
        TextView caption = text(label, 11.0f, this.MUTED);
        caption.setGravity(17);
        item.addView(number);
        item.addView(caption);
        row.addView(item, new LinearLayout.LayoutParams(0, -2, 1.0f));
    }

    private int awardAndFavoriteCount(JSONObject user, JSONObject bbs) {
        int combined = firstJsonInt(user, bbs, "award_favour_num", "award_favorite_num",
                "award_collect_num", "up_fav_num", "liked_fav_num", "total_award_fav_num");
        if (combined > 0) return combined;
        int awards = firstJsonInt(user, bbs, "up_num", "award_num", "award_count",
                "like_num", "liked_num", "praise_num", "praise_count", "link_award_num");
        int favorites = firstJsonInt(user, bbs, "fav_num", "favor_num", "favour_num",
                "favorite_num", "collect_num", "collection_num", "collected_num");
        return awards + favorites;
    }

    private int firstJsonInt(JSONObject first, JSONObject second, String... keys) {
        int value = firstJsonInt(first, keys);
        return value != 0 ? value : firstJsonInt(second, keys);
    }

    private int firstJsonInt(JSONObject object, String... keys) {
        if (object == null) return 0;
        for (String key : keys) {
            if (object.has(key)) return object.optInt(key, 0);
        }
        return 0;
    }

    private void loadUserProfile(String userId, String fallbackName, String fallbackAvatar,
                                 LinearLayout profile) {
        this.api.get(EndpointProvider.profile(), Collections.singletonMap(SecureStrings.userid(), userId), new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                JSONObject user = profileUser(body);
                if (user == null || profile.getParent() == null) {
                    return;
                }
                ViewGroup parent = (ViewGroup) profile.getParent();
                int index = parent.indexOfChild(profile);
                parent.removeView(profile);
                parent.addView(userSpaceHeader(fallbackName, userId, fallbackAvatar, user), index);
            }

            @Override public void onError(String message) {
                MainActivity.this.localCache.log("user profile failed: " + message);
            }
        });
    }

    private JSONObject profileUser(JSONObject body) {
        JSONObject result = body == null ? null : body.optJSONObject("result");
        if (result == null) return null;
        JSONObject account = result.optJSONObject("account_detail");
        if (account != null) return account;
        JSONObject user = result.optJSONObject("user");
        return user != null ? user : result.optJSONObject("profile");
    }

    private void loadUserEvents(String userId, List<FeedItem> items, LinearLayout events,
                                TextView status, boolean[] articlesOnly) {
        Map<String, String> params = new HashMap<>();
        params.put(SecureStrings.userid(), userId);
        params.put("user_id", userId);
        params.put("list_type", "moment");
        params.put("offset", "0");
        this.api.get(EndpointProvider.profileEvents(), params, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                items.clear();
                items.addAll(parseUserEvents(body));
                renderUserEvents(items, events, status, articlesOnly[0]);
            }

            @Override public void onError(String message) {
                status.setText("动态加载失败");
                MainActivity.this.localCache.log("user events failed: " + message);
            }
        });
    }

    private void renderUserEvents(List<FeedItem> items, LinearLayout events,
                                  TextView status, boolean articlesOnly) {
        events.removeAllViews();
        int count = 0;
        for (FeedItem item : items) {
            if (articlesOnly && !item.article) {
                continue;
            }
            events.addView(userEventCard(item));
            count++;
        }
        status.setText(count == 0 ? (articlesOnly ? "暂无投稿" : "暂无动态") : (articlesOnly ? "投稿" : "动态"));
        addBottomNavSafeSpace(events);
    }

    private List<FeedItem> parseUserEvents(JSONObject body) {
        JSONObject result = body == null ? null : body.optJSONObject("result");
        JSONArray array = firstArray(result, "links", "list", "events", "moments", "data");
        List<FeedItem> items = new ArrayList<>();
        if (array == null) return items;
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) continue;
            JSONObject link = object.optJSONObject("link");
            items.add(FeedItem.from(link == null ? object : link));
        }
        return items;
    }

    private JSONArray firstArray(JSONObject object, String... keys) {
        if (object == null) return null;
        for (String key : keys) {
            JSONArray array = object.optJSONArray(key);
            if (array != null) return array;
            JSONObject nested = object.optJSONObject(key);
            if (nested != null) {
                JSONArray nestedArray = firstArray(nested, "links", "list", "events", "moments", "data");
                if (nestedArray != null) return nestedArray;
            }
        }
        return null;
    }

    private View userEventCard(FeedItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(16);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setBackgroundColor(this.PANEL);
        LinearLayout copy = vertical(0);
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1.0f));
        if (item.pinned) {
            TextView pinned = text("置顶", 10.0f, contrast(this.SECONDARY));
            pinned.setGravity(17);
            pinned.setTypeface(appRegularTypeface(), 1);
            Compat.setBackground(pinned, round(this.SECONDARY, 4));
            LinearLayout.LayoutParams pinnedParams = new LinearLayout.LayoutParams(dp(42), dp(20));
            copy.addView(pinned, pinnedParams);
        }
        String titleText = RichContent.plainText(first(item.title, item.description, "无标题内容"));
        TextView titleView = text(titleText, 14.0f, this.TEXT);
        titleView.setTypeface(appRegularTypeface(), 1);
        titleView.setMaxLines(2);
        EmojiRenderer.set(titleView, titleText, this.session.darkMode());
        addTop(copy, titleView, item.pinned ? 5 : 0);
        if (!TextUtils.isEmpty(item.description) && !item.description.equals(item.title)) {
            TextView desc = text(RichContent.plainText(item.description), 11.0f, this.MUTED);
            desc.setMaxLines(2);
            EmojiRenderer.set(desc, RichContent.plainText(item.description), this.session.darkMode());
            addTop(copy, desc, 5);
        }
        addTop(copy, text("" + item.likes + "   评论 " + item.comments, 10.0f, this.MUTED), 7);
        if (!this.session.noImage() && !TextUtils.isEmpty(item.image)) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Compat.setBackground(image, round(this.session.darkMode() ? Color.rgb(42, 42, 42) : Color.rgb(238, 238, 238), 6));
            Compat.clipToOutline(image);
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(118), dp(72));
            imageParams.leftMargin = dp(12);
            card.addView(image, imageParams);
            ImageLoader.intoPlain(image, item.image, 260);
        }
        card.setOnClickListener(view -> showDetail(item));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        card.setLayoutParams(params);
        return card;
    }

    private int activeFollowBackground() {
        return this.session.darkMode() ? blend(this.SECONDARY, -1, 0.1f) : this.SECONDARY;
    }

    private int activeActionBackground(int accent) {
        return this.session.darkMode() ? blend(accent, -1, 0.1f) : accent;
    }

    private void toggleFollow(final TextView follow, final JSONObject link, final JSONObject user, String targetUserId) {
        if (requireLogin("关注")) {
            if (!allowWriteAction("关注")) {
                return;
            }
            if (targetUserId == null || targetUserId.isEmpty()) {
                toast("没有获取到用户 ID");
                return;
            }
            if (targetUserId.equals(this.session.userId())) {
                toast("不能关注自己");
                return;
            }
            Object tag = follow.getTag();
            final boolean before = (tag instanceof Boolean) && ((Boolean) tag).booleanValue();
            final int beforeStatus = followStatus(link, user);
            final boolean next = !before;
            updateFollowView(follow, next);
            follow.setClickable(false);
            follow.setAlpha(0.92f);
            postFollow(targetUserId, next, new ApiClient.Callback() { // from class: com.openzen.heyboxcommunity.MainActivity.19
                @Override // com.openzen.heyboxcommunity.ApiClient.Callback
                public void onSuccess(JSONObject body) {
                    MainActivity.this.applyFollowState(link, user, MainActivity.this.nextFollowStatus(beforeStatus, next));
                    follow.setClickable(true);
                    follow.setAlpha(1.0f);
                    MainActivity.this.updateFollowView(follow, next);
                    MainActivity.this.toast(next ? "已关注" : "已取消关注");
                }

                @Override // com.openzen.heyboxcommunity.ApiClient.Callback
                public void onError(String message) {
                    follow.setClickable(true);
                    follow.setAlpha(1.0f);
                    MainActivity.this.updateFollowView(follow, before);
                    MainActivity.this.toast("关注操作失败" + MainActivity.this.writeErrorMessage("关注", message));
                }
            });
        }
    }

    private void postFollow(String targetUserId, boolean next, ApiClient.Callback callback) {
        String path = next ? EndpointProvider.followUser() : EndpointProvider.unfollowUser();
        runWriteFallback(new WriteStep[]{writeStep("follow-web", cb -> {
            this.api.postForm(path, Collections.emptyMap(), userBody(targetUserId, false, false), cb);
        }), writeStep("follow-web-compact", cb2 -> {
            this.api.postForm(path, Collections.emptyMap(), userBody(targetUserId, true, false), cb2);
        }), writeStep("follow-web-hsrc", cb3 -> {
            this.api.postForm(path, queryHsrc(this.currentDetailItem), userBody(targetUserId, false, false), cb3);
        }), writeStep("follow-web-state", cb4 -> {
            this.api.postForm(path, Collections.emptyMap(), userBody(targetUserId, false, next), cb4);
        })}, callback);
    }

    private Map<String, String> userBody(String value, boolean compact, boolean includeState) {
        Map<String, String> body = new HashMap<>();
        body.put("following_id", value);
        if (!compact && this.currentLinkId != null && !this.currentLinkId.isEmpty()) {
            body.put("link_id", this.currentLinkId);
        }
        if (includeState) {
            body.put("follows", "1");
        }
        return body;
    }

    private boolean isFollowing(JSONObject link, JSONObject user) {
        int status = followStatus(link, user);
        return status >= 0 ? status == 1 || status == 3 : truthy(link, "is_follow", "is_following", "followed") || truthy(user, "is_follow", "is_following", "followed");
    }

    private int followStatus(JSONObject link, JSONObject user) {
        int linkStatus = followStatusValue(link);
        return linkStatus >= 0 ? linkStatus : followStatusValue(user);
    }

    private int followStatusValue(JSONObject source) {
        if (source == null) {
            return -1;
        }
        String[] keys = {"follow_status", "follow_state", "follow_state_v2", "is_follow", "is_following", "followed"};
        for (String key : keys) {
            if (source.has(key)) {
                Object value = source.opt(key);
                if (value instanceof Boolean) {
                    return ((Boolean) value).booleanValue() ? 1 : 0;
                }
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                }
                String text = String.valueOf(value).trim();
                if (text.isEmpty()) {
                    continue;
                } else {
                    if ("true".equalsIgnoreCase(text) || "followed".equalsIgnoreCase(text) || "following".equalsIgnoreCase(text)) {
                        return 1;
                    }
                    if ("mutual".equalsIgnoreCase(text)) {
                        return 3;
                    }
                    if ("false".equalsIgnoreCase(text) || "none".equalsIgnoreCase(text) || "unfollowed".equalsIgnoreCase(text)) {
                        return 0;
                    }
                    try {
                        return Integer.parseInt(text);
                    } catch (NumberFormatException e) {
                    }
                }
            }
        }
        return -1;
    }

    private int nextFollowStatus(int beforeStatus, boolean following) {
        if (following) {
            return beforeStatus == REPLY_PREVIEW_COUNT ? 3 : 1;
        }
        if (beforeStatus == 3) {
            return REPLY_PREVIEW_COUNT;
        }
        return 0;
    }

    private void applyFollowState(JSONObject link, JSONObject user, int status) {
        boolean following = status == 1 || status == 3;
        putFollowState(link, status, following);
        putFollowState(user, status, following);
    }

    private void putFollowState(JSONObject target, int status, boolean following) {
        if (target == null) {
            return;
        }
        try {
            target.put("follow_status", status);
            target.put("follow_state", status);
            target.put("is_follow", following ? 1 : 0);
            target.put("is_following", following);
            target.put("followed", following);
        } catch (Exception e) {
        }
    }

    private String authorUserId(JSONObject link, JSONObject user) {
        String[] strArr = new String[8];
        strArr[0] = link == null ? "" : link.optString(SecureStrings.userid());
        strArr[1] = link == null ? "" : link.optString(SecureStrings.userId());
        strArr[REPLY_PREVIEW_COUNT] = link == null ? "" : link.optString(SecureStrings.heyboxId());
        strArr[3] = link == null ? "" : link.optString("heyboxid");
        strArr[4] = link == null ? "" : link.optString("uid");
        strArr[REPLY_PAGE_SIZE] = link == null ? "" : link.optString("account_id");
        strArr[6] = link == null ? "" : link.optString("id");
        strArr[7] = userId(user);
        return first(strArr);
    }

    private String userId(JSONObject user) {
        return user == null ? "" : first(user.optString(SecureStrings.userid()), user.optString(SecureStrings.userId()), user.optString(SecureStrings.heyboxId()), user.optString("heyboxid"), user.optString("uid"), user.optString("account_id"), user.optString("id"));
    }

    private int userLevel(JSONObject user) {
        if (user == null) {
            return 0;
        }
        JSONObject levelInfo = user.optJSONObject("level_info");
        return levelInfo != null ? levelInfo.optInt("level", 0) : user.optInt("level", 0);
    }

    private void addRichContent(LinearLayout parent, String source, JSONArray fallbackImages) {
        addRichContent(parent, RichContent.parse(source, fallbackImages), true);
    }

    private void addRichContent(LinearLayout parent, JSONObject link, String fallback, JSONArray fallbackImages) {
        List<RichContent.Block> list;
        if (link == null) {
            list = RichContent.parse(fallback, fallbackImages);
        } else {
            list = RichContent.parse(link, fallbackImages);
        }
        List<RichContent.Block> blocks = list;
        if (!RichContent.hasReadableText(blocks) && fallback != null && !fallback.isEmpty()) {
            List<RichContent.Block> fallbackBlocks = RichContent.parse(fallback, (JSONArray) null);
            if (RichContent.hasReadableText(fallbackBlocks)) {
                fallbackBlocks.addAll(blocks);
                blocks = fallbackBlocks;
            } else if (blocks.isEmpty()) {
                blocks = RichContent.parse(fallback, fallbackImages);
            }
        }
        // 文章帖保持图文竖排混排；普通动态帖的多图改为官方式左右滑动图集
        boolean articleMode = link != null && (link.optInt("use_concept_type", -1) == 0
                || link.optBoolean("is_article", false));
        addRichContent(parent, blocks, !articleMode);
    }

    private void addRichContent(LinearLayout parent, List<RichContent.Block> blocks) {
        addRichContent(parent, blocks, false);
    }

    private void addRichContent(LinearLayout parent, List<RichContent.Block> blocks, boolean pagerImages) {
        if (blocks.isEmpty()) {
            addTop(parent, text("正文为空", 13.0f, this.MUTED), 9);
            return;
        }
        int imageCount = 0;
        boolean bodyStarted = false;
        boolean lastWasImage = false;
        int index = 0;
        while (index < blocks.size()) {
            RichContent.Block block = blocks.get(index);
            if (block.image) {
                List<String> run = new ArrayList<>();
                while (index < blocks.size() && blocks.get(index).image) {
                    // 长文章图可以很多（攻略帖 20+ 张），上限只防极端轰炸帖
                    if (!this.session.noImage() && imageCount < 48) {
                        run.add(blocks.get(index).value);
                        imageCount++;
                    }
                    index++;
                }
                lastWasImage = false;
                if (pagerImages && run.size() >= 2) {
                    addImagePager(parent, run);
                } else {
                    for (String url : run) {
                        View image = postImageBlock(url, detailImageTargetPx(), 150);
                        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(-1, -2);
                        imageParams.topMargin = dp(10);
                        parent.addView(image, imageParams);
                        lastWasImage = true;
                    }
                }
                continue;
            }
            index++;
            if (block.kind == RichContent.Block.HEADING) {
                addArticleHeading(parent, block.value, bodyStarted);
                bodyStarted = true;
                lastWasImage = false;
            } else if (block.kind == RichContent.Block.QUOTE) {
                addArticleQuote(parent, block.value, bodyStarted);
                bodyStarted = true;
                lastWasImage = false;
            } else if (block.kind == RichContent.Block.CAPTION) {
                // 图注只在其对应图片真的渲染了才显示，否则并入正文
                if (lastWasImage) {
                    addImageCaption(parent, block.value);
                } else {
                    bodyStarted = addBodyParagraphs(parent, block.value, bodyStarted);
                }
                lastWasImage = false;
            } else {
                bodyStarted = addBodyParagraphs(parent, block.value, bodyStarted);
                lastWasImage = false;
            }
        }
    }

    private void addArticleHeading(LinearLayout parent, String value, boolean bodyStarted) {
        TextView heading = text(value, (16.0f * this.session.bodyTextScale()) / 100.0f, this.TEXT);
        heading.setTypeface(appRegularTypeface(), 1);
        heading.setLineSpacing(dp(1), 1.16f);
        Compat.setLetterSpacing(heading, this.session.bodyLetterSpacing() / 200.0f);
        heading.setTextIsSelectable(true);
        EmojiRenderer.set(heading, value, this.session.darkMode());
        addTop(parent, heading, bodyStarted ? Math.max(14, this.session.bodyParagraphSpacing() + 10) : 14);
    }

    private void addImageCaption(LinearLayout parent, String value) {
        // 对照官方 h4 图注：约为正文 75% 大小的灰色居中小字
        TextView caption = text(value, (11.0f * this.session.bodyTextScale()) / 100.0f, this.MUTED);
        caption.setGravity(17);
        caption.setLineSpacing(dp(1), 1.3f);
        caption.setTextIsSelectable(true);
        EmojiRenderer.set(caption, value, this.session.darkMode());
        addTop(parent, caption, 6);
    }

    /** 官方样式的多图横滑图集：一屏一张，右上角页码，底部圆点指示。 */
    private void addImagePager(LinearLayout parent, List<String> urls) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int pagerHeight = Math.min(dp(270), Math.round(screenWidth * 0.85f));
        FrameLayout wrap = new FrameLayout(this);
        int placeholder = this.session.darkMode() ? Color.rgb(28, NAV_BAR_HEIGHT_DP, 32) : Color.rgb(235, 237, 240);
        Compat.setBackground(wrap, round(placeholder, 7));
        Compat.clipToOutline(wrap);

        TextView counter = text("1/" + urls.size(), 10.0f, -1);
        LinearLayout dots = new LinearLayout(this);
        ImagePagerCore core = new ImagePagerCore(this, page -> {
            counter.setText((page + 1) + "/" + urls.size());
            for (int i = 0; i < dots.getChildCount(); i++) {
                dots.getChildAt(i).setAlpha(i == page ? 1.0f : 0.4f);
            }
        });
        for (String url : urls) {
            FrameLayout pageFrame = new FrameLayout(this);
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setAdjustViewBounds(false);
            pageFrame.addView(image, match());
            image.setOnClickListener(view -> {
                openImage(image, url);
            });
            ImageLoader.intoMeasuredRevealStable(image, url, detailImageTargetPx(), (success, bitmap) -> {
                if (!success && image.getDrawable() == null) {
                    TextView failed = text("图片加载失败", 11.0f, this.MUTED);
                    failed.setGravity(17);
                    pageFrame.addView(failed, match());
                }
            });
            core.addView(pageFrame);
        }
        wrap.addView(core, match());

        counter.setGravity(17);
        counter.setTypeface(appRegularTypeface(), 1);
        counter.setPadding(dp(7), dp(2), dp(7), dp(2));
        Compat.setBackground(counter, round(0x8C000000, 9));
        FrameLayout.LayoutParams counterParams = new FrameLayout.LayoutParams(-2, -2, 53);
        counterParams.topMargin = dp(7);
        counterParams.rightMargin = dp(7);
        wrap.addView(counter, counterParams);

        dots.setGravity(17);
        for (int i = 0; i < urls.size(); i++) {
            View dot = new View(this);
            Compat.setBackground(dot, round(-1, 3));
            dot.setAlpha(i == 0 ? 1.0f : 0.4f);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(5), dp(5));
            dotParams.leftMargin = dp(2);
            dotParams.rightMargin = dp(2);
            dots.addView(dot, dotParams);
        }
        FrameLayout.LayoutParams dotsParams = new FrameLayout.LayoutParams(-2, -2, 81);
        dotsParams.bottomMargin = dp(7);
        wrap.addView(dots, dotsParams);

        LinearLayout.LayoutParams wrapParams = new LinearLayout.LayoutParams(-1, pagerHeight);
        wrapParams.topMargin = dp(10);
        parent.addView(wrap, wrapParams);
    }

    private interface PagerListener {
        void onPage(int page);
    }

    /** 横向翻页容器：水平拖动翻图，垂直手势交还给正文滚动，翻页时通知指示器。 */
    private final class ImagePagerCore extends ViewGroup {
        private final int touchSlop;
        private final PagerListener listener;
        private float startX;
        private float startY;
        private long startTime;
        private int startScrollX;
        private int page;
        private boolean dragging;
        private boolean ignoring;
        private ValueAnimator settleAnimator;

        ImagePagerCore(Context context, PagerListener listener) {
            super(context);
            this.listener = listener;
            this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int height = MeasureSpec.getSize(heightSpec);
            setMeasuredDimension(width, height);
            int childWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
            int childHeight = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).measure(childWidth, childHeight);
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int width = right - left;
            int height = bottom - top;
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).layout(i * width, 0, (i + 1) * width, height);
            }
            scrollTo(this.page * width, 0);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            if (getChildCount() < 2) {
                return false;
            }
            switch (event.getActionMasked()) {
                case 0:
                    cancelSettle();
                    this.startX = event.getX();
                    this.startY = event.getY();
                    this.startTime = event.getEventTime();
                    this.startScrollX = getScrollX();
                    this.dragging = false;
                    this.ignoring = false;
                    // 先声明占用，判定为垂直手势后再交还给正文滚动
                    getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                case 1:
                case 3:
                    if (!this.dragging) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    break;
                case 2:
                    if (this.ignoring) {
                        break;
                    }
                    float dx = event.getX() - this.startX;
                    float dy = event.getY() - this.startY;
                    if (!this.dragging) {
                        if (Math.abs(dy) > this.touchSlop && Math.abs(dy) > Math.abs(dx)) {
                            this.ignoring = true;
                            getParent().requestDisallowInterceptTouchEvent(false);
                        } else if (Math.abs(dx) > this.touchSlop && Math.abs(dx) > Math.abs(dy)) {
                            this.dragging = true;
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                    }
                    break;
            }
            return this.dragging;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case 0:
                    cancelSettle();
                    this.startX = event.getX();
                    this.startY = event.getY();
                    this.startTime = event.getEventTime();
                    this.startScrollX = getScrollX();
                    this.dragging = true;
                    break;
                case 1:
                    finishDrag(event);
                    performClick();
                    break;
                case 2:
                    dragTo(event.getX() - this.startX);
                    break;
                case 3:
                    this.dragging = false;
                    settleTo(this.page, true);
                    break;
            }
            return true;
        }

        private void dragTo(float dx) {
            int width = Math.max(1, getWidth());
            int max = Math.max(0, (getChildCount() - 1) * width);
            int next = Math.max(0, Math.min(max, this.startScrollX - Math.round(dx)));
            scrollTo(next, 0);
        }

        private void finishDrag(MotionEvent event) {
            int width = Math.max(1, getWidth());
            float dx = event.getX() - this.startX;
            long duration = Math.max(1L, event.getEventTime() - this.startTime);
            float velocity = (dx * 1000.0f) / duration;
            int target = Math.round(getScrollX() / (float) width);
            if (Math.abs(velocity) > dp(320)) {
                target = velocity < 0.0f ? this.page + 1 : this.page - 1;
            }
            this.dragging = false;
            settleTo(Math.max(0, Math.min(getChildCount() - 1, target)), true);
        }

        private void settleTo(int next, boolean animate) {
            cancelSettle();
            if (next != this.page) {
                this.page = next;
                if (this.listener != null) {
                    this.listener.onPage(next);
                }
            }
            int destination = next * Math.max(1, getWidth());
            if (!animate || Math.abs(destination - getScrollX()) < dp(REPLY_PREVIEW_COUNT)) {
                scrollTo(destination, 0);
                return;
            }
            int fromX = getScrollX();
            int duration = Math.max(150, Math.min(280, Math.abs(destination - fromX) / 3));
            this.settleAnimator = ValueAnimator.ofInt(fromX, destination);
            this.settleAnimator.setDuration(duration);
            this.settleAnimator.setInterpolator(new DecelerateInterpolator());
            this.settleAnimator.addUpdateListener(value -> {
                scrollTo(((Integer) value.getAnimatedValue()).intValue(), 0);
            });
            this.settleAnimator.start();
        }

        private void cancelSettle() {
            if (this.settleAnimator != null) {
                this.settleAnimator.cancel();
                this.settleAnimator = null;
            }
        }
    }

    private boolean addBodyParagraphs(LinearLayout parent, String source, boolean bodyStarted) {
        List<String> paragraphs = articleParagraphs(source);
        for (String paragraph : paragraphs) {
            if (isArticleQuote(paragraph)) {
                addArticleQuote(parent, paragraph, bodyStarted);
                bodyStarted = true;
            } else if (isInlineHeading(paragraph)) {
                addArticleHeading(parent, paragraph, bodyStarted);
                bodyStarted = true;
            } else {
                TextView bodyText = text(paragraph, (14.5f * this.session.bodyTextScale()) / 100.0f, this.TEXT);
                float lineScale = Math.max(1.18f, this.session.bodyLineSpacing() / 100.0f);
                bodyText.setLineSpacing(dp(1), lineScale);
                Compat.setLetterSpacing(bodyText, this.session.bodyLetterSpacing() / 200.0f);
                bodyText.setTypeface(appRegularTypeface(), 0);
                bodyText.setTextIsSelectable(true);
                EmojiRenderer.set(bodyText, paragraph, this.session.darkMode());
                addTop(parent, bodyText, bodyStarted ? Math.max(8, this.session.bodyParagraphSpacing() + 6) : 14);
                bodyStarted = true;
            }
        }
        return bodyStarted;
    }

    /** 作者手打的编号短行（如“1.设置（推荐）”“三、心得”）当作小标题渲染，与官方排版一致。 */
    private boolean isInlineHeading(String paragraph) {
        String clean = paragraph == null ? "" : paragraph.trim();
        if (clean.length() < 3 || clean.length() > 26) {
            return false;
        }
        if (clean.matches(".*[。，,;；!！?？…].*")) {
            return false;
        }
        return clean.matches("^(?:[0-9０-９]{1,2}[.．、](?![0-9０-９])|[一二三四五六七八九十]{1,2}[、.．]).+");
    }

    private boolean isArticleQuote(String paragraph) {
        if (paragraph == null) {
            return false;
        }
        String clean = paragraph.trim();
        return clean.startsWith(">") || clean.startsWith("阅读对象") || clean.startsWith("专业黑话") || clean.startsWith("扩展阅读") || clean.startsWith("包含AI") || clean.startsWith("本文") || clean.startsWith("热点");
    }

    private void addArticleQuote(LinearLayout parent, String paragraph, boolean bodyStarted) {
        String value;
        String strTrim = paragraph == null ? "" : paragraph.trim();
        while (true) {
            value = strTrim;
            if (!value.startsWith(">")) {
                break;
            } else {
                strTrim = value.substring(1).trim();
            }
        }
        LinearLayout quote = new LinearLayout(this);
        quote.setGravity(16);
        quote.setPadding(0, dp(REPLY_PREVIEW_COUNT), 0, dp(REPLY_PREVIEW_COUNT));
        View bar = new View(this);
        int barColor = blend(this.SECONDARY, this.BG, this.session.darkMode() ? 0.38f : 0.62f);
        Compat.setBackground(bar, round(barColor, REPLY_PREVIEW_COUNT));
        bar.setMinimumHeight(dp(38));
        quote.addView(bar, new LinearLayout.LayoutParams(dp(4), -1));
        TextView copy = text("", (13.5f * this.session.bodyTextScale()) / 100.0f, this.MUTED);
        copy.setLineSpacing(dp(1), Math.max(1.14f, this.session.bodyLineSpacing() / 100.0f));
        Compat.setLetterSpacing(copy, this.session.bodyLetterSpacing() / 220.0f);
        EmojiRenderer.set(copy, value, this.session.darkMode());
        copy.setPadding(dp(10), 0, 0, 0);
        quote.addView(copy, new LinearLayout.LayoutParams(0, -2, 1.0f));
        addTop(parent, quote, bodyStarted ? 10 : 14);
    }

    private View postImageBlock(String url, int targetPx, int heightDp) {
        PostImageFrame frame = new PostImageFrame(this, heightDp);
        int placeholder = this.session.darkMode() ? Color.rgb(28, NAV_BAR_HEIGHT_DP, 32) : Color.rgb(235, 237, 240);
        Compat.setBackground(frame, round(placeholder, 7));
        Compat.clipToOutline(frame);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(false);
        frame.addView(image, match());
        image.setOnClickListener(view -> {
            openImage(image, url);
        });
        ImageLoader.intoMeasuredRevealStable(image, url, targetPx, (success, bitmap) -> {
            if (success && bitmap != null) {
                frame.setImageSize(bitmap.getWidth(), bitmap.getHeight());
                if (this.session.playGif() && GifSupport.isGifUrl(url)) {
                    ImageLoader.intoGif(image, url);
                }
            }
            if (!success && image.getDrawable() == null) {
                TextView failed = text("图片加载失败", 11.0f, this.MUTED);
                failed.setGravity(17);
                frame.addView(failed, match());
            }
        });
        return frame;
    }

    private int detailImageTargetPx() {
        int width = Math.max(320, getResources().getDisplayMetrics().widthPixels);
        int max = this.session != null && this.session.roundScreen() ? 720 : 900;
        return Math.max(480, Math.min(max, Math.round(width * 1.25f)));
    }

    /* JADX INFO: loaded from: MainActivity$ZoomablePostImageView.class */
    private final class ZoomablePostImageView extends ImageView {
        private final Matrix imageMatrixValue;
        private final int slop;
        private float scale;
        private float downX;
        private float downY;
        private float lastX;
        private float lastY;
        private long lastTapAt;
        private float lastTapX;
        private float lastTapY;
        private boolean moved;
        private Runnable pendingSingleTap;
        private Runnable singleTapAction;
        private ValueAnimator zoomAnimator;

        ZoomablePostImageView(Context context) {
            super(context);
            this.imageMatrixValue = new Matrix();
            this.scale = 1.0f;
            setScaleType(ImageView.ScaleType.MATRIX);
            setAdjustViewBounds(false);
            this.slop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        void setSingleTapAction(Runnable action) {
            this.singleTapAction = action;
        }

        void postFit() {
            post(this::fitImage);
        }

        @Override
        public void setImageBitmap(Bitmap bitmap) {
            super.setImageBitmap(bitmap);
            if (bitmap != null) {
                postFit();
                invalidate();
            }
        }

        @Override // android.view.View
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            postFit();
        }

        @Override // android.view.View
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case 0:
                    cancelZoomAnimator();
                    float x = event.getX();
                    this.lastX = x;
                    this.downX = x;
                    float y = event.getY();
                    this.lastY = y;
                    this.downY = y;
                    this.moved = false;
                    if (this.scale > 1.05f) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    break;
                case 1:
                    if (!this.moved) {
                        handleTap(event.getX(), event.getY());
                    }
                    if (this.scale > 1.05f) {
                        correctImageBounds();
                        setImageMatrix(this.imageMatrixValue);
                    }
                    break;
                case MainActivity.REPLY_PREVIEW_COUNT /* 2 */:
                    float dx = event.getX() - this.downX;
                    float dy = event.getY() - this.downY;
                    if (Math.abs(dx) > this.slop || Math.abs(dy) > this.slop) {
                        this.moved = true;
                    }
                    if (this.scale > 1.05f) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                        this.imageMatrixValue.postTranslate(event.getX() - this.lastX, event.getY() - this.lastY);
                        correctImageBounds();
                        setImageMatrix(this.imageMatrixValue);
                    }
                    this.lastX = event.getX();
                    this.lastY = event.getY();
                    if (this.scale > 1.05f) {
                    }
                    break;
                case 3:
                    removePendingSingleTap();
                    break;
            }
            return true;
        }

        private void handleTap(float x, float y) {
            long now = System.currentTimeMillis();
            boolean doubleTap = now - this.lastTapAt < 290 && Math.abs(x - this.lastTapX) < ((float) MainActivity.this.dp(28)) && Math.abs(y - this.lastTapY) < ((float) MainActivity.this.dp(28));
            if (doubleTap) {
                removePendingSingleTap();
                toggleZoom(x, y);
                this.lastTapAt = 0L;
            } else {
                this.lastTapAt = now;
                this.lastTapX = x;
                this.lastTapY = y;
                removePendingSingleTap();
                this.pendingSingleTap = () -> {
                    this.pendingSingleTap = null;
                    performClick();
                };
                postDelayed(this.pendingSingleTap, 260L);
            }
        }

        private void removePendingSingleTap() {
            if (this.pendingSingleTap != null) {
                removeCallbacks(this.pendingSingleTap);
                this.pendingSingleTap = null;
            }
        }

        private void toggleZoom(float x, float y) {
            if (this.scale > 1.05f) {
                animateFitImage();
            } else {
                animateRelativeZoom(2.25f, x, y);
            }
        }

        private void fitImage() {
            Drawable drawable = getDrawable();
            if (drawable == null || getWidth() <= 0 || getHeight() <= 0) {
                return;
            }
            float sx = getWidth() / (float) Math.max(1, drawable.getIntrinsicWidth());
            float sy = getHeight() / (float) Math.max(1, drawable.getIntrinsicHeight());
            float fit = Math.min(sx, sy);
            float dx = (getWidth() - (drawable.getIntrinsicWidth() * fit)) / 2.0f;
            float dy = (getHeight() - (drawable.getIntrinsicHeight() * fit)) / 2.0f;
            this.imageMatrixValue.reset();
            this.imageMatrixValue.postScale(fit, fit);
            this.imageMatrixValue.postTranslate(dx, dy);
            this.scale = 1.0f;
            setImageMatrix(this.imageMatrixValue);
        }

        private Matrix fitMatrix() {
            Drawable drawable = getDrawable();
            if (drawable == null || getWidth() <= 0 || getHeight() <= 0) {
                return null;
            }
            float sx = getWidth() / (float) Math.max(1, drawable.getIntrinsicWidth());
            float sy = getHeight() / (float) Math.max(1, drawable.getIntrinsicHeight());
            float fit = Math.min(sx, sy);
            float dx = (getWidth() - (drawable.getIntrinsicWidth() * fit)) / 2.0f;
            float dy = (getHeight() - (drawable.getIntrinsicHeight() * fit)) / 2.0f;
            Matrix out = new Matrix();
            out.postScale(fit, fit);
            out.postTranslate(dx, dy);
            return out;
        }

        private void animateRelativeZoom(float target, float x, float y) {
            cancelZoomAnimator();
            Matrix targetMatrix = new Matrix(this.imageMatrixValue);
            float factor = target / Math.max(0.001f, this.scale);
            targetMatrix.postScale(factor, factor, x, y);
            RectF corrected = imageBounds(targetMatrix);
            if (corrected != null) {
                float dx = correctionX(corrected);
                float dy = correctionY(corrected);
                if (dx != 0.0f || dy != 0.0f) {
                    targetMatrix.postTranslate(dx, dy);
                }
            }
            animateMatrixTo(targetMatrix, this.scale, target, 230);
        }

        private void animateFitImage() {
            Matrix target = fitMatrix();
            if (target == null) {
                fitImage();
            } else {
                animateMatrixTo(target, this.scale, 1.0f, 210);
            }
        }

        private void animateMatrixTo(final Matrix target, float fromScale, final float toScale, int duration) {
            cancelZoomAnimator();
            float[] startValues = new float[9];
            float[] endValues = new float[9];
            float[] values = new float[9];
            this.imageMatrixValue.getValues(startValues);
            target.getValues(endValues);
            this.zoomAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
            this.zoomAnimator.setDuration(duration);
            this.zoomAnimator.setInterpolator(new DecelerateInterpolator());
            this.zoomAnimator.addUpdateListener(value -> {
                float progress = ((Float) value.getAnimatedValue()).floatValue();
                for (int i = 0; i < values.length; i++) {
                    values[i] = startValues[i] + ((endValues[i] - startValues[i]) * progress);
                }
                this.imageMatrixValue.setValues(values);
                this.scale = fromScale + ((toScale - fromScale) * progress);
                setImageMatrix(this.imageMatrixValue);
            });
            this.zoomAnimator.addListener(new AnimatorListenerAdapter() { // from class: com.openzen.heyboxcommunity.MainActivity.ZoomablePostImageView.1
                @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
                public void onAnimationEnd(Animator animation) {
                    ZoomablePostImageView.this.zoomAnimator = null;
                    ZoomablePostImageView.this.imageMatrixValue.set(target);
                    ZoomablePostImageView.this.scale = toScale;
                    ZoomablePostImageView.this.setImageMatrix(ZoomablePostImageView.this.imageMatrixValue);
                }
            });
            this.zoomAnimator.start();
        }

        private RectF imageBounds(Matrix source) {
            Drawable drawable = getDrawable();
            if (drawable == null || getWidth() <= 0 || getHeight() <= 0) {
                return null;
            }
            RectF bounds = new RectF(0.0f, 0.0f, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
            source.mapRect(bounds);
            return bounds;
        }

        private float correctionX(RectF bounds) {
            if (bounds.width() <= getWidth()) {
                return (getWidth() / 2.0f) - bounds.centerX();
            }
            if (bounds.left > 0.0f) {
                return -bounds.left;
            }
            if (bounds.right < getWidth()) {
                return getWidth() - bounds.right;
            }
            return 0.0f;
        }

        private float correctionY(RectF bounds) {
            if (bounds.height() <= getHeight()) {
                return (getHeight() / 2.0f) - bounds.centerY();
            }
            if (bounds.top > 0.0f) {
                return -bounds.top;
            }
            if (bounds.bottom < getHeight()) {
                return getHeight() - bounds.bottom;
            }
            return 0.0f;
        }

        private void cancelZoomAnimator() {
            if (this.zoomAnimator != null) {
                this.zoomAnimator.removeAllListeners();
                this.zoomAnimator.cancel();
                this.zoomAnimator = null;
            }
        }

        private void correctImageBounds() {
            Drawable drawable = getDrawable();
            if (drawable == null || getWidth() <= 0 || getHeight() <= 0) {
                return;
            }
            RectF bounds = new RectF(0.0f, 0.0f, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
            this.imageMatrixValue.mapRect(bounds);
            float dx = 0.0f;
            float dy = 0.0f;
            if (bounds.width() <= getWidth()) {
                dx = (getWidth() / 2.0f) - bounds.centerX();
            } else if (bounds.left > 0.0f) {
                dx = -bounds.left;
            } else if (bounds.right < getWidth()) {
                dx = getWidth() - bounds.right;
            }
            if (bounds.height() <= getHeight()) {
                dy = (getHeight() / 2.0f) - bounds.centerY();
            } else if (bounds.top > 0.0f) {
                dy = -bounds.top;
            } else if (bounds.bottom < getHeight()) {
                dy = getHeight() - bounds.bottom;
            }
            if (dx != 0.0f || dy != 0.0f) {
                this.imageMatrixValue.postTranslate(dx, dy);
            }
        }

        @Override // android.view.View
        public boolean performClick() {
            super.performClick();
            if (this.singleTapAction != null) {
                this.singleTapAction.run();
                return true;
            }
            return true;
        }
    }

    /* JADX INFO: loaded from: MainActivity$PostImageFrame.class */
    private final class PostImageFrame extends FrameLayout {
        private final int minHeight;
        private final int maxHeight;
        private final int fallbackHeight;
        private float aspect;

        PostImageFrame(Context context, int fallbackHeightDp) {
            super(context);
            this.aspect = 0.5625f;
            this.minHeight = MainActivity.this.dp(Math.max(92, fallbackHeightDp - 36));
            this.maxHeight = MainActivity.this.dp(fallbackHeightDp >= 140 ? 360 : 220);
            this.fallbackHeight = MainActivity.this.dp(fallbackHeightDp);
            setMinimumHeight(this.minHeight);
        }

        void setImageSize(int width, int height) {
            if (width <= 0 || height <= 0) {
                return;
            }
            float next = (float) height / (float) width;
            if (Math.abs(next - this.aspect) < 0.08f) {
                return;
            }
            this.aspect = Math.max(0.3f, Math.min(2.8f, next));
            requestLayout();
        }

        @Override // android.widget.FrameLayout, android.view.View
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = View.MeasureSpec.getSize(widthMeasureSpec);
            int desired = width > 0 ? Math.round(width * this.aspect) : this.fallbackHeight;
            int height = Math.max(this.minHeight, Math.min(this.maxHeight, desired));
            super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(height, 1073741824));
        }
    }

    private List<String> articleParagraphs(String source) {
        List<String> paragraphs = new ArrayList<>();
        String value = source == null ? "" : source.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (value.isEmpty()) {
            return paragraphs;
        }
        String value2 = consumeLeadingArticleLabel(paragraphs, value);
        if (value2.isEmpty()) {
            return paragraphs;
        }
        String value3 = normalizeArticleBreaks(value2);
        if (value3.contains("\n")) {
            String[] parts = value3.split("\\n+");
            for (String part : parts) {
                String clean = part.trim();
                if (!clean.isEmpty()) {
                    paragraphs.add(clean);
                }
            }
            return paragraphs;
        }
        StringBuilder current = new StringBuilder();
        int visible = 0;
        for (int i = 0; i < value3.length(); i++) {
            char ch = value3.charAt(i);
            current.append(ch);
            if (!Character.isWhitespace(ch)) {
                visible++;
            }
            if ((isSentenceEnd(ch) && visible >= 28) || (isSoftBreak(ch) && visible >= 68)) {
                String paragraph = current.toString().trim();
                if (!paragraph.isEmpty()) {
                    paragraphs.add(paragraph);
                }
                current.setLength(0);
                visible = 0;
            }
        }
        String tail = current.toString().trim();
        if (!tail.isEmpty()) {
            paragraphs.add(tail);
        }
        return paragraphs;
    }

    private String normalizeArticleBreaks(String value) {
        String output = value.replaceAll("([\\u3002\\uff01\\uff1f!?])\\s*(?=([\\uff08(][0-9\\u4e00-\\u9fff]{1,3}[\\uff09)]))", "$1\n");
        return output.replaceAll("\\s+(?=([\\uff08(][0-9\\u4e00-\\u9fff]{1,3}[\\uff09)]))", "\n").replaceAll("([^\\n])\\s+(?=([\\u4e00-\\u9fff]{1,4}\\u3001))", "$1\n").replaceAll("([^\\n])\\s+(?=([0-9]{1,2}[.\\uff0e][^0-9]))", "$1\n").replaceAll("([\\u4e00-\\u9fffA-Za-z])(?=([0-9]{1,2}[.\\uff0e][\\u4e00-\\u9fff]))", "$1\n").replaceAll("([\\u3002\\uff01\\uff1f!?])(?=([0-9]{1,2}[.\\uff0e][\\u4e00-\\u9fff]))", "$1\n");
    }

    private String consumeLeadingArticleLabel(List<String> paragraphs, String value) {
        String[] labels = {"提示词：", "提示", "提示", "Prompt:", "Prompt"};
        for (String label : labels) {
            if (value.equals(label)) {
                paragraphs.add(displayLabel(label));
                return "";
            }
            if (value.startsWith(label + " ") || value.startsWith(label + "\n") || ((label.endsWith(":") || label.endsWith("")) && value.startsWith(label))) {
                paragraphs.add(displayLabel(label));
                return value.substring(label.length()).trim();
            }
        }
        return value;
    }

    private String displayLabel(String label) {
        return label.startsWith("提示") ? "提示" : label.startsWith("Prompt") ? "Prompt" : label;
    }

    private boolean isSentenceEnd(char ch) {
        return ch == 12290 || ch == 65281 || ch == 65311 || ch == '!' || ch == '?';
    }

    private boolean isSoftBreak(char ch) {
        return ch == 65307 || ch == ';';
    }

    private int addComments(LinearLayout page, JSONArray groups) {
        if (groups == null) {
            return 0;
        }
        List<JSONObject> threads = new ArrayList<>();
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.optJSONObject(i);
            if (group != null) {
                threads.add(group);
            }
        }
        Collections.sort(threads, (a, b) -> {
            return Integer.compare(threadLikes(b), threadLikes(a));
        });
        int count = 0;
        Iterator<JSONObject> it = threads.iterator();
        while (it.hasNext()) {
            JSONObject group2 = it.next();
            JSONArray comments = group2 == null ? null : group2.optJSONArray("comment");
            JSONObject root = comments == null ? group2 : comments.optJSONObject(0);
            if (root != null) {
                LinearLayout linearLayoutCard = vertical(this.BG);
                linearLayoutCard.setPadding(dp(4), dp(9), dp(4), dp(8));
                if (count > 0) {
                    View divider = new View(this);
                    divider.setBackgroundColor(blend(this.BG, this.MUTED, this.session.darkMode() ? 0.22f : 0.16f));
                    page.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
                }
                addComment(linearLayoutCard, root, false);
                List<JSONObject> replies = new ArrayList<>();
                if (comments != null) {
                    for (int j = 1; j < comments.length(); j++) {
                        JSONObject reply = comments.optJSONObject(j);
                        if (reply != null) {
                            replies.add(reply);
                        }
                    }
                }
                Collections.sort(replies, (a2, b2) -> {
                    return Long.compare(commentTime(a2), commentTime(b2));
                });
                int expected = Math.max(root.optInt("child_num"), group2 == null ? 0 : group2.optInt("child_num"));
                if (!replies.isEmpty() || expected > 0) {
                    LinearLayout replySection = new LinearLayout(this);
                    replySection.setOrientation(1);
                    replySection.setPadding(dp(8), dp(3), dp(8), dp(3));
                    int replyBg = this.session.darkMode()
                            ? Color.rgb(30, 32, 35) : Color.rgb(247, 248, 250);
                    Compat.setBackground(replySection, round(replyBg, 8));
                    LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(-1, -2);
                    sectionParams.topMargin = dp(5);
                    linearLayoutCard.addView(replySection, sectionParams);
                    LinearLayout replyList = new LinearLayout(this);
                    replyList.setOrientation(1);
                    replySection.addView(replyList, new LinearLayout.LayoutParams(-1, -2));
                    int initial = Math.min(REPLY_PREVIEW_COUNT, replies.size());
                    boolean allLoaded = expected <= replies.size();
                    renderReplies(replyList, root, replies, expected, initial, allLoaded);
                }
                addTop(page, linearLayoutCard, count == 0 ? 0 : 2);
                animateIn(linearLayoutCard);
                count += 1 + replies.size();
            }
        }
        return count;
    }

    private void renderReplies(LinearLayout parent, JSONObject root, List<JSONObject> replies, int expected, int visibleCount, boolean allLoaded) {
        parent.removeAllViews();
        int total = Math.max(expected, replies.size());
        int shown = Math.min(Math.max(0, visibleCount), replies.size());
        for (int i = 0; i < shown; i++) {
            addComment(parent, replies.get(i), true);
        }
        if (shown < replies.size()) {
            int nextCount = Math.min(REPLY_PAGE_SIZE, replies.size() - shown);
            TextView more = replyControl("再展开 " + nextCount + " 条", R.drawable.ic_expand);
            addTop(parent, more, 2);
            more.getLayoutParams().height = dp(26);
            more.setOnClickListener(view -> {
                renderReplies(parent, root, replies, total, shown + REPLY_PAGE_SIZE, allLoaded);
                animateIn(parent);
            });
        } else if (!allLoaded && total > replies.size()) {
            TextView more2 = replyControl("再展开 5 条", R.drawable.ic_expand);
            addTop(parent, more2, 2);
            more2.getLayoutParams().height = dp(26);
            more2.setOnClickListener(view2 -> {
                loadSubComments(parent, root, replies, total, shown);
            });
        }
        if (shown > REPLY_PREVIEW_COUNT) {
            TextView collapse = replyControl("收起回复", R.drawable.ic_arrow_back);
            addTop(parent, collapse, 2);
            collapse.getLayoutParams().height = dp(26);
            collapse.setOnClickListener(view3 -> {
                renderReplies(parent, root, replies, total, Math.min(REPLY_PREVIEW_COUNT, replies.size()), allLoaded);
            });
        }
    }

    private TextView replyControl(String label, int icon) {
        int background = this.session.darkMode() ? Color.rgb(34, 36, 39) : Color.rgb(244, 245, 247);
        TextView control = text(label, 11.0f, this.MUTED);
        control.setGravity(17);
        control.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable drawable = round(background, 6);
        Compat.setBackground(control, drawable);
        return control;
    }

    private void loadSubComments(final LinearLayout target, final JSONObject root, final List<JSONObject> preview, final int expected, final int shown) {
        final TextView loading = text("正在加载更多回复", 11.0f, this.MUTED);
        loading.setPadding(0, dp(8), 0, dp(8));
        target.addView(loading);
        Map<String, String> params = new HashMap<>();
        final String id = commentId(root);
        params.put("comment_id", id);
        params.put("commentid", id);
        params.put("root_comment_id", id);
        params.put("link_id", this.currentLinkId);
        params.put("offset", String.valueOf(preview.size()));
        params.put("page", String.valueOf((preview.size() / 50) + 1));
        params.put("limit", "50");
        this.api.get(EndpointProvider.subComments(), params, new ApiClient.Callback() { // from class: com.openzen.heyboxcommunity.MainActivity.20
            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onSuccess(JSONObject body) {
                List<JSONObject> replies = MainActivity.this.extractSubComments(body, id);
                if (replies.isEmpty()) {
                    target.removeView(loading);
                    MainActivity.this.toast("没有获取到更多回复");
                    return;
                }
                List<JSONObject> merged = MainActivity.this.mergeReplies(preview, replies);
                Collections.sort(merged, (a, b) -> {
                    return Long.compare(MainActivity.this.commentTime(a), MainActivity.this.commentTime(b));
                });
                int total = Math.max(expected, merged.size());
                MainActivity.this.renderReplies(target, root, merged, total, shown + MainActivity.REPLY_PAGE_SIZE, merged.size() >= total);
                MainActivity.this.animateIn(target);
            }

            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onError(String message) {
                target.removeView(loading);
                MainActivity.this.toast("回复加载失败" + message);
            }
        });
    }

    private List<JSONObject> mergeReplies(List<JSONObject> first, List<JSONObject> second) {
        List<JSONObject> merged = new ArrayList<>(first);
        for (JSONObject candidate : second) {
            String id = commentId(candidate);
            boolean duplicate = false;
            Iterator<JSONObject> it = merged.iterator();
            while (true) {
                if (!it.hasNext()) {
                    break;
                }
                JSONObject existing = it.next();
                if (!id.isEmpty() && id.equals(commentId(existing))) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                merged.add(candidate);
            }
        }
        return merged;
    }

    private List<JSONObject> extractSubComments(JSONObject body, String rootId) {
        List<JSONObject> values = new ArrayList<>();
        JSONObject result = body.optJSONObject("result");
        if (result == null) {
            return values;
        }
        JSONArray array = result.optJSONArray("comments");
        if (array == null) {
            array = result.optJSONArray("comment");
        }
        if (array == null) {
            array = result.optJSONArray("list");
        }
        if (array == null) {
            array = result.optJSONArray("sub_comments");
        }
        if (array == null) {
            return values;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) {
                JSONArray nested = item.optJSONArray("comment");
                if (nested != null) {
                    for (int j = 0; j < nested.length(); j++) {
                        JSONObject reply = nested.optJSONObject(j);
                        if (reply != null && !rootId.equals(commentId(reply))) {
                            values.add(reply);
                        }
                    }
                } else if (!rootId.equals(commentId(item))) {
                    values.add(item);
                }
            }
        }
        return values;
    }

    private int threadLikes(JSONObject group) {
        JSONArray comments = group.optJSONArray("comment");
        JSONObject root = comments == null ? group : comments.optJSONObject(0);
        return commentLikes(root == null ? group : root);
    }

    private int commentLikes(JSONObject comment) {
        return comment.optInt("comment_award_num", comment.optInt("award_num", comment.optInt("up")));
    }

    private boolean commentLiked(JSONObject comment) {
        if (comment == null) {
            return false;
        }
        return truthy(comment, "is_support", "supported", "is_award", "liked", "has_support");
    }

    private void updateCommentLikeView(TextView view, boolean liked, int likes) {
        int bg = liked ? activeActionBackground(this.PRIMARY) : blend(this.BG, this.MUTED, this.session.darkMode() ? 0.12f : 0.08f);
        int color = liked ? contrast(bg) : this.MUTED;
        view.setText(String.valueOf(Math.max(0, likes)));
        view.setTextColor(color);
        view.setPadding(dp(5), 0, dp(5), 0);
        GradientDrawable drawable = round(bg, 14);
        drawable.setStroke(dp(1), liked ? blend(bg, color, 0.24f) : blend(this.BG, this.MUTED, 0.18f));
        Compat.setBackground(view, drawable);
        setLeftIcon(view, R.drawable.ic_thumb_up, color, liked ? 14 : 13);
    }

    private void toggleCommentLike(final JSONObject comment, final TextView view) {
        if (requireLogin("评论点赞")) {
            if (!allowWriteAction("评论点赞")) {
                return;
            }
            String id = commentId(comment);
            if (id.isEmpty()) {
                toast("没有获取到评论 ID");
                return;
            }
            final boolean beforeLiked = commentLiked(comment);
            final int beforeLikes = commentLikes(comment);
            boolean nextLiked = !beforeLiked;
            int nextLikes = Math.max(0, beforeLikes + (nextLiked ? 1 : -1));
            setCommentLikeState(comment, nextLiked, nextLikes);
            updateCommentLikeView(view, nextLiked, nextLikes);
            view.setEnabled(false);
            postCommentLike(id, nextLiked, new ApiClient.Callback() { // from class: com.openzen.heyboxcommunity.MainActivity.21
                @Override // com.openzen.heyboxcommunity.ApiClient.Callback
                public void onSuccess(JSONObject body) {
                    view.setEnabled(true);
                }

                @Override // com.openzen.heyboxcommunity.ApiClient.Callback
                public void onError(String message) {
                    view.setEnabled(true);
                    MainActivity.this.setCommentLikeState(comment, beforeLiked, beforeLikes);
                    MainActivity.this.updateCommentLikeView(view, beforeLiked, beforeLikes);
                    MainActivity.this.toast("评论点赞失败" + MainActivity.this.writeErrorMessage("评论点赞", message));
                }
            });
        }
    }

    private void setCommentLikeState(JSONObject comment, boolean liked, int likes) {
        try {
            comment.put("is_support", liked ? 1 : REPLY_PREVIEW_COUNT);
            comment.put("comment_award_num", Math.max(0, likes));
        } catch (Exception e) {
        }
    }

    private Map<String, String> commentSupportBody(String id, boolean liked) {
        Map<String, String> body = new HashMap<>();
        body.put("comment_id", id);
        body.put("support_type", liked ? "1" : "2");
        return body;
    }

    private void postCommentLike(String id, boolean liked, ApiClient.Callback callback) {
        runWriteFallback(new WriteStep[]{writeStep("comment-like-web", cb -> {
            this.api.postForm(EndpointProvider.supportComment(), Collections.emptyMap(), commentSupportBody(id, liked), cb);
        }), writeStep("comment-like-web-hsrc", cb2 -> {
            this.api.postForm(EndpointProvider.supportComment(), queryHsrc(this.currentDetailItem), commentSupportBody(id, liked), cb2);
        }), writeStep("comment-like-web-body-hsrc", cb3 -> {
            this.api.postForm(EndpointProvider.supportComment(), Collections.emptyMap(), commentSupportBody(id, liked, this.currentLinkHsrc), cb3);
        })}, callback);
    }

    private Map<String, String> commentSupportBody(String id, boolean liked, String hsrc) {
        Map<String, String> body = commentSupportBody(id, liked);
        if (hsrc != null && !hsrc.isEmpty()) {
            body.put("h_src", hsrc);
        }
        return body;
    }

    private void showCommentDialog(JSONObject replyTo) {
        if (requireLogin(replyTo == null ? "评论" : "回复评论")) {
            if (this.currentLinkId == null || this.currentLinkId.isEmpty()) {
                toast("没有打开的帖子");
                return;
            }
            int dialogBg = this.session.darkMode() ? Color.rgb(30, 31, 33) : Color.rgb(250, 250, 251);
            int inputBg = this.session.darkMode() ? Color.rgb(12, 13, 14) : Color.rgb(241, 242, 244);
            int border = this.session.darkMode() ? Color.rgb(66, 68, 72) : Color.rgb(218, 221, 225);
            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(16), dp(14), dp(16), dp(14));
            Compat.setBackground(panel, roundStroke(dialogBg, 16, border, 1));
            TextView title = text(replyTo == null ? "发表评论" : "回复评论", 16.0f, this.TEXT);
            title.setTypeface(appRegularTypeface(), 1);
            panel.addView(title, new LinearLayout.LayoutParams(-1, -2));
            TextView hint = text(replyTo == null ? "写下你的想法" : "回复这条评论", 11.0f, this.MUTED);
            LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
            hintParams.topMargin = dp(4);
            panel.addView(hint, hintParams);
            EditText input = new EditText(this);
            input.setTextColor(this.TEXT);
            input.setHintTextColor(this.MUTED);
            input.setTextSize(sp(14.0f));
            input.setMinLines(3);
            input.setMaxLines(REPLY_PAGE_SIZE);
            input.setGravity(8388659);
            input.setSingleLine(false);
            input.setHint(replyTo == null ? "友好交流，理性讨论" : "输入回复内容");
            int pad = dp(12);
            input.setPadding(pad, dp(10), pad, dp(10));
            Compat.setBackground(input, roundStroke(inputBg, 14, border, 1));
            LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, dp(118));
            inputParams.topMargin = dp(12);
            panel.addView(input, inputParams);
            AlertDialog dialog = new AlertDialog.Builder(this).setView(panel).create();
            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(17);
            actions.setPadding(0, dp(12), 0, 0);
            TextView cancel = commentDialogAction("取消", false);
            TextView send = commentDialogAction("发送", true);
            LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(38), 1.0f);
            LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(0, dp(38), 1.0f);
            sendParams.leftMargin = dp(9);
            actions.addView(cancel, cancelParams);
            actions.addView(send, sendParams);
            panel.addView(actions);
            cancel.setOnClickListener(view -> {
                runWithPressFeedback(cancel, dialog::dismiss);
            });
            send.setOnClickListener(view -> {
                runWithPressFeedback(send, () -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) {
                        toast("评论不能为空");
                    } else {
                        sendComment(text, replyTo, dialog, send);
                    }
                });
            });
            dialog.show();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0));
                dialog.getWindow().setDimAmount(this.session.darkMode() ? 0.56f : 0.36f);
                int width = getResources().getDisplayMetrics().widthPixels;
                dialog.getWindow().setLayout(Math.max(dp(240), Math.min(width - dp(28), dp(380))), -2);
            }
        }
    }

    private TextView commentDialogAction(String label, boolean primary) {
        int fill = primary ? this.PRIMARY : blend(this.PANEL, this.MUTED, this.session.darkMode() ? 0.22f : 0.09f);
        int stroke = primary ? this.PRIMARY : blend(this.PANEL, this.MUTED, 0.28f);
        int color = primary ? contrast(fill) : this.TEXT;
        TextView view = text(label, 13.0f, color);
        view.setTypeface(appRegularTypeface(), primary ? 1 : 0);
        view.setGravity(17);
        view.setPadding(dp(10), 0, dp(10), 0);
        Compat.setBackground(view, roundStroke(fill, 19, stroke, 1));
        return view;
    }

    private void sendComment(String value, JSONObject replyTo, final AlertDialog dialog, final View sendButton) {
        if (!allowWriteAction("评论")) {
            return;
        }
        String replyId = replyTo == null ? "-1" : commentId(replyTo);
        String rootId = replyTo == null ? "-1" : commentRootId(replyTo);
        if (sendButton != null) {
            sendButton.setEnabled(false);
            sendButton.setAlpha(0.58f);
        }
        postCreateComment(value, rootId, replyId, new ApiClient.Callback() { // from class: com.openzen.heyboxcommunity.MainActivity.22
            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onSuccess(JSONObject body) {
                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                }
                MainActivity.this.toast("评论已发");
                if (MainActivity.this.currentDetailItem != null) {
                    MainActivity.this.showDetail(MainActivity.this.currentDetailItem);
                }
            }

            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onError(String message) {
                if (dialog != null && dialog.isShowing()) {
                    if (sendButton != null) {
                        sendButton.setEnabled(true);
                        sendButton.setAlpha(1.0f);
                    }
                }
                MainActivity.this.toast("评论发送失败：" + MainActivity.this.writeErrorMessage("评论", message));
            }
        });
    }

    private void postCreateComment(String value, String rootId, String replyId, ApiClient.Callback callback) {
        runWriteFallback(new WriteStep[]{writeStep("comment-create-web", cb -> {
            this.api.postForm(EndpointProvider.createComment(), Collections.emptyMap(), commentCreateBody(value, rootId, replyId, false), cb);
        }), writeStep("comment-create-web-auth", cb2 -> {
            this.api.postForm(EndpointProvider.createComment(), commentCreateQuery(), commentCreateBody(value, rootId, replyId, false), cb2);
        }), writeStep("comment-create-web-hsrc", cb3 -> {
            this.api.postForm(EndpointProvider.createComment(), queryHsrc(this.currentDetailItem), commentCreateBody(value, rootId, replyId, false), cb3);
        }), writeStep("comment-create-web-compat", cb4 -> {
            this.api.postForm(EndpointProvider.createComment(), commentCreateQuery(), commentCreateBody(value, rootId, replyId, true), cb4);
        }), writeStep("comment-create-web-compat-no-hsrc", cb5 -> {
            this.api.postForm(EndpointProvider.createComment(), Collections.emptyMap(), commentCreateBody(value, rootId, replyId, true), cb5);
        })}, callback);
    }

    private Map<String, String> commentCreateBody(String value, String rootId, String replyId, boolean compat) {
        Map<String, String> body = new HashMap<>();
        body.put("link_id", this.currentLinkId);
        body.put("text", value);
        body.put("root_id", rootId == null ? "" : rootId);
        body.put("reply_id", replyId == null ? "" : replyId);
        body.put("is_cy", "0");
        if (compat) {
            body.put("recommend_state", "0");
            body.put("linkid", this.currentLinkId);
            body.put("content", value);
            body.put("comment", value);
            body.put("root_comment_id", rootId == null ? "" : rootId);
            body.put("reply_comment_id", replyId == null ? "" : replyId);
        }
        return body;
    }

    private long commentTime(JSONObject comment) {
        return comment.optLong("create_at", comment.optLong("create_time"));
    }

    private String commentId(JSONObject comment) {
        return first(comment.optString("commentid"), first(comment.optString("comment_id"), comment.optString("id")));
    }

    private String commentRootId(JSONObject comment) {
        String root = first(comment.optString("root_id"), comment.optString("root_comment_id"), comment.optString("rootid"));
        return root.isEmpty() ? commentId(comment) : root;
    }

    private void addComment(LinearLayout linearLayout, JSONObject comment, boolean reply) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(48);
        row.setPadding(0, dp(reply ? 2 : 8), 0, dp(reply ? 2 : 8));
        JSONObject user = comment.optJSONObject("user");
        String author = user == null ? "匿名用户" : user.optString("username", "匿名用户");
        if (!reply) {
            ImageView avatar = new ImageView(this);
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Compat.setBackground(avatar, round(this.session.darkMode() ? Color.rgb(50, 53, 56) : Color.rgb(226, 229, 232), NAV_ICON_SIZE_DP));
            Compat.clipToOutline(avatar);
            row.addView(avatar, new LinearLayout.LayoutParams(dp(34), dp(34)));
            String avatarUrl = user == null ? "" : user.optString("avatar");
            if (!this.session.noImage() && !avatarUrl.isEmpty()) {
                ImageLoader.intoPlain(avatar, avatarUrl, 96);
            }
        }
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(1);
        LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        blockParams.leftMargin = reply ? 0 : dp(10);
        row.addView(block, blockParams);
        String target = replyTarget(comment);
        long created = commentTime(comment);
        String visibleComment = RichContent.commentText(comment.optString("text"),
                comment.optString("content"), comment.optString("html"),
                comment.optString("description"));
        if (reply) {
            addCompactReply(block, comment, author, target, visibleComment, created);
        } else {
            block.addView(commentNameRow(author, user, true));
            String meta = commentSubMeta(comment, created);
            if (!meta.isEmpty()) {
                TextView metaView = text(meta, 10.5f, this.MUTED);
                addTop(block, metaView, 1);
            }
            TextView value = text(visibleComment, 13.0f, this.TEXT);
            value.setLineSpacing(dp(1), this.session.bodyLineSpacing() / 100.0f);
            Compat.setLetterSpacing(value, this.session.bodyLetterSpacing() / 200.0f);
            value.setTypeface(Typeface.create("sans-serif-medium", 0));
            EmojiRenderer.set(value, visibleComment, this.session.darkMode());
            addTop(block, value, 5);
        }
        String commentImage = commentImage(comment);
        if (!this.session.noImage() && !commentImage.isEmpty()) {
            View image = postImageBlock(commentImage, 720, reply ? 92 : 115);
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(-1, dp(reply ? 92 : 115));
            imageParams.topMargin = dp(6);
            block.addView(image, imageParams);
        }
        if (!reply) {
            TextView likes = text(String.valueOf(commentLikes(comment)), 10.0f, this.MUTED);
            updateCommentLikeView(likes, commentLiked(comment), commentLikes(comment));
            likes.setGravity(17);
            likes.setOnClickListener(view -> {
                toggleCommentLike(comment, likes);
            });
            LinearLayout.LayoutParams likeParams = new LinearLayout.LayoutParams(dp(48), dp(26));
            likeParams.leftMargin = dp(5);
            row.addView(likes, likeParams);
        }
        attachCommentReplyGesture(row, comment);
        linearLayout.addView(row);
        if (reply) {
            View divider = new View(this);
            divider.setBackgroundColor(blend(this.BG, this.MUTED, this.session.darkMode() ? 0.10f : 0.05f));
            linearLayout.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        }
    }

    private void addCompactReply(LinearLayout block, JSONObject comment, String author,
                                 String target, String visibleComment, long created) {
        // 官方样式：整段文字流（蓝色用户名 + 回复对象 + 冒号 + 内容 + 时间），自然换行不截断
        final String name = author.isEmpty() ? "匿名用户" : author;
        final boolean hasTarget = !target.isEmpty();
        final String replyLabel = hasTarget ? " 回复 " : "";
        final String replyName = hasTarget ? "@" + target : "";
        final String meta = commentSubMeta(comment, created);
        final String metaSeg = meta.isEmpty() ? "" : "  " + meta;
        final String full = name + replyLabel + replyName + "：" + visibleComment + metaSeg;

        final int nameEnd = name.length();
        final int replyNameStart = nameEnd + replyLabel.length();
        final int replyNameEnd = replyNameStart + replyName.length();
        final int metaStart = full.length() - meta.length();
        final int metaEnd = full.length();
        final int nameColor = this.SECONDARY;
        final int metaColor = this.MUTED;

        TextView value = text(full, 12.5f, this.TEXT);
        value.setLineSpacing(dp(1), this.session.bodyLineSpacing() / 100.0f);
        value.setPadding(0, dp(2), 0, dp(3));
        EmojiRenderer.set(value, full, this.session.darkMode(), span -> {
            span.setSpan(new android.text.style.ForegroundColorSpan(nameColor), 0, nameEnd,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            span.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, nameEnd,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (hasTarget) {
                span.setSpan(new android.text.style.ForegroundColorSpan(nameColor),
                        replyNameStart, replyNameEnd, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (!meta.isEmpty()) {
                span.setSpan(new android.text.style.ForegroundColorSpan(metaColor),
                        metaStart, metaEnd, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        });
        block.addView(value, new LinearLayout.LayoutParams(-1, -2));
    }

    private LinearLayout commentNameRow(String author, JSONObject user, boolean strong) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(16);
        row.addView(commentNameText(author, false), new LinearLayout.LayoutParams(-2, -2));
        addLevelBadge(row, user, false);
        return row;
    }

    private TextView commentNameText(String author, boolean reply) {
        TextView name = text(author.isEmpty() ? "匿名用户" : author,
                reply ? 11.5f : 12.5f, reply ? this.SECONDARY : this.TEXT);
        name.setTypeface(appRegularTypeface(), 1);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setMaxWidth(dp(reply ? 88 : 150));
        return name;
    }

    private void addLevelBadge(LinearLayout row, JSONObject user, boolean small) {
        int level = userLevel(user);
        if (level <= 0) return;
        TextView badge = text("Lv." + level, small ? 7.0f : 8.0f, -1);
        badge.setGravity(17);
        badge.setTypeface(appRegularTypeface(), 1);
        badge.setPadding(dp(4), 0, dp(4), 0);
        Compat.setBackground(badge, round(levelBadgeColor(level), 3));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(small ? 13 : 15));
        params.leftMargin = dp(4);
        params.rightMargin = dp(4);
        row.addView(badge, params);
    }

    private int levelBadgeColor(int level) {
        if (level >= 40) return Color.rgb(223, 153, 45);
        if (level >= 30) return Color.rgb(139, 100, 235);
        if (level >= 20) return Color.rgb(55, 178, 218);
        if (level >= 10) return Color.rgb(238, 70, 112);
        return Color.rgb(70, 168, 240);
    }

    private String commentSubMeta(JSONObject comment, long created) {
        String time = created > 0 ? commentDisplayTime(created) : "";
        String location = commentLocation(comment);
        if (time.isEmpty()) return location;
        return location.isEmpty() ? time : time + " · " + location;
    }

    private String commentDisplayTime(long seconds) {
        long millis = seconds > 100000000000L ? seconds : seconds * 1000L;
        long diff = Math.max(0L, System.currentTimeMillis() - millis);
        long minute = 60L * 1000L;
        long hour = 60L * minute;
        long day = 24L * hour;
        if (diff < minute) return "刚刚";
        if (diff < hour) return Math.max(1L, diff / minute) + "分钟前";
        if (diff < day) return Math.max(1L, diff / hour) + "小时前";
        return new SimpleDateFormat("MM-dd", Locale.getDefault()).format(new Date(millis));
    }

    private String commentLocation(JSONObject comment) {
        if (comment == null) return "";
        String direct = first(comment.optString("ip_location"), comment.optString("ipLocation"),
                comment.optString("ip_region"), comment.optString("ipRegion"),
                comment.optString("location"), comment.optString("area"),
                comment.optString("province"), comment.optString("city"),
                comment.optString("region"), comment.optString("address"));
        if (!direct.isEmpty()) return direct;
        JSONObject ip = firstObject(comment, "ip_info", "ipInfo", "location_info", "locationInfo");
        if (ip == null) return "";
        return first(ip.optString("location"), ip.optString("region"),
                ip.optString("province"), ip.optString("city"), ip.optString("area"));
    }

    private JSONObject firstObject(JSONObject object, String... keys) {
        if (object == null) return null;
        for (String key : keys) {
            JSONObject found = object.optJSONObject(key);
            if (found != null) return found;
        }
        return null;
    }

    private void attachCommentReplyGesture(View target, JSONObject comment) {
        target.setOnClickListener(new View.OnClickListener() {
            private long lastTapAt;

            @Override
            public void onClick(View view) {
                if (!MainActivity.this.session.doubleTapCommentReply()) {
                    return;
                }
                long now = System.currentTimeMillis();
                if (now - this.lastTapAt <= 320L) {
                    this.lastTapAt = 0L;
                    MainActivity.this.showCommentDialog(comment);
                } else {
                    this.lastTapAt = now;
                }
            }
        });
    }

    private String commentImage(JSONObject comment) {
        JSONArray images = comment.optJSONArray("imgs");
        if (images == null || images.length() == 0) {
            return "";
        }
        JSONObject object = images.optJSONObject(0);
        if (object != null) {
            return first(object.optString("url"), first(object.optString("src"), object.optString("original")));
        }
        return images.optString(0);
    }

    private String replyTarget(JSONObject comment) {
        JSONObject target = comment.optJSONObject("to_user");
        if (target == null) {
            target = comment.optJSONObject("reply_user");
        }
        if (target == null) {
            target = comment.optJSONObject("target_user");
        }
        return target == null ? "" : target.optString("username", target.optString("nickname"));
    }

    private void showProfile() {
        if (!this.session.isLoggedIn()) {
            showGuestProfile();
            return;
        }
        activate("profile");
        this.title.setText("我的");
        this.action.setText("");
        setIcon(this.action, R.drawable.il_refresh, this.TEXT, 19);
        this.action.setVisibility(0);
        this.action.setOnClickListener(view -> {
            this.cachedProfileContainer = null;
            showProfile();
        });
        if (this.cachedProfileContainer != null && this.cachedProfileLoggedIn && this.session.userId().equals(this.cachedProfileUserId) && this.cachedProfileContainer.getParent() == null) {
            transitionTo(this.cachedProfileContainer);
        } else {
            this.pendingBackTransition = false;
            PageTransitionController.finishNow();
            this.content.removeAllViews();
            showLoading();
            this.api.get(EndpointProvider.profile(), Collections.singletonMap(SecureStrings.userid(), this.session.userId()), new ApiClient.Callback() { // from class: com.openzen.heyboxcommunity.MainActivity.23
                @Override // com.openzen.heyboxcommunity.ApiClient.Callback
                public void onSuccess(JSONObject body) {
                    MainActivity.this.hideLoading();
                    MainActivity.this.renderProfile(body);
                }

                @Override // com.openzen.heyboxcommunity.ApiClient.Callback
                public void onError(String message) {
                    MainActivity.this.hideLoading();
                    MainActivity.this.toast("个人资料加载失败" + message);
                    MainActivity.this.renderProfile(new JSONObject());
                }
            });
        }
    }

    private void showGuestProfile() {
        activate("profile");
        this.title.setText("我的");
        this.action.setVisibility(0);
        setIcon(this.action, R.drawable.il_refresh, this.TEXT, 19);
        this.action.setOnClickListener(view -> {
            this.cachedProfileContainer = null;
            showProfile();
        });
        if (this.cachedProfileContainer != null && !this.cachedProfileLoggedIn && this.cachedProfileContainer.getParent() == null) {
            transitionTo(this.cachedProfileContainer);
            return;
        }
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = vertical(this.BG);
        page.setPadding(dp(8), dp(8), dp(8), dp(12));
        scroll.addView(page);
        LinearLayout profile = card();
        LinearLayout headRow = new LinearLayout(this);
        headRow.setGravity(16);
        ImageView avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int avatarBg = this.session.darkMode() ? Color.rgb(42, 43, 45) : Color.rgb(228, 230, 233);
        Compat.setBackground(avatar, round(avatarBg, 24));
        Drawable personIcon = Compat.tintedDrawable(this, R.drawable.il_person, this.MUTED);
        if (personIcon != null) {
            avatar.setImageDrawable(personIcon);
        }
        avatar.setPadding(dp(11), dp(11), dp(11), dp(11));
        headRow.addView(avatar, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout headCopy = vertical(0);
        TextView name = text("未登录", 18.0f, this.TEXT);
        name.setTypeface(appRegularTypeface(), 1);
        headCopy.addView(name);
        headCopy.addView(text("登录后可点赞、收藏、签到", 12.0f, this.MUTED));
        LinearLayout.LayoutParams headCopyParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        headCopyParams.leftMargin = dp(12);
        headRow.addView(headCopy, headCopyParams);
        profile.addView(headRow);
        Button login = button("扫码登录", R.drawable.il_qr);
        login.setOnClickListener(view -> {
            showLogin();
        });
        addTop(profile, login, 13);
        page.addView(profile);
        animateIn(profile);
        addProfileMenu(page, false);
        addBottomNavSafeSpace(page);
        this.cachedProfileContainer = scroll;
        this.cachedProfileLoggedIn = false;
        this.cachedProfileUserId = "";
        transitionTo(scroll);
    }

    private void renderProfile(JSONObject body) {
        JSONObject result = body.optJSONObject("result");
        JSONObject account = result == null ? null : result.optJSONObject("account_detail");
        if (account == null && result != null) {
            account = result.optJSONObject("user");
        }
        ScrollView scrollView = new ScrollView(this);
        LinearLayout linearLayoutVertical = vertical(this.BG);
        linearLayoutVertical.setPadding(dp(8), dp(8), dp(8), dp(12));
        scrollView.addView(linearLayoutVertical);
        LinearLayout profile = card();
        LinearLayout headRow = new LinearLayout(this);
        headRow.setGravity(16);
        ImageView avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int avatarBg = this.session.darkMode() ? Color.rgb(42, 43, 45) : Color.rgb(228, 230, 233);
        Compat.setBackground(avatar, round(avatarBg, 28));
        Compat.clipToOutline(avatar);
        String avatarUrl = account == null ? this.session.avatar() : account.optString("avatar", this.session.avatar());
        if (!this.session.noImage() && !avatarUrl.isEmpty()) {
            ImageLoader.intoPlain(avatar, avatarUrl, 160);
        } else {
            Drawable placeholder = Compat.tintedDrawable(this, R.drawable.il_person, this.MUTED);
            if (placeholder != null) {
                avatar.setImageDrawable(placeholder);
                avatar.setPadding(dp(13), dp(13), dp(13), dp(13));
            }
        }
        headRow.addView(avatar, new LinearLayout.LayoutParams(dp(56), dp(56)));
        LinearLayout headCopy = vertical(0);
        String nameValue = account == null ? this.session.userName() : account.optString("username", this.session.userName());
        TextView name = text(nameValue.isEmpty() ? "小黑盒用户" : nameValue, 18.0f, this.TEXT);
        name.setTypeface(appRegularTypeface(), 1);
        headCopy.addView(name);
        headCopy.addView(text("ID " + this.session.userId(), 11.0f, this.MUTED));
        JSONObject bbs = account == null ? null : account.optJSONObject("bbs_info");
        if (bbs != null) {
            TextView stats = text("关注 " + bbs.optInt("follow_num") + "  粉丝 "
                    + bbs.optInt("fan_num") + "  获赞 " + bbs.optInt("up_num"), 12.0f, this.SECONDARY);
            LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(-2, -2);
            statsParams.topMargin = dp(4);
            headCopy.addView(stats, statsParams);
        }
        LinearLayout.LayoutParams headCopyParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        headCopyParams.leftMargin = dp(12);
        headRow.addView(headCopy, headCopyParams);
        profile.addView(headRow);
        String signature = account == null ? "" : account.optString("signature");
        if (!signature.isEmpty()) {
            addTop(profile, text(signature, 13.0f, this.TEXT), 11);
        }
        linearLayoutVertical.addView(profile);
        animateIn(profile);
        addProfileMenu(linearLayoutVertical, true);
        addBottomNavSafeSpace(linearLayoutVertical);
        this.cachedProfileContainer = scrollView;
        this.cachedProfileLoggedIn = true;
        this.cachedProfileUserId = this.session.userId();
        transitionTo(scrollView);
    }

    private String signInButtonText(SignInManager.Result state) {
        return (state == null || !state.loggedIn) ? "去登录" : state.inFlight ? "签到" : state.success ? "重新检查" : "签到";
    }

    private void showSignInCredentialImportDialog() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(1);
        panel.setPadding(dp(16), dp(14), dp(16), dp(12));
        Compat.setBackground(panel, round(this.PANEL, 14));
        TextView title = text("导入签到环境", 16.0f, this.TEXT);
        title.setTypeface(appRegularTypeface(), 1);
        panel.addView(title);
        TextView hint = text("粘贴官方成功签到请求 URL、Cookie、HAR 片段key=value 文本。只会保存到签到隔离区，不会覆盖主登录", 11.0f, this.MUTED);
        hint.setLineSpacing(0.0f, 1.18f);
        addTop(panel, hint, 8);
        EditText input = new EditText(this);
        input.setMinLines(5);
        input.setMaxLines(8);
        input.setTextSize(sp(11.0f));
        input.setTextColor(this.TEXT);
        input.setHintTextColor(this.MUTED);
        input.setHint("https://api.xiaoheihe.cn/task/sign_v3/get_sign_state?...\\nCookie: pkey=...; x_xhh_tokenid=...");
        Compat.tint(input, this.PRIMARY);
        addTop(panel, input, 8);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel).create();
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(0);
        actions.setPadding(0, dp(8), 0, 0);
        Button cancel = button("取消", R.drawable.ic_logout);
        cancel.setOnClickListener(view -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(38), 1.0f));
        Button save = button("保存", R.drawable.ic_settings);
        save.setOnClickListener(view -> {
            String result = this.session.importSignInCredentialsFromText(input.getText().toString());
            if (this.localCache != null) {
                this.localCache.log("sign-in manual credential import: "
                        + result + " " + this.session.signInCredentialSummaryForLog());
            }
            toast(result.contains("ok-isolated") ? "已保存签到环境" : "没有提取到可用 pkey");
            dialog.dismiss();
            if ("profile".equals(this.screen)) {
                showProfile();
            }
        });
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp(38), 1.0f);
        saveParams.leftMargin = dp(8);
        actions.addView(save, saveParams);
        panel.addView(actions);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0));
            int width = getResources().getDisplayMetrics().widthPixels;
            dialog.getWindow().setLayout(Math.max(dp(230), Math.min(width - dp(24), dp(380))), -2);
        }
    }

    private void showSettingsHome() {
        stopQrPolling();
        this.screen = "settings_home";
        setBottomNavVisible(false);
        this.leading.setVisibility(0);
        this.leading.setOnClickListener(view -> {
            this.pendingBackTransition = true;
            showProfile();
        });
        this.title.setText("设置中心");
        this.action.setVisibility(4);
        View settingsHome = buildSettingsHomeContent();
        transitionTo(settingsHome);
    }

    private View buildSettingsHomeContent() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = vertical(this.BG);
        page.setPadding(dp(8), dp(8), dp(8), dp(14));
        scroll.addView(page);
        page.addView(settingsTopCard("设置中心"));
        LinearLayout panel = card();
        addSettingEntry(panel, "显示与主题", "主题、字号、间距与界面预览", R.drawable.il_palette, this::showDisplaySettings);
        addSettingEntry(panel, "启动与更新", "开屏动画、开屏文字与自动更新", R.drawable.il_refresh, this::showStartupSettings);
        addSettingEntry(panel, "内容与网络", "图片加载、缓存与登录状态", R.drawable.il_globe, this::showAppSettings);
        addSettingEntry(panel, "关于 heybox Lite", "版本、项目说明与免责声明", R.drawable.il_info, this::showAbout);
        page.addView(panel);
        return scroll;
    }

    private void addSettingEntry(LinearLayout parent, String name, String description, int icon, Runnable action) {
        if (parent.getChildCount() > 0) {
            View divider = new View(this);
            divider.setBackgroundColor(this.session.darkMode()
                    ? Color.argb(26, 255, 255, 255) : Color.argb(20, 0, 0, 0));
            LinearLayout.LayoutParams dividerParams =
                    new LinearLayout.LayoutParams(-1, Math.max(1, dp(1) / 2));
            dividerParams.leftMargin = dp(44);
            parent.addView(divider, dividerParams);
        }
        LinearLayout row = new LinearLayout(this);
        row.setGravity(16);
        row.setPadding(dp(6), dp(14), dp(6), dp(14));
        ImageView marker = new ImageView(this);
        marker.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        Drawable iconDrawable = Compat.tintedDrawable(this, icon, this.TEXT);
        if (iconDrawable != null) {
            marker.setImageDrawable(iconDrawable);
        }
        LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(dp(24), dp(24));
        markerParams.rightMargin = dp(14);
        row.addView(marker, markerParams);
        LinearLayout copy = vertical(0);
        TextView titleView = text(name, 14.5f, this.TEXT);
        titleView.setTypeface(appRegularTypeface(), 1);
        copy.addView(titleView);
        TextView descView = text(description, 11.0f, this.MUTED);
        descView.setPadding(0, dp(1), 0, 0);
        copy.addView(descView);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1.0f));
        ImageView arrow = new ImageView(this);
        arrow.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        Drawable chevron = Compat.tintedDrawable(this, R.drawable.il_chevron, this.MUTED);
        if (chevron != null) {
            arrow.setImageDrawable(chevron);
        }
        arrow.setAlpha(0.55f);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(18), dp(18)));
        row.setOnClickListener(view -> {
            runWithPressFeedback(view, action);
        });
        parent.addView(row);
        animateIn(row);
    }

    private void addProfileMenu(LinearLayout page, boolean loggedIn) {
        LinearLayout panel = card();
        addSettingEntry(panel, "稍后看", "本地保存的帖子与离线内容", R.drawable.il_history,
                this::showWatchLater);
        addSettingEntry(panel, "收藏", "我收藏的帖子", R.drawable.il_bookmark, () -> {
            if (!this.session.isLoggedIn()) {
                showLogin();
                return;
            }
            showFavorites();
        });
        addSettingEntry(panel, "历史", "浏览记录", R.drawable.il_history, () -> {
            if (!this.session.isLoggedIn()) {
                showLogin();
                return;
            }
            Map<String, String> params = new HashMap<>();
            params.put("type", "all");
            params.put("dw", "636");
            params.put("no_more", "false");
            showSavedList("浏览历史", EndpointProvider.history(), params);
        });
        SignInManager.Result state = this.signInManager == null
                ? SignInManager.Result.loggedOut() : this.signInManager.currentState();
        String signSub = state.message;
        addSettingEntry(panel, "每日签到", signSub, R.drawable.il_calendar, () -> {
            toast("签到功能已暂时关闭");
        });
        addSettingEntry(panel, "设置中心", "主题、缓存、字号与关于", R.drawable.il_settings,
                this::showSettingsHome);
        addTop(page, panel, 8);
        animateIn(panel);
    }

    private void showWatchLater() {
        prepareSavedPage("稍后看");
        List<LocalCache.OfflineItem> items = this.localCache.watchLaterItems();
        if (items.isEmpty()) {
            showMessage("还没有稍后看的帖子\n打开帖子后点“稍后看”即可离线保存");
            return;
        }
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = vertical(this.BG);
        page.setPadding(dp(7), dp(7), dp(7), dp(18));
        scroll.addView(page);
        for (LocalCache.OfflineItem entry : items) {
            page.addView(watchLaterCard(entry));
        }
        addBottomNavSafeSpace(page);
        this.content.addView(scroll, match());
    }

    private View watchLaterCard(LocalCache.OfflineItem entry) {
        LinearLayout block = vertical(this.PANEL);
        Compat.setBackground(block, round(this.PANEL, 6));
        block.addView(userEventCard(entry.item));
        View divider = new View(this);
        divider.setBackgroundColor(this.themeTokens == null
                ? blend(this.PANEL, this.MUTED, 0.14f) : this.themeTokens.hairline);
        block.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(16);
        footer.setPadding(dp(12), dp(7), dp(8), dp(7));
        long bytes = entry.detailBytes + ImageLoader.offlineBytes(entry.imageUrls);
        String size = bytes > 0L ? formatOfflineSize(bytes) : "缓存已过期";
        TextView info = text(size + " · 更新于 " + formatOfflineTime(entry.updatedAt), 10.0f, this.MUTED);
        footer.addView(info, new LinearLayout.LayoutParams(0, dp(28), 1.0f));
        TextView remove = text("移除", 11.0f, this.SECONDARY);
        remove.setGravity(17);
        remove.setPadding(dp(10), 0, dp(10), 0);
        remove.setOnClickListener(view -> {
            this.localCache.removeWatchLater(entry.item.id);
            showWatchLater();
        });
        footer.addView(remove, new LinearLayout.LayoutParams(-2, dp(28)));
        block.addView(footer);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(7);
        block.setLayoutParams(params);
        return block;
    }

    private String formatOfflineSize(long bytes) {
        if (bytes < 1024L * 1024L) {
            return Math.max(1L, bytes / 1024L) + " KB";
        }
        return formatCacheMb(bytes);
    }

    private String formatOfflineTime(long millis) {
        if (millis <= 0L) return "未知";
        return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    private void showSignInDialog() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(1);
        panel.setPadding(dp(16), dp(14), dp(16), dp(12));
        Compat.setBackground(panel, round(this.PANEL, 14));
        TextView heading = text("每日签到", 16.0f, this.TEXT);
        heading.setTypeface(appRegularTypeface(), 1);
        panel.addView(heading);
        SignInManager.Result state = this.signInManager == null
                ? SignInManager.Result.loggedOut() : this.signInManager.currentState();
        TextView status = text(state.message, 13.0f, state.success ? this.SECONDARY : this.MUTED);
        status.setTypeface(Typeface.create("sans-serif-medium", 0));
        addTop(panel, status, 6);
        TextView summary = text(state.summary, 11.0f, this.MUTED);
        summary.setLineSpacing(0.0f, 1.18f);
        addTop(panel, summary, 4);

        ScrollView dialogScroll = new ScrollView(this);
        dialogScroll.addView(panel);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogScroll).create();

        Button action = button(signInButtonText(state), R.drawable.ic_refresh);
        action.setEnabled(!state.inFlight);
        action.setOnClickListener(view -> {
            if (!this.session.isLoggedIn()) {
                dialog.dismiss();
                showLogin();
                return;
            }
            long now = System.currentTimeMillis();
            if (now - this.lastManualSignClickAt < 1200) {
                return;
            }
            this.lastManualSignClickAt = now;
            status.setText("正在签到");
            summary.setText("正在向小黑盒提交请求");
            action.setEnabled(false);
            this.signInManager.signIn(result -> {
                if (isFinishing()) {
                    return;
                }
                status.setText(result.message);
                summary.setText(result.summary);
                action.setEnabled(true);
                action.setText(signInButtonText(result));
                this.cachedProfileContainer = null;
            });
        });
        addTop(panel, action, 10);

        TextView advHeading = text("签到环境（实验功能）", 11.0f, this.MUTED);
        addTop(panel, advHeading, 14);
        TextView credential = text((this.session.hasSignInCredentials()
                || this.session.hasSignInReplayRequest())
                ? "签到环境：已保存" : "签到环境：未导入", 10.0f, this.MUTED);
        addTop(panel, credential, 4);
        LinearLayout advRow = new LinearLayout(this);
        advRow.setOrientation(0);
        Button importCredential = button("导入", R.drawable.ic_refresh);
        importCredential.setOnClickListener(view -> {
            String result = this.session.importOfficialProviderAuthForSignInLog();
            if (!result.contains("ok-isolated")) {
                result = result + " fallback=" + this.session.importCurrentSessionForSignInLog();
            }
            if (this.localCache != null) {
                this.localCache.log("sign-in isolated credential import: " + result);
            }
            toast(result.contains("ok-isolated") ? "已导入签到凭据" : "没有读到可用签到凭据");
            credential.setText(result.contains("ok-isolated")
                    ? "签到环境：已保存" : "签到环境：未导入");
        });
        advRow.addView(importCredential, new LinearLayout.LayoutParams(0, dp(34), 1.0f));
        Button pasteCredential = button("粘贴", R.drawable.ic_info);
        pasteCredential.setOnClickListener(view -> {
            dialog.dismiss();
            showSignInCredentialImportDialog();
        });
        LinearLayout.LayoutParams pasteParams = new LinearLayout.LayoutParams(0, dp(34), 1.0f);
        pasteParams.leftMargin = dp(6);
        advRow.addView(pasteCredential, pasteParams);
        Button clearCredential = button("清除", R.drawable.ic_trash);
        clearCredential.setOnClickListener(view -> {
            this.session.clearSignInCredentials();
            toast("已清除签到凭据");
            credential.setText("签到环境：未导入");
        });
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(0, dp(34), 1.0f);
        clearParams.leftMargin = dp(6);
        advRow.addView(clearCredential, clearParams);
        addTop(panel, advRow, 6);

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0));
            int width = getResources().getDisplayMetrics().widthPixels;
            dialog.getWindow().setLayout(
                    Math.max(dp(220), Math.min(width - dp(24), dp(360))), -2);
        }
    }

    private void showFavorites() {
        prepareSavedPage(TITLE_FAVORITES);
        showLoading();
        this.api.get(EndpointProvider.favoriteTabs(), Collections.emptyMap(), new ApiClient.Callback() { // from class: com.openzen.heyboxcommunity.MainActivity.24
            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onSuccess(JSONObject body) {
                MainActivity.this.localCache.log("favorite tabs loaded: " + MainActivity.this.favoriteTabSummary(body));
                MainActivity.this.showFavoriteContents("tab ok");
            }

            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onError(String message) {
                MainActivity.this.localCache.log("favorite tabs failed, trying content: " + message);
                MainActivity.this.showFavoriteContents(message);
            }
        });
    }

    private void showFavoriteContents(String reason) {
        Map<String, String> params = favoriteParams("");
        params.put("limit", "30");
        showSavedList(TITLE_FAVORITES, EndpointProvider.favoriteLinks(), params, false, fallbackReason -> {
            showFavoriteFoldersFallback(reason + "; " + fallbackReason);
            return true;
        });
    }

    private void showFavoriteFoldersFallback(String reason) {
        this.localCache.log("favorite content fallback to folders: " + reason);
        prepareSavedPage(TITLE_FAVORITES);
        showLoading();
        Map<String, String> folderParams = new HashMap<>();
        folderParams.put("enable_new_style_collect", "1");
        folderParams.put("x_os_type", "Windows");
        folderParams.put("device_info", "Edge");
        this.api.get(EndpointProvider.favoriteFolders(), folderParams, new ApiClient.Callback() { // from class: com.openzen.heyboxcommunity.MainActivity.25
            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onSuccess(JSONObject body) {
                JSONObject folder = MainActivity.this.firstFavoriteFolder(MainActivity.this.findFavoriteFolders(body));
                String folderId = MainActivity.this.favoriteFolderId(folder);
                if (folder == null) {
                    MainActivity.this.localCache.log("favorite folders missing: " + MainActivity.this.favoriteFolderSummary(body));
                    MainActivity.this.showFavoriteLegacyList("folders missing");
                } else if (folderId.isEmpty()) {
                    MainActivity.this.localCache.log("favorite folder id missing: " + folder);
                    MainActivity.this.showFavoriteLegacyList("folder id missing");
                } else {
                    MainActivity.this.showSavedList(MainActivity.TITLE_FAVORITES, EndpointProvider.favoriteLinks(), MainActivity.this.favoriteParams(folderId), false, null);
                }
            }

            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onError(String message) {
                MainActivity.this.localCache.log("favorite folders failed: " + message);
                MainActivity.this.showFavoriteLegacyList(message);
            }
        });
    }

    private void showFavoriteLegacyList(String reason) {
        if (this.localCache != null) {
            this.localCache.log("favorite fallback to legacy list: " + reason);
        }
        Map<String, String> params = new HashMap<>();
        params.put("type", "link");
        params.put("limit", "30");
        showSavedList(TITLE_FAVORITES, EndpointProvider.favoriteList(), params, false, fallbackReason -> {
            showFavoritesUnavailable(null, reason + "; " + fallbackReason);
            return true;
        });
    }

    private String favoriteTabSummary(JSONObject body) {
        if (body == null) {
            return "no body";
        }
        JSONObject result = body.optJSONObject("result");
        JSONArray tabs = result == null ? null : result.optJSONArray("tab_list");
        return "status=" + body.optString("status") + ", tabs=" + (tabs == null ? -1 : tabs.length()) + ", msg=" + first(body.optString("msg"), body.optString("message"));
    }

    private void showFavoritesUnavailable(JSONObject body, String message) {
        hideLoading();
        prepareSavedPage(TITLE_FAVORITES);
        String cacheKey = savedCacheKey(TITLE_FAVORITES, EndpointProvider.favoriteLinks());
        List<FeedItem> cached = filterItems(this.localCache.savedList(cacheKey));
        if (!cached.isEmpty()) {
            toast(MSG_OFFLINE_CACHE);
            renderSavedItems(TITLE_FAVORITES, cached);
            return;
        }
        if (message != null && !message.isEmpty()) {
            this.localCache.log("favorite unavailable reason: " + message);
        }
        if (body != null) {
            this.localCache.log("favorite unavailable body: " + favoriteFolderSummary(body));
        }
        showMessage(MSG_FAVORITES_UNAVAILABLE);
    }

    private String favoriteFolderSummary(JSONObject body) {
        if (body == null) {
            return "no body";
        }
        JSONObject result = body.optJSONObject("result");
        JSONObject source = result == null ? body : result;
        JSONArray folders = source.optJSONArray("folders");
        int folderCount = folders == null ? -1 : folders.length();
        return "status=" + body.optString("status") + ", msg=" + first(body.optString("msg"), body.optString("message")) + ", folders=" + folderCount + ", favour_post_num=" + source.optInt("favour_post_num", source.optInt("favor_post_num", source.optInt("favorite_post_num", -1)));
    }

    private Map<String, String> favoriteParams(String folderId) {
        Map<String, String> params = new HashMap<>();
        if (folderId != null && !folderId.isEmpty()) {
            params.put("folder_id", folderId);
            params.put("folderid", folderId);
            params.put("fav_folder_id", folderId);
            params.put("collect_folder_id", folderId);
        }
        params.put("enable_new_style_collect", "1");
        params.put("dw", "604");
        params.put("no_more", "false");
        return params;
    }

    private JSONArray findFavoriteFolders(JSONObject body) {
        JSONObject result = body == null ? null : body.optJSONObject("result");
        JSONArray folders = findFavoriteFolderArray(result, 0);
        return folders == null ? findFavoriteFolderArray(body, 0) : folders;
    }

    private JSONArray findFavoriteFolderArray(Object node, int depth) {
        if (node == null || depth > REPLY_PAGE_SIZE) {
            return null;
        }
        if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            if (looksLikeFolderArray(array)) {
                return array;
            }
            return null;
        }
        if (!(node instanceof JSONObject)) {
            return null;
        }
        JSONObject object = (JSONObject) node;
        String[] keys = {"folders", "folder_list", "fav_folders", "favorite_folders", "collect_folders", "collections", "list", "items", "data"};
        for (String key : keys) {
            JSONArray array2 = object.optJSONArray(key);
            if (array2 != null && looksLikeFolderArray(array2)) {
                return array2;
            }
        }
        Iterator<String> names = object.keys();
        while (names.hasNext()) {
            Object child = object.opt(names.next());
            JSONArray found = findFavoriteFolderArray(child, depth + 1);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private boolean looksLikeFolderArray(JSONArray array) {
        if (array == null || array.length() == 0) {
            return false;
        }
        int limit = Math.min(6, array.length());
        for (int i = 0; i < limit; i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) {
                JSONObject folder = unwrapFavoriteFolder(item);
                if (!favoriteFolderId(folder).isEmpty()) {
                    return true;
                }
                if (folder != null && !first(folder.optString("folder_name"), folder.optString("name"), folder.optString("title")).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private JSONObject firstFavoriteFolder(JSONArray folders) {
        if (folders == null) {
            return null;
        }
        JSONObject fallback = null;
        for (int i = 0; i < folders.length(); i++) {
            JSONObject folder = unwrapFavoriteFolder(folders.optJSONObject(i));
            if (folder != null) {
                if (fallback == null) {
                    fallback = folder;
                }
                if (!favoriteFolderId(folder).isEmpty()) {
                    return folder;
                }
            }
        }
        return fallback;
    }

    private JSONObject unwrapFavoriteFolder(JSONObject item) {
        JSONObject current = item;
        for (int depth = 0; depth < 4 && current != null; depth++) {
            if (!favoriteFolderId(current).isEmpty()) {
                return current;
            }
            JSONObject next = current.optJSONObject("folder");
            if (next == null) {
                next = current.optJSONObject("folder_info");
            }
            if (next == null) {
                next = current.optJSONObject("fav_folder");
            }
            if (next == null) {
                next = current.optJSONObject("collect_folder");
            }
            if (next == null) {
                next = current.optJSONObject("collection");
            }
            if (next == null) {
                next = current.optJSONObject("data");
            }
            if (next == null) {
                next = current.optJSONObject("item");
            }
            if (next == current) {
                break;
            }
            current = next;
        }
        return current;
    }

    private String favoriteFolderId(JSONObject folder) {
        return folder == null ? "" : first(folder.optString("folder_id"), folder.optString("folderid"), folder.optString("fav_folder_id"), folder.optString("collect_folder_id"), folder.optString("collection_id"), folder.optString("id"), folder.optString("fid"));
    }

    private void prepareSavedPage(String pageTitle) {
        stopQrPolling();
        ensureEmojiCatalog(() -> {
        });
        this.screen = "saved";
        setBottomNavVisible(false);
        this.leading.setVisibility(0);
        this.leading.setOnClickListener(view -> {
            showProfile();
        });
        this.title.setText(pageTitle);
        this.action.setVisibility(4);
        this.content.removeAllViews();
    }

    private void showSavedList(String pageTitle, String path, Map<String, String> params) {
        showSavedList(pageTitle, path, params, true, null);
    }

    private void showSavedList(final String pageTitle, final String path, final Map<String, String> params, boolean includeUserId, final SavedListFallback fallback) {
        prepareSavedPage(pageTitle);
        showLoading();
        if (!params.containsKey("offset")) {
            params.put("offset", "0");
        }
        if (!params.containsKey("limit")) {
            params.put("limit", "20");
        }
        if (includeUserId) {
            params.put(SecureStrings.userid(), this.session.userId());
        }
        params.put("x_os_type", "Windows");
        params.put("device_info", "Edge");
        final String cacheKey = savedCacheKey(pageTitle, path);
        this.api.get(path, params, new ApiClient.Callback() { // from class: com.openzen.heyboxcommunity.MainActivity.26
            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onSuccess(JSONObject body) {
                JSONObject value;
                MainActivity.this.hideLoading();
                JSONObject result = body.optJSONObject("result");
                JSONArray links = MainActivity.this.findLinks(result == null ? body : result);
                List<FeedItem> items = new ArrayList<>();
                if (links != null) {
                    for (int i = 0; i < links.length(); i++) {
                        JSONObject item = links.optJSONObject(i);
                        if (item != null && (value = MainActivity.this.savedFeedValue(item)) != null) {
                            items.add(FeedItem.from(value));
                        }
                    }
                }
                if (items.isEmpty()) {
                    MainActivity.this.collectFeedItems(body, items, new HashMap(), 0);
                }
                List<FeedItem> items2 = MainActivity.this.filterItems(items);
                if (items2.isEmpty()) {
                    List<FeedItem> cached = MainActivity.this.filterItems(MainActivity.this.localCache.savedList(cacheKey));
                    if (!cached.isEmpty()) {
                        MainActivity.this.toast(MainActivity.MSG_OFFLINE_CACHE);
                        MainActivity.this.renderSavedItems(pageTitle, cached);
                        return;
                    } else if (fallback != null && fallback.onFallback("empty result")) {
                        return;
                    }
                } else {
                    MainActivity.this.localCache.saveSavedList(cacheKey, items2);
                }
                MainActivity.this.renderSavedItems(pageTitle, items2);
            }

            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onError(String message) {
                if (MainActivity.this.isLoginWriteError(message)) {
                    MainActivity.this.localCache.log(pageTitle + " web failed, trying mobile: " + message);
                    Map<String, String> mobileParams = new HashMap<>((Map<? extends String, ? extends String>) params);
                    mobileParams.remove("x_os_type");
                    mobileParams.remove("device_info");
                    MainActivity.this.requestSavedListMobile(pageTitle, path, mobileParams, cacheKey, fallback, true);
                    return;
                }
                MainActivity.this.hideLoading();
                MainActivity.this.localCache.log(pageTitle + " failed: " + message);
                List<FeedItem> cached = MainActivity.this.filterItems(MainActivity.this.localCache.savedList(cacheKey));
                if (!cached.isEmpty()) {
                    MainActivity.this.toast(MainActivity.MSG_OFFLINE_CACHE);
                    MainActivity.this.renderSavedItems(pageTitle, cached);
                } else if (fallback == null || !fallback.onFallback(message)) {
                    MainActivity.this.showMessage(pageTitle + "加载失败\n" + message);
                }
            }
        });
    }

    private void requestSavedListMobile(final String pageTitle, final String path, final Map<String, String> params, final String cacheKey, final SavedListFallback fallback, final boolean authSuspect) {
        if (this.localCache != null) {
            this.localCache.log(pageTitle + " request mobile path=" + path + " keys=" + params.keySet());
        }
        this.api.getSigned(path, params, HeyboxSigner.Algorithm.ANDROID, ApiClient.RequestProfile.MOBILE, new ApiClient.Callback() { // from class: com.openzen.heyboxcommunity.MainActivity.27
            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onSuccess(JSONObject body) {
                JSONObject value;
                MainActivity.this.hideLoading();
                JSONObject result = body.optJSONObject("result");
                JSONArray links = MainActivity.this.findLinks(result == null ? body : result);
                List<FeedItem> items = new ArrayList<>();
                if (links != null) {
                    for (int i = 0; i < links.length(); i++) {
                        JSONObject item = links.optJSONObject(i);
                        if (item != null && (value = MainActivity.this.savedFeedValue(item)) != null) {
                            items.add(FeedItem.from(value));
                        }
                    }
                }
                if (items.isEmpty()) {
                    MainActivity.this.collectFeedItems(body, items, new HashMap(), 0);
                }
                List<FeedItem> items2 = MainActivity.this.filterItems(items);
                if (items2.isEmpty()) {
                    List<FeedItem> cached = MainActivity.this.filterItems(MainActivity.this.localCache.savedList(cacheKey));
                    if (!cached.isEmpty()) {
                        MainActivity.this.toast(MainActivity.MSG_OFFLINE_CACHE);
                        MainActivity.this.renderSavedItems(pageTitle, cached);
                        return;
                    } else if (fallback != null && fallback.onFallback("mobile empty result")) {
                        return;
                    }
                } else {
                    MainActivity.this.localCache.saveSavedList(cacheKey, items2);
                }
                if (MainActivity.this.localCache != null) {
                    MainActivity.this.localCache.log(pageTitle + " loaded by mobile");
                }
                MainActivity.this.renderSavedItems(pageTitle, items2);
            }

            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onError(String message) {
                if (MainActivity.this.isLoginWriteError(message) || MainActivity.this.isParameterWriteError(message)) {
                    if (MainActivity.this.localCache != null) {
                        MainActivity.this.localCache.log(pageTitle + " mobile failed, trying official: " + message);
                    }
                    MainActivity.this.requestSavedListOfficial(pageTitle, path, params, cacheKey, fallback, authSuspect || MainActivity.this.isLoginWriteError(message));
                    return;
                }
                MainActivity.this.hideLoading();
                if (MainActivity.this.localCache != null) {
                    MainActivity.this.localCache.log(pageTitle + " mobile failed: " + message);
                }
                List<FeedItem> cached = MainActivity.this.filterItems(MainActivity.this.localCache.savedList(cacheKey));
                if (!cached.isEmpty()) {
                    MainActivity.this.toast(MainActivity.MSG_OFFLINE_CACHE);
                    MainActivity.this.renderSavedItems(pageTitle, cached);
                } else if (fallback == null || !fallback.onFallback(message)) {
                    MainActivity.this.showMessage(pageTitle + "加载失败\n" + message);
                }
            }
        });
    }

    private void requestSavedListOfficial(final String pageTitle, String path, Map<String, String> params, final String cacheKey, final SavedListFallback fallback, boolean authSuspect) {
        if (this.localCache != null) {
            this.localCache.log(pageTitle + " request official path=" + path + " keys=" + params.keySet());
        }
        this.api.getSigned(path, params, HeyboxSigner.Algorithm.ANDROID, ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT, new ApiClient.Callback() { // from class: com.openzen.heyboxcommunity.MainActivity.28
            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onSuccess(JSONObject body) {
                JSONObject value;
                MainActivity.this.hideLoading();
                JSONObject result = body.optJSONObject("result");
                JSONArray links = MainActivity.this.findLinks(result == null ? body : result);
                List<FeedItem> items = new ArrayList<>();
                if (links != null) {
                    for (int i = 0; i < links.length(); i++) {
                        JSONObject item = links.optJSONObject(i);
                        if (item != null && (value = MainActivity.this.savedFeedValue(item)) != null) {
                            items.add(FeedItem.from(value));
                        }
                    }
                }
                if (items.isEmpty()) {
                    MainActivity.this.collectFeedItems(body, items, new HashMap(), 0);
                }
                List<FeedItem> items2 = MainActivity.this.filterItems(items);
                if (items2.isEmpty()) {
                    List<FeedItem> cached = MainActivity.this.filterItems(MainActivity.this.localCache.savedList(cacheKey));
                    if (!cached.isEmpty()) {
                        MainActivity.this.toast(MainActivity.MSG_OFFLINE_CACHE);
                        MainActivity.this.renderSavedItems(pageTitle, cached);
                        return;
                    } else if (fallback != null && fallback.onFallback("official empty result")) {
                        return;
                    }
                } else {
                    MainActivity.this.localCache.saveSavedList(cacheKey, items2);
                }
                if (MainActivity.this.localCache != null) {
                    MainActivity.this.localCache.log(pageTitle + " loaded by official");
                }
                MainActivity.this.renderSavedItems(pageTitle, items2);
            }

            @Override // com.openzen.heyboxcommunity.ApiClient.Callback
            public void onError(String message) {
                MainActivity.this.hideLoading();
                if (MainActivity.this.localCache != null) {
                    MainActivity.this.localCache.log(pageTitle + " official failed: " + message);
                }
                List<FeedItem> cached = MainActivity.this.filterItems(MainActivity.this.localCache.savedList(cacheKey));
                if (!cached.isEmpty()) {
                    MainActivity.this.toast(MainActivity.MSG_OFFLINE_CACHE);
                    MainActivity.this.renderSavedItems(pageTitle, cached);
                } else if (fallback == null || !fallback.onFallback(message)) {
                    MainActivity.this.showMessage(pageTitle + "加载失败\n" + message);
                }
            }
        });
    }

    private String savedCacheKey(String pageTitle, String path) {
        return pageTitle + "_" + path;
    }

    private void renderSavedItems(String pageTitle, List<FeedItem> items) {
        if (!isHistoryPage(pageTitle)) {
            this.content.addView(feedList(items), match());
            if (items.isEmpty()) {
                showMessage(MSG_EMPTY_CONTENT);
                return;
            }
            return;
        }
        showHistoryList(items);
    }

    private boolean isHistoryPage(String pageTitle) {
        return pageTitle != null && pageTitle.contains("历史");
    }

    private ListView feedList(List<FeedItem> items) {
        ListView list = new ListView(this);
        list.setBackgroundColor(this.BG);
        list.setDivider(new ColorDrawable(0));
        list.setDividerHeight(dp(REPLY_PREVIEW_COUNT));
        list.setAdapter((ListAdapter) new FeedAdapter(this, items, this.session.noImage(), this.session.uiScale() / 100.0f, this.session.textScale() / 100.0f, this.session.darkMode(), this.PRIMARY, this.SECONDARY, this::showDetail, this::toggleFeedLike));
        return list;
    }

    private void ensureEmojiCatalog(Runnable ready) {
        if (this.api == null) {
            return;
        }
        EmojiStore.load(this.api, ready);
    }

    private void showHistoryList(final List<FeedItem> allItems) {
        this.content.removeAllViews();
        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.setBackgroundColor(this.BG);
        final EditText search = new EditText(this);
        search.setHint("搜索历史：标题、摘要或作者");
        search.setHintTextColor(this.MUTED);
        search.setTextColor(this.TEXT);
        search.setSingleLine(true);
        search.setTextSize(sp(12.0f));
        Compat.tint(search, this.PRIMARY);
        FrameLayout results = new FrameLayout(this);
        frameLayout.addView(results, match());
        FrameLayout.LayoutParams searchParams = new FrameLayout.LayoutParams(-1, dp(40), 48);
        searchParams.leftMargin = dp(7);
        searchParams.rightMargin = dp(7);
        searchParams.topMargin = dp(REPLY_PAGE_SIZE);
        frameLayout.addView(search, searchParams);
        prepareSearchBar(search, dp(40));
        final List<FeedItem> filtered = new ArrayList<>(allItems);
        final FeedAdapter adapter = new FeedAdapter(this, filtered, this.session.noImage(), this.session.uiScale() / 100.0f, this.session.textScale() / 100.0f, this.session.darkMode(), this.PRIMARY, this.SECONDARY, this::showDetail, this::toggleFeedLike);
        final ListView list = new ListView(this);
        list.setBackgroundColor(this.BG);
        list.setDivider(new ColorDrawable(0));
        list.setDividerHeight(dp(REPLY_PREVIEW_COUNT));
        list.setAdapter((ListAdapter) adapter);
        list.setOnScrollListener(new AbsListView.OnScrollListener() { // from class: com.openzen.heyboxcommunity.MainActivity.29
            @Override // android.widget.AbsListView.OnScrollListener
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                MainActivity.this.setSearchBarVisible(search, scrollState == 0);
            }

            @Override // android.widget.AbsListView.OnScrollListener
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            }
        });
        results.addView(list, match());
        final TextView emptyView = text(allItems.isEmpty() ? MSG_EMPTY_CONTENT : "没有找到相关历史记录", 13.0f, this.MUTED);
        emptyView.setGravity(17);
        results.addView(emptyView, match());
        emptyView.setVisibility(allItems.isEmpty() ? 0 : 8);
        list.setVisibility(allItems.isEmpty() ? 8 : 0);
        search.addTextChangedListener(new TextWatcher() { // from class: com.openzen.heyboxcommunity.MainActivity.30
            @Override // android.text.TextWatcher
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override // android.text.TextWatcher
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                String query = value.toString().trim().toLowerCase(Locale.US);
                filtered.clear();
                for (FeedItem item : allItems) {
                    String haystack = (item.title + "\n" + item.description + "\n" + item.author).toLowerCase(Locale.US);
                    if (query.isEmpty() || haystack.contains(query)) {
                        filtered.add(item);
                    }
                }
                adapter.notifyDataSetChanged();
                emptyView.setText("没有找到相关历史记录");
                if (filtered.isEmpty()) {
                    MainActivity.this.setSearchBarVisible(search, true);
                    emptyView.setVisibility(0);
                    list.setVisibility(8);
                } else {
                    emptyView.setVisibility(8);
                    list.setVisibility(0);
                }
            }

            @Override // android.text.TextWatcher
            public void afterTextChanged(Editable value) {
            }
        });
        this.content.addView(frameLayout, match());
    }

    private JSONArray findLinks(JSONObject result) {
        if (result == null) {
            return null;
        }
        JSONArray links = result.optJSONArray("links");
        if (links == null) {
            links = result.optJSONArray("list");
        }
        if (links == null) {
            links = result.optJSONArray("moments");
        }
        if (links == null) {
            links = result.optJSONArray("history_visit");
        }
        if (links == null) {
            links = result.optJSONArray("visits");
        }
        if (links == null) {
            links = result.optJSONArray("history");
        }
        if (links == null) {
            links = result.optJSONArray("data");
        }
        if (links == null) {
            links = result.optJSONArray("items");
        }
        if (links == null) {
            links = result.optJSONArray("rows");
        }
        if (links == null) {
            links = result.optJSONArray("records");
        }
        if (links == null) {
            links = result.optJSONArray("favorites");
        }
        if (links == null) {
            links = result.optJSONArray("collects");
        }
        if (links == null) {
            links = result.optJSONArray("link_list");
        }
        if (links == null) {
            links = result.optJSONArray("links_list");
        }
        if (links == null) {
            links = findNestedLinkArray(result, 0);
        }
        return links;
    }

    private JSONArray findNestedLinkArray(Object node, int depth) {
        if (node == null || depth > REPLY_PAGE_SIZE) {
            return null;
        }
        if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            if (looksLikeLinkArray(array)) {
                return array;
            }
            return null;
        }
        if (!(node instanceof JSONObject)) {
            return null;
        }
        JSONObject object = (JSONObject) node;
        Iterator<String> names = object.keys();
        while (names.hasNext()) {
            JSONArray found = findNestedLinkArray(object.opt(names.next()), depth + 1);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private boolean looksLikeLinkArray(JSONArray array) {
        JSONObject value;
        if (array == null || array.length() == 0) {
            return false;
        }
        int limit = Math.min(8, array.length());
        for (int i = 0; i < limit; i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null && (value = savedFeedValue(item)) != null) {
                String id = value.optString("linkid", value.optString("link_id"));
                if (!id.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private JSONObject unwrapSavedItem(JSONObject item) {
        JSONObject current = item;
        for (int depth = 0; depth < 4; depth++) {
            if (!current.optString("linkid", current.optString("link_id")).isEmpty()) {
                return current;
            }
            JSONObject next = current.optJSONObject("link");
            if (next == null) {
                next = current.optJSONObject("link_info");
            }
            if (next == null) {
                next = current.optJSONObject("link_detail");
            }
            if (next == null) {
                next = current.optJSONObject("moment");
            }
            if (next == null) {
                next = current.optJSONObject("post");
            }
            if (next == null) {
                next = current.optJSONObject("favorite");
            }
            if (next == null) {
                next = current.optJSONObject("fav");
            }
            if (next == null) {
                next = current.optJSONObject("record");
            }
            if (next == null) {
                next = current.optJSONObject("target");
            }
            if (next == null) {
                next = current.optJSONObject("source");
            }
            if (next == null) {
                next = current.optJSONObject("obj");
            }
            if (next == null) {
                next = current.optJSONObject("content");
            }
            if (next == null) {
                next = current.optJSONObject("data");
            }
            if (next == null) {
                next = current.optJSONObject("item");
            }
            if (next == null || next == current) {
                break;
            }
            current = next;
        }
        return current;
    }

    private JSONObject savedFeedValue(JSONObject wrapper) {
        JSONObject value = unwrapSavedItem(wrapper);
        if (value == null) {
            return null;
        }
        try {
            JSONObject merged = new JSONObject(value.toString());
            copyFirstInt(wrapper, merged, "link_award_num", "link_award_num", "like_num", "award_num", "award_count", "like_count", "liked_num", "praise_num", "praise_count", "total_award_num", "award", "awards", "up_num", "up");
            copyFirstInt(wrapper, merged, "comment_num", "comment_num", "comment_count", "reply_num", "reply_count", "comments_count", "total_comment_num", "comment", "comments");
            if (merged.optJSONObject("user") == null) {
                JSONObject user = findObject(wrapper, 0, "user", "author", "account");
                if (user != null) {
                    merged.put("user", user);
                } else {
                    String author = findString(wrapper, 0, "author_name", "username", "nickname", "author");
                    if (!author.isEmpty()) {
                        JSONObject fallbackUser = new JSONObject();
                        fallbackUser.put("username", author);
                        merged.put("user", fallbackUser);
                    }
                }
            }
            return merged;
        } catch (Exception e) {
            return value;
        }
    }

    private void copyFirstInt(JSONObject source, JSONObject target, String targetKey, String... keys) {
        int value;
        if (target.optInt(targetKey, 0) <= 0 && (value = findInt(source, keys, 0)) > 0) {
            try {
                target.put(targetKey, value);
            } catch (Exception e) {
            }
        }
    }

    private int findInt(JSONObject source, String[] keys, int depth) {
        int value;
        if (source == null || depth > 4) {
            return 0;
        }
        for (String key : keys) {
            if (source.has(key) && (value = source.optInt(key, 0)) > 0) {
                return value;
            }
        }
        Iterator<String> names = source.keys();
        while (names.hasNext()) {
            Object value2 = source.opt(names.next());
            if (value2 instanceof JSONObject) {
                int found = findInt((JSONObject) value2, keys, depth + 1);
                if (found > 0) {
                    return found;
                }
            } else if (value2 instanceof JSONArray) {
                JSONArray array = (JSONArray) value2;
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    int found2 = findInt(item, keys, depth + 1);
                    if (found2 > 0) {
                        return found2;
                    }
                }
            } else {
                continue;
            }
        }
        return 0;
    }

    private JSONObject findObject(JSONObject source, int depth, String... keys) {
        JSONObject found;
        if (source == null || depth > 4) {
            return null;
        }
        for (String key : keys) {
            JSONObject value = source.optJSONObject(key);
            if (value != null) {
                return value;
            }
        }
        Iterator<String> names = source.keys();
        while (names.hasNext()) {
            Object child = source.opt(names.next());
            if ((child instanceof JSONObject) && (found = findObject((JSONObject) child, depth + 1, keys)) != null) {
                return found;
            }
        }
        return null;
    }

    private String findString(JSONObject source, int depth, String... keys) {
        if (source == null || depth > 4) {
            return "";
        }
        for (String key : keys) {
            String value = source.optString(key);
            if (!value.isEmpty() && !value.startsWith("{")) {
                return value;
            }
        }
        Iterator<String> names = source.keys();
        while (names.hasNext()) {
            Object child = source.opt(names.next());
            if (child instanceof JSONObject) {
                String found = findString((JSONObject) child, depth + 1, keys);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        return "";
    }

    private LinearLayout settingsPage(String key, String pageTitle) {
        return settingsPage(key, pageTitle, this::showSettingsHome);
    }

    private LinearLayout settingsPage(String key, String pageTitle, Runnable back) {
        stopQrPolling();
        this.screen = key;
        setBottomNavVisible(false);
        this.leading.setVisibility(0);
        this.leading.setOnClickListener(view -> {
            this.pendingBackTransition = true;
            back.run();
        });
        this.title.setText(pageTitle);
        this.action.setVisibility(4);
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = vertical(this.BG);
        page.setPadding(dp(8), dp(8), dp(8), dp(14));
        scroll.addView(page);
        page.addView(settingsTopCard(pageTitle));
        transitionTo(scroll);
        return page;
    }

    /** 动画效果三挡选择：关闭 / 精简 / 完整。切换立即生效并结算进行中的转场。 */
    private View motionLevelRow() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(1);
        TextView label = text("动画效果", 12.0f, this.MUTED);
        wrap.addView(label);
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(0);
        final String[] names = {"关闭", "精简", "完整"};
        final Button[] buttons = new Button[names.length];
        final Runnable refresh = () -> {
            int current = this.session.motionLevel();
            ThemeTokens tokens = this.themeTokens != null ? this.themeTokens
                    : ThemeTokens.of(this.session.darkMode(), this.PRIMARY, this.SECONDARY);
            float scale = this.session.uiScale() / 100.0f;
            for (int i = 0; i < buttons.length; i++) {
                boolean selected = i == current;
                Compat.setBackground(buttons[i], selected
                        ? UiComponents.primaryButton(this, tokens, scale)
                        : UiComponents.ghostButton(this, tokens, scale));
                buttons[i].setTextColor(selected ? tokens.onPrimary : tokens.text);
            }
        };
        for (int i = 0; i < names.length; i++) {
            final int level = i;
            Button item = secondaryButton(names[i], 0);
            buttons[i] = item;
            item.setOnClickListener(view -> {
                this.session.setMotionLevel(level);
                Motions.setLevel(level);
                PageTransitionController.finishNow();
                refresh.run();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(34), 1.0f);
            if (i > 0) {
                params.leftMargin = dp(6);
            }
            seg.addView(item, params);
        }
        refresh.run();
        addTop(wrap, seg, 5);
        TextView hint = text("完整挡包含页面滑动转场与列表入场，低配手表建议精简或关闭", 10.5f, this.MUTED);
        hint.setLineSpacing(0.0f, 1.16f);
        addTop(wrap, hint, 4);
        return wrap;
    }

    private View settingsTopCard(String pageTitle) {
        LinearLayout box = new LinearLayout(this);
        box.setGravity(16);
        box.setPadding(dp(4), 0, dp(4), 0);
        box.setBackgroundColor(this.BG);
        TextView name = text(pageTitle, 15.0f, this.TEXT);
        name.setTypeface(appRegularTypeface(), 1);
        box.addView(name, new LinearLayout.LayoutParams(0, dp(34), 1.0f));
        TextView time = text(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()), 12.0f, this.MUTED);
        time.setGravity(21);
        box.addView(time, new LinearLayout.LayoutParams(dp(58), dp(34)));
        return box;
    }

    private void showDisplaySettings() {
        LinearLayout linearLayout = settingsPage("display_settings", "显示设置");
        LinearLayout panel = card();
        boolean[] dark = {this.session.darkMode()};
        boolean[] bodyBold = {this.session.bodyBold()};
        boolean[] roundScreen = {this.session.roundScreen()};
        addTop(panel, toggleRow("夜间模式", dark[0], value -> {
            dark[0] = value;
        }), 0);
        Button fullPreview = secondaryButton("查看界面预览", 0);
        fullPreview.setOnClickListener(view -> {
            showDisplayPreview();
        });
        addTop(panel, fullPreview, 6);
        LinearLayout livePreview = vertical(this.PANEL);
        TextView previewTitle = text("显示效果预览", 14.0f, this.TEXT);
        previewTitle.setTypeface(appRegularTypeface(), 1);
        TextView previewBody = text("帖子正文会跟随下方设置实时变化。\n第二段用于预览段落间距", 13.0f, this.TEXT);
        previewBody.setTypeface(Typeface.create("sans-serif-medium", 0));
        TextView previewAction = text("主色按钮", 11.0f, contrast(this.PRIMARY));
        previewAction.setGravity(17);
        Compat.setBackground(previewAction, round(this.PRIMARY, 7));
        addTop(livePreview, previewTitle, 0);
        addTop(livePreview, previewBody, 3);
        addTop(livePreview, previewAction, 6);
        addTop(panel, livePreview, 8);
        ScaleControl uiScale = settingSlider(panel, "界面大小", "%", 70, 160, this.session.uiScale(), value2 -> {
            updateDisplayPreview(livePreview, previewTitle, previewBody, previewAction, value2, -1, -1);
        });
        ScaleControl textScale = settingSlider(panel, "文字大小", "%", 70, 180, this.session.textScale(), value3 -> {
            updateDisplayPreview(livePreview, previewTitle, previewBody, previewAction, -1, value3, -1);
        });
        ScaleControl padding = settingSlider(panel, "左右边距", "dp", 0, NAV_BAR_HEIGHT_DP, this.session.pagePadding(), value4 -> {
            updateDisplayPreview(livePreview, previewTitle, previewBody, previewAction, -1, -1, value4);
        });
        addTop(panel, motionLevelRow(), REPLY_PAGE_SIZE);
        linearLayout.addView(panel);
        panel = card();
        TextView roundTitle = text("手表屏幕适配", 13.0f, this.TEXT);
        roundTitle.setTypeface(appRegularTypeface(), 1);
        addTop(panel, roundTitle, 0);
        TextView roundDesc = text("圆屏模式会给页面四周留出安全边距，避免内容贴到屏幕边缘。横纵向边距按屏幕百分比计算，适合圆屏和小屏手表微调", 11.0f, this.MUTED);
        roundDesc.setLineSpacing(0.0f, 1.16f);
        addTop(panel, roundDesc, 4);
        ScaleControl[] screenPaddingH = {settingSlider(panel, "横向边距", "%", 0, NAV_BAR_HEIGHT_DP, this.session.screenPaddingHPercent(), value6 -> {
        })};
        ScaleControl[] screenPaddingV = {settingSlider(panel, "纵向边距", "%", 0, NAV_BAR_HEIGHT_DP, this.session.screenPaddingVPercent(), value7 -> {
        })};
        addTop(panel, toggleRow("圆屏适配", roundScreen[0], value5 -> {
            roundScreen[0] = value5;
            int h = value5 ? REPLY_PAGE_SIZE : 0;
            int v = value5 ? 3 : 0;
            setScaleControlValue(screenPaddingH[0], h, 0);
            setScaleControlValue(screenPaddingV[0], v, 0);
            updateDisplayPreview(livePreview, previewTitle, previewBody, previewAction, -1, -1, parseNumber(padding.input, 0, NAV_BAR_HEIGHT_DP) == null ? this.session.pagePadding() : parseNumber(padding.input, 0, NAV_BAR_HEIGHT_DP).intValue());
        }), REPLY_PAGE_SIZE);
        linearLayout.addView(panel);
        panel = card();
        TextView bodyGroupTitle = text("正文排版", 13.0f, this.TEXT);
        bodyGroupTitle.setTypeface(appRegularTypeface(), 1);
        addTop(panel, bodyGroupTitle, 0);
        ScaleControl bodyText = settingSlider(panel, "正文字号", "%", 75, 170, this.session.bodyTextScale(), value8 -> {
            previewBody.setTextSize((13 * value8) / 100.0f);
        });
        ScaleControl letterSpacing = settingSlider(panel, "字间", "", 0, NAV_ICON_SIZE_DP, this.session.bodyLetterSpacing(), value9 -> {
            Compat.setLetterSpacing(previewBody, value9 / 200.0f);
        });
        ScaleControl paragraphSpacing = settingSlider(panel, "段落间距", "dp", 0, 24, this.session.bodyParagraphSpacing(), value10 -> {
            previewBody.setPadding(0, dp(value10), 0, 0);
        });
        ScaleControl lineSpacing = settingSlider(panel, "行距", "%", 100, 180, this.session.bodyLineSpacing(), value11 -> {
            previewBody.setLineSpacing(0.0f, value11 / 100.0f);
        });
        addTop(panel, toggleRow("正文与一级评论稍加粗", bodyBold[0], value12 -> {
            Typeface typefaceCreate;
            bodyBold[0] = value12;
            if (value12) {
                typefaceCreate = Typeface.create("sans-serif-medium", 0);
            } else {
                typefaceCreate = appRegularTypeface();
            }
            previewBody.setTypeface(typefaceCreate);
        }), 4);
        updateDisplayPreview(livePreview, previewTitle, previewBody, previewAction, this.session.uiScale(), this.session.textScale(), this.session.pagePadding());
        previewBody.setTextSize((13 * this.session.bodyTextScale()) / 100.0f);
        Compat.setLetterSpacing(previewBody, this.session.bodyLetterSpacing() / 200.0f);
        previewBody.setPadding(0, dp(this.session.bodyParagraphSpacing()), 0, 0);
        previewBody.setLineSpacing(0.0f, this.session.bodyLineSpacing() / 100.0f);
        linearLayout.addView(panel);
        panel = card();
        TextView themeTitle = text("预设颜色主题", 13.0f, this.TEXT);
        themeTitle.setTypeface(appRegularTypeface(), 1);
        addTop(panel, themeTitle, 0);
        LinearLayout themeGrid = vertical(this.PANEL);
        int rowIndex = 0;
        while (rowIndex < THEME_NAMES.length) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(16);
            row.addView(themePreset(rowIndex), new LinearLayout.LayoutParams(0, dp(44), 1.0f));
            if (rowIndex + 1 < THEME_NAMES.length) {
                LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(44), 1.0f);
                right.leftMargin = dp(REPLY_PAGE_SIZE);
                row.addView(themePreset(rowIndex + 1), right);
            } else {
                row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(44), 1.0f));
            }
            addTop(themeGrid, row, rowIndex == 0 ? 4 : REPLY_PAGE_SIZE);
            rowIndex += REPLY_PREVIEW_COUNT;
        }
        panel.addView(themeGrid);
        Button save = button("保存显示设置", R.drawable.ic_settings);
        save.setOnClickListener(view2 -> {
            Integer ui = parseNumber(uiScale.input, 70, 160);
            Integer text = parseNumber(textScale.input, 70, 180);
            Integer pad = parseNumber(padding.input, 0, NAV_BAR_HEIGHT_DP);
            Integer insetH = parseNumber(screenPaddingH[0].input, 0, NAV_BAR_HEIGHT_DP);
            Integer insetV = parseNumber(screenPaddingV[0].input, 0, NAV_BAR_HEIGHT_DP);
            Integer bodySize = parseNumber(bodyText.input, 75, 170);
            Integer letters = parseNumber(letterSpacing.input, 0, NAV_ICON_SIZE_DP);
            Integer paragraphs = parseNumber(paragraphSpacing.input, 0, 24);
            Integer lines = parseNumber(lineSpacing.input, 100, 180);
            if (ui == null || text == null || pad == null || insetH == null || insetV == null || bodySize == null || letters == null || paragraphs == null || lines == null) {
                toast("请检查输入数值是否在滑杆范围");
                return;
            }
            this.session.setDarkMode(dark[0]);
            this.session.setUiScale(ui.intValue());
            this.session.setTextScale(text.intValue());
            this.session.setPagePadding(pad.intValue());
            this.session.setRoundScreen(roundScreen[0]);
            this.session.setScreenPaddingHPercent(insetH.intValue());
            this.session.setScreenPaddingVPercent(insetV.intValue());
            this.session.setBodyTextScale(bodySize.intValue());
            this.session.setBodyLetterSpacing(letters.intValue());
            this.session.setBodyParagraphSpacing(paragraphs.intValue());
            this.session.setBodyLineSpacing(lines.intValue());
            this.session.setBodyBold(bodyBold[0]);
            applyPalette();
            Compat.colorSystemBars(getWindow(), this.BG);
            buildShell();
            showDisplaySettings();
            toast("显示设置已保存");
        });
        addTop(panel, save, 10);
        Button reset = secondaryButton("恢复默认设置", 0);
        reset.setOnClickListener(view3 -> {
            showLiteDialog("恢复默认显示设置", "主题、字体、间距和界面大小都将恢复为默认值", "恢复", () -> {
                this.session.resetDisplaySettings();
                applyPalette();
                Compat.colorSystemBars(getWindow(), this.BG);
                buildShell();
                showDisplaySettings();
                toast("已恢复默认显示设置");
            }, "取消", null, null, null);
        });
        addTop(panel, reset, 7);
        linearLayout.addView(panel);
    }

    private void showDisplayPreview() {
        LinearLayout linearLayout = settingsPage("display_preview", "界面预览", this::showDisplaySettings);
        TextView hint = text("预览使用当前已保存的显示参数", 10.0f, this.MUTED);
        hint.setGravity(17);
        linearLayout.addView(hint, new LinearLayout.LayoutParams(-1, dp(28)));
        LinearLayout feedCard = card();
        TextView articleBadge = text("文章", 9.0f, contrast(this.SECONDARY));
        articleBadge.setGravity(17);
        Compat.setBackground(articleBadge, round(this.SECONDARY, 4));
        feedCard.addView(articleBadge, new LinearLayout.LayoutParams(dp(42), dp(NAV_ICON_SIZE_DP)));
        TextView previewTitle = text("方屏上的社区，也可以清晰又从", 15.0f, this.TEXT);
        previewTitle.setTypeface(appRegularTypeface(), 1);
        addTop(feedCard, previewTitle, 6);
        TextView summary = text("这是一条帖子列表摘要，用来观察整体字号、卡片间距和主题颜色", 11.0f, this.MUTED);
        summary.setLineSpacing(0.0f, 1.12f);
        addTop(feedCard, summary, REPLY_PAGE_SIZE);
        TextView stats = text("Ronan   👍 128   评论 36", 10.0f, this.SECONDARY);
        addTop(feedCard, stats, 7);
        linearLayout.addView(feedCard);
        LinearLayout detail = card();
        TextView detailTitle = text("帖子正文预览", 16.0f, this.TEXT);
        detailTitle.setTypeface(appRegularTypeface(), 1);
        detail.addView(detailTitle);
        TextView body = text("这是正文第一段，用于预览文字大小、字间距与行距。\n\n这是正文第二段。调整设置后保存，再回到这里就能查看最终效果", (14 * this.session.bodyTextScale()) / 100.0f, this.TEXT);
        body.setLineSpacing(0.0f, this.session.bodyLineSpacing() / 100.0f);
        Compat.setLetterSpacing(body, this.session.bodyLetterSpacing() / 200.0f);
        body.setTypeface(this.session.bodyBold() ? Typeface.create("sans-serif-medium", 0) : appRegularTypeface());
        addTop(detail, body, this.session.bodyParagraphSpacing());
        addTop(linearLayout, detail, 7);
        LinearLayout comments = card();
        TextView commentTitle = text("评论层级预览", 14.0f, this.TEXT);
        commentTitle.setTypeface(appRegularTypeface(), 1);
        comments.addView(commentTitle);
        TextView first = text("一级评论会稍微加粗，方便快速浏览主要观点", 13.0f, this.TEXT);
        first.setTypeface(Typeface.create("sans-serif-medium", 0));
        first.setLineSpacing(0.0f, this.session.bodyLineSpacing() / 100.0f);
        addTop(comments, first, 7);
        LinearLayout reply = new LinearLayout(this);
        View rail = new View(this);
        rail.setBackgroundColor(this.SECONDARY);
        LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(dp(REPLY_PREVIEW_COUNT), -1);
        railParams.rightMargin = dp(8);
        reply.addView(rail, railParams);
        TextView second = text("二级评论使用稍轻的字重，并通过主题色竖线建立层级", 12.0f, this.MUTED);
        second.setLineSpacing(0.0f, 1.16f);
        reply.addView(second, new LinearLayout.LayoutParams(0, -2, 1.0f));
        addTop(comments, reply, 7);
        addTop(linearLayout, comments, 7);
        Button backToSettings = secondaryButton("返回继续调整", 0);
        backToSettings.setOnClickListener(view -> {
            showDisplaySettings();
        });
        addTop(linearLayout, backToSettings, 9);
    }

    private View themePreset(int index) {
        int primary = THEME_COLORS[index][0];
        int secondary = THEME_COLORS[index][1];
        boolean selected = currentPrimary() == primary && currentSecondary() == secondary;
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setGravity(16);
        linearLayout.setPadding(dp(7), 0, dp(7), 0);
        int background = blend(this.PANEL, selected ? secondary : this.MUTED, selected ? this.session.darkMode() ? 0.34f : 0.18f : 0.06f);
        GradientDrawable shape = round(background, 7);
        shape.setStroke(dp(1), selected ? primary : blend(this.PANEL, this.MUTED, 0.34f));
        Compat.setBackground(linearLayout, shape);
        LinearLayout swatches = new LinearLayout(this);
        View first = new View(this);
        Compat.setBackground(first, round(primary, 9));
        swatches.addView(first, new LinearLayout.LayoutParams(dp(18), dp(18)));
        View second = new View(this);
        Compat.setBackground(second, round(secondary, 9));
        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        secondParams.leftMargin = -dp(REPLY_PAGE_SIZE);
        swatches.addView(second, secondParams);
        linearLayout.addView(swatches, new LinearLayout.LayoutParams(dp(34), dp(22)));
        TextView name = text(THEME_NAMES[index], 11.0f, this.TEXT);
        name.setGravity(16);
        name.setTypeface(appRegularTypeface(), selected ? 1 : 0);
        linearLayout.addView(name, new LinearLayout.LayoutParams(0, -1, 1.0f));
        if (selected) {
            TextView marker = text("", 13.0f, primary);
            marker.setGravity(17);
            linearLayout.addView(marker, new LinearLayout.LayoutParams(dp(NAV_ICON_SIZE_DP), -1));
        }
        linearLayout.setOnClickListener(view -> {
            this.session.setTheme(colorHex(primary), colorHex(secondary));
            applyPalette();
            Compat.colorSystemBars(getWindow(), this.BG);
            buildShell();
            showDisplaySettings();
        });
        return linearLayout;
    }

    private void showAppSettings() {
        LinearLayout page = settingsPage("app_settings", "应用设置");
        LinearLayout panel = card();
        addTop(panel, toggleRow("无图模式", this.session.noImage(), value -> {
            this.session.setNoImage(value);
            this.feed.clear();
            invalidateFeedView();
        }), 0);
        boolean zPlayGif = this.session.playGif();
        SessionStore sessionStoreGif = this.session;
        Objects.requireNonNull(sessionStoreGif);
        addTop(panel, toggleRow("帖子内播放动图", zPlayGif, sessionStoreGif::setPlayGif), 4);
        boolean zOriginalImages = this.session.originalImages();
        SessionStore sessionStore = this.session;
        Objects.requireNonNull(sessionStore);
        addTop(panel, toggleRow("图片查看器允许查看原图", zOriginalImages, sessionStore::setOriginalImages), 4);
        boolean zShellBackSwipe = this.session.shellBackSwipe();
        SessionStore sessionStore2 = this.session;
        Objects.requireNonNull(sessionStore2);
        addTop(panel, toggleRow("右滑返回上一级", zShellBackSwipe, sessionStore2::setShellBackSwipe), 4);
        boolean zRememberDetailScroll = this.session.rememberDetailScroll();
        SessionStore sessionStore3 = this.session;
        Objects.requireNonNull(sessionStore3);
        addTop(panel, toggleRow("记住帖子阅读位置", zRememberDetailScroll, sessionStore3::setRememberDetailScroll), 4);
        addTop(panel, toggleRow("自动清理 30 天前的离线内容",
                this.session.autoOfflineCleanup(), value -> {
                    this.session.setAutoOfflineCleanup(value);
                    if (value) pruneOfflineCache(null);
                }), 4);
        boolean zDoubleTapCommentReply = this.session.doubleTapCommentReply();
        SessionStore sessionStore4 = this.session;
        Objects.requireNonNull(sessionStore4);
        addTop(panel, toggleRow("双击评论回复", zDoubleTapCommentReply, sessionStore4::setDoubleTapCommentReply), 4);
        EditText blockKeywords = textField(panel, "屏蔽关键词", this.session.blockKeywords());
        blockKeywords.setSingleLine(false);
        blockKeywords.setMinLines(REPLY_PREVIEW_COUNT);
        blockKeywords.setGravity(16);
        Button saveFilter = button("保存内容过滤", R.drawable.ic_settings);
        saveFilter.setOnClickListener(view -> {
            this.session.setBlockKeywords(blockKeywords.getText().toString());
            this.feed.clear();
            this.feedOffset = 0;
            invalidateFeedView();
            toast("内容过滤已保存");
        });
        addTop(panel, saveFilter, 7);
        TextView offlineInfo = text("离线缓存 " + formatCacheMb(this.localCache.offlineBytes()) + " / 已缓存帖子" + this.localCache.detailCount(), 11.0f, this.MUTED);
        addTop(panel, offlineInfo, 8);
        Button pruneOffline = secondaryButton("清理过期离线内容", 0);
        pruneOffline.setOnClickListener(view -> {
            pruneOffline.setEnabled(false);
            pruneOfflineCache(() -> {
                offlineInfo.setText("离线缓存 " + formatCacheMb(this.localCache.offlineBytes())
                        + " / 已缓存帖子" + this.localCache.detailCount());
                pruneOffline.setEnabled(true);
                toast("过期离线内容已清理");
            });
        });
        addTop(panel, pruneOffline, 7);
        Button diagnostics = secondaryButton("导出日志", 0);
        diagnostics.setOnClickListener(view2 -> {
            exportDiagnostics();
        });
        addTop(panel, diagnostics, 7);
        Button clearCache = secondaryButton("清除缓存 " + formatCacheMb(cacheBytes()), 0);
        clearCache.setOnClickListener(view3 -> {
            long before = tempCacheBytes();
            long imageBefore = ((long) ImageLoader.cacheSizeKb()) * 1024;
            clearTempCacheFiles(getCacheDir());
            EmojiRenderer.clear();
            ImageLoader.clear();
            clearCache.setText("清除缓存 " + formatCacheMb(cacheBytes()));
            toast("已清除缓存 " + formatCacheMb(before + imageBefore));
        });
        addTop(panel, clearCache, 10);
        Button login = button(this.session.isLoggedIn() ? "退出登录" : "二维码登录", R.drawable.ic_logout);
        login.setOnClickListener(view5 -> {
            if (this.session.isLoggedIn()) {
                this.session.clearSession();
                this.feed.clear();
                invalidateFeedView();
                toast("已退出登录");
            }
            showLogin();
        });
        addTop(panel, login, 7);
        page.addView(panel);
    }

    private void showStartupSettings() {
        LinearLayout page = settingsPage("startup_settings", "启动与更新");
        LinearLayout panel = card();
        boolean[] autoUpdate = {this.session.autoUpdateCheck()};
        boolean[] splashEnabled = {this.session.splashEnabled()};
        addTop(panel, toggleRow("进入软件时检查更新", autoUpdate[0], value -> {
            autoUpdate[0] = value;
        }), 0);
        addTop(panel, toggleRow("显示开屏动画", splashEnabled[0], value2 -> {
            splashEnabled[0] = value2;
        }), 4);
        EditText splashText = textField(panel, "开屏文字", this.session.splashText());
        ScaleControl duration = settingSlider(panel, "开屏时长", "ms", 500, 2600, this.session.splashDuration(), value3 -> {
        });
        Button preview = secondaryButton("预览开屏动画", 0);
        preview.setOnClickListener(view -> {
            showSplashPreview(splashText.getText().toString().trim(), parseNumber(duration.input, 500, 2600));
        });
        addTop(panel, preview, 8);
        Button save = button("保存启动设置", R.drawable.ic_settings);
        save.setOnClickListener(view2 -> {
            Integer durationValue = parseNumber(duration.input, 500, 2600);
            if (durationValue == null) {
                toast("开屏时长请输入 500-2600");
                return;
            }
            this.session.setAutoUpdateCheck(autoUpdate[0]);
            this.session.setSplashEnabled(splashEnabled[0]);
            this.session.setSplashText(splashText.getText().toString());
            this.session.setSplashDuration(durationValue.intValue());
            toast("启动设置已保存");
        });
        addTop(panel, save, 7);
        page.addView(panel);
    }

    private void showSplashPreview(String value, Integer duration) {
        final String textValue = (value == null || value.isEmpty()) ? "方寸之间，看见热爱" : value;
        final int durationValue = duration == null ? this.session.splashDuration() : duration.intValue();
        final FrameLayout overlay = new FrameLayout(this);
        overlay.setTag("splash_preview");
        boolean dark = this.session.darkMode();
        overlay.setBackgroundColor(dark ? Color.rgb(14, 15, 16) : Color.rgb(246, 247, 249));
        if (!dark) {
            ImageView mark = new ImageView(this);
            mark.setImageResource(R.drawable.splash_logo);
            mark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            FrameLayout.LayoutParams markParams = new FrameLayout.LayoutParams(dp(96), dp(96), 17);
            markParams.bottomMargin = dp(54);
            overlay.addView(mark, markParams);
        }
        final TextView message = text("", 14.0f, this.TEXT);
        message.setTypeface(Typeface.create("monospace", 0));
        message.setGravity(17);
        FrameLayout.LayoutParams messageParams = new FrameLayout.LayoutParams(-1, dp(52), 17);
        messageParams.topMargin = dp(dark ? 0 : 58);
        messageParams.leftMargin = dp(18);
        messageParams.rightMargin = dp(18);
        overlay.addView(message, messageParams);
        TextView close = text("点击任意位置退出预览", 10.0f, this.MUTED);
        close.setGravity(17);
        overlay.addView(close, new FrameLayout.LayoutParams(-1, dp(34), 80));
        overlay.setOnClickListener(view -> {
            this.content.removeView(overlay);
        });
        this.content.addView(overlay, match());
        final int[] frame = {0};
        Runnable animation = new Runnable() { // from class: com.openzen.heyboxcommunity.MainActivity.31
            @Override // java.lang.Runnable
            public void run() {
                if (overlay.getParent() == null) {
                    return;
                }
                int count = Math.min(frame[0], textValue.length());
                message.setText(textValue.substring(0, count) + (count < textValue.length() ? "_" : ""));
                int[] iArr = frame;
                iArr[0] = iArr[0] + 1;
                if (count < textValue.length()) {
                    message.postDelayed(this, Math.max(28, durationValue / Math.max(1, textValue.length() + 4)));
                }
            }
        };
        animation.run();
    }

    private void showAnnouncementsV2() {
        stopQrPolling();
        this.screen = "announcement_board";
        setBottomNavVisible(false);
        this.leading.setVisibility(0);
        this.leading.setOnClickListener(view -> {
            showAbout();
        });
        this.title.setText("公告列表");
        this.action.setText("");
        setIcon(this.action, R.drawable.ic_refresh, this.TEXT, 18);
        this.action.setVisibility(0);
        this.content.removeAllViews();
        PullRefreshListView list = new PullRefreshListView(this);
        list.setBackgroundColor(this.BG);
        list.setDivider(null);
        list.setSelector(new ColorDrawable(0));
        list.setCacheColorHint(0);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(12));
        AnnouncementAdapter adapter = new AnnouncementAdapter();
        list.setAdapter((ListAdapter) adapter);
        list.setPullRefreshAction(() -> {
            loadAnnouncementsInto(list, adapter);
        });
        this.action.setOnClickListener(view2 -> {
            list.setRefreshing(true);
            loadAnnouncementsInto(list, adapter);
        });
        this.content.addView(list, match());
        animateIn(list);
        list.setRefreshing(true);
        loadAnnouncementsInto(list, adapter);
    }

    private void loadAnnouncementsInto(final PullRefreshListView list, final AnnouncementAdapter adapter) {
        if (list == null || adapter == null) {
            return;
        }
        AnnouncementChecker.load(new AnnouncementChecker.Callback() { // from class: com.openzen.heyboxcommunity.MainActivity.32
            @Override // com.openzen.heyboxcommunity.AnnouncementChecker.Callback
            public void onResult(List<AnnouncementChecker.Item> items) {
                if (MainActivity.this.isFinishing() || !"announcement_board".equals(MainActivity.this.screen)) {
                    return;
                }
                list.setRefreshing(false);
                adapter.setItems(MainActivity.this.withWelcomeAnnouncement(items));
            }

            @Override // com.openzen.heyboxcommunity.AnnouncementChecker.Callback
            public void onError(String message) {
                if (MainActivity.this.isFinishing() || !"announcement_board".equals(MainActivity.this.screen)) {
                    return;
                }
                list.setRefreshing(false);
                MainActivity.this.toast("公告加载失败，已显示本地欢迎公告");
                adapter.setItems(Collections.singletonList(MainActivity.this.welcomeAnnouncement()));
            }
        });
    }

    private void showAnnouncements() {
        stopQrPolling();
        this.screen = "announcement_board";
        setBottomNavVisible(false);
        this.leading.setVisibility(0);
        this.leading.setOnClickListener(view -> {
            showAbout();
        });
        this.title.setText("公告列表");
        this.action.setText("");
        setIcon(this.action, R.drawable.ic_refresh, this.TEXT, 18);
        this.action.setVisibility(0);
        this.action.setOnClickListener(view2 -> {
            showAnnouncements();
        });
        this.content.removeAllViews();
        showLoading();
        AnnouncementChecker.load(new AnnouncementChecker.Callback() { // from class: com.openzen.heyboxcommunity.MainActivity.33
            @Override // com.openzen.heyboxcommunity.AnnouncementChecker.Callback
            public void onResult(List<AnnouncementChecker.Item> items) {
                if (MainActivity.this.isFinishing() || !"announcement_board".equals(MainActivity.this.screen)) {
                    return;
                }
                MainActivity.this.renderAnnouncementList(items);
            }

            @Override // com.openzen.heyboxcommunity.AnnouncementChecker.Callback
            public void onError(String message) {
                if (MainActivity.this.isFinishing() || !"announcement_board".equals(MainActivity.this.screen)) {
                    return;
                }
                MainActivity.this.toast("公告加载失败，已显示本地欢迎公告");
                MainActivity.this.renderAnnouncementList(Collections.singletonList(MainActivity.this.welcomeAnnouncement()));
            }
        });
    }

    private void renderAnnouncementList(List<AnnouncementChecker.Item> items) {
        hideLoading();
        this.content.removeAllViews();
        List<AnnouncementChecker.Item> items2 = withWelcomeAnnouncement(items);
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(this.BG);
        LinearLayout page = vertical(this.BG);
        page.setPadding(dp(8), dp(8), dp(8), dp(14));
        scrollView.addView(page);
        if (items2 == null || items2.isEmpty()) {
            TextView empty = text("暂无公告", 13.0f, this.MUTED);
            empty.setGravity(17);
            page.addView(empty, new LinearLayout.LayoutParams(-1, dp(88)));
        } else {
            boolean added = false;
            for (AnnouncementChecker.Item item : items2) {
                if (item != null && item.enabled) {
                    LinearLayout card = card();
                    TextView itemTitle = text(TextUtils.isEmpty(item.title) ? "公告" : item.title, 14.0f, this.TEXT);
                    itemTitle.setTypeface(appRegularTypeface(), 1);
                    card.addView(itemTitle);
                    String updatedAt = formatAnnouncementTime(item.updatedAt);
                    if (!TextUtils.isEmpty(updatedAt)) {
                        addTop(card, text(updatedAt, 10.0f, this.MUTED), 4);
                    }
                    TextView preview = text(announcementPreview(item.content), 12.0f, this.MUTED);
                    preview.setLineSpacing(0.0f, 1.18f);
                    addTop(card, preview, 6);
                    card.setOnClickListener(view -> {
                        showAnnouncementDialog(item);
                    });
                    addTop(page, card, 8);
                    added = true;
                }
            }
            if (!added) {
                TextView empty2 = text("暂无公告", 13.0f, this.MUTED);
                empty2.setGravity(17);
                page.addView(empty2, new LinearLayout.LayoutParams(-1, dp(88)));
            }
        }
        this.content.addView(scrollView, match());
        animateIn(scrollView);
    }

    /* JADX INFO: loaded from: MainActivity$AnnouncementAdapter.class */
    private final class AnnouncementAdapter extends BaseAdapter {
        private final List<AnnouncementChecker.Item> items = new ArrayList();

        private AnnouncementAdapter() {
        }

        void setItems(List<AnnouncementChecker.Item> value) {
            this.items.clear();
            if (value != null) {
                for (AnnouncementChecker.Item item : value) {
                    if (item != null && item.enabled) {
                        this.items.add(item);
                    }
                }
            }
            notifyDataSetChanged();
        }

        @Override // android.widget.Adapter
        public int getCount() {
            if (this.items.isEmpty()) {
                return 1;
            }
            return this.items.size();
        }

        @Override // android.widget.Adapter
        public Object getItem(int position) {
            if (this.items.isEmpty()) {
                return null;
            }
            return this.items.get(position);
        }

        @Override // android.widget.Adapter
        public long getItemId(int position) {
            return position;
        }

        @Override // android.widget.Adapter
        public View getView(int position, View convertView, ViewGroup parent) {
            if (this.items.isEmpty()) {
                TextView empty = MainActivity.this.text("暂无公告", 13.0f, MainActivity.this.MUTED);
                empty.setGravity(17);
                empty.setMinHeight(MainActivity.this.dp(88));
                return empty;
            }
            AnnouncementChecker.Item item = this.items.get(position);
            LinearLayout row = MainActivity.this.vertical(MainActivity.this.BG);
            row.setPadding(MainActivity.this.dp(8), MainActivity.this.dp(4), MainActivity.this.dp(8), MainActivity.this.dp(4));
            LinearLayout card = MainActivity.this.card();
            TextView itemTitle = MainActivity.this.text(TextUtils.isEmpty(item.title) ? "公告" : item.title, 14.0f, MainActivity.this.TEXT);
            itemTitle.setTypeface(appRegularTypeface(), 1);
            card.addView(itemTitle);
            String updatedAt = MainActivity.this.formatAnnouncementTime(item.updatedAt);
            if (!TextUtils.isEmpty(updatedAt)) {
                MainActivity.this.addTop(card, MainActivity.this.text(updatedAt, 10.0f, MainActivity.this.MUTED), 4);
            }
            TextView preview = MainActivity.this.text(MainActivity.this.announcementPreview(item.content), 12.0f, MainActivity.this.MUTED);
            preview.setLineSpacing(0.0f, 1.18f);
            MainActivity.this.addTop(card, preview, 6);
            card.setOnClickListener(view -> {
                MainActivity.this.showAnnouncementDialog(item);
            });
            row.addView(card, new LinearLayout.LayoutParams(-1, -2));
            return row;
        }
    }

    private String announcementPreview(String value) {
        if (value == null) {
            return "";
        }
        String clean = value.replace('\r', '\n').replace("\n\n", "\n").trim();
        return clean.length() <= 92 ? clean : clean.substring(0, 92) + "...";
    }

    private String formatAnnouncementTime(String value) {
        if (value == null) {
            return "";
        }
        String clean = value.trim();
        if (clean.isEmpty()) {
            return "";
        }
        if (!clean.matches("\\d+")) {
            return (clean.contains("-") || clean.contains("/") || clean.contains(":")) ? clean : "";
        }
        try {
            long timestamp = Long.parseLong(clean);
            if (timestamp <= 0) {
                return "";
            }
            if (timestamp < 100000000000L) {
                timestamp *= 1000;
            }
            return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(timestamp));
        } catch (Exception e) {
            return "";
        }
    }

    private void showAbout() {
        LinearLayout page = settingsPage("about", "关于");
        LinearLayout panel = card();
        TextView appName = text("heybox Lite", 20.0f, this.TEXT);
        appName.setTypeface(appRegularTypeface(), 1);
        panel.addView(appName);
        addTop(panel, text("版本 " + appVersion(), 13.0f, this.MUTED), 6);
        addTop(panel, text("开发者：Ronan", 13.0f, this.TEXT), REPLY_PAGE_SIZE);
        addTop(panel, text("2.0 正式版支持 Android 7.0 及以上系统", 12.0f, this.MUTED), REPLY_PAGE_SIZE);
        TextView basedOn = text("基于 HeyWear 进行二次开发与方屏适配，非官方应用", 12.0f, this.TEXT);
        basedOn.setLineSpacing(0.0f, 1.18f);
        addTop(panel, basedOn, 7);
        TextView disclaimer = text("免责声明：本项目仅用于学习、研究与个人使用，不代表小黑盒、HeyWear 或相关官方立场。本项目基于 HeyWear 进行二次开发与适配，开发者不对因使用本项目造成的账号、数据、设备或其他风险承担额外责任。请在遵守相关法律法规及平台规则的前提下使用", 11.0f, this.MUTED);
        disclaimer.setLineSpacing(0.0f, 1.22f);
        addTop(panel, disclaimer, 10);
        TextView feedbackTitle = text("交流群", 13.0f, this.TEXT);
        feedbackTitle.setTypeface(appRegularTypeface(), 1);
        addTop(panel, feedbackTitle, 12);
        addTop(panel, text("QQ群：781941517", 12.0f, this.TEXT), REPLY_PAGE_SIZE);
        addTop(panel, text("遇到问题可以扫码进交流群。", 11.0f, this.MUTED), 3);
        Button feedbackQr = secondaryButton("群二维码", 0);
        feedbackQr.setOnClickListener(view -> {
            showFeedbackGroupQr();
        });
        addTop(panel, feedbackQr, 8);
        Button announcements = secondaryButton("公告列表", 0);
        announcements.setOnClickListener(view2 -> {
            showAnnouncementsV2();
        });
        addTop(panel, announcements, 8);
        TextView updateStatus = text("从服务器检查最新版本", 12.0f, this.MUTED);
        addTop(panel, updateStatus, 12);
        Button update = secondaryButton("检查更新", 0);
        update.setOnClickListener(view3 -> {
            update.setEnabled(false);
            update.setText("检查中");
            UpdateChecker.check(appVersion(), new UpdateChecker.Callback() { // from class: com.openzen.heyboxcommunity.MainActivity.34
                @Override // com.openzen.heyboxcommunity.UpdateChecker.Callback
                public void onResult(UpdateChecker.Result result) {
                    if (MainActivity.this.isFinishing()) {
                        return;
                    }
                    update.setEnabled(true);
                    if (result.updateAvailable) {
                        MainActivity.this.showUpdateDialog(result);
                        updateStatus.setText("发现新版" + result.version);
                        update.setText("前往下载 " + result.version);
                        update.setOnClickListener(button -> {
                            MainActivity.this.openUpdateUrl(result.downloadUrl.isEmpty() ? result.releaseUrl : result.downloadUrl);
                        });
                        return;
                    }
                    updateStatus.setText("当前已是最新版");
                    update.setText("重新检查");
                }

                @Override // com.openzen.heyboxcommunity.UpdateChecker.Callback
                public void onError(String message) {
                    if (MainActivity.this.isFinishing()) {
                        return;
                    }
                    update.setEnabled(true);
                    update.setText("重新检查");
                    updateStatus.setText("检查失败：" + message);
                }
            });
        });
        addTop(panel, update, 8);
        Button repository = secondaryButton("打开 GitHub 项目", 0);
        repository.setOnClickListener(view4 -> {
            openUrl("https://github.com/huanghao897/heybox-lite");
        });
        addTop(panel, repository, 7);
        page.addView(panel);
    }

    private void showFeedbackGroupQr() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(1);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        Compat.setBackground(box, round(-1, 12));
        TextView title = text("交流群", 16.0f, Color.rgb(28, 28, 28));
        title.setTypeface(appRegularTypeface(), 1);
        title.setGravity(17);
        box.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView group = text("QQ群：781941517", 12.0f, Color.rgb(84, 84, 84));
        group.setGravity(17);
        addTop(box, group, REPLY_PAGE_SIZE);
        ImageView qr = new ImageView(this);
        qr.setImageResource(R.drawable.qq_feedback_group_qr);
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qr.setAdjustViewBounds(true);
        qr.setPadding(dp(4), dp(4), dp(4), dp(4));
        int size = Math.max(dp(150), Math.min(Math.min(getResources().getDisplayMetrics().widthPixels - dp(56), getResources().getDisplayMetrics().heightPixels - dp(170)), dp(300)));
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(size, size);
        qrParams.gravity = 1;
        qrParams.topMargin = dp(10);
        box.addView(qr, qrParams);
        TextView hint = text("点击空白处关闭", 11.0f, Color.rgb(120, 120, 120));
        hint.setGravity(17);
        addTop(box, hint, 8);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(box).create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0));
        }
    }

    private LinearLayout toggleRow(String label, boolean initial, ToggleListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(16);
        TextView title = text(label, 13.0f, this.TEXT);
        row.addView(title, new LinearLayout.LayoutParams(0, dp(40), 1.0f));
        TextView state = text("", 11.0f, this.TEXT);
        state.setGravity(17);
        row.addView(state, new LinearLayout.LayoutParams(dp(48), dp(28)));
        boolean[] value = {initial};
        Runnable render = () -> {
            int i;
            int iRgb;
            state.setText(value[0] ? "开" : "关");
            if (value[0]) {
                i = this.session.darkMode() ? -16777216 : -1;
            } else {
                i = this.session.darkMode() ? -1 : -16777216;
            }
            int foreground = i;
            if (value[0]) {
                iRgb = this.session.darkMode() ? -1 : -16777216;
            } else {
                iRgb = this.session.darkMode() ? Color.rgb(55, 57, 60) : Color.rgb(220, 222, 225);
            }
            int background = iRgb;
            state.setTextColor(foreground);
            Compat.setBackground(state, round(background, 14));
        };
        render.run();
        row.setOnClickListener(view -> {
            value[0] = !value[0];
            render.run();
            listener.onChanged(value[0]);
        });
        return row;
    }

    private ScaleControl settingSlider(LinearLayout parent, String label, String unit, final int min, int max, int current, final IntListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(16);
        TextView name = text(label, 12.0f, this.TEXT);
        row.addView(name, new LinearLayout.LayoutParams(dp(74), dp(38)));
        SeekBar slider = new SeekBar(this);
        slider.setMax(max - min);
        slider.setProgress(Math.max(0, Math.min(max - min, current - min)));
        Compat.tint(slider, this.PRIMARY);
        row.addView(slider, new LinearLayout.LayoutParams(0, dp(38), 1.0f));
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(String.valueOf(current));
        input.setTextSize(sp(11.0f));
        input.setTextColor(this.TEXT);
        input.setGravity(17);
        input.setInputType(REPLY_PREVIEW_COUNT);
        Compat.tint(input, this.PRIMARY);
        row.addView(input, new LinearLayout.LayoutParams(dp(48), dp(38)));
        TextView suffix = text(unit, 10.0f, this.MUTED);
        suffix.setGravity(17);
        row.addView(suffix, new LinearLayout.LayoutParams(dp(24), dp(38)));
        addTop(parent, row, 4);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { // from class: com.openzen.heyboxcommunity.MainActivity.35
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = min + progress;
                input.setText(String.valueOf(value));
                input.setSelection(input.length());
                listener.onChanged(value);
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        return new ScaleControl(input, slider);
    }

    private void setScaleControlValue(ScaleControl control, int value, int min) {
        if (control == null) {
            return;
        }
        control.input.setText(String.valueOf(value));
        control.input.setSelection(control.input.length());
        control.slider.setProgress(Math.max(0, value - min));
    }

    private void updateDisplayPreview(LinearLayout preview, TextView heading, TextView body, TextView actionView, int ui, int textValue, int padding) {
        if (ui >= 0) {
            preview.setScaleX(1.0f);
            preview.setScaleY(1.0f);
            float scale = ui / 100.0f;
            int vertical = Math.max(REPLY_PAGE_SIZE, Math.round(7.0f * scale));
            preview.setPadding(preview.getPaddingLeft(), dp(vertical), preview.getPaddingRight(), dp(vertical));
            actionView.setMinHeight(dp(Math.max(24, Math.round(28.0f * scale))));
            actionView.setPadding(dp(8), 0, dp(8), 0);
        }
        if (textValue >= 0) {
            float scale2 = textValue / 100.0f;
            heading.setTextSize(14.0f * scale2);
            body.setTextSize(11.0f * scale2);
            actionView.setTextSize(11.0f * scale2);
        }
        if (padding >= 0) {
            int value = Math.round(padding * getResources().getDisplayMetrics().density);
            preview.setPadding(value, preview.getPaddingTop(), value, preview.getPaddingBottom());
        }
    }

    private int currentPrimary() {
        try {
            String saved = this.session.primaryColor();
            if (saved.isEmpty()) {
                return this.session.darkMode() ? -1 : -16777216;
            }
            return Color.parseColor(saved);
        } catch (Exception e) {
            return this.session.darkMode() ? -1 : -16777216;
        }
    }

    private int currentSecondary() {
        try {
            String saved = this.session.secondaryColor();
            if (saved.isEmpty()) {
                return this.session.darkMode() ? Color.rgb(150, 190, 220) : Color.rgb(35, 125, 178);
            }
            return Color.parseColor(saved);
        } catch (Exception e) {
            return this.session.darkMode() ? Color.rgb(150, 190, 220) : Color.rgb(35, 125, 178);
        }
    }

    private static String colorHex(int color) {
        return String.format(Locale.US, "#%02X%02X%02X", Integer.valueOf(Color.red(color)), Integer.valueOf(Color.green(color)), Integer.valueOf(Color.blue(color)));
    }

    private static int contrast(int color) {
        int luminance = (((Color.red(color) * 299) + (Color.green(color) * 587)) + (Color.blue(color) * 114)) / 1000;
        return luminance >= 150 ? -16777216 : -1;
    }

    private static int readableOn(int color) {
        return contrast(color);
    }

    private static int blend(int base, int overlay, float amount) {
        float value = Math.max(0.0f, Math.min(1.0f, amount));
        float keep = 1.0f - value;
        return Color.rgb(Math.round((Color.red(base) * keep) + (Color.red(overlay) * value)), Math.round((Color.green(base) * keep) + (Color.green(overlay) * value)), Math.round((Color.blue(base) * keep) + (Color.blue(overlay) * value)));
    }

    private String appVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.70";
        }
    }

    private void openImage(ImageView source, String url) {
        int[] location = new int[REPLY_PREVIEW_COUNT];
        source.getLocationOnScreen(location);
        Drawable drawable = source.getDrawable();
        if (drawable instanceof BitmapDrawable) {
            ImageViewerActivity.preparePreview(url, ((BitmapDrawable) drawable).getBitmap());
        }
        Intent intent = new Intent(this, (Class<?>) ImageViewerActivity.class);
        intent.putExtra("image_url", url);
        intent.putExtra("origin_x", location[0] + (source.getWidth() / REPLY_PREVIEW_COUNT));
        intent.putExtra("origin_y", location[1] + (source.getHeight() / REPLY_PREVIEW_COUNT));
        intent.putExtra("origin_width", source.getWidth());
        intent.putExtra("origin_height", source.getHeight());
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent("android.intent.action.VIEW", Uri.parse(url)));
        } catch (Exception e) {
            toast("无法打开链接");
        }
    }

    private void openUpdateUrl(String url) {
        String trusted = UpdateChecker.trustedUrlOrEmpty(url);
        if (TextUtils.isEmpty(trusted)) {
            toast("没有可用下载链接");
        } else {
            startInAppUpdateDownload(trusted);
        }
    }

    private void startInAppUpdateDownload(String url) {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            showLiteDialog("需要安装权限", "为了在 App 内完成更新，需要先允许 heybox Lite 安装未知来源应用。授权后请回到 App 再点一次下载。", "去授权", () -> {
                try {
                    Intent intent = new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    startActivity(new Intent("android.settings.SECURITY_SETTINGS"));
                }
            }, "取消", null, null, null);
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(1);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        int dialogBg = this.session.darkMode() ? Color.rgb(34, 34, 34) : Color.rgb(248, 248, 248);
        int border = this.session.darkMode() ? Color.rgb(72, 72, 72) : Color.rgb(215, 215, 215);
        Compat.setBackground(box, roundStroke(dialogBg, 14, border, 1));
        TextView titleView = text("正在下载更新", 16.0f, this.TEXT);
        titleView.setTypeface(appRegularTypeface(), 1);
        box.addView(titleView, new LinearLayout.LayoutParams(-1, -2));
        TextView state = text("准备下载...", 12.0f, this.MUTED);
        addTop(box, state, 8);
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        if (Build.VERSION.SDK_INT >= 21) {
            progress.setProgressTintList(ColorStateList.valueOf(this.PRIMARY));
            progress.setProgressBackgroundTintList(ColorStateList.valueOf(this.session.darkMode() ? Color.rgb(65, 65, 65) : Color.rgb(224, 224, 224)));
        }
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, dp(8));
        barParams.topMargin = dp(12);
        box.addView(progress, barParams);
        TextView hint = text("下载完成后会打开系统安装器", 11.0f, this.MUTED);
        hint.setGravity(17);
        addTop(box, hint, 10);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(box).create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0));
            dialog.getWindow().setDimAmount(this.session.darkMode() ? 0.46f : 0.32f);
            int width = getResources().getDisplayMetrics().widthPixels;
            dialog.getWindow().setLayout(Math.max(dp(220), Math.min(width - dp(28), dp(360))), -2);
        }
        new Thread(() -> {
            downloadUpdateApk(url, progress, state, dialog);
        }, "heybox-update-download").start();
    }

    private void downloadUpdateApk(String url, ProgressBar progress, TextView state, AlertDialog dialog) {
        HttpURLConnection connection = null;
        File output = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("Accept", "application/vnd.android.package-archive,*/*");
            connection.setRequestProperty("User-Agent", "heybox-Lite/" + appVersion());
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("下载失败，HTTP " + status);
            }
            int length = connection.getContentLength();
            File dir = new File(getCacheDir(), "updates");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("无法创建更新缓存目录");
            }
            output = new File(dir, "heybox-Lite-update-" + appVersion() + "-" + System.currentTimeMillis() + ".apk");
            long written = 0;
            byte[] buffer = new byte[16384];
            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(output)) {
                int count;
                while ((count = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, count);
                    written += count;
                    if (length > 0) {
                        int percent = Math.max(0, Math.min(100, (int) ((written * 100) / length)));
                        this.handler.post(() -> {
                            if (isFinishing()) {
                                return;
                            }
                            progress.setIndeterminate(false);
                            progress.setProgress(percent);
                            state.setText("已下载" + percent + "%");
                        });
                    } else {
                        this.handler.post(() -> {
                            if (isFinishing()) {
                                return;
                            }
                            progress.setIndeterminate(true);
                            state.setText("正在下载...");
                        });
                    }
                }
            }
            if (written < 131072) {
                throw new IllegalStateException("下载内容异常，未得到有效 APK");
            }
            final File ready = output;
            this.handler.post(() -> {
                if (isFinishing()) {
                    return;
                }
                progress.setIndeterminate(false);
                progress.setProgress(100);
                state.setText("下载完成，正在打开安装器...");
                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                }
                installDownloadedUpdate(ready);
            });
        } catch (Exception error) {
            if (output != null && output.exists()) {
                output.delete();
            }
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            if (this.localCache != null) {
                this.localCache.log("update download failed: " + message);
            }
            this.handler.post(() -> {
                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                }
                if (!isFinishing()) {
                    showLiteDialog("下载失败", message, "打开浏览器下载", () -> {
                        openUrl(url);
                    }, "知道了", null, null, null);
                }
            });
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void installDownloadedUpdate(File apk) {
        if (apk == null || !apk.isFile()) {
            toast("安装包不存在");
            return;
        }
        try {
            Uri uri = UpdateApkProvider.uriFor(apk);
            Intent intent = new Intent("android.intent.action.VIEW");
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(268435456);
            intent.addFlags(1);
            if (Build.VERSION.SDK_INT >= 16) {
                intent.setClipData(ClipData.newUri(getContentResolver(), apk.getName(), uri));
            }
            startActivity(intent);
        } catch (Exception error) {
            if (this.localCache != null) {
                this.localCache.log("update install failed: " + error.getClass().getSimpleName() + " " + error.getMessage());
            }
            showLiteDialog("无法打开安装器", "安装包已经下载完成，但系统安装器没有响应。可以改用浏览器下载后手动安装", "知道了", null, null, null, null, null);
        }
    }

    private void exportDiagnostics() {
        String diagnostics = buildDiagnostics();
        File file = this.localCache.writeDiagnostics(diagnostics);
        showLiteDialog("导出诊断日志",
                "可以分享给其他应用，也可以保存到 Download/heyboxlite",
                "本地", () -> saveDiagnosticsToDownloads(file.getName(), diagnostics),
                "取消", null,
                "分享", () -> shareDiagnostics(file));
    }

    private void shareDiagnostics(File file) {
        Uri fileUri = DiagnosticsProvider.uriFor(file);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= 16) {
            share.setClipData(ClipData.newUri(getContentResolver(), file.getName(), fileUri));
        }
        share.putExtra(Intent.EXTRA_SUBJECT, "heybox Lite diagnostics");
        share.putExtra(Intent.EXTRA_STREAM, fileUri);
        share.putExtra(Intent.EXTRA_TEXT, "heybox Lite diagnostics txt\nTXT file: " + file.getName());
        try {
            startActivity(Intent.createChooser(share, "分享诊断日志"));
        } catch (Exception e) {
            toast("没有可用的分享应用，日志已保存：" + file.getAbsolutePath());
        }
    }

    private void saveDiagnosticsToDownloads(String fileName, String diagnostics) {
        if (Build.VERSION.SDK_INT >= 29) {
            Uri uri = createDownloadsDocument(fileName);
            if (uri != null && writeDiagnosticsToUri(uri, diagnostics)) {
                toast("已保存到 Download/heyboxlite/" + fileName);
                return;
            }
        }
        File file = downloadsDiagnosticsFile(fileName);
        if (writeDiagnosticsToFile(file, diagnostics)) {
            toast("已保存到 " + file.getAbsolutePath());
        } else {
            toast("保存失败");
        }
    }

    private Uri createDownloadsDocument(String fileName) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/heyboxlite");
            return getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean writeDiagnosticsToUri(Uri uri, String diagnostics) {
        OutputStream output = null;
        try {
            output = getContentResolver().openOutputStream(uri);
            if (output == null) {
                return false;
            }
            output.write((diagnostics == null ? "" : diagnostics).getBytes("UTF-8"));
            output.flush();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private File downloadsDiagnosticsFile(String fileName) {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(new File(downloads, "heyboxlite"), fileName);
    }

    private boolean writeDiagnosticsToFile(File file, String diagnostics) {
        OutputStream output = null;
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            output = new FileOutputStream(file, false);
            output.write((diagnostics == null ? "" : diagnostics).getBytes("UTF-8"));
            output.flush();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private String buildDiagnostics() {
        StringBuilder out = new StringBuilder();
        out.append("heybox Lite diagnostics\n");
        out.append("exportTimeLocal: ").append(diagnosticTime()).append('\n');
        out.append("exportTimeMillis: ").append(System.currentTimeMillis()).append('\n');
        out.append("timeZone: ").append(TimeZone.getDefault().getID()).append('\n');
        out.append("sessionId: ").append(this.localCache.sessionId()).append('\n');
        out.append("sessionStartedLocal: ").append(diagnosticTime(this.localCache.sessionStartedAt())).append('\n');
        out.append("sessionStartedMillis: ").append(this.localCache.sessionStartedAt()).append('\n');
        out.append("version: ").append(appVersion()).append('\n');
        out.append("currentLinkId: ").append(this.currentLinkId).append('\n');
        out.append("currentLinkHsrc: ").append(this.currentLinkHsrc).append('\n');
        out.append("android: ").append(Build.VERSION.RELEASE).append(" api ").append(Build.VERSION.SDK_INT).append('\n');
        out.append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        out.append("screen: ").append(this.screen).append('\n');
        out.append("loggedIn: ").append(this.session.isLoggedIn()).append('\n');
        out.append("signInFlow: task-list-v2-web/v3-sign-key-web-mobile/state-ignore\n");
        out.append("feedCount: ").append(this.feed.size()).append('\n');
        out.append("feedOffset: ").append(this.feedOffset).append('\n');
        out.append("feedNoMore: ").append(this.feedNoMore).append('\n');
        out.append("offlineFeedSavedAt: ").append(this.localCache.feedSavedAt()).append('\n');
        out.append("offlineBytes: ").append(this.localCache.offlineBytes()).append('\n');
        out.append("cachedDetails: ").append(this.localCache.detailCount()).append('\n');
        out.append("imageMemoryCacheKb: ").append(ImageLoader.cacheSizeKb()).append('\n');
        out.append("emojiMemoryCacheKb: ").append(EmojiRenderer.cacheSizeKb()).append('\n');
        out.append("noImage: ").append(this.session.noImage()).append('\n');
        out.append("darkMode: ").append(this.session.darkMode()).append('\n');
        out.append("keywords: ").append(this.session.blockKeywords()).append('\n');
        if (!this.lastDetailDiagnostics.isEmpty()) {
            out.append("\nlast detail diagnostics:\n").append(this.lastDetailDiagnostics);
            if (!this.lastDetailDiagnostics.endsWith("\n")) {
                out.append('\n');
            }
        } else {
            out.append("\nlast detail diagnostics: none captured\n");
        }
        String crashLog = this.localCache.crashLog();
        if (!crashLog.trim().isEmpty()) {
            out.append("\nlast crash:\n").append(crashLog);
            if (!crashLog.endsWith("\n")) {
                out.append('\n');
            }
        }
        String previousCrash = this.localCache.previousCrashLog();
        if (!previousCrash.trim().isEmpty()) {
            out.append("\nprevious crash:\n").append(previousCrash);
            if (!previousCrash.endsWith("\n")) {
                out.append('\n');
            }
        }
        String nativeLog = this.localCache.nativeSignLog();
        if (!nativeLog.trim().isEmpty()) {
            out.append("\nnative signer events:\n").append(nativeLog);
            if (!nativeLog.endsWith("\n")) {
                out.append('\n');
            }
        }
        String previousLog = this.localCache.previousLog();
        if (!previousLog.trim().isEmpty()) {
            out.append("\nprevious session events:\n").append(previousLog);
            if (!previousLog.endsWith("\n")) {
                out.append('\n');
            }
        }
        out.append("\nrecent events:\n").append(this.localCache.recentLog());
        return out.toString();
    }

    private String diagnosticTime() {
        return diagnosticTime(System.currentTimeMillis());
    }

    private String diagnosticTime(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(millis));
    }

    private String buildDetailDiagnostics(JSONObject body, FeedItem fallback, JSONObject link, JSONArray fallbackImages) {
        StringBuilder out = new StringBuilder();
        out.append("detail screen: ").append(this.screen).append('\n');
        out.append("currentLinkId: ").append(this.currentLinkId).append('\n');
        out.append("fallbackId: ").append(fallback == null ? "" : fallback.id).append('\n');
        out.append("fallbackTitle: ").append(compactLogText(fallback == null ? "" : fallback.title, 180)).append('\n');
        out.append("fallbackArticle: ").append(fallback != null && fallback.article).append('\n');
        out.append("bodyKeys: ");
        if (body != null) {
            Iterator<String> keys = body.keys();
            while (keys.hasNext()) {
                out.append(keys.next()).append(' ');
            }
        }
        out.append('\n');
        if (link == null) {
            out.append("link: null\n");
        } else {
            out.append("linkid: ").append(link.optString("linkid", link.optString("link_id"))).append('\n');
            out.append("title: ").append(compactLogText(link.optString("title"), 180)).append('\n');
            out.append("use_concept_type: ").append(link.opt("use_concept_type")).append('\n');
            out.append("is_article: ").append(link.opt("is_article")).append('\n');
            out.append("link_type: ").append(link.opt("link_type")).append('\n');
            out.append("content_type: ").append(link.opt("content_type")).append('\n');
            out.append("has imgs: ").append(link.has("imgs")).append(" count=").append(link.optJSONArray("imgs") == null ? 0 : link.optJSONArray("imgs").length()).append('\n');
            out.append(RichContent.diagnostics(link, fallbackImages));
        }
        return out.toString();
    }

    private String compactLogText(String value, int max) {
        String clean = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, Math.max(0, max)) + "...";
    }

    /* JADX INFO: loaded from: MainActivity$ScaleControl.class */
    private static final class ScaleControl {
        final EditText input;
        final SeekBar slider;

        ScaleControl(EditText input, SeekBar slider) {
            this.input = input;
            this.slider = slider;
        }
    }

    private void addLegacySettings(LinearLayout page) {
        String strPrimaryColor;
        TextView settingsTitle = text("设置", 15.0f, this.TEXT);
        settingsTitle.setTypeface(appRegularTypeface(), 1);
        addTop(page, settingsTitle, 12);
        Switch images = new Switch(this);
        images.setText("无图模式");
        images.setTextColor(this.TEXT);
        images.setTextSize(sp(14.0f));
        images.setChecked(this.session.noImage());
        Compat.tint(images, this.PRIMARY);
        images.setOnCheckedChangeListener((button, checked) -> {
            this.session.setNoImage(checked);
            this.feed.clear();
        });
        addTop(page, images, 8);
        Switch originals = new Switch(this);
        originals.setText("正文图片显示原图");
        originals.setTextColor(this.TEXT);
        originals.setTextSize(sp(14.0f));
        originals.setChecked(this.session.originalImages());
        Compat.tint(originals, this.PRIMARY);
        originals.setOnCheckedChangeListener((button2, checked2) -> {
            this.session.setOriginalImages(checked2);
        });
        addTop(page, originals, REPLY_PREVIEW_COUNT);
        Switch theme = new Switch(this);
        theme.setText(this.session.darkMode() ? "夜间模式" : "白天模式");
        theme.setTextColor(this.TEXT);
        theme.setTextSize(sp(14.0f));
        theme.setChecked(this.session.darkMode());
        Compat.tint(theme, this.PRIMARY);
        theme.setOnCheckedChangeListener((button3, checked3) -> {
            this.session.setDarkMode(checked3);
            recreate();
        });
        addTop(page, theme, REPLY_PREVIEW_COUNT);
        LinearLayout display = card();
        TextView displayTitle = text("显示", 15.0f, this.TEXT);
        displayTitle.setTypeface(appRegularTypeface(), 1);
        display.addView(displayTitle);
        EditText uiScale = numberField(display, "界面大小 (%)", this.session.uiScale());
        EditText textScale = numberField(display, "文字大小 (%)", this.session.textScale());
        EditText padding = numberField(display, "左右边距 (dp)", this.session.pagePadding());
        if (this.session.primaryColor().isEmpty()) {
            strPrimaryColor = this.session.darkMode() ? "#FFFFFF" : "#000000";
        } else {
            strPrimaryColor = this.session.primaryColor();
        }
        EditText accent = textField(display, "主色", strPrimaryColor);
        Button save = button("保存显示设置", R.drawable.ic_settings);
        save.setOnClickListener(view -> {
            Integer ui = parseNumber(uiScale, 70, 160);
            Integer text = parseNumber(textScale, 70, 180);
            Integer pad = parseNumber(padding, 0, NAV_BAR_HEIGHT_DP);
            String accentValue = accent.getText().toString().trim();
            if (ui == null || text == null || pad == null || !validColor(accentValue)) {
                toast("请输入有效数字：界面 70-160，文70-180，边0-30");
                return;
            }
            this.session.setUiScale(ui.intValue());
            this.session.setTextScale(text.intValue());
            this.session.setPagePadding(pad.intValue());
            this.session.setPrimaryColor(accentValue);
            toast("显示设置已保存");
            recreate();
        });
        addTop(display, save, 8);
        addTop(page, display, 8);
        Button clearCache = button("清除缓存", R.drawable.ic_trash);
        clearCache.setOnClickListener(view2 -> {
            int before = ImageLoader.cacheSizeKb();
            ImageLoader.clear();
            toast("已清除缓存 " + Math.max(1, before) + " KB");
        });
        addTop(page, clearCache, 8);
        Button login = button(this.session.isLoggedIn() ? "退出登录" : "二维码登录", R.drawable.ic_logout);
        login.setOnClickListener(view3 -> {
            if (this.session.isLoggedIn()) {
                this.session.clearSession();
                this.feed.clear();
                toast("已退出登录");
            }
            showLogin();
        });
        addTop(page, login, 12);
    }

    private EditText numberField(LinearLayout parent, String label, int current) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(16);
        row.addView(text(label, 12.0f, this.TEXT), new LinearLayout.LayoutParams(0, dp(40), 1.0f));
        EditText input = new EditText(this);
        input.setText(String.valueOf(current));
        input.setTextColor(this.TEXT);
        input.setHintTextColor(this.MUTED);
        input.setTextSize(sp(13.0f));
        input.setGravity(17);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setInputType(REPLY_PREVIEW_COUNT);
        Compat.tint(input, this.PRIMARY);
        row.addView(input, new LinearLayout.LayoutParams(dp(72), dp(40)));
        addTop(parent, row, 3);
        return input;
    }

    private EditText textField(LinearLayout parent, String label, String current) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(16);
        row.addView(text(label, 12.0f, this.TEXT), new LinearLayout.LayoutParams(0, dp(40), 1.0f));
        EditText input = new EditText(this);
        input.setText(current);
        input.setTextColor(this.TEXT);
        input.setTextSize(sp(12.0f));
        input.setGravity(17);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        Compat.tint(input, this.PRIMARY);
        row.addView(input, new LinearLayout.LayoutParams(dp(96), dp(40)));
        addTop(parent, row, 3);
        return input;
    }

    private long tempCacheBytes() {
        return dirSize(getCacheDir()) + (((long) EmojiRenderer.cacheSizeKb()) * 1024);
    }

    private void pruneOfflineCache(Runnable complete) {
        new Thread(() -> {
            this.localCache.pruneExpired(OFFLINE_MAX_AGE_MS);
            ImageLoader.pruneOffline(this, OFFLINE_MAX_AGE_MS);
            if (complete != null) {
                this.handler.post(() -> {
                    if (!isFinishing()) complete.run();
                });
            }
        }, "heybox-offline-cleanup").start();
    }

    private long cacheBytes() {
        return tempCacheBytes() + (((long) ImageLoader.cacheSizeKb()) * 1024);
    }

    private long dirSize(File file) {
        if (file == null || !file.exists()) {
            return 0L;
        }
        if (file.isFile()) {
            return Math.max(0L, file.length());
        }
        File[] children = file.listFiles();
        if (children == null) {
            return 0L;
        }
        long total = 0;
        for (File child : children) {
            total += dirSize(child);
        }
        return total;
    }

    private void clearTempCacheFiles(File dir) {
        File[] children;
        if (dir == null || !dir.isDirectory() || (children = dir.listFiles()) == null) {
            return;
        }
        for (File child : children) {
            deleteFileTree(child);
        }
    }

    private void deleteFileTree(File file) {
        File[] children;
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory() && (children = file.listFiles()) != null) {
            for (File child : children) {
                deleteFileTree(child);
            }
        }
        file.delete();
    }

    private String formatCacheMb(long bytes) {
        return String.format(Locale.US, "%.1f MB", Float.valueOf(Math.max(0L, bytes) / 1048576.0f));
    }

    private boolean validColor(String value) {
        try {
            Color.parseColor(value);
            return value.matches("#[0-9a-fA-F]{6}");
        } catch (Exception e) {
            return false;
        }
    }

    private Integer parseNumber(EditText input, int min, int max) {
        try {
            int value = Integer.parseInt(input.getText().toString().trim());
            if (value < min || value > max) {
                return null;
            }
            return Integer.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    @Override // android.app.Activity
    public void onBackPressed() {
        this.pendingBackTransition = true;
        if ("detail".equals(this.screen)) {
            if (this.detailPager != null && this.detailPager.showingComments()) {
                this.pendingBackTransition = false;
                this.detailPager.showArticle(true);
                return;
            } else {
                returnFromDetailSmooth();
                return;
            }
        }
        if ("user_space".equals(this.screen)) {
            returnFromUserSpace();
            return;
        }
        if (!"saved".equals(this.screen)) {
            if (!"announcement_board".equals(this.screen)) {
                if (!"display_preview".equals(this.screen)) {
                    if (!"display_settings".equals(this.screen) && !"startup_settings".equals(this.screen) && !"app_settings".equals(this.screen) && !"about".equals(this.screen)) {
                        if (!"settings_home".equals(this.screen)) {
                            if ("feed".equals(this.screen)) {
                                super.onBackPressed();
                                return;
                            } else {
                                showFeed();
                                return;
                            }
                        }
                        showProfile();
                        return;
                    }
                    showSettingsHome();
                    return;
                }
                showDisplaySettings();
                return;
            }
            showAbout();
            return;
        }
        showProfile();
    }

    private void returnFromDetail() {
        saveCurrentDetailProgress();
        this.detailRequestToken++;
        this.detailPager = null;
        this.detailScroll = null;
        if (restoreDetailReturnView()) {
            return;
        }
        if (!"saved".equals(this.detailReturn)) {
            if (!"search".equals(this.detailReturn)) {
                showFeed();
                return;
            } else {
                showSearch(true);
                return;
            }
        }
        showProfile();
    }

    private void returnFromDetailSmooth() {
        returnFromDetail();
    }

    private void returnFromUserSpace() {
        FeedItem item = this.userSpaceReturnItem;
        String returnScreen = this.userSpaceReturnScreen;
        this.userSpaceReturnItem = null;
        this.userSpaceReturnScreen = "feed";
        if (item != null) {
            this.pendingDetailReturn = TextUtils.isEmpty(returnScreen) ? "feed" : returnScreen;
            showDetail(item);
        } else {
            showFeed();
        }
    }

    private boolean shouldKeepDetailReturnView(String screenKey) {
        return "search".equals(screenKey) || "saved".equals(screenKey) || "user_space".equals(screenKey);
    }

    private boolean restoreDetailReturnView() {
        // 详情返回直接恢复原 View（保持滚动位置），不走页面转场
        this.pendingBackTransition = false;
        PageTransitionController.finishNow();
        if (this.detailReturnView == null || !shouldKeepDetailReturnView(this.detailReturn)) {
            this.detailReturnView = null;
            return false;
        }
        View view = this.detailReturnView;
        this.detailReturnView = null;
        this.content.removeAllViews();
        this.content.addView(view, match());
        this.screen = this.detailReturn;
        this.title.setText(this.detailReturnTitle == null ? "" : this.detailReturnTitle);
        this.leading.setVisibility(0);
        this.leading.setOnClickListener(v -> onBackPressed());
        this.action.setVisibility(4);
        setBottomNavVisible(false);
        if ("user_space".equals(this.screen)) {
            this.title.setText("动态");
        }
        // 离屏期间列表若收到过 notifyDataSetChanged（如加载更多回包），重挂载会丢滚动位置，这里强制回到进详情前的位置
        if ("search".equals(this.screen) && this.searchListView != null && this.searchListPosition >= 0) {
            final ListView list = this.searchListView;
            final int position = this.searchListPosition;
            final int offset = this.searchListTopOffset;
            list.post(() -> {
                if (list.getWindowToken() != null) {
                    list.setSelectionFromTop(position, offset);
                }
            });
        }
        return true;
    }

    private void saveCurrentDetailProgress() {
        if (!"detail".equals(this.screen) || this.detailScroll == null || this.currentLinkId.isEmpty() || this.localCache == null || !this.session.rememberDetailScroll()) {
            return;
        }
        this.localCache.saveScroll(this.currentLinkId, this.detailScroll.getScrollY());
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        PresenceReporter.ping(this.session);
        this.handler.removeCallbacks(this.presenceTick);
        this.handler.postDelayed(this.presenceTick, 150_000L);
    }

    @Override // android.app.Activity
    protected void onPause() {
        this.handler.removeCallbacks(this.presenceTick);
        saveCurrentDetailProgress();
        super.onPause();
    }

    /** 前台在线心跳：每 2.5 分钟一次，保证 5 分钟在线窗口内持续可见；退后台即停止。 */
    private final Runnable presenceTick = new Runnable() {
        @Override
        public void run() {
            PresenceReporter.ping(MainActivity.this.session);
            MainActivity.this.handler.postDelayed(this, 150_000L);
        }
    };

    @Override // android.app.Activity
    protected void onDestroy() {
        saveCurrentDetailProgress();
        stopQrPolling();
        ImageLoader.cancelTree(this.content);
        this.handler.removeCallbacksAndMessages(null);
        if (this.writeTokenProvider != null) {
            this.writeTokenProvider.close();
        }
        if (this.api != null) {
            this.api.close();
        }
        super.onDestroy();
    }

    /** 页面切换统一入口：真实双 View 转场；方向由 pendingBackTransition 决定，消费后复位。 */
    private void transitionTo(View next) {
        boolean back = this.pendingBackTransition;
        this.pendingBackTransition = false;
        if (this.shellAnimating) {
            PageTransitionController.finishNow();
            this.content.removeAllViews();
            this.content.addView(next, match());
            return;
        }
        PageTransitionController.run(this.content, next, !back);
    }

    private void showLoading() {
        hideLoading();
        LoadingSpinnerView progress = new LoadingSpinnerView(this);
        progress.setTag("loading");
        progress.setColor(this.PRIMARY);
        this.content.addView(progress, new FrameLayout.LayoutParams(dp(38), dp(38), 17));
    }

    private void hideLoading() {
        View loading = this.content.findViewWithTag("loading");
        if (loading != null) {
            this.content.removeView(loading);
        }
    }

    private void showMessage(String message) {
        this.content.removeAllViews();
        FrameLayout box = new FrameLayout(this);
        box.setBackgroundColor(this.BG);
        TextView view = text(message, 13.0f, this.MUTED);
        view.setGravity(17);
        view.setPadding(dp(18), dp(16), dp(18), dp(16));
        view.setMaxWidth(dp(260));
        view.setLineSpacing(0.0f, 1.15f);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, 17);
        box.addView(view, params);
        this.content.addView(box, match());
    }

    private LinearLayout card() {
        ThemeTokens themeTokensOf;
        LinearLayout card = vertical(this.PANEL);
        card.setPadding(dp(12), dp(11), dp(12), dp(11));
        if (this.themeTokens == null) {
            themeTokensOf = ThemeTokens.of(this.session != null && this.session.darkMode(), this.PRIMARY, this.SECONDARY);
        } else {
            themeTokensOf = this.themeTokens;
        }
        ThemeTokens tokens = themeTokensOf;
        Compat.setBackground(card, UiComponents.card(this, tokens, this.session == null ? 1.0f : this.session.uiScale() / 100.0f));
        return card;
    }

    private TextView icon(String value) {
        TextView view = text(value, 23.0f, this.TEXT);
        view.setGravity(17);
        view.setContentDescription(value);
        return view;
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextSize(sp(size));
        view.setTextColor(color);
        view.setTypeface(appRegularTypeface());
        Compat.setLetterSpacing(view, 0.0f);
        return view;
    }

    private Button secondaryButton(String value, int iconRes) {
        ThemeTokens tokens = this.themeTokens != null ? this.themeTokens
                : ThemeTokens.of(this.session != null && this.session.darkMode(), this.PRIMARY, this.SECONDARY);
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(sp(12.0f));
        button.setTextColor(tokens.text);
        button.setAllCaps(false);
        button.setTypeface(appRegularTypeface(), Typeface.BOLD);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setGravity(17);
        Compat.setBackground(button, UiComponents.ghostButton(this, tokens,
                this.session == null ? 1.0f : this.session.uiScale() / 100.0f));
        if (iconRes != 0) {
            setLeftIcon(button, iconRes, tokens.text, 17);
        }
        return button;
    }

    private Button button(String value) {
        return button(value, 0);
    }

    private Button button(String value, int iconRes) {
        ThemeTokens themeTokensOf;
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(sp(12.0f));
        if (this.themeTokens == null) {
            themeTokensOf = ThemeTokens.of(this.session != null && this.session.darkMode(), this.PRIMARY, this.SECONDARY);
        } else {
            themeTokensOf = this.themeTokens;
        }
        ThemeTokens tokens = themeTokensOf;
        int foreground = tokens.onPrimary;
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setTypeface(appRegularTypeface(), Typeface.BOLD);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setGravity(17);
        Compat.setBackground(button, UiComponents.primaryButton(this, tokens, this.session == null ? 1.0f : this.session.uiScale() / 100.0f));
        if (iconRes != 0) {
            setLeftIcon(button, iconRes, foreground, 17);
        }
        return button;
    }

    private Typeface appRegularTypeface() {
        return Typeface.DEFAULT;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private GradientDrawable roundStroke(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = round(color, radius);
        drawable.setStroke(Math.max(1, dp(strokeWidth)), strokeColor);
        return drawable;
    }

    private void setIcon(TextView view, int resource, int color, int size) {
        Drawable drawable = Compat.tintedDrawable(this, resource, color);
        if (drawable == null) {
            return;
        }
        drawable.setBounds(0, 0, dp(size), dp(size));
        view.setCompoundDrawables(null, drawable, null, null);
    }

    private void setLeftIcon(TextView view, int resource, int color, int size) {
        Drawable drawable = Compat.tintedDrawable(this, resource, color);
        if (drawable == null) {
            return;
        }
        drawable.setBounds(0, 0, dp(size), dp(size));
        view.setCompoundDrawables(drawable, null, null, null);
        view.setCompoundDrawablePadding(dp(4));
    }

    private void animateIn(View view) {
        // 页面转场期间内容直接到位，避免与转场动画叠加
        if (this.shellAnimating || PageTransitionController.isRunning()) {
            Motions.reset(view);
        } else {
            Motions.enter(view, dp(6));
        }
    }

    private void runWithPressFeedback(View view, Runnable action) {
        if (action == null) {
            return;
        }
        // 反馈与动作并行：立即执行动作，按压回弹只是视觉效果，不拖慢响应
        if (!Motions.off()) {
            view.animate().cancel();
            view.animate().scaleX(0.975f).scaleY(0.975f)
                    .setDuration(MotionSpec.PRESS_IN_MS)
                    .setInterpolator(MotionSpec.EASE_OUT)
                    .withEndAction(() -> view.animate().scaleX(1.0f).scaleY(1.0f)
                            .setDuration(MotionSpec.PRESS_OUT_MS)
                            .setInterpolator(Motions.full() ? MotionSpec.SPRING : MotionSpec.EASE_OUT)
                            .start())
                    .start();
        }
        if (!isFinishing()) {
            action.run();
        }
    }

    private LinearLayout vertical(int color) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(1);
        layout.setBackgroundColor(color);
        return layout;
    }

    private void addTop(LinearLayout parent, View view, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(margin);
        parent.addView(view, params);
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

    private int dp(int value) {
        float scale = this.session == null ? 1.0f : this.session.uiScale() / 100.0f;
        return Math.round(value * getResources().getDisplayMetrics().density * scale);
    }

    private float sp(float value) {
        float scale = this.session == null ? 1.0f : this.session.textScale() / 100.0f;
        return value * scale;
    }

    private void toast(String message) {
        Toast.makeText(this, message, 1).show();
    }

    private String formatTime(long seconds) {
        return seconds <= 0 ? "" : new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(seconds * 1000));
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }
}
