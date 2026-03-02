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
import stroom.node.shared.Node;
import stroom.node.shared.NodeGroup;
import stroom.node.shared.NodeGroupState;
import stroom.util.shared.Clearable;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class NodeGroupCacheImpl implements Clearable, NodeGroupCache {

    private static final String CACHE_NAME = "Node Group Cache";

    private final LoadingStroomCache<String, Optional<Set<String>>> cache;
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
    public Optional<Set<String>> getIncludedGroupNodes(final String name) {
        return cache.get(name);
    }

    private Optional<Set<String>> create(final String name) {
        final NodeGroup nodeGroup = nodeGroupDao.fetchByName(name);
        if (nodeGroup == null) {
            return Optional.empty();
        }
        final ResultPage<NodeGroupState> state = nodeGroupDao.getNodeGroupState(nodeGroup.getId());
        return Optional.of(state
                .getValues()
                .stream()
                .filter(NodeGroupState::isIncluded)
                .map(NodeGroupState::getNode)
                .map(Node::getName)
                .collect(Collectors.toSet()));
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
