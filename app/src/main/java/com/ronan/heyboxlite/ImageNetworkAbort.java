package com.ronan.heyboxlite;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Disconnect may block in vendor network stacks; never run it on a scrolling UI thread. */
final class ImageNetworkAbort {
    // If the bounded queue is saturated, cooperative cancellation still suppresses delivery.
    // The I/O worker's existing timeout/finally remains responsible for closing its connection.
    static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(1, 1, 30L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), runnable -> {
                Thread thread = new Thread(runnable, "heybox-image-abort");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.DiscardPolicy());

    static { EXECUTOR.allowCoreThreadTimeOut(true); }
    private ImageNetworkAbort() {}
}
