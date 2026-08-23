package com.ronan.heyboxlite;

import android.view.View;
import android.widget.ScrollView;

import org.json.JSONObject;

final class DetailNavigationState {
    final View root;
    final DetailPager pager;
    final ScrollView articleScroll;
    final ScrollView commentScroll;
    final FeedItem item;
    final String returnScreen;
    final View returnView;
    final String returnTitle;
    final String linkId;
    final String linkHsrc;
    final String authCode;
    final String diagnostics;
    final JSONObject body;
    final DetailLoadCoordinator.State loadState;

    DetailNavigationState(View root, DetailPager pager,
                          ScrollView articleScroll, ScrollView commentScroll,
                          FeedItem item, String returnScreen, View returnView,
                          String returnTitle, String linkId, String linkHsrc,
                          String authCode, String diagnostics, JSONObject body,
                          DetailLoadCoordinator.State loadState) {
        this.root = root;
        this.pager = pager;
        this.articleScroll = articleScroll;
        this.commentScroll = commentScroll;
        this.item = item;
        this.returnScreen = returnScreen;
        this.returnView = returnView;
        this.returnTitle = returnTitle;
        this.linkId = linkId;
        this.linkHsrc = linkHsrc;
        this.authCode = authCode;
        this.diagnostics = diagnostics;
        this.body = body;
        this.loadState = loadState;
    }
}
