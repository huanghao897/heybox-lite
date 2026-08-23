package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class DetailContentRenderer {
    interface ImageOpener {
        void open(ImageView source, String[] urls, int index);
    }

    private final Activity activity;
    private final SessionStore session;
    private final ThemeTokens tokens;
    private final boolean roundLayout;
    private final float uiScale;
    private final float textScale;
    private final ImageOpener imageOpener;
    private final DetailContentParser parser = new DetailContentParser();

    DetailContentRenderer(Activity activity, SessionStore session,
                          ThemeTokens tokens, boolean roundLayout,
                          ImageOpener imageOpener) {
        this.activity = activity;
        this.session = session;
        this.tokens = tokens;
        this.roundLayout = roundLayout;
        this.uiScale = session.uiScale() / 100.0f;
        this.textScale = session.textScale() / 100.0f;
        this.imageOpener = imageOpener;
    }

    DetailContentParser.Result resolve(JSONObject link, String fallback,
                                       JSONArray fallbackImages) {
        return this.parser.resolve(link, fallback, fallbackImages);
    }

    void add(LinearLayout parent, DetailContentParser.Result content) {
        addBlocks(parent, content.blocks, content.useImagePager);
    }

    int imageTargetPx() {
        int width = Math.max(320,
                this.activity.getResources().getDisplayMetrics().widthPixels);
        return Math.max(360, Math.min(this.roundLayout ? 720 : 900, width));
    }

    private void addBlocks(LinearLayout parent, List<RichContent.Block> blocks,
                           boolean usePager) {
        if (blocks.isEmpty()) {
            addTop(parent, text("正文为空", 13.0f, this.tokens.muted), 9);
            return;
        }
        int imageCount = 0;
        boolean bodyStarted = false;
        boolean lastWasImage = false;
        int index = 0;
        while (index < blocks.size()) {
            RichContent.Block block = blocks.get(index);
            if (block.image) {
                List<String> images = new ArrayList<>();
                while (index < blocks.size() && blocks.get(index).image) {
                    if (!this.session.noImage() && imageCount < 48) {
                        images.add(blocks.get(index).value);
                        imageCount++;
                    }
                    index++;
                }
                lastWasImage = false;
                if (usePager && images.size() >= 2) {
                    addImagePager(parent, images);
                } else {
                    for (String url : images) {
                        View image = imageBlock(url, imageTargetPx(), 150);
                        LinearLayout.LayoutParams params =
                                new LinearLayout.LayoutParams(-1, -2);
                        params.topMargin = dp(10);
                        parent.addView(image, params);
                        lastWasImage = true;
                    }
                }
                continue;
            }
            index++;
            if (block.kind == RichContent.Block.HEADING) {
                addHeading(parent, block.value, bodyStarted);
                bodyStarted = true;
                lastWasImage = false;
            } else if (block.kind == RichContent.Block.QUOTE) {
                addQuote(parent, block.value, bodyStarted);
                bodyStarted = true;
                lastWasImage = false;
            } else if (block.kind == RichContent.Block.CAPTION && lastWasImage) {
                addCaption(parent, block.value);
                lastWasImage = false;
            } else {
                bodyStarted = addParagraphs(parent, block.value, bodyStarted);
                lastWasImage = false;
            }
        }
    }

    private void addHeading(LinearLayout parent, String source,
                            boolean bodyStarted) {
        String value = ArticleText.stripMarkdownEmphasis(source);
        float size = this.roundLayout ? 15.0f : 16.0f;
        TextView heading = text(value,
                size * this.session.bodyTextScale() / 100.0f, this.tokens.text);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setLineSpacing(dp(1), 1.16f);
        Compat.setLetterSpacing(heading,
                this.session.bodyLetterSpacing() / 200.0f);
        heading.setTextIsSelectable(true);
        EmojiRenderer.set(heading, value, this.session.darkMode());
        addTop(parent, heading, bodyStarted
                ? Math.max(14, this.session.bodyParagraphSpacing() + 10) : 14);
    }

    private void addCaption(LinearLayout parent, String source) {
        String value = ArticleText.stripMarkdownEmphasis(source);
        TextView caption = text(value,
                11.0f * this.session.bodyTextScale() / 100.0f,
                this.tokens.muted);
        caption.setGravity(Gravity.CENTER);
        caption.setLineSpacing(dp(1), 1.3f);
        caption.setTextIsSelectable(true);
        EmojiRenderer.set(caption, value, this.session.darkMode());
        addTop(parent, caption, 6);
    }

    private boolean addParagraphs(LinearLayout parent, String source,
                                  boolean bodyStarted) {
        for (String paragraph : ArticleText.articleParagraphs(source)) {
            if (ArticleText.isArticleQuote(paragraph)) {
                addQuote(parent, paragraph, bodyStarted);
            } else if (ArticleText.isInlineHeading(paragraph)) {
                addHeading(parent, paragraph, bodyStarted);
            } else {
                float size = this.roundLayout ? 13.0f : 14.5f;
                TextView body = text(paragraph,
                        size * this.session.bodyTextScale() / 100.0f,
                        this.tokens.text);
                body.setLineSpacing(dp(1), Math.max(1.18f,
                        this.session.bodyLineSpacing() / 100.0f));
                Compat.setLetterSpacing(body,
                        this.session.bodyLetterSpacing() / 200.0f);
                body.setTypeface(Typeface.DEFAULT);
                body.setTextIsSelectable(true);
                EmojiRenderer.set(body, paragraph, this.session.darkMode());
                addTop(parent, body, bodyStarted
                        ? Math.max(8, this.session.bodyParagraphSpacing() + 6) : 14);
            }
            bodyStarted = true;
        }
        return bodyStarted;
    }

    private void addQuote(LinearLayout parent, String paragraph,
                          boolean bodyStarted) {
        String value = ArticleText.stripMarkdownEmphasis(paragraph).trim();
        while (value.startsWith(">")) value = value.substring(1).trim();
        LinearLayout quote = new LinearLayout(this.activity);
        quote.setGravity(Gravity.CENTER_VERTICAL);
        quote.setPadding(0, dp(2), 0, dp(2));
        View bar = new View(this.activity);
        int barColor = ThemeTokens.blend(this.tokens.secondary,
                this.tokens.background, this.session.darkMode() ? 0.38f : 0.62f);
        Compat.setBackground(bar, UiComponents.round(
                this.activity, barColor, 2, this.uiScale));
        bar.setMinimumHeight(dp(38));
        quote.addView(bar, new LinearLayout.LayoutParams(dp(4), -1));
        float size = this.roundLayout ? 12.5f : 13.5f;
        TextView copy = text("",
                size * this.session.bodyTextScale() / 100.0f,
                this.tokens.muted);
        copy.setLineSpacing(dp(1), Math.max(1.14f,
                this.session.bodyLineSpacing() / 100.0f));
        Compat.setLetterSpacing(copy,
                this.session.bodyLetterSpacing() / 220.0f);
        EmojiRenderer.set(copy, value, this.session.darkMode());
        copy.setPadding(dp(10), 0, 0, 0);
        quote.addView(copy, new LinearLayout.LayoutParams(0, -2, 1.0f));
        addTop(parent, quote, bodyStarted ? 10 : 14);
    }

    private void addImagePager(LinearLayout parent, List<String> urls) {
        new DetailImageGallery(this.activity, this.session, this.tokens,
                this.roundLayout, imageTargetPx(), this.imageOpener::open)
                .addTo(parent, urls);
    }

    private View imageBlock(String url, int targetPx, int heightDp) {
        PostImageFrame frame = new PostImageFrame(
                this.activity, this::dp, heightDp);
        Compat.setBackground(frame, UiComponents.round(this.activity,
                placeholderColor(), 7, this.uiScale));
        Compat.clipToOutline(frame);
        ImageView image = new GifImageView(this.activity);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(false);
        frame.addView(image, match());
        image.setOnClickListener(view ->
                this.imageOpener.open(image, new String[]{url}, 0));
        ImageLoader.intoMeasuredRevealStable(image, url, targetPx,
                (success, bitmap) -> {
                    if (success && bitmap != null) {
                        frame.setImageSize(bitmap.getWidth(), bitmap.getHeight());
                        if (this.session.playGif() && GifSupport.isGifUrl(url)) {
                            ImageLoader.intoGif(image, url);
                        }
                    } else if (image.getDrawable() == null) {
                        TextView failed = text("图片加载失败",
                                11.0f, this.tokens.muted);
                        failed.setGravity(Gravity.CENTER);
                        frame.addView(failed, match());
                    }
                });
        return frame;
    }

    private int placeholderColor() {
        return this.session.darkMode()
                ? Color.rgb(28, 30, 32) : Color.rgb(235, 237, 240);
    }

    private TextView text(String value, float size, int color) {
        TextView view = UiComponents.label(
                this.activity, value, size, color, this.textScale);
        view.setTypeface(Typeface.DEFAULT);
        return view;
    }

    private void addTop(LinearLayout parent, View view, int margin) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(margin);
        parent.addView(view, params);
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

    private int dp(int value) {
        return Math.round(value * this.activity.getResources()
                .getDisplayMetrics().density * this.uiScale);
    }
}
