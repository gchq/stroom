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

package stroom.node.impl.db;

import stroom.entity.shared.EntityAction;
import stroom.entity.shared.EntityEvent;
import stroom.entity.shared.EntityEventHandler;
import stroom.node.api.NodeInfo;
import stroom.node.impl.InternalNodeService;
import stroom.node.impl.NodeConfig;
import stroom.node.shared.Node;
import stroom.util.shared.Clearable;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@EntityEventHandler(type = Node.ENTITY_TYPE, action = {EntityAction.UPDATE, EntityAction.DELETE})
public class NodeInfoImpl implements NodeInfo, Clearable, EntityEvent.Handler {
    private final InternalNodeService nodeService;
    private final NodeConfig nodeConfig;

    private volatile Node thisNode;

    @Inject
    public NodeInfoImpl(final InternalNodeService nodeService,
                        final NodeConfig nodeConfig) {
        this.nodeService = nodeService;
        this.nodeConfig = nodeConfig;
    }

    @Override
    public void clear() {
        thisNode = null;
    }

    @Override
    public Node getThisNode() {
        if (thisNode == null) {
            synchronized (this) {
                if (thisNode == null) {
                    thisNode = nodeService.getNode(nodeConfig.getNodeName());

                    if (thisNode == null) {
                        // This will start a new mini transaction for the update
                        thisNode = nodeService.create(nodeConfig.getNodeName());
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
    public String getThisNodeName() {
        return nodeConfig.getNodeName();
    }

    @Override
    public void onChange(final EntityEvent event) {
        thisNode = null;
    }
}
