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
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

@SuppressLint("WrongConstant")
public final class MainActivity extends Activity implements BackSwipeFrameLayout.Host {
    private static final int REQUEST_CHECKIN_CAPTCHA = 9134;
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
    private DetailLoadCoordinator detailLoader;
    private DetailPageAssembler detailPageAssembler;
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
    private BottomNavigationController bottomNavigation;
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
    private String userSpaceReturnScreen = "feed";
    private final ContentNavigationHistory<DetailNavigationState> detailHistory =
            new ContentNavigationHistory<>();
    private long lastExitBackAt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PageTransitionController pageTransitions = new PageTransitionController();
    private CrownInputHandler crownInput;
    private SearchBarController searchBars;
    private SearchPage searchPage;
    private boolean pendingBackTransition;
    private boolean pendingLateralPush;
    private boolean immediatePageReplacement;
    private final TransitionSnapshotStore screenSnapshots = new TransitionSnapshotStore();
    private final TransitionSnapshotStore fullScreenSnapshots = new TransitionSnapshotStore();
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
        this.crownInput = new CrownInputHandler(this, this.session,
                new CrownInputHandler.Host() {
                    @Override public String screen() {
                        return MainActivity.this.screen;
                    }
                    @Override public View detailScrollTarget() {
                        return MainActivity.this.detailPager != null
                                && MainActivity.this.detailPager.showingComments()
                                ? MainActivity.this.detailCommentScroll
                                : MainActivity.this.detailScroll;
                    }
                    @Override public View feedScrollTarget() {
                        return MainActivity.this.feedPage == null
                                ? null : MainActivity.this.feedPage.listView();
                    }
                    @Override public View searchScrollTarget() {
                        return MainActivity.this.searchPage == null
                                ? null : MainActivity.this.searchPage.listView();
                    }
                    @Override public View contentRoot() {
                        return MainActivity.this.content;
                    }
                });
        Motions.setLevel(this.session.motionLevel());
        this.pageTransitions.setCompactMotion(usesWatchLayout());
        this.localCache = new LocalCache(this);
        this.cacheMaintenance = new CacheMaintenance(this, this.localCache, this.handler);
        boolean pendingCrashReport = CrashReporter.hasPendingCrashReport(this);
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
        if (this.crownInput != null && this.crownInput.handle(event)) return true;
        return super.dispatchGenericMotionEvent(event);
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
        if (this.qrLoginPage != null) {
            this.qrLoginPage.close();
            this.qrLoginPage = null;
        }
        if (this.checkinCenterPage != null) {
            this.checkinCenterPage.close();
            this.checkinCenterPage = null;
        }
        discardRetainedLayoutViews();
        initializeSettingsFeatures();
        initializeContentFeatures();
        initializeDetailFeatures();
        buildShellView();
    }

    private void initializeSettingsFeatures() {
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
                this.settingsUi, this.themeTokens, this.crownInput.scrollController(),
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
        this.noticeCenter = new NoticeCenter(this, this.session, this.localCache,
                this.settingsUi, this.liteDialogs, this.themeTokens, usesRoundLayout(),
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
    }

    private void initializeContentFeatures() {
        MainContentFeatures features = new MainContentFeatures(this, this.session, this.api,
                this.writeActions, this.localCache, this.themeTokens, this.searchBars,
                this.settingsUi, this.checkinCenterCoordinator, this.handler,
                contentFeatureHost());
        this.detailContentRenderer = features.detailContentRenderer;
        this.userSpacePage = features.userSpacePage;
        this.savedContentController = features.savedContentController;
        this.profilePage = features.profilePage;
        this.qrLoginPage = features.qrLoginPage;
        this.postActions = features.postActions;
        this.feedPage = features.feedPage;
    }

    private MainContentFeatures.Host contentFeatureHost() {
        return new MainContentFeatures.Host() {
            @Override public String screen() { return MainActivity.this.screen; }
            @Override public String currentLinkId() { return MainActivity.this.currentLinkId; }
            @Override public String currentLinkHsrc() { return currentLinkHsrc; }
            @Override public FeedItem currentDetailItem() { return currentDetailItem; }
            @Override public List<FeedItem> searchItems() { return searchPage.items(); }
            @Override public void prepareReadingCenter() { prepareReadingCenterChrome(); }
            @Override public void prepareSavedPage(String value) {
                prepareSavedPageChrome(value);
            }
            @Override public void prepareProfile() {
                activate("profile");
                title.setText("我的");
                action.setVisibility(View.INVISIBLE);
            }
            @Override public void prepareLogin() {
                screen = "login";
                shellBar.setVisibility(View.GONE);
                setBottomNavVisible(false);
                leading.setVisibility(View.INVISIBLE);
                action.setVisibility(View.INVISIBLE);
                title.setText("扫码登录");
            }
            @Override public void prepareFeed() {
                activate("feed");
                title.setText("社区");
                action.setText("");
                setIcon(action, R.drawable.ic_refresh, TEXT, 19);
                action.setVisibility(View.INVISIBLE);
                action.setOnClickListener(view -> feedPage.load(true));
            }
            @Override public void showContent(View view) {
                content.removeAllViews();
                content.addView(view, match());
            }
            @Override public void showPage(View view) { transitionTo(view); }
            @Override public void retainPage(String key, View view) {
                retainedPages.put(key, view);
            }
            @Override public void showLoading() { MainActivity.this.showLoading(); }
            @Override public void showProfileLoading() {
                transitionTo(detailLoadingPage());
            }
            @Override public void hideLoading() { MainActivity.this.hideLoading(); }
            @Override public void showMessage(String message) {
                MainActivity.this.showMessage(message);
            }
            @Override public void showToast(String message) { toast(message); }
            @Override public void showProfile() { MainActivity.this.showProfile(); }
            @Override public void showLogin() { MainActivity.this.showLogin(); }
            @Override public void showFeed() { MainActivity.this.showFeed(); }
            @Override public void showSearch() { MainActivity.this.showSearch(); }
            @Override public void showReadingCenter() {
                MainActivity.this.showReadingCenter();
            }
            @Override public void showReadingStats() {
                MainActivity.this.showReadingStats();
            }
            @Override public void showFavorites() { MainActivity.this.showFavorites(); }
            @Override public void showCheckinCenter() {
                MainActivity.this.showCheckinCenter();
            }
            @Override public void showSettings() { showSettingsHome(); }
            @Override public void showDetail(FeedItem item) {
                MainActivity.this.showDetail(item);
            }
            @Override public void showUserSpace(String id, String name, String avatar) {
                MainActivity.this.showUserSpace(id, name, avatar);
            }
            @Override public void openImage(ImageView source, String url) {
                MainActivity.this.openImage(source, url);
            }
            @Override public void openImages(ImageView source, String[] urls, int index) {
                MainActivity.this.openImage(source, urls, index);
            }
            @Override public void addBottomSpace(LinearLayout page) {
                addBottomNavSafeSpace(page);
            }
            @Override public void loginCompleted() {
                feedPage.clearItems();
                profilePage.invalidate();
                toast("登录成功");
                EmojiStore.load(api, () -> { });
                showFeed();
                PresenceReporter.pingNow(session, readingTimeTracker,
                        MainActivity.this::applyAccessStatus);
                RemoteConfig.load(session.userId(), () ->
                        applyAccessStatus(RemoteConfig.accessStatus()));
            }
            @Override public void feedChanged() { feedPage.notifyItemsChanged(); }
            @Override public void setFeedRefreshBusy(boolean busy) {
                action.setEnabled(!busy);
                action.setAlpha(busy ? 0.45f : 1f);
            }
            @Override public void markFeedLateralTransition() {
                if ("profile".equals(screen)) {
                    pendingBackTransition = true;
                    pendingLateralPush = true;
                }
            }
            @Override public FeedAdapter createFeedAdapter(List<FeedItem> items) {
                return MainActivity.this.createFeedAdapter(items);
            }
            @Override public String readingSummary() { return readingEntrySummary(); }
            @Override public boolean savedScreenActive() {
                return "saved".equals(screen);
            }
            @Override public int pageHorizontalPadding() {
                return MainActivity.this.pageHorizontalPadding();
            }
            @Override public int pageTopPadding() {
                return MainActivity.this.pageTopPadding();
            }
            @Override public int subpageTopPadding() {
                return MainActivity.this.subpageTopPadding();
            }
            @Override public int roundHeaderInset() {
                return roundHorizontalInset(RoundLayoutMetrics.HEADER_HORIZONTAL_RATIO, 10);
            }
            @Override public int roundHeaderTopPadding() {
                return RoundLayoutMetrics.componentInset(screenMetrics().heightPixels,
                        RoundLayoutMetrics.PAGE_TOP_RATIO, dp(9));
            }
            @Override public int roundSearchInset() {
                return roundHorizontalInset(RoundLayoutMetrics.SEARCH_HORIZONTAL_RATIO, 6);
            }
        };
    }

    private void initializeDetailFeatures() {
        MainDetailFeatures detail = new MainDetailFeatures(this, this.session, this.api,
                this.writeActions, this.localCache, this.themeTokens, this.postActions,
                this.detailContentRenderer, this.readingTimeTracker, this.handler,
                new MainDetailFeatures.Host() {
                    @Override public FeedItem currentItem() { return currentDetailItem; }
                    @Override public JSONObject currentBody() { return currentDetailBody; }
                    @Override public String currentLinkId() { return currentLinkId; }
                    @Override public String currentAuthCode() { return currentAuthCode; }
                    @Override public DetailPager currentPager() { return detailPager; }
                    @Override public boolean detailActive(FeedItem item) {
                        return "detail".equals(screen) && item != null
                                && item.id.equals(currentLinkId);
                    }
                    @Override public int dp(int value) { return MainActivity.this.dp(value); }
                    @Override public int pageHorizontalPadding() {
                        return MainActivity.this.pageHorizontalPadding();
                    }
                    @Override public int subpageTopPadding() {
                        return MainActivity.this.subpageTopPadding();
                    }
                    @Override public int roundHeaderInnerInset() {
                        return MainActivity.this.roundHeaderInnerInset();
                    }
                    @Override public boolean watchLayout() { return usesWatchLayout(); }
                    @Override public boolean roundLayout() { return usesRoundLayout(); }
                    @Override public void reloadDetail() {
                        if (currentDetailItem != null) showDetail(currentDetailItem);
                    }
                    @Override public void showUserSpace(String id, String name, String avatar) {
                        MainActivity.this.showUserSpace(id, name, avatar);
                    }
                    @Override public void openImage(ImageView source, String url) {
                        MainActivity.this.openImage(source, url);
                    }
                    @Override public void hideLoading() { MainActivity.this.hideLoading(); }
                    @Override public void renderDetail(JSONObject body, FeedItem fallback) {
                        MainActivity.this.renderDetail(body, fallback);
                    }
                    @Override public void showMessage(String message) {
                        MainActivity.this.showMessage(message);
                    }
                    @Override public void showToast(String message) { toast(message); }
                    @Override public LinearLayout articleSurface() {
                        return detailArticleSurface();
                    }
                    @Override public View detailReturnPreview() {
                        return MainActivity.this.detailReturnPreview();
                    }
                    @Override public View detailBackButton() { return detailBackButton(); }
                });
        this.detailHeaderRenderer = detail.headerRenderer;
        this.commentController = detail.commentController;
        this.commentRenderer = detail.commentRenderer;
        this.detailCommentsSection = detail.commentsSection;
        this.detailActionBar = detail.actionBar;
        this.detailLoader = detail.loader;
        this.detailPageAssembler = detail.pageAssembler;
    }

    private void buildShellView() {
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
        this.bottomNavigation = new BottomNavigationController(this, this.session,
                this.themeTokens, usesRoundLayout(), body, this::runWithPressFeedback);
        this.bottomNavigation.addItem("社区", "feed", R.drawable.ic_nav_home,
                this::onFeedNavClick);
        this.bottomNavigation.addItem("我的", "profile", R.drawable.ic_nav_profile, () -> {
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

    private void setBottomNavVisible(boolean visible) {
        setBottomNavVisible(visible, true);
    }

    private void setBottomNavVisible(boolean visible, boolean animate) {
        if (this.bottomNavigation != null) {
            this.bottomNavigation.setVisible(visible, animate, this.shellAnimating);
        }
    }

    private void addBottomNavSafeSpace(LinearLayout page) {
        if (page == null) {
            return;
        }
        View spacer = new View(this);
        int height = this.bottomNavigation == null
                ? dp(76) : this.bottomNavigation.bottomSafeSpace();
        page.addView(spacer, new LinearLayout.LayoutParams(-1, height));
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
        if ("user_space".equals(this.screen) && !this.detailHistory.isEmpty()) {
            discardDetailHistory();
        }
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
            return this.detailHistory.isEmpty() ? this.userSpaceReturnScreen : "detail";
        }
        if ("saved".equals(this.screen)) return this.savedContentController.returnScreen();
        return ScreenRoutes.staticParentOrDefault(this.screen, "feed");
    }

    private boolean canDetailSwipeBack() {
        return "detail".equals(this.screen);
    }

    private void captureShellSnapshot(String key, View view) {
        this.screenSnapshots.capture(key, 8, view, this.BG, this.localCache);
    }

    private void captureFullScreenSnapshot(String key) {
        this.fullScreenSnapshots.capture(key, 4, this.shellRoot, this.BG, this.localCache);
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
        this.fullScreenSnapshots.registerOverlay(overlay, bitmap);
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
                this.fullScreenSnapshots.releaseOverlay(overlay);
            }, 48L);
        });
    }

    private View realShellPreview(String key) {
        DetailNavigationState previousDetail = "detail".equals(key)
                ? this.detailHistory.peek() : null;
        View target = previousDetail != null ? previousDetail.root
                : "feed".equals(key) ? this.feedPage.cachedView()
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
        if (this.bottomNavigation != null) this.bottomNavigation.select(key);
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
        if ("detail".equals(targetKey) && restorePreviousDetail(true)) return;
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
            this.detailReturn = this.screen;
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
        this.detailLoader.load(item);
    }

    private void renderDetail(JSONObject body, FeedItem fallback) {
        boolean replacing = this.detailLoader.rendered();
        boolean previousComments = replacing && this.detailPager != null
                && this.detailPager.showingComments();
        int previousArticleScroll = replacing && this.detailScroll != null
                ? this.detailScroll.getScrollY() : 0;
        int previousCommentScroll = replacing && this.detailCommentScroll != null
                ? this.detailCommentScroll.getScrollY() : 0;
        DetailPageAssembler.Result detail = this.detailPageAssembler.assemble(
                body, fallback, this.currentLinkHsrc, this.screen, this.currentLinkId,
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
        this.currentDetailBody = detail.body;
        this.currentLinkHsrc = detail.hsrc;
        if (!detail.authCode.isEmpty()) this.currentAuthCode = detail.authCode;
        this.lastDetailDiagnostics = detail.diagnostics;
        this.detailPager = detail.pager;
        installDetailRoot(detail.root, detail.pager, replacing, previousArticleScroll,
                previousCommentScroll, previousComments,
                detail.articleScroll, detail.commentScroll);
        this.detailCommentsSection.populate(detail.articleCommentHost, detail.comments,
                detail.pager, 72L, detail.articleScroll, previousArticleScroll);
        this.detailCommentsSection.populate(detail.commentPageHost, detail.comments,
                detail.pager, 140L, detail.commentScroll, previousCommentScroll);
        this.detailLoader.markRendered();
        if (this.activityResumed && this.readingTimeTracker != null) {
            this.readingTimeTracker.start(fallback.article, fallback.id);
        }
        this.detailScroll = detail.articleScroll;
        this.detailCommentScroll = detail.commentScroll;
        int savedScroll = this.session.rememberDetailScroll()
                ? this.localCache.scroll(this.currentLinkId) : 0;
        if (!replacing && savedScroll > 0) {
            detail.articleScroll.postDelayed(
                    () -> detail.articleScroll.scrollTo(0, savedScroll), 80L);
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
                        - this.detailLoader.loadStartedAt())
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
            DetailNavigationState state = suspendCurrentDetail();
            if (state == null) return;
            this.detailHistory.push(state);
        } else {
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

    private String appVersion() {
        return BuildConfig.VERSION_NAME;
    }

    private void openImage(ImageView source, String url) {
        openImage(source, new String[]{url}, 0);
    }

    private void openImage(ImageView source, String[] urls, int index) {
        ImageViewerLauncher.open(this, source, urls, index);
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
        this.detailLoader.cancel();
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
        if (restorePreviousDetail(false)) return;
        String returnScreen = this.userSpaceReturnScreen;
        this.userSpaceReturnScreen = "feed";
        if ("profile".equals(returnScreen)) {
            showProfile();
        } else if ("reading_center".equals(returnScreen)) {
            showReadingCenter();
        } else {
            showFeed();
        }
    }

    private DetailNavigationState suspendCurrentDetail() {
        this.pageTransitions.finishNow();
        saveCurrentDetailProgress();
        View root = this.content == null || this.content.getChildCount() == 0
                ? null : this.content.getChildAt(this.content.getChildCount() - 1);
        if (root == null || this.detailPager == null || this.currentDetailItem == null) {
            return null;
        }
        this.detailPager.cancelMotion();
        DetailLoadCoordinator.State loadState = this.detailLoader.suspend();
        DetailNavigationState state = new DetailNavigationState(
                root, this.detailPager, this.detailScroll, this.detailCommentScroll,
                this.currentDetailItem, this.detailReturn, this.detailReturnView,
                this.detailReturnTitle, this.currentLinkId, this.currentLinkHsrc,
                this.currentAuthCode, this.lastDetailDiagnostics,
                this.currentDetailBody, loadState);
        this.detailPager = null;
        this.detailScroll = null;
        this.detailCommentScroll = null;
        this.detailReturnView = null;
        this.currentDetailBody = null;
        if (this.readingTimeTracker != null) {
            this.readingTimeTracker.pause();
            updateReadingTimeEntry();
        }
        return state;
    }

    private boolean restorePreviousDetail(boolean alreadyAttached) {
        DetailNavigationState state = this.detailHistory.pop();
        if (state == null) return false;
        this.detailLoader.restore(state.loadState);
        this.currentDetailItem = state.item;
        this.detailReturn = state.returnScreen;
        this.detailReturnView = state.returnView;
        this.detailReturnTitle = state.returnTitle;
        this.currentLinkId = state.linkId;
        this.currentLinkHsrc = state.linkHsrc;
        this.currentAuthCode = state.authCode;
        this.lastDetailDiagnostics = state.diagnostics;
        this.currentDetailBody = state.body;
        this.detailPager = state.pager;
        this.detailScroll = state.articleScroll;
        this.detailCommentScroll = state.commentScroll;
        this.screen = "detail";
        if (this.shellBar != null) this.shellBar.setVisibility(View.GONE);
        setBottomNavVisible(false, false);
        this.leading.setVisibility(View.VISIBLE);
        this.leading.setOnClickListener(view -> returnFromDetailSmooth());
        this.action.setVisibility(View.INVISIBLE);
        updateDetailPagerTitle();
        Motions.resetTree(state.root);
        if (!alreadyAttached) {
            if (state.root.getParent() instanceof ViewGroup) {
                ((ViewGroup) state.root.getParent()).removeView(state.root);
            }
            this.pendingBackTransition = true;
            transitionTo(state.root);
        }
        if (this.activityResumed && this.readingTimeTracker != null) {
            this.readingTimeTracker.start(state.item.article, state.item.id);
        }
        return true;
    }

    private void discardDetailHistory() {
        for (DetailNavigationState state : this.detailHistory.drain()) {
            state.pager.cancelMotion();
            state.pager.takeReturnView();
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
        this.crownInput.cancel();
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
        this.screenSnapshots.clear();
        this.fullScreenSnapshots.clear();
    }

    @Override
    public void onLowMemory() {
        ImageLoader.clear();
        this.screenSnapshots.clear();
        this.fullScreenSnapshots.clear();
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
        this.crownInput.cancel();
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
        if (this.qrLoginPage != null) {
            this.qrLoginPage.close();
            this.qrLoginPage = null;
        }
        this.pageTransitions.cancelNow();
        discardDetailHistory();
        if (this.feedPage != null) this.feedPage.close();
        if (this.detailPager != null) this.detailPager.cancelMotion();
        if (this.content instanceof BackSwipeFrameLayout) {
            ((BackSwipeFrameLayout) this.content).cancelMotion();
        }
        ImageLoader.cancelTree(this.content);
        this.screenSnapshots.releaseAll();
        this.fullScreenSnapshots.releaseAll();
        this.retainedPages.clear();
        if (this.searchPage != null) this.searchPage.close();
        if (this.searchBars != null) this.searchBars.clear();
        if (this.writeActions != null) this.writeActions.close();
        if (this.detailLoader != null) this.detailLoader.close();
        this.handler.removeCallbacksAndMessages(null);
        if (this.writeTokenProvider != null) {
            this.writeTokenProvider.close();
        }
        if (this.api != null) {
            this.api.close();
        }
        if (this.cacheMaintenance != null) this.cacheMaintenance.close();
        if (this.diagnosticsController != null) this.diagnosticsController.close();
        super.onDestroy();
    }

    /** 页面切换统一入口：真实双 View 转场；方向由 pendingBackTransition 决定，消费后复位。 */
    private void transitionTo(View next) {
        this.crownInput.cancel();
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
        if (this.bottomNavigation != null) this.bottomNavigation.finishMotion();
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
