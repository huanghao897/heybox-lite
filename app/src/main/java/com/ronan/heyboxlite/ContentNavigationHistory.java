package com.ronan.heyboxlite;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

final class ContentNavigationHistory<T> {
    private final ArrayDeque<T> entries = new ArrayDeque<>();

    void push(T entry) {
        if (entry != null) entries.addLast(entry);
    }

    T peek() {
        return entries.peekLast();
    }

    T pop() {
        return entries.pollLast();
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    List<T> drain() {
        List<T> result = new ArrayList<>(entries);
        entries.clear();
        return result;
    }
}
