package com.ronan.heyboxlite

internal enum class CheckinResultKind { SUCCESS, FAILURE, UNKNOWN }

internal data class CheckinTaskResult(val title: String, val value: String, val kind: CheckinResultKind, val icon: Int)

internal fun checkinTasks(entry: CheckinHistory.Entry): List<CheckinTaskResult> {
    fun share(title: String, aliases: List<String>, icon: Int): CheckinTaskResult {
        val detail = entry.details.firstOrNull { item -> aliases.any { item.startsWith("$it · ") } }
        val value = detail?.substringAfter(" · ", "") ?: "未提供结果"
        val kind = when (value) {
            "已完成", "已分享", "成功", "是" -> CheckinResultKind.SUCCESS
            "失败", "未完成", "未分享", "否" -> CheckinResultKind.FAILURE
            else -> CheckinResultKind.UNKNOWN
        }
        return CheckinTaskResult(title, value, kind, icon)
    }
    return listOf(
        CheckinTaskResult("基础签到", if (entry.checkedIn) "已签到" else "未提供结果",
            if (entry.checkedIn) CheckinResultKind.SUCCESS else CheckinResultKind.UNKNOWN, R.drawable.il_calendar),
        share("分享帖子", listOf("分享帖子"), R.drawable.il_reply),
        share("分享游戏", listOf("分享游戏详情", "分享游戏"), R.drawable.ic_game_link),
        share("分享评价", listOf("分享游戏评价", "分享评价"), R.drawable.il_star),
    )
}

internal fun checkinResultKind(entry: CheckinHistory.Entry): CheckinResultKind = when {
    entry.checkedIn || entry.state == "已完成" -> CheckinResultKind.SUCCESS
    entry.state == "失败" -> CheckinResultKind.FAILURE
    else -> CheckinResultKind.UNKNOWN
}

internal fun checkinResultTitle(entry: CheckinHistory.Entry): String = when {
    entry.checkedIn -> "签到成功"
    entry.state == "失败" -> "签到失败"
    entry.state == "已完成" -> "任务完成"
    else -> entry.state
}

internal fun checkinWindow(task: CheckinCenterClient.Task): String = when {
    task.windowStart.isEmpty() -> task.windowEnd
    task.windowEnd.isEmpty() -> task.windowStart
    else -> "${task.windowStart} - ${task.windowEnd}"
}

internal fun checkinRunMessage(result: CheckinCenterClient.RunResult): String {
    val sign = result.checkIn
    if (sign != null && sign.checkedIn) {
        val rewards = buildList {
            if (sign.coinDelta >= 0) add("${sign.coinDelta} 盒币")
            if (sign.experienceDelta >= 0) add("${sign.experienceDelta} 经验")
        }.joinToString("、")
        return if (rewards.isEmpty()) "已签到" else "已签到，获得 $rewards"
    }
    return when (result.status.lowercase()) {
        "ok" -> "小黑盒签到任务已完成"
        "skipped" -> "今日没有需要执行的签到任务"
        else -> "签到任务已返回结果"
    }
}
