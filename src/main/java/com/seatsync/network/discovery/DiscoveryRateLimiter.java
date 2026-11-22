package com.seatsync.network.discovery;

import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe sliding-window rate limiter to protect UDP discovery listeners
 * against packet flooding and UDP amplification attacks.
 */
public class DiscoveryRateLimiter {

    private final int maxPerSecond;
    private final ConcurrentHashMap<InetAddress, WindowCounter> buckets = new ConcurrentHashMap<>();

    public DiscoveryRateLimiter(int maxPerSecond) {
        this.maxPerSecond = maxPerSecond;
    }

    public DiscoveryRateLimiter() {
        this(DiscoveryConstants.MAX_REPLIES_PER_SEC_PER_IP);
    }

    /**
     * Checks whether a request from the given address is permitted under the rate limit.
     *
     * @param clientAddress Source address of incoming datagram
     * @return true if permitted; false if rate limit is exceeded
     */
    public boolean tryAcquire(InetAddress clientAddress) {
        if (clientAddress == null) {
            return false;
        }

        long currentSecond = System.currentTimeMillis() / 1000L;
        WindowCounter counter = buckets.compute(clientAddress, (k, v) -> {
            if (v == null || v.second != currentSecond) {
                return new WindowCounter(currentSecond, 1);
            }
            v.count.incrementAndGet();
            return v;
        });

        // Periodically evict stale buckets
        if (buckets.size() > 500) {
            buckets.entrySet().removeIf(entry -> entry.getValue().second < currentSecond - 2);
        }

        return counter.count.get() <= maxPerSecond;
    }

    private static class WindowCounter {
        final long second;
        final AtomicInteger count;

        WindowCounter(long second, int initialCount) {
            this.second = second;
            this.count = new AtomicInteger(initialCount);
        }
    }
}
