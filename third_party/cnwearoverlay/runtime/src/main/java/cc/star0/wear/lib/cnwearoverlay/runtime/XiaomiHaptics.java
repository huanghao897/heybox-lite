package cc.star0.wear.lib.cnwearoverlay.runtime;

/** 小米组件：SCROLL_* 常量与 View 的 CLOCK_TICK / SEGMENT_FREQUENT_TICK 重映射。 */
final class XiaomiHaptics extends BaseOverlay {
    private static final String FRAMEWORK_CLASS = "miwear.os.VibrationEffectId";
    private static final String WEAR_CONSTANTS_CLASS =
            "com.xiaomi.miwear.input.WearHapticFeedbackConstants";

    private static final int CLOCK_TICK = 4;
    private static final int SEGMENT_FREQUENT_TICK = 27;
    private static final int SEGMENT_TICK = 26;

    XiaomiHaptics() {
        // 小米将 focus(19) 映射为 211，tick(18) / limit(20) 映射为 210 表冠反馈。
        super(19, 18, 20);
    }

    static boolean isAvailable() {
        // framework 标记不依赖可选 wear-sdk.jar 的加载方式。
        return hasSystemClass(FRAMEWORK_CLASS) || hasSystemClass(WEAR_CONSTANTS_CLASS);
    }

    @Override
    int remapFeedbackConstant(int feedbackConstant) {
        // 系统把 4/27 映射到 TEXTURE_TICK(21)；26 走 EFFECT_TICK(2) -> 210 表冠旋转。
        return feedbackConstant == CLOCK_TICK || feedbackConstant == SEGMENT_FREQUENT_TICK
                ? SEGMENT_TICK : feedbackConstant;
    }
}
