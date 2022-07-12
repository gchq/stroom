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
import stroom.node.api.NodeInfo;
import stroom.node.shared.Node;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.AuditUtil;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class NodeServiceImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(NodeServiceImpl.class);

    private final SecurityContext securityContext;
    private final NodeDao nodeDao;
    private final NodeInfo nodeInfo;
    private final UriFactory uriFactory;
    private final EntityEventBus entityEventBus;

    @Inject
    NodeServiceImpl(final SecurityContext securityContext,
                    final NodeDao nodeDao,
                    final NodeInfo nodeInfo,
                    final UriFactory uriFactory,
                    final EntityEventBus entityEventBus) {
        this.securityContext = securityContext;
        this.nodeDao = nodeDao;
        this.nodeInfo = nodeInfo;
        this.uriFactory = uriFactory;
        this.entityEventBus = entityEventBus;

        // Ensure the node record for this node is in the DB
        refreshNode();
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

    Node getNode(final String nodeName) {
        return securityContext.secureResult(() -> nodeDao.getNode(nodeName));
    }

    private synchronized void refreshNode() {
        securityContext.asProcessingUser(() -> {
            final String nodeName = nodeInfo.getThisNodeName();
            LOGGER.info("Creating node in DB with name: " + nodeName);
            if (nodeName == null || nodeName.isEmpty()) {
                throw new RuntimeException("Node name is not configured");
            }
            // See if we have a node record in the DB, we won't on first boot
            Node thisNode = nodeDao.getNode(nodeName);
            // Get the node endpoint URL from config or determine it
            final String endpointUrl = uriFactory.nodeUri("").toString();
            if (thisNode == null) {
                // This will start a new mini transaction to create the node record
                final Node node = new Node();
                node.setName(nodeName);
                node.setUrl(endpointUrl);
                LOGGER.info("Creating node record for {} with endpoint url {}",
                        node.getName(), node.getUrl());
                nodeDao.create(node);
            } else if (!endpointUrl.equals(thisNode.getUrl())) {
                // Endpoint URL in the DB is out of date so update it
                thisNode.setUrl(endpointUrl);
                LOGGER.info("Updating node endpoint url to {} for node {}", endpointUrl, thisNode.getName());
                update(thisNode);
            }
        });
    }
}
