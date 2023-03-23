package stroom.search.impl;

import stroom.query.api.v2.Query;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.DataStoreSettings;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.api.TerminateHandlerFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.string.ExceptionStringUtil;

import com.esotericsoftware.kryo.KryoException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Provider;

public class RemoteSearchService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RemoteSearchService.class);

    private final RemoteSearchResults remoteSearchResults;
    private final TaskManager taskManager;
    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final Provider<ClusterSearchTaskHandler> clusterSearchTaskHandlerProvider;
    private final CoprocessorsFactory coprocessorsFactory;
    private final SecurityContext securityContext;

    private Coprocessors coprocessors;

    @Inject
    public RemoteSearchService(final RemoteSearchResults remoteSearchResults,
                               final TaskManager taskManager,
                               final ExecutorProvider executorProvider,
                               final TaskContextFactory taskContextFactory,
                               final Provider<ClusterSearchTaskHandler> clusterSearchTaskHandlerProvider,
                               final CoprocessorsFactory coprocessorsFactory,
                               final SecurityContext securityContext) {
        this.remoteSearchResults = remoteSearchResults;
        this.taskManager = taskManager;
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.clusterSearchTaskHandlerProvider = clusterSearchTaskHandlerProvider;
        this.coprocessorsFactory = coprocessorsFactory;
        this.securityContext = securityContext;
    }

    public Boolean start(final ClusterSearchTask clusterSearchTask) {
        LOGGER.debug(() -> "startSearch " + clusterSearchTask);
        final RemoteSearchResultFactory remoteSearchResultFactory
                = new RemoteSearchResultFactory(taskManager, securityContext);
        remoteSearchResults.put(clusterSearchTask.getKey().getUuid(), remoteSearchResultFactory);

        // Create coprocessors.
        securityContext.useAsRead(() -> {
            try {
                final Query query = clusterSearchTask.getQuery();

                // Make sure we have been given a query.
                if (query.getExpression() == null) {
                    throw new SearchException("Search expression has not been set");
                }

                coprocessors = coprocessorsFactory.create(
                        clusterSearchTask.getKey(),
                        clusterSearchTask.getSettings(),
                        query.getParams(),
                        DataStoreSettings.PAYLOAD_PRODUCER_SETTINGS);
                remoteSearchResultFactory.setCoprocessors(coprocessors);

                if (coprocessors != null && coprocessors.size() > 0) {
                    final ClusterSearchTaskHandler clusterSearchTaskHandler =
                            clusterSearchTaskHandlerProvider.get();
                    final CountDownLatch countDownLatch = new CountDownLatch(1);
                    final Runnable runnable = taskContextFactory.context(
                            clusterSearchTask.getTaskName(),
                            TerminateHandlerFactory.NOOP_FACTORY,
                            taskContext -> {
                                try {
                                    taskContext.getTaskId().setParentId(clusterSearchTask.getSourceTaskId());
                                    remoteSearchResultFactory.setTaskId(taskContext.getTaskId());
                                    remoteSearchResultFactory.setStarted(true);
                                    countDownLatch.countDown();

                                    clusterSearchTaskHandler.search(
                                            taskContext,
                                            clusterSearchTask,
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
                                        // the queue and send laST payloads etc.
                                        while (!coprocessors.getCompletionState().awaitCompletion(1,
                                                TimeUnit.SECONDS)) {
                                            clusterSearchTaskHandler.updateInfo();
                                        }
                                    } catch (final InterruptedException e) {
                                        LOGGER.trace(e::getMessage, e);
                                        // Keep interrupting this thread.
                                        Thread.currentThread().interrupt();
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
                    remoteSearchResultFactory.setInitialisationError(Collections.singletonList(
                            "No coprocessors were created"));
                }

            } catch (final RuntimeException e) {
                remoteSearchResultFactory.setInitialisationError(Collections.singletonList(
                        ExceptionStringUtil.getMessage(e)));
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
