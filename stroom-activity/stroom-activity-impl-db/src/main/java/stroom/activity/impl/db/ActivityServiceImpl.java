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

package stroom.activity.impl.db;

import org.jooq.Condition;
import org.jooq.impl.DSL;
import stroom.activity.api.ActivityService;
import stroom.activity.impl.db.jooq.tables.records.ActivityRecord;
import stroom.activity.api.Activity;
import stroom.activity.api.FindActivityCriteria;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.EntityServiceException;
import stroom.security.SecurityContext;
import stroom.db.util.AuditUtil;
import stroom.db.util.JooqUtil;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

import static stroom.activity.impl.db.jooq.tables.Activity.ACTIVITY;

public class ActivityServiceImpl implements ActivityService {
    private final SecurityContext securityContext;
    private final ConnectionProvider connectionProvider;

    @Inject
    ActivityServiceImpl(final SecurityContext securityContext,
                        final ConnectionProvider connectionProvider) {
        this.securityContext = securityContext;
        this.connectionProvider = connectionProvider;
    }

    @Override
    public Activity create() {
        final String userId = securityContext.getUserId();

        final Activity activity = new Activity();
        activity.setUserId(userId);

        AuditUtil.stamp(userId, activity);

        return JooqUtil.contextResult(connectionProvider, context -> {
            final ActivityRecord activityRecord = context.newRecord(ACTIVITY, activity);
            activityRecord.store();
            return activityRecord.into(Activity.class);
        });
    }

    @Override
    public Activity update(final Activity activity) {
        if (!securityContext.isLoggedIn()) {
            throw new EntityServiceException("No user is logged in");
        }
        if (!securityContext.getUserId().equals(activity.getUserId())) {
            throw new EntityServiceException("Attempt to update another persons activity");
        }

        AuditUtil.stamp(securityContext.getUserId(), activity);
        ActivitySerialiser.serialise(activity);

        final Activity result = JooqUtil.contextWithOptimisticLocking(connectionProvider, context -> {
            final ActivityRecord activityRecord = context.newRecord(ACTIVITY, activity);
            activityRecord.update();
            return activityRecord.into(Activity.class);
        });

        return ActivitySerialiser.deserialise(result);
    }

    @Override
    public int delete(final int id) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .deleteFrom(ACTIVITY)
                .where(ACTIVITY.ID.eq(id))
                .execute());
    }

    @Override
    public Activity fetch(final int id) {
        final Activity result = JooqUtil.contextResult(connectionProvider, context -> context
                .fetchOne(ACTIVITY, ACTIVITY.ID.eq(id))
                .into(Activity.class));
        if (!result.getUserId().equals(securityContext.getUserId())) {
            throw new EntityServiceException("Attempt to read another persons activity");
        }
        return ActivitySerialiser.deserialise(result);
    }

    @Override
    public BaseResultList<Activity> find(final FindActivityCriteria criteria) {
        criteria.setUserId(securityContext.getUserId());

        List<Activity> list = JooqUtil.contextResult(connectionProvider, context -> {
            Condition condition = DSL.trueCondition();
            if (criteria.getUserId() != null) {
                condition = condition.and(ACTIVITY.USER_ID.eq(criteria.getUserId()));
            }
            if (criteria.getName() != null && criteria.getName().isConstrained()) {
                condition = condition.and(ACTIVITY.JSON.like(criteria.getName().getMatchString()));
            }

            return JooqUtil.applyLimits(context
                    .select()
                    .from(ACTIVITY)
                    .where(condition), criteria.getPageRequest())
                    .fetch()
                    .into(Activity.class);

        });

        list = list.stream().map(ActivitySerialiser::deserialise).collect(Collectors.toList());
        return BaseResultList.createUnboundedList(list);
    }
}
