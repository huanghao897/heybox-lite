package com.ronan.heyboxlite;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.content.ActivityNotFoundException;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

final class VideoPlayerLauncher {
    private static final String PLAYER_PACKAGE = "com.aliangmaker.media";
    private static final String PLAYER_ACTIVITY = "com.aliangmaker.media.PlayVideoActivity";
    private static final String REFERER = "http://api.maxjia.com/";
    private static final String USER_AGENT = "Mozilla/5.0 AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/41.0.2272.118 Safari/537.36 ApiMaxJia/1.0";

    private VideoPlayerLauncher() {
    }

    static boolean open(Context context, VideoData video) {
        if (context == null || video == null || !video.playable()) return false;
        Intent intent = new Intent(context, VideoPlayerActivity.class);
        intent.putExtra(VideoPlayerActivity.EXTRA_URL, video.url);
        intent.putExtra(VideoPlayerActivity.EXTRA_COVER, video.cover);
        intent.putExtra(VideoPlayerActivity.EXTRA_TITLE, video.title);
        context.startActivity(intent);
        return true;
    }

    static boolean openExternal(Context context, VideoData video) {
        if (context == null || video == null || !video.playable()) return false;
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClassName(PLAYER_PACKAGE, PLAYER_ACTIVITY);
        intent.setData(Uri.parse(video.url));
        intent.putExtra("name", video.title);
        intent.putExtra("danmaku", "");
        intent.putExtra("live_mode", false);
        intent.putExtra("progress", 0L);
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", REFERER);
        intent.putExtra("cookie", (Serializable) headers);
        intent.putExtra("agent", USER_AGENT);
        PackageManager packageManager = context.getPackageManager();
        if (intent.resolveActivity(packageManager) == null) return false;
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException ignored) {
            return false;
        }
    }

    static boolean canOpenExternal(Context context, VideoData video) {
        if (context == null || video == null || !video.playable()) return false;
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClassName(PLAYER_PACKAGE, PLAYER_ACTIVITY);
        return intent.resolveActivity(context.getPackageManager()) != null;
    }
}
