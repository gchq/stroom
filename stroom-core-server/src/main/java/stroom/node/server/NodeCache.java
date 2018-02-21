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

import stroom.entity.server.event.EntityEvent;
import stroom.entity.server.event.EntityEventHandler;
import stroom.entity.shared.Clearable;
import stroom.entity.shared.EntityAction;
import stroom.node.shared.Node;

import javax.annotation.Resource;

@EntityEventHandler(type = Node.ENTITY_TYPE, action = {EntityAction.UPDATE, EntityAction.DELETE})
public class NodeCache implements Clearable, EntityEvent.Handler {
    private volatile NodeServiceGetDefaultNode nodeService;

    private volatile Node defaultNode;

    public NodeCache() {
    }

    public NodeCache(final Node aDefault) {
        defaultNode = aDefault;
    }

    @Override
    public void clear() {
        defaultNode = null;
    }

    public Node getDefaultNode() {
        if (defaultNode == null) {
            synchronized (this) {
                if (defaultNode == null) {
                    defaultNode = nodeService.getDefaultNode();
                }

                if (defaultNode == null) {
                    throw new RuntimeException("Default node not set");
                }
            }
        }

        return defaultNode;
    }

    @Override
    public void onChange(final EntityEvent event) {
        defaultNode = null;
    }

    @Resource
    public void setNodeService(final NodeServiceGetDefaultNode nodeService) {
        this.nodeService = nodeService;
    }
}
