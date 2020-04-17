package stroom.core.entity.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cluster.task.api.NodeNotFoundException;
import stroom.cluster.task.api.NullClusterStateException;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContextFactory;

import javax.inject.Inject;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class ClearableService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClearableService.class);

    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final TargetNodeSetFactory targetNodeSetFactory;
    private final SecurityContext securityContext;
    private final ClearEventHandler clearEventHandler;
    private final ClearableResource clearableResource;

    @Inject
    ClearableService(final Executor executor,
                     final TaskContextFactory taskContextFactory,
                     final TargetNodeSetFactory targetNodeSetFactory,
                     final SecurityContext securityContext,
                     final ClearEventHandler clearEventHandler,
                     final ClearableResource clearableResource) {
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.targetNodeSetFactory = targetNodeSetFactory;
        this.securityContext = securityContext;
        this.clearEventHandler = clearEventHandler;
        this.clearableResource = clearableResource;
    }

    public void clearAll() {
        try {
            // Force a local update so that changes are immediately reflected
            // for the current user.
            clearEventHandler.clearLocally();

            // Dispatch the entity event to all nodes in the cluster.
            final Runnable runnable = taskContextFactory.context("Clear All", taskContext -> fireRemote());
            CompletableFuture.runAsync(runnable, executor);
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private void fireRemote() {
        securityContext.secure(() -> {
            try {
                // Get this node.
                final String sourceNode = targetNodeSetFactory.getSourceNode();

                // Get the nodes that we are going to send the entity event to.
                final Set<String> targetNodes = targetNodeSetFactory.getEnabledActiveTargetNodeSet();

                // Only send the event to remote nodes and not this one.
                // Send the entity event.
                targetNodes.stream().filter(targetNode ->
                        !targetNode.equals(sourceNode)).forEach(clearableResource::clear);
            } catch (final NullClusterStateException | NodeNotFoundException e) {
                LOGGER.warn(e.getMessage());
                LOGGER.debug(e.getMessage(), e);
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }
}
