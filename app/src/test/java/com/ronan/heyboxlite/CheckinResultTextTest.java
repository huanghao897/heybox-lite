package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CheckinResultTextTest {
    @Test
    public void includesCheckInRewards() {
        CheckinCenterClient.RunResult result = new CheckinCenterClient.RunResult(
                "skipped", "", 12L,
                new CheckinCenterClient.CheckinResult(true, true, 20, 15, 3));

        assertEquals("已签到，获得 20 盒币、15 经验", CheckinResultText.runMessage(result));
    }

    @Test
    public void distinguishesSuccessfulAndSkippedTasks() {
        CheckinCenterClient.RunResult success = new CheckinCenterClient.RunResult(
                "ok", "", 12L, new CheckinCenterClient.CheckinResult(false, false, -1, -1, -1));
        CheckinCenterClient.RunResult skipped = new CheckinCenterClient.RunResult(
                "skipped", "", 13L, new CheckinCenterClient.CheckinResult(false, false, -1, -1, -1));

        assertEquals("小黑盒签到任务已完成", CheckinResultText.runMessage(success));
        assertEquals("今日没有需要执行的签到任务", CheckinResultText.runMessage(skipped));
    }

    @Test
    public void handlesUnknownOrMissingResult() {
        assertEquals("签到任务已返回结果", CheckinResultText.runMessage(null));
        assertEquals("签到任务已返回结果", CheckinResultText.runMessage(
                new CheckinCenterClient.RunResult("error", "", 0L, null)));
    }
}
