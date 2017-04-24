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

package stroom.node.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import stroom.entity.server.NamedEntityServiceImpl;
import stroom.entity.server.util.StroomEntityManager;
import stroom.node.shared.FindNodeCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.NodeService;
import stroom.node.shared.Rack;
import stroom.security.Secured;

import javax.inject.Inject;

/**
 * <p>
 * JPA implementation of a node manager.
 * </p>
 */
@Transactional
@Secured(Node.MANAGE_NODES_PERMISSION)
@Component("nodeService")
public class NodeServiceImpl extends NamedEntityServiceImpl<Node, FindNodeCriteria>
        implements NodeService, NodeServiceGetDefaultNode {
    private final NodeServiceTransactionHelper nodeServiceUtil;
    private String nodeName;
    private String rackName;

    @Inject
    NodeServiceImpl(final StroomEntityManager entityManager,
                    final NodeServiceTransactionHelper nodeServiceUtil,
                    @Value("#{propertyConfigurer.getProperty('stroom.node')}") final String nodeName,
                    @Value("#{propertyConfigurer.getProperty('stroom.rack')}") final String rackName) {
        super(entityManager);

        this.nodeServiceUtil = nodeServiceUtil;
        this.nodeName = nodeName;
        this.rackName = rackName;
    }

    public Node getNode(final String name) {
        return nodeServiceUtil.getNode(name);
    }

    public Rack getRack(final String name) {
        return nodeServiceUtil.getRack(name);
    }

    @Override
    @Transactional(readOnly = true)
    public Node getDefaultNode() {
        Node node = getNode(nodeName);

        if (node == null) {
            // This will start a new mini transaction for the update
            node = nodeServiceUtil.buildNode(nodeName, rackName);
        }

        return node;
    }

    public void setNodeName(final String nodeName) {
        this.nodeName = nodeName;
    }

    public void setRackName(final String rackName) {
        this.rackName = rackName;
    }

    @Override
    public Class<Node> getEntityClass() {
        return Node.class;
    }

    @Override
    public FindNodeCriteria createCriteria() {
        return new FindNodeCriteria();
    }
}
