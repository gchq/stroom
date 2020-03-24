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

package stroom.security.impl.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cluster.task.api.ClusterDispatchAsyncHelper;
import stroom.cluster.task.api.NodeNotFoundException;
import stroom.cluster.task.api.NullClusterStateException;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.security.api.SecurityContext;
import stroom.security.impl.event.PermissionChangeEvent.Handler;
import stroom.task.api.TaskContext;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Singleton
class PermissionChangeEventBusImpl implements PermissionChangeEventBus {
    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionChangeEventBusImpl.class);
    private final Set<Handler> handlers = new HashSet<>();
    private volatile boolean initialised;

    private final Provider<Set<PermissionChangeEvent.Handler>> handlerProvider;
    private final ClusterDispatchAsyncHelper dispatchHelper;
    private final TargetNodeSetFactory targetNodeSetFactory;
    private final SecurityContext securityContext;
    private final Executor executor;
    private final Provider<TaskContext> taskContextProvider;

    private volatile boolean started = false;

    @Inject
    PermissionChangeEventBusImpl(final Provider<Set<Handler>> handlerProvider,
                                 final ClusterDispatchAsyncHelper dispatchHelper,
                                 final TargetNodeSetFactory targetNodeSetFactory,
                                 final SecurityContext securityContext,
                                 final Executor executor,
                                 final Provider<TaskContext> taskContextProvider) {
        this.handlerProvider = handlerProvider;
        this.dispatchHelper = dispatchHelper;
        this.targetNodeSetFactory = targetNodeSetFactory;
        this.securityContext = securityContext;
        this.executor = executor;
        this.taskContextProvider = taskContextProvider;
    }

    void init() {
        started = true;
    }

    @Override
    public void fire(final PermissionChangeEvent event) {
        fireGlobally(event);
    }

    /**
     * Fires the entity change to all nodes in the cluster.
     */
    public void fireGlobally(final PermissionChangeEvent event) {
        try {
            // Force a local update so that changes are immediately reflected
            // for the current user.
            fireLocally(event);

            if (started) {
                final TaskContext taskContext = taskContextProvider.get();
                Runnable runnable = () -> {
                    // Dispatch the entity event to all nodes in the cluster.
                    securityContext.secure(() -> {
                        try {
                            // Get this node.
                            final String sourceNode = targetNodeSetFactory.getSourceNode();

                            // Get the nodes that we are going to send the entity event to.
                            final Set<String> targetNodes = targetNodeSetFactory.getEnabledActiveTargetNodeSet();

                            // Only send the event to remote nodes and not this one.
                            targetNodes.stream().filter(targetNode -> !targetNode.equals(sourceNode)).forEach(targetNode -> {
                                // Send the entity event.
                                final ClusterPermissionChangeEventTask clusterEntityEventTask = new ClusterPermissionChangeEventTask(event);
                                dispatchHelper.execAsync(clusterEntityEventTask, targetNode);
                            });
                        } catch (final NullClusterStateException | NodeNotFoundException e) {
                            LOGGER.warn(e.getMessage());
                            LOGGER.debug(e.getMessage(), e);
                        } catch (final RuntimeException e) {
                            LOGGER.error(e.getMessage(), e);
                        }
                    });
                };
                runnable = taskContext.subTask(runnable);
                CompletableFuture.runAsync(runnable, executor);
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public void fireLocally(final PermissionChangeEvent event) {
        try {
            final Set<PermissionChangeEvent.Handler> set = getHandlers();
            for (final PermissionChangeEvent.Handler handler : set) {
                try {
                    handler.onChange(event);
                } catch (final Exception e) {
                    LOGGER.error("Unable to handle onChange event!", e);
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @Override
    public void addHandler(final PermissionChangeEvent.Handler handler) {
        handlers.add(handler);
    }

    private Set<PermissionChangeEvent.Handler> getHandlers() {
        if (!initialised) {
            initialise();
        }

        return handlers;
    }

    private synchronized void initialise() {
        if (!initialised) {
            try {
                for (final Handler handler : handlerProvider.get()) {
                    addHandler(handler);
                }
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to initialise EntityEventBusImpl!", e);
            }

            initialised = true;
        }
    }
}
