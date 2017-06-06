package stroom.statistics.internal;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public interface InternalStatisticsFacade {

    Logger LOGGER = LoggerFactory.getLogger(InternalStatisticsFacade.class);

    Consumer<Throwable> LOG_ONLY_EXCEPTION_HANDLER = throwable ->
            LOGGER.error("Swallowing exception from putting internal statistic events", throwable);

    /**
     * @param internalStatisticEvent A statistic event to record.
     *                               For the statistic event to be record by an implementing service there must be
     *                               All exceptions will be swallowed and logged as errors
     */
    default void putEvent(InternalStatisticEvent internalStatisticEvent) {
        putEvents(Collections.singletonList(internalStatisticEvent), LOG_ONLY_EXCEPTION_HANDLER);
    }

    default void putEvent(final InternalStatisticEvent internalStatisticEvent, final Consumer<Throwable> exceptionHandler) {
        putEvents(Collections.singletonList(internalStatisticEvent), exceptionHandler);
    }

    default void putEvents(List<InternalStatisticEvent> statisticEvents) {
        putEvents(statisticEvents, LOG_ONLY_EXCEPTION_HANDLER);
    }

    default BatchBuilder batchBuilder() {
        return new BatchBuilder(this);
    }

    void putEvents(List<InternalStatisticEvent> statisticEvents, Consumer<Throwable> exceptionHandler);

    class BatchBuilder {

        private final InternalStatisticsFacade internalStatisticsFacade;
        private List<InternalStatisticEvent> events = new ArrayList<>();
        private Consumer<Throwable> exceptionHandler = null;

        public BatchBuilder(InternalStatisticsFacade internalStatisticsFacade) {
            this.internalStatisticsFacade = internalStatisticsFacade;
        }

        public BatchBuilder addEvent(final InternalStatisticEvent event) {
            events.add(Preconditions.checkNotNull(event));

            return this;
        }

        public BatchBuilder addExceptionHandler(Consumer<Throwable> handler) {
            exceptionHandler = Preconditions.checkNotNull(handler);
            return this;
        }

        public void putBatch() {
            internalStatisticsFacade.putEvents(events, exceptionHandler == null ? LOG_ONLY_EXCEPTION_HANDLER : exceptionHandler);
        }
    }
}
