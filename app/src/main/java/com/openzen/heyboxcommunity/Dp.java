package com.openzen.heyboxcommunity;

/**
 * dp 换算（含用户界面缩放）。抽出的视图类通过它复用 MainActivity 的 dp 逻辑，
 * 而不必回传整个 Activity 或各自实现一份。
 */
interface Dp {
    int dp(int value);
}
