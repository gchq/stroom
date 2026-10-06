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

import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.node.shared.FindNodeGroupRequest;
import stroom.node.shared.NodeGroup;
import stroom.node.shared.NodeGroupChange;
import stroom.node.shared.NodeGroupResource;
import stroom.node.shared.NodeGroupState;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
class NodeGroupResourceImpl implements NodeGroupResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(NodeGroupResourceImpl.class);

    private final Provider<NodeGroupService> nodeGroupServiceProvider;
    private final Provider<DocumentEventLog> documentEventLogProvider;

    @Inject
    NodeGroupResourceImpl(final Provider<NodeGroupService> nodeGroupServiceProvider,
                          final Provider<DocumentEventLog> documentEventLogProvider) {
        this.nodeGroupServiceProvider = nodeGroupServiceProvider;
        this.documentEventLogProvider = documentEventLogProvider;
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
    public NodeGroupState getNodeGroupState(final Integer id) {
        return nodeGroupServiceProvider.get().getNodeGroupState(id);
    }

    /// Logged by hand because the auto logger cannot work out what this method changes. It infers
    /// an update from the method name, but the response is a `Boolean` so it cannot be used as the
    /// 'after', and {@link NodeGroupChange} carries no id for the auto logger to fetch a
    /// before/after with. Left to the auto logger, both would be null and no audit event would be
    /// produced at all.
    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public Boolean updateNodeGroupState(final NodeGroupChange change) {
        final Integer nodeGroupId = NullSafe.get(change, NodeGroupChange::getNodeGroup, NodeGroup::getId);
        // This call changes the node group's name, enabled and invert selection flags as well as
        // rewriting its node membership, so the audit event has to carry all of it. A
        // NodeGroupChange holds exactly that combination, so read one either side of the call.
        final NodeGroupChange before = getChangeForAudit(nodeGroupId);

        try {
            final Boolean result = nodeGroupServiceProvider.get().updateNodeGroupState(change);
            logUpdate(before, getChangeForAudit(nodeGroupId), change, null);
            return result;
        } catch (final RuntimeException e) {
            logUpdate(before, getChangeForAudit(nodeGroupId), change, e);
            throw e;
        }
    }

    /// Reads a node group and its membership for the purposes of an audit event.
    ///
    /// Returns null rather than propagating, as failing to read the group for logging must not
    /// stop the update itself from being reported or, worse, fail the request.
    private NodeGroupChange getChangeForAudit(final Integer nodeGroupId) {
        if (nodeGroupId == null) {
            return null;
        }
        try {
            final NodeGroupService nodeGroupService = nodeGroupServiceProvider.get();
            final NodeGroup nodeGroup = nodeGroupService.fetchById(nodeGroupId);
            final NodeGroupState state = nodeGroupService.getNodeGroupState(nodeGroupId);
            if (nodeGroup == null && state == null) {
                return null;
            }
            return new NodeGroupChange(nodeGroup, NullSafe.get(state, NodeGroupState::getSelected));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message(
                    "getChangeForAudit() - Unable to read node group {} for logging: {}",
                    nodeGroupId, LogUtil.exceptionMessage(e)), e);
            return null;
        }
    }

    /// Logs the update, naming the node group so the event says which one was changed.
    ///
    /// If neither side could be read, falls back to the requested change so that an audit event is
    /// always produced. On the failure path that fallback goes in the 'before' slot, as the
    /// requested state was never reached and must not be presented as the 'after'.
    private void logUpdate(final NodeGroupChange before,
                           final NodeGroupChange after,
                           final NodeGroupChange change,
                           final Throwable ex) {
        final String typeId = StroomEventLoggingUtil.buildTypeId(this, "updateNodeGroupState");
        final NodeGroup nodeGroup = NullSafe.get(change, NodeGroupChange::getNodeGroup);
        final String verb = nodeGroup == null
                ? "Updating node group"
                : LogUtil.message("Updating node group \"{}\" id={}", nodeGroup.getName(), nodeGroup.getId());

        if (before == null && after == null) {
            if (ex == null) {
                documentEventLogProvider.get().update(null, change, typeId, verb, ex);
            } else {
                documentEventLogProvider.get().update(change, null, typeId, verb, ex);
            }
        } else {
            documentEventLogProvider.get().update(before, after, typeId, verb, ex);
        }
    }
}
