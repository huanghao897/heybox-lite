package com.ronan.heyboxlite;

import android.widget.LinearLayout;

final class VideoSettingsPage {
    interface Host {
        LinearLayout openPage(String key, String title);
    }

    private final SessionStore session;
    private final SettingsUi settingsUi;
    private final Host host;

    VideoSettingsPage(SessionStore session, SettingsUi settingsUi, Host host) {
        this.session = session;
        this.settingsUi = settingsUi;
        this.host = host;
    }

    void show() {
        LinearLayout page = this.host.openPage("video_settings", "视频播放");

        this.settingsUi.addSection(page, "基础播放");
        LinearLayout playback = this.settingsUi.list();
        addTop(playback, this.settingsUi.toggle("自动播放", null, null,
                this.session.videoAutoplay(), this.session::setVideoAutoplay));
        addTop(playback, this.settingsUi.toggle("循环播放", null, null,
                this.session.videoLoop(), this.session::setVideoLoop));
        addTop(playback, this.settingsUi.toggle("默认静音", null, null,
                this.session.videoMuted(), this.session::setVideoMuted));
        page.addView(playback);

        this.settingsUi.addSection(page, "手势操作");
        LinearLayout gestures = this.settingsUi.list();
        addTop(gestures, this.settingsUi.toggle("长按 2 倍速快进", null, null,
                this.session.videoLongPressFastForward(),
                this.session::setVideoLongPressFastForward));
        page.addView(gestures);

        this.settingsUi.addSection(page, "画面显示");
        LinearLayout display = this.settingsUi.list();
        this.settingsUi.addChoiceEntry(display, "视频显示方式", R.drawable.il_zoom,
                new String[]{"适应屏幕", "裁切填充"}, this.session.videoDisplayMode(),
                this.session::setVideoDisplayMode, null);
        page.addView(display);

        this.settingsUi.addSection(page, "播放失败");
        LinearLayout recovery = this.settingsUi.list();
        addTop(recovery, this.settingsUi.toggle("失败时使用凉腕播放器", null, null,
                this.session.videoExternalFallback(), this.session::setVideoExternalFallback));
        page.addView(recovery);
    }

    private void addTop(LinearLayout parent, LinearLayout child) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        parent.addView(child, params);
    }
}
