package stroom.search.solr.search;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

class Tracker {
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicBoolean completed = new AtomicBoolean();

    long getHitCount() {
        return hitCount.get();
    }

    void incrementHitCount() {
        hitCount.incrementAndGet();
    }

    boolean isCompleted() {
        return completed.get();
    }

    void complete() {
        completed.set(true);
    }
}
