/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.cluster.impl;

import stroom.cluster.api.ClusterNodeManager;
import stroom.cluster.api.ClusterState;
import stroom.node.api.FindNodeCriteria;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.node.shared.ClusterNodeInfo;
import stroom.node.shared.Node;
import stroom.node.shared.NodeResource;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.ModelStringUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Component that remembers the node list and who is the current master node
 */
@Singleton
@EntityEventHandler(
        type = Node.ENTITY_TYPE,
        action = {
                EntityAction.CREATE,
                EntityAction.DELETE,
                EntityAction.UPDATE})
public class ClusterNodeManagerImpl implements ClusterNodeManager, EntityEvent.Handler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ClusterNodeManagerImpl.class);

    private static final int ONE_SECOND = 1000;
    private static final int ONE_MINUTE = 60 * ONE_SECOND;
    /**
     * The amount of time in milliseconds we will delay before re-querying
     * state.
     */
    private static final int REQUERY_DELAY = 5 * ONE_SECOND;

    private final ClusterState clusterState = new ClusterState();
    private final AtomicBoolean updatingState = new AtomicBoolean();
    private final AtomicBoolean pendingUpdate = new AtomicBoolean();
    private final NodeInfo nodeInfo;
    // Provider to fix guice circular dep
    private final Provider<NodeService> nodeServiceProvider;
    private final SecurityContext securityContext;
    private final BuildInfo buildInfo;
    private final NodeResource nodeResource;
    private final Executor executor;
    private final TaskContextFactory taskContextFactory;

    @Inject
    ClusterNodeManagerImpl(final NodeInfo nodeInfo,
                           final Provider<NodeService> nodeServiceProvider,
                           final SecurityContext securityContext,
                           final BuildInfo buildInfo,
                           final NodeResource nodeResource,
                           final Executor executor,
                           final TaskContextFactory taskContextFactory) {
        this.nodeInfo = nodeInfo;
        this.nodeServiceProvider = nodeServiceProvider;
        this.securityContext = securityContext;
        this.buildInfo = buildInfo;
        this.nodeResource = nodeResource;
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
    }

    public void init() {
        // Run initial query of cluster state.
        updateClusterStateAsync(REQUERY_DELAY, false);
    }

    /**
     * Gets the current cluster state. If the current state is null or is older
     * than the expiry time then the cluster state will be updated
     * asynchronously ready for subsequent calls. The current state is still
     * returned so could be null
     */
    @Override
    public ClusterState getClusterState() {
        // If the cluster state has never been updated then make sure we are updating it.
        if (clusterState.getUpdateTime() == 0) {
            updateClusterStateAsync(0, false);
        } else {
            // Determine if the current state should be re-evaluated as it is
            // older than 1 minute.
            final long expiryTime = System.currentTimeMillis() - ONE_MINUTE;
            if (clusterState.getUpdateTime() < expiryTime) {
                updateClusterStateAsync(0, false);
            }
        }

        return clusterState;
    }

    private void updateClusterStateAsync(final int taskDelay, final boolean force) {
        if (force) {
            pendingUpdate.set(true);
        }

        if (updatingState.compareAndSet(false, true)) {
            doUpdate(taskDelay);
        }
    }

    private void doUpdate(final int taskDelay) {
        securityContext.asProcessingUser(() -> {
            pendingUpdate.set(false);
            final Runnable runnable = taskContextFactory.context(
                    "Create Cluster State",
                    taskContext -> exec(clusterState, taskDelay, true));
            CompletableFuture
                    .runAsync(runnable, executor)
                    .whenComplete((r, t) -> {
                        try {
                            if (t == null) {
                                // Output some debug about the state we are about to set.
                                outputDebug(clusterState);

                                // Re-query cluster state if we are pending an update.
                                if (pendingUpdate.get()) {
                                    // Wait a few seconds before we query again.
                                    Thread.sleep(REQUERY_DELAY);
                                    // Query again.
                                    doUpdate(0);
                                }
                            }
                        } catch (final InterruptedException e) {
                            LOGGER.error(e.getMessage(), e);

                            // Continue to interrupt this thread.
                            Thread.currentThread().interrupt();
                        } catch (final RuntimeException e) {
                            LOGGER.error(e.getMessage(), e);
                        } finally {
                            updatingState.set(false);
                        }
                    });
        });
    }

    private void outputDebug(final ClusterState clusterState) {
        try {
            if (LOGGER.isDebugEnabled()) {
                final StringBuilder builder = new StringBuilder();
                builder.append("Setting cluster state:");

                if (clusterState == null) {
                    builder.append(" NULL");

                } else {
                    final List<String> nodes = asList(clusterState.getAllNodes());
                    for (final String nodeName : nodes) {
                        builder.append("\n\t");
                        builder.append(nodeName);
                        if (nodeName.equals(clusterState.getMasterNodeName())) {
                            builder.append("(MASTER)");
                        }
                    }
                }

                LOGGER.debug(builder.toString());
            }
        } catch (final RuntimeException e) {
            // Ignore exceptions.
        }
    }

    /**
     * Gets the current cluster state or builds a new cluster state if no
     * current state exists but without locking. This method is quick as either
     * the current state is returned or a basic state is created that does not
     * attempt to determine which nodes are active or what the current master
     * node is.
     */
    @Override
    public ClusterState getQuickClusterState() {
        // Just get the current state if we can.
        if (clusterState.getUpdateTime() == 0) {
            securityContext.asProcessingUser(() -> {
                // Create a cluster state object but don't bother trying to contact
                // remote nodes to determine active status.
                final Runnable runnable = taskContextFactory.context(
                        "Create Cluster State",
                        taskContext -> exec(clusterState, 0, false));
                runnable.run();
            });
        }
        return clusterState;
    }

    @Override
    public ClusterNodeInfo getClusterNodeInfo() {
        final ClusterState clusterState = getQuickClusterState();

        // Take a copy of our working variables;
        final List<String> allNodeList = asList(clusterState.getAllNodes());
        final List<String> activeNodeList = asList(clusterState.getEnabledActiveNodes());
        final String thisNodeName = nodeInfo.getThisNodeName();
        final String masterNodeName = clusterState.getMasterNodeName();

        final ClusterNodeInfo clusterNodeInfo = new ClusterNodeInfo(clusterState.getUpdateTime(),
                buildInfo,
                thisNodeName,
                nodeServiceProvider.get().getBaseEndpointUrl(thisNodeName));

        if (masterNodeName != null) {
            for (final String nodeName : allNodeList) {
                clusterNodeInfo.addItem(
                        nodeName,
                        activeNodeList.contains(nodeName),
                        masterNodeName.equals(nodeName));
            }
        }

        return clusterNodeInfo;
    }

    @Override
    public Long ping(final String sourceNode) {
        try {
            // Update the cluster state if the node trying to ping us is not us.
            final String thisNodeName = nodeInfo.getThisNodeName();
            if (!thisNodeName.equals(sourceNode)) {
                // Try and get the cluster state and make sure we know about the
                // node trying to ping us. If we can't get the cluster state or
                // the current state doesn't know the source node is active then
                // update the cluster state.
                if (clusterState.getUpdateTime() != 0) {
                    final Set<String> enabledActiveNodes = clusterState.getEnabledActiveNodes();
                    if (!enabledActiveNodes.contains(sourceNode)) {
                        LOGGER.debug("""
                                ping() - Just had a ping from a node that was not in our ping list. \
                                Next cluster call we will re-discover""");
                        updateClusterStateAsync(REQUERY_DELAY, true);
                    }
                } else {
                    updateClusterStateAsync(REQUERY_DELAY, true);
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return System.currentTimeMillis();
    }

    /**
     * Trigger a re-discover if the node saves
     */
    @Override
    public void onChange(final EntityEvent event) {
        // Force this node to redo its cluster state.
        updateClusterStateAsync(REQUERY_DELAY, true);
    }

    private List<String> asList(final Set<String> set) {
        final List<String> list = new ArrayList<>(set);
        list.sort((o1, o2) -> {
            if (o1 != null && o2 != null) {
                return o1.compareTo(o2);
            }
            return 0;
        });
        return list;
    }


    public void exec(final ClusterState clusterState,
                     final int delay,
                     final boolean testActiveNodes) {
        securityContext.secure(() -> {
            try {
                // We sometimes want to wait a bit before we try and establish the
                // cluster state. This is often the case during startup when multiple
                // nodes start to ping each other which triggers an update, but we want
                // to give other nodes some time to start also.
                if (delay > 0) {
                    Thread.sleep(delay);
                }

                updateState(clusterState, testActiveNodes);
            } catch (final InterruptedException e) {
                LOGGER.debug(e.getMessage(), e);

                // Continue to interrupt this thread.
                Thread.currentThread().interrupt();
            }
        });
    }

    private void updateState(final ClusterState clusterState,
                             final boolean testActiveNodes) {
        final String thisNodeName = nodeInfo.getThisNodeName();

        // Not ideal having to hit the service and db twice like this but the service no longer
        // exposes the Node object so limited options.
        final NodeService nodeService = nodeServiceProvider.get();
        final List<String> allNodeNames = Objects.requireNonNullElse(
                nodeService.findNodeNames(new FindNodeCriteria()),
                Collections.emptyList());

        final List<String> enabledNodesByPriority = Objects.requireNonNullElse(
                nodeService.getEnabledNodesByPriority(),
                Collections.emptyList());

        clusterState.setAllNodes(allNodeNames);

//        final Set<String> enabledNodes = new HashSet<>(enabledNodesByPriority);
        clusterState.setEnabledNodes(enabledNodesByPriority);

        // Create a set of active nodes, i.e. nodes we can contact at this
        // time.
        if (testActiveNodes) {
            updateActiveNodes(clusterState, thisNodeName, enabledNodesByPriority);

            updateMasterNode(enabledNodesByPriority);
        }

        clusterState.setUpdateTime(System.currentTimeMillis());
    }

    private void updateMasterNode(final List<String> enabledNodesByPriority) {
        if (!enabledNodesByPriority.isEmpty()) {
            // Master node is the one with the highest priority. If two
            // or more nodes have the same and highest priority then the master
            // is the first node when sorted on ascending node name.
            // enabledNodesByPriority may contain nodes that we can't ping so find the
            // first one that has been deemed active by checking in clusterState which
            // has been mutated in the background based on ping results
            final long enabledAndActiveNodeCount = enabledNodesByPriority.stream()
                    .filter(clusterState::isEnabledAndActive)
                    .count();

            enabledNodesByPriority.stream()
                    .filter(clusterState::isEnabledAndActive)
                    .findFirst()
                    .ifPresent(newMasterNodeName -> {
                        final String oldMasterNodeName = clusterState.setMasterNodeName(newMasterNodeName);
                        if (!Objects.equals(oldMasterNodeName, newMasterNodeName)) {
                            if (oldMasterNodeName == null) {
                                LOGGER.info("Master node is {}, enabledAndActiveNodeCount: {}",
                                        newMasterNodeName, enabledAndActiveNodeCount);
                            } else {
                                LOGGER.info("Master node has changed from {} to {}, enabledAndActiveNodeCount: {}",
                                        oldMasterNodeName, newMasterNodeName, enabledAndActiveNodeCount);
                            }
                        }
                    });
        }
    }

    private void updateActiveNodes(final ClusterState clusterState,
                                   final String thisNodeName,
                                   final List<String> enabledNodesByPriority) {
        // Only retain active nodes that are currently enabled.
        clusterState.retainEnabledActiveNodes(enabledNodesByPriority);

        // Now loop over all the enabled nodes and try and ping them asynchronously
        // updating the cluster state as we go
        for (final String nodeName : enabledNodesByPriority) {
            if (nodeName.equals(thisNodeName)) {
                // local node no need to ping
                clusterState.addEnabledActiveNode(nodeName);
            } else {
                final Runnable runnable = taskContextFactory.context(
                        "Pinging node " + nodeName,
                        taskContext -> {
                            taskContext.info(() -> "Pinging node " + nodeName);
                            try {
                                final Long responseMs = nodeResource.ping(nodeName);
                                LOGGER.debug(() -> "Got ping response in " +
                                                   ModelStringUtil.formatDurationString(responseMs));
                                clusterState.addEnabledActiveNode(nodeName);

                            } catch (final RuntimeException e) {
                                LOGGER.warn("discover() - unable to contact {} - {}", nodeName, e.getMessage());
                                clusterState.removeEnabledActiveNode(nodeName);
                            }
                            // Whatever happened update the master node state based on the current picture
                            // of enabled and active nodes and priority order
                            updateMasterNode(enabledNodesByPriority);
                        });
                CompletableFuture.runAsync(runnable, executor);
            }
        }
    }
}
