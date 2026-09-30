package com.ronan.heyboxlite;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ArticleGameResponseSummaryTest {
    @Test
    public void reportsGameMarkersWithoutIncludingPayloadValues() throws Exception {
        JSONObject response = new JSONObject().put("result", new JSONObject()
                .put("content", new JSONObject().put("cpt", "game")
                        .put("data", new JSONObject().put("appid", "1174180"))));

        String summary = ArticleGameResponseSummary.describe(response);

        assertTrue(summary.contains("cpt:1"));
        assertTrue(summary.contains("appid:1"));
        assertTrue(summary.contains("cpt=game"));
        assertTrue(!summary.contains("1174180"));
    }
}
