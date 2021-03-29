package stroom.search.impl;

import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.common.v2.EventRef;
import stroom.query.common.v2.EventRefs;
import stroom.query.common.v2.EventSearch;
import stroom.task.api.TaskContextFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Provider;

class EventSearchImpl implements EventSearch {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventSearchImpl.class);

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
    public CompletableFuture<EventRefs> search(final Query query,
                                               final EventRef minEvent,
                                               final EventRef maxEvent,
                                               final long maxStreams,
                                               final long maxEvents,
                                               final long maxEventsPerStream,
                                               final int resultSendFrequency,
                                               final Consumer<EventRefs> consumer) {

        final QueryKey key = new QueryKey(UUID.randomUUID().toString());
        final EventSearchTask eventSearchTask = new EventSearchTask(
                key,
                query,
                minEvent,
                maxEvent,
                maxStreams,
                maxEvents,
                maxEventsPerStream);

        final Supplier<EventRefs> supplier = taskContextFactory.contextResult(
                "Event Search",
                taskContext -> {
                    final EventSearchTaskHandler eventSearchTaskHandler = eventSearchTaskHandlerProvider.get();
                    return eventSearchTaskHandler.exec(eventSearchTask);
                });

        // The downside to doing this async is that we may end up creating more tasks than the number
        // required

        return CompletableFuture
                .supplyAsync(supplier, executor)
                .whenComplete((eventRefs, throwable) -> {
                    if (throwable != null) {
                        LOGGER.error("Error supplying eventRefs for query " + query, throwable);
                    } else if (eventRefs == null) {
                        LOGGER.debug("eventRefs is null for query " + query);
                    } else {
                        consumer.accept(eventRefs);
                    }
                });
    }
}
