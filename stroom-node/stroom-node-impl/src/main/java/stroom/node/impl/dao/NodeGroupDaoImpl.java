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

package stroom.node.impl.dao;

import stroom.db.util.JooqUtil;
import stroom.node.impl.NodeGroupDao;
import stroom.node.impl.db.NodeDbConnProvider;
import stroom.node.impl.db.jooq.tables.NodeGroupLink;
import stroom.node.shared.FindNodeGroupRequest;
import stroom.node.shared.NodeGroup;
import stroom.node.shared.NodeGroupChange;
import stroom.node.shared.NodeGroupState;
import stroom.util.exception.DataChangedException;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.exception.DataAccessException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static stroom.node.impl.db.jooq.tables.Node.NODE;
import static stroom.node.impl.db.jooq.tables.NodeGroup.NODE_GROUP;
import static stroom.node.impl.db.jooq.tables.NodeGroupLink.NODE_GROUP_LINK;

public class NodeGroupDaoImpl implements NodeGroupDao {

    private static final RecordToNodeGroupMapper RECORD_TO_NODE_GROUP_MAPPER = new RecordToNodeGroupMapper();

    private static final Map<String, Field<?>> FIELD_MAP = Map.of(
            FindNodeGroupRequest.FIELD_ID_NAME, NODE_GROUP.NAME,
            FindNodeGroupRequest.FIELD_ID_ENABLED, NODE_GROUP.ENABLED);

    private final NodeDbConnProvider nodeDbConnProvider;

    @Inject
    NodeGroupDaoImpl(final NodeDbConnProvider nodeDbConnProvider) {
        this.nodeDbConnProvider = nodeDbConnProvider;
    }

    @Override
    public ResultPage<NodeGroup> find(final FindNodeGroupRequest request) {
        final List<Condition> conditions = new ArrayList<>();
        if (NullSafe.isNonBlankString(request.getFilter())) {
            conditions.add(NODE_GROUP.NAME.startsWith(request.getFilter()));
        }
        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, request);
        final int offset = JooqUtil.getOffset(request.getPageRequest());
        final int limit = JooqUtil.getLimit(request.getPageRequest(), true);

        final List<NodeGroup> list = JooqUtil.contextResult(nodeDbConnProvider, context -> context
                        .select()
                        .from(NODE_GROUP)
                        .where(conditions)
                        .orderBy(orderFields)
                        .offset(offset)
                        .limit(limit)
                        .fetch())
                .map(RECORD_TO_NODE_GROUP_MAPPER::apply);
        return ResultPage.createCriterialBasedList(list, request);
    }

    @Override
    public NodeGroup create(final NodeGroup nodeGroup) {
        final Integer id = JooqUtil.contextResult(nodeDbConnProvider, context -> context
                .insertInto(NODE_GROUP,
                        NODE_GROUP.VERSION,
                        NODE_GROUP.CREATE_USER,
                        NODE_GROUP.CREATE_TIME_MS,
                        NODE_GROUP.UPDATE_USER,
                        NODE_GROUP.UPDATE_TIME_MS,
                        NODE_GROUP.NAME,
                        NODE_GROUP.ENABLED,
                        NODE_GROUP.INVERT_SELECTION)
                .values(1,
                        nodeGroup.getCreateUser(),
                        nodeGroup.getCreateTimeMs(),
                        nodeGroup.getUpdateUser(),
                        nodeGroup.getUpdateTimeMs(),
                        nodeGroup.getName(),
                        nodeGroup.isEnabled(),
                        nodeGroup.isInvertSelection())
                .returning(NODE_GROUP.ID)
                .fetchOne(NODE_GROUP.ID));
        Objects.requireNonNull(id, "Id is null");
        return fetchById(id);
    }

    @Override
    public NodeGroup fetchById(final int id) {
        return JooqUtil.contextResult(nodeDbConnProvider, context -> context
                        .select()
                        .from(NODE_GROUP)
                        .where(NODE_GROUP.ID.eq(id))
                        .fetchOptional())
                .map(RECORD_TO_NODE_GROUP_MAPPER)
                .orElse(null);
    }

    @Override
    public NodeGroup fetchByName(final String name) {
        return JooqUtil.contextResult(nodeDbConnProvider, context -> context
                        .select()
                        .from(NODE_GROUP)
                        .where(NODE_GROUP.NAME.eq(name))
                        .fetchOptional())
                .map(RECORD_TO_NODE_GROUP_MAPPER)
                .orElse(null);
    }

    @Override
    public NodeGroup update(final NodeGroup nodeGroup) {
        JooqUtil.context(nodeDbConnProvider, context -> {
            try {
                final int count = context
                        .update(NODE_GROUP)
                        .set(NODE_GROUP.VERSION, NODE_GROUP.VERSION.plus(1))
                        .set(NODE_GROUP.UPDATE_USER, nodeGroup.getUpdateUser())
                        .set(NODE_GROUP.UPDATE_TIME_MS, nodeGroup.getUpdateTimeMs())
                        .set(NODE_GROUP.NAME, nodeGroup.getName())
                        .set(NODE_GROUP.ENABLED, nodeGroup.isEnabled())
                        .set(NODE_GROUP.INVERT_SELECTION, nodeGroup.isInvertSelection())
                        .where(NODE_GROUP.ID.eq(nodeGroup.getId()))
                        .execute();
                if (count == 0) {
                    throw new DataChangedException("This node group has already been updated");
                }

            } catch (final DataAccessException e) {
                if (e.getCause() != null
                    && e.getCause() instanceof final SQLIntegrityConstraintViolationException sqlEx) {
                    if (sqlEx.getErrorCode() == 1062
                        && sqlEx.getMessage().contains("Duplicate entry")
                        && sqlEx.getMessage().contains("key")
                        && sqlEx.getMessage().contains(NODE_GROUP.NAME.getName())) {
                        throw new RuntimeException("An node group already exists with name '"
                                                   + nodeGroup.getName() + "'");
                    }
                }
                throw e;
            }
        });
        return fetchById(nodeGroup.getId());
    }

    @Override
    public void delete(final int id) {
        JooqUtil.transaction(nodeDbConnProvider, context -> {
            context.deleteFrom(NODE_GROUP_LINK).where(NODE_GROUP_LINK.FK_NODE_GROUP_ID.eq(id)).execute();
            context.deleteFrom(NODE_GROUP).where(NODE_GROUP.ID.eq(id)).execute();
        });
    }

    @Override
    public NodeGroupState getNodeGroupState(final Integer id) {
        final Set<Integer> set = JooqUtil.contextResult(nodeDbConnProvider, context -> context
                .select(NODE_GROUP_LINK.FK_NODE_ID)
                .from(NODE_GROUP_LINK)
                .where(NODE_GROUP_LINK.FK_NODE_GROUP_ID.eq(id))
                .fetchSet(NODE_GROUP_LINK.FK_NODE_ID));
        return new NodeGroupState(set);
    }

    @Override
    public Boolean updateNodeGroupState(final NodeGroupChange change) {
        JooqUtil.transaction(nodeDbConnProvider, context -> {
            final NodeGroup nodeGroup = change.getNodeGroup();
            try {
                final int count = context
                        .update(NODE_GROUP)
                        .set(NODE_GROUP.VERSION, NODE_GROUP.VERSION.plus(1))
                        .set(NODE_GROUP.UPDATE_USER, nodeGroup.getUpdateUser())
                        .set(NODE_GROUP.UPDATE_TIME_MS, nodeGroup.getUpdateTimeMs())
                        .set(NODE_GROUP.NAME, nodeGroup.getName())
                        .set(NODE_GROUP.ENABLED, nodeGroup.isEnabled())
                        .set(NODE_GROUP.INVERT_SELECTION, nodeGroup.isInvertSelection())
                        .where(NODE_GROUP.ID.eq(nodeGroup.getId()))
                        .execute();
                if (count == 0) {
                    throw new DataChangedException("This node group has already been updated");
                }

                // Delete all node selections from node group.
                context
                        .deleteFrom(NODE_GROUP_LINK)
                        .where(NODE_GROUP_LINK.FK_NODE_GROUP_ID.eq(nodeGroup.getId()))
                        .execute();

                // Add selections
                for (final Integer nodeId : change.getSelectedNodes()) {
                    context
                            .insertInto(NODE_GROUP_LINK, NODE_GROUP_LINK.FK_NODE_ID, NODE_GROUP_LINK.FK_NODE_GROUP_ID)
                            .values(nodeId, nodeGroup.getId())
                            .onDuplicateKeyUpdate()
                            .set(NODE_GROUP_LINK.FK_NODE_ID, nodeId)
                            .set(NODE_GROUP_LINK.FK_NODE_GROUP_ID, nodeGroup.getId())
                            .execute();
                }

            } catch (final DataAccessException e) {
                if (e.getCause() != null
                    && e.getCause() instanceof final SQLIntegrityConstraintViolationException sqlEx) {
                    if (sqlEx.getErrorCode() == 1062
                        && sqlEx.getMessage().contains("Duplicate entry")
                        && sqlEx.getMessage().contains("key")
                        && sqlEx.getMessage().contains(NODE_GROUP.NAME.getName())) {
                        throw new RuntimeException("An node group already exists with name '"
                                                   + nodeGroup.getName() + "'");
                    }
                }
                throw e;
            }
        });
        return true;
    }

    @Override
    public Set<String> getSelectedNodesForGroup(final Integer id) {
        return Collections.unmodifiableSet(JooqUtil.contextResult(nodeDbConnProvider, context -> context
                .select(NODE.NAME)
                .from(NODE)
                .join(NODE_GROUP_LINK).on(NODE.ID.eq(NODE_GROUP_LINK.FK_NODE_ID))
                .where(NODE_GROUP_LINK.FK_NODE_GROUP_ID.eq(id))
                .fetchSet(NODE.NAME)));
    }
}
