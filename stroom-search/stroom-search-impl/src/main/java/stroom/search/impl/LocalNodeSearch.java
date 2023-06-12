package stroom.search.impl;

import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.ResultStore;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;

public class LocalNodeSearch implements NodeSearch {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LocalNodeSearch.class);

    private final NodeSearchTaskHandlers nodeSearchTaskHandlers;
    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;

    @Inject
    public LocalNodeSearch(final NodeSearchTaskHandlers nodeSearchTaskHandlers,
                           final SecurityContext securityContext,
                           final TaskContextFactory taskContextFactory) {
        this.nodeSearchTaskHandlers = nodeSearchTaskHandlers;
        this.securityContext = securityContext;
        this.taskContextFactory = taskContextFactory;
    }

    @Override
    public void searchNode(final String sourceNode,
                           final String targetNode,
                           final FederatedSearchTask task,
                           final NodeSearchTask nodeSearchTask,
                           final TaskContext parentContext) {
        LOGGER.debug(() -> task.getSearchName() + " - searching node: " + targetNode + "...");
        parentContext.info(() -> task.getSearchName() + " - searching node: " + targetNode + "...");
        final ResultStore resultStore = task.getResultStore();

        // Start local cluster search execution.
        final Coprocessors coprocessors = resultStore.getCoprocessors();
        LOGGER.debug(() -> "Dispatching node search task to node: " + targetNode);
        try {
            LOGGER.debug(() -> "startSearch " + nodeSearchTask);
            securityContext.useAsRead(() -> {
                if (coprocessors != null && coprocessors.size() > 0) {
                    final NodeSearchTaskHandler nodeSearchTaskHandler =
                            nodeSearchTaskHandlers.get(nodeSearchTask.getType());

                    // Add a child context just to get the same indentation level for local and remote search tasks.
                    taskContextFactory.childContext(
                            parentContext,
                            nodeSearchTask.getTaskName(),
                            TerminateHandlerFactory.NOOP_FACTORY,
                            taskContext ->
                                    nodeSearchTaskHandler.search(taskContext,
                                            nodeSearchTask,
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
            resultStore.onFailure(targetNode, searchException);
            throw searchException;
        }
    }
}
