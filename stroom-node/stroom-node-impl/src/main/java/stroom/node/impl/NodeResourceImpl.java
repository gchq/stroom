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
import stroom.node.shared.ClusterNodeInfo;
import stroom.node.shared.FetchNodeStatusResponse;
import stroom.node.shared.FindNodeCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.NodeResource;
import stroom.node.shared.NodeStatusResult;
import stroom.security.api.ClientSecurityUtil;
import stroom.security.api.SecurityContext;
import stroom.util.HasHealthCheck;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.RestResource;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

// TODO : @66 add event logging
public class NodeResourceImpl implements NodeResource, RestResource, HasHealthCheck {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(NodeResourceImpl.class);

    private final NodeServiceImpl nodeService;
    private final ClusterNodeManager clusterNodeManager;
    private final Provider<Client> clientProvider;
    private final DocumentEventLog documentEventLog;
    private final SecurityContext securityContext;

    @Inject
    private NodeResourceImpl(final NodeServiceImpl nodeService,
                             final ClusterNodeManager clusterNodeManager,
                             final Provider<Client> clientProvider,
                             final DocumentEventLog documentEventLog,
                             final SecurityContext securityContext) {
        this.nodeService = nodeService;
        this.clusterNodeManager = clusterNodeManager;
        this.clientProvider = clientProvider;
        this.documentEventLog = documentEventLog;
        this.securityContext = securityContext;
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
            final List<Node> nodes = nodeService.find(new FindNodeCriteria());
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

            final Node node = nodeService.getNode(nodeName);
            if (node == null) {
                throw new RuntimeException("Unknown node: " + nodeName);
            }

            final Node thisNode = nodeService.getThisNode();
            if (thisNode == null) {
                throw new RuntimeException("This node not set");
            }

            // If this is the node that was contacted then just return our local info.
            if (node.getId().equals(thisNode.getId())) {
                clusterNodeInfo = clusterNodeManager.getClusterNodeInfo();

            } else {
                nodeUrl = node.getUrl();
                if (nodeUrl == null || nodeUrl.trim().length() == 0) {
                    throw new RuntimeException("Remote node has no URL set");
                }

                // A normal cluster call url is something like "http://fqdn:8080/stroom/clustercall.rpc"

                String url = fixUrl(nodeUrl);
                url += "/api/node/" + node.getName();

                final Client client = clientProvider.get();
                final WebTarget webTarget = client.target(url);
                final Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
                ClientSecurityUtil.addAuthorisationHeader(invocationBuilder, securityContext);

                final Response response = invocationBuilder.get();
                if (response.getStatus() != 200) {
                    throw new WebApplicationException(response);
                }
                clusterNodeInfo = response.readEntity(ClusterNodeInfo.class);
                if (clusterNodeInfo == null) {
                    throw new RuntimeException("Unable to contact node \"" + node.getName() + "\" at URL: " + url);
                }
            }

            clusterNodeInfo.setPing(System.currentTimeMillis() - now);

            documentEventLog.view(clusterNodeInfo, null);

        } catch (final RuntimeException e) {
            documentEventLog.view(clusterNodeInfo, e);

            clusterNodeInfo = new ClusterNodeInfo();
            clusterNodeInfo.setNodeName(nodeName);
            clusterNodeInfo.setClusterURL(nodeUrl);
            clusterNodeInfo.setError(e.getMessage());
        }

        return clusterNodeInfo;
    }

    private String fixUrl(String url) {
        int index = url.lastIndexOf("/stroom/clustercall.rpc");
        if (index != -1) {
            url = url.substring(0, index);
        }
        index = url.lastIndexOf("/clustercall.rpc");
        if (index != -1) {
            url = url.substring(0, index);
        }
        return url;
    }

    @Override
    public Long ping(final String nodeName) {
        try {
            final long now = System.currentTimeMillis();
            final Node node = nodeService.getNode(nodeName);
            if (node == null) {
                throw new RuntimeException("Unknown node '" + nodeName + "'");
            }

            final Node thisNode = nodeService.getThisNode();
            if (thisNode == null) {
                throw new RuntimeException("This node not set");
            }

            // If this is the node that was contacted then just return the latency we have incurred within this method.
            if (node.getId().equals(thisNode.getId())) {
                return System.currentTimeMillis() - now;
            }

            if (node.getUrl() == null || node.getUrl().trim().length() == 0) {
                throw new RuntimeException("Remote node '" + nodeName + "' has no URL set");
            }

            // A normal cluster call url is something like "http://fqdn:8080/stroom/clustercall.rpc"

            String url = fixUrl(node.getUrl());
            url += "/api/node/" + node.getName() + "/ping";

            final Client client = clientProvider.get();
            final WebTarget webTarget = client.target(url);
            final Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
            ClientSecurityUtil.addAuthorisationHeader(invocationBuilder, securityContext);

            final Response response = invocationBuilder.get();
            if (response.getStatus() != 200) {
                throw new WebApplicationException(response);
            }
            final Long ping = response.readEntity(Long.class);
            Objects.requireNonNull(ping, "Null ping");
            return System.currentTimeMillis() - now;

        } catch (final WebApplicationException e) {
            throw e;
        } catch (final RuntimeException e) {
            throw new ServerErrorException(Status.INTERNAL_SERVER_ERROR.getStatusCode(), e);
//            throw new ServerErrorException(e.getMessage(), Status.INTERNAL_SERVER_ERROR.getStatusCode());
        }
    }

    @Override
    public void setUrl(final String nodeName, final String url) {
        modifyNode(nodeName, node -> node.setUrl(url));
    }

    @Override
    public void setPriority(final String nodeName, final Integer priority) {
        modifyNode(nodeName, node -> node.setPriority(priority));
    }

    @Override
    public void setEnabled(final String nodeName, final Boolean enabled) {
        modifyNode(nodeName, node -> node.setEnabled(enabled));
    }

    private void modifyNode(final String nodeName, final Consumer<Node> mutation) {
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