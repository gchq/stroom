package stroom.search.extraction;

import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.query.api.v2.QueryKey;
import stroom.query.common.v2.SearchProgressLog;
import stroom.query.common.v2.SearchProgressLog.SearchPhase;
import stroom.util.concurrent.CompletableQueue;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

public class StoredDataQueue extends CompletableQueue<Val[]> implements ValuesConsumer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StoredDataQueue.class);

    private final QueryKey queryKey;

    public StoredDataQueue(final QueryKey queryKey,
                           final int capacity) {
        super(capacity);
        this.queryKey = queryKey;
    }

    @Override
    public void add(final Val[] values) {
        try {
            SearchProgressLog.increment(queryKey, SearchPhase.EXTRACTION_DECORATOR_FACTORY_STORED_DATA_QUEUE_PUT);
            put(values);
        } catch (final InterruptedException e) {
            LOGGER.trace(e::getMessage, e);
            // Keep interrupting this thread.
            Thread.currentThread().interrupt();
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            throw e;
        }
    }
}
