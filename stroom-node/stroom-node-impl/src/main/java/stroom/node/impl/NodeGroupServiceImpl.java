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
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Objects;

@Singleton
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
        final NodeGroup result = securityContext.secureResult(AppPermission.MANAGE_NODES_PERMISSION, () -> {
            final NodeGroup nodeGroup = nodeGroupDao.create(NodeGroup.builder()
                    .name(name)
                    .stampAudit(securityContext)
                    .build());
            fireChange(EntityAction.CREATE, nodeGroup.getName());
            return nodeGroup;
        });
        return result;
    }

    @Override
    public NodeGroup update(final NodeGroup nodeGroup) {
        final NodeGroup result = securityContext.secureResult(AppPermission.MANAGE_NODES_PERMISSION, () -> {
            final NodeGroup persistedNodeGroup = nodeGroupDao.update(nodeGroup.copy()
                    .stampAudit(securityContext)
                    .build());
            fireChange(EntityAction.UPDATE, persistedNodeGroup.getName());
            return persistedNodeGroup;
        });
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
        securityContext.secure(AppPermission.MANAGE_NODES_PERMISSION, () -> {

            final NodeGroup nodeGroup = nodeGroupDao.fetchById(id);
            Objects.requireNonNull(nodeGroup, "NodeGroup with id " + id + " not found");
            nodeGroupDao.delete(id);
            fireChange(EntityAction.DELETE, nodeGroup.getName());
        });
    }

    @Override
    public NodeGroupState getNodeGroupState(final Integer id) {
        return securityContext.secureResult(AppPermission.MANAGE_NODES_PERMISSION, () ->
                nodeGroupDao.getNodeGroupState(id));
    }

    @Override
    public Boolean updateNodeGroupState(final NodeGroupChange change) {
        final Boolean result = securityContext.secureResult(AppPermission.MANAGE_NODES_PERMISSION, () ->
                nodeGroupDao.updateNodeGroupState(change));
        fireChange(EntityAction.UPDATE, NullSafe.get(change, NodeGroupChange::getNodeGroup, NodeGroup::getName));
        return result;
    }

    private void fireChange(final EntityAction action, final String nodeGroupName) {
        NullSafe.consume(entityEventBusProvider, Provider::get, entityEventBus -> {
            try {
                entityEventBus.buildFiring()
                        .withDocRef(EVENT_DOCREF)
                        .withAction(action)
                        .withStringData(nodeGroupName)
                        .fire();
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        });
    }
}
