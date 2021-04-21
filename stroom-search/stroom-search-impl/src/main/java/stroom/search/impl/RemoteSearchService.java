package stroom.search.impl;

import stroom.query.api.v2.Query;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
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
                        clusterSearchTask.getKey().getUuid(),
                        clusterSearchTask.getSettings(),
                        query.getParams());
                remoteSearchResultFactory.setCoprocessors(coprocessors);

                if (coprocessors != null && coprocessors.size() > 0) {
                    final Runnable runnable = taskContextFactory.context(clusterSearchTask.getTaskName(),
                            taskContext -> {
                                taskContext.getTaskId().setParentId(clusterSearchTask.getSourceTaskId());
                                final ClusterSearchTaskHandler clusterSearchTaskHandler =
                                        clusterSearchTaskHandlerProvider.get();
                                remoteSearchResultFactory.setTaskId(taskContext.getTaskId());
                                remoteSearchResultFactory.setStarted(true);
                                clusterSearchTaskHandler.exec(taskContext,
                                        clusterSearchTask,
                                        coprocessors);
                            });

                    final Executor executor = executorProvider.get();
                    CompletableFuture.runAsync(runnable, executor);

                } else {
                    remoteSearchResultFactory.setInitialisationError("No coprocessors were created");
                }

            } catch (final RuntimeException e) {
                remoteSearchResultFactory.setInitialisationError(e.getMessage());
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
//            try (final Output output = new Output(outputStream)) {
//                NodeResultSerialiser.writeEmptyResponse(output, true);
//            }
            }

            outputStream.flush();
            outputStream.close();
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
