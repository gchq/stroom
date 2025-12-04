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

package stroom.activity.impl.db;

import stroom.activity.api.FindActivityCriteria;
import stroom.activity.impl.ActivityDao;
import stroom.activity.shared.Activity;
import stroom.activity.shared.Activity.ActivityDetails;
import stroom.db.util.JooqUtil;
import stroom.util.exception.DataChangedException;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import org.jooq.Condition;
import org.jooq.Record;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static stroom.activity.impl.db.jooq.tables.Activity.ACTIVITY;

public class ActivityDaoImpl implements ActivityDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ActivityDaoImpl.class);

    private final ActivityDbConnProvider activityDbConnProvider;

    private static final Function<Record, Activity> RECORD_TO_ACTIVITY_MAPPER = record -> Activity.builder()
            .id(record.get(ACTIVITY.ID))
            .version(record.get(ACTIVITY.VERSION))
            .createTimeMs(record.get(ACTIVITY.CREATE_TIME_MS))
            .createUser(record.get(ACTIVITY.CREATE_USER))
            .updateTimeMs(record.get(ACTIVITY.UPDATE_TIME_MS))
            .updateUser(record.get(ACTIVITY.UPDATE_USER))
            .userRef(UserRef.builder().uuid(record.get(ACTIVITY.USER_UUID)).build())
            .details(JsonUtil.readValue(record.get(ACTIVITY.JSON), ActivityDetails.class))
            .build();

    @Inject
    ActivityDaoImpl(final ActivityDbConnProvider activityDbConnProvider) {
        this.activityDbConnProvider = activityDbConnProvider;
    }

    private String getJson(final Activity activity) {
        String json = null;
        if (activity.getDetails() != null) {
            json = JsonUtil.writeValueAsString(activity.getDetails());
        }
        return json;
    }

    @Override
    public Activity create(final Activity activity) {
        final String json = getJson(activity);

        final int version = activity.getVersion() == null
                ? 1
                : activity.getVersion();
        final Optional<Integer> id = JooqUtil.contextResult(activityDbConnProvider, context -> context
                .insertInto(ACTIVITY)
                .columns(
                        ACTIVITY.VERSION,
                        ACTIVITY.CREATE_TIME_MS,
                        ACTIVITY.CREATE_USER,
                        ACTIVITY.UPDATE_TIME_MS,
                        ACTIVITY.UPDATE_USER,
                        ACTIVITY.JSON,
                        ACTIVITY.USER_UUID)
                .values(version,
                        activity.getCreateTimeMs(),
                        activity.getCreateUser(),
                        activity.getUpdateTimeMs(),
                        activity.getCreateUser(),
                        json,
                        activity.getUserRef().getUuid())
                .returning(ACTIVITY.ID)
                .fetchOptional(ACTIVITY.ID));

        return activity.copy().id(id.orElseThrow()).version(version).build();
    }

    @Override
    public Activity update(final Activity activity) {
        final String json = getJson(activity);

        final int version = activity.getVersion() + 1;
        final int count = JooqUtil.contextResult(activityDbConnProvider, context -> context
                .update(ACTIVITY)
                .set(ACTIVITY.VERSION, version)
                .set(ACTIVITY.UPDATE_TIME_MS, activity.getUpdateTimeMs())
                .set(ACTIVITY.UPDATE_USER, activity.getUpdateUser())
                .set(ACTIVITY.JSON, json)
                .set(ACTIVITY.USER_UUID, activity.getUserRef().getUuid())
                .where(ACTIVITY.ID.eq(activity.getId()))
                .and(ACTIVITY.VERSION.eq(activity.getVersion()))
                .execute());

        if (count == 0) {
            throw new DataChangedException("Unable to update activity");
        }

        return activity.copy().version(version).build();
    }

    @Override
    public boolean delete(final int id) {
        return JooqUtil.contextResult(activityDbConnProvider, context -> context
                .deleteFrom(ACTIVITY)
                .where(ACTIVITY.ID.eq(id))
                .execute()) > 0;
    }

    @Override
    public Optional<Activity> fetch(final int id) {
        return JooqUtil.contextResult(activityDbConnProvider, context -> context
                        .select()
                        .from(ACTIVITY)
                        .where(ACTIVITY.ID.eq(id))
                        .fetchOptional())
                .map(RECORD_TO_ACTIVITY_MAPPER);
    }

    @Override
    public List<Activity> find(final FindActivityCriteria criteria) {
        // Only filter on the user in the DB as we don't have a jooq/sql version of the
        // QuickFilterPredicateFactory
        final Collection<Condition> conditions = JooqUtil.conditions(
                NullSafe.getAsOptional(criteria.getUserRef(), UserRef::getUuid, ACTIVITY.USER_UUID::eq));
        final Integer offset = JooqUtil.getOffset(criteria.getPageRequest());
        final Integer limit = JooqUtil.getLimit(criteria.getPageRequest(), true);

        return JooqUtil.contextResult(activityDbConnProvider, context -> context
                        .select()
                        .from(ACTIVITY)
                        .where(conditions)
                        .limit(offset, limit)
                        .fetch())
                .stream()
                .map(RECORD_TO_ACTIVITY_MAPPER)
                .toList();
    }

    @Override
    public List<Activity> find(
            final FindActivityCriteria criteria,
            final Function<Stream<Activity>, Stream<Activity>> streamFunction) {
        // Only filter on the user in the DB as we don't have a jooq/sql version of the
        // QuickFilterPredicateFactory
        final Collection<Condition> conditions = JooqUtil.conditions(
                NullSafe.getAsOptional(criteria.getUserRef(), UserRef::getUuid, ACTIVITY.USER_UUID::eq));
        final int offset = JooqUtil.getOffset(criteria.getPageRequest());
        final int limit = JooqUtil.getLimit(criteria.getPageRequest(), true);

        return JooqUtil.contextResult(activityDbConnProvider, context -> {
            try (final Stream<Activity> activityStream = context
                    .select()
                    .from(ACTIVITY)
                    .where(conditions)
                    .fetch()
                    .stream()
                    .map(RECORD_TO_ACTIVITY_MAPPER)) {

                return streamFunction.apply(activityStream)
                        .skip(offset)
                        .limit(limit)
                        .toList();
            }
        });
    }

    @Override
    public int deleteAllByOwner(final UserRef ownerRef) {
        Objects.requireNonNull(ownerRef);
        final int delCount = JooqUtil.contextResult(activityDbConnProvider, dslContext -> dslContext
                .deleteFrom(ACTIVITY)
                .where(ACTIVITY.USER_UUID.eq(ownerRef.getUuid()))
                .execute());

        LOGGER.debug(() -> LogUtil.message("Deleted {} {} records for user {}",
                delCount, ACTIVITY.getName(), ownerRef.toInfoString()));

        return delCount;
    }
}
