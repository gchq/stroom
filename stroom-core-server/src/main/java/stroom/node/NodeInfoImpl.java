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

package stroom.node;

import stroom.entity.event.EntityEvent;
import stroom.entity.event.EntityEventHandler;
import stroom.entity.shared.Clearable;
import stroom.entity.shared.EntityAction;
import stroom.node.shared.Node;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@EntityEventHandler(type = Node.ENTITY_TYPE, action = {EntityAction.UPDATE, EntityAction.DELETE})
public class NodeInfoImpl implements NodeInfo, Clearable, EntityEvent.Handler {
    private final LocalNodeProvider localNodeProvider;
    private final NodeConfig nodeConfig;

    private volatile Node defaultNode;

    @Inject
    public NodeInfoImpl(final LocalNodeProvider localNodeProvider,
                        final NodeConfig nodeConfig) {
        this.localNodeProvider = localNodeProvider;
        this.nodeConfig = nodeConfig;
    }

    @Override
    public void clear() {
        defaultNode = null;
    }

    @Override
    public Node getDefaultNode() {
        if (defaultNode == null) {
            synchronized (this) {
                if (defaultNode == null && localNodeProvider != null) {
                    defaultNode = localNodeProvider.get();
                }

                if (defaultNode == null) {
                    throw new RuntimeException("Default node not set");
                }
            }
        }

        return defaultNode;
    }

    @Override
    public String getThisNodeName() {
        return nodeConfig.getNodeName();
    }

    @Override
    public void onChange(final EntityEvent event) {
        defaultNode = null;
    }
}
