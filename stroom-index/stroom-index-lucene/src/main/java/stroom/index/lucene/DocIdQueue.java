package stroom.index.lucene;

import stroom.util.concurrent.CompletableObjectQueue;

class DocIdQueue extends CompletableObjectQueue<Integer> {

    DocIdQueue(final int capacity) {
        super(capacity);
    }
}
