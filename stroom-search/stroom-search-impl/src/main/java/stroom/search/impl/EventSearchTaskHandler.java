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

import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.Query;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.CoprocessorsImpl;
import stroom.query.common.v2.DataStoreSettings;
import stroom.query.common.v2.EventCoprocessor;
import stroom.query.common.v2.EventCoprocessorSettings;
import stroom.query.common.v2.EventRefs;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.ResultStoreFactory;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ErrorMessage;

import jakarta.inject.Inject;

import java.util.Collections;
import java.util.function.BiConsumer;


public class EventSearchTaskHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EventSearchTaskHandler.class);

    private final SecurityContext securityContext;
    private final CoprocessorsFactory coprocessorsFactory;
    private final ResultStoreFactory resultStoreFactory;
    private final FederatedSearchExecutor federatedSearchExecutor;
    private final NodeSearchTaskCreator nodeSearchTaskCreator;

    @Inject
    EventSearchTaskHandler(final SecurityContext securityContext,
                           final CoprocessorsFactory coprocessorsFactory,
                           final ResultStoreFactory resultStoreFactory,
                           final FederatedSearchExecutor federatedSearchExecutor,
                           final NodeSearchTaskCreator nodeSearchTaskCreator) {
        this.securityContext = securityContext;
        this.coprocessorsFactory = coprocessorsFactory;
        this.resultStoreFactory = resultStoreFactory;
        this.federatedSearchExecutor = federatedSearchExecutor;
        this.nodeSearchTaskCreator = nodeSearchTaskCreator;
    }

    public void exec(final EventSearchTask task,
                     final BiConsumer<EventRefs, Throwable> consumer) {
        securityContext.secure(() -> {
            EventRefs eventRefs = null;
            Throwable throwable = null;

            try {
                // Get the current time in millis since epoch.
                final DateTimeSettings dateTimeSettings = DateTimeSettings.builder().build();

                // Get the search.
                final Query query = task.getQuery();

                // Replace expression parameters.
                final Query modifiedQuery = ExpressionUtil.replaceExpressionParameters(query);

                final int coprocessorId = 0;
                final EventCoprocessorSettings settings = new EventCoprocessorSettings(
                        coprocessorId,
                        task.getMinEvent(),
                        task.getMaxEvent(),
                        task.getMaxStreams(),
                        task.getMaxEvents(),
                        task.getMaxEventsPerStream());

                // Create an asynchronous search task.
                final String searchName = "Event Search";
                final FederatedSearchTask federatedSearchTask = new FederatedSearchTask(
                        task.getSearchRequestSource(),
                        task.getKey(),
                        searchName,
                        modifiedQuery,
                        Collections.singletonList(settings),
                        dateTimeSettings);

                final CoprocessorsImpl coprocessors = coprocessorsFactory.create(
                        task.getSearchRequestSource(),
                        dateTimeSettings,
                        task.getKey(),
                        Collections.singletonList(settings),
                        modifiedQuery.getParams(),
                        DataStoreSettings.createBasicSearchResultStoreSettings());
                final EventCoprocessor eventCoprocessor = (EventCoprocessor) coprocessors.get(coprocessorId);

                // Create the search result collector.
                final ResultStore resultStore = resultStoreFactory.create(
                        null,
                        coprocessors);
                try {
                    federatedSearchExecutor.start(federatedSearchTask, resultStore, nodeSearchTaskCreator);

                    LOGGER.debug(() -> "Started searchResultCollector " + resultStore);

                    // Wait for completion or termination
                    resultStore.awaitCompletion();

                    eventRefs = eventCoprocessor.getEventRefs();
                    if (eventRefs != null) {
                        eventRefs.trim();
                    }

                    if (eventCoprocessor.getErrorConsumer().hasErrors()) {
                        final String errors = String.join("\n", eventCoprocessor
                                .getErrorConsumer()
                                .getErrorMessages().stream()
                                .map(ErrorMessage::getMessage)
                                .toList());
                        LOGGER.debug(errors);
                        throwable = new RuntimeException(errors);
                    }
                } catch (final InterruptedException e) {
                    // Continue to interrupt this thread.
                    Thread.currentThread().interrupt();

                    //Don't want to reset interrupt status as this thread will go back into
                    //the executor's pool. Throwing an exception will terminate the task
                    throw new RuntimeException(
                            LogUtil.message("Thread {} interrupted executing task {}",
                                    Thread.currentThread().getName(), task));
                } finally {
                    resultStore.destroy();
                }
            } finally {
                consumer.accept(eventRefs, throwable);
            }
        });
    }
}
