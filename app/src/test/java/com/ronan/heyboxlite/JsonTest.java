package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** 通用 JSON 取值助手的回归网：多键短路、真值判定、首个非空、深度查找。 */
public class JsonTest {

    // ---- truthy：命中即判，未识别则继续下一个键 ----

    @Test
    public void truthy_boolean() throws Exception {
        assertTrue(Json.truthy(new JSONObject().put("a", true), "a"));
        assertFalse(Json.truthy(new JSONObject().put("a", false), "a"));
    }

    @Test
    public void truthy_number_onlyOneIsTrue() throws Exception {
        assertTrue(Json.truthy(new JSONObject().put("a", 1), "a"));
        assertFalse(Json.truthy(new JSONObject().put("a", 0), "a"));
        assertFalse(Json.truthy(new JSONObject().put("a", 2), "a"));
    }

    @Test
    public void truthy_stringForms() throws Exception {
        assertTrue(Json.truthy(new JSONObject().put("a", "1"), "a"));
        assertTrue(Json.truthy(new JSONObject().put("a", "true"), "a"));
        assertTrue(Json.truthy(new JSONObject().put("a", "YES"), "a"));
        assertFalse(Json.truthy(new JSONObject().put("a", "0"), "a"));
        assertFalse(Json.truthy(new JSONObject().put("a", "false"), "a"));
        assertFalse(Json.truthy(new JSONObject().put("a", "no"), "a"));
    }

    @Test
    public void truthy_unrecognizedFallsThrough() throws Exception {
        // 第一个键的值无法识别，应继续看下一个键
        JSONObject o = new JSONObject().put("a", "maybe").put("b", "1");
        assertTrue(Json.truthy(o, "a", "b"));
    }

    @Test
    public void truthy_recognizedShortCircuits() throws Exception {
        // 第一个键识别为假即返回，不再看后面的键
        JSONObject o = new JSONObject().put("a", 0).put("b", 1);
        assertFalse(Json.truthy(o, "a", "b"));
    }

    @Test
    public void truthy_missingOrNull() throws Exception {
        assertFalse(Json.truthy(new JSONObject(), "a"));
        assertFalse(Json.truthy(null, "a"));
    }

    // ---- first：首个非空字符串 ----

    @Test
    public void first_nonEmpty() {
        assertEquals("x", Json.first("", "x", "y"));
        assertEquals("z", Json.first(null, "", "z"));
        assertEquals("", Json.first("", ""));
        assertEquals("", Json.first());
    }

    // ---- firstInt：首个存在的键（即使值为 0 也算命中）----

    @Test
    public void firstInt_firstPresentWins() throws Exception {
        assertEquals(5, Json.firstInt(new JSONObject().put("a", 5), -1, "a"));
        assertEquals(7, Json.firstInt(new JSONObject().put("b", 7), 0, "a", "b"));
        assertEquals(0, Json.firstInt(new JSONObject().put("a", 0).put("b", 9), -1, "a", "b"));
        assertEquals(-1, Json.firstInt(new JSONObject(), -1, "a"));
        assertEquals(-1, Json.firstInt(null, -1, "a"));
    }

    // ---- firstObject / firstArray ----

    @Test
    public void firstObject_skipsNonObjects() throws Exception {
        JSONObject inner = new JSONObject().put("k", 1);
        assertNotNull(Json.firstObject(new JSONObject().put("a", inner), "a"));
        assertNull(Json.firstObject(new JSONObject().put("a", 5), "a"));
        assertNotNull(Json.firstObject(new JSONObject().put("a", 5).put("b", inner), "a", "b"));
        assertNull(Json.firstObject(null, "a"));
    }

    @Test
    public void firstArray_directAndNested() throws Exception {
        JSONObject direct = new JSONObject().put("a", new JSONArray().put(1).put(2));
        JSONArray got = Json.firstArray(direct, "a");
        assertNotNull(got);
        assertEquals(2, got.length());

        // 键指向对象时，进入对象里找 links/list/events/moments/data
        JSONObject nested = new JSONObject().put("a",
                new JSONObject().put("list", new JSONArray().put(9)));
        JSONArray nestedGot = Json.firstArray(nested, "a");
        assertNotNull(nestedGot);
        assertEquals(1, nestedGot.length());

        assertNull(Json.firstArray(new JSONObject().put("x", 5), "x"));
    }

    // ---- findInt / findObject：深度查找，findInt 只认 >0 ----

    @Test
    public void findInt_deepAndPositiveOnly() throws Exception {
        JSONObject deep = new JSONObject().put("a",
                new JSONObject().put("b", new JSONObject().put("count", 7)));
        assertEquals(7, Json.findInt(deep, new String[]{"count"}, 0));

        // 顶层命中值为 0（不算），继续深入找到 3
        JSONObject skipZero = new JSONObject().put("count", 0)
                .put("n", new JSONObject().put("count", 3));
        assertEquals(3, Json.findInt(skipZero, new String[]{"count"}, 0));

        assertEquals(0, Json.findInt(new JSONObject().put("x", 1), new String[]{"count"}, 0));
    }

    @Test
    public void findObject_directAndRecursive() throws Exception {
        JSONObject target = new JSONObject().put("v", 1);
        assertNotNull(Json.findObject(new JSONObject().put("target", target), 0, "target"));
        assertNotNull(Json.findObject(new JSONObject().put("wrap",
                new JSONObject().put("target", target)), 0, "target"));
        assertNull(Json.findObject(new JSONObject().put("x", new JSONObject()), 0, "target"));
    }
}
