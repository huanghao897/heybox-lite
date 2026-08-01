package com.ronan.heyboxlite;

import android.app.Activity;
import android.os.Build;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class DiagnosticsController {
    interface Host {
        RuntimeState runtimeState();

        void showToast(String message);
    }

    static final class RuntimeState {
        final String currentLinkId;
        final String currentLinkHsrc;
        final String screen;
        final int feedCount;
        final boolean feedCursorPresent;
        final int feedLastPull;
        final boolean feedNoMore;
        final String detailDiagnostics;

        RuntimeState(String currentLinkId, String currentLinkHsrc, String screen,
                     int feedCount, boolean feedCursorPresent, int feedLastPull,
                     boolean feedNoMore, String detailDiagnostics) {
            this.currentLinkId = currentLinkId;
            this.currentLinkHsrc = currentLinkHsrc;
            this.screen = screen;
            this.feedCount = feedCount;
            this.feedCursorPresent = feedCursorPresent;
            this.feedLastPull = feedLastPull;
            this.feedNoMore = feedNoMore;
            this.detailDiagnostics = detailDiagnostics;
        }
    }

    private final Activity activity;
    private final SessionStore session;
    private final LocalCache localCache;
    private final LiteDialogPresenter dialogs;
    private final Host host;
    private boolean uploadInFlight;

    DiagnosticsController(Activity activity, SessionStore session,
                          LocalCache localCache, LiteDialogPresenter dialogs,
                          Host host) {
        this.activity = activity;
        this.session = session;
        this.localCache = localCache;
        this.dialogs = dialogs;
        this.host = host;
    }

    void upload() {
        if (this.uploadInFlight) {
            this.host.showToast("日志正在上传");
            return;
        }
        this.uploadInFlight = true;
        this.host.showToast("正在上传日志");
        DiagnosticsClient.upload(this.session, build(), (uploaded, message) -> {
            this.uploadInFlight = false;
            this.host.showToast(message);
        });
    }

    void showPendingCrash() {
        String crash = CrashReporter.pendingCrashReport(this.activity);
        if (crash.isEmpty() || this.activity.isFinishing()) return;
        String report = DiagnosticsClient.crashReport(this.activity, this.session, crash);
        File file = this.localCache.writeDiagnostics(report);
        String details = DiagnosticSanitizer.redact(crash);
        if (details.length() > 1_400) details = details.substring(0, 1_400) + "\n...";
        CrashReporter.markHandled(this.activity, crash);
        this.dialogs.show("上次运行出现异常",
                "应用已经保存故障信息。日志不会自动上传。\n\n" + details,
                "上传日志", () -> DiagnosticsClient.upload(
                        this.session, report,
                        (uploaded, message) -> this.host.showToast(message)),
                "知道了", null,
                "保存日志", () -> save(file.getName(), report));
    }

    void export() {
        String diagnostics = build();
        File file = this.localCache.writeDiagnostics(diagnostics);
        this.dialogs.show("导出诊断日志",
                "可以分享给其他应用，也可以保存到 Download/heyboxlite",
                "本地", () -> save(file.getName(), diagnostics),
                "取消", null, "分享", () -> share(file));
    }

    String build() {
        RuntimeState state = this.host.runtimeState();
        StringBuilder out = new StringBuilder();
        out.append("heybox Lite diagnostics\n");
        out.append("exportTimeLocal: ").append(time(System.currentTimeMillis())).append('\n');
        out.append("exportTimeMillis: ").append(System.currentTimeMillis()).append('\n');
        out.append("timeZone: ").append(TimeZone.getDefault().getID()).append('\n');
        out.append("sessionId: ").append(this.localCache.sessionId()).append('\n');
        out.append("sessionStartedLocal: ")
                .append(time(this.localCache.sessionStartedAt())).append('\n');
        out.append("sessionStartedMillis: ")
                .append(this.localCache.sessionStartedAt()).append('\n');
        out.append("version: ").append(BuildConfig.VERSION_NAME).append('\n');
        out.append("currentLinkId: ").append(state.currentLinkId).append('\n');
        out.append("currentLinkHsrc: ").append(state.currentLinkHsrc).append('\n');
        out.append("android: ").append(Build.VERSION.RELEASE)
                .append(" api ").append(Build.VERSION.SDK_INT).append('\n');
        out.append("device: ").append(Build.MANUFACTURER)
                .append(' ').append(Build.MODEL).append('\n');
        out.append("screen: ").append(state.screen).append('\n');
        out.append("loggedIn: ").append(this.session.isLoggedIn()).append('\n');
        out.append("feedCount: ").append(state.feedCount).append('\n');
        out.append("feedLastvalPresent: ").append(state.feedCursorPresent).append('\n');
        out.append("feedLastPull: ").append(state.feedLastPull).append('\n');
        out.append("feedNoMore: ").append(state.feedNoMore).append('\n');
        out.append("offlineFeedSavedAt: ").append(this.localCache.feedSavedAt()).append('\n');
        out.append("offlineBytes: ").append(this.localCache.offlineBytes()).append('\n');
        out.append("cachedDetails: ").append(this.localCache.detailCount()).append('\n');
        out.append("imageMemoryCacheKb: ").append(ImageLoader.cacheSizeKb()).append('\n');
        out.append("emojiMemoryCacheKb: ").append(EmojiRenderer.cacheSizeKb()).append('\n');
        out.append("noImage: ").append(this.session.noImage()).append('\n');
        out.append("darkMode: ").append(this.session.darkMode()).append('\n');
        out.append("keywords: ").append(this.session.blockKeywords()).append('\n');
        appendSection(out, "last detail diagnostics", state.detailDiagnostics,
                "last detail diagnostics: none captured\n");
        appendSection(out, "last crash", this.localCache.crashLog(), null);
        appendSection(out, "previous crash", this.localCache.previousCrashLog(), null);
        appendSection(out, "native signer events", this.localCache.nativeSignLog(), null);
        appendSection(out, "previous session events", this.localCache.previousLog(), null);
        out.append("\nrecent events:\n").append(this.localCache.recentLog());
        return out.toString();
    }

    private void appendSection(StringBuilder out, String title,
                               String content, String emptyText) {
        if (content == null || content.trim().isEmpty()) {
            if (emptyText != null) out.append('\n').append(emptyText);
            return;
        }
        out.append('\n').append(title).append(":\n").append(content);
        if (!content.endsWith("\n")) out.append('\n');
    }

    private void share(File file) {
        if (!DiagnosticsExporter.share(this.activity, file)) {
            this.host.showToast("没有可用的分享应用，日志已保存："
                    + file.getAbsolutePath());
        }
    }

    private void save(String fileName, String diagnostics) {
        String path = DiagnosticsExporter.save(this.activity, fileName, diagnostics);
        this.host.showToast(path == null ? "保存失败" : "已保存到 " + path);
    }

    private String time(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new Date(millis));
    }
}
