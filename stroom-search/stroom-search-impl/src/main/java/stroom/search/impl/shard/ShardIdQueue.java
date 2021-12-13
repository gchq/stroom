package stroom.search.impl.shard;

import stroom.util.concurrent.CompletableLongQueue;

import java.util.List;

public class ShardIdQueue extends CompletableLongQueue {

    public ShardIdQueue(final List<Long> shards) throws InterruptedException {
        // Make the queue big enough for all shards plus the completion state.
        super(shards.size() + 1);

        try {
            for (final Long shard : shards) {
                put(shard);
            }
        } finally {
            // Tell the queue there will be no more items.
            complete();
        }
    }
}
