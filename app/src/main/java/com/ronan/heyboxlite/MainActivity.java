package com.ronan.heyboxlite;

import android.annotation.SuppressLint;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
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
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.AbsListView;
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
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;
@SuppressLint("WrongConstant")
public final class MainActivity extends Activity {
    private static final int REPLY_PREVIEW_COUNT = 2;
    private static final int REPLY_PAGE_SIZE = 5;
    private static final int AXIS_ROTARY_SCROLL = 26;
    private static final String TRANSITION_OVERLAY_TAG = "shell_transition_overlay";
    private static final String WELCOME_ANNOUNCEMENT_ID = "welcome-heybox-lite-1.77";
    private static final boolean SIGN_IN_ENABLED = false;
    private static final long OFFLINE_MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L;
    private static final String[] THEME_NAMES = {"默认蓝", "红色", "粉色", "紫色", "绿色", "青色", "橙色", "黄色", "灰色", "深蓝", "黑金", "薄荷绿"};
    private static final int[][] THEME_COLORS = {new int[]{-14386760, -9193242}, new int[]{-3982790, -1083529}, new int[]{-2597743, -1006399}, new int[]{-9022795, -4744481}, new int[]{-14185897, -9320552}, new int[]{-15299695, -9713717}, new int[]{-2921692, -1007516}, new int[]{-3958250, -995480}, new int[]{-7894890, -5327686}, new int[]{-15253642, -10646588}, new int[]{-15263977, -3102658}, new int[]{-13530253, -7808833}};
    private static final String TITLE_FAVORITES = "我的收藏";
    private static final String MSG_OFFLINE_CACHE = "已显示离线缓存";
    private static final String MSG_EMPTY_CONTENT = "暂无内容";
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
    private WriteActionClient writeActions;
    private QrLoginController qrLoginController;
    private SignInManager signInManager;
    private ReadingTimeTracker readingTimeTracker;
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
    private TextView readingTodayView;
    private ScrollView detailScroll;
    private ScrollView detailCommentScroll;
    private DetailPager detailPager;
    private boolean shellAnimating;
    private LocalCache localCache;
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
    private long lastExitBackAt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PageTransitionController pageTransitions = new PageTransitionController();
    private final List<FeedItem> feed = new ArrayList();
    private String cachedProfileUserId = "";
    private final Map<View, Integer> searchBarHeights = new HashMap();
    private final Map<View, Boolean> searchBarStates = new HashMap();
    private boolean pendingBackTransition;
    private boolean pendingLateralPush;
    private final SearchState searchState = new SearchState();
    private ListView searchListView;
    private final Map<String, LikeState> linkLikeOverrides = new HashMap();
    private final Map<String, Bitmap> screenSnapshots = new HashMap();
    private final Map<String, Bitmap> fullScreenSnapshots = new HashMap();
    private final Map<String, View> retainedPages = new HashMap();
    private String screen = "feed";
    private String savedReturnScreen = "profile";
    private String detailReturn = "feed";
    private View detailReturnView;
    private String detailReturnTitle = "";
    private String currentLinkId = "";
    private String currentLinkHsrc = "";
    private String currentAuthCode = "";
    private String lastDetailDiagnostics = "";
    private JSONObject currentDetailBody;
    private boolean activityResumed;
    private boolean accountBlockedScreen;
    private TextView accountBlockedMessage;
    private interface IntListener {
        void onChanged(int i);
    }
    private interface SavedListFallback {
        boolean onFallback(String str);
    }
    private interface ToggleListener {
        void onChanged(boolean z);
    }
    private static final class CommentLikeControl {
        final LinearLayout root;
        final ImageView icon;
        final TextView count;

        CommentLikeControl(LinearLayout root, ImageView icon, TextView count) {
            this.root = root;
            this.icon = icon;
            this.count = count;
        }
    }
    private static final class LikeState {
        final boolean liked;
        final int likes;

        LikeState(boolean liked, int likes) {
            this.liked = liked;
            this.likes = Math.max(0, likes);
        }
    }

    @Override
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
        this.readingTimeTracker = new ReadingTimeTracker(this);
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
        this.writeTokenProvider = new WriteTokenProvider(this, this.session, this.api);
        this.writeActions = new WriteActionClient(this.api, this.session, this.writeTokenProvider,
                this.handler, message -> {
                    if (this.localCache != null) this.localCache.log(message);
                });
        this.qrLoginController = new QrLoginController(this.api, this.handler);
        if (SIGN_IN_ENABLED) {
            this.signInManager = new SignInManager(this.session, this.api, this.writeTokenProvider, message2 -> {
                if (this.localCache != null) {
                    this.localCache.log(message2);
                }
            });
        }
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
        if (this.session.appBlocked()) {
            showAccountBlocked(this.session.appBlockMessage());
        }
        PresenceReporter.ping(this.session, this.readingTimeTracker, this::applyAccessStatus);
        RemoteConfig.load(this.session.userId(), () ->
                applyAccessStatus(RemoteConfig.accessStatus()));
        if (!this.accountBlockedScreen && this.session.autoUpdateCheck()) {
            this.handler.postDelayed(this::checkUpdateOnLaunch, 650L);
        }
        if (!this.accountBlockedScreen) {
            this.handler.postDelayed(this::checkAnnouncementOnLaunch, 950L);
        }
    }

    private void applyAccessStatus(AccessStatus status) {
        if (status == null || isFinishing()) return;
        this.session.setAppBlocked(status.banned, status.message);
        if (status.banned) {
            showAccountBlocked(status.message);
        } else if (this.accountBlockedScreen) {
            this.accountBlockedScreen = false;
            recreate();
        }
    }

    private void showAccountBlocked(String message) {
        String text = TextUtils.isEmpty(message) ? "该账号或设备已被停用。" : message;
        if (this.accountBlockedScreen && this.accountBlockedMessage != null) {
            this.accountBlockedMessage.setText(text);
            return;
        }
        this.accountBlockedScreen = true;
        this.handler.removeCallbacksAndMessages(null);
        if (this.readingTimeTracker != null) this.readingTimeTracker.pause();
        if (this.api != null) this.api.close();

        LinearLayout page = vertical(this.BG);
        page.setGravity(17);
        page.setPadding(dp(24), dp(24), dp(24), dp(24));
        TextView title = text("无法使用", 20.0f, this.TEXT);
        title.setTypeface(appRegularTypeface(), 1);
        title.setGravity(17);
        page.addView(title, new LinearLayout.LayoutParams(-1, -2));

        this.accountBlockedMessage = text(text, 12.5f, this.MUTED);
        this.accountBlockedMessage.setGravity(17);
        this.accountBlockedMessage.setLineSpacing(dp(2), 1.15f);
        addTop(page, this.accountBlockedMessage, 10);

        TextView retry = text("重新检查", 12.5f, Color.WHITE);
        retry.setGravity(17);
        retry.setPadding(dp(18), 0, dp(18), 0);
        Compat.setBackground(retry, round(this.PRIMARY, 8));
        retry.setOnClickListener(view -> PresenceReporter.pingNow(
                this.session, this.readingTimeTracker, this::applyAccessStatus));
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(-2, dp(38));
        retryParams.topMargin = dp(18);
        page.addView(retry, retryParams);
        setContentView(page);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (event != null && event.getActionMasked() == MotionEvent.ACTION_SCROLL) {
            float axis = event.getAxisValue(AXIS_ROTARY_SCROLL);
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
        if (!SIGN_IN_ENABLED || isFinishing() || this.signInManager == null || !this.session.isLoggedIn()) {
            return;
        }
        this.signInManager.autoSignInIfNeeded(result -> {
            if (!isFinishing() && "profile".equals(this.screen)) {
                showProfile();
            }
        });
    }

    private void checkUpdateOnLaunch() {
        UpdateChecker.check(appVersion(), this.session.userId(), this.session.testReleaseId(),
                new UpdateChecker.Callback() {
            @Override
            public void onResult(UpdateChecker.Result result) {
                if (!result.updateAvailable || MainActivity.this.isFinishing()) {
                    return;
                }
                if (result.title != null || result.notes != null) {
                    MainActivity.this.showUpdateDialog(result);
                } else {
                    String target = result.downloadUrl.isEmpty() ? result.releaseUrl : result.downloadUrl;
                    MainActivity.this.showLiteDialog("发现新版本 " + result.version, "heybox Lite 有新版本可用，是否前往下载", "下载", () -> {
                        MainActivity.this.rememberTestRelease(result);
                        MainActivity.this.openUpdateUrl(target);
                    }, "稍后", null, null, null);
                }
            }

            @Override
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
            AnnouncementChecker.load(new AnnouncementChecker.Callback() {
                @Override
                public void onResult(List<AnnouncementChecker.Item> items) {
                    AnnouncementChecker.Item item;
                    if (MainActivity.this.isFinishing() || (item = MainActivity.this.firstUnseenAnnouncement(items)) == null) {
                        return;
                    }
                    MainActivity.this.showAnnouncementDialog(item);
                }

                @Override
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
        return new AnnouncementChecker.Item(WELCOME_ANNOUNCEMENT_ID, "欢迎使用 heybox Lite", "欢迎使用 heybox Lite。这里会放版本公告和重要提醒。\n\n遇到 bug 或有建议，可以加入交流群：781941517。\n\n本项目仅用于学习、研究与个人使用，请在遵守平台规则的前提下使用", "normal", appVersion(), true, true);
    }

    private AnnouncementChecker.Item firstUnseenAnnouncement(List<AnnouncementChecker.Item> items) {
        if (items == null) {
            return null;
        }
        for (AnnouncementChecker.Item item : items) {
            if (item != null && item.enabled && (!item.title.isEmpty() || !item.content.isEmpty())) {
                if (!item.onceOnly || item.id.isEmpty() || this.session == null
                        || !this.session.isAnnouncementSeen(item.id)) {
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
            if (item.onceOnly) markAnnouncementSeen(item);
        };
        String neutralText = null;
        Runnable neutral = null;
        if (WELCOME_ANNOUNCEMENT_ID.equals(item.id)) {
            neutralText = "群二维码";
            neutral = () -> {
                if (item.onceOnly) markAnnouncementSeen(item);
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
            rememberTestRelease(result);
            openUpdateUrl(target);
        }, "稍后", null, null, null);
    }

    private void rememberTestRelease(UpdateChecker.Result result) {
        if (result != null && "test".equals(result.channel) && result.releaseId > 0) {
            this.session.setTestReleaseId(result.releaseId);
        }
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
        LinearLayout.LayoutParams accentParams = new LinearLayout.LayoutParams(dp(34), dp(2));
        accentParams.topMargin = dp(7);
        linearLayout.addView(accent, accentParams);
        MaxHeightScrollView scroll = new MaxHeightScrollView(this);
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(1);
        TextView body = text(message, 13.0f, this.TEXT);
        body.setLineSpacing(dp(2), 1.08f);
        body.setPadding(0, dp(12), 0, dp(2));
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
        setIcon(this.leading, R.drawable.ic_arrow_back, this.TEXT, 20);
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
        int navFill = Color.argb(this.session.darkMode() ? 232 : 224,
                Color.red(this.themeTokens.panelElevated),
                Color.green(this.themeTokens.panelElevated),
                Color.blue(this.themeTokens.panelElevated));
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
        view.setPadding(insets[0], insets[1], insets[2], insets[3]);
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
            horizontal = Math.max(horizontal, 5);
            vertical = Math.max(vertical, 3);
        }
        int horizontal2 = Math.max(0, Math.min(30, horizontal));
        int vertical2 = Math.max(0, Math.min(30, vertical));
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
        this.PRIMARY = parseThemeColor(this.session.primaryColor(), Color.rgb(36, 121, 184));
        this.SECONDARY = parseThemeColor(this.session.secondaryColor(), Color.rgb(94, 158, 255));
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
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(36), 1.0f);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        this.bottom.addView(item, params);
    }

    private void setBottomNavVisible(boolean visible) {
        setBottomNavVisible(visible, true);
    }

    private void setBottomNavVisible(boolean visible, boolean animate) {
        if (this.bottom == null) {
            return;
        }
        animate = animate && !Motions.off();
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
            icon.setBounds(0, 0, dp(20), dp(20));
        }
        return icon;
    }

    private boolean canHeaderBack() {
        return "detail".equals(this.screen) || "user_space".equals(this.screen) || "search".equals(this.screen) || "saved".equals(this.screen) || "reading_center".equals(this.screen) || "reading_stats".equals(this.screen) || "announcement_board".equals(this.screen) || "settings_home".equals(this.screen) || "display_preview".equals(this.screen) || "display_settings".equals(this.screen) || "startup_settings".equals(this.screen) || "app_settings".equals(this.screen) || "about".equals(this.screen);
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
        if ("detail".equals(this.screen)) return this.detailReturn;
        if ("user_space".equals(this.screen)) {
            return this.userSpaceReturnItem == null ? this.userSpaceReturnScreen : "detail";
        }
        if ("search".equals(this.screen)) return "feed";
        if ("saved".equals(this.screen)) return this.savedReturnScreen;
        if ("reading_stats".equals(this.screen)) return "reading_center";
        if ("reading_center".equals(this.screen)) return "profile";
        if ("announcement_board".equals(this.screen)) return "about";
        if ("display_preview".equals(this.screen)) return "display_settings";
        if ("display_settings".equals(this.screen) || "startup_settings".equals(this.screen)
                || "app_settings".equals(this.screen) || "about".equals(this.screen)) {
            return "settings_home";
        }
        return "settings_home".equals(this.screen) ? "profile" : "feed";
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

    private void recycleSnapshots(Map<String, Bitmap> snapshots) {
        for (Bitmap bitmap : snapshots.values()) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
        snapshots.clear();
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

    private View realShellPreview(String key) {
        View target = "feed".equals(key) ? this.cachedFeedContainer
                : "profile".equals(key) ? this.cachedProfileContainer
                : this.retainedPages.get(key);
        return target != null && target.getParent() == null ? target : null;
    }

    private void configureAdoptedShellScreen(String key) {
        if ("feed".equals(key)) {
            activate("feed");
            this.title.setText("社区");
            this.action.setVisibility(4);
            restoreFeedScroll();
            return;
        }
        if ("profile".equals(key)) {
            this.userSpaceReturnItem = null;
            this.userSpaceReturnScreen = "feed";
            activate("profile");
            updateReadingTimeEntry();
            this.title.setText("我的");
            this.action.setVisibility(0);
            setIcon(this.action, R.drawable.il_refresh, this.TEXT, 19);
            this.action.setOnClickListener(view -> {
                this.cachedProfileContainer = null;
                showProfile();
            });
            return;
        }
        if ("reading_stats".equals(key)) {
            this.screen = key;
            setBottomNavVisible(false, false);
            this.title.setText("阅读时长");
            this.leading.setVisibility(0);
            this.leading.setOnClickListener(view -> {
                this.pendingBackTransition = true;
                showReadingCenter();
            });
            this.action.setVisibility(4);
            return;
        }
        if ("reading_center".equals(key)) {
            this.screen = key;
            setBottomNavVisible(false, false);
            this.title.setText("阅读中心");
            this.leading.setVisibility(0);
            this.leading.setOnClickListener(view -> {
                this.pendingBackTransition = true;
                showProfile();
            });
            this.action.setVisibility(4);
            return;
        }
        this.screen = key;
        setBottomNavVisible(false, false);
        this.leading.setVisibility(0);
        this.action.setVisibility(4);
        if ("settings_home".equals(key)) {
            this.title.setText("设置中心");
            this.leading.setOnClickListener(view -> {
                this.pendingBackTransition = true;
                showProfile();
            });
        } else if ("display_settings".equals(key)) {
            this.title.setText("显示与主题");
            this.leading.setOnClickListener(view -> {
                this.pendingBackTransition = true;
                showSettingsHome();
            });
        } else if ("display_preview".equals(key)) {
            this.title.setText("界面预览");
            this.leading.setOnClickListener(view -> {
                this.pendingBackTransition = true;
                showDisplaySettings();
            });
        } else if ("startup_settings".equals(key)) {
            this.title.setText("启动与更新");
            this.leading.setOnClickListener(view -> {
                this.pendingBackTransition = true;
                showSettingsHome();
            });
        } else if ("app_settings".equals(key)) {
            this.title.setText("内容与网络");
            this.leading.setOnClickListener(view -> {
                this.pendingBackTransition = true;
                showSettingsHome();
            });
        } else if ("about".equals(key)) {
            this.title.setText("关于");
            this.leading.setOnClickListener(view -> {
                this.pendingBackTransition = true;
                showSettingsHome();
            });
        }
    }

    private void activate(String key) {
        stopQrPolling();
        this.screen = key;
        if (this.shellBar != null) {
            this.shellBar.setVisibility(8);
        }
        setBottomNavVisible(true);
        this.leading.setVisibility(4);
        int activeColor = this.themeTokens.accent;
        int inactiveColor = this.MUTED;
        for (int i = 0; i < this.bottom.getChildCount(); i++) {
            View item = this.bottom.getChildAt(i);
            boolean active = key.equals(item.getTag());
            item.setAlpha(active ? 1.0f : 0.62f);
            if (item instanceof ImageView) {
                ((ImageView) item).setColorFilter(active ? activeColor : inactiveColor);
                Compat.setBackground(item, active
                        ? UiComponents.softPill(this, this.themeTokens,
                        this.session.uiScale() / 100.0f) : null);
            } else if (item instanceof TextView) {
                TextView textItem = (TextView) item;
                textItem.setTextColor(active ? activeColor : inactiveColor);
                textItem.setTypeface(appRegularTypeface(), active ? 1 : 0);
            }
        }
    }
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

        @Override
        public void addView(View child, int index, ViewGroup.LayoutParams params) {
            super.addView(child, index, params);
            if (child != this.previewChild) {
                this.displayedScreenKey = MainActivity.this.screen;
            }
        }

        @Override
        public void removeAllViews() {
            MainActivity.this.captureShellSnapshot(this.displayedScreenKey, currentShellChild());
            MainActivity.this.captureFullScreenSnapshot(this.displayedScreenKey);
            super.removeAllViews();
            this.previewChild = null;
        }

        @Override
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

        @Override
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
            return !MainActivity.this.shellAnimating && !MainActivity.this.pageTransitions.isRunning()
                    && !"detail".equals(MainActivity.this.screen)
                    && !"login".equals(MainActivity.this.screen);
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
            MainActivity.this.ensurePageBackdrop(this.dragChild);
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
            this.previewChild = MainActivity.this.realShellPreview(targetKey);
            if (this.previewChild == null && this.gestureMode == MODE_BACK
                    && "settings_home".equals(targetKey)) {
                this.previewChild = MainActivity.this.buildSettingsHomeContent();
                MainActivity.this.retainedPages.put(targetKey, this.previewChild);
            }
            if (this.previewChild == null) {
                ImageView image = new ImageView(getContext());
                image.setBackgroundColor(MainActivity.this.themeTokens == null ? MainActivity.this.PANEL : MainActivity.this.themeTokens.panel);
                image.setScaleType(ImageView.ScaleType.FIT_XY);
                Bitmap bitmap = MainActivity.this.screenSnapshot(targetKey);
                if (bitmap != null && !bitmap.isRecycled()) {
                    image.setImageBitmap(bitmap);
                }
                this.previewChild = image;
            }
            MainActivity.this.ensurePageBackdrop(this.previewChild);
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
            if (Motions.off()) {
                this.dragChild.setTranslationX(targetX);
                if (this.previewChild != null) {
                    this.previewChild.setTranslationX((this.gestureDirection
                            * Math.max(MODE_TOP_LEVEL, getWidth())) + targetX);
                }
                if (end != null) end.run();
                return;
            }
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
            this.swipeAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
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
            if (this.previewChild != null && !(this.previewChild instanceof ImageView)) {
                completeRealShellSwipe(targetKey);
                return;
            }
            ImageView rebuildGuard = mode == MODE_BACK ? MainActivity.this.installFullScreenTransitionOverlay(MainActivity.this.fullScreenSnapshot(targetKey)) : null;
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

        private void completeRealShellSwipe(String targetKey) {
            View oldChild = this.dragChild;
            View target = this.previewChild;
            if (target == null) {
                resetShellDrag();
                return;
            }
            Motions.resetTree(target);
            if (oldChild != null && oldChild.getParent() == this) {
                super.removeView(oldChild);
            }
            MainActivity.this.configureAdoptedShellScreen(targetKey);
            this.displayedScreenKey = targetKey;
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

        void cancelMotion() {
            this.tracking = false;
            this.dragging = false;
            resetShellDrag();
        }

        @Override
        public boolean performClick() {
            return super.performClick();
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
        this.qrLoginController.start(new QrLoginController.Listener() {
            @Override
            public void onQrReady(String url) {
                try {
                    int size = Math.round(MainActivity.this.getResources().getDisplayMetrics().widthPixels * 0.5f);
                    ImageView image = (ImageView) MainActivity.this.content.findViewWithTag("qr_image");
                    if (image != null) {
                        image.setImageBitmap(QrCode.create(url, size));
                    }
                    MainActivity.this.setQrStatus("等待扫码", MainActivity.this.SECONDARY);
                } catch (Exception e) {
                    MainActivity.this.setQrStatus("二维码生成失败", MainActivity.this.PRIMARY);
                    MainActivity.this.stopQrPolling();
                }
            }

            @Override
            public void onStatus(String status) {
                int color = status.startsWith("网络") ? MainActivity.this.MUTED
                        : "等待扫码".equals(status) ? MainActivity.this.SECONDARY : MainActivity.this.PRIMARY;
                MainActivity.this.setQrStatus(status, color);
            }

            @Override
            public void onLogin(JSONObject result) {
                MainActivity.this.session.saveLogin(result);
                MainActivity.this.feed.clear();
                MainActivity.this.toast("登录成功");
                EmojiStore.load(MainActivity.this.api, () -> {
                });
                MainActivity.this.showFeed();
                PresenceReporter.pingNow(MainActivity.this.session,
                        MainActivity.this.readingTimeTracker,
                        MainActivity.this::applyAccessStatus);
                RemoteConfig.load(MainActivity.this.session.userId(), () ->
                        MainActivity.this.applyAccessStatus(RemoteConfig.accessStatus()));
            }

            @Override
            public void onError(String message) {
                MainActivity.this.setQrStatus("获取失败：" + message, MainActivity.this.PRIMARY);
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
        if (this.qrLoginController != null) this.qrLoginController.stop();
    }

    private void showFeed() {
        // 底部导航从“我的”切回社区：feed 在左侧，双页整体向右平移
        if ("profile".equals(this.screen)) {
            this.pendingBackTransition = true;
            this.pendingLateralPush = true;
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
            List<FeedItem> cached = FeedCollection.filter(this.localCache.feedItems(),
                    this.session.blockKeywordList());
            if (!cached.isEmpty()) {
                this.feed.addAll(cached);
                this.feedOffset = this.feed.size();
                this.localCache.log("feed restored from offline cache: " + this.feed.size());
            }
        }
        PullRefreshListView list = new PullRefreshListView(this, this.BG, this.MUTED,
                this.SECONDARY, this.session.uiScale() / 100.0f,
                this.session.textScale() / 100.0f);
        this.feedListView = list;
        this.cachedFeedListView = list;
        list.setBackgroundColor(this.BG);
        list.setDivider(new ColorDrawable(0));
        list.setDividerHeight(dp(2));
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
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
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view2, int state) {
            }

            @Override
            public void onScroll(AbsListView view2, int first, int visible, int total) {
                // 持续记录首个可见位置：无论点击还是手势滑动离开 feed，回来都能精确还原、不闪位
                if (visible > 0) {
                    MainActivity.this.feedFirstVisible = Math.max(0, first);
                    View firstChild = view2.getChildAt(0);
                    MainActivity.this.feedFirstTop = firstChild == null ? 0 : firstChild.getTop();
                }
                if (total > 0 && first + visible >= total - 2 && !MainActivity.this.feedNoMore && !MainActivity.this.feedLoadMoreFailed) {
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
        wrap.setPadding(dp(7), dp(5), dp(7), dp(4));
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(16);
        heading.setPadding(dp(4), 0, dp(4), 0);
        TextView name = text("社区", 21.0f, this.TEXT);
        name.setTypeface(appRegularTypeface(), Typeface.BOLD);
        heading.addView(name, new LinearLayout.LayoutParams(0, dp(38), 1.0f));
        TextView time = text(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()), 10.5f, this.MUTED);
        time.setGravity(21);
        heading.addView(time, new LinearLayout.LayoutParams(dp(52), dp(38)));
        wrap.addView(heading);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(16);
        row.setPadding(dp(8), 0, dp(5), 0);
        ThemeTokens tokens = this.themeTokens == null ? ThemeTokens.of(this.session.darkMode(), this.PRIMARY, this.SECONDARY) : this.themeTokens;
        Compat.setBackground(row, roundStroke(tokens.panelElevated, 21, tokens.glassStroke, 1));
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

    private void updateReadingTimeEntry() {
        if (this.readingTodayView == null || this.readingTimeTracker == null) return;
        this.readingTodayView.setText(readingEntrySummary());
    }

    private String readingEntrySummary() {
        ReadingTimeTracker.Stats stats = this.readingTimeTracker.stats();
        return "今天 " + Format.readingDuration(stats.todayMs()) + " · " + stats.todayCount + " 篇";
    }

    private void showReadingStats() {
        this.screen = "reading_stats";
        setBottomNavVisible(false);
        this.leading.setVisibility(0);
        this.leading.setOnClickListener(view -> {
            this.pendingBackTransition = true;
            showReadingCenter();
        });
        this.title.setText("阅读时长");
        this.action.setVisibility(4);

        ReadingTimeTracker.Stats stats = this.readingTimeTracker.stats();
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(this.BG);
        LinearLayout page = vertical(this.BG);
        page.setPadding(dp(8), dp(8), dp(8), dp(18));
        scroll.addView(page);
        page.addView(settingsTopCard("阅读时长"));
        addSectionLabel(page, "今日");
        page.addView(readingHeroCard(stats));
        addSectionLabel(page, "近 7 天");
        page.addView(readingWeekCard(stats));
        addSectionLabel(page, "构成");
        page.addView(readingSplitCard(stats));
        addSectionLabel(page, "常看社区");
        page.addView(readingTopicsCard(stats));
        this.retainedPages.put("reading_stats", scroll);
        transitionTo(scroll);
    }

    /** 今日卡：大数字时长 + 日期与篇数。 */
    private View readingHeroCard(ReadingTimeTracker.Stats stats) {
        LinearLayout panel = card();
        panel.addView(readingDurationView(stats.todayMs(), 30.0f, 13.0f));
        TextView meta = text(new SimpleDateFormat("M 月 d 日", Locale.getDefault()).format(new Date())
                + " · 看过 " + stats.todayCount + " 篇", 10.5f, this.MUTED);
        addTop(panel, meta, 4);
        return panel;
    }

    /** 近 7 天柱状图：今天主题色，其余石墨；下附本周合计与日均。 */
    private View readingWeekCard(ReadingTimeTracker.Stats stats) {
        LinearLayout panel = card();
        LinearLayout chart = new LinearLayout(this);
        long max = 1L;
        for (long value : stats.weekMs) max = Math.max(max, value);
        int chartHeight = dp(52);
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -6);
        String[] names = {"日", "一", "二", "三", "四", "五", "六"};
        for (int i = 0; i < 7; i++) {
            boolean today = i == 6;
            LinearLayout cell = vertical(0);
            cell.setGravity(1);
            LinearLayout barBox = new LinearLayout(this);
            barBox.setGravity(81);
            View bar = new View(this);
            Compat.setBackground(bar, round(today ? this.themeTokens.accent
                    : blend(this.PANEL, this.TEXT, this.session.darkMode() ? 0.10f : 0.08f), 3));
            int height = (int) Math.max(dp(3), stats.weekMs[i] * chartHeight / max);
            barBox.addView(bar, new LinearLayout.LayoutParams(dp(10), height));
            cell.addView(barBox, new LinearLayout.LayoutParams(-1, chartHeight));
            String dayName = today ? "今天" : names[calendar.get(Calendar.DAY_OF_WEEK) - 1];
            TextView label = text(dayName, 8.5f, today ? this.themeTokens.accent : this.themeTokens.subtle);
            label.setGravity(17);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
            labelParams.topMargin = dp(4);
            cell.addView(label, labelParams);
            chart.addView(cell, new LinearLayout.LayoutParams(0, -2, 1.0f));
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        addTop(panel, chart, 4);
        long weekTotal = stats.weekTotalMs();
        TextView meta = text("本周 " + Format.readingDuration(weekTotal)
                + " · 日均 " + Format.readingDuration(weekTotal / 7L), 10.5f, this.MUTED);
        addTop(panel, meta, 9);
        return panel;
    }

    /** 构成卡：累计时长 + 文章/帖子占比条与图例。 */
    private View readingSplitCard(ReadingTimeTracker.Stats stats) {
        LinearLayout panel = card();
        panel.addView(readingDurationView(stats.totalMs(), 20.0f, 11.0f));
        TextView meta = text("累计 · 看过 " + stats.totalCount + " 篇", 10.5f, this.MUTED);
        addTop(panel, meta, 2);
        long total = stats.totalMs();
        long articleMs = stats.totalArticleMs;
        long postMs = stats.totalPostMs;
        int postColor = blend(this.PANEL, this.TEXT, this.session.darkMode() ? 0.24f : 0.20f);
        LinearLayout bar = new LinearLayout(this);
        Compat.setBackground(bar, round(blend(this.PANEL, this.TEXT, 0.06f), 3));
        if (total > 0L) {
            if (articleMs > 0L) {
                View article = new View(this);
                Compat.setBackground(article, round(this.themeTokens.accent, 3));
                bar.addView(article, new LinearLayout.LayoutParams(0, -1, (float) articleMs));
            }
            if (postMs > 0L) {
                View post = new View(this);
                Compat.setBackground(post, round(postColor, 3));
                bar.addView(post, new LinearLayout.LayoutParams(0, -1, (float) postMs));
            }
        }
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, dp(6));
        barParams.topMargin = dp(10);
        panel.addView(bar, barParams);
        panel.addView(readingLegendRow("文章", this.themeTokens.accent, articleMs, total));
        panel.addView(readingLegendRow("帖子", postColor, postMs, total));
        return panel;
    }

    private View readingLegendRow(String label, int color, long ms, long total) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(16);
        View dot = new View(this);
        Compat.setBackground(dot, round(color, 4));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotParams.rightMargin = dp(7);
        row.addView(dot, dotParams);
        TextView name = text(label, 11.5f, this.TEXT);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1.0f));
        int percent = total == 0L ? 0 : (int) Math.round((ms * 100.0d) / total);
        TextView value = text(percent + "% · " + Format.readingDuration(ms), 10.5f, this.MUTED);
        row.addView(value, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(30));
        row.setLayoutParams(rowParams);
        return row;
    }

    /** 常看社区：按话题累计的时长排行，比例条用主题色。 */
    private View readingTopicsCard(ReadingTimeTracker.Stats stats) {
        LinearLayout panel = card();
        if (stats.topics.isEmpty()) {
            TextView empty = text("多看几篇帖子，这里会按社区统计你的阅读分布", 10.5f, this.MUTED);
            empty.setLineSpacing(0.0f, 1.3f);
            empty.setPadding(0, dp(6), 0, dp(6));
            panel.addView(empty);
            return panel;
        }
        long max = Math.max(1L, stats.topics.get(0).ms);
        int shown = Math.min(5, stats.topics.size());
        for (int i = 0; i < shown; i++) {
            ReadingTimeTracker.TopicStat topic = stats.topics.get(i);
            LinearLayout item = vertical(0);
            LinearLayout head = new LinearLayout(this);
            head.setGravity(16);
            TextView name = text(topic.name, 11.5f, this.TEXT);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            head.addView(name, new LinearLayout.LayoutParams(0, -2, 1.0f));
            TextView value = text(Format.readingDuration(topic.ms), 10.5f, this.MUTED);
            head.addView(value, new LinearLayout.LayoutParams(-2, -2));
            item.addView(head, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout track = new LinearLayout(this);
            Compat.setBackground(track, round(blend(this.PANEL, this.TEXT, 0.06f), 2));
            View fill = new View(this);
            Compat.setBackground(fill, round(this.themeTokens.accent, 2));
            track.addView(fill, new LinearLayout.LayoutParams(0, -1, (float) topic.ms));
            track.addView(new View(this), new LinearLayout.LayoutParams(0, -1, (float) (max - topic.ms)));
            LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(-1, dp(3));
            trackParams.topMargin = dp(6);
            item.addView(track, trackParams);
            addTop(panel, item, i == 0 ? 2 : 12);
        }
        return panel;
    }

    /** 大数字时长：数值粗体大号、单位小号次要色。 */
    private TextView readingDurationView(long milliseconds, float numberSp, float unitSp) {
        android.text.SpannableStringBuilder builder = new android.text.SpannableStringBuilder();
        long minutes = Math.max(0L, milliseconds / 60_000L);
        long hours = minutes / 60L;
        long remainder = minutes % 60L;
        if (milliseconds > 0L && minutes == 0L) {
            appendReadingSegment(builder, "不足 ", unitSp, this.MUTED, false);
            appendReadingSegment(builder, "1", numberSp, this.TEXT, true);
            appendReadingSegment(builder, " 分钟", unitSp, this.MUTED, false);
        } else if (hours > 0L) {
            appendReadingSegment(builder, String.valueOf(hours), numberSp, this.TEXT, true);
            appendReadingSegment(builder, " 时 ", unitSp, this.MUTED, false);
            appendReadingSegment(builder, String.valueOf(remainder), numberSp, this.TEXT, true);
            appendReadingSegment(builder, " 分", unitSp, this.MUTED, false);
        } else {
            appendReadingSegment(builder, String.valueOf(minutes), numberSp, this.TEXT, true);
            appendReadingSegment(builder, " 分钟", unitSp, this.MUTED, false);
        }
        TextView view = new TextView(this);
        view.setText(builder);
        view.setTypeface(appRegularTypeface());
        Compat.setLetterSpacing(view, 0.0f);
        return view;
    }

    private void appendReadingSegment(android.text.SpannableStringBuilder builder,
                                      String value, float sizeSp, int color, boolean bold) {
        int start = builder.length();
        builder.append(value);
        int end = builder.length();
        builder.setSpan(new android.text.style.AbsoluteSizeSpan(Math.round(sp(sizeSp)), true),
                start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new android.text.style.ForegroundColorSpan(color),
                start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (bold) {
            builder.setSpan(new android.text.style.StyleSpan(Typeface.BOLD),
                    start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
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
        this.api.get(EndpointProvider.feeds(), params, new ApiClient.Callback() {
            @Override
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
                                if (!FeedCollection.isBlocked(parsed, MainActivity.this.session.blockKeywordList())) {
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
                        FeedCollection.appendUnique(MainActivity.this.feed, fresh);
                        MainActivity.this.localCache.saveFeed(MainActivity.this.feed);
                    }
                    if (!reset && returned > 0 && fresh.isEmpty()) {
                        MainActivity.this.toast("本页内容已被关键词过滤");
                    }
                    if (returned > 0) {
                        MainActivity.this.feedOffset += Math.max(returned, 30);
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

            @Override
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
                        List<FeedItem> cached = FeedCollection.filter(MainActivity.this.localCache.feedItems(),
                                MainActivity.this.session.blockKeywordList());
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
        submitParams.leftMargin = dp(5);
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
        if (restoreResults && this.searchState.hasResults()) {
            input.setText(this.searchState.keyword());
            input.setSelection(input.length());
            recent.setVisibility(8);
            this.searchState.invalidateRequests();
            renderSearchResultList(results, searchBar, "");
            if (this.searchListView != null && this.searchState.listPosition() > 0) {
                this.searchListView.setSelectionFromTop(this.searchState.listPosition(),
                        this.searchState.listTopOffset());
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
        header.addView(label, new LinearLayout.LayoutParams(0, dp(30), 1.0f));
        TextView clear = text("清空", 10.0f, this.SECONDARY);
        clear.setGravity(17);
        clear.setOnClickListener(view -> {
            this.session.clearSearchHistory();
            parent.removeAllViews();
        });
        header.addView(clear, new LinearLayout.LayoutParams(dp(48), dp(30)));
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

    private void performSearch(String keyword, final FrameLayout results, final View searchBar) {
        results.setTag(searchBar);
        results.removeAllViews();
        setSearchBarVisible(searchBar, true);
        LoadingSpinnerView progress = new LoadingSpinnerView(this);
        progress.setColor(this.PRIMARY);
        results.addView(progress, new FrameLayout.LayoutParams(dp(38), dp(38), 17));
        final int session = this.searchState.begin(keyword);
        this.api.get(EndpointProvider.search(), searchParams(keyword, 0), new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                if ("search".equals(MainActivity.this.screen) && MainActivity.this.searchState.isCurrent(session)) {
                    List<FeedItem> items = FeedCollection.parse(body);
                    List<FeedItem> items2 = FeedCollection.filter(items,
                            MainActivity.this.session.blockKeywordList());
                    MainActivity.this.searchState.replace(items2, items.size());
                    MainActivity.this.renderSearchResultList(results, searchBar, "没有找到相关帖子");
                }
            }

            @Override
            public void onError(String message) {
                if ("search".equals(MainActivity.this.screen) && MainActivity.this.searchState.isCurrent(session)) {
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
        if (this.searchState.items().isEmpty()) {
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
        list.setDividerHeight(dp(2));
        // 搜索栏常驻在顶部，列表内容从其下方开始，滚动不再隐藏搜索栏（避免“闪一下像刷新”）
        list.setPadding(0, dp(54), 0, dp(4));
        list.setClipToPadding(false);
        final TextView footer = text(this.searchState.endReached() ? "没有更多了" : "上滑加载更多", 11.5f, this.MUTED);
        footer.setGravity(17);
        footer.setPadding(0, dp(10), 0, dp(12));
        list.addFooterView(footer, null, false);
        final FeedAdapter adapter = new FeedAdapter(this, this.searchState.items(), this.session.noImage(),
                this.session.uiScale() / 100.0f, this.session.textScale() / 100.0f, this.session.darkMode(),
                this.PRIMARY, this.SECONDARY, this::showDetail, this::toggleFeedLike);
        list.setAdapter((ListAdapter) adapter);
        footer.setOnClickListener(view -> {
            loadMoreSearchResults(adapter, footer);
        });
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (totalItemCount > 0 && firstVisibleItem + visibleItemCount >= totalItemCount - 2) {
                    MainActivity.this.loadMoreSearchResults(adapter, footer);
                }
            }
        });
        parent.addView(list, match());
    }

    private void loadMoreSearchResults(final FeedAdapter adapter, final TextView footer) {
        if (!this.searchState.beginLoadMore()) return;
        footer.setText("正在加载更多…");
        final int session = this.searchState.generation();
        this.api.get(EndpointProvider.search(), searchParams(this.searchState.keyword(), this.searchState.offset()), new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                if (!MainActivity.this.searchState.isCurrent(session)) {
                    return;
                }
                List<FeedItem> items = FeedCollection.parse(body);
                int added = MainActivity.this.searchState.appendPage(
                        FeedCollection.filter(items, MainActivity.this.session.blockKeywordList()), items.size());
                if (added > 0) {
                    footer.setText("上滑加载更多");
                    adapter.notifyDataSetChanged();
                    return;
                }
                footer.setText(MainActivity.this.searchState.endReached() ? "没有更多了" : "上滑加载更多");
            }

            @Override
            public void onError(String message) {
                if (!MainActivity.this.searchState.isCurrent(session)) {
                    return;
                }
                MainActivity.this.searchState.failLoadMore();
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
            if (Motions.off()) {
                searchBar.setVisibility(visible ? 0 : 8);
                searchBar.setEnabled(visible);
                setChildrenEnabled(searchBar, visible);
                searchBar.setAlpha(visible ? 1.0f : 0.0f);
                searchBar.setTranslationY(0.0f);
                return;
            }
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
        this.pageTransitions.finishNow();
        saveCurrentDetailProgress();
        stopQrPolling();
        ensureEmojiCatalog(() -> {
        });
        if ("feed".equals(this.screen)) {
            saveFeedScroll();
        }
        if ("search".equals(this.screen) && this.searchListView != null) {
            View firstChild = this.searchListView.getChildCount() > 0 ? this.searchListView.getChildAt(0) : null;
            // setSelectionFromTop 的 y 不含 paddingTop，这里要减掉，否则恢复时会往下偏一个搜索栏
            this.searchState.saveListPosition(this.searchListView.getFirstVisiblePosition(),
                    firstChild == null ? 0 : firstChild.getTop() - this.searchListView.getPaddingTop());
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
        this.localCache.rememberRecent(item);
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
        transitionTo(detailLoadingPage());
        final int requestToken = this.detailRequestToken + 1;
        this.detailRequestToken = requestToken;
        if (!isNetworkConnected()) {
            hideLoading();
            handleDetailFailure(item, "当前无网络");
            return;
        }
        this.api.get(EndpointProvider.linkTree(), detailParams(item), new ApiClient.Callback() {
            @Override
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

            @Override
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
            this.api.get(EndpointProvider.linkTreeV2(), detailParams(item), new ApiClient.Callback() {
                @Override
                public void onSuccess(JSONObject body) {
                    if (MainActivity.this.isCurrentDetailRequest(item, requestToken)) {
                        MainActivity.this.hideLoading();
                        String blocked = MainActivity.this.detailBlockedMessage(body);
                        if (!blocked.isEmpty()) {
                            MainActivity.this.handleDetailFailure(item, blocked);
                        } else if (!MainActivity.this.hasDetailLink(body)) {
                            MainActivity.this.handleDetailFailure(item, Json.first(fallbackMessage, "详情数据为空"));
                        } else {
                            MainActivity.this.cacheDetailAndRender(item, body);
                        }
                    }
                }

                @Override
                public void onError(String message) {
                    if (MainActivity.this.isCurrentDetailRequest(item, requestToken)) {
                        MainActivity.this.hideLoading();
                        MainActivity.this.handleDetailFailure(item, Json.first(message, fallbackMessage));
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
        String message = Json.first(object.optString("msg"), object.optString("message"));
        return (isVerificationStatus(status) || isVerificationStatus(code)) ? Json.first(message, status, code, "需要完成验证后才能继续") : isVerificationText(message) ? message : "";
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
        strArr[2] = this.currentLinkHsrc;
        this.currentLinkHsrc = Json.first(strArr);
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
        DetailPager pager = new DetailPager(this, this::dp, new DetailPager.Listener() {
            @Override
            public boolean canSwipeBack() {
                return MainActivity.this.canDetailSwipeBack();
            }

            @Override
            public void onPageChanged() {
                MainActivity.this.updateDetailPagerTitle();
            }

            @Override
            public void onReturn() {
                MainActivity.this.returnFromDetailGesture();
            }
        });
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
        headline.setLineSpacing(dp(2), 1.08f);
        article.addView(headline);
        addAuthorHeader(article, link, user, author);
        if (this.readingTimeTracker != null) {
            this.readingTimeTracker.tagTopic(firstTopicName(link));
        }
        if (fallback.article) {
            addTopicChips(article, link);
        }
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
        JSONArray comments = result == null ? null : result.optJSONArray("comments");
        this.lastDetailDiagnostics = buildDetailDiagnostics(
                body, fallback, link, fallbackImages, comments);
        this.localCache.log("detail diagnostics captured link=" + (fallback == null ? "" : fallback.id) + " title=" + compactLogText(heading, 48));
        addRichContent(article, link, fallback.description, fallbackImages);
        if (!fallback.article) {
            addTopicChips(article, link);
        }
        addDetailActions(article, fallback, link);
        page.addView(article);
        addDetailCommentSection(page, comments);
        ScrollView commentScroll = new ScrollView(this);
        commentScroll.setBackgroundColor(this.BG);
        LinearLayout commentPage = vertical(this.BG);
        commentPage.setPadding(pagePadding, dp(8), pagePadding, dp(18));
        commentScroll.addView(commentPage);
        addDetailCommentSection(commentPage, comments);
        pager.setPages(detailReturnPreview(), articleScroll, commentScroll);
        pager.setReturnView(this.detailReturnView);
        transitionTo(pager);
        if (this.activityResumed && this.readingTimeTracker != null && fallback != null) {
            this.readingTimeTracker.start(fallback.article, fallback.id);
        }
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
        LinearLayout surface = vertical(0);
        surface.setPadding(0, dp(8), 0, dp(12));
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(16);
        TextView commentTitle = text("评论 " + (commentArray == null ? 0 : commentArray.length()), 14.0f, this.TEXT);
        commentTitle.setTypeface(appRegularTypeface(), 1);
        heading.addView(commentTitle, new LinearLayout.LayoutParams(0, dp(32), 1.0f));
        TextView sort = text("热门", 11.0f, this.MUTED);
        sort.setGravity(17);
        setLeftIcon(sort, R.drawable.ic_sort, this.MUTED, 11);
        heading.addView(sort, new LinearLayout.LayoutParams(dp(60), dp(32)));
        surface.addView(heading);
        LinearLayout comments = vertical(0);
        comments.setBackgroundColor(0);
        surface.addView(comments);
        final boolean[] latest = {false};
        renderCommentList(comments, commentArray, latest[0]);
        // 点一下直接在热门/最新之间切换，不弹选择菜单
        sort.setOnClickListener(view -> {
            latest[0] = !latest[0];
            sort.setText(latest[0] ? "最新" : "热门");
            setLeftIcon(sort, R.drawable.ic_sort, this.MUTED, 11);
            UiComponents.press(sort);
            renderCommentList(comments, commentArray, latest[0]);
        });
        LinearLayout.LayoutParams surfaceParams = new LinearLayout.LayoutParams(-1, -2);
        surfaceParams.topMargin = dp(10);
        page.addView(surface, surfaceParams);
    }

    private void renderCommentList(LinearLayout comments, JSONArray source, boolean latest) {
        comments.removeAllViews();
        int count = addComments(comments, source, latest);
        if (count == 0) {
            TextView empty = text("暂无评论", 13.0f, this.MUTED);
            empty.setPadding(dp(4), dp(8), dp(4), dp(12));
            comments.addView(empty);
        }
    }
    private View detailReturnPreview() {
        ImageView snapshot = new ImageView(this);
        snapshot.setBackgroundColor(this.themeTokens == null ? this.PANEL : this.themeTokens.panel);
        snapshot.setScaleType(ImageView.ScaleType.FIT_XY);
        Bitmap bitmap = screenSnapshot(backTargetScreenKey());
        if (bitmap != null && !bitmap.isRecycled()) snapshot.setImageBitmap(bitmap);
        return snapshot;
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
        CommentLikeControl like = detailCountAction(R.drawable.official_comment_like_line,
                Math.max(0, item.likes));
        LikeState state = linkLikeState(link, item);
        boolean liked = state.liked;
        int likes = state.likes;
        item.liked = liked;
        item.likes = likes;
        updateFeedLike(item.id, liked, likes);
        updateLinkLikeView(like, liked, likes);
        like.root.setOnClickListener(view -> {
            toggleLinkLike(item, like);
        });
        addDetailAction(row, like.root);
        ImageView favorite = detailIconAction();
        boolean favored = linkFavored(link);
        updateFavoriteView(favorite, favored);
        addDetailAction(row, favorite);
        favorite.setOnClickListener(view2 -> {
            toggleFavorite(item, favorite);
        });
        TextView watchLater = actionPill("", R.drawable.ic_history);
        updateWatchLaterView(watchLater, this.localCache.isWatchLater(item.id), false);
        watchLater.setOnClickListener(view3 -> toggleWatchLater(item, watchLater));
        addDetailAction(row, watchLater);
        CommentLikeControl comment = detailCountAction(R.drawable.official_detail_comment,
                Math.max(0, item.comments));
        comment.root.setContentDescription("评论");
        addDetailAction(row, comment.root);
        comment.root.setOnClickListener(view4 -> {
            showCommentDialog(null);
        });
        addTop(article, row, 10);
    }

    private void addDetailAction(LinearLayout dock, View item) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1.0f);
        params.leftMargin = dp(2);
        params.rightMargin = dp(2);
        dock.addView(item, params);
    }

    private CommentLikeControl detailCountAction(int iconRes, int count) {
        CommentLikeControl control = commentLikeControl();
        control.icon.setImageResource(iconRes);
        control.icon.setColorFilter(this.MUTED);
        control.count.setText(Format.commentLikeCount(count));
        control.count.setTextColor(this.MUTED);
        Compat.setBackground(control.root, roundStroke(this.PANEL, 8,
                this.themeTokens.hairline, 1));
        return control;
    }

    private ImageView detailIconAction() {
        ImageView view = new ImageView(this);
        view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        view.setPadding(dp(11), dp(11), dp(11), dp(11));
        Compat.setBackground(view, roundStroke(this.PANEL, 8,
                this.themeTokens.hairline, 1));
        return view;
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
        view.setText(loading ? "缓存中" : saved ? "已缓存" : "缓存");
        view.setContentDescription(saved ? "移出稍后看" : "稍后看");
        updateDockItem(view, saved || loading, R.drawable.ic_history);
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
        TextView view = text(label, 10.5f, this.MUTED);
        view.setGravity(17);
        view.setTypeface(appRegularTypeface(), 1);
        view.setPadding(dp(4), 0, dp(4), 0);
        updateDockItem(view, false, icon);
        return view;
    }

    private void updateLinkLikeView(CommentLikeControl view, boolean liked, int likes) {
        int color = liked ? this.themeTokens.accent : this.MUTED;
        view.icon.setImageResource(liked ? R.drawable.official_comment_like_filled
                : R.drawable.official_comment_like_line);
        view.icon.setColorFilter(color);
        view.count.setText(Format.commentLikeCount(Math.max(0, likes)));
        view.count.setTextColor(color);
        view.root.setContentDescription(liked ? "取消点赞" : "点赞");
        Compat.setBackground(view.root, roundStroke(liked ? this.themeTokens.softAccent() : this.PANEL,
                8, this.themeTokens.hairline, 1));
    }

    private void updateFavoriteView(ImageView view, boolean favored) {
        view.setTag(Boolean.valueOf(favored));
        view.setContentDescription(favored ? "取消收藏" : "收藏");
        view.setImageResource(favored ? R.drawable.official_favorite_filled
                : R.drawable.official_favorite_line);
        view.setColorFilter(favored ? this.themeTokens.accent : this.MUTED);
        Compat.setBackground(view, roundStroke(favored ? this.themeTokens.softAccent() : this.PANEL,
                8, this.themeTokens.hairline, 1));
    }

    private void updateDockItem(TextView view, boolean active, int icon) {
        int color = active ? this.themeTokens.accent : this.MUTED;
        view.setTextColor(color);
        Compat.setBackground(view, roundStroke(active ? this.themeTokens.softAccent() : this.PANEL,
                8, this.themeTokens.hairline, 1));
        setLeftIcon(view, icon, color, 15);
    }

    private boolean linkLiked(JSONObject link, FeedItem fallback) {
        LikeState override = linkLikeOverride(link, fallback);
        if (override != null) {
            return override.liked;
        }
        if (link == null) {
            return fallback != null && fallback.liked;
        }
        if (Json.truthy(link, "is_award_link", "is_award", "liked", "is_liked", "has_award")) {
            return true;
        }
        return Json.truthy(link, "award_state", "like_state");
    }

    private int linkLikes(JSONObject link, FeedItem fallback) {
        LikeState override = linkLikeOverride(link, fallback);
        if (override != null) {
            return override.likes;
        }
        int fallbackLikes = fallback == null ? 0 : fallback.likes;
        return link == null ? fallbackLikes : Json.firstInt(link, fallbackLikes, "link_award_num", "like_num", "award_num", "award_count", "like_count", "liked_num", "total_award_num", "up_num", "up");
    }

    private LikeState linkLikeState(JSONObject link, FeedItem fallback) {
        LikeState override = linkLikeOverride(link, fallback);
        return override != null ? override : new LikeState(linkLiked(link, fallback), linkLikes(link, fallback));
    }

    private LikeState linkLikeOverride(JSONObject link, FeedItem fallback) {
        String[] strArr = new String[4];
        strArr[0] = fallback == null ? "" : fallback.id;
        strArr[1] = link == null ? "" : link.optString("linkid");
        strArr[2] = link == null ? "" : link.optString("link_id");
        strArr[3] = this.currentLinkId;
        String id = Json.first(strArr);
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
        return Json.truthy(link, "is_favour", "is_favor", "is_fav", "favored", "has_favour", "has_favor");
    }

    private String hsrc(JSONObject link) {
        if (link == null) {
            return "";
        }
        String value = Json.first(link.optString("h_src"), link.optString("hsrc"));
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

    private boolean requireLogin(String actionName) {
        if (this.session != null && this.session.isLoggedIn()) {
            return true;
        }
        toast("游客模式可浏览，登录后可" + actionName);
        return false;
    }

    private boolean allowWriteAction(String actionName) {
        String remoteMessage = RemoteConfig.blockedMessage(actionName);
        if (!remoteMessage.isEmpty()) {
            toast(remoteMessage);
            return false;
        }
        String message = this.writeActions.begin(actionName);
        if (message == null) return true;
        toast(message);
        return false;
    }

    private String writeErrorMessage(String actionName, String message) {
        return this.writeActions.errorMessage(actionName, message);
    }

    private void toggleLinkLike(final FeedItem item, final CommentLikeControl view) {
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
        postLinkLike(item, nextLiked, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                MainActivity.this.rememberLinkLike(item.id, nextLiked, nextLikes);
                MainActivity.this.updateFeedLike(item.id, nextLiked, nextLikes);
                MainActivity.this.localCache.log("link like ok " + item.id + ": " + beforeLikes + " -> " + nextLikes);
            }

            @Override
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
        postLinkLike(item, nextLiked, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                MainActivity.this.rememberLinkLike(item.id, nextLiked, nextLikes);
                MainActivity.this.updateFeedLike(item.id, nextLiked, nextLikes);
                MainActivity.this.localCache.log("feed like ok " + item.id + ": " + beforeLikes + " -> " + nextLikes);
            }

            @Override
            public void onError(String message) {
                item.liked = beforeLiked;
                item.likes = beforeLikes;
                MainActivity.this.rememberLinkLike(item.id, beforeLiked, beforeLikes);
                MainActivity.this.updateFeedLike(item.id, beforeLiked, beforeLikes);
                MainActivity.this.toast("点赞失败" + MainActivity.this.writeErrorMessage("点赞", message));
            }
        });
    }

    private void toggleFavorite(FeedItem item, final ImageView view) {
        if (!requireLogin("收藏") || !allowWriteAction("收藏") || item == null || item.id.isEmpty()) {
            return;
        }
        Object tag = view.getTag();
        final boolean favored = (tag instanceof Boolean) && ((Boolean) tag).booleanValue();
        final boolean nextFavored = !favored;
        updateFavoriteView(view, nextFavored);
        view.setEnabled(false);
        postFavorite(item, nextFavored, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                view.setEnabled(true);
                MainActivity.this.toast(nextFavored ? "已收藏" : "已取消收藏");
            }

            @Override
            public void onError(String message) {
                view.setEnabled(true);
                MainActivity.this.updateFavoriteView(view, favored);
                MainActivity.this.toast("收藏操作失败" + MainActivity.this.writeErrorMessage("收藏", message));
            }
        });
    }

    private void postLinkLike(FeedItem item, boolean nextLiked, ApiClient.Callback callback) {
        this.writeActions.like(item.id, hsrcFor(item), nextLiked, callback);
    }

    private void postFavorite(FeedItem item, boolean nextFavored, ApiClient.Callback callback) {
        this.writeActions.favorite(item.id, hsrcFor(item), nextFavored, callback);
    }

    private String hsrcFor(FeedItem item) {
        String value = item == null ? "" : item.hsrc;
        if (value.isEmpty() && item != null && item.id.equals(this.currentLinkId)) {
            value = this.currentLinkHsrc;
        }
        if (value.isEmpty() && item == null) {
            value = this.currentLinkHsrc;
        }
        return value;
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
        String nameValue = user == null ? fallbackAuthor : Json.first(user.optString("username"), user.optString("nickname"), user.optString("name"), fallbackAuthor);
        TextView name = text(nameValue.isEmpty() ? "小黑盒用户" : nameValue, 13.0f, this.TEXT);
        name.setTypeface(appRegularTypeface(), 1);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setMaxWidth(dp(168));
        nameRow.addView(name, new LinearLayout.LayoutParams(-2, -2));
        int level = CommentData.userLevel(user);
        if (level > 0) {
            int levelColor = CommentData.levelBadgeColor(level);
            TextView badge = text("Lv." + level, 8.0f, levelColor);
            badge.setGravity(17);
            badge.setTypeface(appRegularTypeface(), 1);
            Compat.setBackground(badge, round(blend(this.PANEL, levelColor,
                    this.session.darkMode() ? 0.30f : 0.14f), 4));
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(32), dp(15));
            badgeParams.leftMargin = dp(5);
            nameRow.addView(badge, badgeParams);
        }
        linearLayoutVertical.addView(nameRow);
        long createdAt = link == null ? 0L
                : link.optLong("create_at", link.optLong("create_time", link.optLong("createtime")));
        String published = createdAt > 0 ? commentDisplayTime(createdAt) : "";
        String signature = user == null ? "" : Json.first(user.optString("signature"), user.optString("desc"));
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
        Compat.setBackground(follow, round(followBg, 5));
        String targetUserId = authorUserId(link, user);
        View.OnClickListener openUser = view -> showUserSpace(targetUserId, nameValue, avatarUrl);
        avatar.setOnClickListener(openUser);
        linearLayoutVertical.setOnClickListener(openUser);
        updateFollowView(follow, isFollowing(link, user));
        linearLayout.addView(follow, new LinearLayout.LayoutParams(dp(62), dp(30)));
        follow.setOnClickListener(view -> {
            toggleFollow(follow, link, user, targetUserId);
        });
        addTop(article, linearLayout, article.getChildCount() == 0 ? 0 : 14);
    }

    /** 官方同款话题标签：文章放作者行下方，普通帖放正文下方；同时供阅读统计按社区累计。 */
    private void addTopicChips(LinearLayout article, JSONObject link) {
        JSONArray topics = link == null ? null : link.optJSONArray("topics");
        if (topics == null || topics.length() == 0) return;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(16);
        int added = 0;
        for (int i = 0; i < topics.length() && added < 3; i++) {
            JSONObject topic = topics.optJSONObject(i);
            if (topic == null) continue;
            String name = Json.first(topic.optString("name"), topic.optString("title"));
            if (name.isEmpty()) continue;
            LinearLayout chip = new LinearLayout(this);
            chip.setGravity(16);
            chip.setPadding(dp(5), 0, dp(9), 0);
            Compat.setBackground(chip, round(blend(this.BG, this.TEXT,
                    this.session.darkMode() ? 0.07f : 0.05f), 9));
            String icon = Json.first(topic.optString("pic_url"), topic.optString("icon"),
                    topic.optString("img_url"), topic.optString("appicon"));
            if (!this.session.noImage() && !icon.isEmpty()) {
                ImageView pic = new ImageView(this);
                pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Compat.setBackground(pic, round(blend(this.BG, this.TEXT, 0.12f), 4));
                Compat.clipToOutline(pic);
                LinearLayout.LayoutParams picParams = new LinearLayout.LayoutParams(dp(16), dp(16));
                picParams.rightMargin = dp(5);
                chip.addView(pic, picParams);
                ImageLoader.intoPlain(pic, icon, 64);
            } else {
                chip.setPadding(dp(9), 0, dp(9), 0);
            }
            TextView label = text(name, 10.5f, this.TEXT);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setMaxWidth(dp(120));
            chip.addView(label, new LinearLayout.LayoutParams(-2, -2));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(-2, dp(26));
            if (added > 0) chipParams.leftMargin = dp(6);
            row.addView(chip, chipParams);
            added++;
        }
        if (added == 0) return;
        addTop(article, row, 9);
    }

    private String firstTopicName(JSONObject link) {
        JSONArray topics = link == null ? null : link.optJSONArray("topics");
        if (topics == null) return "";
        for (int i = 0; i < topics.length(); i++) {
            JSONObject topic = topics.optJSONObject(i);
            if (topic == null) continue;
            String name = Json.first(topic.optString("name"), topic.optString("title"));
            if (!name.isEmpty()) return name;
        }
        return "";
    }

    private void updateFollowView(TextView follow, boolean following) {
        follow.setTag(Boolean.valueOf(following));
        follow.setText(following ? "已关注" : "+ 关注");
        follow.setTextColor(this.themeTokens.accent);
        GradientDrawable drawable = round(following
                ? this.themeTokens.softAccent() : Color.TRANSPARENT, 15);
        drawable.setStroke(dp(1), this.themeTokens.accent);
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
        } else {
            this.userSpaceReturnItem = null;
            this.userSpaceReturnScreen = this.screen;
        }
        activate("user_space");
        this.title.setText("个人主页");
        this.action.setVisibility(4);
        this.leading.setOnClickListener(view -> {
            returnFromUserSpace();
        });
        this.content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(this.PANEL);
        LinearLayout page = vertical(this.PANEL);
        page.setPadding(0, 0, 0, dp(10));
        scroll.addView(page);
        LinearLayout profile = userSpaceHeader(fallbackName, userId, fallbackAvatar, null);
        page.addView(profile);
        final List<FeedItem> userItems = new ArrayList<>();
        final boolean[] articlesOnly = {false};
        TextView status = text("正在加载动态", 12.0f, this.MUTED);
        status.setGravity(17);
        status.setPadding(dp(16), dp(22), dp(16), dp(22));
        LinearLayout events = vertical(this.PANEL);
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
        profile.setPadding(dp(14), dp(12), dp(14), dp(10));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(16);
        ImageView avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Compat.setBackground(avatar, round(this.session.darkMode()
                ? Color.rgb(46, 48, 52) : Color.rgb(232, 234, 236), 28));
        Compat.clipToOutline(avatar);
        top.addView(avatar, new LinearLayout.LayoutParams(dp(58), dp(58)));
        String resolvedAvatar = Json.first(user == null ? "" : user.optString("avatar", user.optString("avartar")), avatarUrl);
        if (!this.session.noImage() && !resolvedAvatar.isEmpty()) {
            ImageLoader.intoPlain(avatar, resolvedAvatar, 144);
        }
        LinearLayout copy = vertical(0);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        copyParams.leftMargin = dp(11);
        top.addView(copy, copyParams);
        String resolvedName = Json.first(user == null ? "" : user.optString("username", user.optString("nickname", user.optString("name"))), nameValue, "小黑盒用户");
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setGravity(16);
        TextView name = text(resolvedName, 17.0f, this.TEXT);
        name.setTypeface(appRegularTypeface(), 1);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        nameRow.addView(name, new LinearLayout.LayoutParams(0, -2, 1.0f));
        addLevelBadge(nameRow, user, false);
        copy.addView(nameRow);
        TextView id = text("ID " + userId, 10.0f, this.MUTED);
        id.setSingleLine(true);
        addTop(copy, id, 2);
        String signature = user == null ? "" : Json.first(user.optString("signature"), user.optString("desc"));
        if (!signature.isEmpty()) {
            TextView desc = text(signature, 11.0f, this.MUTED);
            desc.setMaxLines(2);
            desc.setEllipsize(TextUtils.TruncateAt.END);
            desc.setLineSpacing(0.0f, 1.12f);
            addTop(copy, desc, 4);
        }
        profile.addView(top);
        JSONObject bbs = user == null ? null : user.optJSONObject("bbs_info");
        LinearLayout stats = new LinearLayout(this);
        stats.setGravity(17);
        addUserStat(stats, firstJsonInt(user, bbs, "follow_num", "following_num", "attention_num"), "关注");
        addUserStat(stats, firstJsonInt(user, bbs, "fan_num", "fans_num", "follower_num"), "粉丝");
        addUserStat(stats, awardAndFavoriteCount(user, bbs), "获赞与收藏");
        addTop(profile, stats, 11);
        View divider = new View(this);
        divider.setBackgroundColor(this.themeTokens == null ? this.MUTED : this.themeTokens.hairline);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.topMargin = dp(10);
        profile.addView(divider, dividerParams);
        return profile;
    }

    private LinearLayout userSpaceTabs(boolean[] articlesOnly, Runnable render) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(12), 0, dp(12), 0);
        row.setBackgroundColor(this.PANEL);
        LinearLayout dynamic = userSpaceTab("动态", !articlesOnly[0]);
        LinearLayout articles = userSpaceTab("投稿", articlesOnly[0]);
        dynamic.setOnClickListener(view -> {
            if (!articlesOnly[0]) return;
            articlesOnly[0] = false;
            updateUserSpaceTabs(row, false);
            render.run();
        });
        articles.setOnClickListener(view -> {
            if (articlesOnly[0]) return;
            articlesOnly[0] = true;
            updateUserSpaceTabs(row, true);
            render.run();
        });
        row.addView(dynamic, new LinearLayout.LayoutParams(0, dp(43), 1.0f));
        row.addView(articles, new LinearLayout.LayoutParams(0, dp(43), 1.0f));
        return row;
    }

    private LinearLayout userSpaceTab(String label, boolean active) {
        LinearLayout tab = vertical(this.PANEL);
        tab.setGravity(17);
        TextView textView = text(label, 13.5f, active ? this.TEXT : this.MUTED);
        textView.setGravity(17);
        textView.setTypeface(appRegularTypeface(), active ? 1 : 0);
        tab.addView(textView, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        View indicator = new View(this);
        Compat.setBackground(indicator, round(this.themeTokens.accent, 1));
        indicator.setVisibility(active ? 0 : 4);
        LinearLayout.LayoutParams indicatorParams = new LinearLayout.LayoutParams(dp(24), dp(2));
        indicatorParams.gravity = 1;
        tab.addView(indicator, indicatorParams);
        return tab;
    }

    private void updateUserSpaceTabs(LinearLayout row, boolean articlesOnly) {
        for (int i = 0; i < row.getChildCount(); i++) {
            LinearLayout tab = (LinearLayout) row.getChildAt(i);
            TextView label = (TextView) tab.getChildAt(0);
            View indicator = tab.getChildAt(1);
            boolean active = articlesOnly ? i == 1 : i == 0;
            label.setTextColor(active ? this.TEXT : this.MUTED);
            label.setTypeface(appRegularTypeface(), active ? 1 : 0);
            indicator.setVisibility(active ? 0 : 4);
        }
    }

    private void addUserStat(LinearLayout row, int value, String label) {
        LinearLayout item = vertical(0);
        item.setGravity(17);
        TextView number = text(Format.commentLikeCount(Math.max(0, value)), 15.5f, this.TEXT);
        number.setGravity(17);
        number.setTypeface(appRegularTypeface(), 1);
        TextView caption = text(label, 9.5f, this.MUTED);
        caption.setGravity(17);
        caption.setSingleLine(true);
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
                status.setVisibility(0);
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
            if (count > 0) {
                View divider = new View(this);
                divider.setBackgroundColor(this.themeTokens.hairline);
                LinearLayout.LayoutParams dividerParams =
                        new LinearLayout.LayoutParams(-1, dp(1));
                dividerParams.leftMargin = dp(14);
                dividerParams.rightMargin = dp(14);
                events.addView(divider, dividerParams);
            }
            events.addView(userEventCard(item));
            count++;
        }
        status.setVisibility(count == 0 ? 0 : 8);
        if (count == 0) status.setText(articlesOnly ? "暂无投稿" : "暂无动态");
        addBottomNavSafeSpace(events);
    }

    private List<FeedItem> parseUserEvents(JSONObject body) {
        JSONObject result = body == null ? null : body.optJSONObject("result");
        JSONArray array = Json.firstArray(result, "links", "list", "events", "moments", "data");
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

    private View userEventCard(FeedItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(48);
        card.setPadding(dp(14), dp(11), dp(14), dp(10));
        card.setBackgroundColor(this.PANEL);
        LinearLayout copy = vertical(0);
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1.0f));

        LinearLayout meta = new LinearLayout(this);
        meta.setGravity(16);
        if (item.pinned) {
            TextView pinned = text("置顶", 9.5f, this.themeTokens.accent);
            pinned.setTypeface(appRegularTypeface(), 1);
            LinearLayout.LayoutParams pinnedParams = new LinearLayout.LayoutParams(-2, -2);
            pinnedParams.rightMargin = dp(7);
            meta.addView(pinned, pinnedParams);
        }
        List<String> metaParts = new ArrayList<>();
        if (item.article) metaParts.add("投稿");
        if (!TextUtils.isEmpty(item.topicName)) metaParts.add(item.topicName);
        if (item.createdAt > 0L) metaParts.add(commentDisplayTime(item.createdAt));
        if (!metaParts.isEmpty()) {
            TextView metaText = text(TextUtils.join(" · ", metaParts), 9.5f, this.MUTED);
            metaText.setSingleLine(true);
            metaText.setEllipsize(TextUtils.TruncateAt.END);
            meta.addView(metaText, new LinearLayout.LayoutParams(0, -2, 1.0f));
        }
        if (meta.getChildCount() > 0) copy.addView(meta);

        String titleText = RichContent.plainText(Json.first(item.title, item.description, "暂无内容"));
        TextView titleView = text(titleText, 14.5f, this.TEXT);
        titleView.setTypeface(appRegularTypeface(), 1);
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setLineSpacing(0.0f, 1.08f);
        EmojiRenderer.set(titleView, titleText, this.session.darkMode());
        addTop(copy, titleView, meta.getChildCount() > 0 ? 5 : 0);
        String description = RichContent.plainText(item.description);
        if (!TextUtils.isEmpty(description) && !description.equals(titleText)) {
            TextView desc = text(description, 11.0f, this.MUTED);
            desc.setMaxLines(2);
            desc.setEllipsize(TextUtils.TruncateAt.END);
            desc.setLineSpacing(0.0f, 1.08f);
            EmojiRenderer.set(desc, description, this.session.darkMode());
            addTop(copy, desc, 4);
        }

        LinearLayout stats = new LinearLayout(this);
        stats.setGravity(16);
        stats.addView(new View(this), new LinearLayout.LayoutParams(0, dp(22), 1.0f));
        stats.addView(userEventStat(R.drawable.official_comment_like_line, item.likes));
        LinearLayout.LayoutParams commentParams = new LinearLayout.LayoutParams(-2, dp(22));
        commentParams.leftMargin = dp(13);
        stats.addView(userEventStat(R.drawable.official_detail_comment, item.comments), commentParams);
        addTop(copy, stats, 6);

        if (!this.session.noImage() && !TextUtils.isEmpty(item.image)) {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            boolean narrow = metrics.widthPixels / Math.max(1.0f, metrics.density) < 300.0f;
            int imageWidth = dp(narrow ? 76 : 94);
            int imageHeight = dp(narrow ? 58 : 68);
            FrameLayout imageFrame = new FrameLayout(this);
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Compat.setBackground(image, round(this.session.darkMode()
                    ? Color.rgb(42, 44, 48) : Color.rgb(232, 234, 236), 5));
            Compat.clipToOutline(image);
            imageFrame.addView(image, new FrameLayout.LayoutParams(-1, -1));
            if (item.images.length > 1) {
                TextView count = text(item.images.length + " 图", 8.5f, Color.WHITE);
                count.setGravity(17);
                count.setPadding(dp(5), 0, dp(5), 0);
                Compat.setBackground(count, round(Color.argb(180, 20, 21, 23), 3));
                FrameLayout.LayoutParams countParams =
                        new FrameLayout.LayoutParams(-2, dp(18), 85);
                countParams.rightMargin = dp(4);
                countParams.bottomMargin = dp(4);
                imageFrame.addView(count, countParams);
            }
            LinearLayout.LayoutParams imageParams =
                    new LinearLayout.LayoutParams(imageWidth, imageHeight);
            imageParams.leftMargin = dp(12);
            card.addView(imageFrame, imageParams);
            ImageLoader.intoPlain(image, item.image, 260);
        }
        card.setOnClickListener(view -> runWithPressFeedback(view, () -> showDetail(item)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        card.setLayoutParams(params);
        return card;
    }

    private TextView userEventStat(int icon, int value) {
        TextView stat = text(Format.commentLikeCount(Math.max(0, value)), 9.5f, this.MUTED);
        stat.setGravity(17);
        Drawable drawable = Compat.tintedDrawable(this, icon, this.MUTED);
        if (drawable != null) {
            drawable.setBounds(0, 0, dp(13), dp(13));
            stat.setCompoundDrawables(drawable, null, null, null);
            stat.setCompoundDrawablePadding(dp(3));
        }
        return stat;
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
            postFollow(targetUserId, next, new ApiClient.Callback() {
                @Override
                public void onSuccess(JSONObject body) {
                    MainActivity.this.applyFollowState(link, user, MainActivity.this.nextFollowStatus(beforeStatus, next));
                    follow.setClickable(true);
                    follow.setAlpha(1.0f);
                    MainActivity.this.updateFollowView(follow, next);
                    MainActivity.this.toast(next ? "已关注" : "已取消关注");
                }

                @Override
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
        this.writeActions.follow(targetUserId, hsrcFor(this.currentDetailItem), next, callback);
    }

    private boolean isFollowing(JSONObject link, JSONObject user) {
        int status = followStatus(link, user);
        return status >= 0 ? status == 1 || status == 3 : Json.truthy(link, "is_follow", "is_following", "followed") || Json.truthy(user, "is_follow", "is_following", "followed");
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
            return beforeStatus == 2 ? 3 : 1;
        }
        if (beforeStatus == 3) {
            return 2;
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
        strArr[2] = link == null ? "" : link.optString(SecureStrings.heyboxId());
        strArr[3] = link == null ? "" : link.optString("heyboxid");
        strArr[4] = link == null ? "" : link.optString("uid");
        strArr[5] = link == null ? "" : link.optString("account_id");
        strArr[6] = link == null ? "" : link.optString("id");
        strArr[7] = userId(user);
        return Json.first(strArr);
    }

    private String userId(JSONObject user) {
        return user == null ? "" : Json.first(user.optString(SecureStrings.userid()), user.optString(SecureStrings.userId()), user.optString(SecureStrings.heyboxId()), user.optString("heyboxid"), user.optString("uid"), user.optString("account_id"), user.optString("id"));
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
        int placeholder = this.session.darkMode() ? Color.rgb(28, 30, 32) : Color.rgb(235, 237, 240);
        Compat.setBackground(wrap, round(placeholder, 7));
        Compat.clipToOutline(wrap);

        TextView counter = text("1/" + urls.size(), 10.0f, -1);
        LinearLayout dots = new LinearLayout(this);
        ImagePagerCore core = new ImagePagerCore(this, this::dp, page -> {
            counter.setText((page + 1) + "/" + urls.size());
            for (int i = 0; i < dots.getChildCount(); i++) {
                dots.getChildAt(i).setAlpha(i == page ? 1.0f : 0.4f);
            }
        });
        final String[] urlArray = urls.toArray(new String[0]);
        for (int pageIndex = 0; pageIndex < urls.size(); pageIndex++) {
            final int position = pageIndex;
            final String url = urls.get(pageIndex);
            FrameLayout pageFrame = new FrameLayout(this);
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setAdjustViewBounds(false);
            pageFrame.addView(image, match());
            image.setOnClickListener(view -> {
                openImage(image, urlArray, position);
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

    private boolean addBodyParagraphs(LinearLayout parent, String source, boolean bodyStarted) {
        List<String> paragraphs = ArticleText.articleParagraphs(source);
        for (String paragraph : paragraphs) {
            if (ArticleText.isArticleQuote(paragraph)) {
                addArticleQuote(parent, paragraph, bodyStarted);
                bodyStarted = true;
            } else if (ArticleText.isInlineHeading(paragraph)) {
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
        quote.setPadding(0, dp(2), 0, dp(2));
        View bar = new View(this);
        int barColor = blend(this.SECONDARY, this.BG, this.session.darkMode() ? 0.38f : 0.62f);
        Compat.setBackground(bar, round(barColor, 2));
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
        PostImageFrame frame = new PostImageFrame(this, this::dp, heightDp);
        int placeholder = this.session.darkMode() ? Color.rgb(28, 30, 32) : Color.rgb(235, 237, 240);
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

    private int addComments(LinearLayout page, JSONArray groups, boolean latest) {
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
            int pinned = Boolean.compare(CommentData.isPinnedThread(b), CommentData.isPinnedThread(a));
            if (pinned != 0) return pinned;
            return latest ? Long.compare(CommentData.threadTime(b), CommentData.threadTime(a))
                    : Integer.compare(CommentData.threadLikes(b), CommentData.threadLikes(a));
        });
        int count = 0;
        Iterator<JSONObject> it = threads.iterator();
        while (it.hasNext()) {
            JSONObject group2 = it.next();
            JSONArray comments = group2 == null ? null : group2.optJSONArray("comment");
            JSONObject root = comments == null ? group2 : comments.optJSONObject(0);
            if (root != null) {
                if (CommentData.isCyComment(group2) && !root.has("is_cy")) {
                    try {
                        root.put("_group_is_cy", 1);
                    } catch (Exception ignored) {
                    }
                }
                LinearLayout linearLayoutCard = vertical(this.BG);
                linearLayoutCard.setPadding(dp(4), dp(9), dp(4), dp(8));
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
                    return Long.compare(CommentData.commentTime(a2), CommentData.commentTime(b2));
                });
                int expected = Math.max(root.optInt("child_num"), group2 == null ? 0 : group2.optInt("child_num"));
                if (!replies.isEmpty() || expected > 0) {
                    LinearLayout replySection = new LinearLayout(this);
                    replySection.setOrientation(1);
                    replySection.setPadding(dp(8), dp(3), dp(8), dp(3));
                    Compat.setBackground(replySection, roundStroke(this.themeTokens.faintAccent(),
                            12, this.themeTokens.hairline, 1));
                    LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(-1, -2);
                    sectionParams.topMargin = dp(5);
                    sectionParams.leftMargin = dp(44);
                    linearLayoutCard.addView(replySection, sectionParams);
                    LinearLayout replyList = new LinearLayout(this);
                    replyList.setOrientation(1);
                    replySection.addView(replyList, new LinearLayout.LayoutParams(-1, -2));
                    int initial = Math.min(REPLY_PREVIEW_COUNT, replies.size());
                    boolean allLoaded = expected <= replies.size();
                    renderReplies(replyList, root, replies, expected, initial, allLoaded);
                }
                addTop(page, CommentData.isPinnedComment(root) ? pinnedCommentCard(linearLayoutCard)
                        : linearLayoutCard, count == 0 ? 0 : 2);
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
            addReplyControl(parent, more);
            more.setOnClickListener(view -> {
                renderReplies(parent, root, replies, total, shown + REPLY_PAGE_SIZE, allLoaded);
            });
        } else if (!allLoaded && total > replies.size()) {
            TextView more2 = replyControl("再展开 5 条", R.drawable.ic_expand);
            addReplyControl(parent, more2);
            more2.setOnClickListener(view2 -> {
                loadSubComments(parent, root, replies, total, shown);
            });
        }
        if (shown > REPLY_PREVIEW_COUNT) {
            TextView collapse = replyControl("收起回复", R.drawable.ic_collapse);
            addReplyControl(parent, collapse);
            collapse.setOnClickListener(view3 -> {
                renderReplies(parent, root, replies, total, Math.min(REPLY_PREVIEW_COUNT, replies.size()), allLoaded);
            });
        }
    }

    private TextView replyControl(String label, int icon) {
        TextView control = text(label, 11.0f, this.themeTokens.accent);
        control.setGravity(17);
        control.setPadding(dp(8), 0, dp(8), 0);
        Compat.setBackground(control, round(this.themeTokens.softAccent(), 13));
        setLeftIcon(control, icon, this.themeTokens.accent, 12);
        return control;
    }

    private void addReplyControl(LinearLayout parent, TextView control) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(26));
        params.topMargin = dp(3);
        parent.addView(control, params);
    }

    private void loadSubComments(final LinearLayout target, final JSONObject root, final List<JSONObject> preview, final int expected, final int shown) {
        final TextView loading = text("正在加载更多回复", 11.0f, this.MUTED);
        loading.setPadding(0, dp(8), 0, dp(8));
        target.addView(loading);
        Map<String, String> params = new HashMap<>();
        final String id = CommentData.commentId(root);
        params.put("comment_id", id);
        params.put("commentid", id);
        params.put("root_comment_id", id);
        params.put("link_id", this.currentLinkId);
        params.put("offset", String.valueOf(preview.size()));
        params.put("page", String.valueOf((preview.size() / 50) + 1));
        params.put("limit", "50");
        this.api.get(EndpointProvider.subComments(), params, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                List<JSONObject> replies = MainActivity.this.extractSubComments(body, id);
                if (replies.isEmpty()) {
                    target.removeView(loading);
                    MainActivity.this.toast("没有获取到更多回复");
                    return;
                }
                List<JSONObject> merged = MainActivity.this.mergeReplies(preview, replies);
                Collections.sort(merged, (a, b) -> {
                    return Long.compare(CommentData.commentTime(a), CommentData.commentTime(b));
                });
                int total = Math.max(expected, merged.size());
                MainActivity.this.renderReplies(target, root, merged, total, shown + MainActivity.REPLY_PAGE_SIZE, merged.size() >= total);
            }

            @Override
            public void onError(String message) {
                target.removeView(loading);
                MainActivity.this.toast("回复加载失败" + message);
            }
        });
    }

    private List<JSONObject> mergeReplies(List<JSONObject> first, List<JSONObject> second) {
        List<JSONObject> merged = new ArrayList<>(first);
        for (JSONObject candidate : second) {
            String id = CommentData.commentId(candidate);
            boolean duplicate = false;
            Iterator<JSONObject> it = merged.iterator();
            while (true) {
                if (!it.hasNext()) {
                    break;
                }
                JSONObject existing = it.next();
                if (!id.isEmpty() && id.equals(CommentData.commentId(existing))) {
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
                        if (reply != null && !rootId.equals(CommentData.commentId(reply))) {
                            values.add(reply);
                        }
                    }
                } else if (!rootId.equals(CommentData.commentId(item))) {
                    values.add(item);
                }
            }
        }
        return values;
    }

    private CommentLikeControl commentLikeControl() {
        LinearLayout root = new LinearLayout(this);
        root.setGravity(17);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(dp(2), 0, dp(2), 0);
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        root.addView(icon, new LinearLayout.LayoutParams(dp(15), dp(15)));
        TextView count = text("0", 10.0f, this.MUTED);
        count.setGravity(16);
        count.setSingleLine(true);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(-2, -2);
        countParams.leftMargin = dp(2);
        root.addView(count, countParams);
        return new CommentLikeControl(root, icon, count);
    }

    private void updateCommentLikeView(CommentLikeControl view, boolean liked, int likes) {
        int color = liked ? this.TEXT : this.MUTED;
        view.icon.setImageResource(liked ? R.drawable.official_comment_like_filled
                : R.drawable.official_comment_like_line);
        view.icon.setColorFilter(color);
        view.count.setText(Format.commentLikeCount(Math.max(0, likes)));
        view.count.setTextColor(color);
    }

    private void toggleCommentLike(final JSONObject comment, final CommentLikeControl view) {
        if (requireLogin("评论点赞")) {
            if (!allowWriteAction("评论点赞")) {
                return;
            }
            String id = CommentData.commentId(comment);
            if (id.isEmpty()) {
                toast("没有获取到评论 ID");
                return;
            }
            final boolean beforeLiked = CommentData.commentLiked(comment);
            final int beforeLikes = CommentData.commentLikes(comment);
            boolean nextLiked = !beforeLiked;
            int nextLikes = Math.max(0, beforeLikes + (nextLiked ? 1 : -1));
            setCommentLikeState(comment, nextLiked, nextLikes);
            updateCommentLikeView(view, nextLiked, nextLikes);
            view.root.setEnabled(false);
            postCommentLike(id, nextLiked, new ApiClient.Callback() {
                @Override
                public void onSuccess(JSONObject body) {
                    view.root.setEnabled(true);
                }

                @Override
                public void onError(String message) {
                    view.root.setEnabled(true);
                    MainActivity.this.setCommentLikeState(comment, beforeLiked, beforeLikes);
                    MainActivity.this.updateCommentLikeView(view, beforeLiked, beforeLikes);
                    MainActivity.this.toast("评论点赞失败" + MainActivity.this.writeErrorMessage("评论点赞", message));
                }
            });
        }
    }

    private void setCommentLikeState(JSONObject comment, boolean liked, int likes) {
        try {
            comment.put("is_support", liked ? 1 : 0);
            comment.put("comment_award_num", Math.max(0, likes));
        } catch (Exception e) {
        }
    }

    private void postCommentLike(String id, boolean liked, ApiClient.Callback callback) {
        this.writeActions.commentLike(id, hsrcFor(this.currentDetailItem), liked, callback);
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
            input.setMaxLines(5);
            input.setGravity(8388659);
            input.setSingleLine(false);
            input.setHint(replyTo == null ? "友好交流，理性讨论" : "输入回复内容");
            String draftKey = commentDraftKey(replyTo);
            String draft = this.session.commentDraft(draftKey);
            if (!draft.isEmpty()) {
                input.setText(draft);
                input.setSelection(draft.length());
            }
            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    MainActivity.this.session.setCommentDraft(draftKey, s == null ? "" : s.toString());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
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
        String replyId = replyTo == null ? "-1" : CommentData.commentId(replyTo);
        String rootId = replyTo == null ? "-1" : CommentData.commentRootId(replyTo);
        if (sendButton != null) {
            sendButton.setEnabled(false);
            sendButton.setAlpha(0.58f);
        }
        postCreateComment(value, rootId, replyId, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                MainActivity.this.session.setCommentDraft(MainActivity.this.commentDraftKey(replyTo), "");
                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                }
                MainActivity.this.toast("评论已发");
                if (MainActivity.this.currentDetailItem != null) {
                    MainActivity.this.showDetail(MainActivity.this.currentDetailItem);
                }
            }

            @Override
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
        this.writeActions.createComment(this.currentLinkId, hsrcFor(this.currentDetailItem),
                this.currentAuthCode, value, rootId, replyId, callback);
    }

    private String commentDraftKey(JSONObject replyTo) {
        String replyId = replyTo == null ? "root" : CommentData.commentId(replyTo);
        return (this.currentLinkId == null ? "" : this.currentLinkId) + ":" + replyId;
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
            Compat.setBackground(avatar, round(this.session.darkMode() ? Color.rgb(50, 53, 56) : Color.rgb(226, 229, 232), 20));
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
        String target = CommentData.replyTarget(comment);
        long created = CommentData.commentTime(comment);
        String visibleComment = RichContent.commentText(comment.optString("text"),
                comment.optString("content"), comment.optString("html"),
                comment.optString("description"), comment.optString("desc_extra"),
                comment.optString("rich_text"), comment.optString("hb_rich_texts"));
        if (reply) {
            addCompactReply(block, comment, author, target, visibleComment, created);
        } else {
            block.addView(commentNameRow(author, user, isPostAuthorComment(user, author)));
            String meta = commentSubMeta(comment, created);
            if (!meta.isEmpty()) {
                TextView metaView = text(meta, 10.5f, this.MUTED);
                addTop(block, metaView, 1);
            }
            String displayComment = CommentData.isCyComment(comment) ? "Cy " + visibleComment : visibleComment;
            TextView value = text(displayComment, 13.0f, this.TEXT);
            value.setLineSpacing(dp(1), this.session.bodyLineSpacing() / 100.0f);
            Compat.setLetterSpacing(value, this.session.bodyLetterSpacing() / 200.0f);
            value.setTypeface(Typeface.create("sans-serif-medium", 0));
            EmojiRenderer.set(value, displayComment, this.session.darkMode(), span -> {
                if (CommentData.isCyComment(comment)) {
                    applyInlineImageBadge(span, 0, 2, R.drawable.official_cy_badge, 15);
                }
            });
            addTop(block, value, 5);
        }
        List<CommentData.CommentImage> commentImages = CommentData.commentImages(comment);
        if (!this.session.noImage() && !commentImages.isEmpty()) {
            addCommentImages(block, commentImages, reply);
        }
        if (!reply) {
            CommentLikeControl likes = commentLikeControl();
            updateCommentLikeView(likes, CommentData.commentLiked(comment), CommentData.commentLikes(comment));
            likes.root.setOnClickListener(view -> {
                toggleCommentLike(comment, likes);
            });
            LinearLayout.LayoutParams likeParams = new LinearLayout.LayoutParams(-2, dp(26));
            likeParams.leftMargin = dp(3);
            row.addView(likes.root, likeParams);
        }
        View.OnLongClickListener copy = view -> {
            copyCommentText(visibleComment);
            return true;
        };
        block.setOnLongClickListener(copy);
        row.setOnLongClickListener(copy);
        attachCommentReplyGesture(row, comment);
        linearLayout.addView(row);
        if (reply) {
            View divider = new View(this);
            divider.setBackgroundColor(blend(this.BG, this.MUTED, this.session.darkMode() ? 0.10f : 0.05f));
            linearLayout.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        }
    }

    private void addCommentImages(LinearLayout parent, List<CommentData.CommentImage> images,
                                  boolean reply) {
        LinearLayout gallery = vertical(0);
        int size = reply ? 68 : 82;
        for (int start = 0; start < images.size(); start += 3) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(3);
            int end = Math.min(start + 3, images.size());
            for (int i = start; i < end; i++) {
                ImageView image = commentImage(images.get(i), size);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(size), dp(size));
                if (i > start) params.leftMargin = dp(5);
                row.addView(image, params);
            }
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(size));
            if (start > 0) rowParams.topMargin = dp(5);
            gallery.addView(row, rowParams);
        }
        LinearLayout.LayoutParams galleryParams = new LinearLayout.LayoutParams(-1, -2);
        galleryParams.topMargin = dp(6);
        parent.addView(gallery, galleryParams);
    }

    private ImageView commentImage(CommentData.CommentImage source, int sizeDp) {
        String url = source.url;
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int placeholder = this.session.darkMode() ? Color.rgb(28, 30, 32)
                : Color.rgb(235, 237, 240);
        Compat.setBackground(image, round(placeholder, 6));
        Compat.clipToOutline(image);
        image.setOnClickListener(view -> openImage(image, url));
        ImageLoader.intoMeasuredRevealStable(image, url, Math.max(240, dp(sizeDp) * 2),
                (success, bitmap) -> {
                    if (success && this.session.playGif()
                            && (source.animated || GifSupport.isGifUrl(url))) {
                        ImageLoader.intoGif(image, url, animated -> {
                            this.localCache.log("comment gif " + (animated ? "started " : "failed ")
                                    + compactLogText(url, 120));
                        });
                    }
                    if (!success && image.getDrawable() == null) {
                        image.setImageDrawable(Compat.tintedDrawable(this,
                                R.drawable.il_image, this.MUTED));
                        image.setPadding(dp(18), dp(18), dp(18), dp(18));
                    }
                });
        return image;
    }

    private void addCompactReply(LinearLayout block, JSONObject comment, String author,
                                 String target, String visibleComment, long created) {
        final String name = author.isEmpty() ? "匿名用户" : author;
        final String authorBadge = isPostAuthorComment(comment.optJSONObject("user"), name)
                ? " 作者 " : "";
        final String cyBadge = CommentData.isCyComment(comment) ? " Cy " : "";
        final boolean hasTarget = !target.isEmpty();
        final String replyLabel = hasTarget ? " 回复 " : "";
        final String replyName = hasTarget ? "@" + target : "";
        final String meta = commentSubMeta(comment, created);
        final String metaSeg = meta.isEmpty() ? "" : "  " + meta;
        final String full = name + authorBadge + replyLabel + replyName + "："
                + cyBadge + visibleComment + metaSeg;

        final int nameEnd = name.length();
        final int authorBadgeStart = nameEnd;
        final int replyNameStart = nameEnd + authorBadge.length() + replyLabel.length();
        final int replyNameEnd = replyNameStart + replyName.length();
        final int cyBadgeStart = replyNameEnd + 1;
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
            if (!authorBadge.isEmpty()) {
                applyInlineImageBadge(span, authorBadgeStart,
                        authorBadgeStart + authorBadge.length(), R.drawable.official_author_badge, 13);
            }
            if (hasTarget) {
                span.setSpan(new android.text.style.ForegroundColorSpan(nameColor),
                        replyNameStart, replyNameEnd, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (!cyBadge.isEmpty()) {
                applyInlineImageBadge(span, cyBadgeStart,
                        cyBadgeStart + cyBadge.length(), R.drawable.official_cy_badge, 15);
            }
            if (!meta.isEmpty()) {
                span.setSpan(new android.text.style.ForegroundColorSpan(metaColor),
                        metaStart, metaEnd, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        });
        block.addView(value, new LinearLayout.LayoutParams(-1, -2));
    }

    private LinearLayout commentNameRow(String author, JSONObject user, boolean postAuthor) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(16);
        row.addView(commentNameText(author, false), new LinearLayout.LayoutParams(-2, -2));
        if (postAuthor) addAuthorBadge(row);
        addLevelBadge(row, user, false);
        return row;
    }

    private void addAuthorBadge(LinearLayout row) {
        ImageView badge = new ImageView(this);
        badge.setImageResource(R.drawable.official_author_badge);
        badge.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(28), dp(15));
        params.leftMargin = dp(2);
        row.addView(badge, params);
    }

    private boolean isPostAuthorComment(JSONObject user, String author) {
        String commentUserId = userId(user);
        String postAuthorId = this.currentDetailItem == null ? "" : this.currentDetailItem.authorId;
        if (postAuthorId.isEmpty() && this.currentDetailBody != null) {
            JSONObject result = this.currentDetailBody.optJSONObject("result");
            JSONObject link = result == null ? null : result.optJSONObject("link");
            postAuthorId = authorUserId(link, link == null ? null : link.optJSONObject("user"));
        }
        if (!commentUserId.isEmpty() && !postAuthorId.isEmpty()) {
            return commentUserId.equals(postAuthorId);
        }
        return this.currentDetailItem != null && !author.isEmpty()
                && author.equals(this.currentDetailItem.author);
    }

    private View pinnedCommentCard(LinearLayout card) {
        // 官方置顶：左上角丝带角标；正文整体下移，圆头像与角标之间留出间距才美观
        card.setPadding(card.getPaddingLeft(), card.getPaddingTop() + dp(9),
                card.getPaddingRight(), card.getPaddingBottom());
        FrameLayout frame = new FrameLayout(this);
        frame.addView(card, new FrameLayout.LayoutParams(-1, -2));
        ImageView corner = new ImageView(this);
        corner.setImageResource(R.drawable.official_comment_pinned_corner);
        corner.setScaleType(ImageView.ScaleType.FIT_XY);
        FrameLayout.LayoutParams cornerParams = new FrameLayout.LayoutParams(dp(34), dp(34));
        cornerParams.gravity = 51;
        frame.addView(corner, cornerParams);
        TextView label = text("置顶", 7.5f, Color.WHITE);
        label.setGravity(17);
        label.setTypeface(appRegularTypeface(), Typeface.BOLD);
        label.setRotation(-45.0f);
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(dp(30), dp(14));
        labelParams.leftMargin = -dp(4);
        labelParams.topMargin = dp(2);
        frame.addView(label, labelParams);
        return frame;
    }

    private void applyInlineImageBadge(android.text.Spannable span, int start, int end,
                                       int drawableRes, int heightDp) {
        if (start < 0 || end > span.length()) return;
        // 收窄到非空白核心：两侧空格保留为真实间距，徽章不再贴着名字和冒号
        while (start < end && span.charAt(start) == ' ') start++;
        while (end > start && span.charAt(end - 1) == ' ') end--;
        if (end <= start) return;
        Drawable drawable = getResources().getDrawable(drawableRes).mutate();
        int height = dp(heightDp);
        int width = drawable.getIntrinsicHeight() <= 0 ? height
                : Math.max(1, Math.round(height * drawable.getIntrinsicWidth()
                / (float) drawable.getIntrinsicHeight()));
        drawable.setBounds(0, 0, width, height);
        span.setSpan(new CenteredImageSpan(drawable), start, end,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
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
        int level = CommentData.userLevel(user);
        if (level <= 0) return;
        int levelColor = CommentData.levelBadgeColor(level);
        TextView badge = text("Lv." + level, small ? 7.0f : 8.0f, levelColor);
        badge.setGravity(17);
        badge.setTypeface(appRegularTypeface(), 1);
        badge.setPadding(dp(4), 0, dp(4), 0);
        Compat.setBackground(badge, round(blend(this.PANEL, levelColor,
                this.session.darkMode() ? 0.30f : 0.14f), 4));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(small ? 13 : 15));
        params.leftMargin = dp(4);
        params.rightMargin = dp(4);
        row.addView(badge, params);
    }

    private String commentSubMeta(JSONObject comment, long created) {
        String time = created > 0 ? commentDisplayTime(created) : "";
        String location = CommentData.commentLocation(comment);
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

    private void copyCommentText(String value) {
        if (TextUtils.isEmpty(value)) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("评论", value));
        toast("评论已复制");
    }

    private void showProfile() {
        // 底部导航从社区切到“我的”：双页整体向左平移
        if ("feed".equals(this.screen)) {
            this.pendingLateralPush = true;
        }
        if (!this.session.isLoggedIn()) {
            showGuestProfile();
            return;
        }
        activate("profile");
        updateReadingTimeEntry();
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
            transitionTo(detailLoadingPage());
            this.api.get(EndpointProvider.profile(), Collections.singletonMap(SecureStrings.userid(), this.session.userId()), new ApiClient.Callback() {
                @Override
                public void onSuccess(JSONObject body) {
                    if (!"profile".equals(MainActivity.this.screen) || MainActivity.this.isFinishing()) return;
                    MainActivity.this.hideLoading();
                    MainActivity.this.renderProfile(body);
                }

                @Override
                public void onError(String message) {
                    if (!"profile".equals(MainActivity.this.screen) || MainActivity.this.isFinishing()) return;
                    MainActivity.this.hideLoading();
                    MainActivity.this.toast("个人资料加载失败" + message);
                    MainActivity.this.renderProfile(new JSONObject());
                }
            });
        }
    }

    private void showGuestProfile() {
        activate("profile");
        updateReadingTimeEntry();
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
        headCopy.addView(text("登录后可点赞、收藏和评论", 12.0f, this.MUTED));
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
        this.retainedPages.put("settings_home", settingsHome);
        transitionTo(settingsHome);
    }

    private View buildSettingsHomeContent() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = vertical(this.BG);
        page.setPadding(dp(8), dp(8), dp(8), dp(14));
        scroll.addView(page);
        page.addView(settingsTopCard("设置中心"));
        LinearLayout panel = settingsList();
        addSettingEntry(panel, "显示与主题", "主题、字号、间距与界面预览", R.drawable.il_palette, this::showDisplaySettings);
        addSettingEntry(panel, "启动与更新", "开屏动画、自动检查更新", R.drawable.il_refresh, this::showStartupSettings);
        addSettingEntry(panel, "内容与缓存", "图片、离线内容与登录状态", R.drawable.il_globe, this::showAppSettings);
        addSettingEntry(panel, "关于", appVersion(), R.drawable.il_info, this::showAbout);
        page.addView(panel);
        return scroll;
    }

    private TextView addSettingEntry(LinearLayout parent, String name, String description, int icon, Runnable action) {
        if (parent.getChildCount() > 0) {
            View divider = new View(this);
            divider.setBackgroundColor(this.session.darkMode()
                    ? Color.argb(16, 255, 255, 255) : Color.argb(14, 0, 0, 0));
            LinearLayout.LayoutParams dividerParams =
                    new LinearLayout.LayoutParams(-1, Math.max(1, dp(1) / 2));
            dividerParams.leftMargin = dp(45);
            parent.addView(divider, dividerParams);
        }
        LinearLayout row = new LinearLayout(this);
        row.setGravity(16);
        row.setPadding(dp(6), dp(14), dp(6), dp(14));
        ImageView marker = new ImageView(this);
        marker.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        Drawable iconDrawable = Compat.tintedDrawable(this, icon, this.themeTokens.text);
        if (iconDrawable != null) {
            marker.setImageDrawable(iconDrawable);
        }
        marker.setPadding(dp(5), dp(5), dp(5), dp(5));
        Compat.setBackground(marker, UiComponents.monoChip(this, this.themeTokens,
                this.session.uiScale() / 100.0f));
        LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(dp(27), dp(27));
        markerParams.rightMargin = dp(12);
        row.addView(marker, markerParams);
        LinearLayout copy = vertical(0);
        TextView titleView = text(name, 14.5f,
                name.startsWith("退出登录") ? Color.rgb(228, 88, 88) : this.TEXT);
        titleView.setTypeface(appRegularTypeface(), 1);
        copy.addView(titleView);
        TextView descView = null;
        if (description != null && !description.isEmpty()) {
            descView = text(description, 11.0f, this.MUTED);
            descView.setPadding(0, dp(1), 0, 0);
            copy.addView(descView);
        }
        if ("阅读时长".equals(name)) {
            this.readingTodayView = descView;
        }
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
        return descView;
    }

    private void addProfileMenu(LinearLayout page, boolean loggedIn) {
        LinearLayout panel = settingsList();
        if (loggedIn) {
            addSettingEntry(panel, "我的动态", "动态与投稿", R.drawable.il_person,
                    () -> showUserSpace(this.session.userId(), this.session.userName(),
                            this.session.avatar()));
        }
        addSettingEntry(panel, "阅读中心", readingEntrySummary() + " · "
                        + this.localCache.watchLaterItems().size() + " 篇离线",
                R.drawable.il_reading, this::showReadingCenter);
        addSettingEntry(panel, "收藏", "我收藏的帖子", R.drawable.il_bookmark, () -> {
            if (!this.session.isLoggedIn()) {
                showLogin();
                return;
            }
            showFavorites();
        });
        addSettingEntry(panel, "每日签到", SIGN_IN_ENABLED ? "每日签到领取盒币" : "暂时关闭",
                R.drawable.il_calendar, () -> {
                    if (SIGN_IN_ENABLED) {
                        showSignInDialog();
                    } else {
                        toast("签到暂时关闭");
                    }
                });
        addSettingEntry(panel, "设置", "主题、缓存与关于", R.drawable.il_settings,
                this::showSettingsHome);
        addTop(page, panel, 8);
        updateReadingTimeEntry();
    }

    private void showReadingCenter() {
        prepareReadingCenter();
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = vertical(this.BG);
        page.setPadding(dp(8), dp(8), dp(8), dp(18));
        scroll.addView(page);
        List<FeedItem> recent = this.localCache.recentItems();
        if (!recent.isEmpty()) {
            FeedItem item = recent.get(0);
            int progress = this.localCache.scroll(item.id);
            LinearLayout continuePanel = settingsList();
            addSettingEntry(continuePanel, "继续阅读", item.title,
                    R.drawable.il_reading, () -> showDetail(item));
            if (progress > 0) {
                addTop(continuePanel, text("已记录上次阅读位置", 10.5f, this.MUTED), 0);
            }
            page.addView(continuePanel);
        }
        LinearLayout library = settingsList();
        addSettingEntry(library, "阅读时长", readingEntrySummary(),
                R.drawable.il_reading, this::showReadingStats);
        addSettingEntry(library, "稍后看", this.localCache.watchLaterItems().size()
                        + " 篇 · " + Format.cacheMb(this.localCache.offlineBytes()),
                R.drawable.il_history, this::showWatchLater);
        addSettingEntry(library, "历史记录", this.session.isLoggedIn()
                        ? "与小黑盒账号同步" : "登录后查看小黑盒记录",
                R.drawable.il_history, this::showCloudHistory);
        addTop(page, library, page.getChildCount() == 0 ? 0 : 8);
        addBottomNavSafeSpace(page);
        this.retainedPages.put("reading_center", scroll);
        this.content.addView(scroll, match());
    }

    private void prepareReadingCenter() {
        stopQrPolling();
        ensureEmojiCatalog(() -> {
        });
        this.screen = "reading_center";
        setBottomNavVisible(false);
        this.leading.setVisibility(0);
        this.leading.setOnClickListener(view -> {
            this.pendingBackTransition = true;
            showProfile();
        });
        this.title.setText("阅读中心");
        this.action.setVisibility(4);
        this.content.removeAllViews();
        this.pendingBackTransition = false;
    }

    private void showCloudHistory() {
        if (!this.session.isLoggedIn()) {
            showLogin();
            return;
        }
        Map<String, String> params = new HashMap<>();
        params.put("type", "all");
        params.put("dw", "636");
        params.put("no_more", "false");
        showSavedList("历史记录", EndpointProvider.history(), params, true, null,
                "reading_center");
    }

    private void showWatchLater() {
        prepareSavedPage("稍后看", "reading_center");
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
        String size = bytes > 0L ? Format.offlineSize(bytes) : "缓存已过期";
        TextView info = text(size + " · 更新于 " + Format.offlineTime(entry.updatedAt), 10.0f, this.MUTED);
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

    private void showSignInDialog() {
        if (!SIGN_IN_ENABLED) {
            toast("签到暂时关闭");
            return;
        }
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
        this.api.get(EndpointProvider.favoriteTabs(), Collections.emptyMap(), new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                MainActivity.this.localCache.log("favorite tabs loaded: " + SavedPostParser.favoriteTabSummary(body));
                MainActivity.this.showFavoriteContents("tab ok");
            }

            @Override
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
        this.api.get(EndpointProvider.favoriteFolders(), folderParams, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                JSONObject folder = SavedPostParser.firstFavoriteFolder(SavedPostParser.findFavoriteFolders(body));
                String folderId = SavedPostParser.favoriteFolderId(folder);
                if (folder == null) {
                    MainActivity.this.localCache.log("favorite folders missing: " + SavedPostParser.favoriteFolderSummary(body));
                    MainActivity.this.showFavoriteLegacyList("folders missing");
                } else if (folderId.isEmpty()) {
                    MainActivity.this.localCache.log("favorite folder id missing: " + folder);
                    MainActivity.this.showFavoriteLegacyList("folder id missing");
                } else {
                    MainActivity.this.showSavedList(MainActivity.TITLE_FAVORITES, EndpointProvider.favoriteLinks(), MainActivity.this.favoriteParams(folderId), false, null);
                }
            }

            @Override
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

    private void showFavoritesUnavailable(JSONObject body, String message) {
        hideLoading();
        prepareSavedPage(TITLE_FAVORITES);
        String cacheKey = savedCacheKey(TITLE_FAVORITES, EndpointProvider.favoriteLinks());
        List<FeedItem> cached = FeedCollection.filter(this.localCache.savedList(cacheKey),
                this.session.blockKeywordList());
        if (!cached.isEmpty()) {
            toast(MSG_OFFLINE_CACHE);
            renderSavedItems(TITLE_FAVORITES, cached);
            return;
        }
        if (message != null && !message.isEmpty()) {
            this.localCache.log("favorite unavailable reason: " + message);
        }
        if (body != null) {
            this.localCache.log("favorite unavailable body: " + SavedPostParser.favoriteFolderSummary(body));
        }
        showMessage(MSG_FAVORITES_UNAVAILABLE);
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

    private void prepareSavedPage(String pageTitle) {
        prepareSavedPage(pageTitle, "profile");
    }

    private void prepareSavedPage(String pageTitle, String returnScreen) {
        stopQrPolling();
        ensureEmojiCatalog(() -> {
        });
        this.screen = "saved";
        this.savedReturnScreen = TextUtils.isEmpty(returnScreen) ? "profile" : returnScreen;
        setBottomNavVisible(false);
        this.leading.setVisibility(0);
        this.leading.setOnClickListener(view -> returnFromSavedPage());
        this.title.setText(pageTitle);
        this.action.setVisibility(4);
        this.content.removeAllViews();
    }

    private void showSavedList(String pageTitle, String path, Map<String, String> params) {
        showSavedList(pageTitle, path, params, true, null);
    }

    private void showSavedList(final String pageTitle, final String path, final Map<String, String> params, boolean includeUserId, final SavedListFallback fallback) {
        showSavedList(pageTitle, path, params, includeUserId, fallback, "profile");
    }

    private void showSavedList(final String pageTitle, final String path,
                               final Map<String, String> params, boolean includeUserId,
                               final SavedListFallback fallback, String returnScreen) {
        prepareSavedPage(pageTitle, returnScreen);
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
        this.api.get(path, params, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                MainActivity.this.hideLoading();
                List<FeedItem> items2 = FeedCollection.filter(SavedPostParser.feedItems(body),
                        MainActivity.this.session.blockKeywordList());
                if (items2.isEmpty()) {
                    List<FeedItem> cached = FeedCollection.filter(MainActivity.this.localCache.savedList(cacheKey),
                            MainActivity.this.session.blockKeywordList());
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

            @Override
            public void onError(String message) {
                if (WriteActionClient.isLoginError(message)) {
                    MainActivity.this.localCache.log(pageTitle + " web failed, trying mobile: " + message);
                    Map<String, String> mobileParams = new HashMap<>((Map<? extends String, ? extends String>) params);
                    mobileParams.remove("x_os_type");
                    mobileParams.remove("device_info");
                    MainActivity.this.requestSavedListMobile(pageTitle, path, mobileParams, cacheKey, fallback, true);
                    return;
                }
                MainActivity.this.hideLoading();
                MainActivity.this.localCache.log(pageTitle + " failed: " + message);
                List<FeedItem> cached = FeedCollection.filter(MainActivity.this.localCache.savedList(cacheKey),
                        MainActivity.this.session.blockKeywordList());
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
        this.api.getSigned(path, params, HeyboxSigner.Algorithm.ANDROID, ApiClient.RequestProfile.MOBILE, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                MainActivity.this.hideLoading();
                List<FeedItem> items2 = FeedCollection.filter(SavedPostParser.feedItems(body),
                        MainActivity.this.session.blockKeywordList());
                if (items2.isEmpty()) {
                    List<FeedItem> cached = FeedCollection.filter(MainActivity.this.localCache.savedList(cacheKey),
                            MainActivity.this.session.blockKeywordList());
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

            @Override
            public void onError(String message) {
                if (WriteActionClient.isLoginError(message) || WriteActionClient.isParameterError(message)) {
                    if (MainActivity.this.localCache != null) {
                        MainActivity.this.localCache.log(pageTitle + " mobile failed, trying official: " + message);
                    }
                    MainActivity.this.requestSavedListOfficial(pageTitle, path, params, cacheKey, fallback, authSuspect || WriteActionClient.isLoginError(message));
                    return;
                }
                MainActivity.this.hideLoading();
                if (MainActivity.this.localCache != null) {
                    MainActivity.this.localCache.log(pageTitle + " mobile failed: " + message);
                }
                List<FeedItem> cached = FeedCollection.filter(MainActivity.this.localCache.savedList(cacheKey),
                        MainActivity.this.session.blockKeywordList());
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
        this.api.getSigned(path, params, HeyboxSigner.Algorithm.ANDROID, ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                MainActivity.this.hideLoading();
                List<FeedItem> items2 = FeedCollection.filter(SavedPostParser.feedItems(body),
                        MainActivity.this.session.blockKeywordList());
                if (items2.isEmpty()) {
                    List<FeedItem> cached = FeedCollection.filter(MainActivity.this.localCache.savedList(cacheKey),
                            MainActivity.this.session.blockKeywordList());
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

            @Override
            public void onError(String message) {
                MainActivity.this.hideLoading();
                if (MainActivity.this.localCache != null) {
                    MainActivity.this.localCache.log(pageTitle + " official failed: " + message);
                }
                List<FeedItem> cached = FeedCollection.filter(MainActivity.this.localCache.savedList(cacheKey),
                        MainActivity.this.session.blockKeywordList());
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
        list.setDividerHeight(dp(2));
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
        searchParams.topMargin = dp(5);
        frameLayout.addView(search, searchParams);
        prepareSearchBar(search, dp(40));
        final List<FeedItem> filtered = new ArrayList<>(allItems);
        final FeedAdapter adapter = new FeedAdapter(this, filtered, this.session.noImage(), this.session.uiScale() / 100.0f, this.session.textScale() / 100.0f, this.session.darkMode(), this.PRIMARY, this.SECONDARY, this::showDetail, this::toggleFeedLike);
        final ListView list = new ListView(this);
        list.setBackgroundColor(this.BG);
        list.setDivider(new ColorDrawable(0));
        list.setDividerHeight(dp(2));
        list.setAdapter((ListAdapter) adapter);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                MainActivity.this.setSearchBarVisible(search, scrollState == 0);
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            }
        });
        results.addView(list, match());
        final TextView emptyView = text(allItems.isEmpty() ? MSG_EMPTY_CONTENT : "没有找到相关历史记录", 13.0f, this.MUTED);
        emptyView.setGravity(17);
        results.addView(emptyView, match());
        emptyView.setVisibility(allItems.isEmpty() ? 0 : 8);
        list.setVisibility(allItems.isEmpty() ? 8 : 0);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
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

            @Override
            public void afterTextChanged(Editable value) {
            }
        });
        this.content.addView(frameLayout, match());
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
        this.retainedPages.put(key, scroll);
        if (this.shellAnimating) {
            transitionTo(scroll);
        } else {
            this.handler.post(() -> {
                if (key.equals(this.screen) && scroll.getParent() == null && !isFinishing()) {
                    transitionTo(scroll);
                }
            });
        }
        return page;
    }

    /** 卡外组名：小号加字距标签，站在分组卡上方，页面形成"标题—内容块"节奏。 */
    private void addSectionLabel(LinearLayout page, String label) {
        TextView view = text(label, 9.5f, this.themeTokens.subtle);
        view.setTypeface(appRegularTypeface(), Typeface.BOLD);
        Compat.setLetterSpacing(view, 0.18f);
        view.setPadding(dp(6), 0, 0, dp(4));
        addTop(page, view, 12);
    }

    private View settingsTopCard(String pageTitle) {
        LinearLayout box = new LinearLayout(this);
        box.setGravity(16);
        box.setPadding(dp(4), 0, dp(4), 0);
        box.setBackgroundColor(this.BG);
        TextView name = text(pageTitle, 20.5f, this.TEXT);
        name.setTypeface(appRegularTypeface(), 1);
        box.addView(name, new LinearLayout.LayoutParams(0, dp(42), 1.0f));
        TextView time = text(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()), 12.0f, this.MUTED);
        time.setGravity(21);
        box.addView(time, new LinearLayout.LayoutParams(dp(58), dp(42)));
        return box;
    }

    private void showDisplaySettings() {
        LinearLayout linearLayout = settingsPage("display_settings", "显示与主题");
        addSectionLabel(linearLayout, "显示");
        LinearLayout panel = settingsList();
        boolean[] dark = {this.session.darkMode()};
        boolean[] bodyBold = {this.session.bodyBold()};
        boolean[] roundScreen = {this.session.roundScreen()};
        addTop(panel, toggleRow("夜间模式", dark[0], value -> {
            dark[0] = value;
        }), 0);
        addSettingEntry(panel, "查看界面预览", "在真实布局里检查当前参数", R.drawable.il_eye,
                this::showDisplayPreview);
        LinearLayout livePreview = vertical(0);
        livePreview.setPadding(dp(10), dp(8), dp(10), dp(8));
        Compat.setBackground(livePreview, roundStroke(this.themeTokens.panelElevated, 12,
                this.themeTokens.hairline, 1));
        TextView previewTitle = text("显示效果预览", 14.0f, this.TEXT);
        previewTitle.setTypeface(appRegularTypeface(), 1);
        TextView previewBody = text("帖子正文会跟随下方设置实时变化。\n第二段用于预览段落间距", 13.0f, this.TEXT);
        previewBody.setTypeface(Typeface.create("sans-serif-medium", 0));
        TextView previewAction = text("主色按钮", 11.0f, contrast(this.PRIMARY));
        previewAction.setGravity(17);
        Compat.setBackground(previewAction, round(this.PRIMARY, 11));
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
        ScaleControl padding = settingSlider(panel, "左右边距", "dp", 0, 30, this.session.pagePadding(), value4 -> {
            updateDisplayPreview(livePreview, previewTitle, previewBody, previewAction, -1, -1, value4);
        });
        linearLayout.addView(panel);
        addSectionLabel(linearLayout, "屏幕适配");
        panel = card();
        TextView roundDesc = text("圆屏模式会给页面四周留出安全边距，避免内容贴到屏幕边缘。横纵向边距按屏幕百分比计算，适合圆屏和小屏手表微调", 11.0f, this.MUTED);
        roundDesc.setLineSpacing(0.0f, 1.16f);
        addTop(panel, roundDesc, 2);
        ScaleControl[] screenPaddingH = {settingSlider(panel, "横向边距", "%", 0, 30, this.session.screenPaddingHPercent(), value6 -> {
        })};
        ScaleControl[] screenPaddingV = {settingSlider(panel, "纵向边距", "%", 0, 30, this.session.screenPaddingVPercent(), value7 -> {
        })};
        addTop(panel, toggleRow("圆屏适配", roundScreen[0], value5 -> {
            roundScreen[0] = value5;
            int h = value5 ? 5 : 0;
            int v = value5 ? 3 : 0;
            setScaleControlValue(screenPaddingH[0], h, 0);
            setScaleControlValue(screenPaddingV[0], v, 0);
            updateDisplayPreview(livePreview, previewTitle, previewBody, previewAction, -1, -1, parseNumber(padding.input, 0, 30) == null ? this.session.pagePadding() : parseNumber(padding.input, 0, 30).intValue());
        }), 0);
        linearLayout.addView(panel);
        addSectionLabel(linearLayout, "正文排版");
        panel = card();
        ScaleControl bodyText = settingSlider(panel, "正文字号", "%", 75, 170, this.session.bodyTextScale(), value8 -> {
            previewBody.setTextSize((13 * value8) / 100.0f);
        });
        ScaleControl letterSpacing = settingSlider(panel, "字间", "", 0, 20, this.session.bodyLetterSpacing(), value9 -> {
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
        }), 0);
        updateDisplayPreview(livePreview, previewTitle, previewBody, previewAction, this.session.uiScale(), this.session.textScale(), this.session.pagePadding());
        previewBody.setTextSize((13 * this.session.bodyTextScale()) / 100.0f);
        Compat.setLetterSpacing(previewBody, this.session.bodyLetterSpacing() / 200.0f);
        previewBody.setPadding(0, dp(this.session.bodyParagraphSpacing()), 0, 0);
        previewBody.setLineSpacing(0.0f, this.session.bodyLineSpacing() / 100.0f);
        linearLayout.addView(panel);
        addSectionLabel(linearLayout, "颜色主题");
        panel = card();
        LinearLayout themeGrid = vertical(0);
        for (int start = 0; start < THEME_NAMES.length; start += 6) {
            LinearLayout row = new LinearLayout(this);
            for (int i = start; i < start + 6; i++) {
                LinearLayout cell = new LinearLayout(this);
                cell.setGravity(17);
                if (i < THEME_NAMES.length) {
                    cell.addView(themeSwatch(i), new LinearLayout.LayoutParams(dp(34), dp(34)));
                }
                row.addView(cell, new LinearLayout.LayoutParams(0, dp(40), 1.0f));
            }
            addTop(themeGrid, row, start == 0 ? 2 : 4);
        }
        panel.addView(themeGrid);
        TextView themeCaption = text(currentThemeCaption(), 10.5f, this.MUTED);
        addTop(panel, themeCaption, 2);
        Button save = button("保存显示设置", R.drawable.ic_save);
        save.setOnClickListener(view2 -> {
            Integer ui = parseNumber(uiScale.input, 70, 160);
            Integer text = parseNumber(textScale.input, 70, 180);
            Integer pad = parseNumber(padding.input, 0, 30);
            Integer insetH = parseNumber(screenPaddingH[0].input, 0, 30);
            Integer insetV = parseNumber(screenPaddingV[0].input, 0, 30);
            Integer bodySize = parseNumber(bodyText.input, 75, 170);
            Integer letters = parseNumber(letterSpacing.input, 0, 20);
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
        addSettingEntry(panel, "恢复默认设置", "主题、字体、间距与界面大小全部还原", R.drawable.il_refresh, () -> {
            showLiteDialog("恢复默认显示设置", "主题、字体、间距和界面大小都将恢复为默认值", "恢复", () -> {
                this.session.resetDisplaySettings();
                applyPalette();
                Compat.colorSystemBars(getWindow(), this.BG);
                buildShell();
                showDisplaySettings();
                toast("已恢复默认显示设置");
            }, "取消", null, null, null);
        });
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
        feedCard.addView(articleBadge, new LinearLayout.LayoutParams(dp(42), dp(20)));
        TextView previewTitle = text("方屏上的社区，也可以清晰又从容", 15.0f, this.TEXT);
        previewTitle.setTypeface(appRegularTypeface(), 1);
        addTop(feedCard, previewTitle, 6);
        TextView summary = text("这是一条帖子列表摘要，用来观察整体字号、卡片间距和主题颜色", 11.0f, this.MUTED);
        summary.setLineSpacing(0.0f, 1.12f);
        addTop(feedCard, summary, 5);
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
        LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(dp(2), -1);
        railParams.rightMargin = dp(8);
        reply.addView(rail, railParams);
        TextView second = text("二级评论使用稍轻的字重，并通过主题色竖线建立层级", 12.0f, this.MUTED);
        second.setLineSpacing(0.0f, 1.16f);
        reply.addView(second, new LinearLayout.LayoutParams(0, -2, 1.0f));
        addTop(comments, reply, 7);
        addTop(linearLayout, comments, 7);
        Button backToSettings = button("返回继续调整", R.drawable.ic_arrow_back);
        backToSettings.setOnClickListener(view -> {
            showDisplaySettings();
        });
        addTop(linearLayout, backToSettings, 9);
    }

    /** 主题色点：对角双色圆，选中时外圈亮环 + 白色对钩。 */
    private View themeSwatch(int index) {
        final int primary = THEME_COLORS[index][0];
        final int secondary = THEME_COLORS[index][1];
        final boolean selected = currentPrimary() == primary && currentSecondary() == secondary;
        View swatch = new View(this) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF rect = new RectF();

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float cx = getWidth() / 2.0f;
                float cy = getHeight() / 2.0f;
                float radius = dp(12);
                rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(primary);
                canvas.drawArc(rect, 135.0f, 180.0f, true, paint);
                paint.setColor(secondary);
                canvas.drawArc(rect, 315.0f, 180.0f, true, paint);
                if (!selected) return;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setColor(secondary);
                canvas.drawCircle(cx, cy, radius + dp(3), paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.WHITE);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(dp(12));
                paint.setFakeBoldText(true);
                canvas.drawText("✓", cx, cy + dp(4), paint);
                paint.setFakeBoldText(false);
                paint.setTextAlign(Paint.Align.LEFT);
            }
        };
        swatch.setContentDescription(THEME_NAMES[index]);
        swatch.setOnClickListener(view -> {
            this.session.setTheme(Format.colorHex(primary), Format.colorHex(secondary));
            applyPalette();
            Compat.colorSystemBars(getWindow(), this.BG);
            buildShell();
            showDisplaySettings();
        });
        return swatch;
    }

    private String currentThemeCaption() {
        for (int i = 0; i < THEME_NAMES.length; i++) {
            if (currentPrimary() == THEME_COLORS[i][0] && currentSecondary() == THEME_COLORS[i][1]) {
                return "当前 · " + THEME_NAMES[i];
            }
        }
        return "当前 · 自定义配色";
    }

    private void showAppSettings() {
        LinearLayout page = settingsPage("app_settings", "内容与缓存");
        addSectionLabel(page, "浏览与交互");
        LinearLayout panel = settingsList();
        addTop(panel, toggleRow("无图模式", this.session.noImage(), value -> {
            this.session.setNoImage(value);
            this.feed.clear();
            invalidateFeedView();
        }), 0);
        addSettingEntry(panel, "网络模式", networkModeLabel(), R.drawable.il_globe,
                this::showNetworkModePicker);
        boolean zShellBackSwipe = this.session.shellBackSwipe();
        SessionStore sessionStore2 = this.session;
        Objects.requireNonNull(sessionStore2);
        addTop(panel, toggleRow("右滑返回上一级", zShellBackSwipe,
                sessionStore2::setShellBackSwipe), 0);
        addTop(panel, toggleRow("退出确认", this.session.confirmExitOnBack(),
                this.session::setConfirmExitOnBack), 0);
        boolean zRememberDetailScroll = this.session.rememberDetailScroll();
        SessionStore sessionStore3 = this.session;
        Objects.requireNonNull(sessionStore3);
        addTop(panel, toggleRow("记住帖子阅读位置", zRememberDetailScroll, sessionStore3::setRememberDetailScroll), 0);
        addTop(panel, toggleRow("自动清理", "开启后自动清理 30 天前的离线缓存",
                this.session.autoOfflineCleanup(), value -> {
                    this.session.setAutoOfflineCleanup(value);
                    if (value) pruneOfflineCache(null);
                }), 0);
        boolean zDoubleTapCommentReply = this.session.doubleTapCommentReply();
        SessionStore sessionStore4 = this.session;
        Objects.requireNonNull(sessionStore4);
        addTop(panel, toggleRow("双击评论回复", zDoubleTapCommentReply, sessionStore4::setDoubleTapCommentReply), 0);
        page.addView(panel);
        addSectionLabel(page, "内容过滤");
        LinearLayout filter = settingsList();
        TextView filterLabel = text("屏蔽关键词（逗号分隔）", 11.0f, this.MUTED);
        addTop(filter, filterLabel, 2);
        EditText blockKeywords = new EditText(this);
        blockKeywords.setText(this.session.blockKeywords());
        blockKeywords.setTextColor(this.TEXT);
        blockKeywords.setTextSize(sp(11.0f));
        blockKeywords.setSingleLine(false);
        blockKeywords.setMinLines(2);
        blockKeywords.setGravity(48);
        Compat.tint(blockKeywords, this.themeTokens.accent);
        blockKeywords.setPadding(dp(8), dp(6), dp(8), dp(6));
        Compat.setBackground(blockKeywords, roundStroke(this.themeTokens.panelElevated, 8,
                this.themeTokens.hairline, 1));
        addTop(filter, blockKeywords, 5);
        Button saveFilter = button("保存内容过滤", R.drawable.ic_save);
        saveFilter.setOnClickListener(view -> {
            this.session.setBlockKeywords(blockKeywords.getText().toString());
            this.feed.clear();
            this.feedOffset = 0;
            invalidateFeedView();
            toast("内容过滤已保存");
        });
        addTop(filter, saveFilter, 9);
        page.addView(filter);
        addSectionLabel(page, "维护");
        LinearLayout maintain = settingsList();
        final TextView[] pruneDesc = new TextView[1];
        pruneDesc[0] = addSettingEntry(maintain, "清理过期离线内容", offlineSummary(), R.drawable.il_cleanup, () ->
                pruneOfflineCache(() -> {
                    if (pruneDesc[0] != null) pruneDesc[0].setText(offlineSummary());
                    toast("过期离线内容已清理");
                }));
        addSettingEntry(maintain, "导出日志", "生成诊断文件用于反馈问题", R.drawable.il_scroll,
                this::exportDiagnostics);
        addSettingEntry(maintain, "运行自检", "检查网络、缓存、登录与更新服务", R.drawable.il_info,
                this::runSelfTest);
        final TextView[] cacheDesc = new TextView[1];
        cacheDesc[0] = addSettingEntry(maintain, "清除缓存", "临时文件与图片缓存 " + Format.cacheMb(cacheBytes()),
                R.drawable.il_cleanup, () -> {
                    long before = tempCacheBytes();
                    long imageBefore = ((long) ImageLoader.cacheSizeKb()) * 1024;
                    clearTempCacheFiles(getCacheDir());
                    EmojiRenderer.clear();
                    ImageLoader.clear();
                    if (cacheDesc[0] != null) {
                        cacheDesc[0].setText("临时文件与图片缓存 " + Format.cacheMb(cacheBytes()));
                    }
                    toast("已清除缓存 " + Format.cacheMb(before + imageBefore));
                });
        addSettingEntry(maintain, this.session.isLoggedIn() ? "退出登录" : "二维码登录",
                this.session.isLoggedIn() ? "当前账号 ID " + this.session.userId() : "扫码登录小黑盒账号",
                this.session.isLoggedIn() ? R.drawable.ic_logout : R.drawable.il_qr, () -> {
                    if (this.session.isLoggedIn()) {
                        this.session.clearSession();
                        this.feed.clear();
                        invalidateFeedView();
                        toast("已退出登录");
                    }
                    showLogin();
                });
        page.addView(maintain);
    }

    private String offlineSummary() {
        return "离线缓存 " + Format.cacheMb(this.localCache.offlineBytes())
                + " · 已缓存帖子 " + this.localCache.detailCount();
    }

    private void showStartupSettings() {
        LinearLayout page = settingsPage("startup_settings", "启动与更新");
        LinearLayout panel = settingsList();
        boolean[] autoUpdate = {this.session.autoUpdateCheck()};
        boolean[] splashEnabled = {this.session.splashEnabled()};
        addTop(panel, toggleRow("进入软件时检查更新", autoUpdate[0], value -> {
            autoUpdate[0] = value;
        }), 0);
        addTop(panel, toggleRow("显示开屏动画", splashEnabled[0], value2 -> {
            splashEnabled[0] = value2;
        }), 0);
        EditText splashText = textField(panel, "开屏文字", this.session.splashText());
        ScaleControl duration = settingSlider(panel, "开屏时长", "ms", 500, 2600, this.session.splashDuration(), value3 -> {
        });
        addSettingEntry(panel, "预览开屏动画", "试运行一次当前开屏效果", R.drawable.il_eye, () ->
                showSplashPreview(splashText.getText().toString().trim(),
                        parseNumber(duration.input, 500, 2600)));
        Button save = button("保存启动设置", R.drawable.ic_save);
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
        Runnable animation = new Runnable() {
            @Override
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
        PullRefreshListView list = new PullRefreshListView(this, this.BG, this.MUTED,
                this.SECONDARY, this.session.uiScale() / 100.0f,
                this.session.textScale() / 100.0f);
        list.setBackgroundColor(this.BG);
        list.setDivider(null);
        list.setSelector(new ColorDrawable(0));
        list.setCacheColorHint(0);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(12));
        AnnouncementListAdapter adapter = new AnnouncementListAdapter(this, this.themeTokens,
                this.session.uiScale() / 100.0f, this.session.textScale() / 100.0f,
                this::showAnnouncementDialog);
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

    private void loadAnnouncementsInto(final PullRefreshListView list, final AnnouncementListAdapter adapter) {
        if (list == null || adapter == null) {
            return;
        }
        AnnouncementChecker.load(new AnnouncementChecker.Callback() {
            @Override
            public void onResult(List<AnnouncementChecker.Item> items) {
                if (MainActivity.this.isFinishing() || !"announcement_board".equals(MainActivity.this.screen)) {
                    return;
                }
                list.setRefreshing(false);
                adapter.setItems(MainActivity.this.withWelcomeAnnouncement(items));
            }

            @Override
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
        AnnouncementChecker.load(new AnnouncementChecker.Callback() {
            @Override
            public void onResult(List<AnnouncementChecker.Item> items) {
                if (MainActivity.this.isFinishing() || !"announcement_board".equals(MainActivity.this.screen)) {
                    return;
                }
                MainActivity.this.renderAnnouncementList(items);
            }

            @Override
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
                    String updatedAt = Format.announcementTime(item.updatedAt);
                    if (!TextUtils.isEmpty(updatedAt)) {
                        addTop(card, text(updatedAt, 10.0f, this.MUTED), 4);
                    }
                    TextView preview = text(Format.announcementPreview(item.content), 12.0f, this.MUTED);
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
    private void showAbout() {
        LinearLayout page = settingsPage("about", "关于");
        addSectionLabel(page, "应用");
        LinearLayout panel = settingsList();
        TextView appName = text("heybox Lite", 20.0f, this.TEXT);
        appName.setTypeface(appRegularTypeface(), 1);
        panel.addView(appName);
        addTop(panel, text("版本 " + appVersion(), 13.0f, this.MUTED), 6);
        addTop(panel, text("开发者：Ronan", 13.0f, this.TEXT), 5);
        addTop(panel, text("2.0 正式版支持 Android 7.0 及以上系统", 12.0f, this.MUTED), 5);
        TextView basedOn = text("基于 HeyWear 进行二次开发与方屏适配，非官方应用", 12.0f, this.TEXT);
        basedOn.setLineSpacing(0.0f, 1.18f);
        addTop(panel, basedOn, 7);
        TextView disclaimer = text("免责声明：本项目仅用于学习、研究与个人使用，不代表小黑盒、HeyWear 或相关官方立场。本项目基于 HeyWear 进行二次开发与适配，开发者不对因使用本项目造成的账号、数据、设备或其他风险承担额外责任。请在遵守相关法律法规及平台规则的前提下使用", 11.0f, this.MUTED);
        disclaimer.setLineSpacing(0.0f, 1.22f);
        addTop(panel, disclaimer, 10);
        page.addView(panel);
        addSectionLabel(page, "支持");
        LinearLayout actions = settingsList();
        addSettingEntry(actions, "群二维码", "QQ 群 781941517，扫码进交流群", R.drawable.il_qr,
                this::showFeedbackGroupQr);
        addSettingEntry(actions, "公告列表", "历史公告与更新说明", R.drawable.il_info,
                this::showAnnouncementsV2);
        final UpdateChecker.Result[] found = {null};
        final boolean[] checking = {false};
        final TextView[] updateDesc = new TextView[1];
        updateDesc[0] = addSettingEntry(actions, "检查更新", "当前版本 " + appVersion(),
                R.drawable.il_update, () -> {
                    if (found[0] != null) {
                        rememberTestRelease(found[0]);
                        openUpdateUrl(found[0].downloadUrl.isEmpty()
                                ? found[0].releaseUrl : found[0].downloadUrl);
                        return;
                    }
                    if (checking[0]) {
                        return;
                    }
                    checking[0] = true;
                    if (updateDesc[0] != null) updateDesc[0].setText("正在检查更新…");
                    UpdateChecker.check(appVersion(), MainActivity.this.session.userId(),
                            MainActivity.this.session.testReleaseId(), new UpdateChecker.Callback() {
                        @Override
                        public void onResult(UpdateChecker.Result result) {
                            if (MainActivity.this.isFinishing()) {
                                return;
                            }
                            checking[0] = false;
                            if (result.updateAvailable) {
                                found[0] = result;
                                MainActivity.this.showUpdateDialog(result);
                                if (updateDesc[0] != null) {
                                    updateDesc[0].setText("发现新版 " + result.version + "，点按前往下载");
                                }
                                return;
                            }
                            if (updateDesc[0] != null) updateDesc[0].setText("当前已是最新版");
                        }

                        @Override
                        public void onError(String message) {
                            if (MainActivity.this.isFinishing()) {
                                return;
                            }
                            checking[0] = false;
                            if (updateDesc[0] != null) updateDesc[0].setText("检查失败：" + message);
                        }
                    });
                });
        addSettingEntry(actions, "打开 GitHub 项目", "huanghao897/heybox-lite", R.drawable.il_globe, () ->
                openUrl("https://github.com/huanghao897/heybox-lite"));
        page.addView(actions);
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
        addTop(box, group, 5);
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
        return toggleRow(label, "", initial, listener);
    }

    /** 统一行式开关：图标芯片 + 标题（可带副标题）+ 滑块，底部带缩进分隔线。 */
    private LinearLayout toggleRow(String label, String description,
                                   boolean initial, ToggleListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(1);
        LinearLayout content = new LinearLayout(this);
        content.setGravity(16);
        content.setPadding(dp(6), dp(9), dp(6), dp(9));

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(5), dp(5), dp(5), dp(5));
        Drawable drawable = Compat.tintedDrawable(this, settingToggleIcon(label), this.themeTokens.text);
        if (drawable != null) {
            icon.setImageDrawable(drawable);
        }
        Compat.setBackground(icon, UiComponents.monoChip(this, this.themeTokens,
                this.session.uiScale() / 100.0f));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(27), dp(27));
        iconParams.rightMargin = dp(12);
        content.addView(icon, iconParams);

        LinearLayout copy = vertical(0);
        TextView title = text(label, 13.5f, this.TEXT);
        title.setTypeface(appRegularTypeface(), Typeface.BOLD);
        copy.addView(title);
        if (description != null && !description.isEmpty()) {
            TextView desc = text(description, 10.5f, this.MUTED);
            desc.setPadding(0, dp(1), 0, 0);
            copy.addView(desc);
        }
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        copyParams.rightMargin = dp(8);
        content.addView(copy, copyParams);

        boolean[] value = {initial};
        FrameLayout toggle = new FrameLayout(this);
        View thumb = new View(this);
        Compat.setBackground(thumb, round(Color.WHITE, 9));
        FrameLayout.LayoutParams thumbParams = new FrameLayout.LayoutParams(dp(18), dp(18), 16);
        thumbParams.leftMargin = dp(4);
        toggle.addView(thumb, thumbParams);
        final int travel = dp(16);
        Runnable paintTrack = () -> Compat.setBackground(toggle, round(value[0]
                ? this.themeTokens.accent
                : (this.session.darkMode() ? Color.rgb(51, 55, 62) : Color.rgb(203, 208, 214)), 13));
        paintTrack.run();
        thumb.setTranslationX(value[0] ? travel : 0.0f);
        content.addView(toggle, new LinearLayout.LayoutParams(dp(42), dp(26)));
        content.setMinimumHeight(dp(52));
        row.addView(content, new LinearLayout.LayoutParams(-1, -2));

        View divider = new View(this);
        divider.setBackgroundColor(this.session.darkMode()
                ? Color.argb(16, 255, 255, 255) : Color.argb(14, 0, 0, 0));
        LinearLayout.LayoutParams dividerParams =
                new LinearLayout.LayoutParams(-1, Math.max(1, dp(1) / 2));
        dividerParams.leftMargin = dp(45);
        row.addView(divider, dividerParams);
        content.setOnClickListener(view -> {
            value[0] = !value[0];
            paintTrack.run();
            thumb.animate().cancel();
            thumb.animate().translationX(value[0] ? travel : 0.0f)
                    .setDuration(MotionSpec.ENTER_MS)
                    .setInterpolator(MotionSpec.EASE_OUT)
                    .start();
            listener.onChanged(value[0]);
        });
        return row;
    }

    private int settingToggleIcon(String label) {
        if (label.contains("夜间")) return R.drawable.il_sun;
        if (label.contains("圆屏")) return R.drawable.il_round_screen;
        if (label.contains("加粗")) return R.drawable.il_bold;
        if (label.contains("无图")) return R.drawable.il_image;
        if (label.contains("动图")) return R.drawable.il_gif;
        if (label.contains("原图")) return R.drawable.il_zoom;
        if (label.contains("退出确认")) return R.drawable.il_gesture_block;
        if (label.contains("右滑")) return R.drawable.il_swipe;
        if (label.contains("阅读位置")) return R.drawable.il_scroll;
        if (label.contains("清理")) return R.drawable.il_cleanup;
        if (label.contains("评论回复")) return R.drawable.il_reply;
        if (label.contains("更新")) return R.drawable.il_update;
        if (label.contains("开屏")) return R.drawable.il_splash;
        return R.drawable.il_settings;
    }

    /** 两行式滑杆：标签与数值同一行（数值点按可键入），通栏轨道在下，白钮与开关圆钮同族。 */
    private ScaleControl settingSlider(LinearLayout parent, String label, String unit, final int min, int max, int current, final IntListener listener) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(1);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(16);
        TextView name = text(label, 11.0f, this.TEXT);
        head.addView(name, new LinearLayout.LayoutParams(0, -2, 1.0f));
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(String.valueOf(current));
        input.setTextSize(sp(11.5f));
        input.setTextColor(this.TEXT);
        input.setTypeface(appRegularTypeface(), Typeface.BOLD);
        input.setGravity(21);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setPadding(dp(6), dp(2), 0, dp(2));
        input.setMinWidth(dp(30));
        Compat.setBackground(input, null);
        head.addView(input, new LinearLayout.LayoutParams(-2, -2));
        if (unit != null && !unit.isEmpty()) {
            TextView suffix = text(unit, 8.5f, this.MUTED);
            LinearLayout.LayoutParams suffixParams = new LinearLayout.LayoutParams(-2, -2);
            suffixParams.leftMargin = dp(2);
            head.addView(suffix, suffixParams);
        }
        wrap.addView(head, new LinearLayout.LayoutParams(-1, -2));
        SeekBar slider = new SeekBar(this);
        slider.setMax(max - min);
        slider.setProgress(Math.max(0, Math.min(max - min, current - min)));
        Compat.tint(slider, this.themeTokens.accent,
                this.session.darkMode() ? Color.WHITE : this.themeTokens.accent);
        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(-1, dp(26));
        sliderParams.topMargin = dp(1);
        wrap.addView(slider, sliderParams);
        addTop(parent, wrap, 6);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = min + progress;
                input.setText(String.valueOf(value));
                input.setSelection(input.length());
                listener.onChanged(value);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
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
            int vertical = Math.max(5, Math.round(7.0f * scale));
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

    private static int contrast(int color) {
        return ThemeTokens.contrast(color);
    }

    private static int blend(int base, int overlay, float amount) {
        return ThemeTokens.blend(base, overlay, amount);
    }

    private String appVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.70";
        }
    }

    private void openImage(ImageView source, String url) {
        openImage(source, new String[]{url}, 0);
    }

    /** 帖子多图：把整组图和当前索引交给查看器，放大后可左右滑动切换。 */
    private void openImage(ImageView source, String[] urls, int index) {
        int[] location = new int[2];
        source.getLocationOnScreen(location);
        String current = urls.length > 0 ? urls[Math.max(0, Math.min(urls.length - 1, index))] : "";
        Drawable drawable = source.getDrawable();
        if (drawable instanceof BitmapDrawable) {
            ImageViewerActivity.preparePreview(current, ((BitmapDrawable) drawable).getBitmap());
        }
        Intent intent = new Intent(this, (Class<?>) ImageViewerActivity.class);
        intent.putExtra("image_url", current);
        if (urls.length > 1) {
            intent.putExtra("image_urls", urls);
            intent.putExtra("image_index", index);
        }
        intent.putExtra("origin_x", location[0] + (source.getWidth() / 2));
        intent.putExtra("origin_y", location[1] + (source.getHeight() / 2));
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
        try {
            File ready = UpdateDownloadClient.download(this, url, appVersion(),
                    (percent, indeterminate) -> this.handler.post(() -> {
                        if (isFinishing()) return;
                        progress.setIndeterminate(indeterminate);
                        state.setText(indeterminate ? "正在下载..." : "已下载" + percent + "%");
                        if (!indeterminate) progress.setProgress(percent);
                    }));
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

    private String networkModeLabel() {
        int mode = this.session.networkMode();
        return mode == 0 ? "省流量" : mode == 2 ? "原图" : "标准";
    }

    private void showNetworkModePicker() {
        showLiteDialog("网络模式", "省流量：缩略图且不播放动图\n标准：缩略图并播放动图\n原图：优先加载高清图片",
                "标准", () -> setNetworkMode(1),
                "省流量", () -> setNetworkMode(0),
                "原图", () -> setNetworkMode(2));
    }

    private void setNetworkMode(int mode) {
        this.session.setNetworkMode(mode);
        this.feed.clear();
        invalidateFeedView();
        toast("已切换为" + networkModeLabel());
        if ("app_settings".equals(this.screen)) showAppSettings();
    }

    private void runSelfTest() {
        toast("正在自检");
        DiagnosticsClient.selfTest(this, this.session, (success, report) -> {
            if (isFinishing()) return;
            showLiteDialog(success ? "自检完成" : "自检发现问题", report,
                    "提交诊断", () -> DiagnosticsClient.upload(this.session, report,
                            (uploaded, message) -> toast(message)),
                    "知道了", null,
                    "导出日志", this::exportDiagnostics);
        });
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
        if (!DiagnosticsExporter.share(this, file)) {
            toast("没有可用的分享应用，日志已保存：" + file.getAbsolutePath());
        }
    }

    private void saveDiagnosticsToDownloads(String fileName, String diagnostics) {
        String path = DiagnosticsExporter.save(this, fileName, diagnostics);
        toast(path == null ? "保存失败" : "已保存到 " + path);
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

    private String buildDetailDiagnostics(JSONObject body, FeedItem fallback, JSONObject link,
                                          JSONArray fallbackImages, JSONArray comments) {
        StringBuilder out = new StringBuilder();
        out.append("detail screen: ").append(this.screen).append('\n');
        out.append("currentLinkId: ").append(this.currentLinkId).append('\n');
        out.append("fallbackId: ").append(fallback == null ? "" : fallback.id).append('\n');
        out.append("fallbackTitle: ").append(compactLogText(fallback == null ? "" : fallback.title, 180)).append('\n');
        out.append("fallbackArticle: ").append(fallback != null && fallback.article).append('\n');
        out.append("playGif: ").append(this.session.playGif()).append('\n');
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
        appendCommentDiagnostics(out, comments);
        return out.toString();
    }

    private void appendCommentDiagnostics(StringBuilder out, JSONArray groups) {
        out.append("\ncomment diagnostics:\n");
        if (groups == null) {
            out.append("groups: null\n");
            return;
        }
        out.append("groups: ").append(groups.length()).append('\n');
        int limit = Math.min(groups.length(), 5);
        for (int i = 0; i < limit; i++) {
            JSONObject group = groups.optJSONObject(i);
            JSONArray thread = group == null ? null : group.optJSONArray("comment");
            JSONObject comment = thread == null ? group : thread.optJSONObject(0);
            if (comment == null) continue;
            String parsed = RichContent.commentText(comment.optString("text"),
                    comment.optString("content"), comment.optString("html"),
                    comment.optString("description"), comment.optString("desc_extra"),
                    comment.optString("rich_text"), comment.optString("hb_rich_texts"));
            out.append('[').append(i).append("] id=")
                    .append(CommentData.commentId(comment))
                    .append(" parsed=").append(compactLogText(parsed, 180)).append('\n');
            out.append("  text=").append(compactLogText(comment.optString("text"), 180)).append('\n');
            List<CommentData.CommentImage> images = CommentData.commentImages(comment);
            out.append("  images=").append(images.size());
            for (CommentData.CommentImage image : images) {
                out.append(" [mime=").append(image.mimeType)
                        .append(" animated=").append(image.animated)
                        .append(" url=").append(compactLogText(image.url, 100)).append(']');
            }
            out.append('\n');
        }
    }

    private String compactLogText(String value, int max) {
        String clean = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, Math.max(0, max)) + "...";
    }
    private static final class ScaleControl {
        final EditText input;
        final SeekBar slider;

        ScaleControl(EditText input, SeekBar slider) {
            this.input = input;
            this.slider = slider;
        }
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
        Compat.tint(input, this.themeTokens.accent);
        input.setPadding(dp(8), dp(4), dp(8), dp(4));
        Compat.setBackground(input, roundStroke(this.themeTokens.panelElevated, 8,
                this.themeTokens.hairline, 1));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(dp(96), -2);
        inputParams.leftMargin = dp(6);
        row.addView(input, inputParams);
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

    @Override
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
        if ("reading_center".equals(this.screen)) {
            showProfile();
            return;
        }
        if ("reading_stats".equals(this.screen)) {
            showReadingCenter();
            return;
        }
        if (!"saved".equals(this.screen)) {
            if (!"announcement_board".equals(this.screen)) {
                if (!"display_preview".equals(this.screen)) {
                    if (!"display_settings".equals(this.screen) && !"startup_settings".equals(this.screen) && !"app_settings".equals(this.screen) && !"about".equals(this.screen)) {
                        if (!"settings_home".equals(this.screen)) {
                            if ("feed".equals(this.screen)) {
                                if (this.session.confirmExitOnBack()) {
                                    long now = System.currentTimeMillis();
                                    if (now - this.lastExitBackAt > 2000L) {
                                        this.lastExitBackAt = now;
                                        toast("再按一次退出");
                                        return;
                                    }
                                }
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
        returnFromSavedPage();
    }

    private void returnFromDetail() {
        returnFromDetail(false);
    }

    private void returnFromDetailGesture() {
        returnFromDetail(true);
    }

    private void returnFromDetail(boolean gestureOwned) {
        if (this.readingTimeTracker != null) {
            this.readingTimeTracker.pause();
            updateReadingTimeEntry();
        }
        saveCurrentDetailProgress();
        this.detailRequestToken++;
        View returnView = this.detailPager == null ? this.detailReturnView
                : this.detailPager.takeReturnView();
        this.detailPager = null;
        this.detailScroll = null;
        this.detailReturnView = returnView;
        if (restoreDetailReturnView(!gestureOwned)) {
            return;
        }
        this.pendingBackTransition = !gestureOwned;
        if (gestureOwned) {
            this.pageTransitions.finishNow();
            this.content.removeAllViews();
        }
        if ("saved".equals(this.detailReturn)) {
            if ("reading_center".equals(this.savedReturnScreen)) showReadingCenter();
            else showProfile();
            return;
        }
        if ("reading_center".equals(this.detailReturn)) {
            showReadingCenter();
            return;
        }
        if ("search".equals(this.detailReturn)) {
            showSearch(true);
            return;
        }
        showFeed();
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
        } else if ("profile".equals(returnScreen)) {
            showProfile();
        } else if ("reading_center".equals(returnScreen)) {
            showReadingCenter();
        } else {
            showFeed();
        }
    }

    private void returnFromSavedPage() {
        this.pendingBackTransition = true;
        if ("reading_center".equals(this.savedReturnScreen)) {
            showReadingCenter();
        } else {
            showProfile();
        }
    }

    private boolean shouldKeepDetailReturnView(String screenKey) {
        return "feed".equals(screenKey) || "search".equals(screenKey)
                || "saved".equals(screenKey) || "reading_center".equals(screenKey)
                || "user_space".equals(screenKey);
    }

    private boolean restoreDetailReturnView(boolean animate) {
        this.pendingBackTransition = false;
        this.pageTransitions.finishNow();
        if (this.detailReturnView == null || !shouldKeepDetailReturnView(this.detailReturn)) {
            this.detailReturnView = null;
            return false;
        }
        View view = this.detailReturnView;
        this.detailReturnView = null;
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
        this.screen = this.detailReturn;
        configureDetailReturnChrome();
        ensurePageBackdrop(view);
        if (animate) this.pageTransitions.run(this.content, view, false);
        else {
            this.content.removeAllViews();
            this.content.addView(view, match());
        }
        // 离屏期间列表若收到过 notifyDataSetChanged（如加载更多回包），重挂载会丢滚动位置，这里强制回到进详情前的位置
        if ("search".equals(this.screen) && this.searchListView != null) {
            final ListView list = this.searchListView;
            final int position = this.searchState.listPosition();
            final int offset = this.searchState.listTopOffset();
            list.post(() -> {
                if (list.getWindowToken() != null) {
                    list.setSelectionFromTop(position, offset);
                }
            });
        }
        if ("feed".equals(this.screen)) restoreFeedScroll();
        return true;
    }

    private void configureDetailReturnChrome() {
        if ("feed".equals(this.screen)) {
            activate("feed");
            this.title.setText("社区");
            this.action.setVisibility(4);
            return;
        }
        setBottomNavVisible(false);
        this.title.setText(this.detailReturnTitle == null ? "" : this.detailReturnTitle);
        this.leading.setVisibility(0);
        this.leading.setOnClickListener(v -> onBackPressed());
        this.action.setVisibility(4);
        if ("user_space".equals(this.screen)) this.title.setText("个人主页");
    }

    private void saveCurrentDetailProgress() {
        if (!"detail".equals(this.screen) || this.detailScroll == null || this.currentLinkId.isEmpty() || this.localCache == null || !this.session.rememberDetailScroll()) {
            return;
        }
        this.localCache.saveScroll(this.currentLinkId, this.detailScroll.getScrollY());
    }

    @Override
    protected void onResume() {
        super.onResume();
        this.activityResumed = true;
        if (this.accountBlockedScreen) {
            PresenceReporter.pingNow(this.session, this.readingTimeTracker,
                    this::applyAccessStatus);
            return;
        }
        if ("detail".equals(this.screen) && this.currentDetailBody != null
                && this.currentDetailItem != null && this.readingTimeTracker != null) {
            this.readingTimeTracker.start(this.currentDetailItem.article, this.currentDetailItem.id);
        }
        PresenceReporter.ping(this.session, this.readingTimeTracker, this::applyAccessStatus);
        this.handler.removeCallbacks(this.presenceTick);
        this.handler.postDelayed(this.presenceTick, 600_000L);
    }

    @Override
    protected void onPause() {
        this.activityResumed = false;
        if (this.readingTimeTracker != null) this.readingTimeTracker.pause();
        this.handler.removeCallbacks(this.presenceTick);
        saveCurrentDetailProgress();
        super.onPause();
    }

    /** 前台在线心跳每 10 分钟一次，退后台即停止。 */
    private final Runnable presenceTick = new Runnable() {
        @Override
        public void run() {
            PresenceReporter.ping(MainActivity.this.session, MainActivity.this.readingTimeTracker,
                    MainActivity.this::applyAccessStatus);
            MainActivity.this.handler.postDelayed(this, 600_000L);
        }
    };

    @Override
    protected void onDestroy() {
        if (this.readingTimeTracker != null) this.readingTimeTracker.pause();
        saveCurrentDetailProgress();
        stopQrPolling();
        this.pageTransitions.cancelNow();
        if (this.detailPager != null) this.detailPager.cancelMotion();
        if (this.content instanceof BackSwipeFrameLayout) {
            ((BackSwipeFrameLayout) this.content).cancelMotion();
        }
        ImageLoader.cancelTree(this.content);
        recycleSnapshots(this.screenSnapshots);
        recycleSnapshots(this.fullScreenSnapshots);
        this.retainedPages.clear();
        if (this.writeActions != null) this.writeActions.close();
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
        if (!"detail".equals(this.screen) && this.readingTimeTracker != null) {
            this.readingTimeTracker.pause();
        }
        boolean back = this.pendingBackTransition;
        boolean push = this.pendingLateralPush;
        this.pendingBackTransition = false;
        this.pendingLateralPush = false;
        ensurePageBackdrop(next);
        if (this.shellAnimating) {
            this.pageTransitions.finishNow();
            this.content.removeAllViews();
            this.content.addView(next, match());
            return;
        }
        this.pageTransitions.run(this.content, next, !back, push);
    }

    /** 页面不满屏时下半截透明，转场重叠期会透出旧页并在结束时闪变，这里统一兜底成不透明底色。 */
    private void ensurePageBackdrop(View view) {
        if (view == null) return;
        Drawable bg = view.getBackground();
        if (bg == null || (bg instanceof ColorDrawable
                && Color.alpha(((ColorDrawable) bg).getColor()) == 0)) {
            view.setBackgroundColor(this.BG);
        }
    }

    private void showLoading() {
        hideLoading();
        LoadingSpinnerView progress = new LoadingSpinnerView(this);
        progress.setTag("loading");
        progress.setColor(this.PRIMARY);
        this.content.addView(progress, new FrameLayout.LayoutParams(dp(38), dp(38), 17));
    }

    private View detailLoadingPage() {
        FrameLayout page = new FrameLayout(this);
        page.setTag("detail_loading");
        page.setBackgroundColor(this.BG);
        LoadingSpinnerView progress = new LoadingSpinnerView(this);
        progress.setColor(this.PRIMARY);
        page.addView(progress, new FrameLayout.LayoutParams(dp(38), dp(38), 17));
        return page;
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
        Compat.setBackground(card, UiComponents.groupCard(this, tokens, this.session == null ? 1.0f : this.session.uiScale() / 100.0f));
        return card;
    }

    private LinearLayout settingsList() {
        return card();
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

    private Button button(String value) {
        return button(value, 0);
    }

    /** 主按钮：主题色粗体文字行——无底无框，全屏唯一的彩色本身就是按钮。 */
    @SuppressLint("ClickableViewAccessibility")
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
        button.setTextColor(tokens.accent);
        button.setAllCaps(false);
        button.setTypeface(appRegularTypeface(), Typeface.BOLD);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setGravity(17);
        button.setMinHeight(dp(40));
        button.setMinimumHeight(dp(40));
        if (Build.VERSION.SDK_INT >= 21) {
            button.setStateListAnimator(null);
        }
        Compat.setBackground(button, null);
        button.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                UiComponents.press(view);
            }
            return false;
        });
        if (iconRes != 0) {
            setLeftIcon(button, iconRes, tokens.accent, 15);
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
        if (this.shellAnimating || this.pageTransitions.isRunning()) {
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
        if (!Motions.off() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
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
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

}
