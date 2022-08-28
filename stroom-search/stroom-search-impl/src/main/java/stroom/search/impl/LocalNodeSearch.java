package stroom.search.impl;

import stroom.query.api.v2.Query;
import stroom.query.common.v2.Coprocessors;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;

public class LocalNodeSearch implements NodeSearch {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LocalNodeSearch.class);

    private final Provider<ClusterSearchTaskHandler> clusterSearchTaskHandlerProvider;
    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;

    @Inject
    public LocalNodeSearch(final Provider<ClusterSearchTaskHandler> clusterSearchTaskHandlerProvider,
                           final SecurityContext securityContext,
                           final TaskContextFactory taskContextFactory) {
        this.clusterSearchTaskHandlerProvider = clusterSearchTaskHandlerProvider;
        this.securityContext = securityContext;
        this.taskContextFactory = taskContextFactory;
    }

    public void searchNode(final String sourceNode,
                           final String targetNode,
                           final List<Long> shards,
                           final AsyncSearchTask task,
                           final Query query,
                           final TaskContext parentContext) {
        LOGGER.debug(() -> task.getSearchName() + " - searching node: " + targetNode + "...");
        parentContext.info(() -> task.getSearchName() + " - searching node: " + targetNode + "...");
        final ClusterSearchResultCollector resultCollector = task.getResultCollector();

        // Start local cluster search execution.
        final ClusterSearchTask clusterSearchTask = new ClusterSearchTask(
                parentContext.getTaskId(),
                "Cluster Search",
                task.getKey(),
                query,
                shards,
                task.getSettings(),
                task.getDateTimeSettings(),
                task.getNow());
        final Coprocessors coprocessors = resultCollector.getCoprocessors();
        LOGGER.debug(() -> "Dispatching clusterSearchTask to node: " + targetNode);
        try {
            LOGGER.debug(() -> "startSearch " + clusterSearchTask);
            securityContext.useAsRead(() -> {

                // Make sure we have been given a query.
                if (query.getExpression() == null) {
                    throw new SearchException("Search expression has not been set");
                }

                if (coprocessors != null && coprocessors.size() > 0) {
                    final ClusterSearchTaskHandler clusterSearchTaskHandler =
                            clusterSearchTaskHandlerProvider.get();

                    // Add a child context just to get the same indentation level for local and remote search tasks.
                    taskContextFactory.childContext(
                            parentContext,
                            clusterSearchTask.getTaskName(),
                            TerminateHandlerFactory.NOOP_FACTORY,
                            taskContext ->
                            clusterSearchTaskHandler.search(taskContext,
                                    clusterSearchTask,
                                    coprocessors)).run();

                } else {
                    throw new SearchException("No coprocessors were created");
                }
            });
        } catch (final Throwable e) {
            LOGGER.debug(e::getMessage, e);
            LOGGER.debug(() -> "Failed to start local search on node: " + targetNode);
            final SearchException searchException = new SearchException(
                    "Failed to start local search on node: " + targetNode, e);
            resultCollector.onFailure(targetNode, searchException);
            throw searchException;
        }
    }
}
