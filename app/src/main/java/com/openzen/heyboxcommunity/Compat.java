package com.openzen.heyboxcommunity;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

final class Compat {
    private Compat() {}

    @SuppressWarnings("deprecation")
    static void setBackground(View view, Drawable drawable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            view.setBackground(drawable);
        } else {
            view.setBackgroundDrawable(drawable);
        }
    }

    static void clipToOutline(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setClipToOutline(true);
        }
    }

    @SuppressWarnings("deprecation")
    static Drawable tintedDrawable(Context context, int resource, int color) {
        Drawable drawable = context.getResources().getDrawable(resource);
        if (drawable == null) return null;
        drawable = drawable.mutate();
        drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        return drawable;
    }

    static void tintDrawable(Drawable drawable, int color) {
        if (drawable != null) drawable.mutate().setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    static void setLetterSpacing(TextView view, float spacing) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setLetterSpacing(spacing);
        }
    }

    static void colorSystemBars(Window window, int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(color);
            window.setNavigationBarColor(color);
        }
    }

    static int fullscreenFlags() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) return 0;
        int flags = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        return flags;
    }

    static void tint(CompoundButton button, int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setButtonTintList(ColorStateList.valueOf(color));
        }
    }

    static void tint(EditText input, int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            input.setBackgroundTintList(ColorStateList.valueOf(color));
        }
    }

    static void tint(ProgressBar progress, int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            progress.setIndeterminateTintList(ColorStateList.valueOf(color));
        }
    }

    static void tint(SeekBar seekBar, int color) {
        tint(seekBar, color, color);
    }

    static void tint(SeekBar seekBar, int trackColor, int thumbColor) {
        if (seekBar.getProgressDrawable() != null) {
            seekBar.getProgressDrawable().mutate()
                    .setColorFilter(trackColor, PorterDuff.Mode.SRC_IN);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN
                && seekBar.getThumb() != null) {
            seekBar.getThumb().mutate().setColorFilter(thumbColor, PorterDuff.Mode.SRC_IN);
        }
    }
}
