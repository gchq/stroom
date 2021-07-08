/*
 * Copyright 2018 Crown Copyright
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
import stroom.node.api.FindNodeCriteria;
import stroom.node.impl.NodeDao;
import stroom.node.impl.db.jooq.tables.records.NodeRecord;
import stroom.node.shared.Node;
import stroom.util.shared.ResultPage;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.impl.DSL;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;

import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.trueCondition;
import static stroom.job.impl.db.jooq.tables.Job.JOB;
import static stroom.job.impl.db.jooq.tables.JobNode.JOB_NODE;
import static stroom.node.impl.db.jooq.tables.Node.NODE;

public class NodeDaoImpl implements NodeDao {

    private static final Map<String, Field<?>> FIELD_MAP = Map.of(
            FindNodeCriteria.FIELD_ID, NODE.ID,
            FindNodeCriteria.FIELD_NAME, NODE.NAME);

    private final NodeDbConnProvider nodeDbConnProvider;

    @Inject
    NodeDaoImpl(final NodeDbConnProvider nodeDbConnProvider) {
        this.nodeDbConnProvider = nodeDbConnProvider;
    }

    @Override
    public Node create(final Node node) {
        JooqUtil.context(nodeDbConnProvider, context -> context
                .insertInto(NODE)
                .set(NODE.NAME, node.getName())
                .set(NODE.URL, node.getUrl())
                .set(NODE.PRIORITY, (short) node.getPriority())
                .set(NODE.ENABLED, node.isEnabled())
                .onDuplicateKeyIgnore()
                .execute());

        return getNode(node.getName());
    }

    @Override
    public Node update(final Node node) {
        return JooqUtil.contextResultWithOptimisticLocking(nodeDbConnProvider, context -> {
            final NodeRecord nodeRecord = context.newRecord(NODE, node);
            nodeRecord.update();
            return nodeRecord.into(Node.class);
        });
    }

    @Override
    public ResultPage<Node> find(final FindNodeCriteria criteria) {
        final Collection<Condition> conditions = JooqUtil.conditions(
                JooqUtil.getStringCondition(NODE.NAME, criteria.getName()),
                JooqUtil.getBooleanCondition(NODE.ENABLED, criteria.isEnabled()));

        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);

        final List<Node> list = JooqUtil.contextResult(nodeDbConnProvider, context ->
                context
                        .selectFrom(NODE)
                        .where(conditions)
                        .orderBy(orderFields)
                        .limit(JooqUtil.getLimit(criteria.getPageRequest(), true))
                        .offset(JooqUtil.getOffset(criteria.getPageRequest()))
                        .fetch()
                        .map(r -> r.into(Node.class)));
        return ResultPage.createCriterialBasedList(list, criteria);
    }

    @Override
    public Node getNode(final String nodeName) {
        final Optional<Node> optional = JooqUtil.contextResult(nodeDbConnProvider, context -> context
                .selectFrom(NODE)
                .where(NODE.NAME.eq(nodeName))
                .fetchOptional()
                .map(r -> r.into(Node.class)));
        return optional.orElse(null);
    }

    @Override
    public int setJobsEnabled(final String nodeName,
                              final boolean enabled,
                              final Set<String> includeJobs,
                              final Set<String> excludeJobs) {
        return JooqUtil.contextResult(nodeDbConnProvider, context -> context
                .update(JOB_NODE)
                .set(JOB_NODE.ENABLED, enabled)
                .where(JOB_NODE.NODE_NAME.eq(nodeName)
                        .and(JOB_NODE.JOB_ID.in(
                                select(JOB.ID).from(JOB)
                                        .where(JOB.NAME.in(includeJobs)
                                                .or(DSL.condition(includeJobs.size() == 0)))
                        )).and(JOB_NODE.JOB_ID.notIn(
                                select(JOB.ID).from(JOB)
                                        .where(JOB.NAME.in(excludeJobs)
                                                .and(DSL.condition(excludeJobs.size() > 0)))
                        )))
                .execute()
        );
    }
}
