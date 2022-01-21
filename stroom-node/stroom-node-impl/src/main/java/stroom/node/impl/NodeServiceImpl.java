/*
 * Copyright 2018 Crown Copyright
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

import stroom.config.common.UriFactory;
import stroom.docref.DocRef;
import stroom.node.api.FindNodeCriteria;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.node.shared.Node;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.AuditUtil;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.rest.RestUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

@Singleton
@EntityEventHandler(type = Node.ENTITY_TYPE, action = {EntityAction.UPDATE, EntityAction.DELETE})
public class NodeServiceImpl implements NodeService, Clearable, EntityEvent.Handler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(NodeServiceImpl.class);

    private final SecurityContext securityContext;
    private final NodeDao nodeDao;
    private final NodeInfo nodeInfo;
    private final UriFactory uriFactory;
    private volatile Node thisNode;
    private final EntityEventBus entityEventBus;
    private final WebTargetFactory webTargetFactory;

    @Inject
    NodeServiceImpl(final SecurityContext securityContext,
                    final NodeDao nodeDao,
                    final NodeInfo nodeInfo,
                    final UriFactory uriFactory,
                    final EntityEventBus entityEventBus,
                    final WebTargetFactory webTargetFactory) {
        this.securityContext = securityContext;
        this.nodeDao = nodeDao;
        this.nodeInfo = nodeInfo;
        this.uriFactory = uriFactory;
        this.entityEventBus = entityEventBus;
        this.webTargetFactory = webTargetFactory;

        // Ensure the node record for this node is in the DB
        securityContext.asProcessingUser(this::ensureNodeCreated);
    }

    Node update(final Node node) {
        if (!securityContext.hasAppPermission(PermissionNames.MANAGE_NODES_PERMISSION)) {
            throw new PermissionException(securityContext.getUserId(), "You are not authorised to update nodes");
        }
        AuditUtil.stamp(securityContext.getUserId(), node);
        final Node updated = nodeDao.update(node);

        // Let all nodes know that the node has changed.
        EntityEvent.fire(entityEventBus,
                new DocRef(Node.ENTITY_TYPE, String.valueOf(updated.getId()), updated.getName()),
                EntityAction.UPDATE);

        return updated;
    }

    ResultPage<Node> find(final FindNodeCriteria criteria) {
        return securityContext.secureResult(() ->
                nodeDao.find(criteria));
    }

    @Override
    public List<String> findNodeNames(final FindNodeCriteria criteria) {
        return find(criteria)
                .getValues()
                .stream()
                .map(Node::getName)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getEnabledNodesByPriority() {

        final FindNodeCriteria findNodeCriteria = new FindNodeCriteria();
        findNodeCriteria.setEnabled(true);

        return find(findNodeCriteria)
                .getValues()
                .stream()
                .sorted(Comparator.comparingInt(Node::getPriority)
                        .reversed()
                        .thenComparing(Node::getName))
                .map(Node::getName)
                .collect(Collectors.toList());
    }

    @Override
    public String getBaseEndpointUrl(final String nodeName) {
        final Node node = getNode(nodeName);
        if (node != null) {
            return node.getUrl();
        }
        return null;
    }

    @Override
    public boolean isEnabled(final String nodeName) {
        final Node node = getNode(nodeName);
        if (node != null) {
            return node.isEnabled();
        }
        return false;
    }

    @Override
    public int getPriority(final String nodeName) {
        final Node node = getNode(nodeName);
        if (node != null) {
            return node.getPriority();
        }
        return -1;
    }

    @Override
    public <T_RESP> T_RESP remoteRestResult(final String nodeName,
                                            final Supplier<String> fullPathSupplier,
                                            final Supplier<T_RESP> localSupplier,
                                            final Function<Invocation.Builder, Response> responseBuilderFunc,
                                            final Function<Response, T_RESP> responseMapper,
                                            final Map<String, Object> queryParams) {
        RestUtil.requireNonNull(nodeName, "nodeName not supplied");

        final T_RESP resp;

        // If this is the node that was contacted then just resolve it locally
        if (NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {

            LOGGER.debug(() -> LogUtil.message("Executing {} locally", fullPathSupplier.get()));
            resp = localSupplier.get();

        } else {
            // A different node to make a rest call to the required node
            final String url = NodeCallUtil.getBaseEndpointUrl(
                    nodeInfo,
                    this,
                    nodeName) + fullPathSupplier.get();
            LOGGER.debug("Fetching value from remote node at {}", url);
            try {
                final Builder builder = createBuilder(queryParams, url);

                final Response response = responseBuilderFunc.apply(builder);

                LOGGER.debug(() -> "Response status " + response.getStatus());
                if (response.getStatus() != Status.OK.getStatusCode()) {
                    throw new WebApplicationException(response);
                }
                resp = responseMapper.apply(response);

                Objects.requireNonNull(resp, "Null response calling url " + url);
            } catch (final Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }
        return resp;
    }

    @Override
    public void remoteRestCall(final String nodeName,
                               final Supplier<String> fullPathSupplier,
                               final Runnable localRunnable,
                               final Function<Builder, Response> responseBuilderFunc,
                               final Map<String, Object> queryParams) {

        RestUtil.requireNonNull(nodeName, "nodeName not supplied");

        // If this is the node that was contacted then just resolve it locally
        if (NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {

            LOGGER.debug(() -> LogUtil.message("Executing {} locally", fullPathSupplier.get()));
            localRunnable.run();
        } else {
            // A different node to make a rest call to the required node
            final String url = NodeCallUtil.getBaseEndpointUrl(
                    nodeInfo,
                    this,
                    nodeName) + fullPathSupplier.get();
            LOGGER.debug("Calling remote node at {}", url);
            try {
                final Builder builder = createBuilder(queryParams, url);

                final Response response = responseBuilderFunc.apply(builder);

                LOGGER.debug(() -> "Response status " + response.getStatus());
                if (response.getStatus() != Status.OK.getStatusCode()) {
                    throw new WebApplicationException(response);
                }
            } catch (final Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }
    }

    private Builder createBuilder(final Map<String, Object> queryParams, final String url) {
        WebTarget webTarget = webTargetFactory
                .create(url);

        if (queryParams != null) {
            for (final Entry<String, Object> entry : queryParams.entrySet()) {
                if (entry.getKey() != null) {
                    webTarget = webTarget.queryParam(entry.getKey(), entry.getValue());
                }
            }
        }

        return webTarget
                .request(MediaType.APPLICATION_JSON);
    }

    Node getNode(final String nodeName) {
        return securityContext.secureResult(() -> nodeDao.getNode(nodeName));
    }

    private void ensureNodeCreated() {
        // Ensure we have created a node for ourselves.
        getThisNode();
    }

    Node getThisNode() {
        if (thisNode == null) {
            synchronized (this) {
                if (thisNode == null) {
                    refreshNode();
                }

                if (thisNode == null) {
                    throw new RuntimeException("Default node not set");
                }
            }
        } else {
            if (!uriFactory.nodeUri("").toString().equals(thisNode.getUrl())) {
                // Endpoint url has changed in config so update the node record
                refreshNode();
            }
        }

        return thisNode;
    }

    private synchronized void refreshNode() {

        final String nodeName = nodeInfo.getThisNodeName();
        if (nodeName == null || nodeName.isEmpty()) {
            throw new RuntimeException("Node name is not configured");
        }
        // See if we have a node record in the DB, we won't on first boot
        thisNode = nodeDao.getNode(nodeName);

        // Get the node endpoint URL from config or determine it
        final String endpointUrl = uriFactory.nodeUri("").toString();
        if (thisNode == null) {
            // This will start a new mini transaction to create the node record
            final Node node = new Node();
            node.setName(nodeName);
            node.setUrl(endpointUrl);
            LOGGER.info("Creating node record for {} with endpoint url {}",
                    node.getName(), node.getUrl());
            thisNode = nodeDao.create(node);
        } else if (!endpointUrl.equals(thisNode.getUrl())) {
            // Endpoint URL in the DB is out of date so update it
            thisNode.setUrl(endpointUrl);
            LOGGER.info("Updating node endpoint url to {} for node {}", endpointUrl, thisNode.getName());
            update(thisNode);
        }
    }

    @Override
    public void onChange(final EntityEvent event) {
        final Node node = thisNode;
        if (node != null) {
            if (Node.ENTITY_TYPE.equals(event.getDocRef().getType()) &&
                    String.valueOf(node.getId()).equals(event.getDocRef().getUuid())) {
                thisNode = null;
            }
        }
    }

    @Override
    public void clear() {
        thisNode = null;
        // Ensure the node record for this node is in the DB
        securityContext.asProcessingUser(this::ensureNodeCreated);
    }
}
