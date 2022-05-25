package stroom.search.impl.shard;

import stroom.util.concurrent.CompletableIntQueue;

public class DocIdQueue extends CompletableIntQueue {

    public DocIdQueue(final int capacity) {
        super(capacity);
    }
}
