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

package stroom.node.impl.db;

import stroom.db.util.JooqUtil;
import stroom.node.impl.NodeGroupDao;
import stroom.node.shared.FindNodeGroupRequest;
import stroom.node.shared.Node;
import stroom.node.shared.NodeGroup;
import stroom.node.shared.NodeGroupChange;
import stroom.node.shared.NodeGroupState;
import stroom.util.exception.DataChangedException;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import org.jooq.Condition;
import org.jooq.exception.DataAccessException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static stroom.node.impl.db.jooq.tables.Node.NODE;
import static stroom.node.impl.db.jooq.tables.NodeGroup.NODE_GROUP;
import static stroom.node.impl.db.jooq.tables.NodeGroupLink.NODE_GROUP_LINK;

public class NodeGroupDaoImpl implements NodeGroupDao {

    private static final RecordToNodeMapper RECORD_TO_NODE_MAPPER = new RecordToNodeMapper();
    private static final RecordToNodeGroupMapper RECORD_TO_NODE_GROUP_MAPPER = new RecordToNodeGroupMapper();

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
        final int offset = JooqUtil.getOffset(request.getPageRequest());
        final int limit = JooqUtil.getLimit(request.getPageRequest(), true);

        final List<NodeGroup> list = JooqUtil.contextResult(nodeDbConnProvider, context -> context
                        .select()
                        .from(NODE_GROUP)
                        .where(conditions)
                        .orderBy(NODE_GROUP.NAME)
                        .offset(offset)
                        .limit(limit)
                        .fetch())
                .map(RECORD_TO_NODE_GROUP_MAPPER::apply);
        return ResultPage.createCriterialBasedList(list, request);
    }


//    @Override
//    public List<String> getNames() {
//        return JooqUtil.contextResult(nodeDbConnProvider, context -> context
//                .select(NODE_GROUP.NAME)
//                .from(NODE_GROUP)
//                .orderBy(NODE_GROUP.NAME)
//                .fetch(NODE_GROUP.NAME));
//    }
//
//    @Override
//    public List<NodeGroup> getAll() {
//        return JooqUtil.contextResult(nodeDbConnProvider, context -> context
//                        .select()
//                        .from(NODE_GROUP)
//                        .orderBy(NODE_GROUP.NAME)
//                        .fetch())
//                .map(RECORD_TO_NODE_GROUP_MAPPER::apply);
//    }


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
                        NODE_GROUP.ENABLED)
                .values(1,
                        nodeGroup.getCreateUser(),
                        nodeGroup.getCreateTimeMs(),
                        nodeGroup.getUpdateUser(),
                        nodeGroup.getUpdateTimeMs(),
                        nodeGroup.getName(),
                        nodeGroup.isEnabled())
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
    public ResultPage<NodeGroupState> getNodeGroupState(final Integer id) {
//        final Collection<Condition> conditions = JooqUtil.conditions(
//                JooqUtil.getStringCondition(NODE.NAME, criteria.getName()),
//                JooqUtil.getBooleanCondition(NODE.ENABLED, criteria.isEnabled()));
//
//        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);
//        final int offset = JooqUtil.getOffset(criteria.getPageRequest());
//        final int limit = JooqUtil.getLimit(criteria.getPageRequest(), true);
        final List<NodeGroupState> list = JooqUtil.contextResult(nodeDbConnProvider, context ->
                        context
                                .select(NODE.ID,
                                        NODE.VERSION,
                                        NODE.CREATE_TIME_MS,
                                        NODE.CREATE_USER,
                                        NODE.UPDATE_TIME_MS,
                                        NODE.UPDATE_USER,
                                        NODE.NAME,
                                        NODE.URL,
                                        NODE.PRIORITY,
                                        NODE.ENABLED,
                                        NODE.BUILD_VERSION,
                                        NODE.LAST_BOOT_MS,
                                        NODE_GROUP_LINK.ID)
                                .from(NODE)
                                .leftOuterJoin(NODE_GROUP_LINK)
                                .on(NODE_GROUP_LINK.FK_NODE_ID.eq(NODE.ID).and(NODE_GROUP_LINK.FK_NODE_GROUP_ID.eq(id)))
                                .orderBy(NODE.NAME)
//                                .where(NODE_GROUP_LINK.FK_NODE_GROUP_ID.eq(id))
//                                .orderBy(orderFields)
//                                .limit(offset, limit)
                                .fetch())
                .map(r -> {
                    final Node node = RECORD_TO_NODE_MAPPER.apply(r);
                    final Integer groupId = r.get(NODE_GROUP_LINK.ID);
                    return new NodeGroupState(node, groupId != null);
                });
        return ResultPage.createUnboundedList(list);
    }

    @Override
    public boolean updateNodeGroupState(final NodeGroupChange change) {
        if (change.isIncluded()) {
            return JooqUtil.contextResult(nodeDbConnProvider, context ->
                    context
                            .insertInto(NODE_GROUP_LINK,
                                    NODE_GROUP_LINK.FK_NODE_ID,
                                    NODE_GROUP_LINK.FK_NODE_GROUP_ID)
                            .values(change.getNodeId(),
                                    change.getNodeGroupId())
                            .onDuplicateKeyUpdate()
                            .set(NODE_GROUP_LINK.FK_NODE_ID,
                                    change.getNodeId())
                            .set(NODE_GROUP_LINK.FK_NODE_GROUP_ID,
                                    change.getNodeGroupId())
                            .execute() > 0);
        } else {
            return JooqUtil.contextResult(nodeDbConnProvider, context ->
                    context
                            .deleteFrom(NODE_GROUP_LINK)
                            .where(NODE_GROUP_LINK.FK_NODE_ID.eq(
                                    change.getNodeId()))
                            .and(NODE_GROUP_LINK.FK_NODE_GROUP_ID.eq(
                                    change.getNodeGroupId()))
                            .execute() > 0);
        }
    }
}
