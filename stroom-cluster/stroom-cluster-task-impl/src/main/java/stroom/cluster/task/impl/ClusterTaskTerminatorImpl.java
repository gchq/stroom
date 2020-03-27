package stroom.cluster.task.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cluster.task.api.ClusterTaskTerminator;
import stroom.cluster.task.api.NodeNotFoundException;
import stroom.cluster.task.api.NullClusterStateException;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.shared.FindTaskCriteria;
import stroom.task.shared.TaskId;
import stroom.task.shared.TaskResource;
import stroom.task.shared.TerminateTaskProgressRequest;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class ClusterTaskTerminatorImpl implements ClusterTaskTerminator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterTaskTerminatorImpl.class);

    private final Executor executor;
    private final Provider<TaskContext> taskContextProvider;
    private final TargetNodeSetFactory targetNodeSetFactory;
    private final TaskResource taskResource;
    private final SecurityContext securityContext;

    @Inject
    ClusterTaskTerminatorImpl(final Executor executor,
                              final Provider<TaskContext> taskContextProvider,
                              final TargetNodeSetFactory targetNodeSetFactory,
                              final TaskResource taskResource,
                              final SecurityContext securityContext) {
        this.executor = executor;
        this.taskContextProvider = taskContextProvider;
        this.targetNodeSetFactory = targetNodeSetFactory;
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
        CompletableFuture.runAsync(()
                -> securityContext.asProcessingUser(()
                -> terminate(findTaskCriteria, searchName, taskName)), executor);
    }

    private void terminate(final FindTaskCriteria findTaskCriteria, final String searchName, final String taskName) {
        final TerminateTaskProgressRequest terminateTaskProgressRequest = new TerminateTaskProgressRequest(findTaskCriteria, false);

        final TaskContext taskContext = taskContextProvider.get();
        taskContext.setName("Terminate: " + taskName);
        taskContext.info(() -> searchName + " - terminating child tasks");

        try {
            // Get the nodes that we are going to send the entity event to.
            final Set<String> targetNodes = targetNodeSetFactory.getEnabledActiveTargetNodeSet();

            // Only send the event to remote nodes and not this one.
            // Send the entity event.
            targetNodes.forEach(nodeName -> {
                Runnable runnable = () -> {
                    try {
                        taskContext.setName("Terminate '" + taskName + "' on node '" + nodeName + "'");
                        final Boolean response = taskResource.terminate(nodeName, terminateTaskProgressRequest);
                        if (!Boolean.TRUE.equals(response)) {
                            LOGGER.warn("Failed tp terminate task '" + taskName + "'");
                        }
                    } catch (final RuntimeException e) {
                        LOGGER.warn(e.getMessage());
                        LOGGER.debug(e.getMessage(), e);
                    }
                };
                runnable = taskContext.sub(runnable);
                CompletableFuture.runAsync(runnable, executor);
            });

        } catch (final NullClusterStateException | NodeNotFoundException e) {
            LOGGER.warn(e.getMessage());
            LOGGER.debug(e.getMessage(), e);
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}
