package stroom.search.impl;

import stroom.query.api.v2.Query;
import stroom.query.common.v2.Coprocessors;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Provider;

public class LocalNodeSearch implements NodeSearch {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LocalNodeSearch.class);

    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final Provider<ClusterSearchTaskHandler> clusterSearchTaskHandlerProvider;
    private final SecurityContext securityContext;

    @Inject
    public LocalNodeSearch(final ExecutorProvider executorProvider,
                           final TaskContextFactory taskContextFactory,
                           final Provider<ClusterSearchTaskHandler> clusterSearchTaskHandlerProvider,
                           final SecurityContext securityContext) {
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.clusterSearchTaskHandlerProvider = clusterSearchTaskHandlerProvider;
        this.securityContext = securityContext;
    }

    public void searchNode(final String sourceNode,
                           final String targetNode,
                           final List<Long> shards,
                           final AsyncSearchTask task,
                           final Query query,
                           final TaskContext taskContext) {
        LOGGER.debug(() -> task.getSearchName() + " - start searching node: " + targetNode);
        taskContext.info(() -> task.getSearchName() + " - start searching node: " + targetNode);
        final ClusterSearchResultCollector resultCollector = task.getResultCollector();

        // Start local cluster search execution.
        final ClusterSearchTask clusterSearchTask = new ClusterSearchTask(
                taskContext.getTaskId(),
                "Cluster Search",
                task.getKey(),
                query,
                shards,
                task.getSettings(),
                task.getDateTimeLocale(),
                task.getNow());
        final Coprocessors coprocessors = resultCollector.getCoprocessors();
        LOGGER.debug(() -> "Dispatching clusterSearchTask to node: " + targetNode);
        try {
            final boolean success = start(clusterSearchTask, coprocessors);
            if (!success) {
                LOGGER.debug(() -> "Failed to start local search on node: " + targetNode);
                final SearchException searchException = new SearchException(
                        "Failed to start local search on node: " + targetNode);
                resultCollector.onFailure(targetNode, searchException);
                throw searchException;
            }
        } catch (final Throwable e) {
            LOGGER.debug(e::getMessage, e);
            final SearchException searchException = new SearchException(e.getMessage(), e);
            resultCollector.onFailure(targetNode, searchException);
            throw searchException;
        }

        try {
            LOGGER.debug(() -> task.getSearchName() + " - searching node: " + targetNode + "...");
            taskContext.info(() -> task.getSearchName() + " - searching node: " + targetNode + "...");

            // Poll for results until completion.
            boolean complete = false;
            while (!Thread.currentThread().isInterrupted() && !complete) {
                try {
                    try {
                        complete = coprocessors.getCompletionState().awaitCompletion(1,
                                TimeUnit.MINUTES);
                    } catch (final InterruptedException e) {
                        LOGGER.debug(e.getMessage(), e);
                        complete = true;
                        Thread.currentThread().interrupt();
                    }
                } catch (final RuntimeException e) {
                    complete = true;
                    resultCollector.onFailure(targetNode, e);
                }
            }

        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            resultCollector.onFailure(sourceNode, e);

        } finally {
            LOGGER.debug(() -> task.getSearchName() + " - finished searching node: " + targetNode);
            taskContext.info(() -> task.getSearchName() + " - finished searching node: " + targetNode);
        }
    }

    public Boolean start(final ClusterSearchTask clusterSearchTask, final Coprocessors coprocessors) {
        LOGGER.debug(() -> "startSearch " + clusterSearchTask);

        securityContext.useAsRead(() -> {
            final Query query = clusterSearchTask.getQuery();

            // Make sure we have been given a query.
            if (query.getExpression() == null) {
                throw new SearchException("Search expression has not been set");
            }

            if (coprocessors != null && coprocessors.size() > 0) {
                final Runnable runnable = taskContextFactory.context(clusterSearchTask.getTaskName(),
                        taskContext -> {
                            taskContext.getTaskId().setParentId(clusterSearchTask.getSourceTaskId());
                            final ClusterSearchTaskHandler clusterSearchTaskHandler =
                                    clusterSearchTaskHandlerProvider.get();
                            clusterSearchTaskHandler.exec(taskContext,
                                    clusterSearchTask,
                                    coprocessors);
                        });

                final Executor executor = executorProvider.get();
                CompletableFuture.runAsync(runnable, executor);

            } else {
                throw new SearchException("No coprocessors were created");
            }
        });

        return true;
    }
}
