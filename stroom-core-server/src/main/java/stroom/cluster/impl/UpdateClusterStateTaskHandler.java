/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.cluster.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cluster.api.ClusterNodeManager;
import stroom.cluster.api.ClusterState;
import stroom.node.NodeCache;
import stroom.node.NodeService;
import stroom.node.shared.FindNodeCriteria;
import stroom.node.shared.Node;
import stroom.security.Security;
import stroom.task.GenericServerTask;
import stroom.task.api.TaskManager;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


class UpdateClusterStateTaskHandler extends AbstractTaskHandler<UpdateClusterStateTask, VoidResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateClusterStateTaskHandler.class);

    private final NodeService nodeService;
    private final NodeCache nodeCache;
    private final ClusterCallServiceRemoteImpl clusterCallServiceRemote;
    private final TaskManager taskManager;
    private final Security security;

    @Inject
    UpdateClusterStateTaskHandler(final NodeService nodeService,
                                  final NodeCache nodeCache,
                                  final ClusterCallServiceRemoteImpl clusterCallServiceRemote,
                                  final TaskManager taskManager,
                                  final Security security) {
        this.nodeService = nodeService;
        this.nodeCache = nodeCache;
        this.clusterCallServiceRemote = clusterCallServiceRemote;
        this.taskManager = taskManager;
        this.security = security;
    }

    @Override
    public VoidResult exec(final UpdateClusterStateTask task) {
        return security.secureResult(() -> {
            try {
                // We sometimes want to wait a bit before we try and establish the
                // cluster state. This is often the case during startup when multiple
                // nodes start to ping each other which triggers an update but we want
                // to give other nodes some time to start also.
                if (task.getDelay() > 0) {
                    Thread.sleep(task.getDelay());
                }

                updateState(task);
            } catch (final InterruptedException e) {
                LOGGER.debug(e.getMessage(), e);

                // Continue to interrupt this thread.
                Thread.currentThread().interrupt();
            }

            return VoidResult.INSTANCE;
        });
    }

    private void updateState(final UpdateClusterStateTask task) {
        final ClusterState clusterState = task.getClusterState();

        String thisNode = nodeCache.getThisNodeName();

        // Get nodes and ensure uniqueness.
         Set<String> uniqueNodes = Collections.emptySet();
        final List<Node> nodes = nodeService.find(new FindNodeCriteria());
        if (nodes != null) {
            uniqueNodes = nodes.stream().map(Node::getName).collect(Collectors.toSet());
        }
        clusterState.setAllNodes(uniqueNodes);

        // Get a list of enabled nodes and ensure we have the latest version of
        // the local node, i.e. not cached.
        final Set<String> enabledNodes = new HashSet<>();
        for (final String node : clusterState.getAllNodes()) {
            if (nodeCache.isEnabled(node)) {
                enabledNodes.add(node);
            }

            // Make sure we have the most up to date copy of this node.
            if (node.equals(thisNode)) {
                thisNode = node;
            }
        }
        clusterState.setEnabledNodes(enabledNodes);

        // Create a set of active nodes, i.e. nodes we can contact at this
        // time.
        if (task.isTestActiveNodes()) {
            updateActiveNodes(task, thisNode, enabledNodes);

            // Determine which node should be the master.
            int maxPriority = -1;
            String masterNode = null;
            for (final String node : enabledNodes) {
                final int priority = nodeCache.getPriority(node);
                if (priority> maxPriority) {
                    maxPriority = priority;
                    masterNode = node;
                }
            }

            clusterState.setMasterNode(masterNode);
        }

        clusterState.setUpdateTime(System.currentTimeMillis());
    }

    private void updateActiveNodes(final UpdateClusterStateTask updateClusterStateTask, final String thisNode, final Set<String> enabledNodes) {
        final ClusterState clusterState = updateClusterStateTask.getClusterState();

        // Only retain active nodes that are currently enabled.
        retainEnabledActiveNodes(clusterState, enabledNodes);

        // Now m
        for (final String node : enabledNodes) {
            if (node.equals(thisNode)) {
                addEnabledActiveNode(clusterState, node);
            } else {
                final GenericServerTask genericServerTask = GenericServerTask.create(updateClusterStateTask,
                        "Get Active Nodes", "Getting active nodes");
                genericServerTask.setRunnable(() -> {
                    try {
                        // We call the API like this rather than using the
                        // Cluster API as the Cluster API will trigger a
                        // discover call (cyclic dependency).
                        // clusterCallServiceRemote will call getThisNode but
                        // that's OK as we have worked it out above.
                        clusterCallServiceRemote.call(thisNode, node, ClusterNodeManager.SERVICE_NAME,
                                ClusterNodeManager.PING_METHOD, new Class<?>[]{String.class},
                                new Object[]{thisNode});
                        addEnabledActiveNode(clusterState, node);

                    } catch (final RuntimeException e) {
                        LOGGER.warn("discover() - unable to contact {} - {}", node, e.getMessage());
                        removeEnabledActiveNode(clusterState, node);
                    }
                });
                taskManager.execAsync(genericServerTask);
            }
        }
    }

    private synchronized void retainEnabledActiveNodes(final ClusterState clusterState, final Set<String> enabledNodes) {
        final Set<String> enabledActiveNodes = new HashSet<>();
        for (final String node : enabledNodes) {
            if (clusterState.getEnabledActiveNodes().contains(node)) {
                enabledActiveNodes.add(node);
            }
        }
        clusterState.setEnabledActiveNodes(enabledActiveNodes);
    }

    private synchronized void addEnabledActiveNode(final ClusterState clusterState, final String node) {
        final Set<String> enabledActiveNodes = new HashSet<>(clusterState.getEnabledActiveNodes());
        enabledActiveNodes.add(node);
        clusterState.setEnabledActiveNodes(enabledActiveNodes);
    }

    private synchronized void removeEnabledActiveNode(final ClusterState clusterState, final String node) {
        final Set<String> enabledActiveNodes = new HashSet<>(clusterState.getEnabledActiveNodes());
        enabledActiveNodes.remove(node);
        clusterState.setEnabledActiveNodes(enabledActiveNodes);
    }
}
