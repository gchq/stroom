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

package stroom.node.impl;

import stroom.entity.shared.EntityEvent;
import stroom.entity.shared.EntityEventHandler;
import stroom.util.shared.Clearable;
import stroom.entity.shared.EntityAction;
import stroom.node.api.NodeInfo;
import stroom.node.shared.Node;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@EntityEventHandler(type = Node.ENTITY_TYPE, action = {EntityAction.UPDATE, EntityAction.DELETE})
public class NodeInfoImpl implements NodeInfo, Clearable, EntityEvent.Handler {
    private final NodeServiceTransactionHelper nodeServiceTransactionHelper;
    private final NodeConfig nodeConfig;

    private volatile Node thisNode;

    @Inject
    public NodeInfoImpl(final NodeServiceTransactionHelper nodeServiceTransactionHelper,
                        final NodeConfig nodeConfig) {
        this.nodeServiceTransactionHelper = nodeServiceTransactionHelper;
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
                    thisNode = nodeServiceTransactionHelper.getNode(nodeConfig.getNodeName());

                    if (thisNode == null) {
                        // This will start a new mini transaction for the update
                        thisNode = nodeServiceTransactionHelper.buildNode(nodeConfig.getNodeName());
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
