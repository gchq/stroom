package stroom.query.common.v2;

import java.util.function.Consumer;

public interface LmdbPutFilter {
    void put(LmdbQueueItem queueItem, Consumer<LmdbQueueItem> consumer);
}
