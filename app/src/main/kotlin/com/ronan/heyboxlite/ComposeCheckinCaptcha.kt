package com.ronan.heyboxlite

import android.app.Activity
import android.content.Intent

/** Callback boundary used by the parent Activity for the dedicated captcha Activity. */
internal interface ComposeCheckinCaptchaCallbacks {
    fun onCaptchaResult(ticket: String, randstr: String)
    fun onCaptchaCancelled(message: String)
}

/**
 * Keeps Activity-result plumbing out of the Compose tree. There is only one captcha
 * session per Activity, matching the single mobile-login flow owned by the page.
 */
internal object ComposeCheckinCaptchaController {
    const val REQUEST_CODE = 0x7C41

    private var callbacks: ComposeCheckinCaptchaCallbacks? = null
    private var requestCode = REQUEST_CODE
    private var nextRequestCode = REQUEST_CODE
    private const val LAST_REQUEST_CODE = 0x7FFF

    @JvmStatic
    fun launch(
        activity: Activity,
        verificationUri: String,
        roundLayout: Boolean,
        listener: ComposeCheckinCaptchaCallbacks,
    ) {
        callbacks?.onCaptchaCancelled("安全验证已取消")
        callbacks = listener
        requestCode = nextRequestCode
        nextRequestCode = if (nextRequestCode == LAST_REQUEST_CODE) REQUEST_CODE else nextRequestCode + 1
        try {
            activity.startActivityForResult(
                CheckinCaptchaActivity.intent(activity, verificationUri, roundLayout),
                requestCode,
            )
        } catch (_: RuntimeException) {
            callbacks = null
            listener.onCaptchaCancelled("当前系统无法打开安全验证")
        }
    }

    /** Returns true when the result belongs to the Compose captcha session. */
    @JvmStatic
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode !in REQUEST_CODE..LAST_REQUEST_CODE) return false
        if (requestCode != this.requestCode) return true
        val listener = callbacks
        callbacks = null
        if (listener == null) return true

        if (resultCode == Activity.RESULT_OK && data != null) {
            val ticket = data.getStringExtra(CheckinCaptchaActivity.EXTRA_TICKET).orEmpty()
            val randstr = data.getStringExtra(CheckinCaptchaActivity.EXTRA_RANDSTR).orEmpty()
            if (CheckinCenterClient.captchaProofValid(ticket, randstr) && ticket.isNotEmpty()) {
                listener.onCaptchaResult(ticket, randstr)
                return true
            }
        }

        val message = data?.getStringExtra(CheckinCaptchaActivity.EXTRA_ERROR)
            ?.takeIf { it.isNotBlank() } ?: "安全验证已取消"
        listener.onCaptchaCancelled(message)
        return true
    }

    @JvmStatic
    fun clear(listener: ComposeCheckinCaptchaCallbacks? = null) {
        if (listener == null || callbacks === listener) callbacks = null
    }
}
