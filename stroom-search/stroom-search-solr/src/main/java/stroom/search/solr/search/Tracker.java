package stroom.search.solr.search;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

class Tracker {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Tracker.class);

    private final AtomicLong hitCount;
    private final CountDownLatch completed = new CountDownLatch(1);

    Tracker(final AtomicLong hitCount) {
        this.hitCount = hitCount;
    }

    long getHitCount() {
        return hitCount.get();
    }

    void incrementHitCount() {
        hitCount.incrementAndGet();
    }

    boolean awaitCompletion(final long timeout, final TimeUnit unit) throws InterruptedException {
        return completed.await(timeout, unit);
    }

    void complete() {
        completed.countDown();
    }
}
