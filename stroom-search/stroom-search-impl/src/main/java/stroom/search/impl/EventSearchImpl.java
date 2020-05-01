package stroom.search.impl;

import stroom.query.api.v2.Query;
import stroom.search.api.EventRef;
import stroom.search.api.EventRefs;
import stroom.search.api.EventSearch;
import stroom.task.api.TaskContextFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

class EventSearchImpl implements EventSearch {
    private static final int POLL_INTERVAL_MS = 10000;

    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final Provider<EventSearchTaskHandler> eventSearchTaskHandlerProvider;

    @Inject
    EventSearchImpl(final Executor executor,
                    final TaskContextFactory taskContextFactory,
                    final Provider<EventSearchTaskHandler> eventSearchTaskHandlerProvider) {
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.eventSearchTaskHandlerProvider = eventSearchTaskHandlerProvider;
    }

    @Override
    public void search(final Query query, final EventRef minEvent, final EventRef maxEvent, final long maxStreams, final long maxEvents, final long maxEventsPerStream, final int resultSendFrequency, final Consumer<EventRefs> consumer) {
        final EventSearchTask eventSearchTask = new EventSearchTask(query,
                minEvent, maxEvent, maxStreams, maxEvents, maxEventsPerStream, POLL_INTERVAL_MS);
        final Supplier<EventRefs> supplier = taskContextFactory.contextResult("Event Search", taskContext -> {
            final EventSearchTaskHandler eventSearchTaskHandler = eventSearchTaskHandlerProvider.get();
            return eventSearchTaskHandler.exec(eventSearchTask);
        });
        CompletableFuture
                .supplyAsync(supplier, executor)
                .whenComplete((r, t) -> consumer.accept(r));
    }
}
