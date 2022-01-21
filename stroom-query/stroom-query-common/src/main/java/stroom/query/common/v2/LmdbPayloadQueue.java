package stroom.query.common.v2;

import stroom.util.concurrent.CompletableQueue;

public class LmdbPayloadQueue extends CompletableQueue<LmdbPayload> {

    public LmdbPayloadQueue(final int capacity) {
        super(capacity);
    }
}
