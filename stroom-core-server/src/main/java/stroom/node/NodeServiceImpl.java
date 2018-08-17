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

package stroom.node;


import stroom.entity.NamedEntityServiceImpl;
import stroom.entity.StroomEntityManager;
import stroom.node.shared.FindNodeCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.Rack;
import stroom.properties.api.PropertyService;
import stroom.security.Security;
import stroom.security.shared.PermissionNames;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * <p>
 * JPA implementation of a node manager.
 * </p>
 */
@Singleton
public class NodeServiceImpl extends NamedEntityServiceImpl<Node, FindNodeCriteria> implements NodeService {
    private final NodeServiceTransactionHelper nodeServiceUtil;

    @Inject
    NodeServiceImpl(final StroomEntityManager entityManager,
                    final Security security,
                    final NodeServiceTransactionHelper nodeServiceUtil) {
        super(entityManager, security);
        this.nodeServiceUtil = nodeServiceUtil;
    }

    Node getNode(final String name) {
        return nodeServiceUtil.getNode(name);
    }

    Rack getRack(final String name) {
        return nodeServiceUtil.getRack(name);
    }

    @Override
    public Class<Node> getEntityClass() {
        return Node.class;
    }

    @Override
    public FindNodeCriteria createCriteria() {
        return new FindNodeCriteria();
    }

    @Override
    protected String permission() {
        return PermissionNames.MANAGE_NODES_PERMISSION;
    }
}
