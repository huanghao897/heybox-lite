package com.ronan.heyboxlite;

import android.os.SystemClock;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class GameDetailClient {
    // This is the web client endpoint used by the official game-card data
    // loader. It returns result.base_infos, unlike the old detail endpoint.
    private static final String GAME_INFO_PATH = "/game/get_game_infos/";

    interface BatchCallback {
        default void onBatch(Map<String, GameCardData> data) {
        }

        void onComplete(Map<String, GameCardData> data);
    }

    interface Clock {
        long elapsedRealtime();
    }

    interface Request {
        void getSigned(String path, Map<String, String> query, HeyboxSigner.Algorithm algorithm,
                       ApiClient.RequestProfile profile, ApiClient.Callback callback);
    }

    private static final int BATCH_SIZE = 20;
    private static final int CACHE_SIZE = 128;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;
    private final Request request;
    private final Clock clock;
    private final Map<String, Pending> inFlight = new LinkedHashMap<>();
    private final Map<String, Cached> cache = new LinkedHashMap<String, Cached>(
            CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    GameDetailClient(ApiClient api) {
        this(api::getSigned, SystemClock::elapsedRealtime);
    }

    GameDetailClient(Request request, Clock clock) {
        this.request = request;
        this.clock = clock;
    }

    void loadBatch(List<RichLinkClassifier.GameLinkInfo> references,
                   BatchCallback callback) {
        if (callback == null) return;
        final LinkedHashMap<String, GameCardData> result = new LinkedHashMap<>();
        final ArrayList<String> missing = new ArrayList<>();
        if (references != null) {
            synchronized (this) {
                for (RichLinkClassifier.GameLinkInfo reference : references) {
                    if (reference == null || !reference.valid()) continue;
                    String id = reference.appId;
                    if (result.containsKey(id)) continue;
                    GameCardData hit = cached(id);
                    if (hit != null) result.put(id, hit);
                    else if (!missing.contains(id)) missing.add(id);
                }
            }
        }
        new Load(missing, result, callback).next(0);
    }

    private GameCardData cached(String id) {
        Cached value = cache.get(id);
        if (value == null) return null;
        if (clock.elapsedRealtime() - value.storedAt >= CACHE_TTL_MS) {
            cache.remove(id);
            return null;
        }
        return value.data;
    }

    private void requestPart(Part part) {
        Map<String, GameCardData> hits = new LinkedHashMap<>();
        Map<String, Pending> owned = new LinkedHashMap<>();
        synchronized (this) {
            for (String id : part.ids) {
                GameCardData hit = cached(id);
                if (hit != null) {
                    hits.put(id, hit);
                    continue;
                }
                Pending pending = inFlight.get(id);
                if (pending == null) {
                    pending = new Pending(id);
                    inFlight.put(id, pending);
                    owned.put(id, pending);
                }
                pending.waiters.add(part);
            }
        }
        for (Map.Entry<String, GameCardData> hit : hits.entrySet()) {
            part.accept(hit.getKey(), hit.getValue(), false);
        }
        if (owned.isEmpty()) return;
        StringBuilder joined = new StringBuilder();
        for (String id : owned.keySet()) {
            if (joined.length() > 0) joined.append(',');
            joined.append(id);
        }
        Map<String, String> query = new LinkedHashMap<>();
        query.put("appids", joined.toString());
        query.put("from", "link");
        request.getSigned(GAME_INFO_PATH, query, HeyboxSigner.Algorithm.LEGACY,
                ApiClient.RequestProfile.WEB, new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject body) {
                        finish(owned, body, true);
                    }

                    @Override
                    public void onError(String message) {
                        finish(owned, null, false);
                    }
                });
    }

    private void finish(Map<String, Pending> owned, JSONObject body, boolean success) {
        Map<Pending, GameCardData> completed = new LinkedHashMap<>();
        long now = clock.elapsedRealtime();
        synchronized (this) {
            for (Pending pending : owned.values()) {
                if (inFlight.get(pending.id) != pending) continue;
                GameCardData data = success ? GameCardData.from(body, pending.id) : null;
                if (data != null && !data.hasVisibleDetails() && data.name.isEmpty()
                        && data.platforms.isEmpty()) data = null;
                inFlight.remove(pending.id);
                if (data != null) cache.put(pending.id, new Cached(data, now));
                completed.put(pending, data);
            }
        }
        // Publish only after all owned IDs are released, so callbacks can safely re-enter.
        for (Map.Entry<Pending, GameCardData> entry : completed.entrySet()) {
            Pending pending = entry.getKey();
            for (Part waiter : pending.waiters) waiter.accept(pending.id, entry.getValue(), success);
        }
    }

    private static Map<String, GameCardData> snapshot(Map<String, GameCardData> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private final class Load {
        final List<String> ids;
        final LinkedHashMap<String, GameCardData> result;
        final BatchCallback callback;

        Load(List<String> ids, LinkedHashMap<String, GameCardData> result, BatchCallback callback) {
            this.ids = ids;
            this.result = result;
            this.callback = callback;
        }

        void next(int offset) {
            if (offset >= ids.size()) {
                callback.onComplete(snapshot(result));
            } else {
                int end = Math.min(offset + BATCH_SIZE, ids.size());
                requestPart(new Part(this, ids.subList(offset, end), end));
            }
        }
    }

    private final class Part {
        final Load load;
        final List<String> ids;
        final int end;
        final Map<String, GameCardData> values = new LinkedHashMap<>();
        int remaining;
        boolean successful;

        Part(Load load, List<String> ids, int end) {
            this.load = load;
            this.ids = ids;
            this.end = end;
            this.remaining = ids.size();
        }

        void accept(String id, GameCardData data, boolean success) {
            boolean notifyBatch;
            synchronized (this) {
                if (data != null) values.put(id, data);
                successful |= success;
                if (--remaining != 0) return;
                for (String requested : ids) {
                    if (values.containsKey(requested)) load.result.put(requested, values.get(requested));
                }
                notifyBatch = successful;
            }
            if (notifyBatch) load.callback.onBatch(snapshot(load.result));
            load.next(end);
        }
    }

    private static final class Cached {
        final GameCardData data;
        final long storedAt;

        Cached(GameCardData data, long storedAt) {
            this.data = data;
            this.storedAt = storedAt;
        }
    }

    private final class Pending {
        final String id;
        final List<Part> waiters = new ArrayList<>();

        Pending(String id) {
            this.id = id;
        }
    }
}
