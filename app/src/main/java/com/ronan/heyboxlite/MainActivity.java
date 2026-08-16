package com.ronan.heyboxlite;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@SuppressLint("WrongConstant")
public final class MainActivity extends Activity implements BackSwipeFrameLayout.Host {
    private static final int AXIS_ROTARY_SCROLL = 26;
    private static final int REQUEST_CHECKIN_CAPTCHA = 9134;
    private static final String MSG_OFFLINE_CACHE = "已显示离线缓存";
    private int BG;
    private int PANEL;
    private int TEXT;
    private int MUTED;
    private int PRIMARY;
    private int SECONDARY;
    private ThemeTokens themeTokens;
    private SettingsUi settingsUi;
    private LiteDialogPresenter liteDialogs;
    private DisplaySettingsPage displaySettingsPage;
    private AppSettingsPage appSettingsPage;
    private NoticeCenter noticeCenter;
    private UpdateInstaller updateInstaller;
    private DiagnosticsController diagnosticsController;
    private DetailContentRenderer detailContentRenderer;
    private DetailHeaderRenderer detailHeaderRenderer;
    private DetailActionBar detailActionBar;
    private DetailCommentsSection detailCommentsSection;
    private UserSpacePage userSpacePage;
    private SavedContentController savedContentController;
    private ProfilePage profilePage;
    private CommentController commentController;
    private CommentRenderer commentRenderer;
    private PostActionController postActions;
    private SessionStore session;
    private ApiClient api;
    private WriteTokenProvider writeTokenProvider;
    private WriteActionClient writeActions;
    private QrLoginPage qrLoginPage;
    private CheckinCenterCoordinator checkinCenterCoordinator;
    private CheckinCenterPage checkinCenterPage;
    private ReadingTimeTracker readingTimeTracker;
    private LinearLayout shellRoot;
    private LinearLayout shellBar;
    private FrameLayout content;
    private LinearLayout bottom;
    private ResponsiveDock.Dimensions bottomDockDimensions;
    private boolean bottomVisible;
    private int bottomNavAnimSerial;
    private boolean bottomNavShowPending;
    private TextView title;
    private TextView leading;
    private TextView action;
    private FeedPage feedPage;
    private ScrollView detailScroll;
    private ScrollView detailCommentScroll;
    private DetailPager detailPager;
    private boolean shellAnimating;
    private LocalCache localCache;
    private CacheMaintenance cacheMaintenance;
    private FeedItem currentDetailItem;
    private FeedItem userSpaceReturnItem;
    private String userSpaceReturnScreen = "feed";
    private String pendingDetailReturn = "";
    private int detailRequestToken;
    private long lastExitBackAt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PageTransitionController pageTransitions = new PageTransitionController();
    private final CrownScrollController crownScrollController = new CrownScrollController();
    private final CrownScrollDispatcher crownScrollDispatcher =
            new CrownScrollDispatcher(this::findCrownScrollTarget, this::performCrownFeedback);
    private long lastCrownFeedbackAt;
    private SearchBarController searchBars;
    private SearchPage searchPage;
    private boolean pendingBackTransition;
    private boolean pendingLateralPush;
    private boolean immediatePageReplacement;
    private final Map<String, Bitmap> screenSnapshots = new HashMap<>();
    private final Map<String, Bitmap> fullScreenSnapshots = new HashMap<>();
    private final Map<String, View> retainedPages = new HashMap<>();
    private String screen = "feed";
    private String detailReturn = "feed";
    private View detailReturnView;
    private String detailReturnTitle = "";
    private String currentLinkId = "";
    private String currentLinkHsrc = "";
    private String currentAuthCode = "";
    private String lastDetailDiagnostics = "";
    private JSONObject currentDetailBody;
    private boolean detailHasRendered;
    private long detailLoadStartedAt;
    private JSONObject pendingDetailBody;
    private boolean activityResumed;
    private boolean accountBlockedScreen;
    private TextView accountBlockedMessage;
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
        Motions.setLevel(this.session.motionLevel());
        this.pageTransitions.setCompactMotion(usesWatchLayout());
        this.localCache = new LocalCache(this);
        this.cacheMaintenance = new CacheMaintenance(this, this.localCache, this.handler);
        boolean pendingCrashReport = !CrashReporter.pendingCrashReport(this).isEmpty();
        this.checkinCenterCoordinator = new CheckinCenterCoordinator(this, this.localCache);
        this.checkinCenterCoordinator.setAuthorizationListener(paired -> {
            if (this.profilePage != null) this.profilePage.invalidate();
            if (!paired && this.checkinCenterPage != null
                    && "checkin_center".equals(this.screen)) {
                this.checkinCenterPage.refresh();
            }
        });
        this.readingTimeTracker = new ReadingTimeTracker(this);
        ImageLoader.init(this);
        ImageLoader.setLogger(this.localCache::log);
        if (this.session.autoOfflineCleanup()) {
            this.cacheMaintenance.pruneOffline(null);
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
                message -> {
                    if (this.localCache != null) this.localCache.log(message);
                });
        this.searchBars = new SearchBarController(this, this.handler,
                this.session.uiScale() / 100.0f);
        this.searchPage = new SearchPage(this, this.session, this.api,
                this.localCache, this.searchBars, new SearchPage.Host() {
                    @Override
                    public boolean isSearchActive() {
                        return "search".equals(MainActivity.this.screen);
                    }

                    @Override
                    public FeedAdapter createFeedAdapter(List<FeedItem> items) {
                        return MainActivity.this.createFeedAdapter(items);
                    }

                    @Override
                    public void showToast(String message) {
                        MainActivity.this.toast(message);
                    }
                });
        buildShell();
        if (this.session.isLoggedIn()) {
            EmojiStore.load(this.api, () -> {
                if ("feed".equals(this.screen)) this.feedPage.notifyAdapter();
            });
        }
        showFeed();
        if (this.session.appBlocked()) {
            showAccountBlocked(this.session.appBlockMessage());
        }
        PresenceReporter.ping(this.session, this.readingTimeTracker, this::applyAccessStatus);
        RemoteConfig.load(this.session.userId(), () ->
                applyAccessStatus(RemoteConfig.accessStatus()));
        if (!this.accountBlockedScreen && pendingCrashReport) {
            this.handler.postDelayed(this::showPendingCrashDialog, 220L);
        }
        if (!this.accountBlockedScreen && this.session.autoUpdateCheck()) {
            this.handler.postDelayed(this::checkUpdateOnLaunch,
                    pendingCrashReport ? 1_800L : 650L);
        }
        if (!this.accountBlockedScreen) {
            this.handler.postDelayed(this::checkAnnouncementOnLaunch,
                    pendingCrashReport ? 2_100L : 950L);
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
        int horizontal = usesRoundLayout() ? pageHorizontalPadding() : dp(24);
        page.setPadding(horizontal, dp(24), horizontal, dp(24));
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
            if (axis == 0.0f) return super.dispatchGenericMotionEvent(event);
            if (this.session == null || !this.session.crownScrollEnabled()) {
                this.crownScrollController.reset();
                this.crownScrollDispatcher.cancel();
                return true;
            }
            int distance = this.crownScrollController.distance(
                    axis, dp(28), this.session.crownScrollSpeed());
            if (distance == 0) return true;
            if (scrollWithCrown(distance)) return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }

    private boolean scrollWithCrown(int distance) {
        return this.crownScrollDispatcher.enqueue(distance);
    }

    private View findCrownScrollTarget(int direction) {
        View target;
        if ("detail".equals(this.screen)) {
            target = this.detailPager != null && this.detailPager.showingComments()
                    ? this.detailCommentScroll : this.detailScroll;
        } else if ("feed".equals(this.screen)) {
            target = this.feedPage == null ? null : this.feedPage.listView();
        } else if ("search".equals(this.screen)) {
            target = this.searchPage == null ? null : this.searchPage.listView();
        } else {
            target = findScrollableView(this.content, direction);
        }
        if (target == null || !target.canScrollVertically(direction)) {
            target = findScrollableView(this.content, direction);
        }
        return target;
    }

    private void performCrownFeedback() {
        if (this.session == null || !this.session.crownHapticsEnabled()) return;
        long now = SystemClock.uptimeMillis();
        if (now - this.lastCrownFeedbackAt < 36L) return;
        this.lastCrownFeedbackAt = now;
        int effect;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            effect = HapticFeedbackConstants.CLOCK_TICK;
        } else {
            effect = HapticFeedbackConstants.KEYBOARD_TAP;
        }
        View target = this.content == null ? getWindow().getDecorView() : this.content;
        target.performHapticFeedback(effect);
    }

    private View findScrollableView(View view, int direction) {
        if (view == null || view.getVisibility() != View.VISIBLE) return null;
        if ((view instanceof ScrollView || view instanceof AbsListView)
                && view.canScrollVertically(direction)) {
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

    private void checkUpdateOnLaunch() {
        this.noticeCenter.checkUpdateOnLaunch();
    }

    private void checkAnnouncementOnLaunch() {
        this.noticeCenter.checkAnnouncementOnLaunch();
    }

    private void showLiteDialog(String titleValue, String message, String positiveText, Runnable positiveAction, String negativeText, Runnable negativeAction, String neutralText, Runnable neutralAction) {
        this.liteDialogs.show(titleValue, message, positiveText, positiveAction,
                negativeText, negativeAction, neutralText, neutralAction);
    }

    private void buildShell() {
        if (this.qrLoginPage != null) this.qrLoginPage.stop();
        if (this.checkinCenterPage != null) {
            this.checkinCenterPage.close();
            this.checkinCenterPage = null;
        }
        discardRetainedLayoutViews();
        this.settingsUi = new SettingsUi(this, this.session, this.themeTokens,
                usesRoundLayout(), roundHeaderInnerInset(), this.handler,
                this::onBackPressed);
        if (this.liteDialogs == null) {
            this.liteDialogs = new LiteDialogPresenter(this, this.session, this.themeTokens);
        } else {
            this.liteDialogs.updateTheme(this.themeTokens);
        }
        this.updateInstaller = new UpdateInstaller(this, this.session, this.localCache,
                this.handler, this.liteDialogs, this.themeTokens, this::toast);
        if (this.diagnosticsController == null) {
            this.diagnosticsController = new DiagnosticsController(this, this.session,
                    this.localCache, this.liteDialogs, new DiagnosticsController.Host() {
                        @Override
                        public DiagnosticsController.RuntimeState runtimeState() {
                            return new DiagnosticsController.RuntimeState(
                                    MainActivity.this.currentLinkId,
                                    MainActivity.this.currentLinkHsrc,
                                    MainActivity.this.screen,
                                    MainActivity.this.feedPage.itemCount(),
                                    !MainActivity.this.feedPage.paging().lastval().isEmpty(),
                                    MainActivity.this.feedPage.paging().lastPull(),
                                    MainActivity.this.feedPage.paging().noMore(),
                                    MainActivity.this.lastDetailDiagnostics);
                        }

                        @Override
                        public void showToast(String message) {
                            MainActivity.this.toast(message);
                        }
                    });
        }
        this.displaySettingsPage = new DisplaySettingsPage(this, this.session,
                this.settingsUi, this.themeTokens, usesRoundLayout(),
                new DisplaySettingsPage.Host() {
                    @Override
                    public LinearLayout openPage(String key, String title, Runnable back) {
                        return MainActivity.this.settingsPage(key, title,
                                back == null ? MainActivity.this::showSettingsHome : back);
                    }

                    @Override
                    public void rebuildDisplayShell(String destination) {
                        MainActivity.this.applyPalette();
                        Compat.colorSystemBars(MainActivity.this.getWindow(), MainActivity.this.BG);
                        MainActivity.this.immediatePageReplacement = true;
                        MainActivity.this.buildShell();
                        if ("display_preview".equals(destination)) {
                            MainActivity.this.showDisplayPreview();
                        } else {
                            MainActivity.this.showDisplaySettings();
                        }
                    }

                    @Override
                    public void showDialog(String title, String message,
                                           String positiveText, Runnable positiveAction,
                                           String negativeText, Runnable negativeAction,
                                           String neutralText, Runnable neutralAction) {
                        MainActivity.this.showLiteDialog(title, message, positiveText,
                                positiveAction, negativeText, negativeAction,
                                neutralText, neutralAction);
                    }

                    @Override
                    public void cancelAllMotion() {
                        MainActivity.this.cancelAllMotion();
                    }

                    @Override
                    public void showToast(String message) {
                        MainActivity.this.toast(message);
                    }
                });
        this.appSettingsPage = new AppSettingsPage(this, this.session, this.localCache,
                this.settingsUi, this.themeTokens, this.crownScrollController,
                this.cacheMaintenance, new AppSettingsPage.Host() {
                    @Override
                    public LinearLayout openPage(String key, String title) {
                        return MainActivity.this.settingsPage(key, title);
                    }

                    @Override
                    public void reloadFeed(boolean resetPaging) {
                        MainActivity.this.feedPage.resetContent(resetPaging);
                    }

                    @Override
                    public void exportDiagnostics() {
                        MainActivity.this.exportDiagnostics();
                    }

                    @Override
                    public void uploadDiagnostics() {
                        MainActivity.this.uploadDiagnostics();
                    }

                    @Override
                    public void showLogin() {
                        MainActivity.this.showLogin();
                    }

                    @Override
                    public void showToast(String message) {
                        MainActivity.this.toast(message);
                    }

                    @Override
                    public FrameLayout content() {
                        return MainActivity.this.content;
                    }
                });
        this.noticeCenter = new NoticeCenter(this, this.session, this.settingsUi,
                this.liteDialogs, this.themeTokens, usesRoundLayout(),
                new NoticeCenter.Host() {
                    @Override
                    public LinearLayout openSettingsPage(String key, String title) {
                        return MainActivity.this.settingsPage(key, title);
                    }

                    @Override
                    public void showAnnouncementPage(View page) {
                        MainActivity.this.stopQrPolling();
                        MainActivity.this.screen = "announcement_board";
                        MainActivity.this.setBottomNavVisible(false);
                        MainActivity.this.leading.setVisibility(View.INVISIBLE);
                        MainActivity.this.title.setText("公告");
                        MainActivity.this.action.setVisibility(View.INVISIBLE);
                        MainActivity.this.retainedPages.put("announcement_board", page);
                        MainActivity.this.transitionTo(page);
                    }

                    @Override
                    public boolean isAnnouncementPageActive() {
                        return "announcement_board".equals(MainActivity.this.screen);
                    }

                    @Override
                    public void openUpdateUrl(String url) {
                        MainActivity.this.openUpdateUrl(url);
                    }

                    @Override
                    public void openUrl(String url) {
                        MainActivity.this.openUrl(url);
                    }

                    @Override
                    public void showToast(String message) {
                        MainActivity.this.toast(message);
                    }

                    @Override
                    public int pageHorizontalPadding() {
                        return MainActivity.this.pageHorizontalPadding();
                    }

                    @Override
                    public int subpageTopPadding() {
                        return MainActivity.this.subpageTopPadding();
                    }
                });
        this.detailContentRenderer = new DetailContentRenderer(this, this.session,
                this.themeTokens, usesRoundLayout(), this::openImage);
        this.userSpacePage = new UserSpacePage(this, this.session, this.api,
                this.localCache, this.themeTokens, new UserSpacePage.Host() {
                    @Override
                    public boolean isActive() {
                        return "user_space".equals(MainActivity.this.screen);
                    }

                    @Override
                    public void openPost(FeedItem item) {
                        MainActivity.this.showDetail(item);
                    }
                });
        this.savedContentController = new SavedContentController(this, this.session,
                this.api, this.localCache, this.searchBars, this.settingsUi,
                this.userSpacePage, this.themeTokens, usesRoundLayout(),
                new SavedContentController.Host() {
                    @Override
                    public void prepareReadingCenter() {
                        MainActivity.this.prepareReadingCenterChrome();
                    }

                    @Override
                    public void prepareSavedPage(String title) {
                        MainActivity.this.prepareSavedPageChrome(title);
                    }

                    @Override
                    public void showContent(View view) {
                        MainActivity.this.content.removeAllViews();
                        MainActivity.this.content.addView(view, MainActivity.this.match());
                    }

                    @Override
                    public void retainPage(String key, View view) {
                        MainActivity.this.retainedPages.put(key, view);
                    }

                    @Override
                    public void showLoading() {
                        MainActivity.this.showLoading();
                    }

                    @Override
                    public void hideLoading() {
                        MainActivity.this.hideLoading();
                    }

                    @Override
                    public void showMessage(String message) {
                        MainActivity.this.showMessage(message);
                    }

                    @Override
                    public void showToast(String message) {
                        MainActivity.this.toast(message);
                    }

                    @Override
                    public void showProfile() {
                        MainActivity.this.showProfile();
                    }

                    @Override
                    public void showLogin() {
                        MainActivity.this.showLogin();
                    }

                    @Override
                    public void showReadingStats() {
                        MainActivity.this.showReadingStats();
                    }

                    @Override
                    public void showDetail(FeedItem item) {
                        MainActivity.this.showDetail(item);
                    }

                    @Override
                    public FeedAdapter createFeedAdapter(List<FeedItem> items) {
                        return MainActivity.this.createFeedAdapter(items);
                    }

                    @Override
                    public void addBottomNavSafeSpace(LinearLayout page) {
                        MainActivity.this.addBottomNavSafeSpace(page);
                    }

                    @Override
                    public String readingSummary() {
                        return MainActivity.this.readingEntrySummary();
                    }

                    @Override
                    public boolean isSavedScreen() {
                        return "saved".equals(MainActivity.this.screen);
                    }

                    @Override
                    public int pageHorizontalPadding() {
                        return MainActivity.this.pageHorizontalPadding();
                    }

                    @Override
                    public int subpageTopPadding() {
                        return MainActivity.this.subpageTopPadding();
                    }

                    @Override
                    public int roundSearchInset() {
                        return MainActivity.this.roundHorizontalInset(
                                RoundLayoutMetrics.SEARCH_HORIZONTAL_RATIO, 6);
                    }
                });
        this.profilePage = new ProfilePage(this, this.session, this.api,
                this.localCache, this.settingsUi, this.themeTokens,
                this.checkinCenterCoordinator, usesRoundLayout(),
                new ProfilePage.Host() {
                    @Override
                    public void prepareProfileChrome() {
                        MainActivity.this.activate("profile");
                        MainActivity.this.title.setText("我的");
                        MainActivity.this.action.setVisibility(View.INVISIBLE);
                    }

                    @Override
                    public boolean isProfileActive() {
                        return "profile".equals(MainActivity.this.screen);
                    }

                    @Override
                    public void showPage(View page) {
                        MainActivity.this.transitionTo(page);
                    }

                    @Override
                    public void showLoading() {
                        MainActivity.this.transitionTo(MainActivity.this.detailLoadingPage());
                    }

                    @Override
                    public void hideLoading() {
                        MainActivity.this.hideLoading();
                    }

                    @Override
                    public void showLogin() {
                        MainActivity.this.showLogin();
                    }

                    @Override
                    public void showReadingCenter() {
                        MainActivity.this.showReadingCenter();
                    }

                    @Override
                    public void showFavorites() {
                        MainActivity.this.showFavorites();
                    }

                    @Override
                    public void showCheckinCenter() {
                        MainActivity.this.showCheckinCenter();
                    }

                    @Override
                    public void showSettings() {
                        MainActivity.this.showSettingsHome();
                    }

                    @Override
                    public void showUserSpace(String userId, String name, String avatar) {
                        MainActivity.this.showUserSpace(userId, name, avatar);
                    }

                    @Override
                    public void showToast(String message) {
                        MainActivity.this.toast(message);
                    }

                    @Override
                    public void addBottomSafeSpace(LinearLayout page) {
                        MainActivity.this.addBottomNavSafeSpace(page);
                    }

                    @Override
                    public String readingSummary() {
                        return MainActivity.this.readingEntrySummary();
                    }

                    @Override
                    public int pageHorizontalPadding() {
                        return MainActivity.this.pageHorizontalPadding();
                    }

                    @Override
                    public int pageTopPadding() {
                        return MainActivity.this.pageTopPadding();
                    }

                    @Override
                    public int roundHeaderInset() {
                        return MainActivity.this.roundHeaderInnerInset();
                    }
                });
        this.qrLoginPage = new QrLoginPage(this, this.session, this.api,
                this.handler, this.themeTokens, usesRoundLayout(),
                new QrLoginPage.Host() {
                    @Override
                    public void prepareLoginChrome() {
                        MainActivity.this.screen = "login";
                        MainActivity.this.shellBar.setVisibility(View.GONE);
                        MainActivity.this.setBottomNavVisible(false);
                        MainActivity.this.leading.setVisibility(View.INVISIBLE);
                        MainActivity.this.action.setVisibility(View.INVISIBLE);
                        MainActivity.this.title.setText("扫码登录");
                    }

                    @Override
                    public void showPage(View page) {
                        MainActivity.this.content.removeAllViews();
                        MainActivity.this.content.addView(page, MainActivity.this.match());
                    }

                    @Override
                    public void onLoginComplete() {
                        MainActivity.this.feedPage.clearItems();
                        MainActivity.this.profilePage.invalidate();
                        MainActivity.this.toast("登录成功");
                        EmojiStore.load(MainActivity.this.api, () -> {
                        });
                        MainActivity.this.showFeed();
                        PresenceReporter.pingNow(MainActivity.this.session,
                                MainActivity.this.readingTimeTracker,
                                MainActivity.this::applyAccessStatus);
                        RemoteConfig.load(MainActivity.this.session.userId(), () ->
                                MainActivity.this.applyAccessStatus(
                                        RemoteConfig.accessStatus()));
                    }

                    @Override
                    public void showFeed() {
                        MainActivity.this.showFeed();
                    }

                    @Override
                    public int pageHorizontalPadding() {
                        return MainActivity.this.pageHorizontalPadding();
                    }

                    @Override
                    public int pageTopPadding() {
                        return MainActivity.this.pageTopPadding();
                    }
                });
        this.postActions = new PostActionController(this, this.session,
                this.writeActions, this.localCache, this.themeTokens,
                usesRoundLayout(), new PostActionController.Host() {
                    @Override
                    public String currentLinkId() {
                        return MainActivity.this.currentLinkId;
                    }

                    @Override
                    public String currentLinkHsrc() {
                        return MainActivity.this.currentLinkHsrc;
                    }

                    @Override
                    public FeedItem currentDetailItem() {
                        return MainActivity.this.currentDetailItem;
                    }

                    @Override
                    public List<FeedItem> feedItems() {
                        return MainActivity.this.feedPage.items();
                    }

                    @Override
                    public List<FeedItem> searchItems() {
                        return MainActivity.this.searchPage.items();
                    }

                    @Override
                    public void feedChanged() {
                        MainActivity.this.feedPage.notifyItemsChanged();
                    }

                    @Override
                    public void showToast(String message) {
                        MainActivity.this.toast(message);
                    }
                });
        this.feedPage = new FeedPage(this, this.session, this.api,
                this.localCache, this.themeTokens, this.postActions,
                usesRoundLayout(), new FeedPage.Host() {
                    @Override
                    public void prepareFeedChrome() {
                        if ("profile".equals(MainActivity.this.screen)) {
                            MainActivity.this.pendingBackTransition = true;
                            MainActivity.this.pendingLateralPush = true;
                        }
                        MainActivity.this.activate("feed");
                        MainActivity.this.title.setText("社区");
                        MainActivity.this.action.setText("");
                        MainActivity.this.setIcon(MainActivity.this.action,
                                R.drawable.ic_refresh, MainActivity.this.TEXT, 19);
                        MainActivity.this.action.setVisibility(View.INVISIBLE);
                        MainActivity.this.action.setOnClickListener(
                                view -> MainActivity.this.feedPage.load(true));
                    }

                    @Override
                    public boolean isFeedActive() {
                        return "feed".equals(MainActivity.this.screen);
                    }

                    @Override
                    public void showPage(View page) {
                        MainActivity.this.transitionTo(page);
                    }

                    @Override
                    public void showSearch() {
                        MainActivity.this.showSearch();
                    }

                    @Override
                    public void openDetail(FeedItem item) {
                        MainActivity.this.showDetail(item);
                    }

                    @Override
                    public void showLoading() {
                        MainActivity.this.showLoading();
                    }

                    @Override
                    public void hideLoading() {
                        MainActivity.this.hideLoading();
                    }

                    @Override
                    public void showMessage(String message) {
                        MainActivity.this.showMessage(message);
                    }

                    @Override
                    public void showToast(String message) {
                        MainActivity.this.toast(message);
                    }

                    @Override
                    public void setRefreshBusy(boolean busy) {
                        MainActivity.this.action.setEnabled(!busy);
                        MainActivity.this.action.setAlpha(busy ? 0.45f : 1.0f);
                    }

                    @Override
                    public int pageTopPadding() {
                        return MainActivity.this.pageTopPadding();
                    }

                    @Override
                    public int roundHeaderTopPadding() {
                        return RoundLayoutMetrics.componentInset(
                                MainActivity.this.screenMetrics().heightPixels,
                                RoundLayoutMetrics.PAGE_TOP_RATIO,
                                MainActivity.this.dp(9));
                    }

                    @Override
                    public int roundHeaderInset() {
                        return MainActivity.this.roundHorizontalInset(
                                RoundLayoutMetrics.HEADER_HORIZONTAL_RATIO, 10);
                    }

                    @Override
                    public int roundCardInset() {
                        return MainActivity.this.pageHorizontalPadding();
                    }

                    @Override
                    public int roundSearchInset() {
                        return MainActivity.this.roundHorizontalInset(
                                RoundLayoutMetrics.SEARCH_HORIZONTAL_RATIO, 6);
                    }
                });
        this.detailHeaderRenderer = new DetailHeaderRenderer(this, this.session,
                this.themeTokens, this.postActions, usesRoundLayout(),
                this::showUserSpace);
        this.commentController = new CommentController(this, this.session,
                this.api, this.writeActions, this.localCache, this.themeTokens,
                usesRoundLayout(), new CommentController.PageHost() {
                    @Override
                    public FeedItem currentItem() {
                        return MainActivity.this.currentDetailItem;
                    }

                    @Override
                    public JSONObject currentBody() {
                        return MainActivity.this.currentDetailBody;
                    }

                    @Override
                    public String currentLinkId() {
                        return MainActivity.this.currentLinkId;
                    }

                    @Override
                    public String currentHsrc() {
                        return MainActivity.this.postActions.hsrcFor(
                                MainActivity.this.currentDetailItem);
                    }

                    @Override
                    public String currentAuthCode() {
                        return MainActivity.this.currentAuthCode;
                    }

                    @Override
                    public boolean requireLogin(String actionName) {
                        return MainActivity.this.postActions.requireLogin(actionName);
                    }

                    @Override
                    public boolean allowWriteAction(String actionName) {
                        return MainActivity.this.postActions.allowWriteAction(actionName);
                    }

                    @Override
                    public String writeErrorMessage(String actionName,
                                                    String message) {
                        return MainActivity.this.postActions.writeErrorMessage(
                                actionName, message);
                    }

                    @Override
                    public void reloadDetail() {
                        if (MainActivity.this.currentDetailItem != null) {
                            MainActivity.this.showDetail(
                                    MainActivity.this.currentDetailItem);
                        }
                    }

                    @Override
                    public void showToast(String message) {
                        MainActivity.this.toast(message);
                    }

                    @Override
                    public void openImage(ImageView source, String url) {
                        MainActivity.this.openImage(source, url);
                    }
                });
        this.commentRenderer = this.commentController.renderer();
        this.detailCommentsSection = new DetailCommentsSection(this, this.session,
                this.themeTokens, this.commentRenderer, this.handler,
                pager -> MainActivity.this.detailPager == pager);
        this.detailActionBar = new DetailActionBar(this, this.session,
                this.localCache, this.themeTokens, this.postActions,
                this.commentController, this.commentRenderer,
                this.detailContentRenderer, usesRoundLayout(),
                new DetailActionBar.Host() {
                    @Override
                    public JSONObject currentDetailBody() {
                        return MainActivity.this.currentDetailBody;
                    }

                    @Override
                    public void showToast(String message) {
                        MainActivity.this.toast(message);
                    }
                });
        LinearLayout linearLayoutVertical = vertical(this.BG);
        this.shellRoot = linearLayoutVertical;
        LinearLayout bar = new LinearLayout(this);
        this.shellBar = bar;
        bar.setGravity(16);
        int sidePadding = usesRoundLayout() ? dp(9) : dp(4);
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
        this.content = new BackSwipeFrameLayout(this, this);
        body.addView(this.content, match());
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        this.bottomDockDimensions = ResponsiveDock.fromScreen(
                displayMetrics.widthPixels, displayMetrics.heightPixels,
                usesRoundLayout());
        this.bottom = new LinearLayout(this);
        this.bottom.setGravity(17);
        this.bottom.setPadding(this.bottomDockDimensions.paddingHorizontal,
                this.bottomDockDimensions.paddingVertical,
                this.bottomDockDimensions.paddingHorizontal,
                this.bottomDockDimensions.paddingVertical);
        if (Build.VERSION.SDK_INT >= 21) this.bottom.setElevation(dp(10));
        Compat.setBackground(this.bottom, UiComponents.dock(this, this.themeTokens,
                this.session.uiScale() / 100.0f));
        this.bottom.setVisibility(8);
        this.bottom.setAlpha(0.0f);
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                this.bottomDockDimensions.width, this.bottomDockDimensions.height, 81);
        bottomParams.setMargins(0, 0, 0, this.bottomDockDimensions.marginBottom);
        body.addView(this.bottom, bottomParams);
        addNav("社区", "feed", R.drawable.ic_nav_home, this::onFeedNavClick);
        addNav("我的", "profile", R.drawable.ic_nav_profile, () -> {
            showTopLevel(1);
        });
        setContentView(linearLayoutVertical);
    }

    private DisplayMetrics screenMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        try {
            if (Build.VERSION.SDK_INT >= 17) {
                getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
            } else {
                getWindowManager().getDefaultDisplay().getMetrics(metrics);
            }
        } catch (RuntimeException error) {
            return getResources().getDisplayMetrics();
        }
        return metrics;
    }

    private boolean usesRoundLayout() {
        return this.session != null && this.session.usesRoundLayout();
    }

    private boolean usesWatchLayout() {
        return this.session != null && RoundLayoutMetrics.isWatchDisplay(this);
    }

    private int pageHorizontalPadding() {
        if (!usesRoundLayout()) return dp(8);
        DisplayMetrics metrics = screenMetrics();
        return RoundLayoutMetrics.componentInset(metrics.widthPixels,
                RoundLayoutMetrics.PAGE_HORIZONTAL_RATIO, dp(8));
    }

    private int pageTopPadding() {
        if (!usesRoundLayout()) return dp(8);
        DisplayMetrics metrics = screenMetrics();
        return RoundLayoutMetrics.componentInset(metrics.heightPixels,
                RoundLayoutMetrics.PAGE_TOP_RATIO, dp(8));
    }

    private int subpageTopPadding() {
        if (!usesRoundLayout()) return dp(8);
        DisplayMetrics metrics = screenMetrics();
        return RoundLayoutMetrics.componentInset(metrics.heightPixels,
                RoundLayoutMetrics.SUBPAGE_TOP_RATIO, dp(8));
    }

    private int roundHorizontalInset(float targetRatio, int minimumDp) {
        DisplayMetrics metrics = screenMetrics();
        return RoundLayoutMetrics.componentInset(metrics.widthPixels,
                targetRatio, dp(minimumDp));
    }

    private int roundHeaderInnerInset() {
        return usesRoundLayout()
                ? RoundLayoutMetrics.headerInnerInset(screenMetrics().widthPixels)
                : 0;
    }

    private void discardRetainedLayoutViews() {
        if (this.feedPage != null) this.feedPage.invalidateView();
        if (this.profilePage != null) this.profilePage.invalidate();
        this.retainedPages.clear();
    }

    private void applyPalette() {
        int defaultPrimary = this.session.darkMode()
                ? Color.WHITE : Color.rgb(20, 21, 23);
        int defaultSecondary = this.session.darkMode()
                ? Color.rgb(196, 198, 201) : Color.rgb(87, 91, 96);
        this.PRIMARY = parseThemeColor(this.session.primaryColor(), defaultPrimary);
        this.SECONDARY = parseThemeColor(this.session.secondaryColor(), defaultSecondary);
        this.themeTokens = ThemeTokens.of(this.session.darkMode(), this.PRIMARY, this.SECONDARY);
        this.BG = this.themeTokens.background;
        this.PANEL = this.themeTokens.panel;
        this.TEXT = this.themeTokens.text;
        this.MUTED = this.themeTokens.muted;
    }

    private int parseThemeColor(String value, int fallback) {
        try {
            return value.isEmpty() ? fallback : Color.parseColor(value);
        } catch (IllegalArgumentException error) {
            return fallback;
        }
    }

    private void addNav(String label, String key, int drawable, Runnable click) {
        ImageView item = new ImageView(this);
        item.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int itemWidth = Math.max(this.bottomDockDimensions.iconSize,
                (this.bottomDockDimensions.width
                        - (this.bottomDockDimensions.paddingHorizontal * 2)
                        - (this.bottomDockDimensions.itemMargin * 4)) / 2);
        int itemHeight = Math.max(this.bottomDockDimensions.iconSize,
                this.bottomDockDimensions.height
                        - (this.bottomDockDimensions.paddingVertical * 2));
        int horizontalInset = Math.max(0,
                (itemWidth - this.bottomDockDimensions.iconSize) / 2);
        int verticalInset = Math.max(0,
                (itemHeight - this.bottomDockDimensions.iconSize) / 2);
        item.setPadding(horizontalInset, verticalInset, horizontalInset, verticalInset);
        item.setAdjustViewBounds(false);
        item.setImageDrawable(navIcon(drawable, this.MUTED));
        item.setColorFilter(this.MUTED);
        item.setContentDescription(label);
        item.setTag(key);
        item.setOnClickListener(view -> {
            runWithPressFeedback(item, click);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1.0f);
        params.setMargins(this.bottomDockDimensions.itemMargin, 0,
                this.bottomDockDimensions.itemMargin, 0);
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
            icon.setBounds(0, 0, this.bottomDockDimensions.iconSize,
                    this.bottomDockDimensions.iconSize);
        }
        return icon;
    }

    private boolean canHeaderBack() {
        return ScreenRoutes.canNavigateBack(this.screen);
    }

    private int topLevelIndex() {
        if ("feed".equals(this.screen)) {
            return 0;
        }
        return "profile".equals(this.screen) ? 1 : -1;
    }

    private void showTopLevel(int index) {
        if ("feed".equals(this.screen) && index != 0) {
            this.feedPage.saveScroll();
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
        this.feedPage.scrollToTopAndRefresh();
    }

    private String screenKeyForTopLevel(int index) {
        return index == 0 ? "feed" : index == 1 ? "profile" : "";
    }

    private String backTargetScreenKey() {
        if ("detail".equals(this.screen)) return this.detailReturn;
        if ("user_space".equals(this.screen)) {
            return this.userSpaceReturnItem == null ? this.userSpaceReturnScreen : "detail";
        }
        if ("saved".equals(this.screen)) return this.savedContentController.returnScreen();
        return ScreenRoutes.staticParentOrDefault(this.screen, "feed");
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
            target.put(key, bitmap);
            trimSnapshots(target, maxCount);
        } catch (RuntimeException | OutOfMemoryError error) {
            if (this.localCache != null) {
                this.localCache.log("transition snapshot skipped error="
                        + error.getClass().getSimpleName());
            }
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
            iterator.remove();
        }
    }

    private void clearSnapshots(Map<String, Bitmap> snapshots) {
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
        overlay.setTag(BackSwipeFrameLayout.TRANSITION_OVERLAY_TAG);
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
        View target = "feed".equals(key) ? this.feedPage.cachedView()
                : "profile".equals(key) && this.profilePage != null
                        ? this.profilePage.cachedPage()
                : this.retainedPages.get(key);
        return target != null && target.getParent() == null ? target : null;
    }

    private void configureAdoptedShellScreen(String key) {
        if ("checkin_center".equals(this.screen) && this.checkinCenterPage != null) {
            this.checkinCenterPage.onPause();
        }
        if ("feed".equals(key)) {
            activate("feed");
            this.title.setText("社区");
            this.action.setVisibility(4);
            this.feedPage.restoreScroll();
            return;
        }
        if ("profile".equals(key)) {
            this.userSpaceReturnItem = null;
            this.userSpaceReturnScreen = "feed";
            activate("profile");
            if (this.profilePage != null) this.profilePage.updateReadingSummary();
            this.title.setText("我的");
            this.action.setVisibility(0);
            setIcon(this.action, R.drawable.il_refresh, this.TEXT, 19);
            this.action.setOnClickListener(view -> {
                if (this.profilePage != null) this.profilePage.invalidate();
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
            this.title.setText("设置");
            this.leading.setOnClickListener(view -> {
                this.pendingBackTransition = true;
                showProfile();
            });
        } else if ("display_settings".equals(key)) {
            this.title.setText("显示");
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
            this.title.setText("内容与缓存");
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
        int activeColor = this.TEXT;
        int inactiveColor = this.themeTokens.subtle;
        for (int i = 0; i < this.bottom.getChildCount(); i++) {
            View item = this.bottom.getChildAt(i);
            boolean active = key.equals(item.getTag());
            item.setAlpha(active ? 1.0f : 0.62f);
            if (item instanceof ImageView) {
                ((ImageView) item).setColorFilter(active ? activeColor : inactiveColor);
                Compat.setBackground(item, active
                        ? UiComponents.navSelection(this, this.themeTokens,
                        this.session.uiScale() / 100.0f) : null);
            } else if (item instanceof TextView) {
                TextView textItem = (TextView) item;
                textItem.setTextColor(active ? activeColor : inactiveColor);
                textItem.setTypeface(appRegularTypeface(), active ? 1 : 0);
            }
        }
    }

    @Override
    public String shellScreenKey() {
        return this.screen;
    }

    @Override
    public boolean canStartShellSwipe() {
        return !this.shellAnimating && !this.pageTransitions.isRunning()
                && !"detail".equals(this.screen) && !"login".equals(this.screen);
    }

    @Override
    public int shellTopLevelIndex() {
        return topLevelIndex();
    }

    @Override
    public boolean hasShellSwipeTarget(float distanceX) {
        int index = topLevelIndex();
        if (index < 0) {
            return this.session.shellBackSwipe() && canHeaderBack() && distanceX > 0.0f;
        }
        int next = index + (distanceX < 0.0f ? 1 : -1);
        return next >= 0 && next <= 1;
    }

    @Override
    public String shellSwipeTarget(boolean topLevel, int direction) {
        return topLevel
                ? screenKeyForTopLevel(topLevelIndex() + direction)
                : backTargetScreenKey();
    }

    @Override
    public void captureShellState(String screenKey, View currentView) {
        captureShellSnapshot(screenKey, currentView);
        captureFullScreenSnapshot(screenKey);
    }

    @Override
    public BackSwipeFrameLayout.Preview createShellPreview(String targetKey, boolean back) {
        View target = realShellPreview(targetKey);
        if (target == null && back && "settings_home".equals(targetKey)) {
            target = buildSettingsHomeContent();
            this.retainedPages.put(targetKey, target);
        }
        if (target != null) {
            return new BackSwipeFrameLayout.Preview(target, true);
        }
        ImageView fallback = new ImageView(this);
        fallback.setBackgroundColor(this.themeTokens == null
                ? this.PANEL : this.themeTokens.panel);
        fallback.setScaleType(ImageView.ScaleType.FIT_XY);
        Bitmap bitmap = screenSnapshot(targetKey);
        if (bitmap != null && !bitmap.isRecycled()) fallback.setImageBitmap(bitmap);
        return new BackSwipeFrameLayout.Preview(fallback, false);
    }

    @Override
    public void prepareShellView(View view) {
        ensurePageBackdrop(view);
    }

    @Override
    public int shellDp(int value) {
        return dp(value);
    }

    @Override
    public void setShellGestureActive(boolean active) {
        this.shellAnimating = active;
    }

    @Override
    public boolean compactShellMotion() {
        return usesWatchLayout();
    }

    @Override
    public void navigateAfterShellSwipe(boolean topLevel, int direction,
                                        String targetKey, Runnable settled) {
        ImageView guard = topLevel ? null
                : installFullScreenTransitionOverlay(fullScreenSnapshot(targetKey));
        if (topLevel) {
            showTopLevel(topLevelIndex() + direction);
        } else {
            onBackPressed();
        }
        settled.run();
        removeFullScreenTransitionOverlayAfterLayout(guard);
    }

    @Override
    public void adoptShellPreview(String targetKey) {
        configureAdoptedShellScreen(targetKey);
    }

    private void showLogin() {
        this.qrLoginPage.show();
    }

    private void stopQrPolling() {
        if (this.qrLoginPage != null) this.qrLoginPage.stop();
    }

    private void showFeed() {
        this.feedPage.show();
    }

    private void updateReadingTimeEntry() {
        if (this.profilePage != null) this.profilePage.updateReadingSummary();
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

        ReadingStatsPage page = new ReadingStatsPage(this, this.themeTokens,
                usesRoundLayout(), this.session.uiScale() / 100.0f,
                this.session.textScale() / 100.0f);
        ScrollView scroll = page.build(this.readingTimeTracker.stats(),
                settingsTopCard("阅读时长"), pageHorizontalPadding(), subpageTopPadding());
        this.retainedPages.put("reading_stats", scroll);
        transitionTo(scroll);
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
        boolean roundLayout = usesRoundLayout();
        int horizontalPadding = pageHorizontalPadding();
        int searchHorizontal = roundLayout
                ? roundHorizontalInset(RoundLayoutMetrics.SEARCH_HORIZONTAL_RATIO, 6)
                : horizontalPadding;
        View page = this.searchPage.create(restoreResults, settingsTopCard("搜索"),
                this.themeTokens, roundLayout, horizontalPadding,
                subpageTopPadding(), searchHorizontal);
        transitionTo(page);
    }

    private void showDetail(final FeedItem item) {
        this.pendingBackTransition = false;
        this.pageTransitions.finishNow();
        saveCurrentDetailProgress();
        stopQrPolling();
        ensureEmojiCatalog(() -> {
        });
        if ("feed".equals(this.screen)) {
            this.feedPage.saveScroll();
        }
        if ("search".equals(this.screen)) this.searchPage.saveListPosition();
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
        this.detailHasRendered = false;
        this.pendingDetailBody = null;
        this.detailLoadStartedAt = SystemClock.elapsedRealtime();
        this.commentController.reset();
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
        renderDetailAfterEntry(item, requestToken);
        if (!isNetworkConnected()) {
            runAfterDetailEntry(() -> {
                if (isCurrentDetailRequest(item, requestToken)) {
                    hideLoading();
                    handleDetailFailure(item, "当前无网络");
                }
            });
            return;
        }
        this.api.get(EndpointProvider.linkTreeV2(), detailParams(item), new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                if (MainActivity.this.isCurrentDetailRequest(item, requestToken)) {
                    MainActivity.this.hideLoading();
                    String blocked = MainActivity.this.detailBlockedMessage(body);
                    if (!blocked.isEmpty()) {
                        MainActivity.this.handleDetailFailureAfterEntry(
                                item, requestToken, blocked);
                    } else if (!MainActivity.this.hasDetailLink(body)) {
                        MainActivity.this.handleDetailFailureAfterEntry(
                                item, requestToken, "详情数据为空");
                    } else {
                        MainActivity.this.cacheDetailAndRender(item, body);
                    }
                }
            }

            @Override
            public void onError(String message) {
                if (MainActivity.this.isCurrentDetailRequest(item, requestToken)) {
                    MainActivity.this.hideLoading();
                    MainActivity.this.handleDetailFailureAfterEntry(
                            item, requestToken, message);
                }
            }
        });
    }

    private boolean isCurrentDetailRequest(FeedItem item, int requestToken) {
        return "detail".equals(this.screen) && item != null && item.id.equals(this.currentLinkId) && requestToken == this.detailRequestToken;
    }

    private void cacheDetailAndRender(FeedItem item, JSONObject body) {
        JSONObject normalized = DetailResponseNormalizer.normalize(body);
        this.localCache.saveDetail(item.id, normalized);
        if (this.localCache.isWatchLater(item.id)) {
            this.detailActionBar.refreshOffline(item, normalized);
        }
        this.pendingDetailBody = normalized;
        renderDetailAfterEntry(item, this.detailRequestToken);
    }

    private void renderDetailAfterEntry(FeedItem item, int requestToken) {
        runAfterDetailEntry(() -> {
            if (!isCurrentDetailRequest(item, requestToken)) return;
            JSONObject body = this.pendingDetailBody;
            this.pendingDetailBody = null;
            if (body == null && !this.detailHasRendered) {
                body = initialDetailBody(item);
            }
            if (body != null) renderDetail(body, item);
        });
    }

    private void runAfterDetailEntry(Runnable action) {
        long elapsed = Math.max(0L,
                SystemClock.elapsedRealtime() - this.detailLoadStartedAt);
        long delay = Math.max(0L, MotionSpec.TRANSITION_FULL_MS - elapsed);
        this.handler.postDelayed(action, delay);
    }

    private JSONObject initialDetailBody(FeedItem item) {
        JSONObject cached = this.localCache.detail(item.id);
        if (cached != null && detailBlockedMessage(cached).isEmpty()
                && hasDetailLink(cached)) {
            this.localCache.log("perf app stage=detail-cache-hit link=true");
            return cached;
        }
        try {
            JSONObject result = new JSONObject();
            result.put("link", item.toJson());
            result.put("comments", new JSONArray());
            JSONObject body = new JSONObject();
            body.put("result", result);
            body.put("_progressive_preview", true);
            return body;
        } catch (JSONException error) {
            return null;
        }
    }

    private Map<String, String> detailParams(FeedItem item) {
        return OfficialRequestParams.detail(item.id, item.hsrc);
    }

    private boolean isNetworkConnected() {
        try {
            ConnectivityManager manager = (ConnectivityManager)
                    getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = manager == null ? null : manager.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (SecurityException ignored) {
            return true;
        }
    }

    private void handleDetailFailure(FeedItem item, String message) {
        this.localCache.log("detail failed " + item.id + ": " + message);
        if (this.detailHasRendered) {
            toast("详情更新失败，已保留当前内容");
            return;
        }
        JSONObject cached = this.localCache.detail(item.id);
        if (cached != null && detailBlockedMessage(cached).isEmpty() && hasDetailLink(cached)) {
            toast(MSG_OFFLINE_CACHE);
            renderDetail(cached, item);
        } else if (!renderFallbackDetail(item, message)) {
            showMessage("详情加载失败\n" + message);
        }
    }

    private void handleDetailFailureAfterEntry(FeedItem item, int requestToken,
                                               String message) {
        runAfterDetailEntry(() -> {
            if (isCurrentDetailRequest(item, requestToken)) {
                handleDetailFailure(item, message);
            }
        });
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
        } catch (JSONException error) {
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
        boolean replacing = this.detailHasRendered;
        boolean previousComments = replacing && this.detailPager != null
                && this.detailPager.showingComments();
        int previousArticleScroll = replacing && this.detailScroll != null
                ? this.detailScroll.getScrollY() : 0;
        int previousCommentScroll = replacing && this.detailCommentScroll != null
                ? this.detailCommentScroll.getScrollY() : 0;
        body = DetailResponseNormalizer.normalize(body);
        this.currentDetailBody = body;
        JSONObject result = body.optJSONObject("result");
        JSONObject link = result == null ? null : result.optJSONObject("link");
        String[] strArr = new String[3];
        strArr[0] = this.postActions.hsrc(link);
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
        DetailPager pager = new DetailPager(this, this::dp, usesWatchLayout(),
                new DetailPager.Listener() {
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
        boolean roundLayout = usesRoundLayout();
        int pagePadding = roundLayout ? pageHorizontalPadding()
                : Math.max(dp(10), dp(this.session.pagePadding()));
        int roundHeaderTop = roundLayout ? subpageTopPadding() : 0;
        int detailTopPadding = roundLayout
                ? roundHeaderTop + dp(40) : dp(50);
        page.setPadding(pagePadding, detailTopPadding, pagePadding, dp(18));
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
        this.detailHeaderRenderer.addAuthor(article, link, user, author);
        if (this.readingTimeTracker != null) {
            this.readingTimeTracker.tagTopic(
                    this.detailHeaderRenderer.firstTopicName(link, fallback.topicName));
        }
        if (fallback.article) {
            this.detailHeaderRenderer.addTopics(article, link, fallback.topicName);
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
        this.lastDetailDiagnostics = DetailDiagnostics.build(
                this.screen, this.currentLinkId, this.session.playGif(),
                body, fallback, link, fallbackImages, comments);
        this.localCache.log("detail diagnostics captured link="
                + (fallback == null ? "" : fallback.id)
                + " title=" + DetailDiagnostics.compactText(heading, 48));
        this.detailContentRenderer.add(
                article, link, fallback.description, fallbackImages);
        if (!fallback.article) {
            this.detailHeaderRenderer.addTopics(article, link, fallback.topicName);
        }
        this.detailActionBar.add(article, fallback, link);
        page.addView(article);
        LinearLayout articleCommentHost = this.detailCommentsSection.placeholder(
                page, comments);
        ScrollView commentScroll = new ScrollView(this);
        commentScroll.setBackgroundColor(this.BG);
        LinearLayout commentPage = vertical(this.BG);
        commentPage.setPadding(pagePadding, detailTopPadding, pagePadding, dp(18));
        commentScroll.addView(commentPage);
        LinearLayout commentPageHost = this.detailCommentsSection.placeholder(
                commentPage, comments);
        pager.setPages(detailReturnPreview(), articleScroll, commentScroll);
        FrameLayout detailRoot = new FrameLayout(this);
        detailRoot.setBackgroundColor(this.BG);
        detailRoot.addView(pager, match());
        ImageView back = detailBackButton();
        FrameLayout.LayoutParams backParams =
                new FrameLayout.LayoutParams(dp(36), dp(36), 51);
        backParams.leftMargin = pagePadding
                + (roundLayout ? roundHeaderInnerInset() : 0);
        backParams.topMargin = roundLayout ? roundHeaderTop : dp(8);
        detailRoot.addView(back, backParams);
        installDetailRoot(detailRoot, pager, replacing, previousArticleScroll,
                previousCommentScroll, previousComments, articleScroll, commentScroll);
        this.detailCommentsSection.populate(articleCommentHost, comments, pager, 72L,
                articleScroll, previousArticleScroll);
        this.detailCommentsSection.populate(commentPageHost, comments, pager, 140L,
                commentScroll, previousCommentScroll);
        this.detailHasRendered = true;
        if (this.activityResumed && this.readingTimeTracker != null && fallback != null) {
            this.readingTimeTracker.start(fallback.article, fallback.id);
        }
        this.detailScroll = articleScroll;
        this.detailCommentScroll = commentScroll;
        int savedScroll = this.session.rememberDetailScroll() ? this.localCache.scroll(this.currentLinkId) : 0;
        if (!replacing && savedScroll > 0) {
            articleScroll.postDelayed(() -> {
                articleScroll.scrollTo(0, savedScroll);
            }, 80L);
        }
    }

    private void installDetailRoot(FrameLayout root, DetailPager pager,
                                   boolean replacing, int previousArticleScroll,
                                   int previousCommentScroll, boolean previousComments,
                                   ScrollView articleScroll, ScrollView commentScroll) {
        if (!replacing) {
            this.pageTransitions.finishNow();
            if (this.detailReturnView != null) {
                pager.setReturnView(this.detailReturnView);
            }
            transitionTo(root);
        } else {
            this.pageTransitions.finishNow();
            View previous = this.content.getChildCount() == 0 ? null
                    : this.content.getChildAt(this.content.getChildCount() - 1);
            if (this.detailReturnView != null) {
                pager.setReturnView(this.detailReturnView);
            }
            root.setVisibility(View.INVISIBLE);
            this.content.addView(root, match());
            root.post(() -> {
                if (this.detailPager != pager || isFinishing()) return;
                if (previousArticleScroll > 0) {
                    articleScroll.scrollTo(0, previousArticleScroll);
                }
                if (previousComments) pager.showComments(false);
                if (previousCommentScroll > 0) {
                    commentScroll.scrollTo(0, previousCommentScroll);
                }
                root.setVisibility(View.VISIBLE);
                if (previous != null && previous.getParent() == this.content) {
                    this.content.removeView(previous);
                }
            });
        }
        root.post(() -> {
            if (this.detailPager == pager && !isFinishing()) {
                this.localCache.log("perf app stage=detail-first-frame totalMs="
                        + Math.max(0L, SystemClock.elapsedRealtime()
                        - this.detailLoadStartedAt)
                        + " progressiveUpdate=" + replacing);
            }
        });
    }

    private ImageView detailBackButton() {
        ImageView back = new ImageView(this);
        back.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        back.setPadding(dp(9), dp(9), dp(9), dp(9));
        Drawable icon = Compat.tintedDrawable(this, R.drawable.ic_arrow_back, this.TEXT);
        if (icon != null) back.setImageDrawable(icon);
        Compat.setBackground(back, UiComponents.round(this, this.themeTokens.dockSurface(),
                18, this.session.uiScale() / 100.0f));
        if (Build.VERSION.SDK_INT >= 21) back.setElevation(dp(6));
        back.setContentDescription("返回");
        back.setOnClickListener(view ->
                runWithPressFeedback(back, this::returnFromDetailSmooth));
        return back;
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

    private LinearLayout detailArticleSurface() {
        LinearLayout article = vertical(this.BG);
        int horizontal = dp(usesRoundLayout() ? 8 : 4);
        article.setPadding(horizontal, dp(8), horizontal, dp(12));
        return article;
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
        View page = this.userSpacePage.create(userId, fallbackName,
                fallbackAvatar, pageHorizontalPadding(), subpageTopPadding());
        this.content.removeAllViews();
        this.content.addView(page, match());
    }

    private void showProfile() {
        if ("checkin_center".equals(this.screen) && this.checkinCenterPage != null) {
            this.checkinCenterPage.onPause();
        }
        if ("feed".equals(this.screen)) {
            this.pendingLateralPush = true;
        }
        this.profilePage.show();
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
        this.title.setText("设置");
        this.action.setVisibility(4);
        View settingsHome = buildSettingsHomeContent();
        this.retainedPages.put("settings_home", settingsHome);
        transitionTo(settingsHome);
    }

    private View buildSettingsHomeContent() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = vertical(this.BG);
        int horizontal = pageHorizontalPadding();
        page.setPadding(horizontal, subpageTopPadding(), horizontal, dp(14));
        scroll.addView(page);
        page.addView(settingsTopCard("设置"));
        LinearLayout panel = this.settingsUi.list();
        this.settingsUi.addEntry(panel, "显示", null,
                this.session.darkMode() ? "深色" : "浅色",
                R.drawable.il_palette, this::showDisplaySettings);
        this.settingsUi.addEntry(panel, "启动与更新", null, null,
                R.drawable.il_refresh, this::showStartupSettings);
        this.settingsUi.addEntry(panel, "内容与缓存", null, null,
                R.drawable.il_globe, this::showAppSettings);
        this.settingsUi.addEntry(panel, "关于", null, appVersion(),
                R.drawable.il_info, this::showAbout);
        page.addView(panel);
        return scroll;
    }

    private void showCheckinCenter() {
        stopQrPolling();
        this.screen = "checkin_center";
        setBottomNavVisible(false);
        this.leading.setVisibility(0);
        this.leading.setOnClickListener(view -> {
            this.pendingBackTransition = true;
            showProfile();
        });
        this.title.setText("小黑盒签到");
        this.action.setVisibility(4);
        this.action.setOnClickListener(null);
        if (this.checkinCenterPage == null) {
            this.checkinCenterPage = new CheckinCenterPage(this, this.session,
                    this.checkinCenterCoordinator, this.themeTokens,
                    new CheckinCenterPage.Host() {
                        @Override
                        public void closePage() {
                            MainActivity.this.pendingBackTransition = true;
                            MainActivity.this.showProfile();
                        }

                        @Override
                        public void openCaptcha(String verificationUri) {
                            try {
                                MainActivity.this.startActivityForResult(
                                        CheckinCaptchaActivity.intent(
                                                MainActivity.this, verificationUri,
                                                MainActivity.this.usesRoundLayout()),
                                        REQUEST_CHECKIN_CAPTCHA);
                            } catch (RuntimeException error) {
                                if (MainActivity.this.checkinCenterPage != null) {
                                    MainActivity.this.checkinCenterPage.onCaptchaCancelled(
                                            "当前系统无法打开安全验证");
                                }
                            }
                        }

                        @Override
                        public void confirmRevoke(Runnable confirmed) {
                            MainActivity.this.showLiteDialog("撤销此设备",
                                    "撤销后，这台设备需要重新连接才能查看或执行小黑盒签到。服务器中的定时任务不会自动删除。",
                                    "撤销", confirmed, "取消", null, null, null);
                        }

                        @Override
                        public void showMessage(String message) {
                            MainActivity.this.toast(message);
                        }
                    });
        } else {
            this.checkinCenterPage.refresh();
        }
        this.checkinCenterPage.onResume();
        View page = this.checkinCenterPage.view();
        this.retainedPages.put("checkin_center", page);
        transitionTo(page);
    }

    private void showReadingCenter() {
        this.savedContentController.showReadingCenter();
    }

    private void prepareReadingCenterChrome() {
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

    private void showFavorites() {
        this.savedContentController.showFavorites();
    }

    private void prepareSavedPageChrome(String pageTitle) {
        stopQrPolling();
        this.screen = "saved";
        setBottomNavVisible(false);
        this.leading.setVisibility(0);
        this.leading.setOnClickListener(view -> returnFromSavedPage());
        this.title.setText(pageTitle);
        this.action.setVisibility(4);
        this.content.removeAllViews();
    }

    private FeedAdapter createFeedAdapter(List<FeedItem> items) {
        return this.feedPage.createAdapter(items);
    }

    private void ensureEmojiCatalog(Runnable ready) {
        if (this.api == null) {
            return;
        }
        EmojiStore.load(this.api, ready);
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
        int horizontal = pageHorizontalPadding();
        page.setPadding(horizontal, subpageTopPadding(), horizontal, dp(14));
        scroll.addView(page);
        page.addView(settingsTopCard(pageTitle));
        this.retainedPages.put(key, scroll);
        if (this.immediatePageReplacement) {
            this.immediatePageReplacement = false;
            this.content.removeAllViews();
            this.content.addView(scroll, match());
            Motions.reset(scroll);
        } else if (this.shellAnimating) {
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

    private View settingsTopCard(String pageTitle) {
        return this.settingsUi.topCard(pageTitle);
    }

    private void showDisplaySettings() {
        this.displaySettingsPage.showSettings();
    }

    private void showDisplayPreview() {
        this.displaySettingsPage.showPreview();
    }

    private void showAppSettings() {
        this.appSettingsPage.showContentAndCache();
    }

    private void showStartupSettings() {
        this.appSettingsPage.showStartup();
    }

    private void showAbout() {
        this.noticeCenter.showAbout();
    }

    private static int blend(int base, int overlay, float amount) {
        return ThemeTokens.blend(base, overlay, amount);
    }

    private String appVersion() {
        return BuildConfig.VERSION_NAME;
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
        Bitmap preview = null;
        if (drawable instanceof BitmapDrawable) {
            preview = ((BitmapDrawable) drawable).getBitmap();
        }
        ImageViewerActivity.preparePreview(current, preview, source);
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
        this.updateInstaller.openExternal(url);
    }

    private void openUpdateUrl(String url) {
        this.updateInstaller.openUpdate(url);
    }

    private void uploadDiagnostics() {
        this.diagnosticsController.upload();
    }

    private void showPendingCrashDialog() {
        this.diagnosticsController.showPendingCrash();
    }

    private void exportDiagnostics() {
        this.diagnosticsController.export();
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
        if ("checkin_center".equals(this.screen)) {
            if (this.checkinCenterPage != null && this.checkinCenterPage.handleBack()) {
                this.pendingBackTransition = false;
                return;
            }
            showProfile();
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
            String savedReturn = this.savedContentController.returnScreen();
            if ("reading_center".equals(savedReturn)) showReadingCenter();
            else if ("favorites".equals(savedReturn)) showFavorites();
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
        this.savedContentController.cancelRequests();
        String savedReturn = this.savedContentController.returnScreen();
        if ("reading_center".equals(savedReturn)) {
            showReadingCenter();
        } else if ("favorites".equals(savedReturn)) {
            showFavorites();
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
        if ("search".equals(this.screen)) this.searchPage.restoreListPosition();
        if ("feed".equals(this.screen)) this.feedPage.restoreScroll();
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CHECKIN_CAPTCHA || this.checkinCenterPage == null) return;
        if (resultCode == RESULT_OK && data != null) {
            String ticket = data.getStringExtra(CheckinCaptchaActivity.EXTRA_TICKET);
            String randstr = data.getStringExtra(CheckinCaptchaActivity.EXTRA_RANDSTR);
            if (CheckinCenterClient.captchaProofValid(ticket, randstr)
                    && ticket != null && !ticket.trim().isEmpty()) {
                if (this.localCache != null) {
                    this.localCache.log("captcha result=success");
                }
                this.checkinCenterPage.onCaptchaResult(ticket, randstr);
                return;
            }
        }
        String message = data == null ? "安全验证已取消"
                : data.getStringExtra(CheckinCaptchaActivity.EXTRA_ERROR);
        String diagnosticCode = data == null ? "no_result"
                : data.getStringExtra(CheckinCaptchaActivity.EXTRA_DIAGNOSTIC);
        if (diagnosticCode == null || !diagnosticCode.matches("[a-z0-9_]{1,48}")) {
            diagnosticCode = "unknown";
        }
        if (this.localCache != null) {
            this.localCache.log("captcha result=cancelled reason=" + diagnosticCode);
        }
        this.checkinCenterPage.onCaptchaCancelled(message);
    }

    @Override
    protected void onResume() {
        super.onResume();
        this.activityResumed = true;
        if (this.checkinCenterPage != null && "checkin_center".equals(this.screen)) {
            this.checkinCenterPage.onResume();
        }
        if ("login".equals(this.screen) && this.qrLoginPage != null) {
            this.qrLoginPage.resume();
        }
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
        this.crownScrollDispatcher.cancel();
        if (this.checkinCenterPage != null) this.checkinCenterPage.onPause();
        if (this.qrLoginPage != null) this.qrLoginPage.pause();
        if (this.readingTimeTracker != null) this.readingTimeTracker.pause();
        this.handler.removeCallbacks(this.presenceTick);
        saveCurrentDetailProgress();
        super.onPause();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level < ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) return;
        ImageLoader.clear();
        clearSnapshots(this.screenSnapshots);
        clearSnapshots(this.fullScreenSnapshots);
    }

    @Override
    public void onLowMemory() {
        ImageLoader.clear();
        clearSnapshots(this.screenSnapshots);
        clearSnapshots(this.fullScreenSnapshots);
        super.onLowMemory();
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
        this.crownScrollDispatcher.cancel();
        if (this.readingTimeTracker != null) this.readingTimeTracker.pause();
        if (this.checkinCenterPage != null) {
            this.checkinCenterPage.close();
            this.checkinCenterPage = null;
        }
        if (this.checkinCenterCoordinator != null) {
            this.checkinCenterCoordinator.close();
            this.checkinCenterCoordinator = null;
        }
        saveCurrentDetailProgress();
        stopQrPolling();
        this.pageTransitions.cancelNow();
        if (this.feedPage != null) this.feedPage.close();
        if (this.detailPager != null) this.detailPager.cancelMotion();
        if (this.content instanceof BackSwipeFrameLayout) {
            ((BackSwipeFrameLayout) this.content).cancelMotion();
        }
        ImageLoader.cancelTree(this.content);
        clearSnapshots(this.screenSnapshots);
        clearSnapshots(this.fullScreenSnapshots);
        this.retainedPages.clear();
        if (this.searchPage != null) this.searchPage.close();
        if (this.searchBars != null) this.searchBars.clear();
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
        this.crownScrollDispatcher.cancel();
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
        this.pageTransitions.setCompactMotion(usesWatchLayout());
        this.pageTransitions.run(this.content, next, !back, push);
    }

    private void cancelAllMotion() {
        this.pageTransitions.finishNow();
        if (this.detailPager != null) this.detailPager.cancelMotion();
        if (this.content instanceof BackSwipeFrameLayout) {
            ((BackSwipeFrameLayout) this.content).cancelMotion();
        }
        Motions.resetTree(this.shellRoot);
        if (this.feedPage != null) this.feedPage.finishMotion();
        this.shellAnimating = false;
        this.pendingBackTransition = false;
        this.pendingLateralPush = false;
        this.bottomNavShowPending = false;
        this.bottomNavAnimSerial++;
        if (this.bottom != null) {
            this.bottom.animate().cancel();
            this.bottom.setTranslationY(0.0f);
            this.bottom.setScaleX(1.0f);
            this.bottom.setScaleY(1.0f);
            this.bottom.setAlpha(this.bottomVisible ? 1.0f : 0.0f);
            this.bottom.setVisibility(this.bottomVisible ? View.VISIBLE : View.GONE);
        }
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

    private Typeface appRegularTypeface() {
        return Typeface.DEFAULT;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
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
