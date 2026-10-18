package com.ronan.heyboxlite

import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

/** Owns the capsule's native mount and visibility; its content lives in Compose. */
internal class BottomNavigationController(
    private val activity: ComponentActivity,
    private val session: SessionStore,
    tokens: ThemeTokens,
    roundLayout: Boolean,
    parent: FrameLayout,
) {
    val state = ComposeBottomNavState(
        composeThemeState(tokens, session.uiScale() / 100f,
            session.textScale() / 100f, roundLayout),
        tokens.dockSurface().asComposeColor(),
        tokens.navSelection().asComposeColor(),
        resolveDimensions(roundLayout),
    )
    val dimensions: ResponsiveDock.Dimensions get() = state.dimensions
    val view = ComposeView(activity).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent { ComposeBottomNavigation(state, !Motions.off()) }
        alpha = 0f
        visibility = View.GONE
    }
    private var visible = false
    private var animationSerial = 0

    init {
        parent.addView(view, layoutParams())
    }

    fun addItem(label: String, key: String, @DrawableRes drawable: Int, click: Runnable) {
        state.items.add(ComposeBottomNavItem(label, key, drawable, click))
    }

    fun select(key: String?) {
        state.selection.value = key
    }

    fun setVisible(show: Boolean, animate: Boolean, shellAnimating: Boolean) {
        val animated = animate && !Motions.off()
        if (show && visible && view.visibility == View.VISIBLE &&
            view.alpha > 0.98f && kotlin.math.abs(view.translationY) < 1f) return
        if (!show && !visible && view.visibility != View.VISIBLE) return
        val serial = ++animationSerial
        val wasHidden = view.visibility != View.VISIBLE
        view.animate().cancel()
        visible = show
        if (show) {
            view.visibility = View.VISIBLE
            if (!animated || shellAnimating) {
                view.alpha = 1f
                view.translationY = 0f
                return
            }
            if (wasHidden || view.alpha <= 0f) {
                view.alpha = 0f
                view.translationY = dp(22).toFloat()
            }
            view.animate().alpha(1f).translationY(0f)
                .setDuration(170L).setInterpolator(MotionSpec.EASE_OUT).start()
            return
        }
        if (!animated) {
            view.alpha = 0f
            view.translationY = 0f
            view.visibility = View.GONE
            return
        }
        view.animate().alpha(0f).translationY(dp(22).toFloat()).setDuration(120L)
            .setInterpolator(DecelerateInterpolator()).start()
        view.postDelayed({
            if (serial != animationSerial || visible) return@postDelayed
            view.visibility = View.GONE
            view.translationY = 0f
        }, 140L)
    }

    fun finishMotion() {
        animationSerial++
        view.animate().cancel()
        view.translationY = 0f
        view.scaleX = 1f
        view.scaleY = 1f
        view.alpha = if (visible) 1f else 0f
        view.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun bottomSafeSpace(): Int = dimensions.height + dimensions.marginBottom + dp(8)

    fun applyTheme(tokens: ThemeTokens, roundLayout: Boolean) {
        state.dimensions = resolveDimensions(roundLayout)
        state.apply(tokens, roundLayout, session.uiScale() / 100f, session.textScale() / 100f)
        view.layoutParams = layoutParams()
    }

    private fun resolveDimensions(roundLayout: Boolean): ResponsiveDock.Dimensions {
        val metrics = activity.resources.displayMetrics
        return ResponsiveDock.fromScreen(metrics.widthPixels, metrics.heightPixels,
            roundLayout, session.uiScale() / 100f)
    }

    private fun layoutParams() = FrameLayout.LayoutParams(dimensions.width, dimensions.height,
        Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
        bottomMargin = dimensions.marginBottom
    }

    private fun dp(value: Int): Int = UiComponents.dp(activity, value, session.uiScale() / 100f)
}
