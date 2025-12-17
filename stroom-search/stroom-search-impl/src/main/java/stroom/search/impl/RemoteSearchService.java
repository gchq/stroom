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
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.CoprocessorsImpl;
import stroom.query.common.v2.DataStoreSettings;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.api.TerminateHandlerFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.Severity;
import stroom.util.string.ExceptionStringUtil;

import com.esotericsoftware.kryo.KryoException;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class RemoteSearchService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RemoteSearchService.class);

    private final RemoteSearchResults remoteSearchResults;
    private final TaskManager taskManager;
    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final NodeSearchTaskHandlers nodeSearchTaskHandlers;
    private final CoprocessorsFactory coprocessorsFactory;
    private final SecurityContext securityContext;

    private CoprocessorsImpl coprocessors;

    @Inject
    public RemoteSearchService(final RemoteSearchResults remoteSearchResults,
                               final TaskManager taskManager,
                               final ExecutorProvider executorProvider,
                               final TaskContextFactory taskContextFactory,
                               final NodeSearchTaskHandlers nodeSearchTaskHandlers,
                               final CoprocessorsFactory coprocessorsFactory,
                               final SecurityContext securityContext) {
        this.remoteSearchResults = remoteSearchResults;
        this.taskManager = taskManager;
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.nodeSearchTaskHandlers = nodeSearchTaskHandlers;
        this.coprocessorsFactory = coprocessorsFactory;
        this.securityContext = securityContext;
    }

    public Boolean start(final NodeSearchTask nodeSearchTask) {
        LOGGER.debug(() -> "startSearch " + nodeSearchTask);
        final RemoteSearchResultFactory remoteSearchResultFactory
                = new RemoteSearchResultFactory(taskManager, securityContext);
        remoteSearchResults.put(nodeSearchTask.getKey().getUuid(), remoteSearchResultFactory);

        // Create coprocessors.
        securityContext.useAsRead(() -> {
            try {
                final Query query = nodeSearchTask.getQuery();

                // Make sure we have been given a query.
                if (query.getExpression() == null) {
                    throw new SearchException("Search expression has not been set");
                }

                coprocessors = coprocessorsFactory.create(
                        nodeSearchTask.getSearchRequestSource(),
                        nodeSearchTask.getDateTimeSettings(),
                        nodeSearchTask.getKey(),
                        nodeSearchTask.getSettings(),
                        query.getParams(),
                        DataStoreSettings.createPayloadProducerSearchResultStoreSettings());
                remoteSearchResultFactory.setCoprocessors(coprocessors);

                if (coprocessors != null && coprocessors.isPresent()) {
                    final NodeSearchTaskHandler nodeSearchTaskHandler =
                            nodeSearchTaskHandlers.get(nodeSearchTask.getType());
                    final CountDownLatch countDownLatch = new CountDownLatch(1);
                    final Runnable runnable = taskContextFactory.context(
                            nodeSearchTask.getTaskName(),
                            TerminateHandlerFactory.NOOP_FACTORY,
                            taskContext -> {
                                try {
                                    taskContext.getTaskId().setParentId(nodeSearchTask.getSourceTaskId());
                                    remoteSearchResultFactory.setTaskId(taskContext.getTaskId());
                                    remoteSearchResultFactory.setStarted(true);
                                    countDownLatch.countDown();

                                    nodeSearchTaskHandler.search(
                                            taskContext,
                                            nodeSearchTask,
                                            coprocessors);

                                } catch (final RuntimeException e) {
                                    coprocessors.getErrorConsumer().add(e);

                                } finally {
                                    try {
                                        // Tell the coprocessors they can complete now as we won't be receiving
                                        // payloads.
                                        LOGGER.trace(() -> "Search is complete, setting searchComplete to true and " +
                                                           "counting down searchCompleteLatch");
                                        coprocessors.getCompletionState().signalComplete();

                                        // Wait for the coprocessors to actually complete, i.e. consume last items from
                                        // the queue and send last payloads etc.
                                        while (!taskContext.isTerminated() &&
                                               !coprocessors.getCompletionState().awaitCompletion(1,
                                                       TimeUnit.SECONDS)) {
                                            nodeSearchTaskHandler.updateInfo();
                                        }

                                    } catch (final InterruptedException e) {
                                        LOGGER.trace(e::getMessage, e);
                                        // Keep interrupting this thread.
                                        Thread.currentThread().interrupt();

                                    } finally {
                                        coprocessors.clear();
                                    }
                                }
                            });

                    final Executor executor = executorProvider.get();
                    CompletableFuture.runAsync(runnable, executor);

                    // Ensure the process starts before we return.
                    try {
                        countDownLatch.await();
                    } catch (final InterruptedException e) {
                        LOGGER.trace(e.getMessage(), e);
                        // Keep interrupting this thread.
                        Thread.currentThread().interrupt();
                    }

                } else {
                    remoteSearchResultFactory.setInitialisationError(
                            Collections.singletonList(new ErrorMessage(Severity.ERROR,
                                    "No coprocessors were created")));
                }

            } catch (final RuntimeException e) {
                remoteSearchResultFactory.setInitialisationError(
                        Collections.singletonList(new ErrorMessage(Severity.ERROR,
                                ExceptionStringUtil.getMessage(e))));
            }
        });

        return true;
    }

    public void poll(final String queryKey, final OutputStream outputStream) throws IOException {
        try {
            LOGGER.debug(() -> "poll " + queryKey);
            final Optional<RemoteSearchResultFactory> optional = remoteSearchResults.get(queryKey);

            if (optional.isPresent()) {
                final RemoteSearchResultFactory factory = optional.get();
                factory.write(outputStream);

            } else {
                // There aren't any results in the cache so the search is probably dead
                LOGGER.error("Expected search results in cache for " + queryKey);
                throw new RuntimeException("Expected search results in cache for " + queryKey);
            }

            outputStream.flush();
            outputStream.close();
        } catch (final KryoException e) {
            // Expected as sometimes the output stream is closed by the receiving node.
            LOGGER.debug(e::getMessage, e);
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }
    }

    public Boolean destroy(final String queryKey) {
        LOGGER.debug(() -> "destroy " + queryKey);
        remoteSearchResults.invalidate(queryKey);
        return true;
    }
}
