package stroom.search.server.shard;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.HasTerminate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

class IndexShardSearchProgressTracker {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardSearchProgressTracker.class);

    private final int shardTotal;
    private final CountDownLatch shardCount;
    private final AtomicLong hitCount;
    private final HasTerminate hasTerminate;

    IndexShardSearchProgressTracker(final HasTerminate hasTerminate,
                                    final AtomicLong hitCount,
                                    final int shardTotal) {
        this.hasTerminate = hasTerminate;
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
        return hasTerminate.isTerminated() || shardCount.getCount() == 0;
    }

    public boolean awaitCompletion(final long timeout, final TimeUnit unit) {
        LOGGER.debug(this::toString);
        try {
            return shardCount.await(timeout, unit);
        } catch (final InterruptedException e) {
            LOGGER.debug(this::toString);
            // Keep interrupting.
            Thread.currentThread().interrupt();
            return true;
        }
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
                ", hasTerminate=" + hasTerminate +
                '}';
    }
}
