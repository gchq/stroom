package stroom.query.common.v2;

import stroom.util.concurrent.CompletableQueue;

public class LmdbWriteQueue extends CompletableQueue<LmdbQueueItem> {

    public LmdbWriteQueue(final int capacity) {
        super(capacity);
    }
}
