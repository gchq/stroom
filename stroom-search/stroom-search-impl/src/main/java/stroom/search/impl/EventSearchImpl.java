package stroom.search.impl;

import stroom.query.api.v2.Query;
import stroom.search.api.EventRef;
import stroom.search.api.EventRefs;
import stroom.search.api.EventSearch;
import stroom.task.api.TaskContext;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

class EventSearchImpl implements EventSearch {
    private static final int POLL_INTERVAL_MS = 10000;

    private final Executor executor;
    private final Provider<TaskContext> taskContextProvider;
    private final Provider<EventSearchTaskHandler> eventSearchTaskHandlerProvider;

    @Inject
    EventSearchImpl(final Executor executor,
                    final Provider<TaskContext> taskContextProvider,
                    final Provider<EventSearchTaskHandler> eventSearchTaskHandlerProvider) {
        this.executor = executor;
        this.taskContextProvider = taskContextProvider;
        this.eventSearchTaskHandlerProvider = eventSearchTaskHandlerProvider;
    }

    @Override
    public void search(final Query query, final EventRef minEvent, final EventRef maxEvent, final long maxStreams, final long maxEvents, final long maxEventsPerStream, final int resultSendFrequency, final Consumer<EventRefs> consumer) {
        final EventSearchTask eventSearchTask = new EventSearchTask(query,
                minEvent, maxEvent, maxStreams, maxEvents, maxEventsPerStream, POLL_INTERVAL_MS);
        final TaskContext taskContext = taskContextProvider.get();
        Supplier<EventRefs> supplier = () -> {
            final EventSearchTaskHandler eventSearchTaskHandler = eventSearchTaskHandlerProvider.get();
            return eventSearchTaskHandler.exec(eventSearchTask);
        };
        supplier = taskContext.sub(supplier);
        CompletableFuture
                .supplyAsync(supplier, executor)
                .whenComplete((r, t) -> {
                    consumer.accept(r);
                });
    }
}
