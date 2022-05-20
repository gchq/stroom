package stroom.search.impl.shard;

import stroom.util.concurrent.CompleteException;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

public class ShardIdQueue {

    private final ArrayBlockingQueue<Long> queue;

    public ShardIdQueue(final List<Long> shards) {
        queue = new ArrayBlockingQueue<>(shards.size());
        queue.addAll(shards);
    }

    public Long next() throws CompleteException {
        final Long shardId =  queue.poll();
        if (shardId == null) {
            throw new CompleteException();
        }
        return shardId;
    }
}
