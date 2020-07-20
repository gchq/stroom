package stroom.search.server.shard;

import java.util.concurrent.atomic.AtomicLong;

class HitCount {
    private final AtomicLong hitCount = new AtomicLong();

    long get() {
        return hitCount.get();
    }

    void increment() {
        hitCount.incrementAndGet();
    }

    @Override
    public String toString() {
        return hitCount.toString();
    }
}
