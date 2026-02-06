/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.node.shared.FindNodeGroupRequest;
import stroom.node.shared.NodeGroup;
import stroom.node.shared.NodeGroupChange;
import stroom.node.shared.NodeGroupState;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.AuditUtil;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
@EntityEventHandler(type = NodeGroupService.ENTITY_TYPE, action = {
        EntityAction.UPDATE,
        EntityAction.CREATE,
        EntityAction.DELETE})
public class NodeGroupServiceImpl implements NodeGroupService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(NodeGroupServiceImpl.class);

    private final NodeGroupDao nodeGroupDao;
    private final SecurityContext securityContext;
    private final Provider<EntityEventBus> entityEventBusProvider;

    @Inject
    public NodeGroupServiceImpl(final NodeGroupDao nodeGroupDao,
                                final SecurityContext securityContext,
                                final Provider<EntityEventBus> entityEventBusProvider) {
        this.nodeGroupDao = nodeGroupDao;
        this.securityContext = securityContext;
        this.entityEventBusProvider = entityEventBusProvider;
    }

    @Override
    public ResultPage<NodeGroup> find(final FindNodeGroupRequest request) {
        return securityContext.secureResult(() -> nodeGroupDao.find(request));
    }

    @Override
    public NodeGroup create(final String name) {
        final NodeGroup result = securityContext.secureResult(AppPermission.MANAGE_NODES_PERMISSION, () ->
                nodeGroupDao.create(AuditUtil.stamp(securityContext, NodeGroup.builder().name(name))));
        fireChange(EntityAction.CREATE);
        return result;
    }

    @Override
    public NodeGroup update(final NodeGroup nodeGroup) {
        final NodeGroup result = securityContext.secureResult(AppPermission.MANAGE_NODES_PERMISSION, () ->
                nodeGroupDao.update(AuditUtil.stamp(securityContext, nodeGroup.copy())));
        fireChange(EntityAction.UPDATE);
        return result;
    }

    @Override
    public NodeGroup fetchByName(final String name) {
        return securityContext.secureResult(() -> nodeGroupDao.fetchByName(name));
    }

    @Override
    public NodeGroup fetchById(final int id) {
        return securityContext.secureResult(() -> nodeGroupDao.fetchById(id));
    }

    @Override
    public void delete(final int id) {
        securityContext.secure(AppPermission.MANAGE_NODES_PERMISSION, () -> nodeGroupDao.delete(id));
        fireChange(EntityAction.DELETE);
    }

    @Override
    public ResultPage<NodeGroupState> getNodeGroupState(final Integer id) {
        return nodeGroupDao.getNodeGroupState(id);
    }

    @Override
    public boolean updateNodeGroupState(final NodeGroupChange change) {
        return nodeGroupDao.updateNodeGroupState(change);
    }

    private void fireChange(final EntityAction action) {
        if (entityEventBusProvider != null) {
            try {
                final EntityEventBus entityEventBus = entityEventBusProvider.get();
                if (entityEventBus != null) {
                    entityEventBus.fire(new EntityEvent(EVENT_DOCREF, action));
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
    }
}
