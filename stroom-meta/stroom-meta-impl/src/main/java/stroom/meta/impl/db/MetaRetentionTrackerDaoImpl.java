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

package stroom.meta.impl.db;

import stroom.data.retention.api.DataRetentionTracker;
import stroom.db.util.JooqUtil;
import stroom.meta.impl.MetaRetentionTrackerDao;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import org.jooq.Record;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static stroom.meta.impl.db.jooq.tables.MetaRetentionTracker.META_RETENTION_TRACKER;

public class MetaRetentionTrackerDaoImpl implements MetaRetentionTrackerDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MetaRetentionTrackerDaoImpl.class);

    private static final Function<Record, DataRetentionTracker> RECORD_MAPPER = record ->
            new DataRetentionTracker(
                    record.get(META_RETENTION_TRACKER.RETENTION_RULES_VERSION),
                    record.get(META_RETENTION_TRACKER.RULE_AGE),
                    record.get(META_RETENTION_TRACKER.LAST_RUN_TIME));

    private final MetaDbConnProvider metaDbConnProvider;

    @Inject
    public MetaRetentionTrackerDaoImpl(final MetaDbConnProvider metaDbConnProvider) {
        this.metaDbConnProvider = metaDbConnProvider;
    }

    @Override
    public List<DataRetentionTracker> getTrackers() {

        final List<DataRetentionTracker> trackers = JooqUtil.contextResult(metaDbConnProvider, context -> context
                .select(
                        META_RETENTION_TRACKER.RETENTION_RULES_VERSION,
                        META_RETENTION_TRACKER.RULE_AGE,
                        META_RETENTION_TRACKER.LAST_RUN_TIME)
                .from(META_RETENTION_TRACKER)
                .fetch())
                .map(RECORD_MAPPER::apply);

        LOGGER.debug(LogUtil.message("Found {} trackers, unique versions [{}]",
                trackers.size(),
                trackers.stream()
                        .map(DataRetentionTracker::getRulesVersion)
                        .distinct()
                        .collect(Collectors.joining(", "))));

        return trackers;
    }

    @Override
    public void createOrUpdate(final DataRetentionTracker dataRetentionTracker) {
        JooqUtil.context(metaDbConnProvider, context -> context
                .insertInto(META_RETENTION_TRACKER)
                .set(META_RETENTION_TRACKER.RETENTION_RULES_VERSION,
                        dataRetentionTracker.getRulesVersion())
                .set(META_RETENTION_TRACKER.RULE_AGE,
                        dataRetentionTracker.getRuleAge())
                .set(META_RETENTION_TRACKER.LAST_RUN_TIME,
                        dataRetentionTracker.getLastRunTime().toEpochMilli())
                .onDuplicateKeyUpdate()
                .set(META_RETENTION_TRACKER.LAST_RUN_TIME,
                        dataRetentionTracker.getLastRunTime().toEpochMilli())
                .execute());
        LOGGER.debug("Set tracker: {}", dataRetentionTracker);
    }

    @Override
    public int deleteTrackers(final String rulesVersion) {
        Objects.requireNonNull(rulesVersion);

        final int deleteCount = JooqUtil.contextResult(metaDbConnProvider, context -> context
                .deleteFrom(META_RETENTION_TRACKER)
                .where(META_RETENTION_TRACKER.RETENTION_RULES_VERSION.eq(rulesVersion))
                .execute());

        LOGGER.debug("Deleted {} tracker records", deleteCount);

        return deleteCount;
    }
}
