package stroom.search.impl;

import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.common.v2.EventRef;
import stroom.query.common.v2.EventRefs;
import stroom.query.common.v2.EventSearch;
import stroom.task.api.TaskContextFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

class EventSearchImpl implements EventSearch {
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
        final QueryKey key = new QueryKey(UUID.randomUUID().toString());
        final EventSearchTask eventSearchTask = new EventSearchTask(
                key,
                query,
                minEvent,
                maxEvent,
                maxStreams,
                maxEvents,
                maxEventsPerStream);
        final Supplier<EventRefs> supplier = taskContextFactory.contextResult("Event Search", taskContext -> {
            final EventSearchTaskHandler eventSearchTaskHandler = eventSearchTaskHandlerProvider.get();
            return eventSearchTaskHandler.exec(eventSearchTask);
        });
        CompletableFuture
                .supplyAsync(supplier, executor)
                .whenComplete((r, t) -> consumer.accept(r));
    }
}
