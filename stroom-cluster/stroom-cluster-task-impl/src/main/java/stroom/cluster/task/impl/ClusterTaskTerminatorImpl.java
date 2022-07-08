package stroom.cluster.task.impl;

import stroom.cluster.api.ClusterService;
import stroom.cluster.task.api.ClusterTaskTerminator;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.shared.FindTaskCriteria;
import stroom.task.shared.TaskId;
import stroom.task.shared.TaskResource;
import stroom.task.shared.TerminateTaskProgressRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.inject.Inject;

public class ClusterTaskTerminatorImpl implements ClusterTaskTerminator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterTaskTerminatorImpl.class);

    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final ClusterService clusterService;
    private final TaskResource taskResource;
    private final SecurityContext securityContext;

    @Inject
    ClusterTaskTerminatorImpl(final Executor executor,
                              final TaskContextFactory taskContextFactory,
                              final ClusterService clusterService,
                              final TaskResource taskResource,
                              final SecurityContext securityContext) {
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.clusterService = clusterService;
        this.taskResource = taskResource;
        this.securityContext = securityContext;
    }

    @Override
    public void terminate(final String searchName, final TaskId ancestorId, final String taskName) {
        final FindTaskCriteria findTaskCriteria = new FindTaskCriteria();
        findTaskCriteria.addAncestorId(ancestorId);

        // We have to wrap the cluster termination task in another task or
        // ClusterDispatchAsyncImpl
        // will not execute it if the parent task is terminated.
        CompletableFuture.runAsync(() ->
                securityContext.asProcessingUser(() ->
                        terminate(findTaskCriteria, searchName, taskName)), executor);
    }

    private void terminate(final FindTaskCriteria findTaskCriteria,
                           final String searchName,
                           final String taskName) {
        final TerminateTaskProgressRequest terminateTaskProgressRequest = new TerminateTaskProgressRequest(
                findTaskCriteria);

        taskContextFactory.context("Terminate: " + taskName, parentContext -> {
            parentContext.info(() -> searchName + " - terminating child tasks");

            try {
                // Get the nodes that we are going to send the entity event to.
                final Set<String> targetNodes = clusterService.getMembers();

                // Only send the event to remote nodes and not this one.
                // Send the entity event.
                targetNodes.forEach(nodeName -> {
                    final Runnable runnable = taskContextFactory.context(
                            "Terminate '" + taskName + "' on node '" + nodeName + "'",
                            taskContext -> {
                                try {
                                    final Boolean response = taskResource.terminate(
                                            nodeName, terminateTaskProgressRequest);
                                    if (!Boolean.TRUE.equals(response)) {
                                        LOGGER.warn("Failed tp terminate task '" + taskName + "'");
                                    }
                                } catch (final RuntimeException e) {
                                    LOGGER.warn(e.getMessage());
                                    LOGGER.debug(e.getMessage(), e);
                                }
                            });
                    CompletableFuture.runAsync(runnable, executor);
                });
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }).run();
    }
}
