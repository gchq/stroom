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

package stroom.node.impl;

import stroom.cluster.api.ClusterNodeManager;
import stroom.cluster.api.ClusterState;
import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.node.api.FindNodeCriteria;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.shared.ClusterNodeInfo;
import stroom.node.shared.FetchNodeStatusResponse;
import stroom.node.shared.FindNodeStatusCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.NodeResource;
import stroom.node.shared.NodeStatusResult;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.CompareUtil;
import stroom.util.shared.CompareUtil.FieldComparators;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.StringCriteria;

import event.logging.AdvancedQuery;
import event.logging.And;
import event.logging.Query;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.SyncInvoker;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@AutoLogged
class NodeResourceImpl implements NodeResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(NodeResourceImpl.class);

    private final Provider<NodeServiceImpl> nodeServiceProvider;
    private final Provider<NodeInfo> nodeInfoProvider;
    private final Provider<ClusterNodeManager> clusterNodeManagerProvider;
    private final Provider<DocumentEventLog> documentEventLogProvider;

    private static final FieldComparators<Node> FIELD_COMPARATORS = FieldComparators.builder(Node.class)
            .addStringComparator(FindNodeStatusCriteria.FIELD_ID_NAME, Node::getName)
            .addStringComparator(FindNodeStatusCriteria.FIELD_ID_URL, Node::getUrl)
            .addIntComparator(FindNodeStatusCriteria.FIELD_ID_PRIORITY, Node::getPriority)
            .addBooleanComparator(FindNodeStatusCriteria.FIELD_ID_ENABLED, Node::isEnabled)
            .addStringComparator(FindNodeStatusCriteria.FIELD_ID_BUILD_VERSION, Node::getBuildVersion)
            .addCaseLessComparator(FindNodeStatusCriteria.FIELD_ID_LAST_BOOT_MS, Node::getLastBootMs)
            .build();

    @Inject
    NodeResourceImpl(final Provider<NodeServiceImpl> nodeServiceProvider,
                     final Provider<NodeInfo> nodeInfoProvider,
                     final Provider<ClusterNodeManager> clusterNodeManagerProvider,
                     final Provider<DocumentEventLog> documentEventLogProvider) {
        this.nodeServiceProvider = nodeServiceProvider;
        this.nodeInfoProvider = nodeInfoProvider;
        this.clusterNodeManagerProvider = clusterNodeManagerProvider;
        this.documentEventLogProvider = documentEventLogProvider;
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED) // Too noisy and of little value
    public List<String> listAllNodes() {
        final FetchNodeStatusResponse response = find();
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
    @AutoLogged(OperationType.UNLOGGED) // Too noisy and of little value
    public List<String> listEnabledNodes() {
        return find().getValues()
                .stream()
                .map(NodeStatusResult::getNode)
                .filter(Node::isEnabled)
                .map(Node::getName)
                .collect(Collectors.toList());
    }

    private FetchNodeStatusResponse find() {
        return find(new FindNodeStatusCriteria());
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public FetchNodeStatusResponse find(final FindNodeStatusCriteria findNodeStatusCriteria) {
        FetchNodeStatusResponse response = null;

        final Query query = Query.builder()
                .withAdvanced(AdvancedQuery.builder()
                        .addAnd(And.builder()
                                .build())
                        .build())
                .build();

        final String typeId = StroomEventLoggingUtil.buildTypeId(this, "find");
        try {
            final FindNodeCriteria findNodeCriteria;

            if (findNodeStatusCriteria != null) {
                findNodeCriteria = new FindNodeCriteria(
                        findNodeStatusCriteria.getPageRequest(),
                        findNodeStatusCriteria.getSortList(),
                        new StringCriteria(),
                        null);
            } else {
                findNodeCriteria = new FindNodeCriteria();
            }

            final List<Node> nodes = nodeServiceProvider.get()
                    .find(findNodeCriteria)
                    .getValues();

            final ClusterState clusterState = clusterNodeManagerProvider.get().getClusterState();
            final String masterNodeName = clusterState.getMasterNodeName();

            final Comparator<Node> comparator = CompareUtil.buildCriteriaComparator(
                    FIELD_COMPARATORS, findNodeStatusCriteria, FindNodeStatusCriteria.FIELD_ID_NAME);

            final List<NodeStatusResult> resultList = nodes.stream()
                    .sorted(comparator)
                    .map(node ->
                            new NodeStatusResult(node, node.getName().equals(masterNodeName)))
                    .collect(Collectors.toList());
            response = new FetchNodeStatusResponse(resultList);

            documentEventLogProvider.get().search(
                    typeId,
                    query,
                    Node.class.getSimpleName(),
                    response.getPageResponse(),
                    null);
        } catch (final RuntimeException e) {
            LOGGER.error("Error finding nodes for {}: {}", findNodeStatusCriteria, LogUtil.exceptionMessage(e), e);
            documentEventLogProvider.get().search(
                    typeId,
                    query,
                    Node.class.getSimpleName(),
                    null,
                    e);
            throw e;
        }

        return response;
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED) // Too noisy and of little value
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
        } catch (final Exception e) {
            clusterNodeInfo = new ClusterNodeInfo();
            clusterNodeInfo.setNodeName(nodeName);
            clusterNodeInfo.setEndpointUrl(null);
            clusterNodeInfo.setError(e.getMessage());
        }

        return clusterNodeInfo;
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED) // Not a user action
    public Long ping(final String nodeName) {
        final long now = System.currentTimeMillis();

        final Supplier<String> urlSupplier = () -> ResourcePaths.buildAuthenticatedApiPath(
                NodeResource.BASE_PATH,
                NodeResource.PING_PATH_PART,
                nodeName);

        final Long ping;
        try {
            ping = nodeServiceProvider.get().remoteRestResult(
                    nodeName,
                    Long.class,
                    urlSupplier,
                    () ->
                            // If this is the node that was contacted then just return the latency
                            // we have incurred within this method.
                            System.currentTimeMillis() - now,
                    SyncInvoker::get);
        } catch (final WebApplicationException e) {
            throw new RuntimeException("Unable to connect to node '" + nodeName + "': "
                                       + e.getMessage());
        }

        Objects.requireNonNull(ping, "Null ping");
        return System.currentTimeMillis() - now;

//         These lines for testing in dev
//        final long ping2 = new Random().nextLong(600L);
//        if (ping2 > 100 && ping2 < 200) {
//            throw new RuntimeException("Unable to connect, blah blah blah");
//        }
//        return ping2;
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
