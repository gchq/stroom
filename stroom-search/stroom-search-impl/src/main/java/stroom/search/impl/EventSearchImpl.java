/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.search.impl;

import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.SearchRequestSource.SourceType;
import stroom.query.common.v2.EventRef;
import stroom.query.common.v2.EventRefs;
import stroom.query.common.v2.EventSearch;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

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
