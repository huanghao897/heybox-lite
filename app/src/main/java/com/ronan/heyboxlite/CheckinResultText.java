package com.ronan.heyboxlite;

/** User-facing text for completed check-in actions. */
final class CheckinResultText {
    private CheckinResultText() {}

    static String runMessage(CheckinCenterClient.RunResult result) {
        if (result != null && result.checkIn != null && result.checkIn.checkedIn) {
            String reward = rewardLabel(result.checkIn);
            return reward.isEmpty() ? "已签到" : "已签到，获得 " + reward;
        }
        if (result != null && "ok".equalsIgnoreCase(result.status)) {
            return "小黑盒签到任务已完成";
        }
        if (result != null && "skipped".equalsIgnoreCase(result.status)) {
            return "今日没有需要执行的签到任务";
        }
        return "签到任务已返回结果";
    }

    private static String rewardLabel(CheckinCenterClient.CheckinResult result) {
        StringBuilder value = new StringBuilder();
        if (result.coinDelta >= 0) value.append(result.coinDelta).append(" 盒币");
        if (result.experienceDelta >= 0) {
            if (value.length() > 0) value.append("、");
            value.append(result.experienceDelta).append(" 经验");
        }
        return value.toString();
    }
}
