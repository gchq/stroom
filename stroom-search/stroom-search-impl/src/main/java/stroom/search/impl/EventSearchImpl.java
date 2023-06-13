package stroom.search.impl;

import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequestSource;
import stroom.query.api.v2.SearchRequestSource.SourceType;
import stroom.query.common.v2.EventRef;
import stroom.query.common.v2.EventRefs;
import stroom.query.common.v2.EventSearch;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import javax.inject.Inject;
import javax.inject.Provider;

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
    public CompletableFuture<Void> search(final TaskContext parentTaskContext,
                                          final Query query,
                                          final EventRef minEvent,
                                          final EventRef maxEvent,
                                          final long maxStreams,
                                          final long maxEvents,
                                          final long maxEventsPerStream,
                                          final BiConsumer<EventRefs, Throwable> consumer) {
        final SearchRequestSource searchRequestSource = SearchRequestSource
                .builder()
                .sourceType(SourceType.BATCH_SEARCH)
                .build();
        final QueryKey key = new QueryKey(UUID.randomUUID().toString());
        final EventSearchTask eventSearchTask = new EventSearchTask(
                searchRequestSource,
                key,
                query,
                minEvent,
                maxEvent,
                maxStreams,
                maxEvents,
                maxEventsPerStream);

        final Runnable runnable = taskContextFactory.childContext(
                parentTaskContext,
                "Event Search",
                taskContext -> {
                    final EventSearchTaskHandler eventSearchTaskHandler = eventSearchTaskHandlerProvider.get();
                    eventSearchTaskHandler.exec(eventSearchTask, consumer);
                });

        // The downside to doing this async is that we may end up creating more tasks than the number
        // required

        return CompletableFuture.runAsync(runnable, executor);
    }
}
