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

import stroom.event.logging.rs.api.AutoLogged;
import stroom.node.shared.FindNodeGroupRequest;
import stroom.node.shared.NodeGroup;
import stroom.node.shared.NodeGroupChange;
import stroom.node.shared.NodeGroupResource;
import stroom.node.shared.NodeGroupState;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
class NodeGroupResourceImpl implements NodeGroupResource {

    private final Provider<NodeGroupService> nodeGroupServiceProvider;

    @Inject
    NodeGroupResourceImpl(final Provider<NodeGroupService> nodeGroupServiceProvider) {
        this.nodeGroupServiceProvider = nodeGroupServiceProvider;
    }

    @Override
    public ResultPage<NodeGroup> find(final FindNodeGroupRequest request) {
        return nodeGroupServiceProvider.get().find(request);
    }

    @Override
    public NodeGroup create(final String name) {
        return nodeGroupServiceProvider.get().create(name);
    }

    @Override
    public NodeGroup fetchById(final Integer id) {
        return nodeGroupServiceProvider.get().fetchById(id);
    }

    @Override
    public NodeGroup fetchByName(final String name) {
        return nodeGroupServiceProvider.get().fetchByName(name);
    }

    @Override
    public NodeGroup update(final Integer id, final NodeGroup indexVolumeGroup) {
        return nodeGroupServiceProvider.get().update(indexVolumeGroup);
    }

    @Override
    public Boolean delete(final Integer id) {
        nodeGroupServiceProvider.get().delete(id);
        return true;
    }

    @Override
    public ResultPage<NodeGroupState> getNodeGroupState(final Integer id) {
        return nodeGroupServiceProvider.get().getNodeGroupState(id);
    }

    @Override
    public Boolean updateNodeGroupState(final NodeGroupChange change) {
        return nodeGroupServiceProvider.get().updateNodeGroupState(change);
    }
}
