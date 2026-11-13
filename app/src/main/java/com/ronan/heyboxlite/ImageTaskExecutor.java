package com.ronan.heyboxlite;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Recent visible bindings go first; overflow retires the oldest queued binding. */
final class ImageTaskExecutor extends ThreadPoolExecutor {
    interface Discardable extends Runnable { void discarded(); }

    ImageTaskExecutor(int threads, int capacity) {
        super(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingDeque<Runnable>(capacity) {
                    @Override public boolean offer(Runnable task) { return offerFirst(task); }
                });
        setRejectedExecutionHandler((task, executor) -> {
            if (!executor.isShutdown()) {
                Runnable oldest = ((LinkedBlockingDeque<Runnable>) executor.getQueue()).pollLast();
                discard(oldest);
                if (executor.getQueue().offer(task)) return;
            }
            discard(task);
        });
    }

    private static void discard(Runnable task) {
        if (task instanceof Discardable) ((Discardable) task).discarded();
    }
}
