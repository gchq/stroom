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

import stroom.entity.shared.EntityAction;
import stroom.entity.shared.EntityEvent;
import stroom.entity.shared.EntityEventHandler;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.node.shared.FindNodeCriteria;
import stroom.node.shared.Node;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.AuditUtil;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.Clearable;
import stroom.util.shared.PermissionException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
@EntityEventHandler(type = Node.ENTITY_TYPE, action = {EntityAction.UPDATE, EntityAction.DELETE})
public class NodeServiceImpl implements NodeService, Clearable, EntityEvent.Handler {
    private final SecurityContext securityContext;
    private final NodeDao nodeDao;
    private final NodeInfo nodeInfo;
    private volatile Node thisNode;

    @Inject
    NodeServiceImpl(final SecurityContext securityContext,
                    final NodeDao nodeDao,
                    final NodeInfo nodeInfo) {
        this.securityContext = securityContext;
        this.nodeDao = nodeDao;
        this.nodeInfo = nodeInfo;
    }

    Node update(final Node node) {
        if (!securityContext.hasAppPermission(PermissionNames.MANAGE_NODES_PERMISSION)) {
            throw new PermissionException(securityContext.getUserId(), "You are not authorised to update this node");
        }
        AuditUtil.stamp(securityContext.getUserId(), node);
        return nodeDao.update(node);
    }

    BaseResultList<Node> find(final FindNodeCriteria criteria) {
        if (!securityContext.hasAppPermission(PermissionNames.MANAGE_NODES_PERMISSION)) {
            throw new PermissionException(securityContext.getUserId(), "You are not authorised to find nodes");
        }
        ensureNodeCreated();
        return nodeDao.find(criteria);
    }


    @Override
    public List<String> findNodeNames(final FindNodeCriteria criteria) {
        return find(criteria).stream().map(Node::getName).collect(Collectors.toList());
    }

    @Override
    public String getClusterUrl(final String nodeName) {
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

    Node getNode(final String nodeName) {
        if (!securityContext.hasAppPermission(PermissionNames.MANAGE_NODES_PERMISSION)) {
            throw new PermissionException(securityContext.getUserId(), "You are not authorised to get this node");
        }

        ensureNodeCreated();
        return nodeDao.getNode(nodeName);
    }

    private void ensureNodeCreated() {
        // Ensure we have created a node for ourselves.
        getThisNode();
    }

    Node getThisNode() {
        if (thisNode == null) {
            synchronized (this) {
                if (thisNode == null) {
                    thisNode = nodeDao.getNode(nodeInfo.getThisNodeName());

                    if (thisNode == null) {
                        // This will start a new mini transaction for the update
                        final Node node = new Node();
                        node.setName(nodeInfo.getThisNodeName());
                        thisNode = nodeDao.create(node);
                    }
                }

                if (thisNode == null) {
                    throw new RuntimeException("Default node not set");
                }
            }
        }

        return thisNode;
    }

    @Override
    public void onChange(final EntityEvent event) {
        thisNode = null;
    }

    @Override
    public void clear() {
        thisNode = null;
    }
}
