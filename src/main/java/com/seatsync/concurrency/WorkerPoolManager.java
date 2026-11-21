package com.seatsync.concurrency;

import com.seatsync.common.contracts.RequestContext;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/** Runs one ordered queue per active session, sharing four workers across sessions. */
public final class WorkerPoolManager implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(WorkerPoolManager.class.getName());
    private static final int MAX_PENDING = 128;
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final ExecutorService pool = Executors.newFixedThreadPool(4,
        r -> new Thread(r, "seatsync-worker-" + threadNumber.getAndIncrement()));
    private final Map<RequestContext, ArrayDeque<Runnable>> queues = new HashMap<>();
    private int pending;

    synchronized boolean submit(RequestContext context, Runnable task) {
        if (pool.isShutdown() || pending >= MAX_PENDING) {
            return false;
        }
        var queue = queues.get(context);
        if (queue == null) {
            queue = new ArrayDeque<>();
            queues.put(context, queue);
            pool.execute(() -> drain(context));
        }
        queue.addLast(task);
        pending++;
        return true;
    }

    private void drain(RequestContext context) {
        while (true) {
            Runnable task;
            synchronized (this) {
                task = queues.get(context).pollFirst();
                if (task == null) {
                    queues.remove(context);
                    return;
                }
                pending--;
            }
            try {
                if (context.isOpen()) {
                    task.run();
                }
            } catch (RuntimeException failure) {
                LOG.warning("Worker could not deliver a session reply");
            }
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            pool.shutdown();
        }
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
