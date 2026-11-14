package cc.star0.wear.lib.cnwearoverlay.runtime;

import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

/**
 * Gradle 插桩、Google Wear 桩类和 Xposed 共用的稳定入口。
 * 基础行为由 BaseOverlay 提供，平台差异分别由小米、OPPO 现代与传统组件处理。
 */
public final class CnWearOverlay {
    private static final String GOOGLE_WEAR_CONSTANTS_CLASS =
            "com.google.wear.input.WearHapticFeedbackConstants";

    private static volatile BaseOverlay activeComponent;

    private CnWearOverlay() {}

    public static boolean isXiaomi() {
        return component() instanceof XiaomiHaptics;
    }

    public static boolean isOppo() {
        return component().isOppo();
    }

    public static boolean isOppoModern() {
        return component() instanceof OppoModernHaptics;
    }

    public static boolean isOppoLegacy() {
        return component() instanceof OppoLegacyHaptics;
    }

    public static boolean isActive() {
        return component().isActive();
    }

    private static BaseOverlay component() {
        BaseOverlay component = activeComponent;
        if (component != null) {
            return component;
        }
        synchronized (CnWearOverlay.class) {
            if (activeComponent == null) {
                activeComponent = detectComponent();
            }
            return activeComponent;
        }
    }

    private static BaseOverlay detectComponent() {
        // 系统 Google Wear SDK 优先；应用中注入的桩类不会命中 boot classpath 探测。
        if (BaseOverlay.hasSystemClass(GOOGLE_WEAR_CONSTANTS_CLASS)) {
            OverlayLog.debug("platform=GOOGLE_WEAR active=false");
            return new BaseOverlay();
        }
        if (XiaomiHaptics.isAvailable()) {
            OverlayLog.debug("platform=XIAOMI active=true");
            return new XiaomiHaptics();
        }
        if (OppoModernHaptics.isAvailable()) {
            OverlayLog.debug("platform=OPPO_MODERN active=true");
            return new OppoModernHaptics();
        }
        if (OppoLegacyHaptics.isAvailable()) {
            OverlayLog.debug("platform=OPPO_LEGACY active=true");
            return new OppoLegacyHaptics();
        }
        OverlayLog.debug("platform=UNKNOWN active=false");
        return new BaseOverlay();
    }

    /** 目标设备上强制走 WearSDK 常量路径（配合注入的桩类）。 */
    public static boolean forceWearSdk() {
        boolean force = isActive();
        if (OverlayLog.enabled()) {
            OverlayLog.debug("forceWearSdk=" + force);
        }
        return force;
    }

    public static int getScrollItemFocus() {
        return component().scrollItemFocus;
    }

    public static int getScrollTick() {
        return component().scrollTick;
    }

    public static int getScrollLimit() {
        return component().scrollLimit;
    }

    /**
     * @param hapticConstantsClass 由插桩代码传入的 Compose HapticConstants 类，兼容 R8 改名
     * @return 独立的平台常量实例；非目标设备返回 null，回落原逻辑
     */
    public static Object rotaryConstants(Class<?> hapticConstantsClass) {
        return component().rotaryConstants(hapticConstantsClass);
    }

    public static boolean performHapticFeedback(View view, int feedbackConstant) {
        return component().performHapticFeedback(view, feedbackConstant);
    }

    public static boolean performHapticFeedback(View view, int feedbackConstant, int flags) {
        return component().performHapticFeedback(view, feedbackConstant, flags);
    }

    /** 只适配旧 OPPO，供关闭小米重映射时的 Compose 调用点使用。 */
    public static boolean performRotaryHapticFeedback(View view, int feedbackConstant) {
        return component().performRotaryHapticFeedback(view, feedbackConstant);
    }

    public static boolean performRotaryHapticFeedback(View view, int feedbackConstant, int flags) {
        return component().performRotaryHapticFeedback(view, feedbackConstant, flags);
    }

    /** 供已确认 OPPO_LEGACY 且常量为 12 的 Xposed Hook 调用，不重入 View。 */
    public static boolean performOppoLegacyScrollFeedback(View view, int flags) {
        return OppoLegacyHaptics.perform(view, flags);
    }

    /** 调用方负责回收 OPPO 表冠转换产生的事件副本。 */
    public static MotionEvent rotaryEvent(MotionEvent event) {
        MotionEvent mapped = component().rotaryEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_SCROLL && OverlayLog.enabled()) {
            InputDevice device = event.getDevice();
            OverlayLog.debug("input device=" + (device == null ? "null" : device.getName())
                    + " id=" + event.getDeviceId() + " time=" + event.getEventTime()
                    + " source=0x" + Integer.toHexString(event.getSource())
                    + " pointers=" + event.getPointerCount()
                    + " vscroll=" + event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                    + " scroll=" + event.getAxisValue(26)
                    + " converted=" + (mapped != event)
                    + " mappedSource=0x" + Integer.toHexString(mapped.getSource())
                    + " mappedScroll=" + mapped.getAxisValue(26));
        }
        return mapped;
    }

    /** Gradle 输入包装在首次分发后记录结果；不改变消费、回退或回收行为。 */
    public static void logRotaryDispatch(MotionEvent original, MotionEvent mapped, boolean consumed) {
        if (original != mapped && OverlayLog.enabled()) {
            OverlayLog.debug("dispatch time=" + original.getEventTime()
                    + " converted=true consumed=" + consumed + " fallback=" + !consumed);
        }
    }

    /** 分发完成（包括异常）后仅回收自己创建的副本。 */
    public static void recycleRotaryEvent(MotionEvent original, MotionEvent mapped) {
        if (mapped != original) {
            mapped.recycle();
        }
    }
}
