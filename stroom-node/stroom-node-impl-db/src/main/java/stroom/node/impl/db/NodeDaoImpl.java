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

import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.node.api.FindNodeCriteria;
import stroom.node.impl.NodeDao;
import stroom.node.impl.db.jooq.tables.records.NodeRecord;
import stroom.node.shared.Node;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static stroom.node.impl.db.jooq.tables.Node.NODE;

public class NodeDaoImpl implements NodeDao {

    private static final BiFunction<Node, NodeRecord, NodeRecord> NODE_TO_RECORD_MAPPER =
            (node, record) -> {
                record.set(NODE.ID, node.getId());
                record.set(NODE.VERSION, node.getVersion());
                record.set(NODE.CREATE_TIME_MS, node.getCreateTimeMs());
                record.set(NODE.CREATE_USER, node.getCreateUser());
                record.set(NODE.UPDATE_TIME_MS, node.getUpdateTimeMs());
                record.set(NODE.UPDATE_USER, node.getUpdateUser());
                record.set(NODE.NAME, node.getName());
                record.set(NODE.URL, node.getUrl());
                record.set(NODE.PRIORITY, (short) node.getPriority());
                record.set(NODE.ENABLED, node.isEnabled());
                record.set(NODE.BUILD_VERSION, node.getBuildVersion());
                record.set(NODE.LAST_BOOT_MS, node.getLastBootMs());
                return record;
            };

    private static final Map<String, Field<?>> FIELD_MAP = Map.of(
            FindNodeCriteria.FIELD_ID, NODE.ID,
            FindNodeCriteria.FIELD_LAST_BOOT_MS, NODE.LAST_BOOT_MS,
            FindNodeCriteria.FIELD_BUILD_VERSION, NODE.BUILD_VERSION,
            FindNodeCriteria.FIELD_NAME, NODE.NAME);
    private static final RecordToNodeMapper RECORD_TO_NODE_MAPPER = new RecordToNodeMapper();

    private final GenericDao<NodeRecord, Node, Integer> genericDao;
    private final NodeDbConnProvider nodeDbConnProvider;

    @Inject
    NodeDaoImpl(final NodeDbConnProvider nodeDbConnProvider) {
        this.nodeDbConnProvider = nodeDbConnProvider;
        this.genericDao = new GenericDao<>(
                nodeDbConnProvider,
                NODE,
                NODE.ID,
                NODE_TO_RECORD_MAPPER,
                RECORD_TO_NODE_MAPPER);
    }

    @Override
    public Node tryCreate(final Node node) {
        return genericDao.tryCreate(node, NODE.NAME);
    }

    @Override
    public Node update(final Node node) {
        return genericDao.update(node);
    }

    @Override
    public ResultPage<Node> find(final FindNodeCriteria criteria) {
        final Collection<Condition> conditions = JooqUtil.conditions(
                JooqUtil.getStringCondition(NODE.NAME, criteria.getName()),
                JooqUtil.getBooleanCondition(NODE.ENABLED, criteria.isEnabled()));

        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);
        final int offset = JooqUtil.getOffset(criteria.getPageRequest());
        final int limit = JooqUtil.getLimit(criteria.getPageRequest(), true);
        final List<Node> list = JooqUtil.contextResult(nodeDbConnProvider, context ->
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
                                        NODE.LAST_BOOT_MS)
                                .from(NODE)
                                .where(conditions)
                                .orderBy(orderFields)
                                .limit(offset, limit)
                                .fetch())
                .map(RECORD_TO_NODE_MAPPER::apply);
        return ResultPage.createCriterialBasedList(list, criteria);
    }

    @Override
    public Node getNode(final String nodeName) {
        return genericDao.fetchBy(NODE.NAME, nodeName)
                .orElse(null);
    }
}
