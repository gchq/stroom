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
 */

package stroom.node.impl;

import stroom.cluster.api.ClusterNodeManager;
import stroom.cluster.api.ClusterState;
import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.node.api.FindNodeCriteria;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.shared.ClusterNodeInfo;
import stroom.node.shared.FetchNodeStatusResponse;
import stroom.node.shared.Node;
import stroom.node.shared.NodeResource;
import stroom.node.shared.NodeStatusResult;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResourcePaths;

import event.logging.AdvancedQuery;
import event.logging.And;
import event.logging.Query;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.client.SyncInvoker;

@AutoLogged
class NodeResourceImpl implements NodeResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(NodeResourceImpl.class);

    private final Provider<NodeServiceImpl> nodeServiceProvider;
    private final Provider<NodeInfo> nodeInfoProvider;
    private final Provider<ClusterNodeManager> clusterNodeManagerProvider;
    private final Provider<DocumentEventLog> documentEventLogProvider;

    @Inject
    NodeResourceImpl(final Provider<NodeServiceImpl> nodeServiceProvider,
                     final Provider<NodeInfo> nodeInfoProvider,
                     final Provider<ClusterNodeManager> clusterNodeManagerProvider,
                     final Provider<WebTargetFactory> webTargetFactoryProvider,
                     final Provider<DocumentEventLog> documentEventLogProvider) {
        this.nodeServiceProvider = nodeServiceProvider;
        this.nodeInfoProvider = nodeInfoProvider;
        this.clusterNodeManagerProvider = clusterNodeManagerProvider;
        this.documentEventLogProvider = documentEventLogProvider;
    }

    @Override
    public List<String> listAllNodes() {
        FetchNodeStatusResponse response = find();
        if (response != null && response.getValues() != null) {
            return response.getValues()
                    .stream()
                    .map(NodeStatusResult::getNode)
                    .map(Node::getName)
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    @Override
    public List<String> listEnabledNodes() {
        return find().getValues()
                .stream()
                .map(NodeStatusResult::getNode)
                .filter(Node::isEnabled)
                .map(Node::getName)
                .collect(Collectors.toList());
    }

    @Override
    public FetchNodeStatusResponse find() {
        FetchNodeStatusResponse response = null;

        final Query query = Query.builder()
                .withAdvanced(AdvancedQuery.builder()
                        .addAnd(And.builder()
                                .build())
                        .build())
                .build();

        try {
            final List<Node> nodes = nodeServiceProvider.get()
                    .find(new FindNodeCriteria())
                    .getValues();

            final ClusterState clusterState = clusterNodeManagerProvider.get().getClusterState();
            final String masterNodeName = clusterState.getMasterNodeName();

            final List<NodeStatusResult> resultList = nodes.stream()
                    .sorted(Comparator.comparing(Node::getName))
                    .map(node ->
                            new NodeStatusResult(node, node.getName().equals(masterNodeName)))
                    .collect(Collectors.toList());
            response = new FetchNodeStatusResponse(resultList);

            documentEventLogProvider.get().search(
                    "List Nodes",
                    query,
                    Node.class.getSimpleName(),
                    response.getPageResponse(),
                    null);
        } catch (final RuntimeException e) {
            documentEventLogProvider.get().search(
                    "List Nodes",
                    query,
                    Node.class.getSimpleName(),
                    null,
                    e);
            throw e;
        }

        return response;
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public ClusterNodeInfo info(final String nodeName) {
        ClusterNodeInfo clusterNodeInfo = null;

        final Supplier<String> pathSupplier = () -> ResourcePaths.buildAuthenticatedApiPath(
                NodeResource.BASE_PATH,
                NodeResource.INFO_PATH_PART,
                nodeName);


        try {
            final long now = System.currentTimeMillis();

            clusterNodeInfo = nodeServiceProvider.get().remoteRestResult(
                    nodeName,
                    ClusterNodeInfo.class,
                    pathSupplier,
                    () ->
                            clusterNodeManagerProvider.get().getClusterNodeInfo(),
                    SyncInvoker::get);

            if (clusterNodeInfo == null) {
                final String url = NodeCallUtil.getBaseEndpointUrl(
                        nodeInfoProvider.get(),
                        nodeServiceProvider.get(),
                        nodeName) + pathSupplier.get();
                throw new RuntimeException("Unable to contact node \"" + nodeName + "\" at URL: " + url);
            }

            clusterNodeInfo.setPing(System.currentTimeMillis() - now);

            documentEventLogProvider.get().view(clusterNodeInfo, null);

        } catch (Exception e) {
            documentEventLogProvider.get().view(clusterNodeInfo, e);

            clusterNodeInfo = new ClusterNodeInfo();
            clusterNodeInfo.setNodeName(nodeName);
            clusterNodeInfo.setEndpointUrl(null);
            clusterNodeInfo.setError(e.getMessage());
        }

        return clusterNodeInfo;
    }

    @Override
    @AutoLogged(value = OperationType.PROCESS, verb = "Pinging other node")
    public Long ping(final String nodeName) {
        final long now = System.currentTimeMillis();

        final Long ping = nodeServiceProvider.get().remoteRestResult(
                nodeName,
                Long.class,
                () -> ResourcePaths.buildAuthenticatedApiPath(
                        NodeResource.BASE_PATH,
                        NodeResource.PING_PATH_PART,
                        nodeName),
                () ->
                        // If this is the node that was contacted then just return the latency
                        // we have incurred within this method.
                        System.currentTimeMillis() - now,
                SyncInvoker::get);

        Objects.requireNonNull(ping, "Null ping");

        return System.currentTimeMillis() - now;
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public boolean setPriority(final String nodeName, final Integer priority) {
        modifyNode(nodeName, node -> node.setPriority(priority));
        return true;
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public boolean setEnabled(final String nodeName, final Boolean enabled) {
        modifyNode(nodeName, node -> node.setEnabled(enabled));
        return true;
    }

    private void modifyNode(final String nodeName,
                            final Consumer<Node> mutation) {
        Node node = null;
        Node before = null;
        Node after = null;
        final NodeServiceImpl nodeService = nodeServiceProvider.get();
        final DocumentEventLog documentEventLog = documentEventLogProvider.get();

        try {
            // Get the before version.
            before = nodeService.getNode(nodeName);
            node = nodeService.getNode(nodeName);
            if (node == null) {
                throw new RuntimeException("Unknown node: " + nodeName);
            }
            mutation.accept(node);
            after = nodeService.update(node);

            documentEventLog.update(before, after, null);

        } catch (final RuntimeException e) {
            documentEventLog.update(before, after, e);
            throw e;
        }
    }
}
