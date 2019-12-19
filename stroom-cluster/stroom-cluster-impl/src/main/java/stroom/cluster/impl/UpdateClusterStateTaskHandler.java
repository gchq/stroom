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
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.node.shared.FindNodeCriteria;
import stroom.security.api.SecurityContext;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


class UpdateClusterStateTaskHandler extends AbstractTaskHandler<UpdateClusterStateTask, VoidResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateClusterStateTaskHandler.class);

    private final NodeService nodeService;
    private final NodeInfo nodeInfo;
    private final ClusterCallServiceRemoteImpl clusterCallServiceRemote;
    private final ExecutorProvider executorProvider;
    private final TaskContext taskContext;
    private final SecurityContext securityContext;

    @Inject
    UpdateClusterStateTaskHandler(final NodeService nodeService,
                                  final NodeInfo nodeInfo,
                                  final ClusterCallServiceRemoteImpl clusterCallServiceRemote,
                                  final ExecutorProvider executorProvider,
                                  final TaskContext taskContext,
                                  final SecurityContext securityContext) {
        this.nodeService = nodeService;
        this.nodeInfo = nodeInfo;
        this.clusterCallServiceRemote = clusterCallServiceRemote;
        this.executorProvider = executorProvider;
        this.taskContext = taskContext;
        this.securityContext = securityContext;
    }

    @Override
    public VoidResult exec(final UpdateClusterStateTask task) {
        return securityContext.secureResult(() -> {
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
        if (task.isTestActiveNodes()) {
            updateActiveNodes(task, thisNodeName, enabledNodes);

            // Determine which node should be the master.
            int maxPriority = -1;
            String masterNodeName = null;
            for (final String nodeName : enabledNodes) {
                final int priority = nodeService.getPriority(nodeName);
                if (priority> maxPriority) {
                    maxPriority = priority;
                    masterNodeName = nodeName;
                }
            }

            clusterState.setMasterNodeName(masterNodeName);
        }

        clusterState.setUpdateTime(System.currentTimeMillis());
    }

    private void updateActiveNodes(final UpdateClusterStateTask updateClusterStateTask, final String thisNodeName, final Set<String> enabledNodes) {
        final ClusterState clusterState = updateClusterStateTask.getClusterState();

        // Only retain active nodes that are currently enabled.
        retainEnabledActiveNodes(clusterState, enabledNodes);

        // Now m
        for (final String nodeName : enabledNodes) {
            if (nodeName.equals(thisNodeName)) {
                addEnabledActiveNode(clusterState, nodeName);
            } else {
                executorProvider.getExecutor().execute(() -> {
                    taskContext.setName("Get Active Nodes");
                    taskContext.info(()->"Getting active nodes");

                    try {
                        // We call the API like this rather than using the
                        // Cluster API as the Cluster API will trigger a
                        // discover call (cyclic dependency).
                        // clusterCallServiceRemote will call getThisNode but
                        // that's OK as we have worked it out above.
                        clusterCallServiceRemote.call(thisNodeName, nodeName, ClusterNodeManager.SERVICE_NAME,
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
