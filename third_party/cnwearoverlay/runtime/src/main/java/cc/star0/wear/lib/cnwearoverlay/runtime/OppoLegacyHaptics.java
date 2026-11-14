package cc.star0.wear.lib.cnwearoverlay.runtime;

import android.annotation.TargetApi;
import android.content.Context;
import android.provider.Settings;
import android.view.View;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** OPPO 传统组件：将系统走通用波形 300 的常量 12 改为原生 ScrollView 的 302 / strength 2。 */
@TargetApi(19)
final class OppoLegacyHaptics extends BaseOverlay {
    private static final String VIBRATOR_CLASS =
            "android.os.linearmotorvibrator.LinearmotorVibrator";
    private static final String SCROLL_FEEDBACK_CLASS = "android.view.HapticScrollFeedbackProvider";
    private static final int SCROLL_FEEDBACK = 12;
    private static final int SCROLL_EFFECT = 302;
    private static final int IGNORE_VIEW_SETTING = 1;
    private static final int IGNORE_GLOBAL_SETTING = 2;

    OppoLegacyHaptics() {
        super(SCROLL_FEEDBACK, SCROLL_FEEDBACK, SCROLL_FEEDBACK);
    }

    static boolean isAvailable() {
        return hasSystemClass(VIBRATOR_CLASS) && !hasSystemClass(SCROLL_FEEDBACK_CLASS);
    }

    @Override
    boolean isOppo() {
        return true;
    }

    @Override
    boolean performRotaryHapticFeedback(View view, int feedbackConstant) {
        return feedbackConstant == SCROLL_FEEDBACK
                ? perform(view, 0) : super.performRotaryHapticFeedback(view, feedbackConstant);
    }

    @Override
    boolean performRotaryHapticFeedback(View view, int feedbackConstant, int flags) {
        return feedbackConstant == SCROLL_FEEDBACK
                ? perform(view, flags) : super.performRotaryHapticFeedback(view, feedbackConstant, flags);
    }

    static boolean perform(View view, int flags) {
        if (OverlayLog.enabled()) {
            OverlayLog.debug("haptic legacy request constant=12 effect=302 strength=2 flags=" + flags);
        }
        // 直接调用马达服务绕过了 View / WindowManager，需在这里保留它们的触觉开关语义。
        if (!view.isAttachedToWindow()) {
            OverlayLog.debug("haptic legacy skipped: view detached");
            return false;
        }
        if (!view.isHapticFeedbackEnabled() && (flags & IGNORE_VIEW_SETTING) == 0) {
            OverlayLog.debug("haptic legacy skipped: view haptics disabled");
            return false;
        }
        String stage = "settings";
        try {
            Context context = view.getContext();
            if ((flags & IGNORE_GLOBAL_SETTING) == 0
                    && Settings.System.getInt(context.getContentResolver(),
                            "haptic_feedback_enabled", 0) == 0) {
                OverlayLog.debug("haptic legacy skipped: system haptics disabled or setting absent");
                return false;
            }
            stage = "resolve_api";
            Api api = Api.INSTANCE;
            if (api == null) {
                OverlayLog.debug("haptic legacy skipped: reflection API unavailable (cached)");
                return false;
            }
            stage = "service";
            Object vibrator = context.getSystemService("linearmotor");
            if (vibrator == null) {
                OverlayLog.debug("haptic legacy skipped: linearmotor service unavailable");
                return false;
            }
            stage = "has_motor";
            if (!Boolean.TRUE.equals(api.hasVibrator.invoke(vibrator))) {
                OverlayLog.debug("haptic legacy skipped: hasLinearMotorVibrator=false");
                return false;
            }
            stage = "build_effect";
            Object builder = api.builder.newInstance();
            api.setEffectType.invoke(builder, SCROLL_EFFECT);
            api.setEffectStrength.invoke(builder, 2);
            api.setEffectLoop.invoke(builder, false);
            api.setAsynchronous.invoke(builder, false);
            Object effect = api.build.invoke(builder);
            // 不支持高压马达的固件可能把 302 改成 6；仅允许请求表冠专用波形。
            Object effectType = api.getEffectType.invoke(effect);
            if (!Integer.valueOf(SCROLL_EFFECT).equals(effectType)) {
                if (OverlayLog.enabled()) {
                    OverlayLog.debug("haptic legacy skipped: effect changed to " + effectType);
                }
                return false;
            }
            stage = "vibrate";
            api.vibrate.invoke(vibrator, effect);
            // vendor 客户端返回 void；这里只表示调用已返回，不能证明马达实际执行。
            OverlayLog.debug("haptic legacy invoked effect=302 strength=2; physical vibration unconfirmed");
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            OverlayLog.failure("haptic legacy failed stage=" + stage, e);
            // 隐藏 API、服务或权限不可用时结束；不尝试 VIRTUAL_KEY(1) 或其他震动。
            return false;
        }
    }

    /** 只缓存系统反射入口，不持有 View、Context 或服务实例。 */
    private static final class Api {
        static final Api INSTANCE = create();

        final Constructor<?> builder;
        final Method hasVibrator;
        final Method setEffectType;
        final Method setEffectStrength;
        final Method setEffectLoop;
        final Method setAsynchronous;
        final Method build;
        final Method getEffectType;
        final Method vibrate;

        private Api() throws ReflectiveOperationException {
            ClassLoader boot = View.class.getClassLoader();
            Class<?> vibrator = Class.forName(VIBRATOR_CLASS, false, boot);
            Class<?> effect = Class.forName(
                    "android.os.linearmotorvibrator.WaveformEffect", false, boot);
            Class<?> builderClass = Class.forName(
                    "android.os.linearmotorvibrator.WaveformEffect$Builder", false, boot);
            builder = builderClass.getConstructor();
            hasVibrator = vibrator.getMethod("hasLinearMotorVibrator");
            setEffectType = builderClass.getMethod("setEffectType", int.class);
            setEffectStrength = builderClass.getMethod("setEffectStrength", int.class);
            setEffectLoop = builderClass.getMethod("setEffectLoop", boolean.class);
            setAsynchronous = builderClass.getMethod("setAsynchronous", boolean.class);
            build = builderClass.getMethod("build");
            getEffectType = effect.getMethod("getEffectType");
            vibrate = vibrator.getMethod("vibrate", effect);
        }

        private static Api create() {
            try {
                Api api = new Api();
                OverlayLog.debug("haptic legacy reflection API ready");
                return api;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                OverlayLog.failure("haptic legacy reflection API init failed", e);
                return null;
            }
        }
    }
}
