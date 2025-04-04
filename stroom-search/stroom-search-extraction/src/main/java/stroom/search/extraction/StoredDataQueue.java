package stroom.search.extraction;

import stroom.query.api.QueryKey;
import stroom.query.common.v2.SearchProgressLog;
import stroom.query.common.v2.SearchProgressLog.SearchPhase;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.concurrent.CompletableObjectQueue;

public class StoredDataQueue extends CompletableObjectQueue<Val[]> implements ValuesConsumer {

    private final QueryKey queryKey;

    public StoredDataQueue(final QueryKey queryKey,
                           final int capacity) {
        super(capacity);
        this.queryKey = queryKey;
    }

    @Override
    public void accept(final Val[] values) {
        SearchProgressLog.increment(queryKey, SearchPhase.EXTRACTION_DECORATOR_FACTORY_STORED_DATA_QUEUE_PUT);
        put(values);
    }
}
