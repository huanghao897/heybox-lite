package com.ronan.heyboxlite;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/** Main-thread interaction overrides, scoped to one account/session and shared by all projections. */
final class PostInteractionState {
    static final class Like {
        final boolean liked;
        final int likes;

        Like(boolean liked, int likes) {
            this.liked = liked;
            this.likes = Math.max(0, likes);
        }
    }

    static final class Operation<T> {
        private final Slot<T> slot;
        private final long epoch;
        private final long version;
        private final T value;

        private Operation(Slot<T> slot, long epoch, long version, T value) {
            this.slot = slot;
            this.epoch = epoch;
            this.version = version;
            this.value = value;
        }
    }

    private static final class Slot<T> {
        T confirmed;
        long latestVersion;
        final TreeMap<Long, Operation<T>> pending = new TreeMap<>();

        Slot(T baseline) { confirmed = baseline; }

        T value() { return pending.isEmpty() ? confirmed : pending.lastEntry().getValue().value; }
    }

    private final SessionStore session;
    private final Map<String, Slot<Like>> likes = new HashMap<>();
    private final Map<String, Slot<Boolean>> favorites = new HashMap<>();
    private final Map<String, Slot<Boolean>> follows = new HashMap<>();
    private String account;
    private long sessionGeneration;
    private long epoch;
    private long version;

    PostInteractionState(SessionStore session) {
        this.session = session;
        this.account = session.userId();
        this.sessionGeneration = session.sessionGeneration();
    }

    void invalidateSession() {
        likes.clear();
        favorites.clear();
        follows.clear();
        epoch++;
        account = session.userId();
        sessionGeneration = session.sessionGeneration();
    }

    void apply(FeedItem item) {
        ensureSession();
        if (item == null) return;
        Like like = like(item.id);
        if (like != null) {
            item.liked = like.liked;
            item.likes = like.likes;
        }
        Boolean favorite = favorite(item.id);
        if (favorite != null) item.favorited = favorite;
        Boolean following = following(item.authorId);
        if (following != null) item.following = following;
        item.followPending = followPending(item.authorId);
    }

    void applyAll(Iterable<FeedItem> items) {
        ensureSession();
        for (FeedItem item : items) apply(item);
    }

    Like like(String postId) {
        ensureSession();
        Slot<Like> state = likes.get(postId);
        return state == null ? null : state.value();
    }

    Boolean favorite(String postId) {
        ensureSession();
        Slot<Boolean> state = favorites.get(postId);
        return state == null ? null : state.value();
    }

    Boolean following(String authorId) {
        ensureSession();
        Slot<Boolean> state = follows.get(authorId);
        return state == null ? null : state.value();
    }

    boolean followPending(String authorId) {
        ensureSession();
        Slot<Boolean> state = follows.get(authorId);
        return state != null && !state.pending.isEmpty();
    }

    Operation<Like> beginLike(FeedItem item, boolean liked) {
        apply(item);
        return begin(likes, item.id, new Like(item.liked, item.likes),
                new Like(liked, item.likes + (liked == item.liked ? 0 : liked ? 1 : -1)));
    }

    Operation<Boolean> beginFavorite(FeedItem item, boolean favorite) {
        apply(item);
        return begin(favorites, item.id, item.favorited, favorite);
    }

    Operation<Boolean> beginFollow(String authorId, boolean before, boolean following) {
        ensureSession();
        return begin(follows, authorId, before, following);
    }

    boolean isLatest(Operation<?> operation) {
        ensureSession();
        return operation.epoch == epoch && operation.slot.latestVersion == operation.version;
    }

    <T> boolean complete(Operation<T> operation, boolean success) {
        ensureSession();
        if (operation.epoch != epoch || operation.slot.pending.get(operation.version) != operation) return false;
        if (success) {
            operation.slot.confirmed = operation.value;
            // A confirmed newer write supersedes older pending writes and their late callbacks.
            operation.slot.pending.headMap(operation.version, true).clear();
        } else {
            operation.slot.pending.remove(operation.version);
        }
        return true;
    }

    private <T> Operation<T> begin(Map<String, Slot<T>> states, String id, T baseline, T next) {
        Slot<T> state = states.get(id);
        if (state == null) {
            state = new Slot<>(baseline);
            states.put(id, state);
        }
        Operation<T> operation = new Operation<>(state, epoch, ++version, next);
        state.latestVersion = operation.version;
        state.pending.put(operation.version, operation);
        return operation;
    }

    private void ensureSession() {
        if (!account.equals(session.userId()) || sessionGeneration != session.sessionGeneration()) {
            invalidateSession();
        }
    }
}
