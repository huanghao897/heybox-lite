package com.ronan.heyboxlite;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Owns game metadata loading, retained page data, and foreground publication. */
final class NativeDetailGamesController implements LifecycleEventObserver {
    interface Host {
        boolean active(Object entry);
        void changed(Snapshot snapshot);
    }

    static final class Snapshot {
        final Map<String, GameCardData> data;
        final Set<String> finished;

        Snapshot(State state) {
            data = Collections.unmodifiableMap(new LinkedHashMap<>(state.data));
            finished = Collections.unmodifiableSet(new LinkedHashSet<>(state.finished));
        }
    }

    private static final int BATCH_SIZE = 20;
    private static final class State {
        final Map<String, GameCardData> data = new LinkedHashMap<>();
        final Set<String> finished = new LinkedHashSet<>();
        List<RichLinkClassifier.GameLinkInfo> references = Collections.emptyList();
        boolean loading;
        boolean discarded;
    }

    private final GameDetailClient client;
    private final Lifecycle lifecycle;
    private final Host host;
    private final Map<Object, State> pages = new WeakHashMap<>();
    private Object entry;
    private State current;
    private boolean resumed;
    private boolean closed;

    NativeDetailGamesController(GameDetailClient client, Lifecycle lifecycle, Host host) {
        this.client = client;
        this.lifecycle = lifecycle;
        this.host = host;
        resumed = lifecycle == null || lifecycle.getCurrentState().isAtLeast(Lifecycle.State.RESUMED);
        if (lifecycle != null) lifecycle.addObserver(this);
    }

    void show(Object entry, List<RichContent.Block> blocks) {
        if (closed) return;
        this.entry = entry;
        current = pages.get(entry);
        if (current == null) {
            current = new State();
            pages.put(entry, current);
        }
        Map<String, RichLinkClassifier.GameLinkInfo> references = new LinkedHashMap<>();
        for (RichContent.Block block : blocks) {
            if (!RichGameCardParser.isCard(block)) continue;
            GameCardData embedded = GameCardData.fromEmbedded(block.gameObject, block.value);
            if (embedded != null && embedded.hasVisibleDetails()) continue;
            String id = block.value == null ? "" : block.value.trim();
            if (!id.isEmpty()) references.put(id, new RichLinkClassifier.GameLinkInfo(id, "pc", "", ""));
        }
        current.references = new ArrayList<>(references.values());
        continueLoading();
    }

    void restore(Object entry) {
        if (closed) return;
        this.entry = entry;
        current = pages.get(entry);
        continueLoading();
    }

    void suspend() {
        entry = null;
        current = null;
    }

    void discard(Object entry) {
        State state = pages.remove(entry);
        if (state != null) state.discarded = true;
        if (this.entry == entry) suspend();
    }

    private boolean active() {
        return !closed && resumed && current != null && host.active(entry);
    }

    private void continueLoading() {
        if (!active()) return;
        host.changed(new Snapshot(current));
        if (!active() || current.loading) return;
        State state = current;
        List<RichLinkClassifier.GameLinkInfo> batch = new ArrayList<>();
        for (RichLinkClassifier.GameLinkInfo reference : state.references) {
            if (!state.finished.contains(reference.appId)) batch.add(reference);
            if (batch.size() == BATCH_SIZE) break;
        }
        if (batch.isEmpty()) return;
        state.loading = true;
        client.loadBatch(batch, new GameDetailClient.BatchCallback() {
            @Override public void onComplete(Map<String, GameCardData> data) {
                if (closed || state.discarded) return;
                state.loading = false;
                state.data.putAll(data);
                for (RichLinkClassifier.GameLinkInfo reference : batch) state.finished.add(reference.appId);
                if (current == state) continueLoading();
            }
        });
    }

    @Override public void onStateChanged(LifecycleOwner owner, Lifecycle.Event event) {
        if (event == Lifecycle.Event.ON_RESUME) {
            resumed = true;
            continueLoading();
        } else if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
            resumed = false;
        } else if (event == Lifecycle.Event.ON_DESTROY) {
            close();
        }
    }

    void close() {
        closed = true;
        for (State state : pages.values()) state.discarded = true;
        pages.clear();
        suspend();
        if (lifecycle != null) lifecycle.removeObserver(this);
    }
}
