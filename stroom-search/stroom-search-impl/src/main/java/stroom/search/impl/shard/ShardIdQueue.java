package stroom.search.impl.shard;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

public class ShardIdQueue {

    private final ArrayBlockingQueue<Long> queue;

    public ShardIdQueue(final List<Long> shards) {
        queue = new ArrayBlockingQueue<>(shards.size(), false, shards);
    }

    public Long next() {
        return queue.poll();
    }
}
