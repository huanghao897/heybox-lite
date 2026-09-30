package com.ronan.heyboxlite;

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

    private final ApiClient api;
    private static final int BATCH_SIZE = 20;
    private static final int CACHE_SIZE = 128;
    private final Map<String, GameCardData> cache = new LinkedHashMap<String, GameCardData>(
            CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, GameCardData> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    GameDetailClient(ApiClient api) {
        this.api = api;
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
                    GameCardData cached = this.cache.get(id);
                    if (cached != null) result.put(id, cached);
                    else if (!missing.contains(id)) missing.add(id);
                }
            }
        }
        loadBatchPart(missing, 0, result, callback);
    }

    private void loadBatchPart(List<String> ids, int offset,
                               LinkedHashMap<String, GameCardData> result,
                               BatchCallback callback) {
        if (offset >= ids.size()) {
            callback.onComplete(Collections.unmodifiableMap(result));
            return;
        }
        int end = Math.min(offset + BATCH_SIZE, ids.size());
        StringBuilder joined = new StringBuilder();
        for (int i = offset; i < end; i++) {
            if (joined.length() > 0) joined.append(',');
            joined.append(ids.get(i));
        }
        Map<String, String> query = new LinkedHashMap<>();
        query.put("appids", joined.toString());
        query.put("from", "link");
        this.api.getSigned(GAME_INFO_PATH, query, HeyboxSigner.Algorithm.LEGACY,
                ApiClient.RequestProfile.WEB, new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject body) {
                        for (String id : ids.subList(offset, end)) {
                            GameCardData data = GameCardData.from(body, id);
                            if (data != null && data.hasVisibleDetails()) {
                                synchronized (GameDetailClient.this) {
                                    cache.put(id, data);
                                }
                                result.put(id, data);
                            }
                        }
                        callback.onBatch(Collections.unmodifiableMap(
                                new LinkedHashMap<>(result)));
                        loadBatchPart(ids, end, result, callback);
                    }

                    @Override
                    public void onError(String message) {
                        // Keep the remaining cards visible as unavailable. A later
                        // article open can retry through the normal cache miss path.
                        loadBatchPart(ids, end, result, callback);
                    }
                });
    }

}
