package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class GameDetailClientTest {
    private static final long TTL = 5 * 60 * 1000L;
    private final FakeClock clock = new FakeClock();
    private final FakeRequest request = new FakeRequest();
    private final GameDetailClient client = new GameDetailClient(request, clock);

    @Test public void inFlightReentryUsesOneRequestForBothListeners() throws Exception {
        Recording first = load("42");
        Recording second = load("42");
        assertEquals(1, request.size());
        request.get(0).success(priced("42", "10"));
        first.assertComplete(1, 1);
        second.assertComplete(1, 1);
        assertSame(first.result.get("42"), second.result.get("42"));
        assertEquals("10", first.result.get("42").currentPrice);
    }

    @Test public void unpricedSuccessIsCachedUntilTheExactExpiryBoundary() throws Exception {
        Recording first = load("42");
        request.get(0).success(basic("42"));
        assertEquals("", first.result.get("42").currentPrice);
        clock.now = TTL - 1;
        Recording hit = load("42");
        hit.assertComplete(0, 1);
        assertEquals(1, request.size());
        clock.now = TTL;
        Recording refreshed = load("42");
        assertEquals(2, request.size());
        assertEquals(0, refreshed.completions);
        request.get(1).success(priced("42", "8"));
        refreshed.assertComplete(1, 1);
        assertEquals("8", refreshed.result.get("42").currentPrice);
    }

    @Test public void nameOnlyBasicDataIsAlsoCachedWithoutAPrice() throws Exception {
        Recording first = load("42");
        request.get(0).success(new JSONObject().put("appid", "42").put("name", "Game 42"));
        first.assertComplete(1, 1);
        Recording cached = load("42");
        cached.assertComplete(0, 1);
        assertSame(first.result.get("42"), cached.result.get("42"));
        assertEquals(1, request.size());
    }

    @Test public void pricedDataHasTheSameFiniteTtl() throws Exception {
        load("42");
        request.get(0).success(priced("42", "10"));
        clock.now = TTL;
        Recording refreshed = load("42");
        assertEquals(2, request.size());
        request.get(1).success(priced("42", "9"));
        assertEquals("9", refreshed.result.get("42").currentPrice);
    }

    @Test public void ttlStartsAtResponseTimeRatherThanRequestTime() throws Exception {
        load("42");
        clock.now = TTL * 2;
        request.get(0).success(basic("42"));
        clock.now += TTL - 1;
        load("42").assertComplete(0, 1);
        assertEquals(1, request.size());
        clock.now++;
        load("42");
        assertEquals(2, request.size());
    }

    @Test public void failedRequestReleasesEveryWaiterAndAllowsRetry() throws Exception {
        Recording first = load("42");
        Recording second = load("42");
        request.get(0).callback.onError("fixture failure");
        first.assertComplete(0, 0);
        second.assertComplete(0, 0);
        Recording retry = load("42");
        assertEquals(2, request.size());
        request.get(1).success(priced("42", "7"));
        retry.assertComplete(1, 1);
        first.assertComplete(0, 0);
        second.assertComplete(0, 0);
    }

    @Test public void lateDuplicateCallbackCannotConsumeANewerRetryOrOverwriteItsCache() throws Exception {
        Recording first = load("42");
        request.get(0).callback.onError("fixture failure");
        Recording retry = load("42");
        request.get(0).success(priced("42", "999"));
        assertEquals(0, retry.completions);
        request.get(1).success(priced("42", "7"));
        request.get(0).callback.onError("duplicate error");
        request.get(1).success(priced("42", "888"));
        first.assertComplete(0, 0);
        retry.assertComplete(1, 1);
        Recording cached = load("42");
        assertEquals("7", cached.result.get("42").currentPrice);
        assertEquals(2, request.size());
    }

    @Test public void emptySuccessDoesNotCreateAPhantomOrNegativeCache() throws Exception {
        Recording empty = load("42");
        request.get(0).success();
        empty.assertComplete(1, 0);
        load("42");
        assertEquals(2, request.size());
    }

    @Test public void batchesStaySequentialAndPublishCumulativeImmutableSnapshots() throws Exception {
        Recording result = load(range(1, 45));
        assertEquals(1, request.size());
        assertEquals(range(1, 20), request.get(0).ids);
        request.get(0).successAll();
        assertEquals(2, request.size());
        assertEquals(range(21, 40), request.get(1).ids);
        assertEquals(0, result.completions);
        request.get(1).successAll();
        assertEquals(3, request.size());
        assertEquals(range(41, 45), request.get(2).ids);
        request.get(2).successAll();
        result.assertComplete(3, 45);
        assertEquals(20, result.batches.get(0).size());
        assertEquals(40, result.batches.get(1).size());
        assertEquals(range(1, 45), new ArrayList<>(result.result.keySet()));
        assertImmutable(result.batches.get(0));
        assertImmutable(result.result);
        load(range(1, 45)).assertComplete(0, 45);
        assertEquals(3, request.size());
    }

    @Test public void aFailedBatchDoesNotPreventLaterBatchesOrCauseAutomaticRetry() throws Exception {
        Recording result = load(range(1, 25));
        request.get(0).callback.onError("fixture failure");
        assertEquals(2, request.size());
        assertEquals(range(21, 25), request.get(1).ids);
        request.get(1).successAll();
        result.assertComplete(1, 5);
        assertEquals(range(21, 25), new ArrayList<>(result.result.keySet()));
        assertEquals(2, request.size());
    }

    @Test public void overlappingBatchesCompleteOnceWhenFirstRequestFinishesFirst() throws Exception {
        verifyOverlap(true);
    }

    @Test public void overlappingBatchesCompleteOnceWhenSecondRequestFinishesFirst() throws Exception {
        verifyOverlap(false);
    }

    private void verifyOverlap(boolean firstFinishesFirst) throws Exception {
        Recording first = load(range(1, 25));
        Recording second = load(range(11, 35));
        assertEquals(2, request.size());
        assertEquals(range(1, 20), request.get(0).ids);
        assertEquals(range(21, 30), request.get(1).ids);
        request.get(firstFinishesFirst ? 0 : 1).successAll();
        assertEquals(2, request.size());
        assertEquals(0, second.completions);
        request.get(firstFinishesFirst ? 1 : 0).successAll();
        assertEquals(3, request.size());
        assertEquals(range(31, 35), request.get(2).ids);
        assertEquals(1, first.completions);
        request.get(2).successAll();
        assertEquals(1, first.completions);
        assertEquals(1, second.completions);
        assertEquals(range(1, 25), new ArrayList<>(first.result.keySet()));
        assertEquals(range(11, 35), new ArrayList<>(second.result.keySet()));
        assertEquals(20, first.batches.get(0).size());
        assertEquals(20, second.batches.get(0).size());
        assertEquals(25, second.batches.get(1).size());
        load(range(1, 35)).assertComplete(0, 35);
        assertEquals(3, request.size());
    }

    @Test public void overlappingFailureReleasesSharedIdsWithoutStrandingOtherResults() throws Exception {
        Recording first = load(range(1, 20));
        Recording second = load(range(11, 30));
        request.get(0).callback.onError("fixture failure");
        first.assertComplete(0, 0);
        assertEquals(0, second.completions);
        request.get(1).successAll();
        second.assertComplete(1, 10);
        assertEquals(range(21, 30), new ArrayList<>(second.result.keySet()));
        Recording retry = load(range(11, 20));
        assertEquals(3, request.size());
        request.get(2).successAll();
        retry.assertComplete(1, 10);
        load(range(11, 30)).assertComplete(0, 20);
        assertEquals(3, request.size());
    }

    @Test public void reentrantCallbacksSeeTheWholePublishedBatchWithoutAnyClientLock() throws Exception {
        Recording nested = new Recording();
        Recording outer = new Recording() {
            @Override public void onBatch(Map<String, GameCardData> data) {
                super.onBatch(data);
                client.loadBatch(references(range(1, 20)), nested);
            }
        };
        client.loadBatch(references(range(1, 20)), outer);
        request.get(0).successAll();
        outer.assertComplete(1, 20);
        nested.assertComplete(0, 20);
        assertEquals(1, request.size());
    }

    @Test public void concurrentCallersReserveTheSameIdOnlyOnce() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Recording> results = new ArrayList<>();
        List<Future<?>> calls = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                Recording result = new Recording();
                results.add(result);
                calls.add(workers.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    client.loadBatch(references(Arrays.asList("42")), result);
                    return null;
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> call : calls) call.get(5, TimeUnit.SECONDS);
            assertEquals(1, request.size());
            request.get(0).success(basic("42"));
            for (Recording result : results) result.assertComplete(1, 1);
        } finally {
            start.countDown();
            workers.shutdownNow();
        }
    }

    @Test public void concurrentOverlappingResponsesStillCompleteEachLoadExactlyOnce() throws Exception {
        Recording first = load(range(1, 25));
        Recording second = load(range(11, 35));
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> responses = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Call call = request.get(i);
                responses.add(workers.submit(() -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    call.successAll();
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> response : responses) response.get(5, TimeUnit.SECONDS);
            assertEquals(3, request.size());
            request.get(2).successAll();
            assertEquals(1, first.completions);
            second.assertComplete(2, 25);
            assertEquals(25, first.result.size());
            load(range(1, 35)).assertComplete(0, 35);
        } finally {
            start.countDown();
            workers.shutdownNow();
        }
    }

    @Test public void cacheRetainsItsExistingBoundAndLeastRecentlyUsedEviction() throws Exception {
        Recording initial = load(range(1, 128));
        for (int i = 0; i < 7; i++) request.get(i).successAll();
        initial.assertComplete(7, 128);
        load("1").assertComplete(0, 1);
        load("129");
        request.get(7).successAll();
        load("1").assertComplete(0, 1);
        assertEquals(8, request.size());
        load("2");
        assertEquals(9, request.size());
    }

    @Test public void nullEmptyInvalidAndDuplicateReferencesKeepTheCallbackContract() throws Exception {
        Recording empty = new Recording();
        client.loadBatch(null, empty);
        empty.assertComplete(0, 0);
        client.loadBatch(references(Arrays.asList("42")), null);
        assertEquals(0, request.size());
        Recording duplicate = new Recording();
        client.loadBatch(Arrays.asList(null,
                new RichLinkClassifier.GameLinkInfo("", "pc", "", ""),
                new RichLinkClassifier.GameLinkInfo("42", "pc", "", ""),
                new RichLinkClassifier.GameLinkInfo("42", "pc", "", "")), duplicate);
        assertEquals(Arrays.asList("42"), request.get(0).ids);
        request.get(0).successAll();
        duplicate.assertComplete(1, 1);
    }

    private Recording load(String id) {
        return load(Arrays.asList(id));
    }

    private Recording load(List<String> ids) {
        Recording result = new Recording();
        client.loadBatch(references(ids), result);
        return result;
    }

    private static List<RichLinkClassifier.GameLinkInfo> references(List<String> ids) {
        List<RichLinkClassifier.GameLinkInfo> result = new ArrayList<>();
        for (String id : ids) result.add(new RichLinkClassifier.GameLinkInfo(id, "pc", "", ""));
        return result;
    }

    private static List<String> range(int start, int end) {
        List<String> result = new ArrayList<>();
        for (int id = start; id <= end; id++) result.add(String.valueOf(id));
        return result;
    }

    private static JSONObject basic(String id) throws Exception {
        return new JSONObject().put("appid", id).put("name", "Game " + id)
                .put("image", "https://game-card-test.invalid/" + id + ".jpg");
    }

    private static JSONObject priced(String id, String price) throws Exception {
        return basic(id).put("price", new JSONObject().put("current", price));
    }

    private static void assertImmutable(Map<String, GameCardData> value) {
        try {
            value.clear();
            fail("Callback maps must be immutable snapshots");
        } catch (UnsupportedOperationException expected) {
            // Expected: callers cannot modify a later listener's results.
        }
    }

    private final class FakeRequest implements GameDetailClient.Request {
        final List<Call> calls = Collections.synchronizedList(new ArrayList<>());

        @Override public void getSigned(String path, Map<String, String> query,
                HeyboxSigner.Algorithm algorithm, ApiClient.RequestProfile profile,
                ApiClient.Callback callback) {
            assertFalse("Transport must execute outside the client lock", Thread.holdsLock(client));
            assertEquals("/game/get_game_infos/", path);
            assertEquals("link", query.get("from"));
            assertEquals(2, query.size());
            assertEquals(HeyboxSigner.Algorithm.LEGACY, algorithm);
            assertEquals(ApiClient.RequestProfile.WEB, profile);
            List<String> ids = Arrays.asList(query.get("appids").split(","));
            assertTrue(ids.size() <= 20);
            calls.add(new Call(ids, callback));
        }

        int size() { return calls.size(); }
        Call get(int index) { return calls.get(index); }
    }

    private static final class Call {
        final List<String> ids;
        final ApiClient.Callback callback;

        Call(List<String> ids, ApiClient.Callback callback) {
            this.ids = ids;
            this.callback = callback;
        }

        void successAll() throws Exception {
            List<JSONObject> games = new ArrayList<>();
            for (String id : ids) games.add(basic(id));
            success(games.toArray(new JSONObject[0]));
        }

        void success(JSONObject... games) throws Exception {
            JSONArray values = new JSONArray();
            for (JSONObject game : games) values.put(game);
            callback.onSuccess(new JSONObject().put("result", new JSONObject().put("base_infos", values)));
        }
    }

    private class Recording implements GameDetailClient.BatchCallback {
        final List<Map<String, GameCardData>> batches = new ArrayList<>();
        Map<String, GameCardData> result = new LinkedHashMap<>();
        int completions;

        @Override public synchronized void onBatch(Map<String, GameCardData> data) {
            assertFalse("onBatch must execute outside the client lock", Thread.holdsLock(client));
            assertEquals(0, completions);
            batches.add(data);
        }

        @Override public synchronized void onComplete(Map<String, GameCardData> data) {
            assertFalse("onComplete must execute outside the client lock", Thread.holdsLock(client));
            assertEquals(0, completions);
            completions++;
            result = data;
        }

        void assertComplete(int batchCount, int size) {
            assertEquals(1, completions);
            assertEquals(batchCount, batches.size());
            assertEquals(size, result.size());
        }
    }

    private static final class FakeClock implements GameDetailClient.Clock {
        volatile long now;
        @Override public long elapsedRealtime() { return now; }
    }
}
