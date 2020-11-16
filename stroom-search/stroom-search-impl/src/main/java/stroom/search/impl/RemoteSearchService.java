package stroom.search.impl;

import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class RemoteSearchService {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RemoteSearchService.class);

    private final RemoteSearchResults remoteSearchResults;
    private final TaskManager taskManager;
    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final Provider<ClusterSearchTaskHandler> clusterSearchTaskHandlerProvider;

    @Inject
    public RemoteSearchService(final RemoteSearchResults remoteSearchResults,
                               final TaskManager taskManager,
                               final ExecutorProvider executorProvider,
                               final TaskContextFactory taskContextFactory,
                               final Provider<ClusterSearchTaskHandler> clusterSearchTaskHandlerProvider) {
        this.remoteSearchResults = remoteSearchResults;
        this.taskManager = taskManager;
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.clusterSearchTaskHandlerProvider = clusterSearchTaskHandlerProvider;
    }

    public Boolean start(final ClusterSearchTask clusterSearchTask) {
        LOGGER.debug(() -> "startSearch " + clusterSearchTask);
        final RemoteSearchResultFactory remoteSearchResultFactory = new RemoteSearchResultFactory(taskManager);
        remoteSearchResults.put(clusterSearchTask.getKey().getUuid(), remoteSearchResultFactory);

        final Runnable runnable = taskContextFactory.context(clusterSearchTask.getTaskName(), taskContext -> {
            taskContext.getTaskId().setParentId(clusterSearchTask.getSourceTaskId());
            final ClusterSearchTaskHandler clusterSearchTaskHandler = clusterSearchTaskHandlerProvider.get();
            clusterSearchTaskHandler.exec(taskContext, clusterSearchTask, remoteSearchResultFactory);
        });

        final Executor executor = executorProvider.get();
        CompletableFuture.runAsync(runnable, executor);

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
