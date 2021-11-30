package stroom.search.extraction;

import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.util.concurrent.CompletableQueue;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.SearchProgressLog;
import stroom.util.logging.SearchProgressLog.SearchPhase;

public class StoredDataQueue extends CompletableQueue<Val[]> implements ValuesConsumer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StoredDataQueue.class);

    public StoredDataQueue(final int capacity) {
        super(capacity);
    }

    public void onComplete() {
        try {
            complete();
        } catch (final InterruptedException e) {
            LOGGER.trace(e::getMessage, e);
            // Keep interrupting this thread.
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void add(final Val[] values) {
        try {
            SearchProgressLog.increment(SearchPhase.EXTRACTION_DECORATOR_FACTORY_STORED_DATA_QUEUE_PUT);
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
