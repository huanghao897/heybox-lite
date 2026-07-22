package com.ronan.heyboxlite;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

final class UiComponents {
    private UiComponents() {}

    static GradientDrawable card(Context context, ThemeTokens tokens, float scale) {
        return round(context, tokens.surfaceContainer, 9, scale);
    }

    static GradientDrawable elevatedCard(Context context, ThemeTokens tokens, float scale) {
        return round(context, tokens.surfaceContainerHigh, 11, scale);
    }

    static GradientDrawable outlinedCard(Context context, ThemeTokens tokens, float scale) {
        GradientDrawable drawable = round(context, tokens.surface, 10, scale);
        drawable.setStroke(Math.max(1, dp(context, 1, scale)), tokens.outlineVariant);
        return drawable;
    }

    static GradientDrawable softPill(Context context, ThemeTokens tokens, float scale) {
        return round(context, tokens.primaryContainer, 18, scale);
    }

    static GradientDrawable monoChip(Context context, ThemeTokens tokens, float scale) {
        return round(context, tokens.surfaceContainerHighest, 9, scale);
    }

    static GradientDrawable groupCard(Context context, ThemeTokens tokens, float scale) {
        return round(context, tokens.surfaceContainerLow, 9, scale);
    }

    static GradientDrawable dock(Context context, ThemeTokens tokens, float scale) {
        int alpha = tokens.dark ? 244 : 248;
        int fill = Color.argb(alpha, Color.red(tokens.surfaceContainerHigh),
                Color.green(tokens.surfaceContainerHigh), Color.blue(tokens.surfaceContainerHigh));
        return round(context, fill, 24, scale);
    }

    static GradientDrawable primaryButton(Context context, ThemeTokens tokens, float scale) {
        return round(context, tokens.primary, 20, scale);
    }

    static GradientDrawable tonalButton(Context context, ThemeTokens tokens, float scale) {
        return round(context, tokens.primaryContainer, 20, scale);
    }

    static GradientDrawable ghostButton(Context context, ThemeTokens tokens, float scale) {
        return round(context, tokens.surfaceContainerHigh, 20, scale);
    }

    static GradientDrawable textField(Context context, ThemeTokens tokens, float scale) {
        return round(context, tokens.surfaceContainerHighest, 12, scale);
    }

    static Drawable selectableCard(Context context, ThemeTokens tokens, float scale) {
        return selectable(context, tokens.surfaceContainer,
                tokens.pressedSurface(), tokens.primary, 9, scale);
    }

    static Drawable selectableRow(Context context, ThemeTokens tokens, float scale) {
        return selectable(context, Color.TRANSPARENT,
                ThemeTokens.blend(tokens.surfaceContainerLow, tokens.text,
                        tokens.dark ? 0.10f : 0.05f), tokens.primary, 10, scale);
    }

    static Drawable selectable(Context context, int normal, int pressed, int ripple,
                               int radius, float scale) {
        GradientDrawable content = round(context, normal, radius, scale);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new RippleDrawable(ColorStateList.valueOf(withAlpha(ripple, 42)), content, null);
        }
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},
                round(context, pressed, radius, scale));
        states.addState(new int[0], content);
        return states;
    }

    static GradientDrawable round(Context context, int color, int radius, float scale) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radius, scale));
        return drawable;
    }

    static void elevate(View view, int dp) {
        if (view != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setElevation(dp(view.getContext(), dp, 1.0f));
        }
    }

    static void installPressFeedback(View view) {
        if (view == null) return;
        view.setOnTouchListener((target, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                pressIn(target);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                pressOut(target);
            }
            return false;
        });
    }

    static void press(View view) {
        if (view == null || Motions.off()
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) return;
        view.animate().cancel();
        view.animate().scaleX(0.965f).scaleY(0.965f)
                .setDuration(MotionSpec.PRESS_IN_MS)
                .setInterpolator(MotionSpec.STANDARD)
                .withEndAction(() -> pressOut(view))
                .start();
    }

    private static void pressIn(View view) {
        if (Motions.off() || Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) return;
        view.animate().cancel();
        view.animate().scaleX(0.975f).scaleY(0.975f)
                .setDuration(MotionSpec.PRESS_IN_MS)
                .setInterpolator(MotionSpec.STANDARD)
                .start();
    }

    private static void pressOut(View view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) return;
        view.animate().cancel();
        view.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(MotionSpec.PRESS_OUT_MS)
                .setInterpolator(MotionSpec.EMPHASIZED_DECELERATE)
                .start();
    }

    static TextView label(Context context, String value, float sp, int color, float textScale) {
        TextView view = new TextView(context);
        view.setText(value == null ? "" : value);
        view.setTextSize(sp * textScale);
        view.setTextColor(color);
        Compat.setLetterSpacing(view, 0);
        return view;
    }

    static int dp(Context context, int value, float scale) {
        return Math.round(value * context.getResources().getDisplayMetrics().density * scale);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
