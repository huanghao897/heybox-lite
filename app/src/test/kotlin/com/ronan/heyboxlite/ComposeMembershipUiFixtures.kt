package com.ronan.heyboxlite

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.activity.ComponentActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue

/** Offline UI snapshots only; these fixtures never instantiate payment or check-in controllers. */
internal object MembershipUiFixtures {
    const val MONTH = "server_month"
    const val QUARTER = "server_quarter"
    const val MONTH_NAME = "30 \u5929\u4f1a\u5458"
    const val QUARTER_NAME = "90 \u5929\u4f1a\u5458"
    const val ORDER_ID = "HBAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    const val ORDER_EXPIRY = "2026-10-03T15:45:00Z"
    const val MEMBERSHIP_EXPIRY = "2027-02-04T12:34:00Z"

    fun products() = listOf(
        CheckinBilling.Product(MONTH, MONTH_NAME, 500, "CNY", 30, true),
        CheckinBilling.Product(QUARTER, QUARTER_NAME, 1000, "CNY", 90, true),
    )

    fun catalog(
        products: List<CheckinBilling.Product> = products(),
        mode: String = "paid",
        entitled: Boolean = false,
        admin: Boolean = false,
        expiresAt: String = "",
        available: Boolean = true,
        voluntary: Boolean = false,
        plan: CheckinBilling.Plan = CheckinBilling.Plan(MONTH_NAME, 500,
            "CNY", 30, false, 500, 1000),
    ) = CheckinBilling.Membership(mode, mode == "paid", entitled, admin, expiresAt,
        available, voluntary, plan, products, "afdian")

    fun state(
        catalog: CheckinBilling.Membership? = catalog(),
        order: CheckinBilling.Order? = null,
    ) = ComposeMembershipUiState(catalog = catalog, selectedSku = MONTH, order = order)

    fun order(
        status: String = "pending",
        provider: String = "afdian",
        payable: Int = 1000,
        currency: String = "CNY",
        name: String = QUARTER_NAME,
        sku: String = QUARTER,
        manual: Boolean = false,
        checkout: String = "https://afdian.com/order/create?ui_fixture=not_a_payment",
        review: CheckinBilling.Review? = null,
        duration: Int = 90,
        amount: Int = 1000,
    ) = CheckinBilling.Order(ORDER_ID, provider, amount, payable, currency, status,
        true, manual, ORDER_EXPIRY, review, checkout, sku, name, duration, "2026-10-03T15:30:00Z")

    fun account() = CheckinCenterClient.Account("connected", "Private fixture name", "123****89")

    fun task(
        enabled: Boolean = true,
        platformBlocked: Boolean = false,
        signBlocked: Boolean = false,
        schedule: String = "09:17",
        windowStart: String = "23:40",
        windowEnd: String = "00:10",
    ) = CheckinCenterClient.Task(enabled, true, schedule, 83, windowStart, windowEnd,
        platformBlocked, signBlocked, CheckinSharing.parse(JSONObject()))

    fun center(
        membership: CheckinBilling.Membership = catalog(),
        task: CheckinCenterClient.Task = task(),
        account: CheckinCenterClient.Account = account(),
        stage: ComposeCheckinStage = ComposeCheckinStage.CONNECTED,
    ) = ComposeCheckinUiState(paired = true, stage = stage,
        status = CheckinCenterClient.Status(account, task, null, membership))

    // Archived local records are display fixtures, not results of a live check-in.
    fun history(): CheckinHistory = CheckinHistory.parse(JSONObject().put("items", JSONArray()
        .put(JSONObject().put("id", 4001).put("status", "ok")
            .put("started_at", "2026-09-29T07:55:00Z").put("finished_at", "2026-09-29T08:03:00Z")
            .put("summary", "Archived server summary; no request was made")
            .put("check_in", JSONObject().put("checked_in", true)
                .put("coin_delta", 17).put("experience_delta", 93))
            .put("details", JSONArray()
                .put(JSONObject().put("label", "\u5206\u4eab\u5e16\u5b50").put("value", "\u5df2\u5b8c\u6210"))
                .put(JSONObject().put("label", "\u5206\u4eab\u6e38\u620f\u8be6\u60c5").put("value", "\u5931\u8d25"))
                .put(JSONObject().put("label", "Server detail").put("value", "Archived final field"))))
        .put(JSONObject().put("id", 4002).put("status", "failed")
            .put("started_at", "2026-09-30T23:51:00Z")
            .put("summary", "Archived failure; no rewards supplied")
            .put("details", JSONArray().put(JSONObject().put("label", "Server detail")
                .put("value", "Archived failure field"))))))

    fun emptyHistory(): CheckinHistory = CheckinHistory.parse(JSONObject().put("items", JSONArray()))

    fun purchases() = listOf(
        CheckinBilling.OrderRecord(ORDER_ID, QUARTER, QUARTER_NAME, 1000, 1000,
            "CNY", "paid", "2026-09-12T10:20:00Z", ORDER_EXPIRY, 90),
        CheckinBilling.OrderRecord("HBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", MONTH, MONTH_NAME,
            500, 500, "CNY", "expired", "2026-09-30T11:22:00Z", ORDER_EXPIRY, 30),
    )

    fun qr(): Bitmap {
        val matrix = MultiFormatWriter().encode("UI_FIXTURE_NOT_A_PAYMENT",
            BarcodeFormat.QR_CODE, 192, 192)
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            if (matrix[index % matrix.width, index / matrix.width]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    }
}

internal fun captureMembershipUi(activity: ComponentActivity, name: String) {
    val view = activity.window.decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    view.draw(Canvas(bitmap))
    try {
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "-")
        val file = File("build/outputs/ui-regression/checkin-$safeName-" +
            "${bitmap.width}x${bitmap.height}-${UUID.randomUUID()}.png")
        requireNotNull(file.parentFile).let { assertTrue(it.isDirectory || it.mkdirs()) }
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        println("Native offline UI screenshot: " + file.absolutePath)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue("Native rendering must not be blank: ${file.absolutePath}", pixels.toSet().size > 5)
    } finally {
        bitmap.recycle()
    }
}
