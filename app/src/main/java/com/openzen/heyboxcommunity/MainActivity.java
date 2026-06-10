package com.openzen.heyboxcommunity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
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
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
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
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MainActivity extends Activity {
    private interface SavedListFallback {
        boolean onFallback(String reason);
    }

    private interface WriteRequest {
        void start(ApiClient.Callback callback);
    }

    private static final class LikeState {
        final boolean liked;
        final int likes;

        LikeState(boolean liked, int likes) {
            this.liked = liked;
            this.likes = Math.max(0, likes);
        }
    }

    private static final int REPLY_PREVIEW_COUNT = 2;
    private static final int REPLY_PAGE_SIZE = 5;
    private static final int NAV_BAR_HEIGHT_DP = 30;
    private static final int NAV_ICON_SIZE_DP = 20;
    private static final String TRANSITION_OVERLAY_TAG = "shell_transition_overlay";
    private static final String WELCOME_ANNOUNCEMENT_ID = "welcome-heybox-lite-1.77";
    private static final String[] THEME_NAMES = {
            "默认蓝", "红色", "粉色", "紫色", "绿色", "青色",
            "橙色", "黄色", "灰色", "深蓝", "黑金", "薄荷绿"
    };
    private static final int[][] THEME_COLORS = {
            {0xFF2479B8, 0xFF73B8E6}, {0xFFC33A3A, 0xFFEF7777},
            {0xFFD85C91, 0xFFF0A4C1}, {0xFF7652B5, 0xFFB79ADF},
            {0xFF278A57, 0xFF71C798}, {0xFF168B91, 0xFF6BC7CB},
            {0xFFD36B24, 0xFFF0A064}, {0xFFC39A16, 0xFFF0CF68},
            {0xFF5D636A, 0xFFAEB4BA}, {0xFF173F76, 0xFF5D8BC4},
            {0xFF171717, 0xFFD0A83E}, {0xFF318B73, 0xFF88D8BF}
    };
    private static final String TITLE_FAVORITES = "\u6211\u7684\u6536\u85cf";
    private static final String MSG_OFFLINE_CACHE = "\u5df2\u663e\u793a\u79bb\u7ebf\u7f13\u5b58";
    private static final String MSG_EMPTY_CONTENT = "\u8fd9\u91cc\u6682\u65f6\u6ca1\u6709\u5185\u5bb9";
    private static final String MSG_FAVORITES_UNAVAILABLE =
            "\u6211\u7684\u6536\u85cf\u6682\u4e0d\u53ef\u7528\n"
                    + "\u5c0f\u9ed1\u76d2\u63a5\u53e3\u672a\u8fd4\u56de\u53ef\u8bbf\u95ee\u7684\u6536\u85cf\u5939\uff0c"
                    + "\u8fd9\u901a\u5e38\u662f\u8d26\u53f7\u6743\u9650\u6216\u5f53\u524d\u7f51\u9875\u63a5\u53e3\u9650\u5236\u3002\n"
                    + "\u5df2\u4fdd\u7559\u73b0\u6709\u7f13\u5b58\uff0c\u53ef\u7a0d\u540e\u518d\u8bd5\u3002";

    private int BG;
    private int PANEL;
    private int TEXT;
    private int MUTED;
    private int PRIMARY;
    private int SECONDARY;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable qrPollTask = new Runnable() {
        @Override public void run() {
            pollQr();
        }
    };
    private final List<FeedItem> feed = new ArrayList<>();
    private SessionStore session;
    private ApiClient api;
    private WriteTokenProvider writeTokenProvider;
    private SignInManager signInManager;
    private LinearLayout shellRoot;
    private FrameLayout content;
    private LinearLayout bottom;
    private TextView title;
    private TextView leading;
    private TextView action;
    private FeedAdapter feedAdapter;
    private ListView feedListView;
    private ListView cachedFeedListView;
    private TextView feedFooter;
    private ScrollView detailScroll;
    private DetailPager detailPager;
    private boolean shellAnimating;
    private LocalCache localCache;
    private final Map<View, Integer> searchBarHeights = new HashMap<>();
    private final Map<View, Boolean> searchBarStates = new HashMap<>();
    private final Map<String, LikeState> linkLikeOverrides = new HashMap<>();
    private final Map<String, Bitmap> screenSnapshots = new HashMap<>();
    private final Map<String, Bitmap> fullScreenSnapshots = new HashMap<>();
    private String screen = "feed";
    private String qrKey;
    private boolean pollingQr;
    private boolean feedLoadingMore;
    private boolean feedRefreshing;
    private boolean feedNoMore;
    private boolean feedLoadMoreFailed;
    private int feedOffset;
    private int feedRequestSerial;
    private int feedResetSerial;
    private int feedFirstVisible;
    private int feedFirstTop;
    private String detailReturn = "feed";
    private String currentLinkId = "";
    private String currentLinkHsrc = "";
    private String currentAuthCode = "";
    private FeedItem currentDetailItem;
    private int detailRequestToken;
    private String lastDetailDiagnostics = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!AppIntegrityCheck.isTrusted(this)) {
            TextView blocked = new TextView(this);
            blocked.setText("应用签名校验失败，请安装官方构建版本。");
            blocked.setTextColor(Color.WHITE);
            blocked.setTextSize(16);
            blocked.setGravity(Gravity.CENTER);
            blocked.setPadding(32, 32, 32, 32);
            blocked.setBackgroundColor(Color.rgb(14, 15, 16));
            setContentView(blocked);
            return;
        }
        session = new SessionStore(this);
        localCache = new LocalCache(this);
        applyPalette();
        Compat.colorSystemBars(getWindow(), BG);
        getWindow().getDecorView().setSystemUiVisibility(Compat.fullscreenFlags());
        api = new ApiClient(session);
        writeTokenProvider = new WriteTokenProvider(this, session, api);
        signInManager = new SignInManager(session, api, writeTokenProvider, message -> {
            if (localCache != null) localCache.log(message);
        });
        buildShell();
        if (session.isLoggedIn()) {
            EmojiStore.load(api, () -> {
                if ("feed".equals(screen) && feedAdapter != null) feedAdapter.notifyDataSetChanged();
            });
        }
        showFeed();
        if (session.autoUpdateCheck()) {
            handler.postDelayed(this::checkUpdateOnLaunch, 650);
        }
        handler.postDelayed(this::checkAnnouncementOnLaunch, 950);
        handler.postDelayed(this::autoSignInOnLaunch, 1250);
    }

    private void autoSignInOnLaunch() {
        if (isFinishing() || signInManager == null || !session.isLoggedIn()) return;
        signInManager.autoSignInIfNeeded(result -> {
            if (isFinishing()) return;
            if ("profile".equals(screen)) showProfile();
        });
    }

    private void checkUpdateOnLaunch() {
        UpdateChecker.check(appVersion(), new UpdateChecker.Callback() {
            @Override public void onResult(UpdateChecker.Result result) {
                if (!result.updateAvailable || isFinishing()) return;
                if (result.title != null || result.notes != null) {
                    showUpdateDialog(result);
                    return;
                }
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("发现新版本 " + result.version)
                        .setMessage("heybox Lite 有新版本可用，是否前往下载？")
                        .setNegativeButton("稍后", null)
                        .setPositiveButton("下载", (dialog, which) -> openUrl(
                                result.downloadUrl.isEmpty()
                                        ? result.releaseUrl : result.downloadUrl))
                        .show();
            }

            @Override public void onError(String message) {
                // 启动检查失败时保持安静，避免网络不稳定影响正常使用。
            }
        });
    }

    private void checkAnnouncementOnLaunch() {
        AnnouncementChecker.Item welcome = welcomeAnnouncement();
        if (shouldShowWelcomeAnnouncement(welcome)) {
            showAnnouncementDialog(welcome);
            return;
        }
        AnnouncementChecker.load(new AnnouncementChecker.Callback() {
            @Override public void onResult(List<AnnouncementChecker.Item> items) {
                if (isFinishing()) return;
                AnnouncementChecker.Item item = firstUnseenAnnouncement(items);
                if (item == null) return;
                showAnnouncementDialog(item);
            }

            @Override public void onError(String message) {
                // Server announcements are optional; a network failure must not show
                // the one-time local welcome again after it has been dismissed.
            }
        });
    }

    private boolean shouldShowWelcomeAnnouncement(AnnouncementChecker.Item item) {
        if (item == null || TextUtils.isEmpty(item.id) || session == null) return false;
        if (session.isAnnouncementSeen(item.id)) return false;
        String previous = session.lastAnnouncementId();
        if (!TextUtils.isEmpty(previous) && !item.id.equals(previous)) {
            session.markAnnouncementSeen(item.id);
            return false;
        }
        return true;
    }

    private List<AnnouncementChecker.Item> withWelcomeAnnouncement(
            List<AnnouncementChecker.Item> items) {
        List<AnnouncementChecker.Item> result = new ArrayList<>();
        result.add(welcomeAnnouncement());
        if (items != null) {
            for (AnnouncementChecker.Item item : items) {
                if (item == null || WELCOME_ANNOUNCEMENT_ID.equals(item.id)) continue;
                result.add(item);
            }
        }
        return result;
    }

    private AnnouncementChecker.Item welcomeAnnouncement() {
        return new AnnouncementChecker.Item(
                WELCOME_ANNOUNCEMENT_ID,
                "欢迎使用 heybox Lite",
                "欢迎使用 heybox Lite。这里会放版本公告和重要提醒。\n\n"
                        + "遇到 bug 或有建议，可以加入反馈群：781941517。\n\n"
                        + "本项目仅用于学习、研究与个人使用，请在遵守平台规则的前提下使用。",
                "normal",
                appVersion(),
                true);
    }

    private AnnouncementChecker.Item firstUnseenAnnouncement(
            List<AnnouncementChecker.Item> items) {
        if (items == null) return null;
        for (AnnouncementChecker.Item item : items) {
            if (item != null && item.enabled
                    && (!item.title.isEmpty() || !item.content.isEmpty())
                    && (item.id.isEmpty() || session == null
                    || !session.isAnnouncementSeen(item.id))) return item;
        }
        return null;
    }

    private void showAnnouncementDialog(AnnouncementChecker.Item item) {
        if (item == null || isFinishing()) return;
        String titleValue = TextUtils.isEmpty(item.title) ? "\u516c\u544a" : item.title;
        StringBuilder message = new StringBuilder();
        if (!TextUtils.isEmpty(item.updatedAt)) {
            message.append(item.updatedAt).append("\n\n");
        }
        message.append(item.content);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(titleValue)
                .setMessage(message.toString())
                .setPositiveButton("\u77e5\u9053\u4e86",
                        (dialog, which) -> markAnnouncementSeen(item));
        if (WELCOME_ANNOUNCEMENT_ID.equals(item.id)) {
            builder.setNeutralButton("群二维码",
                    (dialog, which) -> {
                        markAnnouncementSeen(item);
                        showFeedbackGroupQr();
                    });
        }
        builder.show();
    }

    private void markAnnouncementSeen(AnnouncementChecker.Item item) {
        if (session != null && item != null && !TextUtils.isEmpty(item.id)) {
            session.markAnnouncementSeen(item.id);
        }
    }

    private void showUpdateDialog(UpdateChecker.Result result) {
        String releaseTitle = TextUtils.isEmpty(result.title) ? "" : result.title.trim();
        String notes = TextUtils.isEmpty(result.notes)
                ? "暂无更新内容说明。"
                : limitUpdateNotes(result.notes.trim());
        StringBuilder message = new StringBuilder();
        message.append("当前版本：").append(appVersion()).append('\n');
        message.append("最新版本：").append(result.version);
        if (!TextUtils.isEmpty(releaseTitle)) {
            message.append('\n').append("发布标题：").append(releaseTitle);
        }
        message.append("\n\n更新内容：\n").append(notes);

        String target = TextUtils.isEmpty(result.downloadUrl)
                ? result.releaseUrl : result.downloadUrl;
        new AlertDialog.Builder(this)
                .setTitle("发现新版本 " + result.version)
                .setMessage(message.toString())
                .setNegativeButton("稍后", null)
                .setPositiveButton("下载", (dialog, which) -> openUpdateUrl(target))
                .show();
    }

    private String limitUpdateNotes(String notes) {
        int max = 1800;
        if (notes.length() <= max) return notes;
        return notes.substring(0, max) + "\n\n内容较长，完整更新日志请打开更新页面查看。";
    }

    private void buildShell() {
        LinearLayout root = vertical(BG);
        shellRoot = root;
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), 0, dp(6), 0);
        bar.setBackgroundColor(session.darkMode()
                ? Color.rgb(20, 20, 20) : Color.rgb(232, 234, 237));
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(38)));

        leading = icon("");
        setIcon(leading, R.drawable.ic_arrow_back, TEXT, 20);
        leading.setVisibility(View.INVISIBLE);
        bar.addView(leading, new LinearLayout.LayoutParams(dp(36), dp(38)));

        title = text("heybox Lite", 15, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setOnClickListener(view -> {
            if (canHeaderBack()) onBackPressed();
        });
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(38), 1));

        TextView clock = text("", 12, MUTED);
        clock.setGravity(Gravity.CENTER);
        updateClock(clock);
        bar.addView(clock, new LinearLayout.LayoutParams(dp(48), dp(38)));

        action = icon("");
        setIcon(action, R.drawable.ic_refresh, TEXT, 19);
        bar.addView(action, new LinearLayout.LayoutParams(dp(38), dp(38)));

        content = new BackSwipeFrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(0, 0, 0, 0);
        bottom.setBackgroundColor(BG);
        root.addView(bottom, new LinearLayout.LayoutParams(-1, dp(NAV_BAR_HEIGHT_DP)));
        addNav("社区", "feed", R.drawable.ic_home, this::showFeed);
        addNav("搜索", "search", R.drawable.ic_search, this::showSearch);
        addNav("我的", "profile", R.drawable.ic_person, this::showProfile);
        setContentView(root);
    }

    private void updateClock(TextView clock) {
        clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        handler.postDelayed(() -> {
            if (!isFinishing()) updateClock(clock);
        }, 30_000);
    }

    private void applyPalette() {
        if (session.darkMode()) {
            PRIMARY = parseThemeColor(session.primaryColor(), Color.rgb(36, 121, 184));
            SECONDARY = parseThemeColor(session.secondaryColor(), Color.rgb(115, 184, 230));
            BG = blend(Color.rgb(14, 15, 16), SECONDARY, 0.025f);
            PANEL = blend(Color.rgb(29, 31, 33), PRIMARY, 0.075f);
            TEXT = Color.rgb(241, 243, 245);
            MUTED = Color.rgb(157, 163, 169);
        } else {
            PRIMARY = parseThemeColor(session.primaryColor(), Color.rgb(36, 121, 184));
            SECONDARY = parseThemeColor(session.secondaryColor(), Color.rgb(115, 184, 230));
            BG = blend(Color.rgb(246, 247, 249), SECONDARY, 0.025f);
            PANEL = blend(Color.WHITE, PRIMARY, 0.035f);
            TEXT = Color.rgb(30, 32, 35);
            MUTED = Color.rgb(105, 110, 116);
        }
    }

    private int parseThemeColor(String value, int fallback) {
        try {
            return value.isEmpty() ? fallback : Color.parseColor(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void addNav(String label, String key, int drawable, Runnable click) {
        ImageView item = new ImageView(this);
        item.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        item.setPadding(0, dp(4), 0, dp(4));
        item.setAdjustViewBounds(false);
        item.setImageDrawable(navIcon(drawable, MUTED));
        item.setColorFilter(MUTED);
        item.setContentDescription(label);
        item.setTag(key);
        item.setOnClickListener(view -> click.run());
        bottom.addView(item, new LinearLayout.LayoutParams(0, dp(NAV_BAR_HEIGHT_DP), 1));
    }

    private Drawable navIcon(int drawable, int color) {
        Drawable icon = Compat.tintedDrawable(this, drawable, color);
        if (icon != null) icon.setBounds(0, 0, dp(NAV_ICON_SIZE_DP), dp(NAV_ICON_SIZE_DP));
        return icon;
    }

    private boolean canHeaderBack() {
        if ("detail".equals(screen)) return true;
        if ("saved".equals(screen)) return true;
        if ("announcement_board".equals(screen)) return true;
        if ("settings_home".equals(screen)) return true;
        return "display_preview".equals(screen)
                || "display_settings".equals(screen)
                || "startup_settings".equals(screen)
                || "app_settings".equals(screen)
                || "about".equals(screen);
    }

    private int topLevelIndex() {
        if ("feed".equals(screen)) return 0;
        if ("search".equals(screen)) return 1;
        if ("profile".equals(screen)) return 2;
        return -1;
    }

    private void showTopLevel(int index) {
        if (index == 0) showFeed();
        else if (index == 1) showSearch();
        else if (index == 2) showProfile();
    }

    private String screenKeyForTopLevel(int index) {
        if (index == 0) return "feed";
        if (index == 1) return "search";
        if (index == 2) return "profile";
        return "";
    }

    private String backTargetScreenKey() {
        if ("detail".equals(screen)) return detailReturn;
        if ("saved".equals(screen)) return "profile";
        if ("announcement_board".equals(screen)) return "about";
        if ("display_preview".equals(screen)) return "display_settings";
        if ("display_settings".equals(screen)
                || "startup_settings".equals(screen)
                || "app_settings".equals(screen)
                || "about".equals(screen)) return "settings_home";
        if ("settings_home".equals(screen)) return "profile";
        return "feed";
    }

    private void captureShellSnapshot(String key, View view) {
        captureSnapshot(screenSnapshots, 8, key, view);
    }

    private void captureFullScreenSnapshot(String key) {
        captureSnapshot(fullScreenSnapshots, 4, key, shellRoot);
    }

    private void captureSnapshot(Map<String, Bitmap> target, int maxCount,
                                 String key, View view) {
        if (key == null || key.isEmpty() || view == null) return;
        int width = view.getWidth();
        int height = view.getHeight();
        if (width <= 1 || height <= 1) return;
        try {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(BG);
            view.draw(canvas);
            Bitmap old = target.put(key, bitmap);
            if (old != null && old != bitmap && !old.isRecycled()) old.recycle();
            trimSnapshots(target, maxCount);
        } catch (Throwable ignored) {
        }
    }

    private Bitmap screenSnapshot(String key) {
        if (key == null || key.isEmpty()) return null;
        return screenSnapshots.get(key);
    }

    private Bitmap fullScreenSnapshot(String key) {
        if (key == null || key.isEmpty()) return null;
        return fullScreenSnapshots.get(key);
    }

    private void trimScreenSnapshots() {
        trimSnapshots(screenSnapshots, 8);
    }

    private void trimSnapshots(Map<String, Bitmap> target, int maxCount) {
        if (target.size() <= maxCount) return;
        Iterator<String> iterator = target.keySet().iterator();
        if (iterator.hasNext()) {
            String key = iterator.next();
            Bitmap bitmap = target.get(key);
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            iterator.remove();
        }
    }

    private ImageView installFullScreenTransitionOverlay(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return null;
        ViewGroup root = getWindow() == null ? null
                : getWindow().getDecorView().findViewById(android.R.id.content);
        if (root == null) return null;
        ImageView overlay = new ImageView(this);
        overlay.setTag(TRANSITION_OVERLAY_TAG);
        overlay.setBackgroundColor(BG);
        overlay.setScaleType(ImageView.ScaleType.FIT_XY);
        overlay.setImageBitmap(bitmap);
        overlay.setAlpha(1f);
        root.addView(overlay, new ViewGroup.LayoutParams(-1, -1));
        overlay.bringToFront();
        return overlay;
    }

    private void fadeFullScreenTransitionOverlay(ImageView overlay) {
        if (overlay == null) return;
        overlay.post(() -> overlay.postDelayed(() -> overlay.animate()
                .alpha(0f)
                .setStartDelay(40)
                .setDuration(210)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(new android.animation.AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(android.animation.Animator animation) {
                        if (overlay.getParent() instanceof ViewGroup) {
                            ((ViewGroup) overlay.getParent()).removeView(overlay);
                        }
                    }
                }), 32));
    }

    private void showShellTransitionOverlay(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || content == null) return;
        ImageView overlay = new ImageView(this);
        overlay.setTag(TRANSITION_OVERLAY_TAG);
        overlay.setBackgroundColor(BG);
        overlay.setScaleType(ImageView.ScaleType.FIT_XY);
        overlay.setImageBitmap(bitmap);
        overlay.setAlpha(1f);
        content.addView(overlay, match());
        overlay.bringToFront();
        overlay.animate().alpha(0f).setStartDelay(35).setDuration(150)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(new android.animation.AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(android.animation.Animator animation) {
                        if (overlay.getParent() instanceof ViewGroup) {
                            ((ViewGroup) overlay.getParent()).removeView(overlay);
                        }
                    }
                });
    }

    private void activate(String key) {
        stopQrPolling();
        screen = key;
        bottom.setVisibility(View.VISIBLE);
        leading.setVisibility(View.INVISIBLE);
        int activeColor = TEXT;
        int inactiveColor = MUTED;
        for (int i = 0; i < bottom.getChildCount(); i++) {
            View item = bottom.getChildAt(i);
            boolean active = key.equals(item.getTag());
            item.setAlpha(active ? 1f : 0.62f);
            if (item instanceof ImageView) {
                ((ImageView) item).setColorFilter(active ? activeColor : inactiveColor);
            } else if (item instanceof TextView) {
                TextView textItem = (TextView) item;
                textItem.setTextColor(active ? activeColor : inactiveColor);
                textItem.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
            }
        }
    }

    private void animateShellChange(Runnable change, int direction) {
        if (shellAnimating) return;
        View oldChild = content == null || content.getChildCount() == 0
                ? null : content.getChildAt(0);
        int width = content == null ? 0 : Math.max(1, content.getWidth());
        if (oldChild == null || width <= 1) {
            change.run();
            return;
        }
        shellAnimating = true;
        animateShellView(oldChild, 0f, -direction * width, 1f, 0.35f, 145, () -> {
            oldChild.setTranslationX(0f);
            oldChild.setAlpha(1f);
            change.run();
            View newChild = content.getChildCount() == 0 ? null : content.getChildAt(0);
            if (newChild == null) {
                shellAnimating = false;
                return;
            }
            animateShellView(newChild, direction * width, 0f, 0.35f, 1f, 185,
                    () -> shellAnimating = false);
        });
    }

    private void animateShellView(View view, float fromX, float toX, float fromAlpha,
                                  float toAlpha, int duration, Runnable end) {
        if (view == null) {
            if (end != null) end.run();
            return;
        }
        view.setTranslationX(fromX);
        view.setAlpha(fromAlpha);
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(value -> {
            float progress = (Float) value.getAnimatedValue();
            view.setTranslationX(fromX + (toX - fromX) * progress);
            view.setAlpha(fromAlpha + (toAlpha - fromAlpha) * progress);
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                view.setTranslationX(toX);
                view.setAlpha(toAlpha);
                if (end != null) end.run();
            }
        });
        animator.start();
    }

    private void navigateTopLevelSwipe(int direction) {
        int index = topLevelIndex();
        if (index < 0) return;
        int next = index + direction;
        if (next < 0 || next > 2) return;
        animateShellChange(() -> showTopLevel(next), direction);
    }

    private void backWithShellAnimation() {
        if (!canHeaderBack() || "detail".equals(screen)) return;
        animateShellChange(this::onBackPressed, -1);
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
        private int gestureMode = MODE_NONE;
        private int gestureDirection;
        private View dragChild;
        private ImageView previewChild;
        private String displayedScreenKey = "";
        private android.animation.ValueAnimator swipeAnimator;

        BackSwipeFrameLayout(android.content.Context context) {
            super(context);
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        @Override public void addView(View child, int index, ViewGroup.LayoutParams params) {
            super.addView(child, index, params);
            if (child != previewChild) displayedScreenKey = screen;
        }

        @Override public void removeAllViews() {
            captureShellSnapshot(displayedScreenKey, currentShellChild());
            captureFullScreenSnapshot(displayedScreenKey);
            super.removeAllViews();
            previewChild = null;
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            if (!canUseShellSwipe()) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    cancelSwipeAnimator();
                    startX = event.getX();
                    startY = event.getY();
                    startTime = event.getEventTime();
                    tracking = true;
                    dragging = false;
                    gestureMode = MODE_NONE;
                    dragChild = null;
                    return false;
                case MotionEvent.ACTION_MOVE: {
                    if (!tracking) return false;
                    float dx = event.getX() - startX;
                    float dy = event.getY() - startY;
                    if (startShellDragIfReady(dx, dy)) {
                        dragShellTo(dx);
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    }
                    return false;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    tracking = false;
                    dragging = false;
                    return false;
                default:
                    return false;
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (!dragging && !canUseShellSwipe()) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    cancelSwipeAnimator();
                    startX = event.getX();
                    startY = event.getY();
                    startTime = event.getEventTime();
                    tracking = true;
                    dragging = false;
                    gestureMode = MODE_NONE;
                    dragChild = null;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getX() - startX;
                    float dy = event.getY() - startY;
                    if (!dragging && !startShellDragIfReady(dx, dy)) return true;
                    dragShellTo(dx);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (dragging) finishShellSwipe(event);
                    else performClick();
                    tracking = false;
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) settleShellDrag(0f, this::resetShellDrag);
                    tracking = false;
                    dragging = false;
                    return true;
                default:
                    return true;
            }
        }

        private boolean canUseShellSwipe() {
            return !shellAnimating && !"detail".equals(screen) && !"login".equals(screen);
        }

        private boolean hasShellSwipeTarget(float dx) {
            int index = topLevelIndex();
            if (index >= 0) {
                int next = index + (dx < 0 ? 1 : -1);
                return next >= 0 && next <= 2;
            }
            return canHeaderBack() && dx > 0;
        }

        private boolean startShellDragIfReady(float dx, float dy) {
            if (!tracking) return false;
            if (Math.abs(dy) > touchSlop * 2 && Math.abs(dy) > Math.abs(dx)) {
                tracking = false;
                return false;
            }
            if (Math.abs(dx) <= touchSlop * 2
                    || Math.abs(dx) <= Math.abs(dy) * 1.18f
                    || !hasShellSwipeTarget(dx)) {
                return false;
            }
            int index = topLevelIndex();
            gestureMode = index >= 0 ? MODE_TOP_LEVEL : MODE_BACK;
            gestureDirection = index >= 0 ? (dx < 0 ? 1 : -1) : -1;
            dragChild = currentShellChild();
            if (dragChild == null) return false;
            installShellPreview(targetScreenKeyForGesture());
            cancelSwipeAnimator();
            shellAnimating = true;
            dragging = true;
            return true;
        }

        private View currentShellChild() {
            for (int i = getChildCount() - 1; i >= 0; i--) {
                View child = getChildAt(i);
                if (child != previewChild
                        && !TRANSITION_OVERLAY_TAG.equals(child.getTag())) return child;
            }
            return null;
        }

        private String targetScreenKeyForGesture() {
            if (gestureMode == MODE_TOP_LEVEL) {
                return screenKeyForTopLevel(topLevelIndex() + gestureDirection);
            }
            return backTargetScreenKey();
        }

        private void installShellPreview(String targetKey) {
            removeShellPreview();
            previewChild = new ImageView(getContext());
            previewChild.setBackgroundColor(BG);
            previewChild.setScaleType(ImageView.ScaleType.FIT_XY);
            Bitmap bitmap = screenSnapshot(targetKey);
            if (bitmap != null && !bitmap.isRecycled()) previewChild.setImageBitmap(bitmap);
            super.addView(previewChild, 0, new FrameLayout.LayoutParams(-1, -1));
        }

        private void removeShellPreview() {
            if (previewChild != null) {
                super.removeView(previewChild);
                previewChild = null;
            }
        }

        private void dragShellTo(float dx) {
            if (dragChild == null) return;
            int width = Math.max(1, getWidth());
            float exitSign = -gestureDirection;
            float offset;
            if (exitSign < 0) offset = Math.max(-width, Math.min(0f, dx));
            else offset = Math.max(0f, Math.min(width, dx));
            float progress = Math.min(1f, Math.abs(offset) / width);
            dragChild.setTranslationX(offset);
            dragChild.setAlpha(1f - 0.08f * progress);
        }

        private void finishShellSwipe(MotionEvent event) {
            float dx = event.getX() - startX;
            float dy = event.getY() - startY;
            long duration = Math.max(1L, event.getEventTime() - startTime);
            float velocity = dx * 1000f / duration;
            float offset = dragChild == null ? 0f : dragChild.getTranslationX();
            boolean enoughDistance = Math.abs(offset) > Math.max(dp(52), getWidth() * 0.24f);
            boolean enoughVelocity = -gestureDirection * velocity > dp(300);
            if (Math.abs(dx) <= Math.abs(dy) * 1.10f || (!enoughDistance && !enoughVelocity)) {
                settleShellDrag(0f, this::resetShellDrag);
                return;
            }
            settleShellDrag(-gestureDirection * Math.max(1, getWidth()),
                    this::completeShellSwipe);
        }

        private void settleShellDrag(float targetX, Runnable end) {
            if (dragChild == null) {
                resetShellDrag();
                if (end != null) end.run();
                return;
            }
            cancelSwipeAnimator();
            float fromX = dragChild.getTranslationX();
            if (Math.abs(fromX - targetX) < dp(1)) {
                dragChild.setTranslationX(targetX);
                if (end != null) end.run();
                return;
            }
            int distance = Math.round(Math.abs(fromX - targetX));
            int duration = Math.max(120, Math.min(260, distance / 3));
            swipeAnimator = android.animation.ValueAnimator.ofFloat(fromX, targetX);
            swipeAnimator.setDuration(duration);
            swipeAnimator.setInterpolator(new DecelerateInterpolator());
            swipeAnimator.addUpdateListener(value -> {
                float x = (Float) value.getAnimatedValue();
                int width = Math.max(1, getWidth());
                float progress = Math.min(1f, Math.abs(x) / width);
                if (dragChild != null) {
                    dragChild.setTranslationX(x);
                    dragChild.setAlpha(1f - 0.08f * progress);
                }
            });
            swipeAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    swipeAnimator = null;
                    if (end != null) end.run();
                }
            });
            swipeAnimator.start();
        }

        private void completeShellSwipe() {
            int direction = gestureDirection;
            int mode = gestureMode;
            String targetKey = targetScreenKeyForGesture();
            Bitmap overlay = screenSnapshot(targetKey);
            if (dragChild != null) {
                dragChild.setTranslationX(0f);
                dragChild.setAlpha(1f);
            }
            Runnable change;
            if (mode == MODE_TOP_LEVEL) {
                int next = topLevelIndex() + direction;
                change = () -> showTopLevel(next);
            } else {
                change = MainActivity.this::onBackPressed;
            }
            change.run();
            showShellTransitionOverlay(overlay);
            View newChild = currentShellChild();
            if (newChild == null) {
                resetShellDrag();
                return;
            }
            dragChild = newChild;
            newChild.setTranslationX(0f);
            newChild.setAlpha(1f);
            resetShellDrag();
        }

        private void resetShellDrag() {
            cancelSwipeAnimator();
            if (dragChild != null) {
                dragChild.setTranslationX(0f);
                dragChild.setAlpha(1f);
            }
            removeShellPreview();
            dragChild = null;
            gestureMode = MODE_NONE;
            gestureDirection = 0;
            dragging = false;
            shellAnimating = false;
        }

        private void cancelSwipeAnimator() {
            if (swipeAnimator != null) {
                swipeAnimator.removeAllListeners();
                swipeAnimator.cancel();
                swipeAnimator = null;
            }
        }

        @Override public boolean performClick() {
            return super.performClick();
        }
    }

    private void showLogin() {
        stopQrPolling();
        screen = "login";
        bottom.setVisibility(View.GONE);
        leading.setVisibility(View.INVISIBLE);
        action.setVisibility(View.INVISIBLE);
        title.setText("扫码登录");
        content.removeAllViews();

        LinearLayout page = vertical(BG);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(12), dp(12), dp(12), dp(12));
        TextView heading = text("heybox Lite", 20, TEXT);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        page.addView(heading, new LinearLayout.LayoutParams(-1, dp(34)));

        TextView status = text("正在获取二维码…", 12, MUTED);
        status.setGravity(Gravity.CENTER);
        status.setTag("qr_status");
        page.addView(status, new LinearLayout.LayoutParams(-1, dp(28)));

        int qrSize = Math.round(getResources().getDisplayMetrics().widthPixels * 0.54f);
        ImageView qr = new ImageView(this);
        qr.setTag("qr_image");
        qr.setPadding(dp(7), dp(7), dp(7), dp(7));
        qr.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(qrSize, qrSize);
        qrParams.topMargin = dp(4);
        page.addView(qr, qrParams);

        TextView hint = text("请使用小黑盒 App 扫码", 12, SECONDARY);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, dp(34));
        hintParams.topMargin = dp(4);
        page.addView(hint, hintParams);

        Button retry = button("重新获取", R.drawable.ic_refresh);
        retry.setOnClickListener(view -> requestQr());
        page.addView(retry, new LinearLayout.LayoutParams(dp(150), dp(38)));

        Button guest = button("游客浏览", R.drawable.ic_home);
        guest.setOnClickListener(view -> showFeed());
        LinearLayout.LayoutParams guestParams = new LinearLayout.LayoutParams(dp(150), dp(38));
        guestParams.topMargin = dp(7);
        page.addView(guest, guestParams);
        content.addView(page, match());
        requestQr();
    }

    private void requestQr() {
        stopQrPolling();
        ImageView oldImage = content.findViewWithTag("qr_image");
        if (oldImage != null) oldImage.setImageDrawable(null);
        setQrStatus("正在获取二维码…", MUTED);
        Map<String, String> params = new HashMap<>();
        params.put("app", "web");
        params.put(SecureStrings.heyboxId(), "");
        api.get(EndpointProvider.qrUrl(), params, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                JSONObject result = body.optJSONObject("result");
                String url = result == null ? "" : result.optString("qr_url");
                if (url.isEmpty()) {
                    setQrStatus("二维码获取失败", PRIMARY);
                    return;
                }
                qrKey = Uri.parse(url).getQueryParameter("qr");
                if (qrKey == null || qrKey.isEmpty()) qrKey = result.optString("qr");
                try {
                    int size = Math.round(getResources().getDisplayMetrics().widthPixels * 0.50f);
                    ImageView image = content.findViewWithTag("qr_image");
                    if (image != null) image.setImageBitmap(QrCode.create(url, size));
                    setQrStatus("等待扫码", SECONDARY);
                    pollingQr = true;
                    handler.postDelayed(qrPollTask, 900);
                } catch (Exception error) {
                    setQrStatus("二维码生成失败", PRIMARY);
                }
            }

            @Override public void onError(String message) {
                setQrStatus("获取失败：" + qrErrorMessage(message), PRIMARY);
            }
        });
    }

    private String qrErrorMessage(String message) {
        if (message == null || message.isEmpty()) return "请稍后重试";
        if (message.contains("HTTP 403")) return "请求过于频繁，请稍后重试";
        String compact = message.replace('\n', ' ').trim();
        return compact.length() > 28 ? compact.substring(0, 28) + "…" : compact;
    }

    private void pollQr() {
        if (!pollingQr || qrKey == null || qrKey.isEmpty()) return;
        Map<String, String> params = new HashMap<>();
        params.put("qr", qrKey);
        params.put("app", "web");
        api.get(EndpointProvider.qrState(), params, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                if (!pollingQr) return;
                JSONObject result = body.optJSONObject("result");
                String state = result == null ? "" : result.optString("error");
                if ("ok".equals(state)) {
                    session.saveLogin(result);
                    pollingQr = false;
                    feed.clear();
                    toast("登录成功");
                    EmojiStore.load(api, () -> {});
                    showFeed();
                    return;
                }
                if ("ready".equals(state)) setQrStatus("已扫码，请在手机确认", PRIMARY);
                else if ("cancel".equals(state)) {
                    pollingQr = false;
                    setQrStatus("登录已取消，请重新获取", PRIMARY);
                    return;
                } else setQrStatus("等待扫码", SECONDARY);
                handler.postDelayed(qrPollTask, 1300);
            }

            @Override public void onError(String message) {
                if (!pollingQr) return;
                setQrStatus("网络波动，正在重试", MUTED);
                handler.postDelayed(qrPollTask, 2200);
            }
        });
    }

    private void setQrStatus(String value, int color) {
        TextView view = content.findViewWithTag("qr_status");
        if (view != null) {
            view.setText(value);
            view.setTextColor(color);
        }
    }

    private void stopQrPolling() {
        pollingQr = false;
        handler.removeCallbacks(qrPollTask);
    }

    private void showFeed() {
        activate("feed");
        title.setText("社区");
        action.setText("");
        setIcon(action, R.drawable.ic_refresh, TEXT, 19);
        action.setVisibility(View.VISIBLE);
        action.setOnClickListener(view -> loadFeed(true));
        content.removeAllViews();
        if (cachedFeedListView != null && feedListView == cachedFeedListView
                && cachedFeedListView.getParent() == null) {
            content.addView(cachedFeedListView, match());
            restoreFeedScroll();
            updateFeedFooter();
            return;
        }
        if (feed.isEmpty()) {
            List<FeedItem> cached = filterItems(localCache.feedItems());
            if (!cached.isEmpty()) {
                feed.addAll(cached);
                feedOffset = feed.size();
                localCache.log("feed restored from offline cache: " + feed.size());
            }
        }

        ListView list = new ListView(this);
        feedListView = list;
        cachedFeedListView = list;
        list.setBackgroundColor(BG);
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(dp(2));
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setSelector(new ColorDrawable(session.darkMode()
                ? Color.rgb(50, 50, 50) : Color.rgb(225, 228, 232)));
        feedFooter = feedFooterView();
        list.addFooterView(feedFooter, null, false);
        feedAdapter = new FeedAdapter(this, feed, session.noImage(),
                session.uiScale() / 100f, session.textScale() / 100f,
                session.darkMode(), PRIMARY, SECONDARY, this::showDetail, this::toggleFeedLike);
        list.setAdapter(feedAdapter);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int state) {}
            @Override public void onScroll(AbsListView view, int first, int visible, int total) {
                if (total > 0 && first + visible >= total - 2
                        && !feedNoMore && !feedLoadMoreFailed) loadFeed(false);
            }
        });
        updateFeedFooter();
        content.addView(list, match());
        restoreFeedScroll();
        if (feed.isEmpty()) loadFeed(true);
    }

    private TextView feedFooterView() {
        TextView footer = text("", 12, MUTED);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(8), dp(10), dp(8), dp(16));
        footer.setOnClickListener(view -> {
            if (feedLoadMoreFailed) loadFeed(false);
        });
        return footer;
    }

    private void updateFeedFooter() {
        if (feedFooter == null) return;
        feedFooter.setVisibility(feed.isEmpty() && !feedLoadingMore
                && !feedLoadMoreFailed && !feedNoMore ? View.GONE : View.VISIBLE);
        feedFooter.setEnabled(feedLoadMoreFailed);
        if (feedLoadingMore) {
            feedFooter.setText("正在加载更多...");
            feedFooter.setTextColor(MUTED);
        } else if (feedLoadMoreFailed) {
            feedFooter.setText("加载失败，点按重试");
            feedFooter.setTextColor(SECONDARY);
        } else if (feedNoMore) {
            feedFooter.setText("没有更多了");
            feedFooter.setTextColor(MUTED);
        } else {
            feedFooter.setText("上滑加载更多");
            feedFooter.setTextColor(MUTED);
        }
    }

    private boolean loadFeed(boolean reset) {
        if (reset) {
            if (feedRefreshing) return false;
            feedRefreshing = true;
            feedNoMore = false;
            feedLoadMoreFailed = false;
        } else {
            if (feedNoMore) return false;
            if (feedRefreshing || feedLoadingMore) return false;
            feedLoadingMore = true;
            feedLoadMoreFailed = false;
        }
        updateFeedFooter();
        final int requestSerial = ++feedRequestSerial;
        if (reset) feedResetSerial = requestSerial;
        final int previousOffset = feedOffset;
        final List<FeedItem> previous = reset ? new ArrayList<>(feed) : null;
        if (reset) {
            feedOffset = 0;
            setFeedRefreshBusy(true);
            if (feed.isEmpty()) showLoading();
        }
        Map<String, String> params = new HashMap<>();
        params.put("offset", String.valueOf(feedOffset));
        params.put("pull", reset ? "1" : "0");
        api.get(EndpointProvider.feeds(), params, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                if (reset) feedRefreshing = false;
                else feedLoadingMore = false;
                if (!reset && requestSerial < feedResetSerial) return;
                hideLoading();
                JSONObject result = body.optJSONObject("result");
                JSONArray links = result == null ? null : result.optJSONArray("links");
                List<FeedItem> fresh = new ArrayList<>();
                int returned = 0;
                if (links != null) {
                    for (int i = 0; i < links.length(); i++) {
                        JSONObject item = links.optJSONObject(i);
                        if (item != null) {
                            FeedItem parsed = FeedItem.from(item);
                            if (!isBlocked(parsed)) fresh.add(parsed);
                            returned++;
                        }
                    }
                }
                if (reset && fresh.isEmpty() && previous != null && !previous.isEmpty()) {
                    feedOffset = Math.max(previousOffset, feed.size());
                    toast("没有获取到新内容，已保留原列表");
                } else if (!reset && returned == 0) {
                    feedNoMore = true;
                } else {
                    if (reset) feed.clear();
                    appendUnique(feed, fresh);
                    localCache.saveFeed(feed);
                }
                if (!reset && returned > 0 && fresh.isEmpty()) {
                    toast("本页内容已被关键词过滤");
                }
                if (returned > 0) feedOffset += Math.max(returned, 30);
                setFeedRefreshBusy(false);
                updateFeedFooter();
                if (feedAdapter != null) feedAdapter.notifyDataSetChanged();
                if (feed.isEmpty()) showMessage("暂时没有获取到社区内容");
            }

            @Override public void onError(String message) {
                if (reset) feedRefreshing = false;
                else feedLoadingMore = false;
                if (!reset && requestSerial < feedResetSerial) return;
                hideLoading();
                setFeedRefreshBusy(false);
                localCache.log((reset ? "feed refresh failed: " : "feed load more failed: ") + message);
                if (reset && previous != null && feed.isEmpty()) feed.addAll(previous);
                if (reset && !feed.isEmpty()) feedOffset = Math.max(previousOffset, feed.size());
                if (reset && feed.isEmpty()) {
                    List<FeedItem> cached = filterItems(localCache.feedItems());
                    if (!cached.isEmpty()) {
                        feed.addAll(cached);
                        toast("已显示离线缓存");
                    }
                } else if (!reset) {
                    feedLoadMoreFailed = true;
                }
                updateFeedFooter();
                if (feedAdapter != null) feedAdapter.notifyDataSetChanged();
                if (feed.isEmpty()) showMessage("加载失败\n" + message);
                else toast("刷新失败，已保留原内容");
            }
        });
        return true;
    }

    private void showSearch() {
        ensureEmojiCatalog(() -> {});
        activate("search");
        title.setText("搜索");
        action.setVisibility(View.INVISIBLE);
        content.removeAllViews();

        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(BG);
        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        EditText input = new EditText(this);
        input.setHint("搜索帖子");
        input.setHintTextColor(MUTED);
        input.setTextColor(TEXT);
        input.setSingleLine(true);
        input.setTextSize(sp(13));
        Compat.tint(input, PRIMARY);
        searchBar.addView(input, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button submit = button("搜索", R.drawable.ic_search);
        LinearLayout.LayoutParams submitParams = new LinearLayout.LayoutParams(dp(82), dp(38));
        submitParams.leftMargin = dp(5);
        searchBar.addView(submit, submitParams);

        LinearLayout recent = vertical(BG);
        FrameLayout results = new FrameLayout(this);
        results.setTag(searchBar);
        page.addView(results, match());
        TextView hint = text("输入关键词搜索社区帖子", 13, MUTED);
        hint.setGravity(Gravity.CENTER);
        results.addView(hint, match());
        FrameLayout.LayoutParams recentParams = new FrameLayout.LayoutParams(-1, -2);
        recentParams.leftMargin = dp(7);
        recentParams.rightMargin = dp(7);
        recentParams.topMargin = dp(54);
        page.addView(recent, recentParams);
        FrameLayout.LayoutParams searchParams =
                new FrameLayout.LayoutParams(-1, dp(42), Gravity.TOP);
        searchParams.leftMargin = dp(7);
        searchParams.rightMargin = dp(7);
        searchParams.topMargin = dp(6);
        page.addView(searchBar, searchParams);
        prepareSearchBar(searchBar, dp(42));

        Runnable search = () -> {
            String keyword = input.getText().toString().trim();
            if (keyword.isEmpty()) {
                toast("请输入搜索关键词");
                return;
            }
            session.addSearchHistory(keyword);
            recent.setVisibility(View.GONE);
            performSearch(keyword, results, searchBar);
        };
        submit.setOnClickListener(view -> search.run());
        input.setOnEditorActionListener((view, actionId, event) -> {
            search.run();
            return true;
        });
        renderSearchHistory(recent, input, results);
        content.addView(page, match());
    }

    private void renderSearchHistory(LinearLayout parent, EditText input, FrameLayout results) {
        parent.removeAllViews();
        List<String> history = session.searchHistory();
        if (history.isEmpty()) return;
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text("最近搜索", 11, MUTED);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(label, new LinearLayout.LayoutParams(0, dp(30), 1));
        TextView clear = text("清空", 10, SECONDARY);
        clear.setGravity(Gravity.CENTER);
        clear.setOnClickListener(view -> {
            session.clearSearchHistory();
            parent.removeAllViews();
        });
        header.addView(clear, new LinearLayout.LayoutParams(dp(48), dp(30)));
        parent.addView(header);

        LinearLayout rows = vertical(BG);
        int visibleCount = Math.min(4, history.size());
        for (int i = 0; i < visibleCount; i++) {
            String value = history.get(i);
            TextView item = text(value, 12, TEXT);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(9), 0, dp(9), 0);
            Compat.setBackground(item, round(blend(PANEL, SECONDARY,
                    session.darkMode() ? 0.16f : 0.09f), 7));
            item.setOnClickListener(view -> {
                input.setText(value);
                input.setSelection(input.length());
                session.addSearchHistory(value);
                parent.setVisibility(View.GONE);
                performSearch(value, results, (View) results.getTag());
            });
            addTop(rows, item, 4);
            item.getLayoutParams().height = dp(34);
        }
        parent.addView(rows);
    }

    private void performSearch(String keyword, FrameLayout results, View searchBar) {
        results.setTag(searchBar);
        results.removeAllViews();
        setSearchBarVisible(searchBar, true);
        LoadingSpinnerView progress = new LoadingSpinnerView(this);
        progress.setColor(PRIMARY);
        results.addView(progress,
                new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER));
        Map<String, String> params = new HashMap<>();
        params.put("q", keyword);
        params.put("keyword", keyword);
        params.put("search_type", "link");
        params.put("page", "1");
        params.put("limit", "30");
        api.get(EndpointProvider.search(), params, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                if (!"search".equals(screen)) return;
                List<FeedItem> items = new ArrayList<>();
                collectFeedItems(body, items, new HashMap<>(), 0);
                items = filterItems(items);
                showSearchResults(results, items, searchBar,
                        items.isEmpty() ? "没有找到相关帖子" : "");
            }

            @Override public void onError(String message) {
                if (!"search".equals(screen)) return;
                localCache.log("search failed: " + message);
                setSearchBarVisible(searchBar, true);
                results.removeAllViews();
                TextView error = text("搜索失败\n" + message, 13, MUTED);
                error.setGravity(Gravity.CENTER);
                results.addView(error, match());
            }
        });
    }

    private void showSearchResults(FrameLayout parent, List<FeedItem> items, View searchBar,
                                   String emptyText) {
        parent.removeAllViews();
        if (items.isEmpty()) {
            setSearchBarVisible(searchBar, true);
            TextView empty = text(emptyText, 13, MUTED);
            empty.setGravity(Gravity.CENTER);
            parent.addView(empty, match());
            return;
        }
        ListView list = feedList(items);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int scrollState) {
                setSearchBarVisible(searchBar, scrollState == SCROLL_STATE_IDLE);
            }

            @Override public void onScroll(AbsListView view, int firstVisibleItem,
                                           int visibleItemCount, int totalItemCount) {}
        });
        parent.addView(list, match());
    }

    private void setSearchBarVisible(View searchBar, boolean visible) {
        if (searchBar == null) return;
        Boolean state = searchBarStates.get(searchBar);
        if (state != null && state == visible) return;
        searchBarStates.put(searchBar, visible);
        searchBar.animate().cancel();
        if (visible) {
            searchBar.setVisibility(View.VISIBLE);
            searchBar.setEnabled(true);
            setChildrenEnabled(searchBar, true);
            searchBar.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(120)
                    .start();
        } else {
            clearFocusTree(searchBar);
            searchBar.setEnabled(false);
            setChildrenEnabled(searchBar, false);
            searchBar.animate()
                    .alpha(0f)
                    .translationY(-dp(12))
                    .setDuration(110)
                    .start();
            handler.postDelayed(() -> {
                Boolean current = searchBarStates.get(searchBar);
                if (current != null && !current) searchBar.setVisibility(View.GONE);
            }, 120);
        }
    }

    private void prepareSearchBar(View searchBar, int height) {
        searchBarHeights.put(searchBar, height);
        searchBarStates.put(searchBar, true);
        searchBar.setVisibility(View.VISIBLE);
        searchBar.setAlpha(1f);
        searchBar.setTranslationY(0f);
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

    private void collectFeedItems(Object node, List<FeedItem> output,
                                  Map<String, Boolean> ids, int depth) {
        if (node == null || depth > 7) return;
        if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                collectFeedItems(array.opt(i), output, ids, depth + 1);
            }
            return;
        }
        if (!(node instanceof JSONObject)) return;
        JSONObject object = (JSONObject) node;
        String id = object.optString("linkid", object.optString("link_id"));
        String titleValue = object.optString("title");
        if (!id.isEmpty() && (!titleValue.isEmpty() || object.has("text")
                || object.has("description") || object.has("content"))) {
            if (!ids.containsKey(id)) {
                ids.put(id, true);
                output.add(FeedItem.from(object));
            }
            return;
        }
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            collectFeedItems(object.opt(keys.next()), output, ids, depth + 1);
        }
    }

    private void appendUnique(List<FeedItem> target, List<FeedItem> incoming) {
        Map<String, Boolean> ids = new HashMap<>();
        for (FeedItem item : target) ids.put(item.id, true);
        for (FeedItem item : incoming) {
            if (item == null || ids.containsKey(item.id)) continue;
            target.add(item);
            ids.put(item.id, true);
        }
    }

    private List<FeedItem> filterItems(List<FeedItem> items) {
        List<FeedItem> filtered = new ArrayList<>();
        if (items == null) return filtered;
        for (FeedItem item : items) {
            if (!isBlocked(item)) filtered.add(item);
        }
        return filtered;
    }

    private boolean isBlocked(FeedItem item) {
        if (item == null) return false;
        List<String> keywords = session.blockKeywordList();
        if (keywords.isEmpty()) return false;
        String haystack = (item.title + "\n" + item.description + "\n" + item.author)
                .toLowerCase(Locale.US);
        for (String keyword : keywords) {
            if (!keyword.isEmpty() && haystack.contains(keyword)) return true;
        }
        return false;
    }

    private void setFeedRefreshBusy(boolean busy) {
        if (!"feed".equals(screen) || action == null) return;
        action.setEnabled(!busy);
        action.setAlpha(busy ? 0.45f : 1f);
    }

    private void saveFeedScroll() {
        if (feedListView == null) return;
        feedFirstVisible = Math.max(0, feedListView.getFirstVisiblePosition());
        View first = feedListView.getChildAt(0);
        feedFirstTop = first == null ? 0 : first.getTop();
    }

    private void restoreFeedScroll() {
        if (feedListView == null || feed.isEmpty()) return;
        final int position = Math.max(0, Math.min(feedFirstVisible, feed.size() - 1));
        final int top = feedFirstTop;
        feedListView.post(() -> {
            feedListView.setSelectionFromTop(position, top);
            feedListView.post(() -> feedListView.setSelectionFromTop(position, top));
        });
    }

    private void invalidateFeedView() {
        cachedFeedListView = null;
        feedListView = null;
        feedAdapter = null;
        feedFooter = null;
    }

    private void showDetail(FeedItem item) {
        saveCurrentDetailProgress();
        stopQrPolling();
        ensureEmojiCatalog(() -> {});
        if ("feed".equals(screen)) saveFeedScroll();
        View sourceChild = content == null || content.getChildCount() == 0
                ? null : content.getChildAt(0);
        captureShellSnapshot(screen, sourceChild);
        captureFullScreenSnapshot(screen);
        if (!"detail".equals(screen)) detailReturn = screen;
        screen = "detail";
        currentLinkId = item.id;
        currentLinkHsrc = item.hsrc;
        currentAuthCode = "";
        currentDetailItem = item;
        bottom.setVisibility(View.GONE);
        leading.setVisibility(View.VISIBLE);
        leading.setOnClickListener(view -> returnFromDetailSmooth());
        title.setText("\u6b63\u6587");
        action.setVisibility(View.INVISIBLE);
        content.removeAllViews();
        showLoading();
        int requestToken = ++detailRequestToken;
        api.get(EndpointProvider.linkTree(), detailParams(item), new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                if (!isCurrentDetailRequest(item, requestToken)) return;
                hideLoading();
                String blocked = detailBlockedMessage(body);
                if (!blocked.isEmpty()) {
                    requestDetailV2(item, requestToken, blocked);
                    return;
                }
                if (!hasDetailLink(body)) {
                    requestDetailV2(item, requestToken, "\u8be6\u60c5\u6570\u636e\u4e3a\u7a7a");
                    if (isCurrentDetailRequest(item, requestToken)) return;
                    handleDetailFailure(item, "详情数据为空");
                    return;
                }
                localCache.saveDetail(item.id, body);
                renderDetail(body, item);
            }

            @Override public void onError(String message) {
                if (!isCurrentDetailRequest(item, requestToken)) return;
                hideLoading();
                requestDetailV2(item, requestToken, message);
                if (isCurrentDetailRequest(item, requestToken)) return;
                handleDetailFailure(item, message);
            }
        });
    }

    private void requestDetailV2(FeedItem item, int requestToken, String fallbackMessage) {
        if (!isCurrentDetailRequest(item, requestToken)) return;
        showLoading();
        api.get(EndpointProvider.linkTreeV2(), detailParams(item), new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                if (!isCurrentDetailRequest(item, requestToken)) return;
                hideLoading();
                String blocked = detailBlockedMessage(body);
                if (!blocked.isEmpty()) {
                    handleDetailFailure(item, blocked);
                    return;
                }
                if (!hasDetailLink(body)) {
                    handleDetailFailure(item, first(fallbackMessage,
                            "\u8be6\u60c5\u6570\u636e\u4e3a\u7a7a"));
                    return;
                }
                localCache.saveDetail(item.id, body);
                renderDetail(body, item);
            }

            @Override public void onError(String message) {
                if (!isCurrentDetailRequest(item, requestToken)) return;
                hideLoading();
                handleDetailFailure(item, first(message, fallbackMessage));
            }
        });
    }

    private boolean isCurrentDetailRequest(FeedItem item, int requestToken) {
        return "detail".equals(screen)
                && item != null
                && item.id.equals(currentLinkId)
                && requestToken == detailRequestToken;
    }

    private Map<String, String> detailParams(FeedItem item) {
        Map<String, String> params = new HashMap<>();
        params.put("link_id", item.id);
        params.put("page", "1");
        params.put("limit", "20");
        params.put("index", "1");
        params.put("is_first", "1");
        params.put("owner_only", "0");
        if (item.hsrc != null && !item.hsrc.isEmpty()) params.put("h_src", item.hsrc);
        return params;
    }

    private void handleDetailFailure(FeedItem item, String message) {
        localCache.log("detail failed " + item.id + ": " + message);
        JSONObject cached = localCache.detail(item.id);
        if (cached != null && detailBlockedMessage(cached).isEmpty()
                && hasDetailLink(cached)) {
            toast("已显示离线缓存");
            renderDetail(cached, item);
            return;
        }
        if (renderFallbackDetail(item, message)) {
            toast("已显示游客摘要");
            return;
        }
        showMessage("详情加载失败\n" + message);
    }

    private boolean renderFallbackDetail(FeedItem item, String reason) {
        if (item == null) return false;
        try {
            String notice = fallbackDetailNotice(reason);
            JSONObject link = item.toJson();
            if (link.optString("title").isEmpty()) link.put("title", "帖子摘要暂不可用");
            if (link.optString("description").isEmpty() && link.optString("text").isEmpty()) {
                link.put("description", notice + " 当前列表没有返回正文摘要，登录后可查看完整详情。");
            }
            JSONObject result = new JSONObject();
            result.put("link", link);
            result.put("comments", new JSONArray());
            JSONObject body = new JSONObject();
            body.put("result", result);
            body.put("_fallback_notice", notice);
            renderDetail(body, item);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String fallbackDetailNotice(String reason) {
        if (reason == null) reason = "";
        if (reason.contains("验证") || reason.contains("captcha")
                || reason.contains("403") || reason.contains("限制")) {
            return "游客模式：完整详情需要验证，已显示首页摘要。";
        }
        return "详情接口暂不可用，已显示首页摘要。";
    }

    private String detailBlockedMessage(JSONObject body) {
        if (body == null) return "详情数据为空";
        String direct = detailBlockedMessageFrom(body);
        if (!direct.isEmpty()) return direct;
        JSONObject result = body.optJSONObject("result");
        return result == null ? "" : detailBlockedMessageFrom(result);
    }

    private boolean hasDetailLink(JSONObject body) {
        if (body == null) return false;
        JSONObject result = body.optJSONObject("result");
        return result != null && result.optJSONObject("link") != null;
    }

    private String detailBlockedMessageFrom(JSONObject object) {
        String status = object.optString("status");
        String code = object.optString("code");
        String message = first(object.optString("msg"), object.optString("message"));
        if (isVerificationStatus(status) || isVerificationStatus(code)) {
            return first(message, status, code, "需要完成验证后才能继续");
        }
        if (isVerificationText(message)) return message;
        return "";
    }

    private boolean isVerificationStatus(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.US);
        return lower.contains("captcha")
                || lower.contains("verify")
                || lower.contains("verification")
                || lower.contains("name_verify")
                || lower.contains("need_alipay_verify")
                || lower.contains("need_bind_phone")
                || lower.contains("need_phone_code");
    }

    private boolean isVerificationText(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.US);
        return lower.contains("captcha")
                || lower.contains("verify")
                || value.contains("需要完成验证")
                || value.contains("验证后")
                || value.contains("接口限制")
                || value.contains("请求过于频繁");
    }

    private void renderDetail(JSONObject body, FeedItem fallback) {
        JSONObject result = body.optJSONObject("result");
        JSONObject link = result == null ? null : result.optJSONObject("link");
        currentLinkHsrc = first(hsrc(link), fallback == null ? "" : fallback.hsrc, currentLinkHsrc);
        String authCode = link == null ? "" : link.optString("auth_code");
        if (authCode.isEmpty() && result != null) authCode = result.optString("auth_code");
        if (authCode.isEmpty()) authCode = body.optString("auth_code");
        if (!authCode.isEmpty()) currentAuthCode = authCode;
        DetailPager pager = new DetailPager(this);
        pager.setBackgroundColor(BG);
        detailPager = pager;

        ScrollView articleScroll = new ScrollView(this);
        articleScroll.setBackgroundColor(BG);
        LinearLayout page = vertical(BG);
        int pagePadding = Math.max(dp(10), dp(session.pagePadding()));
        page.setPadding(pagePadding, dp(8), pagePadding, dp(18));
        articleScroll.addView(page);

        LinearLayout article = vertical(BG);
        article.setPadding(dp(2), dp(4), dp(2), dp(12));
        JSONObject user = link == null ? null : link.optJSONObject("user");
        String author = user == null ? fallback.author : user.optString("username", fallback.author);
        addAuthorHeader(article, link, user, author);
        String heading = link == null ? fallback.title : link.optString("title", fallback.title);
        TextView headline = text(heading, 18, TEXT);
        headline.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        headline.setLineSpacing(dp(1), 1.04f);
        addTop(article, headline, 14);
        String notice = body.optString("_fallback_notice");
        if (!notice.isEmpty()) {
            TextView fallbackNotice = text(notice, 11, SECONDARY);
            fallbackNotice.setLineSpacing(0, 1.16f);
            GradientDrawable noticeBg = round(
                    blend(PANEL, SECONDARY, session.darkMode() ? 0.22f : 0.12f), 7);
            noticeBg.setStroke(dp(1), blend(SECONDARY, TEXT, session.darkMode() ? 0.20f : 0.12f));
            fallbackNotice.setPadding(dp(8), dp(6), dp(8), dp(6));
            Compat.setBackground(fallbackNotice, noticeBg);
            addTop(article, fallbackNotice, 7);
        }
        JSONArray fallbackImages = link == null ? null : link.optJSONArray("imgs");
        lastDetailDiagnostics = buildDetailDiagnostics(body, fallback, link, fallbackImages);
        localCache.log("detail diagnostics captured link="
                + (fallback == null ? "" : fallback.id)
                + " title=" + compactLogText(heading, 48));
        addRichContent(article, link, fallback.description, fallbackImages);
        addDetailActions(article, fallback, link);
        page.addView(article);
        addDetailCommentSection(page, result == null ? null : result.optJSONArray("comments"));

        ScrollView commentScroll = new ScrollView(this);
        commentScroll.setBackgroundColor(BG);
        LinearLayout commentPage = vertical(BG);
        commentPage.setPadding(pagePadding, dp(8), pagePadding, dp(18));
        commentScroll.addView(commentPage);

        addDetailCommentSection(commentPage, result == null ? null : result.optJSONArray("comments"));
        pager.setPages(articleScroll, commentScroll);
        content.addView(pager, match());
        detailScroll = articleScroll;
        int savedScroll = localCache.scroll(currentLinkId);
        if (savedScroll > 0) {
            articleScroll.postDelayed(() -> articleScroll.scrollTo(0, savedScroll), 80);
        }
    }

    private void addDetailCommentSection(LinearLayout page, JSONArray commentArray) {
        View section = new View(this);
        section.setBackgroundColor(blend(PANEL, SECONDARY, session.darkMode() ? 0.20f : 0.12f));
        addTop(page, section, 10);
        section.getLayoutParams().height = dp(1);

        TextView commentTitle = text("\u8bc4\u8bba", 14, TEXT);
        commentTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        commentTitle.setPadding(dp(2), dp(10), dp(2), dp(5));
        page.addView(commentTitle);
        LinearLayout comments = vertical(BG);
        page.addView(comments);
        int count = addComments(comments, commentArray);
        if (count == 0) {
            TextView empty = text("\u6682\u65e0\u8bc4\u8bba", 13, MUTED);
            empty.setPadding(dp(4), dp(8), dp(4), dp(12));
            comments.addView(empty);
        }
    }

    private final class DetailPager extends FrameLayout {
        private static final int PAGE_ARTICLE = 0;
        private static final int PAGE_COMMENTS = 1;
        private final int touchSlop;
        private float startX;
        private float startY;
        private long startTime;
        private int startScrollX;
        private int currentPage = PAGE_ARTICLE;
        private boolean dragging;
        private boolean returning;
        private ImageView returnPreview;
        private android.animation.ValueAnimator settleAnimator;

        DetailPager(android.content.Context context) {
            super(context);
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        void setPages(View article, View comments) {
            removeAllViews();
            returnPreview = new ImageView(getContext());
            returnPreview.setBackgroundColor(BG);
            returnPreview.setScaleType(ImageView.ScaleType.FIT_XY);
            Bitmap bitmap = screenSnapshot(backTargetScreenKey());
            if (bitmap != null && !bitmap.isRecycled()) returnPreview.setImageBitmap(bitmap);
            addView(returnPreview, new FrameLayout.LayoutParams(-1, -1));
            addView(article, new FrameLayout.LayoutParams(-1, -1));
            addView(comments, new FrameLayout.LayoutParams(-1, -1));
            currentPage = PAGE_ARTICLE;
            post(() -> scrollTo(pageScrollX(currentPage), 0));
        }

        boolean showingComments() {
            return currentPage == PAGE_COMMENTS;
        }

        void showArticle(boolean animate) {
            settleToPage(PAGE_ARTICLE, animate);
        }

        @Override protected void onLayout(boolean changed, int left, int top,
                                          int right, int bottom) {
            int width = right - left;
            int height = bottom - top;
            if (returnPreview != null) {
                returnPreview.layout(-width, 0, 0, height);
            }
            if (getChildCount() > 1) {
                getChildAt(1).layout(0, 0, width, height);
            }
            if (getChildCount() > 2) {
                getChildAt(2).layout(width, 0, width * 2, height);
            }
            if (changed) post(() -> scrollTo(pageScrollX(currentPage), 0));
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    cancelSettle();
                    startX = event.getX();
                    startY = event.getY();
                    startTime = event.getEventTime();
                    startScrollX = getScrollX();
                    dragging = false;
                    returning = false;
                    return false;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getX() - startX;
                    float dy = event.getY() - startY;
                    if (Math.abs(dx) > touchSlop * 2
                            && Math.abs(dx) > Math.abs(dy) * 1.15f) {
                        dragging = true;
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    }
                    return false;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    returning = false;
                    return false;
                default:
                    return false;
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    cancelSettle();
                    startX = event.getX();
                    startY = event.getY();
                    startTime = event.getEventTime();
                    startScrollX = getScrollX();
                    dragging = true;
                    returning = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    dragTo(event.getX() - startX);
                    return true;
                case MotionEvent.ACTION_UP:
                    finishHorizontalDrag(event);
                    performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    settleToPage(currentPage, true);
                    dragging = false;
                    returning = false;
                    return true;
                default:
                    return true;
            }
        }

        private void dragTo(float dx) {
            int width = Math.max(1, getWidth());
            int min = currentPage == PAGE_ARTICLE ? -width : 0;
            int next = Math.max(min, Math.min(width, startScrollX - Math.round(dx)));
            scrollTo(next, 0);
        }

        private void finishHorizontalDrag(MotionEvent event) {
            int width = Math.max(1, getWidth());
            float dx = event.getX() - startX;
            long duration = Math.max(1L, event.getEventTime() - startTime);
            float velocity = dx * 1000f / duration;
            if (currentPage == PAGE_ARTICLE && getScrollX() < 0) {
                boolean enoughDistance = Math.abs(getScrollX()) > Math.max(dp(52), width * 0.24f);
                boolean enoughVelocity = velocity > dp(320);
                dragging = false;
                if (enoughDistance || enoughVelocity) settleToReturn();
                else settleToPage(PAGE_ARTICLE, true);
                return;
            }
            int target = getScrollX() > width / 2 ? PAGE_COMMENTS : PAGE_ARTICLE;
            if (Math.abs(velocity) > dp(360)) {
                target = velocity < 0 ? PAGE_COMMENTS : PAGE_ARTICLE;
            }
            dragging = false;
            settleToPage(target, true);
        }

        private void settleToPage(int page, boolean animate) {
            cancelSettle();
            currentPage = page;
            updateDetailPagerTitle();
            int destination = pageScrollX(page);
            if (!animate) {
                scrollTo(destination, 0);
                return;
            }
            int fromX = getScrollX();
            int distance = Math.abs(destination - fromX);
            if (distance < dp(2)) {
                scrollTo(destination, 0);
                return;
            }
            int duration = Math.max(160, Math.min(300, distance / 3));
            settleAnimator = android.animation.ValueAnimator.ofInt(fromX, destination);
            settleAnimator.setDuration(duration);
            settleAnimator.setInterpolator(new DecelerateInterpolator());
            settleAnimator.addUpdateListener(value ->
                    scrollTo((Integer) value.getAnimatedValue(), 0));
            settleAnimator.start();
        }

        private int pageScrollX(int page) {
            return page == PAGE_COMMENTS ? Math.max(0, getWidth()) : 0;
        }

        private void settleToReturn() {
            cancelSettle();
            returning = true;
            int fromX = getScrollX();
            int destination = -Math.max(1, getWidth());
            int distance = Math.abs(destination - fromX);
            int duration = Math.max(150, Math.min(280, distance / 3));
            settleAnimator = android.animation.ValueAnimator.ofInt(fromX, destination);
            settleAnimator.setDuration(duration);
            settleAnimator.setInterpolator(new DecelerateInterpolator());
            settleAnimator.addUpdateListener(value ->
                    scrollTo((Integer) value.getAnimatedValue(), 0));
            settleAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    settleAnimator = null;
                    if (!returning) return;
                    returning = false;
                    returnFromDetailSmooth();
                }
            });
            settleAnimator.start();
        }

        private void cancelSettle() {
            if (settleAnimator != null) {
                returning = false;
                settleAnimator.cancel();
                settleAnimator = null;
            }
        }

        @Override public boolean performClick() {
            return super.performClick();
        }
    }

    private void updateDetailPagerTitle() {
        if (!"detail".equals(screen) || title == null || detailPager == null) return;
        title.setText(detailPager.showingComments() ? "\u8bc4\u8bba" : "\u6b63\u6587");
    }

    private void addDetailActions(LinearLayout article, FeedItem item, JSONObject link) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView like = actionPill("", R.drawable.ic_thumb_up);
        LikeState state = linkLikeState(link, item);
        boolean liked = state.liked;
        int likes = state.likes;
        item.liked = liked;
        item.likes = likes;
        updateFeedLike(item.id, liked, likes);
        updateLinkLikeView(like, liked, likes);
        like.setOnClickListener(view -> toggleLinkLike(item, like));
        row.addView(like, new LinearLayout.LayoutParams(0, dp(36), 1));

        TextView favorite = actionPill("", R.drawable.ic_bookmark);
        boolean favored = linkFavored(link);
        updateFavoriteView(favorite, favored);
        LinearLayout.LayoutParams favoriteParams = new LinearLayout.LayoutParams(0, dp(36), 1);
        favoriteParams.leftMargin = dp(6);
        row.addView(favorite, favoriteParams);
        favorite.setOnClickListener(view -> toggleFavorite(item, favorite));

        TextView comment = actionPill("评论", R.drawable.ic_comment);
        LinearLayout.LayoutParams commentParams = new LinearLayout.LayoutParams(0, dp(36), 1);
        commentParams.leftMargin = dp(6);
        row.addView(comment, commentParams);
        comment.setOnClickListener(view -> showCommentDialog(null));

        addTop(article, row, 10);
    }

    private TextView actionPill(String label, int icon) {
        TextView view = text(label, 12, TEXT);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(dp(7), 0, dp(7), 0);
        updatePill(view, blend(PANEL, SECONDARY, session.darkMode() ? 0.20f : 0.10f),
                TEXT, icon);
        return view;
    }

    private void updateLinkLikeView(TextView view, boolean liked, int likes) {
        int bg = liked ? activeActionBackground(PRIMARY)
                : blend(PANEL, SECONDARY, session.darkMode() ? 0.20f : 0.10f);
        int fg = liked ? contrast(bg) : TEXT;
        view.setText(String.valueOf(Math.max(0, likes)));
        updatePill(view, bg, fg, R.drawable.ic_thumb_up);
    }

    private void updateFavoriteView(TextView view, boolean favored) {
        view.setTag(Boolean.valueOf(favored));
        int bg = favored ? blend(PANEL, SECONDARY, session.darkMode() ? 0.48f : 0.22f)
                : blend(PANEL, SECONDARY, session.darkMode() ? 0.20f : 0.10f);
        int fg = favored ? SECONDARY : TEXT;
        view.setText(favored ? "已收藏" : "收藏");
        updatePill(view, bg, fg, R.drawable.ic_bookmark);
    }

    private void updatePill(TextView view, int bg, int fg, int icon) {
        view.setTextColor(fg);
        GradientDrawable drawable = round(bg, 10);
        drawable.setStroke(dp(1), blend(bg, fg, 0.20f));
        Compat.setBackground(view, drawable);
        setLeftIcon(view, icon, fg, 15);
    }

    private boolean linkLiked(JSONObject link, FeedItem fallback) {
        LikeState override = linkLikeOverride(link, fallback);
        if (override != null) return override.liked;
        if (link == null) return fallback != null && fallback.liked;
        if (truthy(link, "is_award_link", "is_award", "liked", "is_liked", "has_award")) {
            return true;
        }
        return truthy(link, "award_state", "like_state");
    }

    private int linkLikes(JSONObject link, FeedItem fallback) {
        LikeState override = linkLikeOverride(link, fallback);
        if (override != null) return override.likes;
        int fallbackLikes = fallback == null ? 0 : fallback.likes;
        if (link == null) return fallbackLikes;
        return firstInt(link, fallbackLikes, "link_award_num", "like_num", "award_num",
                "award_count", "like_count", "liked_num", "total_award_num", "up_num", "up");
    }

    private LikeState linkLikeState(JSONObject link, FeedItem fallback) {
        LikeState override = linkLikeOverride(link, fallback);
        if (override != null) return override;
        return new LikeState(linkLiked(link, fallback), linkLikes(link, fallback));
    }

    private LikeState linkLikeOverride(JSONObject link, FeedItem fallback) {
        String id = first(fallback == null ? "" : fallback.id,
                link == null ? "" : link.optString("linkid"),
                link == null ? "" : link.optString("link_id"),
                currentLinkId);
        return id.isEmpty() ? null : linkLikeOverrides.get(id);
    }

    private void rememberLinkLike(String linkId, boolean liked, int likes) {
        if (linkId == null || linkId.isEmpty()) return;
        linkLikeOverrides.put(linkId, new LikeState(liked, likes));
    }

    private boolean linkFavored(JSONObject link) {
        if (link == null) return false;
        return truthy(link, "is_favour", "is_favor", "is_fav", "favored",
                "has_favour", "has_favor");
    }

    private boolean truthy(JSONObject source, String... keys) {
        if (source == null) return false;
        for (String key : keys) {
            if (!source.has(key)) continue;
            Object value = source.opt(key);
            if (value instanceof Boolean) return (Boolean) value;
            if (value instanceof Number) return ((Number) value).intValue() == 1;
            String text = String.valueOf(value).trim();
            if ("1".equals(text) || "true".equalsIgnoreCase(text)
                    || "yes".equalsIgnoreCase(text)) return true;
            if ("0".equals(text) || "2".equals(text) || "false".equalsIgnoreCase(text)
                    || "no".equalsIgnoreCase(text)) return false;
        }
        return false;
    }

    private String hsrc(JSONObject link) {
        if (link == null) return "";
        String value = first(link.optString("h_src"), link.optString("hsrc"));
        if (!value.isEmpty()) return value;
        String shareUrl = link.optString("share_url");
        if (shareUrl.isEmpty()) return "";
        try {
            value = Uri.parse(shareUrl).getQueryParameter("h_src");
            return value == null ? "" : value;
        } catch (Exception ignored) {
            return "";
        }
    }

    private int firstInt(JSONObject source, int fallback, String... keys) {
        if (source == null) return fallback;
        for (String key : keys) {
            if (source.has(key)) return source.optInt(key, fallback);
        }
        return fallback;
    }

    private boolean requireLogin(String actionName) {
        if (session != null && session.isLoggedIn()) return true;
        toast("游客模式可浏览，登录后可" + actionName);
        return false;
    }

    private void toggleLinkLike(FeedItem item, TextView view) {
        if (!requireLogin("点赞")) return;
        if (item == null || item.id.isEmpty()) return;
        boolean beforeLiked = item.liked;
        int beforeLikes = Math.max(0, item.likes);
        boolean nextLiked = !beforeLiked;
        int nextLikes = Math.max(0, beforeLikes + (nextLiked ? 1 : -1));
        item.liked = nextLiked;
        item.likes = nextLikes;
        rememberLinkLike(item.id, nextLiked, nextLikes);
        updateLinkLikeView(view, nextLiked, nextLikes);
        updateFeedLike(item.id, nextLiked, nextLikes);

        postLinkLike(item, nextLiked, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                rememberLinkLike(item.id, nextLiked, nextLikes);
                updateFeedLike(item.id, nextLiked, nextLikes);
                localCache.log("link like ok " + item.id + ": " + beforeLikes + " -> " + nextLikes);
            }

            @Override public void onError(String message) {
                item.liked = beforeLiked;
                item.likes = beforeLikes;
                rememberLinkLike(item.id, beforeLiked, beforeLikes);
                updateLinkLikeView(view, beforeLiked, beforeLikes);
                updateFeedLike(item.id, beforeLiked, beforeLikes);
                toast("点赞失败：" + message);
            }
        });
    }

    private void toggleFeedLike(FeedItem item) {
        if (!requireLogin("点赞")) return;
        if (item == null || item.id.isEmpty()) return;
        boolean beforeLiked = item.liked;
        int beforeLikes = Math.max(0, item.likes);
        boolean nextLiked = !beforeLiked;
        int nextLikes = Math.max(0, beforeLikes + (nextLiked ? 1 : -1));
        item.liked = nextLiked;
        item.likes = nextLikes;
        rememberLinkLike(item.id, nextLiked, nextLikes);
        updateFeedLike(item.id, nextLiked, nextLikes);

        postLinkLike(item, nextLiked, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                rememberLinkLike(item.id, nextLiked, nextLikes);
                updateFeedLike(item.id, nextLiked, nextLikes);
                localCache.log("feed like ok " + item.id + ": " + beforeLikes + " -> " + nextLikes);
            }

            @Override public void onError(String message) {
                item.liked = beforeLiked;
                item.likes = beforeLikes;
                rememberLinkLike(item.id, beforeLiked, beforeLikes);
                updateFeedLike(item.id, beforeLiked, beforeLikes);
                toast("点赞失败：" + message);
            }
        });
    }

    private void toggleFavorite(FeedItem item, TextView view) {
        if (!requireLogin("收藏")) return;
        if (item == null || item.id.isEmpty()) return;
        Object tag = view.getTag();
        boolean favored = tag instanceof Boolean && (Boolean) tag;
        boolean nextFavored = !favored;
        updateFavoriteView(view, nextFavored);
        view.setEnabled(false);
        postFavorite(item, nextFavored, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                view.setEnabled(true);
                toast(nextFavored ? "已收藏" : "已取消收藏");
            }

            @Override public void onError(String message) {
                view.setEnabled(true);
                updateFavoriteView(view, favored);
                toast("收藏操作失败：" + message);
            }
        });
    }

    private void postLinkLike(FeedItem item, boolean nextLiked, ApiClient.Callback callback) {
        WriteRequest award = cb -> api.postForm(EndpointProvider.awardLink(),
                Collections.emptyMap(), awardBody(item.id, nextLiked), cb);
        WriteRequest awardWithHsrc = cb -> api.postForm(EndpointProvider.awardLink(),
                queryHsrc(item), awardBody(item.id, nextLiked), cb);
        runWriteFallback(new WriteRequest[]{award, awardWithHsrc}, callback);
    }

    private void postFavorite(FeedItem item, boolean nextFavored, ApiClient.Callback callback) {
        WriteRequest official = cb -> api.postForm(EndpointProvider.favourLink(),
                Collections.emptyMap(), favouriteBody(item.id, nextFavored ? "1" : "2", false), cb);
        WriteRequest withFolder = cb -> api.postForm(EndpointProvider.favourLink(),
                Collections.emptyMap(), favouriteBody(item.id, nextFavored ? "1" : "2", true), cb);
        WriteRequest compact = cb -> api.postForm(EndpointProvider.favourLink(),
                queryHsrc(item), favouriteBody(item.id, nextFavored ? "1" : "2", false), cb);
        WriteRequest withNewsId = cb -> api.postForm(EndpointProvider.favourLink(),
                queryHsrc(item), favouriteBody(item.id, nextFavored ? "1" : "2", true, true), cb);
        WriteRequest noHsrc = cb -> api.postForm(EndpointProvider.favourLink(),
                Collections.emptyMap(), favouriteBody(item.id, nextFavored ? "1" : "2", true), cb);
        runWriteFallback(new WriteRequest[]{official, withFolder, compact, withNewsId, noHsrc}, callback);
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

    private Map<String, String> favouriteBody(String linkId, String type, boolean includeFolder,
                                              boolean includeNewsId) {
        Map<String, String> body = linkIdBody(linkId);
        body.put("favour_type", type);
        if (!session.userId().isEmpty()) body.put(SecureStrings.userid(), session.userId());
        if (includeNewsId) body.put("newsid", linkId);
        if (includeFolder) body.put("folder_id", "");
        return body;
    }

    private Map<String, String> queryHsrc(FeedItem item) {
        Map<String, String> query = new HashMap<>();
        String value = item == null ? "" : item.hsrc;
        if (value.isEmpty() && item != null && item.id.equals(currentLinkId)) {
            value = currentLinkHsrc;
        }
        if (value.isEmpty() && item == null) value = currentLinkHsrc;
        if (!value.isEmpty()) query.put("h_src", value);
        return query;
    }

    private Map<String, String> commentCreateQuery() {
        Map<String, String> query = queryHsrc(currentDetailItem);
        if (!currentAuthCode.isEmpty()) query.put("auth_code", currentAuthCode);
        return query;
    }

    private void runWriteFallback(WriteRequest[] requests, ApiClient.Callback callback) {
        if (localCache != null) {
            localCache.log("write cookie keys: " + session.authCookieKeysForLog());
        }
        runWriteFallback(requests, 0, "", "", false, callback);
    }

    private void runWriteFallback(WriteRequest[] requests, int index, String lastError,
                                  String importantError, boolean tokenRetried,
                                  ApiClient.Callback callback) {
        if (index >= requests.length) {
            String message = !importantError.isEmpty() ? importantError : lastError;
            callback.onError(message == null || message.isEmpty()
                    ? "\u63a5\u53e3\u672a\u8fd4\u56de\u53ef\u7528\u7ed3\u679c" : message);
            return;
        }
        requests[index].start(new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                callback.onSuccess(body);
            }

            @Override public void onError(String message) {
                if (localCache != null) {
                    localCache.log("write fallback " + index + " failed: " + message);
                }
                if (isTokenWriteError(message)) {
                    if (!tokenRetried && writeTokenProvider != null) {
                        if (localCache != null) localCache.log("write auth failed, refreshing token");
                        writeTokenProvider.refresh(new WriteTokenProvider.Callback() {
                            @Override public void onReady() {
                                runWriteFallback(requests, 0, "", "", true, callback);
                            }

                            @Override public void onError(String tokenMessage) {
                                if (localCache != null) {
                                    localCache.log("write token refresh failed: " + tokenMessage);
                                }
                                callback.onError(message + "\n" + tokenMessage);
                            }
                        });
                        return;
                    }
                    callback.onError(message);
                    return;
                }
                if (isLoginWriteError(message)) {
                    callback.onError(message);
                    return;
                }
                String nextImportant = importantError;
                if (nextImportant == null || nextImportant.isEmpty()) {
                    nextImportant = isParameterWriteError(message) ? message : "";
                }
                runWriteFallback(requests, index + 1, message, nextImportant,
                        tokenRetried, callback);
            }
        });
    }

    private boolean isTokenWriteError(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase(java.util.Locale.US);
        return lower.contains("lack_token")
                || lower.contains("x_xhh_tokenid")
                || lower.contains("tokenid")
                || lower.contains("token")
                || message.contains("\u4ee4\u724c");
    }

    private boolean isLoginWriteError(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase(java.util.Locale.US);
        return lower.contains("login")
                || lower.contains("relogin")
                || message.contains("\u767b\u5f55")
                || message.contains("\u91cd\u65b0\u767b\u5f55");
    }

    private boolean isParameterWriteError(String message) {
        if (message == null) return false;
        return message.contains("验证参数") || message.contains("参数")
                || message.contains("param") || message.contains("sign");
    }

    private void updateFeedLike(String linkId, boolean liked, int likes) {
        if (linkId == null || linkId.isEmpty()) return;
        for (FeedItem candidate : feed) {
            if (linkId.equals(candidate.id)) {
                candidate.liked = liked;
                candidate.likes = likes;
                break;
            }
        }
        localCache.saveFeed(feed);
        if (feedAdapter != null) feedAdapter.notifyDataSetChanged();
    }

    private void addAuthorHeader(LinearLayout article, JSONObject link, JSONObject user,
                                 String fallbackAuthor) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Compat.setBackground(avatar, round(session.darkMode()
                ? Color.rgb(50, 53, 56) : Color.rgb(226, 229, 232), 18));
        Compat.clipToOutline(avatar);
        row.addView(avatar, new LinearLayout.LayoutParams(dp(36), dp(36)));
        String avatarUrl = user == null ? "" : user.optString("avatar",
                user.optString("avartar"));
        if (!session.noImage() && !avatarUrl.isEmpty()) ImageLoader.into(avatar, avatarUrl, 96);

        LinearLayout copy = vertical(Color.TRANSPARENT);
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        String nameValue = user == null ? fallbackAuthor
                : first(user.optString("username"), user.optString("nickname"),
                user.optString("name"), fallbackAuthor);
        TextView name = text(nameValue.isEmpty() ? "小黑盒用户" : nameValue, 13, TEXT);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setMaxWidth(dp(118));
        nameRow.addView(name, new LinearLayout.LayoutParams(-2, -2));
        int level = userLevel(user);
        if (level > 0) {
            TextView badge = text("Lv." + level, 8, Color.WHITE);
            badge.setGravity(Gravity.CENTER);
            badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            Compat.setBackground(badge, round(Color.rgb(210, 72, 218), 3));
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(32), dp(15));
            badgeParams.leftMargin = dp(5);
            nameRow.addView(badge, badgeParams);
        }
        copy.addView(nameRow);

        String signature = user == null ? "" : first(user.optString("signature"),
                user.optString("desc"));
        if (!signature.isEmpty()) {
            TextView desc = text(signature, 10, MUTED);
            desc.setSingleLine(true);
            addTop(copy, desc, 1);
        }
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1);
        copyParams.leftMargin = dp(9);
        row.addView(copy, copyParams);

        int followBg = session.darkMode() ? blend(PANEL, TEXT, 0.12f)
                : blend(PANEL, TEXT, 0.06f);
        TextView follow = text("+ 关注", 11, TEXT);
        follow.setGravity(Gravity.CENTER);
        follow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        Compat.setBackground(follow, round(followBg, 5));
        String targetUserId = authorUserId(link, user);
        updateFollowView(follow, isFollowing(link, user));
        row.addView(follow, new LinearLayout.LayoutParams(dp(62), dp(30)));
        follow.setOnClickListener(view -> toggleFollow(follow, link, user, targetUserId));
        article.addView(row);
    }

    private void updateFollowView(TextView follow, boolean following) {
        follow.setTag(Boolean.valueOf(following));
        int bg = following ? activeFollowBackground()
                : blend(PANEL, TEXT, session.darkMode() ? 0.12f : 0.06f);
        int fg = following ? contrast(bg) : TEXT;
        follow.setText(following ? "已关注" : "+ 关注");
        follow.setTextColor(fg);
        GradientDrawable drawable = round(bg, 7);
        drawable.setStroke(dp(1), following
                ? blend(bg, fg, 0.26f)
                : blend(bg, SECONDARY, session.darkMode() ? 0.32f : 0.18f));
        Compat.setBackground(follow, drawable);
    }

    private int activeFollowBackground() {
        return session.darkMode() ? blend(SECONDARY, Color.WHITE, 0.10f) : SECONDARY;
    }

    private int activeActionBackground(int accent) {
        return session.darkMode() ? blend(accent, Color.WHITE, 0.10f) : accent;
    }

    private void toggleFollow(TextView follow, JSONObject link, JSONObject user,
                              String targetUserId) {
        if (!requireLogin("关注")) return;
        if (targetUserId == null || targetUserId.isEmpty()) {
            toast("没有获取到用户 ID");
            return;
        }
        if (targetUserId.equals(session.userId())) {
            toast("不能关注自己");
            return;
        }
        Object tag = follow.getTag();
        boolean before = tag instanceof Boolean && (Boolean) tag;
        int beforeStatus = followStatus(link, user);
        boolean next = !before;
        updateFollowView(follow, next);
        follow.setClickable(false);
        follow.setAlpha(0.92f);
        postFollow(targetUserId, next, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                applyFollowState(link, user, nextFollowStatus(beforeStatus, next));
                follow.setClickable(true);
                follow.setAlpha(1f);
                updateFollowView(follow, next);
                toast(next ? "已关注" : "已取消关注");
            }

            @Override public void onError(String message) {
                follow.setClickable(true);
                follow.setAlpha(1f);
                updateFollowView(follow, before);
                toast("关注操作失败：" + message);
            }
        });
    }

    private void postFollow(String targetUserId, boolean next, ApiClient.Callback callback) {
        String path = next ? EndpointProvider.followUser() : EndpointProvider.unfollowUser();
        WriteRequest official = cb -> api.postForm(path, Collections.emptyMap(),
                userBody(targetUserId, false, false), cb);
        WriteRequest compact = cb -> api.postForm(path, Collections.emptyMap(),
                userBody(targetUserId, true, false), cb);
        WriteRequest withHsrc = cb -> api.postForm(path, queryHsrc(currentDetailItem),
                userBody(targetUserId, false, false), cb);
        WriteRequest withState = cb -> api.postForm(path, Collections.emptyMap(),
                userBody(targetUserId, false, next), cb);
        runWriteFallback(new WriteRequest[]{official, compact, withHsrc, withState}, callback);
    }

    private Map<String, String> userBody(String value, boolean compact, boolean includeState) {
        Map<String, String> body = new HashMap<>();
        body.put("following_id", value);
        if (!compact && currentLinkId != null && !currentLinkId.isEmpty()) {
            body.put("link_id", currentLinkId);
        }
        if (includeState) body.put("follows", "1");
        return body;
    }

    private boolean isFollowing(JSONObject link, JSONObject user) {
        int status = followStatus(link, user);
        if (status >= 0) return status == 1 || status == 3;
        return truthy(link, "is_follow", "is_following", "followed")
                || truthy(user, "is_follow", "is_following", "followed");
    }

    private int followStatus(JSONObject link, JSONObject user) {
        int linkStatus = followStatusValue(link);
        if (linkStatus >= 0) return linkStatus;
        return followStatusValue(user);
    }

    private int followStatusValue(JSONObject source) {
        if (source == null) return -1;
        String[] keys = {"follow_status", "follow_state", "follow_state_v2",
                "is_follow", "is_following", "followed"};
        for (String key : keys) {
            if (!source.has(key)) continue;
            Object value = source.opt(key);
            if (value instanceof Boolean) return (Boolean) value ? 1 : 0;
            if (value instanceof Number) return ((Number) value).intValue();
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) continue;
            if ("true".equalsIgnoreCase(text) || "followed".equalsIgnoreCase(text)
                    || "following".equalsIgnoreCase(text)) return 1;
            if ("mutual".equalsIgnoreCase(text)) return 3;
            if ("false".equalsIgnoreCase(text) || "none".equalsIgnoreCase(text)
                    || "unfollowed".equalsIgnoreCase(text)) return 0;
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                // Some API variants return labels; keep looking for a numeric field.
            }
        }
        return -1;
    }

    private int nextFollowStatus(int beforeStatus, boolean following) {
        if (following) return beforeStatus == 2 ? 3 : 1;
        return beforeStatus == 3 ? 2 : 0;
    }

    private void applyFollowState(JSONObject link, JSONObject user, int status) {
        boolean following = status == 1 || status == 3;
        putFollowState(link, status, following);
        putFollowState(user, status, following);
    }

    private void putFollowState(JSONObject target, int status, boolean following) {
        if (target == null) return;
        try {
            target.put("follow_status", status);
            target.put("follow_state", status);
            target.put("is_follow", following ? 1 : 0);
            target.put("is_following", following);
            target.put("followed", following);
        } catch (Exception ignored) {
            // This only updates the already loaded detail JSON for the current screen.
        }
    }

    private String authorUserId(JSONObject link, JSONObject user) {
        return first(link == null ? "" : link.optString(SecureStrings.userid()),
                link == null ? "" : link.optString(SecureStrings.userId()),
                link == null ? "" : link.optString(SecureStrings.heyboxId()),
                link == null ? "" : link.optString("heyboxid"),
                link == null ? "" : link.optString("uid"),
                link == null ? "" : link.optString("account_id"),
                link == null ? "" : link.optString("id"),
                userId(user));
    }

    private String userId(JSONObject user) {
        if (user == null) return "";
        return first(user.optString(SecureStrings.userid()),
                user.optString(SecureStrings.userId()),
                user.optString(SecureStrings.heyboxId()),
                user.optString("heyboxid"),
                user.optString("uid"),
                user.optString("account_id"),
                user.optString("id"));
    }

    private int userLevel(JSONObject user) {
        if (user == null) return 0;
        JSONObject levelInfo = user.optJSONObject("level_info");
        if (levelInfo != null) return levelInfo.optInt("level", 0);
        return user.optInt("level", 0);
    }

    private void addRichContent(LinearLayout parent, String source, JSONArray fallbackImages) {
        addRichContent(parent, RichContent.parse(source, fallbackImages));
    }

    private void addRichContent(LinearLayout parent, JSONObject link, String fallback,
                                JSONArray fallbackImages) {
        List<RichContent.Block> blocks = link == null
                ? RichContent.parse(fallback, fallbackImages)
                : RichContent.parse(link, fallbackImages);
        if (!RichContent.hasReadableText(blocks) && fallback != null && !fallback.isEmpty()) {
            List<RichContent.Block> fallbackBlocks = RichContent.parse(fallback, null);
            if (RichContent.hasReadableText(fallbackBlocks)) {
                fallbackBlocks.addAll(blocks);
                blocks = fallbackBlocks;
            } else if (blocks.isEmpty()) {
                blocks = RichContent.parse(fallback, fallbackImages);
            }
        }
        addRichContent(parent, blocks);
    }

    private void addRichContent(LinearLayout parent, List<RichContent.Block> blocks) {
        if (blocks.isEmpty()) {
            addTop(parent, text("正文为空", 13, MUTED), 9);
            return;
        }
        int imageCount = 0;
        boolean bodyStarted = false;
        for (RichContent.Block block : blocks) {
            if (block.image) {
                if (session.noImage() || imageCount >= 12) continue;
                View image = postImageBlock(block.value, 1080, 150);
                LinearLayout.LayoutParams imageParams =
                        new LinearLayout.LayoutParams(-1, dp(150));
                imageParams.topMargin = dp(8);
                parent.addView(image, imageParams);
                imageCount++;
            } else {
                bodyStarted = addBodyParagraphs(parent, block.value, bodyStarted);
            }
        }
    }

    private boolean addBodyParagraphs(LinearLayout parent, String source, boolean bodyStarted) {
        List<String> paragraphs = articleParagraphs(source);
        for (String paragraph : paragraphs) {
            TextView bodyText = text(paragraph,
                    15 * session.bodyTextScale() / 100f, TEXT);
            float lineScale = Math.max(1.10f, session.bodyLineSpacing() / 100f);
            bodyText.setLineSpacing(dp(3), lineScale);
            Compat.setLetterSpacing(bodyText, session.bodyLetterSpacing() / 200f);
            bodyText.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
            bodyText.setTextIsSelectable(true);
            EmojiRenderer.set(bodyText, paragraph, session.darkMode());
            addTop(parent, bodyText, bodyStarted
                    ? Math.max(dp(0), session.bodyParagraphSpacing() + 9) : 14);
            bodyStarted = true;
        }
        return bodyStarted;
    }

    private View postImageBlock(String url, int targetPx, int heightDp) {
        FrameLayout frame = new FrameLayout(this);
        int placeholder = session.darkMode()
                ? Color.rgb(28, 30, 32) : Color.rgb(235, 237, 240);
        Compat.setBackground(frame, round(placeholder, 7));
        Compat.clipToOutline(frame);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        frame.addView(image, match());

        LoadingSpinnerView spinner = new LoadingSpinnerView(this);
        spinner.setColor(blend(TEXT, SECONDARY, 0.35f));
        FrameLayout.LayoutParams spinnerParams =
                new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER);
        frame.addView(spinner, spinnerParams);

        View.OnClickListener open = view -> openImage(image, url);
        frame.setOnClickListener(open);
        image.setOnClickListener(open);
        ImageLoader.intoMeasured(image, url, targetPx, (success, bitmap) -> {
            spinner.setVisibility(View.GONE);
            if (success && bitmap != null) adjustImageBlockHeight(frame, bitmap, heightDp);
            if (!success && image.getDrawable() == null) {
                TextView failed = text("图片加载失败", 11, MUTED);
                failed.setGravity(Gravity.CENTER);
                frame.addView(failed, match());
            }
        });
        return frame;
    }

    private void adjustImageBlockHeight(FrameLayout frame, Bitmap bitmap, int fallbackHeightDp) {
        if (frame == null || bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return;
        }
        int width = frame.getWidth();
        if (width <= 0) {
            frame.post(() -> adjustImageBlockHeight(frame, bitmap, fallbackHeightDp));
            return;
        }
        int min = dp(Math.max(92, fallbackHeightDp - 36));
        int max = dp(fallbackHeightDp >= 140 ? 360 : 220);
        int desired = Math.round(width * (bitmap.getHeight() / (float) bitmap.getWidth()));
        int height = Math.max(min, Math.min(max, desired));
        ViewGroup.LayoutParams params = frame.getLayoutParams();
        if (params != null && Math.abs(params.height - height) > dp(2)) {
            params.height = height;
            frame.setLayoutParams(params);
        }
    }

    private List<String> articleParagraphs(String source) {
        List<String> paragraphs = new ArrayList<>();
        String value = source == null ? "" : source
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
        if (value.isEmpty()) return paragraphs;
        value = consumeLeadingArticleLabel(paragraphs, value);
        if (value.isEmpty()) return paragraphs;
        value = normalizeArticleBreaks(value);
        if (value.contains("\n")) {
            String[] parts = value.split("\\n+");
            for (String part : parts) {
                String clean = part.trim();
                if (!clean.isEmpty()) paragraphs.add(clean);
            }
            return paragraphs;
        }

        StringBuilder current = new StringBuilder();
        int visible = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            current.append(ch);
            if (!Character.isWhitespace(ch)) visible++;
            if ((isSentenceEnd(ch) && visible >= 28)
                    || (isSoftBreak(ch) && visible >= 68)) {
                String paragraph = current.toString().trim();
                if (!paragraph.isEmpty()) paragraphs.add(paragraph);
                current.setLength(0);
                visible = 0;
            }
        }
        String tail = current.toString().trim();
        if (!tail.isEmpty()) paragraphs.add(tail);
        return paragraphs;
    }

    private String normalizeArticleBreaks(String value) {
        String output = value;
        output = output.replaceAll(
                "([\\u3002\\uff01\\uff1f!?])\\s*(?=([\\uff08(][0-9\\u4e00-\\u9fff]{1,3}[\\uff09)]))",
                "$1\n");
        output = output.replaceAll(
                "\\s+(?=([\\uff08(][0-9\\u4e00-\\u9fff]{1,3}[\\uff09)]))",
                "\n");
        output = output.replaceAll(
                "([^\\n])\\s+(?=([\\u4e00-\\u9fff]{1,4}\\u3001))",
                "$1\n");
        output = output.replaceAll(
                "([^\\n])\\s+(?=([0-9]{1,2}[.\\uff0e][^0-9]))",
                "$1\n");
        output = output.replaceAll(
                "([\\u4e00-\\u9fffA-Za-z])(?=([0-9]{1,2}[.\\uff0e][\\u4e00-\\u9fff]))",
                "$1\n");
        output = output.replaceAll(
                "([\\u3002\\uff01\\uff1f!?])(?=([0-9]{1,2}[.\\uff0e][\\u4e00-\\u9fff]))",
                "$1\n");
        return output;
    }

    private String consumeLeadingArticleLabel(List<String> paragraphs, String value) {
        String[] labels = {"提示词：", "提示词:", "提示词", "Prompt:", "Prompt"};
        for (String label : labels) {
            if (value.equals(label)) {
                paragraphs.add(displayLabel(label));
                return "";
            }
            if (value.startsWith(label + " ") || value.startsWith(label + "\n")
                    || (label.endsWith(":") || label.endsWith("：")) && value.startsWith(label)) {
                paragraphs.add(displayLabel(label));
                return value.substring(label.length()).trim();
            }
        }
        return value;
    }

    private String displayLabel(String label) {
        if (label.startsWith("提示词")) return "提示词";
        if (label.startsWith("Prompt")) return "Prompt";
        return label;
    }

    private boolean isSentenceEnd(char ch) {
        return ch == '。' || ch == '！' || ch == '？'
                || ch == '!' || ch == '?';
    }

    private boolean isSoftBreak(char ch) {
        return ch == '；' || ch == ';';
    }

    private int addComments(LinearLayout page, JSONArray groups) {
        if (groups == null) return 0;
        List<JSONObject> threads = new ArrayList<>();
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.optJSONObject(i);
            if (group != null) threads.add(group);
        }
        Collections.sort(threads,
                (a, b) -> Integer.compare(threadLikes(b), threadLikes(a)));
        int count = 0;
        for (JSONObject group : threads) {
            JSONArray comments = group == null ? null : group.optJSONArray("comment");
            JSONObject root = comments == null ? group : comments.optJSONObject(0);
            if (root == null) continue;

            LinearLayout threadCard = card();
            threadCard.setPadding(dp(8), dp(5), dp(8), dp(7));
            addComment(threadCard, root, false);

            List<JSONObject> replies = new ArrayList<>();
            if (comments != null) {
                for (int j = 1; j < comments.length(); j++) {
                    JSONObject reply = comments.optJSONObject(j);
                    if (reply != null) replies.add(reply);
                }
            }
            Collections.sort(replies,
                    (a, b) -> Long.compare(commentTime(a), commentTime(b)));
            int expected = Math.max(root.optInt("child_num"),
                    group == null ? 0 : group.optInt("child_num"));
            if (!replies.isEmpty() || expected > 0) {
                LinearLayout replySection = new LinearLayout(this);
                replySection.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(-1, -2);
                sectionParams.topMargin = dp(3);
                threadCard.addView(replySection, sectionParams);

                View rail = new View(this);
                rail.setBackgroundColor(SECONDARY);
                LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(dp(2), -1);
                railParams.leftMargin = dp(14);
                railParams.rightMargin = dp(8);
                replySection.addView(rail, railParams);

                LinearLayout replyList = new LinearLayout(this);
                replyList.setOrientation(LinearLayout.VERTICAL);
                replySection.addView(replyList, new LinearLayout.LayoutParams(0, -2, 1));
                int initial = Math.min(REPLY_PREVIEW_COUNT, replies.size());
                boolean allLoaded = expected <= replies.size();
                renderReplies(replyList, root, replies, expected, initial, allLoaded);
            }
            addTop(page, threadCard, count == 0 ? 0 : 7);
            animateIn(threadCard);
            count += 1 + replies.size();
        }
        return count;
    }

    private void renderReplies(LinearLayout parent, JSONObject root,
                               List<JSONObject> replies, int expected,
                               int visibleCount, boolean allLoaded) {
        parent.removeAllViews();
        int total = Math.max(expected, replies.size());
        int shown = Math.min(Math.max(0, visibleCount), replies.size());
        TextView heading = text(total + " 条回复", 11, MUTED);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setPadding(0, dp(3), 0, dp(2));
        parent.addView(heading);
        for (int i = 0; i < shown; i++) addComment(parent, replies.get(i), true);

        if (shown < replies.size()) {
            int nextCount = Math.min(REPLY_PAGE_SIZE, replies.size() - shown);
            TextView more = replyControl("再展开 " + nextCount + " 条", R.drawable.ic_expand);
            addTop(parent, more, 4);
            more.getLayoutParams().height = dp(36);
            more.setOnClickListener(view -> {
                renderReplies(parent, root, replies, total,
                        shown + REPLY_PAGE_SIZE, allLoaded);
                animateIn(parent);
            });
        } else if (!allLoaded && total > replies.size()) {
            TextView more = replyControl("再展开 5 条", R.drawable.ic_expand);
            addTop(parent, more, 4);
            more.getLayoutParams().height = dp(36);
            more.setOnClickListener(view ->
                    loadSubComments(parent, root, replies, total, shown));
        }
        if (shown > REPLY_PREVIEW_COUNT) {
            TextView collapse = replyControl("收起回复", R.drawable.ic_arrow_back);
            addTop(parent, collapse, 3);
            collapse.getLayoutParams().height = dp(34);
            collapse.setOnClickListener(view ->
                    renderReplies(parent, root, replies, total,
                            Math.min(REPLY_PREVIEW_COUNT, replies.size()), allLoaded));
        }
    }

    private TextView replyControl(String label, int icon) {
        int background = blend(PANEL, SECONDARY, session.darkMode() ? 0.42f : 0.22f);
        int foreground = readableOn(background);
        TextView control = text(label, 12, foreground);
        control.setGravity(Gravity.CENTER_VERTICAL);
        control.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable drawable = round(background, 7);
        drawable.setStroke(dp(1), SECONDARY);
        Compat.setBackground(control, drawable);
        setLeftIcon(control, icon, foreground, 16);
        return control;
    }

    private void loadSubComments(LinearLayout target, JSONObject root,
                                 List<JSONObject> preview, int expected, int shown) {
        TextView loading = text("正在加载更多回复…", 11, MUTED);
        loading.setPadding(0, dp(8), 0, dp(8));
        target.addView(loading);
        Map<String, String> params = new HashMap<>();
        String id = commentId(root);
        params.put("comment_id", id);
        params.put("commentid", id);
        params.put("root_comment_id", id);
        params.put("link_id", currentLinkId);
        params.put("offset", String.valueOf(preview.size()));
        params.put("page", String.valueOf(preview.size() / 50 + 1));
        params.put("limit", "50");
        api.get(EndpointProvider.subComments(), params, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                List<JSONObject> replies = extractSubComments(body, id);
                if (replies.isEmpty()) {
                    target.removeView(loading);
                    toast("没有获取到更多回复");
                    return;
                }
                List<JSONObject> merged = mergeReplies(preview, replies);
                Collections.sort(merged,
                        (a, b) -> Long.compare(commentTime(a), commentTime(b)));
                int total = Math.max(expected, merged.size());
                renderReplies(target, root, merged, total,
                        shown + REPLY_PAGE_SIZE, merged.size() >= total);
                animateIn(target);
            }

            @Override public void onError(String message) {
                target.removeView(loading);
                toast("回复加载失败：" + message);
            }
        });
    }

    private List<JSONObject> mergeReplies(List<JSONObject> first, List<JSONObject> second) {
        List<JSONObject> merged = new ArrayList<>(first);
        for (JSONObject candidate : second) {
            String id = commentId(candidate);
            boolean duplicate = false;
            for (JSONObject existing : merged) {
                if (!id.isEmpty() && id.equals(commentId(existing))) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) merged.add(candidate);
        }
        return merged;
    }

    private List<JSONObject> extractSubComments(JSONObject body, String rootId) {
        List<JSONObject> values = new ArrayList<>();
        JSONObject result = body.optJSONObject("result");
        if (result == null) return values;
        JSONArray array = result.optJSONArray("comments");
        if (array == null) array = result.optJSONArray("comment");
        if (array == null) array = result.optJSONArray("list");
        if (array == null) array = result.optJSONArray("sub_comments");
        if (array == null) return values;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            JSONArray nested = item.optJSONArray("comment");
            if (nested != null) {
                for (int j = 0; j < nested.length(); j++) {
                    JSONObject reply = nested.optJSONObject(j);
                    if (reply != null && !rootId.equals(commentId(reply))) values.add(reply);
                }
            } else if (!rootId.equals(commentId(item))) {
                values.add(item);
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
        return comment.optInt("comment_award_num",
                comment.optInt("award_num", comment.optInt("up")));
    }

    private boolean commentLiked(JSONObject comment) {
        if (comment == null) return false;
        return truthy(comment, "is_support", "supported", "is_award", "liked",
                "has_support");
    }

    private void updateCommentLikeView(TextView view, boolean liked, int likes) {
        int bg = liked ? activeActionBackground(PRIMARY) : Color.TRANSPARENT;
        int color = liked ? contrast(bg) : MUTED;
        view.setText(String.valueOf(Math.max(0, likes)));
        view.setTextColor(color);
        view.setPadding(dp(4), 0, dp(4), 0);
        GradientDrawable drawable = round(bg, 8);
        drawable.setStroke(dp(1), liked ? blend(bg, color, 0.24f) : Color.TRANSPARENT);
        Compat.setBackground(view, drawable);
        setLeftIcon(view, R.drawable.ic_thumb_up, color, liked ? 14 : 13);
    }

    private void toggleCommentLike(JSONObject comment, TextView view) {
        if (!requireLogin("评论点赞")) return;
        String id = commentId(comment);
        if (id.isEmpty()) {
            toast("没有获取到评论 ID");
            return;
        }
        boolean beforeLiked = commentLiked(comment);
        int beforeLikes = commentLikes(comment);
        boolean nextLiked = !beforeLiked;
        int nextLikes = Math.max(0, beforeLikes + (nextLiked ? 1 : -1));
        setCommentLikeState(comment, nextLiked, nextLikes);
        updateCommentLikeView(view, nextLiked, nextLikes);
        view.setEnabled(false);
        postCommentLike(id, nextLiked, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                view.setEnabled(true);
            }

            @Override public void onError(String message) {
                view.setEnabled(true);
                setCommentLikeState(comment, beforeLiked, beforeLikes);
                updateCommentLikeView(view, beforeLiked, beforeLikes);
                toast("评论点赞失败：" + message);
            }
        });
    }

    private void setCommentLikeState(JSONObject comment, boolean liked, int likes) {
        try {
            comment.put("is_support", liked ? 1 : 2);
            comment.put("comment_award_num", Math.max(0, likes));
        } catch (Exception ignored) {
        }
    }

    private Map<String, String> commentSupportBody(String id, boolean liked) {
        Map<String, String> body = new HashMap<>();
        body.put("comment_id", id);
        body.put("support_type", liked ? "1" : "2");
        return body;
    }

    private void postCommentLike(String id, boolean liked, ApiClient.Callback callback) {
        WriteRequest official = cb -> api.postForm(EndpointProvider.supportComment(),
                Collections.emptyMap(), commentSupportBody(id, liked), cb);
        WriteRequest withHsrc = cb -> api.postForm(EndpointProvider.supportComment(),
                queryHsrc(currentDetailItem), commentSupportBody(id, liked), cb);
        WriteRequest hsrcInBody = cb -> api.postForm(EndpointProvider.supportComment(),
                Collections.emptyMap(), commentSupportBody(id, liked, currentLinkHsrc), cb);
        runWriteFallback(new WriteRequest[]{official, withHsrc, hsrcInBody}, callback);
    }

    private Map<String, String> commentSupportBody(String id, boolean liked, String hsrc) {
        Map<String, String> body = commentSupportBody(id, liked);
        if (hsrc != null && !hsrc.isEmpty()) body.put("h_src", hsrc);
        return body;
    }

    private void showCommentDialog(JSONObject replyTo) {
        if (!requireLogin(replyTo == null ? "评论" : "回复评论")) return;
        if (currentLinkId == null || currentLinkId.isEmpty()) {
            toast("没有打开的帖子");
            return;
        }
        EditText input = new EditText(this);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setTextSize(sp(14));
        input.setMinLines(3);
        input.setMaxLines(5);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setHint(replyTo == null ? "写下你的评论" : "回复评论");
        Compat.tint(input, PRIMARY);
        int pad = dp(12);
        input.setPadding(pad, dp(8), pad, dp(8));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(replyTo == null ? "发表评论" : "回复评论")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("发送", null)
                .create();
        dialog.setOnShowListener(value -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) {
                        toast("评论不能为空");
                        return;
                    }
                    sendComment(text, replyTo, dialog);
                }));
        dialog.show();
    }

    private void sendComment(String value, JSONObject replyTo, AlertDialog dialog) {
        String replyId = replyTo == null ? "-1" : commentId(replyTo);
        String rootId = replyTo == null ? "-1" : commentRootId(replyTo);
        if (dialog != null) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        postCreateComment(value, rootId, replyId, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                if (dialog != null && dialog.isShowing()) dialog.dismiss();
                toast("评论已发送");
                if (currentDetailItem != null) showDetail(currentDetailItem);
            }

            @Override public void onError(String message) {
                if (dialog != null && dialog.isShowing()) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                }
                toast("评论发送失败：" + message);
            }
        });
    }

    private void postCreateComment(String value, String rootId, String replyId,
                                   ApiClient.Callback callback) {
        WriteRequest official = cb -> api.postForm(EndpointProvider.createComment(),
                Collections.emptyMap(), commentCreateBody(value, rootId, replyId, false), cb);
        WriteRequest withAuth = cb -> api.postForm(EndpointProvider.createComment(),
                commentCreateQuery(), commentCreateBody(value, rootId, replyId, false), cb);
        WriteRequest withHsrc = cb -> api.postForm(EndpointProvider.createComment(),
                queryHsrc(currentDetailItem), commentCreateBody(value, rootId, replyId, false), cb);
        WriteRequest compat = cb -> api.postForm(EndpointProvider.createComment(),
                commentCreateQuery(), commentCreateBody(value, rootId, replyId, true), cb);
        WriteRequest compatNoHsrc = cb -> api.postForm(EndpointProvider.createComment(),
                Collections.emptyMap(), commentCreateBody(value, rootId, replyId, true), cb);
        runWriteFallback(new WriteRequest[]{official, withAuth, withHsrc, compat, compatNoHsrc}, callback);
    }

    private Map<String, String> commentCreateBody(String value, String rootId, String replyId,
                                                  boolean compat) {
        Map<String, String> body = new HashMap<>();
        body.put("link_id", currentLinkId);
        body.put("text", value);
        body.put("root_id", rootId == null ? "" : rootId);
        body.put("reply_id", replyId == null ? "" : replyId);
        body.put("is_cy", "0");
        if (compat) {
            body.put("recommend_state", "0");
            body.put("linkid", currentLinkId);
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
        return first(comment.optString("commentid"),
                first(comment.optString("comment_id"), comment.optString("id")));
    }

    private String commentRootId(JSONObject comment) {
        String root = first(comment.optString("root_id"),
                comment.optString("root_comment_id"),
                comment.optString("rootid"));
        return root.isEmpty() ? commentId(comment) : root;
    }

    private void addComment(LinearLayout page, JSONObject comment, boolean reply) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(reply ? 5 : 7), 0, dp(reply ? 6 : 8));
        JSONObject user = comment.optJSONObject("user");
        String author = user == null ? "匿名用户" : user.optString("username", "匿名用户");

        ImageView avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Compat.setBackground(avatar, round(session.darkMode()
                ? Color.rgb(50, 53, 56) : Color.rgb(226, 229, 232), 20));
        Compat.clipToOutline(avatar);
        int avatarSize = reply ? 24 : 30;
        row.addView(avatar, new LinearLayout.LayoutParams(dp(avatarSize), dp(avatarSize)));
        String avatarUrl = user == null ? "" : user.optString("avatar");
        if (!session.noImage() && !avatarUrl.isEmpty()) ImageLoader.into(avatar, avatarUrl, 96);

        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(0, -2, 1);
        blockParams.leftMargin = dp(reply ? 7 : 8);
        row.addView(block, blockParams);
        String target = replyTarget(comment);
        String meta = author + (target.isEmpty() ? "" : "  回复 @" + target);
        long created = commentTime(comment);
        if (created > 0) meta += "  ·  " + formatTime(created);
        TextView metaView = text(meta, reply ? 10 : 11, reply ? MUTED : TEXT);
        if (!reply) metaView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        block.addView(metaView);
        String rawComment = first(comment.optString("text"), comment.optString("content"),
                comment.optString("html"), comment.optString("description"));
        String visibleComment = RichContent.plainText(rawComment);
        TextView value = text(visibleComment, 13, TEXT);
        value.setLineSpacing(0, reply ? 1.16f : session.bodyLineSpacing() / 100f);
        Compat.setLetterSpacing(value, reply ? 0 : session.bodyLetterSpacing() / 200f);
        if (!reply) {
            value.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        EmojiRenderer.set(value, visibleComment, session.darkMode());
        addTop(block, value, 4);
        String commentImage = commentImage(comment);
        if (!session.noImage() && !commentImage.isEmpty()) {
            View image = postImageBlock(commentImage, 720, reply ? 92 : 115);
            LinearLayout.LayoutParams imageParams =
                    new LinearLayout.LayoutParams(-1, dp(reply ? 92 : 115));
            imageParams.topMargin = dp(6);
            block.addView(image, imageParams);
        }

        if (!reply) {
            TextView likes = text(String.valueOf(commentLikes(comment)), 10, MUTED);
            updateCommentLikeView(likes, commentLiked(comment), commentLikes(comment));
            likes.setGravity(Gravity.CENTER_VERTICAL);
            likes.setOnClickListener(view -> toggleCommentLike(comment, likes));
            LinearLayout.LayoutParams likeParams = new LinearLayout.LayoutParams(dp(44), dp(24));
            likeParams.leftMargin = dp(3);
            row.addView(likes, likeParams);
        }
        page.addView(row);

        if (reply) {
            View divider = new View(this);
            divider.setBackgroundColor(blend(PANEL, SECONDARY,
                    session.darkMode() ? 0.28f : 0.16f));
            page.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        }
    }

    private String commentImage(JSONObject comment) {
        JSONArray images = comment.optJSONArray("imgs");
        if (images == null || images.length() == 0) return "";
        JSONObject object = images.optJSONObject(0);
        if (object != null) {
            return first(object.optString("url"),
                    first(object.optString("src"), object.optString("original")));
        }
        return images.optString(0);
    }

    private String replyTarget(JSONObject comment) {
        JSONObject target = comment.optJSONObject("to_user");
        if (target == null) target = comment.optJSONObject("reply_user");
        if (target == null) target = comment.optJSONObject("target_user");
        return target == null ? "" : target.optString("username", target.optString("nickname"));
    }

    private void showProfile() {
        if (!session.isLoggedIn()) {
            showGuestProfile();
            return;
        }
        activate("profile");
        title.setText("我的");
        action.setText("");
        setIcon(action, R.drawable.ic_refresh, TEXT, 19);
        action.setVisibility(View.VISIBLE);
        action.setOnClickListener(view -> showProfile());
        content.removeAllViews();
        showLoading();
        api.get(EndpointProvider.profile(),
                Collections.singletonMap(SecureStrings.userid(), session.userId()),
                new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                hideLoading();
                renderProfile(body);
            }

            @Override public void onError(String message) {
                hideLoading();
                toast("个人资料加载失败：" + message);
                renderProfile(new JSONObject());
            }
        });
    }

    private void showGuestProfile() {
        activate("profile");
        title.setText("我的");
        action.setVisibility(View.INVISIBLE);
        content.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = vertical(BG);
        page.setPadding(dp(8), dp(8), dp(8), dp(12));
        scroll.addView(page);

        LinearLayout profile = card();
        TextView name = text("游客模式", 20, TEXT);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        profile.addView(name);
        TextView hint = text("可以浏览社区、搜索帖子和查看详情。点赞、收藏、评论、关注等互动需要二维码登录。",
                12, MUTED);
        hint.setLineSpacing(0, 1.18f);
        addTop(profile, hint, 8);
        Button login = button("二维码登录", R.drawable.ic_logout);
        login.setOnClickListener(view -> showLogin());
        addTop(profile, login, 10);
        page.addView(profile);
        animateIn(profile);

        addSignInCard(page);
        addSettingsMenu(page);
        content.addView(scroll, match());
    }

    private void renderProfile(JSONObject body) {
        JSONObject result = body.optJSONObject("result");
        JSONObject account = result == null ? null : result.optJSONObject("account_detail");
        if (account == null && result != null) account = result.optJSONObject("user");
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = vertical(BG);
        page.setPadding(dp(8), dp(8), dp(8), dp(12));
        scroll.addView(page);
        LinearLayout profile = card();

        ImageView avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        profile.addView(avatar, new LinearLayout.LayoutParams(dp(66), dp(66)));
        String avatarUrl = account == null ? session.avatar() : account.optString("avatar", session.avatar());
        if (!session.noImage()) ImageLoader.into(avatar, avatarUrl, 160);

        String nameValue = account == null ? session.userName() : account.optString("username", session.userName());
        TextView name = text(nameValue, 20, TEXT);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        addTop(profile, name, 10);
        profile.addView(text("ID " + session.userId(), 11, MUTED));
        String signature = account == null ? "" : account.optString("signature");
        if (!signature.isEmpty()) addTop(profile, text(signature, 13, TEXT), 9);
        JSONObject bbs = account == null ? null : account.optJSONObject("bbs_info");
        if (bbs != null) {
            addTop(profile, text("关注 " + bbs.optInt("follow_num")
                    + "   粉丝 " + bbs.optInt("fan_num")
                    + "   获赞 " + bbs.optInt("up_num"), 12, SECONDARY), 10);
        }
        page.addView(profile);
        animateIn(profile);

        addSignInCard(page);
        LinearLayout shortcuts = new LinearLayout(this);
        Button favorites = button("收藏", R.drawable.ic_bookmark);
        favorites.setOnClickListener(view -> showFavorites());
        shortcuts.addView(favorites, new LinearLayout.LayoutParams(0, dp(40), 1));
        Button history = button("历史", R.drawable.ic_history);
        history.setOnClickListener(view -> {
            Map<String, String> params = new HashMap<>();
            params.put("type", "all");
            params.put("dw", "636");
            params.put("no_more", "false");
            showSavedList("浏览历史", EndpointProvider.history(), params);
        });
        LinearLayout.LayoutParams historyParams = new LinearLayout.LayoutParams(0, dp(40), 1);
        historyParams.leftMargin = dp(6);
        shortcuts.addView(history, historyParams);
        addTop(page, shortcuts, 8);

        addSettingsMenu(page);
        content.addView(scroll, match());
    }

    private void addSignInCard(LinearLayout page) {
        LinearLayout panel = card();
        TextView heading = text("每日签到", 15, TEXT);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(heading);

        SignInManager.Result state = signInManager == null
                ? SignInManager.Result.pending() : signInManager.currentState();
        int statusColor = state.success ? SECONDARY : (state.loggedIn ? TEXT : MUTED);
        TextView status = text(state.message, 13, statusColor);
        status.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        addTop(panel, status, 6);

        TextView summary = text(state.summary, 11, MUTED);
        summary.setLineSpacing(0, 1.18f);
        addTop(panel, summary, 4);

        Button actionButton = button(signInButtonText(state),
                state.loggedIn ? R.drawable.ic_thumb_up : R.drawable.ic_logout);
        if (state.success) {
            int fg = contrast(SECONDARY);
            actionButton.setTextColor(fg);
            setLeftIcon(actionButton, R.drawable.ic_thumb_up, fg, 17);
            Compat.setBackground(actionButton, round(SECONDARY, 8));
        }
        actionButton.setEnabled(!state.inFlight && !state.success);
        actionButton.setOnClickListener(view -> {
            if (!session.isLoggedIn()) {
                showLogin();
                return;
            }
            if (signInManager == null) return;
            actionButton.setEnabled(false);
            actionButton.setText("正在签到");
            signInManager.signIn(result -> {
                if (isFinishing()) return;
                toast(result.message);
                if ("profile".equals(screen)) showProfile();
            });
        });
        addTop(panel, actionButton, 9);
        addTop(page, panel, 8);
        animateIn(panel);
    }

    private String signInButtonText(SignInManager.Result state) {
        if (state == null || !state.loggedIn) return "去登录";
        if (state.inFlight) return "签到中";
        if (state.success) return "已签到";
        return "签到";
    }

    private void addSettingsMenu(LinearLayout page) {
        TextView heading = text("设置", 15, TEXT);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        addTop(page, heading, 12);

        Button settings = button("设置中心", R.drawable.ic_settings);
        settings.setOnClickListener(view -> showSettingsHome());
        addTop(page, settings, 7);
    }

    private void showSettingsHome() {
        LinearLayout page = settingsPage("settings_home", "设置中心", this::showProfile);
        LinearLayout panel = card();
        addSettingEntry(panel, "显示与主题", "主题、字号、间距与界面预览",
                R.drawable.ic_settings, this::showDisplaySettings);
        addSettingEntry(panel, "启动与更新", "开屏动画、开屏文字与自动更新",
                R.drawable.ic_refresh, this::showStartupSettings);
        addSettingEntry(panel, "内容与网络", "图片加载、缓存与登录状态",
                R.drawable.ic_home, this::showAppSettings);
        addSettingEntry(panel, "关于 heybox Lite", "版本、项目说明与免责声明",
                R.drawable.ic_info, this::showAbout);
        page.addView(panel);
    }

    private void addSettingEntry(LinearLayout parent, String name, String description,
                                 int icon, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(7), dp(5), dp(7), dp(5));
        TextView marker = icon("");
        setIcon(marker, icon, SECONDARY, 18);
        row.addView(marker, new LinearLayout.LayoutParams(dp(34), dp(42)));
        LinearLayout copy = vertical(Color.TRANSPARENT);
        TextView titleView = text(name, 13, TEXT);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        copy.addView(titleView);
        copy.addView(text(description, 10, MUTED));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = text("›", 20, MUTED);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(42)));
        row.setOnClickListener(view -> runWithPressFeedback(view, action));
        addTop(parent, row, parent.getChildCount() == 0 ? 0 : 4);
        animateIn(row);
    }

    private void showFavorites() {
        showLoading();
        api.get(EndpointProvider.favoriteTabs(), Collections.emptyMap(), new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                localCache.log("favorite tabs loaded: " + favoriteTabSummary(body));
                showFavoriteContents("tab ok");
            }

            @Override public void onError(String message) {
                localCache.log("favorite tabs failed, trying content: " + message);
                showFavoriteContents(message);
            }
        });
    }

    private void showFavoriteContents(String reason) {
        Map<String, String> params = favoriteParams("");
        params.put("limit", "30");
        showSavedList(TITLE_FAVORITES, EndpointProvider.favoriteLinks(), params, false,
                fallbackReason -> {
                    showFavoriteFoldersFallback(reason + "; " + fallbackReason);
                    return true;
                });
    }

    private void showFavoriteFoldersFallback(String reason) {
        localCache.log("favorite content fallback to folders: " + reason);
        showLoading();
        Map<String, String> folderParams = new HashMap<>();
        folderParams.put("enable_new_style_collect", "1");
        folderParams.put("x_os_type", "Windows");
        folderParams.put("device_info", "Edge");
        api.get(EndpointProvider.favoriteFolders(), folderParams, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                JSONObject folder = firstFavoriteFolder(findFavoriteFolders(body));
                String folderId = favoriteFolderId(folder);
                if (folder == null) {
                    localCache.log("favorite folders missing: " + favoriteFolderSummary(body));
                    showFavoritesUnavailable(body, "");
                    return;
                } else if (folderId.isEmpty()) {
                    localCache.log("favorite folder id missing: " + folder);
                    showFavoritesUnavailable(body, "");
                    return;
                }
                showSavedList(TITLE_FAVORITES, EndpointProvider.favoriteLinks(),
                        favoriteParams(folderId), false, null);
            }

            @Override public void onError(String message) {
                localCache.log("favorite folders failed: " + message);
                showFavoritesUnavailable(null, message);
            }
        });
    }

    private String favoriteTabSummary(JSONObject body) {
        if (body == null) return "no body";
        JSONObject result = body.optJSONObject("result");
        JSONArray tabs = result == null ? null : result.optJSONArray("tab_list");
        return "status=" + body.optString("status")
                + ", tabs=" + (tabs == null ? -1 : tabs.length())
                + ", msg=" + first(body.optString("msg"), body.optString("message"));
    }

    private void showFavoritesUnavailable(JSONObject body, String message) {
        hideLoading();
        prepareSavedPage(TITLE_FAVORITES);
        String cacheKey = savedCacheKey(TITLE_FAVORITES, EndpointProvider.favoriteLinks());
        List<FeedItem> cached = filterItems(localCache.savedList(cacheKey));
        if (!cached.isEmpty()) {
            toast(MSG_OFFLINE_CACHE);
            renderSavedItems(TITLE_FAVORITES, cached);
            return;
        }
        if (message != null && !message.isEmpty()) {
            localCache.log("favorite unavailable reason: " + message);
        }
        if (body != null) {
            localCache.log("favorite unavailable body: " + favoriteFolderSummary(body));
        }
        showMessage(MSG_FAVORITES_UNAVAILABLE);
    }

    private String favoriteFolderSummary(JSONObject body) {
        if (body == null) return "no body";
        JSONObject result = body.optJSONObject("result");
        JSONObject source = result == null ? body : result;
        JSONArray folders = source.optJSONArray("folders");
        int folderCount = folders == null ? -1 : folders.length();
        return "status=" + body.optString("status")
                + ", msg=" + first(body.optString("msg"), body.optString("message"))
                + ", folders=" + folderCount
                + ", favour_post_num=" + source.optInt("favour_post_num",
                source.optInt("favor_post_num", source.optInt("favorite_post_num", -1)));
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
        if (node == null || depth > 5) return null;
        if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            return looksLikeFolderArray(array) ? array : null;
        }
        if (!(node instanceof JSONObject)) return null;
        JSONObject object = (JSONObject) node;
        String[] keys = {"folders", "folder_list", "fav_folders", "favorite_folders",
                "collect_folders", "collections", "list", "items", "data"};
        for (String key : keys) {
            JSONArray array = object.optJSONArray(key);
            if (array != null && looksLikeFolderArray(array)) return array;
        }
        Iterator<String> names = object.keys();
        while (names.hasNext()) {
            Object child = object.opt(names.next());
            JSONArray found = findFavoriteFolderArray(child, depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    private boolean looksLikeFolderArray(JSONArray array) {
        if (array == null || array.length() == 0) return false;
        int limit = Math.min(6, array.length());
        for (int i = 0; i < limit; i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            JSONObject folder = unwrapFavoriteFolder(item);
            if (!favoriteFolderId(folder).isEmpty()) return true;
            if (folder == null) continue;
            if (!first(folder.optString("folder_name"), folder.optString("name"),
                    folder.optString("title")).isEmpty()) return true;
        }
        return false;
    }

    private JSONObject firstFavoriteFolder(JSONArray folders) {
        if (folders == null) return null;
        JSONObject fallback = null;
        for (int i = 0; i < folders.length(); i++) {
            JSONObject folder = unwrapFavoriteFolder(folders.optJSONObject(i));
            if (folder == null) continue;
            if (fallback == null) fallback = folder;
            if (!favoriteFolderId(folder).isEmpty()) return folder;
        }
        return fallback;
    }

    private JSONObject unwrapFavoriteFolder(JSONObject item) {
        JSONObject current = item;
        for (int depth = 0; depth < 4 && current != null; depth++) {
            if (!favoriteFolderId(current).isEmpty()) return current;
            JSONObject next = current.optJSONObject("folder");
            if (next == null) next = current.optJSONObject("folder_info");
            if (next == null) next = current.optJSONObject("fav_folder");
            if (next == null) next = current.optJSONObject("collect_folder");
            if (next == null) next = current.optJSONObject("collection");
            if (next == null) next = current.optJSONObject("data");
            if (next == null) next = current.optJSONObject("item");
            if (next == current) break;
            current = next;
        }
        return current;
    }

    private String favoriteFolderId(JSONObject folder) {
        if (folder == null) return "";
        return first(folder.optString("folder_id"), folder.optString("folderid"),
                folder.optString("fav_folder_id"), folder.optString("collect_folder_id"),
                folder.optString("collection_id"), folder.optString("id"),
                folder.optString("fid"));
    }

    private void prepareSavedPage(String pageTitle) {
        stopQrPolling();
        ensureEmojiCatalog(() -> {});
        screen = "saved";
        bottom.setVisibility(View.GONE);
        leading.setVisibility(View.VISIBLE);
        leading.setOnClickListener(view -> showProfile());
        title.setText(pageTitle);
        action.setVisibility(View.INVISIBLE);
        content.removeAllViews();
    }

    private void showSavedList(String pageTitle, String path, Map<String, String> params) {
        showSavedList(pageTitle, path, params, true, null);
    }

    private void showSavedList(String pageTitle, String path, Map<String, String> params,
                               boolean includeUserId, SavedListFallback fallback) {
        prepareSavedPage(pageTitle);
        showLoading();
        if (!params.containsKey("offset")) params.put("offset", "0");
        if (!params.containsKey("limit")) params.put("limit", "20");
        if (includeUserId) params.put(SecureStrings.userid(), session.userId());
        params.put("x_os_type", "Windows");
        params.put("device_info", "Edge");
        final String cacheKey = savedCacheKey(pageTitle, path);
        api.get(path, params, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                hideLoading();
                JSONObject result = body.optJSONObject("result");
                JSONArray links = findLinks(result == null ? body : result);
                List<FeedItem> items = new ArrayList<>();
                if (links != null) {
                    for (int i = 0; i < links.length(); i++) {
                        JSONObject item = links.optJSONObject(i);
                        if (item != null) {
                            JSONObject value = savedFeedValue(item);
                            if (value != null) items.add(FeedItem.from(value));
                        }
                    }
                }
                if (items.isEmpty()) {
                    collectFeedItems(body, items, new HashMap<>(), 0);
                }
                items = filterItems(items);
                if (items.isEmpty()) {
                    List<FeedItem> cached = filterItems(localCache.savedList(cacheKey));
                    if (!cached.isEmpty()) {
                        toast(MSG_OFFLINE_CACHE);
                        renderSavedItems(pageTitle, cached);
                        return;
                    }
                    if (fallback != null && fallback.onFallback("empty result")) return;
                } else {
                    localCache.saveSavedList(cacheKey, items);
                }
                renderSavedItems(pageTitle, items);
            }

            @Override public void onError(String message) {
                hideLoading();
                localCache.log(pageTitle + " failed: " + message);
                List<FeedItem> cached = filterItems(localCache.savedList(cacheKey));
                if (!cached.isEmpty()) {
                    toast(MSG_OFFLINE_CACHE);
                    renderSavedItems(pageTitle, cached);
                    return;
                }
                if (fallback != null && fallback.onFallback(message)) return;
                showMessage(pageTitle + "加载失败\n" + message);
            }
        });
    }

    private String savedCacheKey(String pageTitle, String path) {
        return pageTitle + "_" + path;
    }

    private void renderSavedItems(String pageTitle, List<FeedItem> items) {
        if (isHistoryPage(pageTitle)) showHistoryList(items);
        else {
            content.addView(feedList(items), match());
            if (items.isEmpty()) showMessage(MSG_EMPTY_CONTENT);
        }
    }

    private boolean isHistoryPage(String pageTitle) {
        return pageTitle != null
                && (pageTitle.contains("历史") || pageTitle.contains("鍘嗗彶"));
    }

    private ListView feedList(List<FeedItem> items) {
        ListView list = new ListView(this);
        list.setBackgroundColor(BG);
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(dp(2));
        list.setAdapter(new FeedAdapter(this, items, session.noImage(),
                session.uiScale() / 100f, session.textScale() / 100f,
                session.darkMode(), PRIMARY, SECONDARY, this::showDetail, this::toggleFeedLike));
        return list;
    }

    private void ensureEmojiCatalog(Runnable ready) {
        if (api == null) return;
        EmojiStore.load(api, ready);
    }

    private void showHistoryList(List<FeedItem> allItems) {
        content.removeAllViews();
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(BG);
        EditText search = new EditText(this);
        search.setHint("搜索历史：标题、摘要或作者");
        search.setHintTextColor(MUTED);
        search.setTextColor(TEXT);
        search.setSingleLine(true);
        search.setTextSize(sp(12));
        Compat.tint(search, PRIMARY);

        FrameLayout results = new FrameLayout(this);
        page.addView(results, match());
        FrameLayout.LayoutParams searchParams =
                new FrameLayout.LayoutParams(-1, dp(40), Gravity.TOP);
        searchParams.leftMargin = dp(7);
        searchParams.rightMargin = dp(7);
        searchParams.topMargin = dp(5);
        page.addView(search, searchParams);
        prepareSearchBar(search, dp(40));
        List<FeedItem> filtered = new ArrayList<>(allItems);
        FeedAdapter adapter = new FeedAdapter(this, filtered, session.noImage(),
                session.uiScale() / 100f, session.textScale() / 100f,
                session.darkMode(), PRIMARY, SECONDARY, this::showDetail, this::toggleFeedLike);
        ListView list = new ListView(this);
        list.setBackgroundColor(BG);
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(dp(2));
        list.setAdapter(adapter);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int scrollState) {
                setSearchBarVisible(search, scrollState == SCROLL_STATE_IDLE);
            }

            @Override public void onScroll(AbsListView view, int firstVisibleItem,
                                           int visibleItemCount, int totalItemCount) {}
        });
        results.addView(list, match());
        TextView emptyView = text(allItems.isEmpty()
                ? "这里暂时没有内容" : "没有找到相关历史记录", 13, MUTED);
        emptyView.setGravity(Gravity.CENTER);
        results.addView(emptyView, match());
        emptyView.setVisibility(allItems.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(allItems.isEmpty() ? View.GONE : View.VISIBLE);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start,
                                                    int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start,
                                                int before, int count) {
                String query = value.toString().trim().toLowerCase(Locale.US);
                filtered.clear();
                for (FeedItem item : allItems) {
                    String haystack = (item.title + "\n" + item.description + "\n" + item.author)
                            .toLowerCase(Locale.US);
                    if (query.isEmpty() || haystack.contains(query)) filtered.add(item);
                }
                adapter.notifyDataSetChanged();
                emptyView.setText("没有找到相关历史记录");
                if (filtered.isEmpty()) {
                    setSearchBarVisible(search, true);
                    emptyView.setVisibility(View.VISIBLE);
                    list.setVisibility(View.GONE);
                } else {
                    emptyView.setVisibility(View.GONE);
                    list.setVisibility(View.VISIBLE);
                }
            }
            @Override public void afterTextChanged(Editable value) {}
        });
        content.addView(page, match());
    }

    private JSONArray findLinks(JSONObject result) {
        if (result == null) return null;
        JSONArray links = result.optJSONArray("links");
        if (links == null) links = result.optJSONArray("list");
        if (links == null) links = result.optJSONArray("moments");
        if (links == null) links = result.optJSONArray("history_visit");
        if (links == null) links = result.optJSONArray("visits");
        if (links == null) links = result.optJSONArray("history");
        if (links == null) links = result.optJSONArray("data");
        if (links == null) links = result.optJSONArray("items");
        if (links == null) links = result.optJSONArray("rows");
        if (links == null) links = result.optJSONArray("records");
        if (links == null) links = result.optJSONArray("favorites");
        if (links == null) links = result.optJSONArray("collects");
        if (links == null) links = result.optJSONArray("link_list");
        if (links == null) links = result.optJSONArray("links_list");
        if (links == null) links = findNestedLinkArray(result, 0);
        return links;
    }

    private JSONArray findNestedLinkArray(Object node, int depth) {
        if (node == null || depth > 5) return null;
        if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            return looksLikeLinkArray(array) ? array : null;
        }
        if (!(node instanceof JSONObject)) return null;
        JSONObject object = (JSONObject) node;
        Iterator<String> names = object.keys();
        while (names.hasNext()) {
            JSONArray found = findNestedLinkArray(object.opt(names.next()), depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    private boolean looksLikeLinkArray(JSONArray array) {
        if (array == null || array.length() == 0) return false;
        int limit = Math.min(8, array.length());
        for (int i = 0; i < limit; i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            JSONObject value = savedFeedValue(item);
            if (value == null) continue;
            String id = value.optString("linkid", value.optString("link_id"));
            if (!id.isEmpty()) return true;
        }
        return false;
    }

    private JSONObject unwrapSavedItem(JSONObject item) {
        JSONObject current = item;
        for (int depth = 0; depth < 4; depth++) {
            if (!current.optString("linkid",
                    current.optString("link_id")).isEmpty()) return current;
            JSONObject next = current.optJSONObject("link");
            if (next == null) next = current.optJSONObject("link_info");
            if (next == null) next = current.optJSONObject("link_detail");
            if (next == null) next = current.optJSONObject("moment");
            if (next == null) next = current.optJSONObject("post");
            if (next == null) next = current.optJSONObject("favorite");
            if (next == null) next = current.optJSONObject("fav");
            if (next == null) next = current.optJSONObject("record");
            if (next == null) next = current.optJSONObject("target");
            if (next == null) next = current.optJSONObject("source");
            if (next == null) next = current.optJSONObject("obj");
            if (next == null) next = current.optJSONObject("content");
            if (next == null) next = current.optJSONObject("data");
            if (next == null) next = current.optJSONObject("item");
            if (next == null || next == current) break;
            current = next;
        }
        return current;
    }

    private JSONObject savedFeedValue(JSONObject wrapper) {
        JSONObject value = unwrapSavedItem(wrapper);
        if (value == null) return null;
        try {
            JSONObject merged = new JSONObject(value.toString());
            copyFirstInt(wrapper, merged, "link_award_num",
                    "link_award_num", "like_num", "award_num", "award_count",
                    "like_count", "liked_num", "praise_num", "praise_count",
                    "total_award_num", "award", "awards", "up_num", "up");
            copyFirstInt(wrapper, merged, "comment_num",
                    "comment_num", "comment_count", "reply_num", "reply_count",
                    "comments_count", "total_comment_num", "comment", "comments");
            if (merged.optJSONObject("user") == null) {
                JSONObject user = findObject(wrapper, 0, "user", "author", "account");
                if (user != null) merged.put("user", user);
                else {
                    String author = findString(wrapper, 0, "author_name", "username",
                            "nickname", "author");
                    if (!author.isEmpty()) {
                        JSONObject fallbackUser = new JSONObject();
                        fallbackUser.put("username", author);
                        merged.put("user", fallbackUser);
                    }
                }
            }
            return merged;
        } catch (Exception ignored) {
            return value;
        }
    }

    private void copyFirstInt(JSONObject source, JSONObject target, String targetKey,
                              String... keys) {
        if (target.optInt(targetKey, 0) > 0) return;
        int value = findInt(source, keys, 0);
        if (value > 0) {
            try {
                target.put(targetKey, value);
            } catch (Exception ignored) {
            }
        }
    }

    private int findInt(JSONObject source, String[] keys, int depth) {
        if (source == null || depth > 4) return 0;
        for (String key : keys) {
            if (source.has(key)) {
                int value = source.optInt(key, 0);
                if (value > 0) return value;
            }
        }
        Iterator<String> names = source.keys();
        while (names.hasNext()) {
            Object value = source.opt(names.next());
            if (value instanceof JSONObject) {
                int found = findInt((JSONObject) value, keys, depth + 1);
                if (found > 0) return found;
            } else if (value instanceof JSONArray) {
                JSONArray array = (JSONArray) value;
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    int found = findInt(item, keys, depth + 1);
                    if (found > 0) return found;
                }
            }
        }
        return 0;
    }

    private JSONObject findObject(JSONObject source, int depth, String... keys) {
        if (source == null || depth > 4) return null;
        for (String key : keys) {
            JSONObject value = source.optJSONObject(key);
            if (value != null) return value;
        }
        Iterator<String> names = source.keys();
        while (names.hasNext()) {
            Object child = source.opt(names.next());
            if (child instanceof JSONObject) {
                JSONObject found = findObject((JSONObject) child, depth + 1, keys);
                if (found != null) return found;
            }
        }
        return null;
    }

    private String findString(JSONObject source, int depth, String... keys) {
        if (source == null || depth > 4) return "";
        for (String key : keys) {
            String value = source.optString(key);
            if (!value.isEmpty() && !value.startsWith("{")) return value;
        }
        Iterator<String> names = source.keys();
        while (names.hasNext()) {
            Object child = source.opt(names.next());
            if (child instanceof JSONObject) {
                String found = findString((JSONObject) child, depth + 1, keys);
                if (!found.isEmpty()) return found;
            }
        }
        return "";
    }

    private LinearLayout settingsPage(String key, String pageTitle) {
        return settingsPage(key, pageTitle, this::showSettingsHome);
    }

    private LinearLayout settingsPage(String key, String pageTitle, Runnable back) {
        stopQrPolling();
        screen = key;
        bottom.setVisibility(View.GONE);
        leading.setVisibility(View.VISIBLE);
        leading.setOnClickListener(view -> back.run());
        title.setText(pageTitle);
        action.setVisibility(View.INVISIBLE);
        content.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = vertical(BG);
        page.setPadding(dp(8), dp(8), dp(8), dp(14));
        scroll.addView(page);
        content.addView(scroll, match());
        animateIn(scroll);
        return page;
    }

    private void showDisplaySettings() {
        LinearLayout page = settingsPage("display_settings", "显示设置");
        LinearLayout panel = card();

        final boolean[] dark = {session.darkMode()};
        final boolean[] bodyBold = {session.bodyBold()};
        addTop(panel, toggleRow("夜间模式", dark[0], value -> dark[0] = value), 0);

        Button fullPreview = button("查看界面预览", R.drawable.ic_search);
        fullPreview.setOnClickListener(view -> showDisplayPreview());
        addTop(panel, fullPreview, 6);

        LinearLayout livePreview = vertical(PANEL);
        TextView previewTitle = text("显示效果预览", 14, TEXT);
        previewTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView previewBody = text("帖子正文会跟随下方设置实时变化。\n第二段用于预览段落间距。", 13, TEXT);
        previewBody.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        TextView previewAction = text("主色按钮", 11, contrast(PRIMARY));
        previewAction.setGravity(Gravity.CENTER);
        Compat.setBackground(previewAction, round(PRIMARY, 7));
        addTop(livePreview, previewTitle, 0);
        addTop(livePreview, previewBody, 3);
        addTop(livePreview, previewAction, 6);
        addTop(panel, livePreview, 8);

        ScaleControl uiScale = settingSlider(panel, "界面大小", "%", 70, 160,
                session.uiScale(), value -> updateDisplayPreview(livePreview, previewTitle,
                        previewBody, previewAction, value, -1, -1));
        ScaleControl textScale = settingSlider(panel, "文字大小", "%", 70, 180,
                session.textScale(), value -> updateDisplayPreview(livePreview, previewTitle,
                        previewBody, previewAction, -1, value, -1));
        ScaleControl padding = settingSlider(panel, "左右边距", "dp", 0, 30,
                session.pagePadding(), value -> updateDisplayPreview(livePreview, previewTitle,
                        previewBody, previewAction, -1, -1, value));
        ScaleControl bodyText = settingSlider(panel, "正文字号", "%", 75, 170,
                session.bodyTextScale(), value -> {
                    previewBody.setTextSize(13 * value / 100f);
                });
        ScaleControl letterSpacing = settingSlider(panel, "字间距", "", 0, 20,
                session.bodyLetterSpacing(), value ->
                        Compat.setLetterSpacing(previewBody, value / 200f));
        ScaleControl paragraphSpacing = settingSlider(panel, "段落间距", "dp", 0, 24,
                session.bodyParagraphSpacing(), value ->
                        previewBody.setPadding(0, dp(value), 0, 0));
        ScaleControl lineSpacing = settingSlider(panel, "行距", "%", 100, 180,
                session.bodyLineSpacing(), value ->
                        previewBody.setLineSpacing(0, value / 100f));
        addTop(panel, toggleRow("正文与一级评论稍加粗", bodyBold[0], value -> {
            bodyBold[0] = value;
            previewBody.setTypeface(value
                    ? Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    : Typeface.DEFAULT);
        }), 4);
        updateDisplayPreview(livePreview, previewTitle, previewBody, previewAction,
                session.uiScale(), session.textScale(), session.pagePadding());
        previewBody.setTextSize(13 * session.bodyTextScale() / 100f);
        Compat.setLetterSpacing(previewBody, session.bodyLetterSpacing() / 200f);
        previewBody.setPadding(0, dp(session.bodyParagraphSpacing()), 0, 0);
        previewBody.setLineSpacing(0, session.bodyLineSpacing() / 100f);

        TextView themeTitle = text("预设颜色主题", 13, TEXT);
        themeTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        addTop(panel, themeTitle, 12);
        LinearLayout themeGrid = vertical(PANEL);
        for (int rowIndex = 0; rowIndex < THEME_NAMES.length; rowIndex += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(themePreset(rowIndex), new LinearLayout.LayoutParams(0, dp(44), 1));
            if (rowIndex + 1 < THEME_NAMES.length) {
                LinearLayout.LayoutParams right =
                        new LinearLayout.LayoutParams(0, dp(44), 1);
                right.leftMargin = dp(5);
                row.addView(themePreset(rowIndex + 1), right);
            } else {
                row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(44), 1));
            }
            addTop(themeGrid, row, rowIndex == 0 ? 4 : 5);
        }
        panel.addView(themeGrid);

        Button save = button("保存显示设置", R.drawable.ic_settings);
        save.setOnClickListener(view -> {
            Integer ui = parseNumber(uiScale.input, 70, 160);
            Integer text = parseNumber(textScale.input, 70, 180);
            Integer pad = parseNumber(padding.input, 0, 30);
            Integer bodySize = parseNumber(bodyText.input, 75, 170);
            Integer letters = parseNumber(letterSpacing.input, 0, 20);
            Integer paragraphs = parseNumber(paragraphSpacing.input, 0, 24);
            Integer lines = parseNumber(lineSpacing.input, 100, 180);
            if (ui == null || text == null || pad == null || bodySize == null
                    || letters == null || paragraphs == null || lines == null) {
                toast("请检查输入数值是否在滑杆范围内");
                return;
            }
            session.setDarkMode(dark[0]);
            session.setUiScale(ui);
            session.setTextScale(text);
            session.setPagePadding(pad);
            session.setBodyTextScale(bodySize);
            session.setBodyLetterSpacing(letters);
            session.setBodyParagraphSpacing(paragraphs);
            session.setBodyLineSpacing(lines);
            session.setBodyBold(bodyBold[0]);
            applyPalette();
            Compat.colorSystemBars(getWindow(), BG);
            buildShell();
            showDisplaySettings();
            toast("显示设置已保存");
        });
        addTop(panel, save, 10);

        Button reset = button("恢复默认设置", R.drawable.ic_refresh);
        reset.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle("恢复默认显示设置")
                .setMessage("主题、字体、间距和界面大小都将恢复为默认值。")
                .setNegativeButton("取消", null)
                .setPositiveButton("恢复", (dialog, which) -> {
                    session.resetDisplaySettings();
                    applyPalette();
                    Compat.colorSystemBars(getWindow(), BG);
                    buildShell();
                    showDisplaySettings();
                    toast("已恢复默认显示设置");
                })
                .show());
        addTop(panel, reset, 7);
        page.addView(panel);
    }

    private void showDisplayPreview() {
        LinearLayout page = settingsPage("display_preview", "界面预览",
                this::showDisplaySettings);

        TextView hint = text("预览使用当前已保存的显示参数", 10, MUTED);
        hint.setGravity(Gravity.CENTER);
        page.addView(hint, new LinearLayout.LayoutParams(-1, dp(28)));

        LinearLayout feedCard = card();
        TextView articleBadge = text("文章", 9, contrast(SECONDARY));
        articleBadge.setGravity(Gravity.CENTER);
        Compat.setBackground(articleBadge, round(SECONDARY, 4));
        feedCard.addView(articleBadge, new LinearLayout.LayoutParams(dp(42), dp(20)));
        TextView previewTitle = text("方屏上的社区，也可以清晰又从容", 15, TEXT);
        previewTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        addTop(feedCard, previewTitle, 6);
        TextView summary = text("这是一条帖子列表摘要，用来观察整体字号、卡片间距和主题颜色。", 11, MUTED);
        summary.setLineSpacing(0, 1.12f);
        addTop(feedCard, summary, 5);
        TextView stats = text("Ronan   👍 128   评论 36", 10, SECONDARY);
        addTop(feedCard, stats, 7);
        page.addView(feedCard);

        LinearLayout detail = card();
        TextView detailTitle = text("帖子正文预览", 16, TEXT);
        detailTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        detail.addView(detailTitle);
        TextView body = text(
                "这是正文第一段，用于预览文字大小、字间距与行距。\n\n"
                        + "这是正文第二段。调整设置后保存，再回到这里就能查看最终效果。",
                14 * session.bodyTextScale() / 100f, TEXT);
        body.setLineSpacing(0, session.bodyLineSpacing() / 100f);
        Compat.setLetterSpacing(body, session.bodyLetterSpacing() / 200f);
        body.setTypeface(session.bodyBold()
                ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.DEFAULT);
        addTop(detail, body, session.bodyParagraphSpacing());
        addTop(page, detail, 7);

        LinearLayout comments = card();
        TextView commentTitle = text("评论层级预览", 14, TEXT);
        commentTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        comments.addView(commentTitle);
        TextView first = text("一级评论会稍微加粗，方便快速浏览主要观点。", 13, TEXT);
        first.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        first.setLineSpacing(0, session.bodyLineSpacing() / 100f);
        addTop(comments, first, 7);

        LinearLayout reply = new LinearLayout(this);
        View rail = new View(this);
        rail.setBackgroundColor(SECONDARY);
        LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(dp(2), -1);
        railParams.rightMargin = dp(8);
        reply.addView(rail, railParams);
        TextView second = text("二级评论使用稍轻的字重，并通过主题色竖线建立层级。", 12, MUTED);
        second.setLineSpacing(0, 1.16f);
        reply.addView(second, new LinearLayout.LayoutParams(0, -2, 1));
        addTop(comments, reply, 7);
        addTop(page, comments, 7);

        Button backToSettings = button("返回继续调整", R.drawable.ic_arrow_back);
        backToSettings.setOnClickListener(view -> showDisplaySettings());
        addTop(page, backToSettings, 9);
    }

    private View themePreset(int index) {
        int primary = THEME_COLORS[index][0];
        int secondary = THEME_COLORS[index][1];
        boolean selected = currentPrimary() == primary && currentSecondary() == secondary;
        LinearLayout item = new LinearLayout(this);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(7), 0, dp(7), 0);
        int background = blend(PANEL, selected ? secondary : MUTED,
                selected ? (session.darkMode() ? 0.34f : 0.18f) : 0.06f);
        GradientDrawable shape = round(background, 7);
        shape.setStroke(dp(1), selected ? primary : blend(PANEL, MUTED, 0.34f));
        Compat.setBackground(item, shape);

        LinearLayout swatches = new LinearLayout(this);
        View first = new View(this);
        Compat.setBackground(first, round(primary, 9));
        swatches.addView(first, new LinearLayout.LayoutParams(dp(18), dp(18)));
        View second = new View(this);
        Compat.setBackground(second, round(secondary, 9));
        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        secondParams.leftMargin = -dp(5);
        swatches.addView(second, secondParams);
        item.addView(swatches, new LinearLayout.LayoutParams(dp(34), dp(22)));

        TextView name = text(THEME_NAMES[index], 11, TEXT);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        item.addView(name, new LinearLayout.LayoutParams(0, -1, 1));
        if (selected) {
            TextView marker = text("✓", 13, primary);
            marker.setGravity(Gravity.CENTER);
            item.addView(marker, new LinearLayout.LayoutParams(dp(20), -1));
        }
        item.setOnClickListener(view -> {
            session.setTheme(colorHex(primary), colorHex(secondary));
            applyPalette();
            Compat.colorSystemBars(getWindow(), BG);
            buildShell();
            showDisplaySettings();
        });
        return item;
    }

    private void showAppSettings() {
        LinearLayout page = settingsPage("app_settings", "应用设置");
        LinearLayout panel = card();
        addTop(panel, toggleRow("无图模式", session.noImage(), value -> {
            session.setNoImage(value);
            feed.clear();
            invalidateFeedView();
        }), 0);
        addTop(panel, toggleRow("图片查看器允许查看原图", session.originalImages(),
                session::setOriginalImages), 4);

        EditText blockKeywords = textField(panel, "屏蔽关键词", session.blockKeywords());
        blockKeywords.setSingleLine(false);
        blockKeywords.setMinLines(2);
        blockKeywords.setGravity(Gravity.CENTER_VERTICAL);
        Button saveFilter = button("保存内容过滤", R.drawable.ic_settings);
        saveFilter.setOnClickListener(view -> {
            session.setBlockKeywords(blockKeywords.getText().toString());
            feed.clear();
            feedOffset = 0;
            invalidateFeedView();
            toast("内容过滤已保存");
        });
        addTop(panel, saveFilter, 7);

        TextView offlineInfo = text("离线缓存 " + formatCacheMb(localCache.offlineBytes())
                + " / 已缓存帖子 " + localCache.detailCount(), 11, MUTED);
        addTop(panel, offlineInfo, 8);

        Button diagnostics = button("导出诊断信息", R.drawable.ic_info);
        diagnostics.setOnClickListener(view -> exportDiagnostics());
        addTop(panel, diagnostics, 7);

        Button clearTempCache = button("清除临时缓存 " + formatCacheMb(tempCacheBytes()),
                R.drawable.ic_trash);
        clearTempCache.setOnClickListener(view -> {
            long before = tempCacheBytes();
            clearTempCacheFiles(getCacheDir());
            EmojiRenderer.clear();
            clearTempCache.setText("清除临时缓存 " + formatCacheMb(tempCacheBytes()));
            toast("已清除临时缓存 " + formatCacheMb(before));
        });
        addTop(panel, clearTempCache, 10);

        Button clearCache = button("清除图片缓存 " + formatCacheMb(ImageLoader.cacheSizeKb() * 1024L),
                R.drawable.ic_trash);
        clearCache.setOnClickListener(view -> {
            int before = ImageLoader.cacheSizeKb();
            ImageLoader.clear();
            clearCache.setText("清除图片缓存 " + formatCacheMb(ImageLoader.cacheSizeKb() * 1024L));
            toast("已清除图片缓存 " + formatCacheMb(before * 1024L));
        });
        addTop(panel, clearCache, 7);

        Button login = button(session.isLoggedIn() ? "退出登录" : "二维码登录",
                R.drawable.ic_logout);
        login.setOnClickListener(view -> {
            if (session.isLoggedIn()) {
                session.clearSession();
                feed.clear();
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
        final boolean[] autoUpdate = {session.autoUpdateCheck()};
        final boolean[] splashEnabled = {session.splashEnabled()};
        addTop(panel, toggleRow("进入软件时检查更新", autoUpdate[0],
                value -> autoUpdate[0] = value), 0);
        addTop(panel, toggleRow("显示开屏动画", splashEnabled[0],
                value -> splashEnabled[0] = value), 4);

        EditText splashText = textField(panel, "开屏文字", session.splashText());
        ScaleControl duration = settingSlider(panel, "开屏时长", "ms", 500, 2600,
                session.splashDuration(), value -> {});

        Button preview = button("预览开屏动画", R.drawable.ic_search);
        preview.setOnClickListener(view ->
                showSplashPreview(splashText.getText().toString().trim(),
                        parseNumber(duration.input, 500, 2600)));
        addTop(panel, preview, 8);

        Button save = button("保存启动设置", R.drawable.ic_settings);
        save.setOnClickListener(view -> {
            Integer durationValue = parseNumber(duration.input, 500, 2600);
            if (durationValue == null) {
                toast("开屏时长请输入 500-2600");
                return;
            }
            session.setAutoUpdateCheck(autoUpdate[0]);
            session.setSplashEnabled(splashEnabled[0]);
            session.setSplashText(splashText.getText().toString());
            session.setSplashDuration(durationValue);
            toast("启动设置已保存");
        });
        addTop(panel, save, 7);
        page.addView(panel);
    }

    private void showSplashPreview(String value, Integer duration) {
        final String textValue = value == null || value.isEmpty()
                ? SessionStore.DEFAULT_SPLASH_TEXT : value;
        final int durationValue = duration == null ? session.splashDuration() : duration;
        FrameLayout overlay = new FrameLayout(this);
        overlay.setTag("splash_preview");
        boolean dark = session.darkMode();
        overlay.setBackgroundColor(dark ? Color.rgb(14, 15, 16) : Color.rgb(246, 247, 249));
        if (!dark) {
            ImageView mark = new ImageView(this);
            mark.setImageResource(R.drawable.splash_logo);
            mark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            FrameLayout.LayoutParams markParams =
                    new FrameLayout.LayoutParams(dp(96), dp(96), Gravity.CENTER);
            markParams.bottomMargin = dp(54);
            overlay.addView(mark, markParams);
        }
        TextView message = text("", 14, TEXT);
        message.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        message.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams messageParams =
                new FrameLayout.LayoutParams(-1, dp(52), Gravity.CENTER);
        messageParams.topMargin = dp(dark ? 0 : 58);
        messageParams.leftMargin = dp(18);
        messageParams.rightMargin = dp(18);
        overlay.addView(message, messageParams);
        TextView close = text("点击任意位置退出预览", 10, MUTED);
        close.setGravity(Gravity.CENTER);
        overlay.addView(close, new FrameLayout.LayoutParams(
                -1, dp(34), Gravity.BOTTOM));
        overlay.setOnClickListener(view -> content.removeView(overlay));
        content.addView(overlay, match());

        final int[] frame = {0};
        Runnable animation = new Runnable() {
            @Override public void run() {
                if (overlay.getParent() == null) return;
                int count = Math.min(frame[0], textValue.length());
                message.setText(textValue.substring(0, count)
                        + (count < textValue.length() ? "_" : ""));
                frame[0]++;
                if (count < textValue.length()) {
                    message.postDelayed(this,
                            Math.max(28, durationValue / Math.max(1, textValue.length() + 4)));
                }
            }
        };
        animation.run();
    }

    private void showAnnouncements() {
        stopQrPolling();
        screen = "announcement_board";
        bottom.setVisibility(View.GONE);
        leading.setVisibility(View.VISIBLE);
        leading.setOnClickListener(view -> showAbout());
        title.setText("\u516c\u544a\u5217\u8868");
        action.setText("");
        setIcon(action, R.drawable.ic_refresh, TEXT, 18);
        action.setVisibility(View.VISIBLE);
        action.setOnClickListener(view -> showAnnouncements());
        content.removeAllViews();
        showLoading();
        AnnouncementChecker.load(new AnnouncementChecker.Callback() {
            @Override public void onResult(List<AnnouncementChecker.Item> items) {
                if (isFinishing() || !"announcement_board".equals(screen)) return;
                renderAnnouncementList(items);
            }

            @Override public void onError(String message) {
                if (isFinishing() || !"announcement_board".equals(screen)) return;
                toast("公告加载失败，已显示本地欢迎公告");
                renderAnnouncementList(Collections.singletonList(welcomeAnnouncement()));
            }
        });
    }

    private void renderAnnouncementList(List<AnnouncementChecker.Item> items) {
        hideLoading();
        content.removeAllViews();
        items = withWelcomeAnnouncement(items);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout page = vertical(BG);
        page.setPadding(dp(8), dp(8), dp(8), dp(14));
        scroll.addView(page);
        if (items == null || items.isEmpty()) {
            TextView empty = text("\u6682\u65e0\u516c\u544a", 13, MUTED);
            empty.setGravity(Gravity.CENTER);
            page.addView(empty, new LinearLayout.LayoutParams(-1, dp(88)));
        } else {
            boolean added = false;
            for (AnnouncementChecker.Item item : items) {
                if (item == null || !item.enabled) continue;
                LinearLayout card = card();
                TextView itemTitle = text(TextUtils.isEmpty(item.title)
                        ? "\u516c\u544a" : item.title, 14, TEXT);
                itemTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                card.addView(itemTitle);
                if (!TextUtils.isEmpty(item.updatedAt)) {
                    addTop(card, text(item.updatedAt, 10, MUTED), 4);
                }
                TextView preview = text(announcementPreview(item.content), 12, MUTED);
                preview.setLineSpacing(0, 1.18f);
                addTop(card, preview, 6);
                card.setOnClickListener(view -> showAnnouncementDialog(item));
                addTop(page, card, 8);
                added = true;
            }
            if (!added) {
                TextView empty = text("\u6682\u65e0\u516c\u544a", 13, MUTED);
                empty.setGravity(Gravity.CENTER);
                page.addView(empty, new LinearLayout.LayoutParams(-1, dp(88)));
            }
        }
        content.addView(scroll, match());
        animateIn(scroll);
    }

    private String announcementPreview(String value) {
        if (value == null) return "";
        String clean = value.replace('\r', '\n').replace("\n\n", "\n").trim();
        int max = 92;
        return clean.length() <= max ? clean : clean.substring(0, max) + "...";
    }

    private void showAbout() {
        LinearLayout page = settingsPage("about", "关于");
        LinearLayout panel = card();
        TextView appName = text("heybox Lite", 20, TEXT);
        appName.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(appName);
        addTop(panel, text("版本 " + appVersion(), 13, MUTED), 6);
        addTop(panel, text("开发者：Ronan", 13, TEXT), 5);
        addTop(panel, text("支持 Android 4.0 及以上系统", 12, MUTED), 5);
        TextView basedOn = text("基于 HeyWear 进行二次开发与方屏适配，非官方应用。", 12, TEXT);
        basedOn.setLineSpacing(0, 1.18f);
        addTop(panel, basedOn, 7);

        TextView disclaimer = text(
                "免责声明：本项目仅用于学习、研究与个人使用，不代表小黑盒、HeyWear 或相关官方立场。"
                        + "本项目基于 HeyWear 进行二次开发与适配，开发者不对因使用本项目造成的账号、"
                        + "数据、设备或其他风险承担额外责任。请在遵守相关法律法规及平台规则的前提下使用。",
                11, MUTED);
        disclaimer.setLineSpacing(0, 1.22f);
        addTop(panel, disclaimer, 10);

        TextView feedbackTitle = text("Bug 反馈群", 13, TEXT);
        feedbackTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        addTop(panel, feedbackTitle, 12);
        addTop(panel, text("QQ群：781941517", 12, TEXT), 5);
        addTop(panel, text("遇到问题可以扫码进群反馈。", 11, MUTED), 3);
        Button feedbackQr = button("查看反馈群二维码", R.drawable.ic_info);
        feedbackQr.setOnClickListener(view -> showFeedbackGroupQr());
        addTop(panel, feedbackQr, 8);

        Button announcements = button("\u516c\u544a\u5217\u8868", R.drawable.ic_info);
        announcements.setOnClickListener(view -> showAnnouncements());
        addTop(panel, announcements, 8);

        TextView updateStatus = text("\u4ece\u81ea\u5efa\u670d\u52a1\u5668\u68c0\u67e5\u6700\u65b0\u7248\u672c", 12, MUTED);
        addTop(panel, updateStatus, 12);
        Button update = button("检查更新", R.drawable.ic_refresh);
        update.setOnClickListener(view -> {
            update.setEnabled(false);
            update.setText("正在检查...");
            UpdateChecker.check(appVersion(), new UpdateChecker.Callback() {
                @Override public void onResult(UpdateChecker.Result result) {
                    if (isFinishing()) return;
                    update.setEnabled(true);
                    if (result.updateAvailable) {
                        showUpdateDialog(result);
                        updateStatus.setText("发现新版本 " + result.version);
                        update.setText("前往下载 " + result.version);
                        update.setOnClickListener(button -> openUpdateUrl(
                                result.downloadUrl.isEmpty() ? result.releaseUrl : result.downloadUrl));
                    } else {
                        updateStatus.setText("当前已是最新版本");
                        update.setText("重新检查");
                    }
                }

                @Override public void onError(String message) {
                    if (isFinishing()) return;
                    update.setEnabled(true);
                    update.setText("重新检查");
                    updateStatus.setText("检查失败：" + message);
                }
            });
        });
        addTop(panel, update, 8);

        Button repository = button("打开 GitHub 项目", R.drawable.ic_info);
        repository.setOnClickListener(view ->
                openUrl("https://github.com/huanghao897/heybox-lite"));
        addTop(panel, repository, 7);
        page.addView(panel);
    }

    private void showFeedbackGroupQr() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        Compat.setBackground(box, round(Color.WHITE, 12));

        TextView title = text("Bug 反馈群", 16, Color.rgb(28, 28, 28));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        box.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView group = text("QQ群：781941517", 12, Color.rgb(84, 84, 84));
        group.setGravity(Gravity.CENTER);
        addTop(box, group, 5);

        ImageView qr = new ImageView(this);
        qr.setImageResource(R.drawable.qq_feedback_group_qr);
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qr.setAdjustViewBounds(true);
        qr.setPadding(dp(4), dp(4), dp(4), dp(4));
        int size = Math.min(
                Math.min(getResources().getDisplayMetrics().widthPixels - dp(56),
                        getResources().getDisplayMetrics().heightPixels - dp(170)),
                dp(300));
        size = Math.max(dp(150), size);
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(size, size);
        qrParams.gravity = Gravity.CENTER_HORIZONTAL;
        qrParams.topMargin = dp(10);
        box.addView(qr, qrParams);

        TextView hint = text("点击空白处关闭", 11, Color.rgb(120, 120, 120));
        hint.setGravity(Gravity.CENTER);
        addTop(box, hint, 8);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(box).create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private LinearLayout toggleRow(String label, boolean initial, ToggleListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(label, 13, TEXT);
        row.addView(title, new LinearLayout.LayoutParams(0, dp(40), 1));
        TextView state = text("", 11, TEXT);
        state.setGravity(Gravity.CENTER);
        row.addView(state, new LinearLayout.LayoutParams(dp(48), dp(28)));
        final boolean[] value = {initial};
        Runnable render = () -> {
            state.setText(value[0] ? "开" : "关");
            int foreground = value[0]
                    ? (session.darkMode() ? Color.BLACK : Color.WHITE)
                    : (session.darkMode() ? Color.WHITE : Color.BLACK);
            int background = value[0]
                    ? (session.darkMode() ? Color.WHITE : Color.BLACK)
                    : (session.darkMode() ? Color.rgb(55, 57, 60) : Color.rgb(220, 222, 225));
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

    private ScaleControl settingSlider(LinearLayout parent, String label, String unit,
                                       int min, int max, int current, IntListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(label, 12, TEXT);
        row.addView(name, new LinearLayout.LayoutParams(dp(74), dp(38)));

        SeekBar slider = new SeekBar(this);
        slider.setMax(max - min);
        slider.setProgress(Math.max(0, Math.min(max - min, current - min)));
        Compat.tint(slider, PRIMARY);
        row.addView(slider, new LinearLayout.LayoutParams(0, dp(38), 1));

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(String.valueOf(current));
        input.setTextSize(sp(11));
        input.setTextColor(TEXT);
        input.setGravity(Gravity.CENTER);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        Compat.tint(input, PRIMARY);
        row.addView(input, new LinearLayout.LayoutParams(dp(48), dp(38)));

        TextView suffix = text(unit, 10, MUTED);
        suffix.setGravity(Gravity.CENTER);
        row.addView(suffix, new LinearLayout.LayoutParams(dp(24), dp(38)));
        addTop(parent, row, 4);

        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                                                    boolean fromUser) {
                int value = min + progress;
                input.setText(String.valueOf(value));
                input.setSelection(input.length());
                listener.onChanged(value);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        return new ScaleControl(input, slider);
    }

    private void updateDisplayPreview(LinearLayout preview, TextView heading, TextView body,
                                      TextView actionView, int ui, int textValue, int padding) {
        if (ui >= 0) {
            preview.setScaleX(1f);
            preview.setScaleY(1f);
            float scale = ui / 100f;
            int vertical = Math.max(5, Math.round(7 * scale));
            preview.setPadding(preview.getPaddingLeft(), dp(vertical),
                    preview.getPaddingRight(), dp(vertical));
            actionView.setMinHeight(dp(Math.max(24, Math.round(28 * scale))));
            actionView.setPadding(dp(8), 0, dp(8), 0);
        }
        if (textValue >= 0) {
            float scale = textValue / 100f;
            heading.setTextSize(14 * scale);
            body.setTextSize(11 * scale);
            actionView.setTextSize(11 * scale);
        }
        if (padding >= 0) {
            int value = Math.round(padding * getResources().getDisplayMetrics().density);
            preview.setPadding(value, preview.getPaddingTop(),
                    value, preview.getPaddingBottom());
        }
    }

    private int currentPrimary() {
        try {
            String saved = session.primaryColor();
            return saved.isEmpty() ? (session.darkMode() ? Color.WHITE : Color.BLACK)
                    : Color.parseColor(saved);
        } catch (Exception ignored) {
            return session.darkMode() ? Color.WHITE : Color.BLACK;
        }
    }

    private int currentSecondary() {
        try {
            String saved = session.secondaryColor();
            return saved.isEmpty() ? (session.darkMode()
                    ? Color.rgb(150, 190, 220) : Color.rgb(35, 125, 178))
                    : Color.parseColor(saved);
        } catch (Exception ignored) {
            return session.darkMode() ? Color.rgb(150, 190, 220)
                    : Color.rgb(35, 125, 178);
        }
    }

    private static String colorHex(int color) {
        return String.format(Locale.US, "#%02X%02X%02X",
                Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int contrast(int color) {
        int luminance = (Color.red(color) * 299 + Color.green(color) * 587
                + Color.blue(color) * 114) / 1000;
        return luminance >= 150 ? Color.BLACK : Color.WHITE;
    }

    private static int readableOn(int color) {
        return contrast(color);
    }

    private static int blend(int base, int overlay, float amount) {
        float value = Math.max(0f, Math.min(1f, amount));
        float keep = 1f - value;
        return Color.rgb(
                Math.round(Color.red(base) * keep + Color.red(overlay) * value),
                Math.round(Color.green(base) * keep + Color.green(overlay) * value),
                Math.round(Color.blue(base) * keep + Color.blue(overlay) * value));
    }

    @SuppressWarnings("deprecation")
    private String appVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "1.70";
        }
    }

    private void openImage(ImageView source, String url) {
        int[] location = new int[2];
        source.getLocationOnScreen(location);
        Intent intent = new Intent(this, ImageViewerActivity.class);
        intent.putExtra(ImageViewerActivity.EXTRA_URL, url);
        intent.putExtra(ImageViewerActivity.EXTRA_ORIGIN_X,
                location[0] + source.getWidth() / 2);
        intent.putExtra(ImageViewerActivity.EXTRA_ORIGIN_Y,
                location[1] + source.getHeight() / 2);
        intent.putExtra(ImageViewerActivity.EXTRA_ORIGIN_WIDTH, source.getWidth());
        intent.putExtra(ImageViewerActivity.EXTRA_ORIGIN_HEIGHT, source.getHeight());
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
            toast("无法打开链接");
        }
    }

    private void openUpdateUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            toast("没有可用下载链接");
            return;
        }
        openUrl(url);
    }

    private void exportDiagnostics() {
        String diagnostics = buildDiagnostics();
        java.io.File file = localCache.writeDiagnostics(diagnostics);
        Uri fileUri = DiagnosticsProvider.uriFor(file);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            share.setClipData(ClipData.newUri(getContentResolver(), file.getName(), fileUri));
        }
        share.putExtra(Intent.EXTRA_SUBJECT, "heybox Lite diagnostics");
        share.putExtra(Intent.EXTRA_STREAM, fileUri);
        share.putExtra(Intent.EXTRA_TEXT, "heybox Lite diagnostics txt\n"
                + "TXT file: " + file.getName()
                + "\nPath: " + file.getAbsolutePath());
        try {
            startActivity(Intent.createChooser(share, "导出诊断信息"));
        } catch (Exception ignored) {
            toast("诊断信息已保存：" + file.getAbsolutePath());
        }
    }

    private String buildDiagnostics() {
        StringBuilder out = new StringBuilder();
        out.append("heybox Lite diagnostics\n");
        out.append("exportTimeLocal: ").append(diagnosticTime()).append('\n');
        out.append("exportTimeMillis: ").append(System.currentTimeMillis()).append('\n');
        out.append("timeZone: ").append(java.util.TimeZone.getDefault().getID()).append('\n');
        out.append("version: ").append(appVersion()).append('\n');
        out.append("currentLinkId: ").append(currentLinkId).append('\n');
        out.append("currentLinkHsrc: ").append(currentLinkHsrc).append('\n');
        out.append("android: ").append(Build.VERSION.RELEASE)
                .append(" api ").append(Build.VERSION.SDK_INT).append('\n');
        out.append("device: ").append(Build.MANUFACTURER).append(' ')
                .append(Build.MODEL).append('\n');
        out.append("screen: ").append(screen).append('\n');
        out.append("loggedIn: ").append(session.isLoggedIn()).append('\n');
        out.append("signInFlow: task-list-v2-first/no-native\n");
        out.append("feedCount: ").append(feed.size()).append('\n');
        out.append("feedOffset: ").append(feedOffset).append('\n');
        out.append("feedNoMore: ").append(feedNoMore).append('\n');
        out.append("offlineFeedSavedAt: ").append(localCache.feedSavedAt()).append('\n');
        out.append("offlineBytes: ").append(localCache.offlineBytes()).append('\n');
        out.append("cachedDetails: ").append(localCache.detailCount()).append('\n');
        out.append("imageMemoryCacheKb: ").append(ImageLoader.cacheSizeKb()).append('\n');
        out.append("emojiMemoryCacheKb: ").append(EmojiRenderer.cacheSizeKb()).append('\n');
        out.append("noImage: ").append(session.noImage()).append('\n');
        out.append("darkMode: ").append(session.darkMode()).append('\n');
        out.append("keywords: ").append(session.blockKeywords()).append('\n');
        if (!lastDetailDiagnostics.isEmpty()) {
            out.append("\nlast detail diagnostics:\n").append(lastDetailDiagnostics);
            if (!lastDetailDiagnostics.endsWith("\n")) out.append('\n');
        } else {
            out.append("\nlast detail diagnostics: none captured\n");
        }
        out.append("\nrecent events:\n").append(localCache.recentLog());
        return out.toString();
    }

    private String diagnosticTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new Date());
    }

    private String buildDetailDiagnostics(JSONObject body, FeedItem fallback,
                                          JSONObject link, JSONArray fallbackImages) {
        StringBuilder out = new StringBuilder();
        out.append("detail screen: ").append(screen).append('\n');
        out.append("currentLinkId: ").append(currentLinkId).append('\n');
        out.append("fallbackId: ").append(fallback == null ? "" : fallback.id).append('\n');
        out.append("fallbackTitle: ")
                .append(compactLogText(fallback == null ? "" : fallback.title, 180))
                .append('\n');
        out.append("fallbackArticle: ")
                .append(fallback != null && fallback.article).append('\n');
        out.append("bodyKeys: ");
        if (body != null) {
            Iterator<String> keys = body.keys();
            while (keys.hasNext()) out.append(keys.next()).append(' ');
        }
        out.append('\n');
        if (link == null) {
            out.append("link: null\n");
        } else {
            out.append("linkid: ").append(link.optString("linkid",
                    link.optString("link_id"))).append('\n');
            out.append("title: ")
                    .append(compactLogText(link.optString("title"), 180)).append('\n');
            out.append("use_concept_type: ")
                    .append(link.opt("use_concept_type")).append('\n');
            out.append("is_article: ").append(link.opt("is_article")).append('\n');
            out.append("link_type: ").append(link.opt("link_type")).append('\n');
            out.append("content_type: ").append(link.opt("content_type")).append('\n');
            out.append("has imgs: ").append(link.has("imgs"))
                    .append(" count=")
                    .append(link.optJSONArray("imgs") == null ? 0 : link.optJSONArray("imgs").length())
                    .append('\n');
            out.append(RichContent.diagnostics(link, fallbackImages));
        }
        return out.toString();
    }

    private String compactLogText(String value, int max) {
        String clean = value == null ? "" : value.replace('\n', ' ')
                .replace('\r', ' ').replaceAll("\\s+", " ").trim();
        if (clean.length() <= max) return clean;
        return clean.substring(0, Math.max(0, max)) + "...";
    }

    private interface ToggleListener {
        void onChanged(boolean value);
    }

    private interface IntListener {
        void onChanged(int value);
    }

    private static final class ScaleControl {
        final EditText input;
        final SeekBar slider;

        ScaleControl(EditText input, SeekBar slider) {
            this.input = input;
            this.slider = slider;
        }
    }

    private void addLegacySettings(LinearLayout page) {
        TextView settingsTitle = text("设置", 15, TEXT);
        settingsTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        addTop(page, settingsTitle, 12);

        Switch images = new Switch(this);
        images.setText("无图模式");
        images.setTextColor(TEXT);
        images.setTextSize(sp(14));
        images.setChecked(session.noImage());
        Compat.tint(images, PRIMARY);
        images.setOnCheckedChangeListener((button, checked) -> {
            session.setNoImage(checked);
            feed.clear();
        });
        addTop(page, images, 8);

        Switch originals = new Switch(this);
        originals.setText("正文图片显示原图");
        originals.setTextColor(TEXT);
        originals.setTextSize(sp(14));
        originals.setChecked(session.originalImages());
        Compat.tint(originals, PRIMARY);
        originals.setOnCheckedChangeListener((button, checked) ->
                session.setOriginalImages(checked));
        addTop(page, originals, 2);

        Switch theme = new Switch(this);
        theme.setText(session.darkMode() ? "夜间模式" : "白天模式");
        theme.setTextColor(TEXT);
        theme.setTextSize(sp(14));
        theme.setChecked(session.darkMode());
        Compat.tint(theme, PRIMARY);
        theme.setOnCheckedChangeListener((button, checked) -> {
            session.setDarkMode(checked);
            recreate();
        });
        addTop(page, theme, 2);

        LinearLayout display = card();
        TextView displayTitle = text("显示", 15, TEXT);
        displayTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        display.addView(displayTitle);
        EditText uiScale = numberField(display, "界面大小 (%)", session.uiScale());
        EditText textScale = numberField(display, "文字大小 (%)", session.textScale());
        EditText padding = numberField(display, "左右边距 (dp)", session.pagePadding());
        EditText accent = textField(display, "主色调", session.primaryColor().isEmpty()
                ? (session.darkMode() ? "#FFFFFF" : "#000000") : session.primaryColor());
        Button save = button("保存显示设置", R.drawable.ic_settings);
        save.setOnClickListener(view -> {
            Integer ui = parseNumber(uiScale, 70, 160);
            Integer text = parseNumber(textScale, 70, 180);
            Integer pad = parseNumber(padding, 0, 30);
            String accentValue = accent.getText().toString().trim();
            if (ui == null || text == null || pad == null || !validColor(accentValue)) {
                toast("请输入有效数字：界面 70-160，文字 70-180，边距 0-30");
                return;
            }
            session.setUiScale(ui);
            session.setTextScale(text);
            session.setPagePadding(pad);
            session.setPrimaryColor(accentValue);
            toast("显示设置已保存");
            recreate();
        });
        addTop(display, save, 8);
        addTop(page, display, 8);

        Button clearCache = button("清除图片缓存", R.drawable.ic_trash);
        clearCache.setOnClickListener(view -> {
            int before = ImageLoader.cacheSizeKb();
            ImageLoader.clear();
            toast("已清除图片缓存 " + Math.max(1, before) + " KB");
        });
        addTop(page, clearCache, 8);

        Button login = button(session.isLoggedIn() ? "退出登录" : "二维码登录",
                R.drawable.ic_logout);
        login.setOnClickListener(view -> {
            if (session.isLoggedIn()) {
                session.clearSession();
                feed.clear();
                toast("已退出登录");
            }
            showLogin();
        });
        addTop(page, login, 12);
    }

    private EditText numberField(LinearLayout parent, String label, int current) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text(label, 12, TEXT), new LinearLayout.LayoutParams(0, dp(40), 1));
        EditText input = new EditText(this);
        input.setText(String.valueOf(current));
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setTextSize(sp(13));
        input.setGravity(Gravity.CENTER);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        Compat.tint(input, PRIMARY);
        row.addView(input, new LinearLayout.LayoutParams(dp(72), dp(40)));
        addTop(parent, row, 3);
        return input;
    }

    private EditText textField(LinearLayout parent, String label, String current) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text(label, 12, TEXT), new LinearLayout.LayoutParams(0, dp(40), 1));
        EditText input = new EditText(this);
        input.setText(current);
        input.setTextColor(TEXT);
        input.setTextSize(sp(12));
        input.setGravity(Gravity.CENTER);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        Compat.tint(input, PRIMARY);
        row.addView(input, new LinearLayout.LayoutParams(dp(96), dp(40)));
        addTop(parent, row, 3);
        return input;
    }

    private long tempCacheBytes() {
        return dirSize(getCacheDir()) + EmojiRenderer.cacheSizeKb() * 1024L;
    }

    private long dirSize(java.io.File file) {
        if (file == null || !file.exists()) return 0L;
        if (file.isFile()) return Math.max(0L, file.length());
        java.io.File[] children = file.listFiles();
        if (children == null) return 0L;
        long total = 0L;
        for (java.io.File child : children) total += dirSize(child);
        return total;
    }

    private void clearTempCacheFiles(java.io.File dir) {
        if (dir == null || !dir.isDirectory()) return;
        java.io.File[] children = dir.listFiles();
        if (children == null) return;
        for (java.io.File child : children) deleteFileTree(child);
    }

    private void deleteFileTree(java.io.File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            java.io.File[] children = file.listFiles();
            if (children != null) {
                for (java.io.File child : children) deleteFileTree(child);
            }
        }
        file.delete();
    }

    private String formatCacheMb(long bytes) {
        return String.format(Locale.US, "%.1f MB", Math.max(0L, bytes) / 1048576f);
    }

    private boolean validColor(String value) {
        try {
            Color.parseColor(value);
            return value.matches("#[0-9a-fA-F]{6}");
        } catch (Exception ignored) {
            return false;
        }
    }

    private Integer parseNumber(EditText input, int min, int max) {
        try {
            int value = Integer.parseInt(input.getText().toString().trim());
            return value < min || value > max ? null : value;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public void onBackPressed() {
        if ("detail".equals(screen)) {
            if (detailPager != null && detailPager.showingComments()) {
                detailPager.showArticle(true);
                return;
            }
            returnFromDetailSmooth();
        }
        else if ("saved".equals(screen)) showProfile();
        else if ("announcement_board".equals(screen)) showAbout();
        else if ("display_preview".equals(screen)) showDisplaySettings();
        else if ("display_settings".equals(screen)
                || "startup_settings".equals(screen)
                || "app_settings".equals(screen)
                || "about".equals(screen)) showSettingsHome();
        else if ("settings_home".equals(screen)) showProfile();
        else if (!"feed".equals(screen)) showFeed();
        else super.onBackPressed();
    }

    private void returnFromDetail() {
        saveCurrentDetailProgress();
        detailRequestToken++;
        detailPager = null;
        detailScroll = null;
        if ("saved".equals(detailReturn)) showProfile();
        else if ("search".equals(detailReturn)) showSearch();
        else showFeed();
    }

    private void returnFromDetailSmooth() {
        String targetKey = backTargetScreenKey();
        Bitmap overlay = screenSnapshot(targetKey);
        returnFromDetail();
        showShellTransitionOverlay(overlay);
    }

    private void saveCurrentDetailProgress() {
        if (!"detail".equals(screen) || detailScroll == null || currentLinkId.isEmpty()
                || localCache == null) return;
        localCache.saveScroll(currentLinkId, detailScroll.getScrollY());
    }

    @Override
    protected void onPause() {
        saveCurrentDetailProgress();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        saveCurrentDetailProgress();
        stopQrPolling();
        ImageLoader.cancelTree(content);
        handler.removeCallbacksAndMessages(null);
        if (writeTokenProvider != null) writeTokenProvider.close();
        if (api != null) api.close();
        super.onDestroy();
    }

    private void showLoading() {
        hideLoading();
        LoadingSpinnerView progress = new LoadingSpinnerView(this);
        progress.setTag("loading");
        progress.setColor(PRIMARY);
        content.addView(progress, new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER));
    }

    private void hideLoading() {
        View loading = content.findViewWithTag("loading");
        if (loading != null) content.removeView(loading);
    }

    private void showMessage(String message) {
        content.removeAllViews();
        TextView view = text(message, 13, MUTED);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(20), dp(20), dp(20), dp(20));
        content.addView(view, match());
    }

    private LinearLayout card() {
        LinearLayout card = vertical(PANEL);
        card.setPadding(dp(10), dp(9), dp(10), dp(9));
        Compat.setBackground(card, round(PANEL, 8));
        return card;
    }

    private TextView icon(String value) {
        TextView view = text(value, 23, TEXT);
        view.setGravity(Gravity.CENTER);
        view.setContentDescription(value);
        return view;
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextSize(sp(size));
        view.setTextColor(color);
        Compat.setLetterSpacing(view, 0);
        return view;
    }

    private Button button(String value) {
        return button(value, 0);
    }

    private Button button(String value, int iconRes) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(sp(12));
        int foreground = contrast(PRIMARY);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setGravity(Gravity.CENTER);
        Compat.setBackground(button, round(PRIMARY, 8));
        if (iconRes != 0) setLeftIcon(button, iconRes, foreground, 17);
        return button;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private void setIcon(TextView view, int resource, int color, int size) {
        Drawable drawable = Compat.tintedDrawable(this, resource, color);
        if (drawable == null) return;
        drawable.setBounds(0, 0, dp(size), dp(size));
        view.setCompoundDrawables(null, drawable, null, null);
    }

    private void setLeftIcon(TextView view, int resource, int color, int size) {
        Drawable drawable = Compat.tintedDrawable(this, resource, color);
        if (drawable == null) return;
        drawable.setBounds(0, 0, dp(size), dp(size));
        view.setCompoundDrawables(drawable, null, null, null);
        view.setCompoundDrawablePadding(dp(4));
    }

    private void animateIn(View view) {
        view.setAlpha(0f);
        view.setTranslationY(dp(7));
        view.animate().alpha(1f).translationY(0).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void runWithPressFeedback(View view, Runnable action) {
        if (action == null) return;
        view.setEnabled(false);
        view.animate().cancel();
        view.animate().scaleX(0.98f).scaleY(0.98f).setDuration(65).start();
        handler.postDelayed(() -> {
            view.animate().scaleX(1f).scaleY(1f).setDuration(90)
                    .setInterpolator(new DecelerateInterpolator()).start();
            view.setEnabled(true);
            if (!isFinishing()) action.run();
        }, 75);
    }

    private LinearLayout vertical(int color) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
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
        float scale = session == null ? 1f : session.uiScale() / 100f;
        return Math.round(value * getResources().getDisplayMetrics().density * scale);
    }

    private float sp(float value) {
        float scale = session == null ? 1f : session.textScale() / 100f;
        return value * scale;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private String formatTime(long seconds) {
        if (seconds <= 0) return "";
        return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                .format(new Date(seconds * 1000L));
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) return value;
        }
        return "";
    }
}
