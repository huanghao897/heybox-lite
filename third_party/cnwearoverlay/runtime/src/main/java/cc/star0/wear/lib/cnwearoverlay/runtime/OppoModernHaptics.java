package cc.star0.wear.lib.cnwearoverlay.runtime;

import android.view.InputDevice;
import android.view.MotionEvent;

/** OPPO 现代组件：系统将 GESTURE_START(12) 路由到波形 302，触觉调用保持原生透传。 */
final class OppoModernHaptics extends BaseOverlay {
    private static final String VIBRATOR_CLASS =
            "android.os.linearmotorvibrator.LinearmotorVibrator";
    private static final String SCROLL_FEEDBACK_CLASS = "android.view.HapticScrollFeedbackProvider";

    OppoModernHaptics() {
        super(12, 12, 12);
    }

    static boolean isAvailable() {
        return hasSystemClass(VIBRATOR_CLASS) && hasSystemClass(SCROLL_FEEDBACK_CLASS);
    }

    @Override
    boolean isOppo() {
        return true;
    }

    @Override
    boolean acceptsRotaryDevice(MotionEvent event) {
        InputDevice device = event.getDevice();
        return device != null && "oplus_crown".equals(device.getName());
    }
}
