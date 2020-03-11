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

import com.codahale.metrics.health.HealthCheck.Result;
import event.logging.BaseAdvancedQueryOperator.And;
import event.logging.Query;
import event.logging.Query.Advanced;
import stroom.cluster.api.ClusterNodeManager;
import stroom.event.logging.api.DocumentEventLog;
import stroom.node.api.FindNodeCriteria;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.shared.ClusterNodeInfo;
import stroom.node.shared.FetchNodeStatusResponse;
import stroom.node.shared.Node;
import stroom.node.shared.NodeResource;
import stroom.node.shared.NodeStatusResult;
import stroom.util.HasHealthCheck;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResourcePaths;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

// TODO : @66 add event logging
class NodeResourceImpl implements NodeResource, HasHealthCheck {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(NodeResourceImpl.class);

    private final NodeServiceImpl nodeService;
    private final NodeInfo nodeInfo;
    private final ClusterNodeManager clusterNodeManager;
    private final WebTargetFactory webTargetFactory;
    private final DocumentEventLog documentEventLog;

    @Inject
    NodeResourceImpl(final NodeServiceImpl nodeService,
                     final NodeInfo nodeInfo,
                     final ClusterNodeManager clusterNodeManager,
                     final WebTargetFactory webTargetFactory,
                     final DocumentEventLog documentEventLog) {
        this.nodeService = nodeService;
        this.nodeInfo = nodeInfo;
        this.clusterNodeManager = clusterNodeManager;
        this.webTargetFactory = webTargetFactory;
        this.documentEventLog = documentEventLog;
    }

    @Override
    public FetchNodeStatusResponse list() {
        FetchNodeStatusResponse response = null;

        final Query query = new Query();
        final Advanced advanced = new Advanced();
        query.setAdvanced(advanced);
        final And and = new And();
        advanced.getAdvancedQueryItems().add(and);

        try {
            final List<Node> nodes = nodeService.find(new FindNodeCriteria()).getValues();
            Node master = null;
            for (final Node node : nodes) {
                if (node.isEnabled()) {
                    if (master == null || master.getPriority() < node.getPriority()) {
                        master = node;
                    }
                }
            }

            final List<NodeStatusResult> resultList = new ArrayList<>();
            for (final Node node : nodes) {
                resultList.add(new NodeStatusResult(node, node.equals(master)));
            }
            response = new FetchNodeStatusResponse(resultList);

            documentEventLog.search("List Nodes", query, Node.class.getSimpleName(), response.getPageResponse(), null);
        } catch (final RuntimeException e) {
            documentEventLog.search("List Nodes", query, Node.class.getSimpleName(), null, e);
        }

        return response;
    }

    @Override
    public ClusterNodeInfo info(final String nodeName) {
        ClusterNodeInfo clusterNodeInfo = null;
        String nodeUrl = null;
        try {
            final long now = System.currentTimeMillis();

            // If this is the node that was contacted then just return our local info.
            if (NodeCallUtil.executeLocally(nodeInfo, nodeName)) {
                clusterNodeInfo = clusterNodeManager.getClusterNodeInfo();

            } else {
                String url = NodeCallUtil.getBaseEndpointUrl(nodeService, nodeName);
                url += ResourcePaths.API_ROOT_PATH + NodeResource.BASE_PATH;
                url += nodeName;
                final Response response = webTargetFactory
                        .create(url)
                        .request(MediaType.APPLICATION_JSON)
                        .get();
                if (response.getStatus() != 200) {
                    throw new WebApplicationException(response);
                }
                clusterNodeInfo = response.readEntity(ClusterNodeInfo.class);
                if (clusterNodeInfo == null) {
                    throw new RuntimeException("Unable to contact node \"" + nodeName + "\" at URL: " + url);
                }
            }

            clusterNodeInfo.setPing(System.currentTimeMillis() - now);

            documentEventLog.view(clusterNodeInfo, null);

        } catch (final RuntimeException e) {
            documentEventLog.view(clusterNodeInfo, e);

            clusterNodeInfo = new ClusterNodeInfo();
            clusterNodeInfo.setNodeName(nodeName);
            clusterNodeInfo.setEndpointUrl(nodeUrl);
            clusterNodeInfo.setError(e.getMessage());
        }

        return clusterNodeInfo;
    }

    @Override
    public Long ping(final String nodeName) {
        final long now = System.currentTimeMillis();

        // If this is the node that was contacted then just return the latency we have incurred within this method.
        if (NodeCallUtil.executeLocally(nodeInfo, nodeName)) {
            return System.currentTimeMillis() - now;
        } else {
            final String url = NodeCallUtil.getBaseEndpointUrl(nodeService, nodeName)
                + ResourcePaths.buildAuthenticatedApiPath(
                NodeResource.BASE_PATH,
                nodeName,
                "/ping");

            try {
                final Response response = webTargetFactory
                    .create(url)
                    .request(MediaType.APPLICATION_JSON)
                    .get();
                if (response.getStatus() != 200) {
                    throw new WebApplicationException(response);
                }
                final Long ping = response.readEntity(Long.class);
                Objects.requireNonNull(ping, "Null ping");
                return System.currentTimeMillis() - now;
            } catch (Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }
    }

    @Override
    public void setPriority(final String nodeName, final Integer priority) {
        modifyNode(nodeName, node -> node.setPriority(priority));
    }

    @Override
    public void setEnabled(final String nodeName, final Boolean enabled) {
        modifyNode(nodeName, node -> node.setEnabled(enabled));
    }

    private void modifyNode(final String nodeName,
                            final Consumer<Node> mutation) {
        Node node = null;
        Node before = null;
        Node after = null;

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

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}