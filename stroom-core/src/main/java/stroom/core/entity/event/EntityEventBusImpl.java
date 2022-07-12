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

import stroom.cluster.api.ClusterMember;
import stroom.cluster.api.ClusterService;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
class EntityEventBusImpl implements EntityEventBus {

    private static final Logger LOGGER = LoggerFactory.getLogger(EntityEventBusImpl.class);

    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final Provider<ClusterService> clusterServiceProvider;
    private final SecurityContext securityContext;
    private final EntityEventHandler entityEventHandler;
    private final EntityEventResource entityEventResource;

    private volatile boolean started = false;

    @Inject
    EntityEventBusImpl(final Executor executor,
                       final TaskContextFactory taskContextFactory,
                       final Provider<ClusterService> clusterServiceProvider,
                       final SecurityContext securityContext,
                       final EntityEventHandler entityEventHandler,
                       final EntityEventResource entityEventResource) {
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.clusterServiceProvider = clusterServiceProvider;
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
        securityContext.asProcessingUser(() -> {
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
                        final Runnable runnable = taskContextFactory.context("Fire Entity Event Globally",
                                taskContext -> fireRemote(event, taskContext));
                        CompletableFuture.runAsync(runnable, executor);
                    }
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }

    private void fireRemote(final EntityEvent entityEvent,
                            final TaskContext parentTaskContext) {
        try {
            final ClusterService clusterService = clusterServiceProvider.get();

            // Get this node.
            final ClusterMember local = clusterService.getLocal();

            // Get the nodes that we are going to send the entity event to.
            final Set<ClusterMember> targetNodes = clusterService.getMembers();

            // Only send the event to remote nodes and not this one.
            CompletableFuture.allOf(targetNodes
                    .stream()
                    .filter(targetNode -> !targetNode.equals(local))
                    .map(targetNode -> {
                        // Send the entity event asynchronously.
                        final Runnable runnable = taskContextFactory.childContext(
                                parentTaskContext,
                                "Fire Entity Event To " + targetNode, taskContext ->
                                        entityEventResource.fireEvent(targetNode.getUuid(), entityEvent)
                        );
                        return CompletableFuture.runAsync(runnable, executor);
                    }).toArray(CompletableFuture[]::new)).join();
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}
