package stroom.search.server.shard;

import stroom.util.shared.HasTerminate;

import java.util.concurrent.atomic.AtomicInteger;

class IndexShardSearchProgressTracker {
    private final int shardCount;
    private final AtomicInteger completeShardCount = new AtomicInteger();
    private final HitCount hitCount = new HitCount();
    private final HasTerminate hasTerminate;

    IndexShardSearchProgressTracker(final HasTerminate hasTerminate,
                                    final int shardCount) {
        this.hasTerminate = hasTerminate;
        this.shardCount = shardCount;
    }

    int getShardCount() {
        return shardCount;
    }

    void incrementCompleteShardCount() {
        completeShardCount.incrementAndGet();
    }

    boolean isComplete() {
        return hasTerminate.isTerminated() || completeShardCount.get() == shardCount;
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
                ", hasTerminate=" + hasTerminate +
                '}';
    }
}
