package stroom.search.impl.shard;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

class IndexShardSearchProgressTracker {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardSearchProgressTracker.class);

    private final int shardTotal;
    private final CountDownLatch shardCount;
    private final AtomicLong hitCount;

    IndexShardSearchProgressTracker(final AtomicLong hitCount,
                                    final int shardTotal) {
        this.hitCount = hitCount;
        this.shardTotal = shardTotal;
        this.shardCount = new CountDownLatch(shardTotal);
    }

    int getShardTotal() {
        return shardTotal;
    }

    void incrementCompleteShardCount() {
        shardCount.countDown();
    }

    public boolean isComplete() {
        LOGGER.debug(this::toString);
        return Thread.currentThread().isInterrupted() || shardCount.getCount() == 0;
    }

    public boolean awaitCompletion(final long timeout, final TimeUnit unit) throws InterruptedException {
        LOGGER.debug(this::toString);
        return shardCount.await(timeout, unit);
    }

    AtomicLong getHitCount() {
        return hitCount;
    }

    @Override
    public String toString() {
        return "IndexShardSearchProgressTracker{" +
                "shardTotal=" + shardTotal +
                ", completeShardCount=" + shardCount.getCount() +
                ", hitCount=" + hitCount +
                '}';
    }
}
