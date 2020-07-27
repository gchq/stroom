package stroom.search.impl.shard;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

class IndexShardSearchProgressTracker {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardSearchProgressTracker.class);

    private final int shardCount;
    private final AtomicInteger completeShardCount = new AtomicInteger();
    private final HitCount hitCount = new HitCount();

    IndexShardSearchProgressTracker(final int shardCount) {
        this.shardCount = shardCount;
    }

    int getShardCount() {
        return shardCount;
    }

    void incrementCompleteShardCount() {
        completeShardCount.incrementAndGet();
    }

    boolean isComplete() {
        LOGGER.debug(this::toString);
        return completeShardCount.get() == shardCount;
    }

    HitCount getHitCount() {
        return hitCount;
    }

    @Override
    public String toString() {
        return "IndexShardSearchProgressTracker{" +
                "shardCount=" + shardCount +
                ", completeShardCount=" + completeShardCount +
                ", hitCount=" + hitCount +
                '}';
    }
}
