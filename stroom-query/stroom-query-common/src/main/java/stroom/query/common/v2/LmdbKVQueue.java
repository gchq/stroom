package stroom.query.common.v2;

import stroom.util.concurrent.CompletableQueue;

public class LmdbKVQueue extends CompletableQueue<LmdbKV> {

    public LmdbKVQueue(final int capacity) {
        super(capacity);
    }
}
