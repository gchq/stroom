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

import org.jooq.Condition;
import org.jooq.OrderField;
import org.jooq.TableField;
import stroom.db.util.AuditUtil;
import stroom.db.util.JooqUtil;
import stroom.node.impl.InternalNodeService;
import stroom.node.impl.db.jooq.tables.records.NodeRecord;
import stroom.node.shared.FindNodeCriteria;
import stroom.node.shared.Node;
import stroom.security.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.PermissionException;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static stroom.node.impl.db.jooq.tables.Node.NODE;

public class NodeServiceImpl implements InternalNodeService {
    private final Map<String, TableField> FIELD_MAP = Map.of(FindNodeCriteria.FIELD_ID, NODE.ID, FindNodeCriteria.FIELD_NAME, NODE.NAME);

    private final SecurityContext securityContext;
    private final ConnectionProvider connectionProvider;

    @Inject
    NodeServiceImpl(final SecurityContext securityContext,
                    final ConnectionProvider connectionProvider) {
        this.securityContext = securityContext;
        this.connectionProvider = connectionProvider;
    }

    @Override
    public Node create(final String name) {
        if (!securityContext.hasAppPermission(PermissionNames.MANAGE_NODES_PERMISSION)) {
            throw new PermissionException(securityContext.getUserId(), "You are not authorised to create this node");
        }

        JooqUtil.context(connectionProvider, context -> context
                .insertInto(NODE)
                .set(NODE.NAME, name)
                .onDuplicateKeyIgnore()
                .execute());

        return getNode(name);
    }

    Node update(final Node node) {
        if (!securityContext.isLoggedIn()) {
            throw new EntityServiceException("No user is logged in");
        }

        AuditUtil.stamp(securityContext.getUserId(), node);

        final Node result = JooqUtil.contextWithOptimisticLocking(connectionProvider, context -> {
            final NodeRecord nodeRecord = context.newRecord(NODE, node);
            nodeRecord.update();
            return nodeRecord.into(Node.class);
        });

        return result;
    }

    @Override
    public BaseResultList<Node> find(final FindNodeCriteria criteria) {
        if (!securityContext.hasAppPermission(PermissionNames.MANAGE_NODES_PERMISSION)) {
            throw new PermissionException(securityContext.getUserId(), "You are not authorised to find nodes");
        }

        final List<Condition> conditions = new ArrayList<>();
        JooqUtil.applyString(NODE.NAME, criteria.getName()).ifPresent(conditions::add);

        final OrderField[] orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);

        final List<Node> list = JooqUtil.contextResult(connectionProvider, context ->
                JooqUtil.applyLimits(context
                                .selectFrom(NODE)
                                .where(conditions)
                                .orderBy(orderFields)
                        , criteria.getPageRequest())
                        .fetch()
                        .map(r -> r.into(Node.class)));
        return BaseResultList.createCriterialBasedList(list, criteria);
    }

    @Override
    public FindNodeCriteria createCriteria() {
        return new FindNodeCriteria();
    }

    @Override
    public String getClusterUrl(final String nodeName) {
        final Node node = getNode(nodeName);
        if (node != null) {
            return node.getClusterURL();
        }
        return null;
    }

    @Override
    public boolean isEnabled(final String nodeName) {
        final Node node = getNode(nodeName);
        if (node != null) {
            return node.isEnabled();
        }
        return false;
    }

    @Override
    public int getPriority(final String nodeName) {
        final Node node = getNode(nodeName);
        if (node != null) {
            return node.getPriority();
        }
        return -1;
    }

    @Override
    public Node getNode(final String nodeName) {
        if (!securityContext.hasAppPermission(PermissionNames.MANAGE_NODES_PERMISSION)) {
            throw new PermissionException(securityContext.getUserId(), "You are not authorised to get this node");
        }

        final Optional<Node> optional = JooqUtil.contextResult(connectionProvider, context -> context
                .selectFrom(NODE)
                .where(NODE.NAME.eq(nodeName))
                .fetchOptional()
                .map(r -> r.into(Node.class)));
        return optional.orElse(null);
    }

//    @Override
//    public Activity create() {
//        final String userId = securityContext.getUserId();
//
//        final Activity activity = new Activity();
//        activity.setUserId(userId);
//
//        AuditUtil.stamp(userId, activity);
//
//        return JooqUtil.contextResult(connectionProvider, context -> {
//            final ActivityRecord activityRecord = context.newRecord(ACTIVITY, activity);
//            activityRecord.store();
//            return activityRecord.into(Activity.class);
//        });
//    }
//
//    @Override
//    public Activity update(final Activity activity) {
//        if (!securityContext.isLoggedIn()) {
//            throw new EntityServiceException("No user is logged in");
//        }
//        if (!securityContext.getUserId().equals(activity.getUserId())) {
//            throw new EntityServiceException("Attempt to update another persons activity");
//        }
//
//        AuditUtil.stamp(securityContext.getUserId(), activity);
//        ActivitySerialiser.serialise(activity);
//
//        final Activity result = JooqUtil.contextWithOptimisticLocking(connectionProvider, context -> {
//            final ActivityRecord activityRecord = context.newRecord(ACTIVITY, activity);
//            activityRecord.update();
//            return activityRecord.into(Activity.class);
//        });
//
//        return ActivitySerialiser.deserialise(result);
//    }
//
//    @Override
//    public int delete(final int id) {
//        return JooqUtil.contextResult(connectionProvider, context -> context
//                .deleteFrom(ACTIVITY)
//                .where(ACTIVITY.ID.eq(id))
//                .execute());
//    }
//
//    @Override
//    public Activity fetch(final int id) {
//        final Activity result = JooqUtil.contextResult(connectionProvider, context -> context
//                .fetchOne(ACTIVITY, ACTIVITY.ID.eq(id))
//                .into(Activity.class));
//        if (!result.getUserId().equals(securityContext.getUserId())) {
//            throw new EntityServiceException("Attempt to read another persons activity");
//        }
//        return ActivitySerialiser.deserialise(result);
//    }
//
//    @Override
//    public BaseResultList<Activity> find(final FindActivityCriteria criteria) {
//        criteria.setUserId(securityContext.getUserId());
//
//        List<Activity> list = JooqUtil.contextResult(connectionProvider, context -> {
//            Condition condition = DSL.trueCondition();
//            if (criteria.getUserId() != null) {
//                condition = condition.and(ACTIVITY.USER_ID.eq(criteria.getUserId()));
//            }
//            if (criteria.getName() != null && criteria.getName().isConstrained()) {
//                condition = condition.and(ACTIVITY.JSON.like(criteria.getName().getMatchString()));
//            }
//
//            return JooqUtil.applyLimits(context
//                    .select()
//                    .from(ACTIVITY)
//                    .where(condition), criteria.getPageRequest())
//                    .fetch()
//                    .into(Activity.class);
//
//        });
//
//        list = list.stream().map(ActivitySerialiser::deserialise).collect(Collectors.toList());
//        return BaseResultList.createUnboundedList(list);
//    }


//    private static final Logger LOGGER = LoggerFactory.getLogger(NodeServiceTransactionHelper.class);
//
//    private final StroomEntityManager entityManager;
//
//    @Inject
//    NodeServiceTransactionHelper(final StroomEntityManager entityManager) {
//        this.entityManager = entityManager;
//    }
//
//    @SuppressWarnings("unchecked")
//        // @Transactional
//    Node getNode(final String name) {
//        final HqlBuilder sql = new HqlBuilder();
//        sql.append("SELECT r FROM ");
//        sql.append(Node.class.getName());
//        sql.append(" r where r.name = ");
//        sql.arg(name);
//
//        // This should just bring back 1
//        final List<Node> results = entityManager.executeQueryResultList(sql);
//
//        if (results == null || results.size() == 0) {
//            return null;
//        }
//        return results.get(0);
//    }
//
//    /**
//     * Create a new transaction to create the node .... only ever called once at
//     * initial deployment time.
//     */
//    // @Transactional
//    Node buildNode(final String nodeName) {
//        Node node = getNode(nodeName);
//
//        if (node == null) {
//            node = Node.create(nodeName);
//            node = entityManager.saveEntity(node);
//
//            LOGGER.info("Unable to find default node " + nodeName + ", so I created it");
//        }
//
//        return node;
//    }
}
