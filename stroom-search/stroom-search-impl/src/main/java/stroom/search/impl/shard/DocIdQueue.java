package stroom.search.impl.shard;

import stroom.util.concurrent.CompletableObjectQueue;

public class DocIdQueue extends CompletableObjectQueue<Integer> {

    public DocIdQueue(final int capacity) {
        super(capacity);
    }
}
