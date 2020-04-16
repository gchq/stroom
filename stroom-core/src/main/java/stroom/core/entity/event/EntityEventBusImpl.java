/*
 * Copyright 2016 Crown Copyright
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

package stroom.core.entity.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cluster.task.api.NodeNotFoundException;
import stroom.cluster.task.api.NullClusterStateException;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Singleton
class EntityEventBusImpl implements EntityEventBus {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityEventBusImpl.class);

    private final Executor executor;
    private final Provider<TaskContext> taskContextProvider;
    private final TargetNodeSetFactory targetNodeSetFactory;
    private final SecurityContext securityContext;
    private final EntityEventHandler entityEventHandler;
    private final EntityEventResource entityEventResource;

    private volatile boolean started = false;

    @Inject
    EntityEventBusImpl(final Executor executor,
                       final Provider<TaskContext> taskContextProvider,
                       final TargetNodeSetFactory targetNodeSetFactory,
                       final SecurityContext securityContext,
                       final EntityEventHandler entityEventHandler,
                       final EntityEventResource entityEventResource) {
        this.executor = executor;
        this.taskContextProvider = taskContextProvider;
        this.targetNodeSetFactory = targetNodeSetFactory;
        this.securityContext = securityContext;
        this.entityEventHandler = entityEventHandler;
        this.entityEventResource = entityEventResource;
    }

    void init() {
        started = true;
    }

    @Override
    public void fire(final EntityEvent event) {
        if (started) {
            fireGlobally(event);
        }
    }

    /**
     * Fires the entity change to all nodes in the cluster.
     */
    private void fireGlobally(final EntityEvent event) {
        try {
            // Make sure there are some handlers that care about this event.
            boolean handlerExists = entityEventHandler.handlerExists(event, event.getDocRef().getType());
            if (!handlerExists) {
                // Look for handlers that cater for all types.
                handlerExists = entityEventHandler.handlerExists(event, "*");
            }

            // If there are registered handlers then go ahead and fire the event.
            if (handlerExists) {
                // Force a local update so that changes are immediately reflected
                // for the current user.
                entityEventHandler.fireLocally(event);

                if (started) {
                    // Dispatch the entity event to all nodes in the cluster.
                    final TaskContext taskContext = taskContextProvider.get();
                    Runnable runnable = () -> fireRemote(event);
                    runnable = taskContext.sub(runnable);
                    CompletableFuture.runAsync(runnable, executor);
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private void fireRemote(final EntityEvent entityEvent) {
         securityContext.secure(() -> {
            try {
                // Get this node.
                final String sourceNode = targetNodeSetFactory.getSourceNode();

                // Get the nodes that we are going to send the entity event to.
                final Set<String> targetNodes = targetNodeSetFactory.getEnabledActiveTargetNodeSet();

                // Only send the event to remote nodes and not this one.
                targetNodes.stream().filter(targetNode -> !targetNode.equals(sourceNode)).forEach(targetNode -> {
                    // Send the entity event.
                    entityEventResource.fireEvent(targetNode, entityEvent);
                });
            } catch (final NullClusterStateException | NodeNotFoundException e) {
                LOGGER.warn(e.getMessage());
                LOGGER.debug(e.getMessage(), e);
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }
}
