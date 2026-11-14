package cc.star0.wear.lib.cnwearoverlay.runtime;

import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/** 基础组件：原生透传、系统类探测、Compose 常量构造和两代 OPPO 共用的输入转换。 */
class BaseOverlay {
    private final boolean active;
    final int scrollItemFocus;
    final int scrollTick;
    final int scrollLimit;

    BaseOverlay() {
        // 非目标系统不启用适配；桩类取值保留原有的 Wear4 默认值。
        this(false, 19, 18, 20);
    }

    BaseOverlay(int focus, int tick, int limit) {
        this(true, focus, tick, limit);
    }

    private BaseOverlay(boolean active, int focus, int tick, int limit) {
        this.active = active;
        this.scrollItemFocus = focus;
        this.scrollTick = tick;
        this.scrollLimit = limit;
    }

    final boolean isActive() {
        return active;
    }

    boolean isOppo() {
        return false;
    }

    boolean acceptsRotaryDevice(MotionEvent event) {
        // 旧 OPPO 的设备名不统一；需要名称限制的平台组件单独覆写。
        return true;
    }

    /** 只探测系统类且不初始化，避免命中 app 内的 Google 桩类或厂商 SDK。 */
    static boolean hasSystemClass(String name) {
        try {
            ClassLoader boot = BaseOverlay.class.getClassLoader();
            while (boot != null && boot.getParent() != null) {
                boot = boot.getParent();
            }
            Class.forName(name, false, boot);
            if (OverlayLog.enabled()) {
                OverlayLog.debug("systemClass=" + name + " present=true");
            }
            return true;
        } catch (ClassNotFoundException e) {
            if (OverlayLog.enabled()) {
                OverlayLog.debug("systemClass=" + name + " present=false error=" + e);
            }
            return false;
        } catch (LinkageError | SecurityException e) {
            OverlayLog.failure("systemClass=" + name + " probe failed", e);
            return false;
        }
    }

    final Object rotaryConstants(Class<?> cls) {
        if (!active) {
            OverlayLog.debug("rotaryConstants inactive; using Compose defaults");
            return null;
        }
        try {
            // 1.6.2 的基类是 abstract；创建具体子类的新实例，不修改 Kotlin 全局单例。
            Class<?> concrete = Class.forName(
                    cls.getName() + "$Wear4RotaryHapticConstants", true, cls.getClassLoader());
            Constructor<?> constructor = concrete.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object constants = constructor.newInstance();
            setConstant(cls, constants, "scrollFocus", scrollItemFocus);
            setConstant(cls, constants, "scrollTick", scrollTick);
            setConstant(cls, constants, "scrollLimit", scrollLimit);
            if (OverlayLog.enabled()) {
                OverlayLog.debug("rotaryConstants focus=" + scrollItemFocus + " tick=" + scrollTick
                        + " limit=" + scrollLimit + " class=" + cls.getName());
            }
            return constants;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            OverlayLog.failure("rotaryConstants reflection failed", e);
            throw new IllegalStateException("Unsupported wear-compose HapticConstants structure", e);
        }
    }

    private static void setConstant(Class<?> cls, Object instance, String name, int value)
            throws NoSuchFieldException, IllegalAccessException {
        Field field = cls.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    int remapFeedbackConstant(int feedbackConstant) {
        return feedbackConstant;
    }

    final boolean performHapticFeedback(View view, int feedbackConstant) {
        return performRotaryHapticFeedback(view, remapFeedbackConstant(feedbackConstant));
    }

    final boolean performHapticFeedback(View view, int feedbackConstant, int flags) {
        return performRotaryHapticFeedback(view, remapFeedbackConstant(feedbackConstant), flags);
    }

    boolean performRotaryHapticFeedback(View view, int feedbackConstant) {
        boolean result = view.performHapticFeedback(feedbackConstant);
        if (OverlayLog.enabled()) {
            OverlayLog.debug("haptic native constant=" + feedbackConstant + " result=" + result);
        }
        return result;
    }

    boolean performRotaryHapticFeedback(View view, int feedbackConstant, int flags) {
        boolean result = view.performHapticFeedback(feedbackConstant, flags);
        if (OverlayLog.enabled()) {
            OverlayLog.debug("haptic native constant=" + feedbackConstant + " flags=" + flags
                    + " result=" + result);
        }
        return result;
    }

    final MotionEvent rotaryEvent(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_SCROLL
                || !event.isFromSource(InputDevice.SOURCE_MOUSE)
                || event.isFromSource(0x00400000)
                || event.getPointerCount() != 1
                || !isOppo()) {
            return event;
        }
        float delta = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
        if (Float.isNaN(delta) || Float.isInfinite(delta) || delta == 0f
                || !acceptsRotaryDevice(event)) {
            return event;
        }
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        event.getPointerProperties(0, properties);
        event.getPointerCoords(0, coords);
        // 两条 Compose 路径都会对原始轴值取负并乘系统 scroll factor；这里只换轴，不缩放/翻转。
        coords.setAxisValue(26, delta);
        return MotionEvent.obtain(event.getDownTime(), event.getEventTime(), event.getAction(), 1,
                new MotionEvent.PointerProperties[] {properties},
                new MotionEvent.PointerCoords[] {coords},
                event.getMetaState(), event.getButtonState(), event.getXPrecision(), event.getYPrecision(),
                event.getDeviceId(), event.getEdgeFlags(), 0x00400000, event.getFlags());
    }
}
