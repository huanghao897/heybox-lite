package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class UserSpacePage {
    interface Host {
        boolean isActive();

        void openPost(FeedItem item);
    }

    private final Activity activity;
    private final SessionStore session;
    private final ApiClient api;
    private final LocalCache localCache;
    private final ThemeTokens tokens;
    private final float uiScale;
    private final float textScale;
    private final Host host;

    UserSpacePage(Activity activity, SessionStore session, ApiClient api,
                  LocalCache localCache, ThemeTokens tokens, Host host) {
        this.activity = activity;
        this.session = session;
        this.api = api;
        this.localCache = localCache;
        this.tokens = tokens;
        this.uiScale = session.uiScale() / 100.0f;
        this.textScale = session.textScale() / 100.0f;
        this.host = host;
    }

    View create(String userId, String fallbackName, String fallbackAvatar,
                int horizontalPadding, int topPadding) {
        ScrollView scroll = new ScrollView(this.activity);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(this.tokens.background);
        LinearLayout page = vertical(this.tokens.background);
        page.setPadding(horizontalPadding, topPadding, horizontalPadding, dp(12));
        scroll.addView(page);

        LinearLayout profile = profileHeader(
                fallbackName, userId, fallbackAvatar, null);
        LinearLayout.LayoutParams profileParams =
                new LinearLayout.LayoutParams(-1, -2);
        profileParams.bottomMargin = dp(8);
        page.addView(profile, profileParams);

        List<FeedItem> items = new ArrayList<>();
        boolean[] articlesOnly = {false};
        TextView status = text("正在加载动态", 12.0f, this.tokens.muted);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(16), dp(22), dp(16), dp(22));
        LinearLayout events = vertical(this.tokens.background);
        Runnable render = () -> renderEvents(
                items, events, status, articlesOnly[0]);
        LinearLayout tabs = tabs(articlesOnly, render);
        LinearLayout.LayoutParams tabsParams =
                new LinearLayout.LayoutParams(-1, -2);
        tabsParams.bottomMargin = dp(8);
        page.addView(tabs, tabsParams);
        page.addView(status);
        page.addView(events);
        load(userId, fallbackName, fallbackAvatar, profile,
                items, events, status, articlesOnly);
        return scroll;
    }

    private void load(String userId, String fallbackName, String fallbackAvatar,
                      LinearLayout profile, List<FeedItem> items,
                      LinearLayout events, TextView status,
                      boolean[] articlesOnly) {
        this.api.get(EndpointProvider.profileUserLinks(),
                OfficialRequestParams.profileLinks(userId, 0, 20),
                new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject body) {
                        if (!host.isActive()) return;
                        JSONObject user = ProfileData.user(body);
                        if (user != null && profile.getParent() instanceof ViewGroup) {
                            ViewGroup parent = (ViewGroup) profile.getParent();
                            int index = parent.indexOfChild(profile);
                            ViewGroup.LayoutParams params = profile.getLayoutParams();
                            parent.removeView(profile);
                            parent.addView(profileHeader(fallbackName, userId,
                                    fallbackAvatar, user), index, params);
                        }
                        items.clear();
                        items.addAll(parseEvents(body));
                        renderEvents(items, events, status, articlesOnly[0]);
                    }

                    @Override
                    public void onError(String message) {
                        if (!host.isActive()) return;
                        status.setVisibility(View.VISIBLE);
                        status.setText("动态加载失败");
                        localCache.log("user events failed: " + message);
                    }
                });
    }

    private LinearLayout profileHeader(String fallbackName, String userId,
                                       String fallbackAvatar, JSONObject user) {
        LinearLayout profile = vertical(this.tokens.panel);
        profile.setPadding(dp(14), dp(12), dp(14), dp(10));
        Compat.setBackground(profile, round(this.tokens.panel, 12));
        Compat.clipToOutline(profile);

        LinearLayout top = new LinearLayout(this.activity);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView avatar = new ImageView(this.activity);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Compat.setBackground(avatar, round(this.session.darkMode()
                ? Color.rgb(46, 48, 52) : Color.rgb(232, 234, 236), 28));
        Compat.clipToOutline(avatar);
        top.addView(avatar, new LinearLayout.LayoutParams(dp(58), dp(58)));
        String avatarUrl = Json.first(user == null ? ""
                        : user.optString("avatar", user.optString("avartar")),
                fallbackAvatar);
        if (!this.session.noImage() && !avatarUrl.isEmpty()) {
            ImageLoader.intoPlain(avatar, avatarUrl, 144);
        }

        LinearLayout copy = vertical(Color.TRANSPARENT);
        LinearLayout.LayoutParams copyParams =
                new LinearLayout.LayoutParams(0, -2, 1.0f);
        copyParams.leftMargin = dp(11);
        top.addView(copy, copyParams);
        String nameValue = Json.first(user == null ? ""
                        : user.optString("username", user.optString("nickname",
                        user.optString("name"))),
                fallbackName, "小黑盒用户");
        LinearLayout nameRow = new LinearLayout(this.activity);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(nameValue, 17.0f, this.tokens.text);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        nameRow.addView(name, new LinearLayout.LayoutParams(0, -2, 1.0f));
        addLevelBadge(nameRow, user);
        copy.addView(nameRow);
        TextView id = text("ID " + userId, 10.0f, this.tokens.muted);
        id.setSingleLine(true);
        addTop(copy, id, 2);
        String signature = user == null ? "" : Json.first(
                user.optString("signature"), user.optString("desc"));
        if (!signature.isEmpty()) {
            TextView description = text(signature, 11.0f, this.tokens.muted);
            description.setMaxLines(2);
            description.setEllipsize(TextUtils.TruncateAt.END);
            description.setLineSpacing(0.0f, 1.12f);
            addTop(copy, description, 4);
        }
        profile.addView(top);

        LinearLayout stats = new LinearLayout(this.activity);
        stats.setGravity(Gravity.CENTER);
        addStat(stats, ProfileData.followCount(user), "关注");
        addStat(stats, ProfileData.fanCount(user), "粉丝");
        addStat(stats, ProfileData.likeCount(user), "获赞");
        addTop(profile, stats, 11);
        View divider = new View(this.activity);
        divider.setBackgroundColor(this.tokens.hairline);
        LinearLayout.LayoutParams dividerParams =
                new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.topMargin = dp(10);
        profile.addView(divider, dividerParams);
        return profile;
    }

    private LinearLayout tabs(boolean[] articlesOnly, Runnable render) {
        LinearLayout row = new LinearLayout(this.activity);
        row.setPadding(dp(4), dp(4), dp(4), dp(4));
        Compat.setBackground(row, round(this.tokens.panel, 10));
        Compat.clipToOutline(row);
        LinearLayout dynamic = tab("动态", !articlesOnly[0]);
        LinearLayout articles = tab("投稿", articlesOnly[0]);
        dynamic.setOnClickListener(view -> {
            if (!articlesOnly[0]) return;
            articlesOnly[0] = false;
            updateTabs(row, false);
            render.run();
        });
        articles.setOnClickListener(view -> {
            if (articlesOnly[0]) return;
            articlesOnly[0] = true;
            updateTabs(row, true);
            render.run();
        });
        row.addView(dynamic, new LinearLayout.LayoutParams(0, dp(43), 1.0f));
        row.addView(articles, new LinearLayout.LayoutParams(0, dp(43), 1.0f));
        return row;
    }

    private LinearLayout tab(String label, boolean active) {
        LinearLayout tab = vertical(this.tokens.panel);
        tab.setGravity(Gravity.CENTER);
        Compat.setBackground(tab, round(active
                ? this.tokens.faintAccent() : Color.TRANSPARENT, 7));
        TextView title = text(label, 13.5f,
                active ? this.tokens.text : this.tokens.muted);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT, active
                ? Typeface.BOLD : Typeface.NORMAL);
        tab.addView(title, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        View indicator = new View(this.activity);
        Compat.setBackground(indicator, round(this.tokens.accent, 1));
        indicator.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
        LinearLayout.LayoutParams indicatorParams =
                new LinearLayout.LayoutParams(dp(24), dp(2));
        indicatorParams.gravity = Gravity.CENTER_HORIZONTAL;
        tab.addView(indicator, indicatorParams);
        return tab;
    }

    private void updateTabs(LinearLayout row, boolean articlesOnly) {
        for (int i = 0; i < row.getChildCount(); i++) {
            LinearLayout tab = (LinearLayout) row.getChildAt(i);
            TextView label = (TextView) tab.getChildAt(0);
            View indicator = tab.getChildAt(1);
            boolean active = articlesOnly ? i == 1 : i == 0;
            label.setTextColor(active ? this.tokens.text : this.tokens.muted);
            label.setTypeface(Typeface.DEFAULT, active
                    ? Typeface.BOLD : Typeface.NORMAL);
            Compat.setBackground(tab, round(active
                    ? this.tokens.faintAccent() : Color.TRANSPARENT, 7));
            indicator.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void addStat(LinearLayout row, int value, String label) {
        LinearLayout item = vertical(Color.TRANSPARENT);
        item.setGravity(Gravity.CENTER);
        TextView number = text(Format.commentLikeCount(Math.max(0, value)),
                15.5f, this.tokens.text);
        number.setGravity(Gravity.CENTER);
        number.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView caption = text(label, 9.5f, this.tokens.muted);
        caption.setGravity(Gravity.CENTER);
        caption.setSingleLine(true);
        item.addView(number);
        item.addView(caption);
        row.addView(item, new LinearLayout.LayoutParams(0, -2, 1.0f));
    }

    private List<FeedItem> parseEvents(JSONObject body) {
        JSONArray array = ProfileData.posts(body);
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

    private void renderEvents(List<FeedItem> items, LinearLayout events,
                              TextView status, boolean articlesOnly) {
        events.removeAllViews();
        int count = 0;
        for (FeedItem item : items) {
            if (articlesOnly && !item.article) continue;
            events.addView(postCard(item));
            count++;
        }
        status.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        if (count == 0) status.setText(articlesOnly ? "暂无投稿" : "暂无动态");
        events.addView(new View(this.activity),
                new LinearLayout.LayoutParams(-1, dp(76)));
    }

    View postCard(FeedItem item) {
        LinearLayout card = new LinearLayout(this.activity);
        card.setGravity(Gravity.TOP);
        card.setPadding(dp(14), dp(11), dp(14), dp(10));
        Compat.setBackground(card, round(this.tokens.panel, 10));
        Compat.clipToOutline(card);
        LinearLayout copy = vertical(Color.TRANSPARENT);
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1.0f));

        LinearLayout meta = new LinearLayout(this.activity);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        if (item.pinned) {
            TextView pinned = text("置顶", 9.5f, this.tokens.accent);
            pinned.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(-2, -2);
            params.rightMargin = dp(7);
            meta.addView(pinned, params);
        }
        List<String> metaParts = new ArrayList<>();
        if (item.article) metaParts.add("投稿");
        if (!TextUtils.isEmpty(item.topicName)) metaParts.add(item.topicName);
        if (item.createdAt > 0L) metaParts.add(Format.relativeTime(item.createdAt));
        if (!metaParts.isEmpty()) {
            TextView value = text(TextUtils.join(" · ", metaParts),
                    9.5f, this.tokens.muted);
            value.setSingleLine(true);
            value.setEllipsize(TextUtils.TruncateAt.END);
            meta.addView(value, new LinearLayout.LayoutParams(0, -2, 1.0f));
        }
        if (meta.getChildCount() > 0) copy.addView(meta);

        String titleValue = RichContent.plainText(Json.first(
                item.title, item.description, "暂无内容"));
        TextView title = text(titleValue, 14.5f, this.tokens.text);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setLineSpacing(0.0f, 1.08f);
        EmojiRenderer.set(title, titleValue, this.session.darkMode());
        addTop(copy, title, meta.getChildCount() > 0 ? 5 : 0);
        String description = RichContent.plainText(item.description);
        if (!TextUtils.isEmpty(description) && !description.equals(titleValue)) {
            TextView summary = text(description, 11.0f, this.tokens.muted);
            summary.setMaxLines(2);
            summary.setEllipsize(TextUtils.TruncateAt.END);
            summary.setLineSpacing(0.0f, 1.08f);
            EmojiRenderer.set(summary, description, this.session.darkMode());
            addTop(copy, summary, 4);
        }

        LinearLayout stats = new LinearLayout(this.activity);
        stats.setGravity(Gravity.CENTER_VERTICAL);
        stats.addView(new View(this.activity),
                new LinearLayout.LayoutParams(0, dp(22), 1.0f));
        stats.addView(eventStat(R.drawable.official_comment_like_line, item.likes));
        LinearLayout.LayoutParams commentParams =
                new LinearLayout.LayoutParams(-2, dp(22));
        commentParams.leftMargin = dp(13);
        stats.addView(eventStat(
                R.drawable.official_detail_comment, item.comments), commentParams);
        addTop(copy, stats, 6);

        if (!this.session.noImage() && !TextUtils.isEmpty(item.image)) {
            DisplayMetrics metrics = this.activity.getResources().getDisplayMetrics();
            boolean narrow = metrics.widthPixels
                    / Math.max(1.0f, metrics.density) < 300.0f;
            int imageWidth = dp(narrow ? 76 : 94);
            int imageHeight = dp(narrow ? 58 : 68);
            FrameLayout imageFrame = new FrameLayout(this.activity);
            ImageView image = new ImageView(this.activity);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Compat.setBackground(image, round(this.session.darkMode()
                    ? Color.rgb(42, 44, 48) : Color.rgb(232, 234, 236), 5));
            Compat.clipToOutline(image);
            imageFrame.addView(image, new FrameLayout.LayoutParams(-1, -1));
            if (item.images.length > 1) {
                TextView count = text(item.images.length + " 图",
                        8.5f, Color.WHITE);
                count.setGravity(Gravity.CENTER);
                count.setPadding(dp(5), 0, dp(5), 0);
                Compat.setBackground(count, round(
                        Color.argb(180, 20, 21, 23), 3));
                FrameLayout.LayoutParams countParams =
                        new FrameLayout.LayoutParams(-2, dp(18),
                                Gravity.BOTTOM | Gravity.RIGHT);
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
        card.setOnClickListener(view -> {
            UiComponents.press(view);
            this.host.openPost(item);
        });
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);
        params.leftMargin = dp(2);
        params.rightMargin = dp(2);
        params.bottomMargin = dp(7);
        card.setLayoutParams(params);
        return card;
    }

    private TextView eventStat(int icon, int value) {
        TextView stat = text(Format.commentLikeCount(Math.max(0, value)),
                9.5f, this.tokens.muted);
        stat.setGravity(Gravity.CENTER);
        Drawable drawable = Compat.tintedDrawable(
                this.activity, icon, this.tokens.muted);
        if (drawable != null) {
            drawable.setBounds(0, 0, dp(13), dp(13));
            stat.setCompoundDrawables(drawable, null, null, null);
            stat.setCompoundDrawablePadding(dp(3));
        }
        return stat;
    }

    private void addLevelBadge(LinearLayout row, JSONObject user) {
        int level = CommentData.userLevel(user);
        if (level <= 0) return;
        int levelColor = CommentData.levelBadgeColor(level);
        TextView badge = text("Lv." + level, 8.0f, levelColor);
        badge.setGravity(Gravity.CENTER);
        badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        badge.setPadding(dp(4), 0, dp(4), 0);
        Compat.setBackground(badge, round(ThemeTokens.blend(
                this.tokens.panel, levelColor,
                this.session.darkMode() ? 0.30f : 0.14f), 4));
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-2, dp(15));
        params.leftMargin = dp(4);
        params.rightMargin = dp(4);
        row.addView(badge, params);
    }

    private TextView text(String value, float size, int color) {
        TextView view = UiComponents.label(
                this.activity, value, size, color, this.textScale);
        view.setTypeface(Typeface.DEFAULT);
        return view;
    }

    private LinearLayout vertical(int color) {
        LinearLayout layout = new LinearLayout(this.activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(color);
        return layout;
    }

    private void addTop(LinearLayout parent, View view, int margin) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(margin);
        parent.addView(view, params);
    }

    private Drawable round(int color, int radius) {
        return UiComponents.round(this.activity, color, radius, this.uiScale);
    }

    private int dp(int value) {
        return Math.round(value * this.activity.getResources()
                .getDisplayMetrics().density * this.uiScale);
    }
}
