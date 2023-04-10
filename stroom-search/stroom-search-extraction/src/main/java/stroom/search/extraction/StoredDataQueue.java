package stroom.search.extraction;

import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.query.api.v2.QueryKey;
import stroom.query.common.v2.SearchProgressLog;
import stroom.query.common.v2.SearchProgressLog.SearchPhase;
import stroom.util.concurrent.CompletableObjectQueue;

public class StoredDataQueue extends CompletableObjectQueue<Val[]> implements ValuesConsumer {

    private final QueryKey queryKey;

    public StoredDataQueue(final QueryKey queryKey,
                           final int capacity) {
        super(capacity);
        this.queryKey = queryKey;
    }

    @Override
    public void add(final Val[] values) {
        SearchProgressLog.increment(queryKey, SearchPhase.EXTRACTION_DECORATOR_FACTORY_STORED_DATA_QUEUE_PUT);
        put(values);
    }
}
