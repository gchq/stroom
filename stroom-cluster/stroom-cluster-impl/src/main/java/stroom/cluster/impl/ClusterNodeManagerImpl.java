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

package stroom.cluster.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cluster.api.ClusterNodeManager;
import stroom.cluster.api.ClusterState;
import stroom.node.api.FindNodeCriteria;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.node.shared.ClusterNodeInfo;
import stroom.node.shared.Node;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.util.date.DateUtil;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.shared.BuildInfo;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Component that remembers the node list and who is the current master node
 */
@Singleton
@EntityEventHandler(type = Node.ENTITY_TYPE, action = {EntityAction.CREATE, EntityAction.DELETE, EntityAction.UPDATE})
public class ClusterNodeManagerImpl implements ClusterNodeManager, EntityEvent.Handler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterNodeManagerImpl.class);

    private static final int ONE_SECOND = 1000;
    private static final int ONE_MINUTE = 60 * ONE_SECOND;
    private static final int TEN_MINUTES = 10 * ONE_MINUTE;
    /**
     * The amount of time in milliseconds we will delay before re-querying
     * state.
     */
    private static final int REQUERY_DELAY = 5 * ONE_SECOND;

    private final ClusterState clusterState = new ClusterState();
    private final AtomicBoolean updatingState = new AtomicBoolean();
    private final AtomicBoolean pendingUpdate = new AtomicBoolean();
    private final NodeInfo nodeInfo;
    private final NodeService nodeService;
    private final SecurityContext securityContext;
    private final BuildInfo buildInfo;
    private final ClusterCallServiceRemoteImpl clusterCallServiceRemote;
    private final Executor executor;
    private final Provider<TaskContext> taskContextProvider;

    @Inject
    ClusterNodeManagerImpl(final NodeInfo nodeInfo,
                           final NodeService nodeService,
                           final SecurityContext securityContext,
                           final BuildInfo buildInfo,
                           final ClusterCallServiceRemoteImpl clusterCallServiceRemote,
                           final Executor executor,
                           final Provider<TaskContext> taskContextProvider) {
        this.nodeInfo = nodeInfo;
        this.nodeService = nodeService;
        this.securityContext = securityContext;
        this.buildInfo = buildInfo;
        this.clusterCallServiceRemote = clusterCallServiceRemote;
        this.executor = executor;
        this.taskContextProvider = taskContextProvider;
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
            // older than 10 minutes.
            final long expiryTime = System.currentTimeMillis() - TEN_MINUTES;
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
            final TaskContext taskContext = taskContextProvider.get();
            Runnable runnable = () -> exec(clusterState, taskDelay, true);
            runnable = taskContext.sub(runnable);
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
                final TaskContext taskContext = taskContextProvider.get();
                Runnable runnable = () -> exec(clusterState, 0, false);
                runnable = taskContext.sub(runnable);
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
        final String discoverTime = DateUtil.createNormalDateTimeString(clusterState.getUpdateTime());

        final ClusterNodeInfo clusterNodeInfo = new ClusterNodeInfo(discoverTime,
                buildInfo,
                thisNodeName,
                nodeService.getBaseEndpointUrl(thisNodeName));

        if (masterNodeName != null) {
            for (final String nodeName : allNodeList) {
                clusterNodeInfo.addItem(nodeName, activeNodeList.contains(nodeName), masterNodeName.equals(nodeName));
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
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug(
                                    "ping() - Just had a ping from a node that was not in our ping list.  Next cluster call we will re-discover");
                        }
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
        // Force this node to redo it's cluster state.
        updateClusterStateAsync(REQUERY_DELAY, true);
    }

    private List<String> asList(Set<String> set) {
        final List<String> list = new ArrayList<>(set);
        list.sort((o1, o2) -> {
            if (o1 != null && o2 != null) {
                return o1.compareTo(o2);
            }
            return 0;
        });
        return list;
    }


    public void exec(final ClusterState clusterState, final int delay, final boolean testActiveNodes) {
        taskContextProvider.get().setName("Create Cluster State");
        securityContext.secure(() -> {
            try {
                // We sometimes want to wait a bit before we try and establish the
                // cluster state. This is often the case during startup when multiple
                // nodes start to ping each other which triggers an update but we want
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

    private void updateState(final ClusterState clusterState, final boolean testActiveNodes) {
        String thisNodeName = nodeInfo.getThisNodeName();

        // Get nodes and ensure uniqueness.
        Set<String> uniqueNodes = Collections.emptySet();
        final List<String> nodes = nodeService.findNodeNames(new FindNodeCriteria());
        if (nodes != null) {
            uniqueNodes = Set.copyOf(nodes);
        }
        clusterState.setAllNodes(uniqueNodes);

        // Get a list of enabled nodes and ensure we have the latest version of
        // the local node, i.e. not cached.
        final Set<String> enabledNodes = new HashSet<>();
        for (final String nodeName : clusterState.getAllNodes()) {
            if (nodeService.isEnabled(nodeName)) {
                enabledNodes.add(nodeName);
            }

            // Make sure we have the most up to date copy of this node.
            if (nodeName.equals(thisNodeName)) {
                thisNodeName = nodeName;
            }
        }
        clusterState.setEnabledNodes(enabledNodes);

        // Create a set of active nodes, i.e. nodes we can contact at this
        // time.
        if (testActiveNodes) {
            updateActiveNodes(clusterState, thisNodeName, enabledNodes);

            // Determine which node should be the master.
            int maxPriority = -1;
            String masterNodeName = null;
            for (final String nodeName : enabledNodes) {
                final int priority = nodeService.getPriority(nodeName);
                if (priority > maxPriority) {
                    maxPriority = priority;
                    masterNodeName = nodeName;
                }
            }

            clusterState.setMasterNodeName(masterNodeName);
        }

        clusterState.setUpdateTime(System.currentTimeMillis());
    }

    private void updateActiveNodes(final ClusterState clusterState, final String thisNodeName, final Set<String> enabledNodes) {
        // Only retain active nodes that are currently enabled.
        retainEnabledActiveNodes(clusterState, enabledNodes);

        // Now m
        for (final String nodeName : enabledNodes) {
            if (nodeName.equals(thisNodeName)) {
                addEnabledActiveNode(clusterState, nodeName);
            } else {
                executor.execute(() -> {
                    taskContextProvider.get().setName("Get Active Nodes");
                    taskContextProvider.get().info(() -> "Getting active nodes");

                    try {
                        // We call the API like this rather than using the
                        // Cluster API as the Cluster API will trigger a
                        // discover call (cyclic dependency).
                        // clusterCallServiceRemote will call getThisNode but
                        // that's OK as we have worked it out above.
                        clusterCallServiceRemote.call(thisNodeName, nodeName, securityContext.getUserIdentity(), ClusterNodeManager.SERVICE_NAME,
                                ClusterNodeManager.PING_METHOD, new Class<?>[]{String.class},
                                new Object[]{thisNodeName});
                        addEnabledActiveNode(clusterState, nodeName);

                    } catch (final RuntimeException e) {
                        LOGGER.warn("discover() - unable to contact {} - {}", nodeName, e.getMessage());
                        removeEnabledActiveNode(clusterState, nodeName);
                    }
                });
            }
        }
    }

    private synchronized void retainEnabledActiveNodes(final ClusterState clusterState, final Set<String> enabledNodes) {
        final Set<String> enabledActiveNodes = new HashSet<>();
        for (final String nodeName : enabledNodes) {
            if (clusterState.getEnabledActiveNodes().contains(nodeName)) {
                enabledActiveNodes.add(nodeName);
            }
        }
        clusterState.setEnabledActiveNodes(enabledActiveNodes);
    }

    private synchronized void addEnabledActiveNode(final ClusterState clusterState, final String nodeName) {
        final Set<String> enabledActiveNodes = new HashSet<>(clusterState.getEnabledActiveNodes());
        enabledActiveNodes.add(nodeName);
        clusterState.setEnabledActiveNodes(enabledActiveNodes);
    }

    private synchronized void removeEnabledActiveNode(final ClusterState clusterState, final String nodeName) {
        final Set<String> enabledActiveNodes = new HashSet<>(clusterState.getEnabledActiveNodes());
        enabledActiveNodes.remove(nodeName);
        clusterState.setEnabledActiveNodes(enabledActiveNodes);
    }
}
