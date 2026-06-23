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
import stroom.node.api.NodeGroupInfo;
import stroom.node.shared.NodeGroup;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Optional;
import java.util.Set;

@Singleton
public class NodeGroupCacheImpl implements Clearable, NodeGroupCache {

    private static final String CACHE_NAME = "Node Group Cache";

    private final LoadingStroomCache<String, Optional<NodeGroupInfo>> cache;
    private final NodeGroupDao nodeGroupDao;

    @Inject
    public NodeGroupCacheImpl(final CacheManager cacheManager,
                              final NodeGroupDao nodeGroupDao,
                              final Provider<NodeConfig> nodeConfigProvider) {
        this.nodeGroupDao = nodeGroupDao;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> nodeConfigProvider.get().getNodeGroupCache(),
                this::create);
    }

    @Override
    public Optional<NodeGroupInfo> getIncludedGroupNodes(final String name) {
        return cache.get(name);
    }

    private Optional<NodeGroupInfo> create(final String name) {
        final NodeGroup nodeGroup = nodeGroupDao.fetchByName(name);
        if (nodeGroup == null) {
            return Optional.empty();
        }
        final Set<String> includedNodes = nodeGroupDao.getNodeGroupIncludedNodes(nodeGroup.getId());
        return Optional.of(new NodeGroupInfo(nodeGroup, includedNodes));
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
