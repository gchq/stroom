/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.node.api.NodeGroupCache;
import stroom.node.api.NodeGroupState;
import stroom.node.shared.NodeGroup;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Optional;
import java.util.Set;

@Singleton
@EntityEventHandler(type = NodeGroupService.ENTITY_TYPE, action = {
        EntityAction.UPDATE,
        EntityAction.CREATE,
        EntityAction.DELETE})
public class NodeGroupCacheImpl implements Clearable, NodeGroupCache, EntityEvent.Handler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(NodeGroupCacheImpl.class);

    private static final String CACHE_NAME = "Node Group Cache";

    private final LoadingStroomCache<String, Optional<NodeGroupState>> nameToNodeGroupCache;
    private final NodeGroupDao nodeGroupDao;

    @Inject
    public NodeGroupCacheImpl(final CacheManager cacheManager,
                              final NodeGroupDao nodeGroupDao,
                              final Provider<NodeConfig> nodeConfigProvider) {
        this.nodeGroupDao = nodeGroupDao;
        nameToNodeGroupCache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> nodeConfigProvider.get().getNodeGroupCache(),
                this::create);
    }

    @Override
    public Optional<NodeGroupState> getSelectedGroupNodes(final String name) {
        return nameToNodeGroupCache.get(name);
    }

    private Optional<NodeGroupState> create(final String name) {
        final NodeGroup nodeGroup = nodeGroupDao.fetchByName(name);
        if (nodeGroup == null) {
            return Optional.empty();
        }
        final Set<String> selectedNodes = nodeGroupDao.getSelectedNodesForGroup(nodeGroup.getId());
        return Optional.of(new NodeGroupState(nodeGroup, selectedNodes));
    }

    @Override
    public void clear() {
        nameToNodeGroupCache.clear();
    }

    @Override
    public void onChange(final EntityEvent event) {
        if (event != null) {
            LOGGER.debug("onChange: {}", event);
            final EntityAction action = event.getAction();
            final String profileName = event.getStringData();
            if (NullSafe.isNonBlankString(profileName)) {
                switch (action) {
                    case CREATE, UPDATE, DELETE -> nameToNodeGroupCache.invalidate(profileName);
                }
            } else {
                switch (action) {
                    // We don't know what the profile name is, so clear the cache.
                    case CREATE, UPDATE, DELETE -> clear();
                }
            }
        }
    }
}
