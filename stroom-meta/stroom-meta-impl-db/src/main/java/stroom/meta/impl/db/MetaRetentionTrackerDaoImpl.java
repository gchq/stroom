package stroom.meta.impl.db;

import stroom.data.retention.api.DataRetentionTracker;
import stroom.db.util.JooqUtil;
import stroom.meta.impl.MetaRetentionTrackerDao;

import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static stroom.meta.impl.db.jooq.tables.MetaRetentionTracker.META_RETENTION_TRACKER;

public class MetaRetentionTrackerDaoImpl implements MetaRetentionTrackerDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetaRetentionTrackerDaoImpl.class);

    private static final Function<Record, DataRetentionTracker> RECORD_MAPPER = record ->
            new DataRetentionTracker(
                    record.get(META_RETENTION_TRACKER.LAST_RUN_TIME),
                    record.get(META_RETENTION_TRACKER.RETENTION_RULES_VERSION));

    private final MetaDbConnProvider metaDbConnProvider;

    @Inject
    public MetaRetentionTrackerDaoImpl(final MetaDbConnProvider metaDbConnProvider) {
        this.metaDbConnProvider = metaDbConnProvider;
    }

    @Override
    public Optional<DataRetentionTracker> getTracker() {
        final List<DataRetentionTracker> trackers = JooqUtil.contextResult(metaDbConnProvider, context -> context
                .select(
                        META_RETENTION_TRACKER.LAST_RUN_TIME,
                        META_RETENTION_TRACKER.RETENTION_RULES_VERSION)
                .from(META_RETENTION_TRACKER)
                .fetch()
                .map(RECORD_MAPPER::apply)
        );
        if (trackers.size() > 1) {
            throw new RuntimeException("Found more than one tracker: " + trackers);
        }
        return trackers.stream()
                .findFirst();
    }

    @Override
    public void createOrUpdate(final DataRetentionTracker dataRetentionTracker) {
        if (getTracker().isPresent()) {
            JooqUtil.context(metaDbConnProvider, context -> context
                    .update(META_RETENTION_TRACKER)
                    .set(META_RETENTION_TRACKER.LAST_RUN_TIME,
                            dataRetentionTracker.getLastRunTime().toEpochMilli())
                    .set(META_RETENTION_TRACKER.RETENTION_RULES_VERSION,
                            dataRetentionTracker.getRulesVersion())
                    .execute());
        } else {
            JooqUtil.context(metaDbConnProvider, context -> context
                    .insertInto(META_RETENTION_TRACKER)
                    .set(META_RETENTION_TRACKER.LAST_RUN_TIME,
                            dataRetentionTracker.getLastRunTime().toEpochMilli())
                    .set(META_RETENTION_TRACKER.RETENTION_RULES_VERSION,
                            dataRetentionTracker.getRulesVersion())
                    .execute());
        }
    }

    @Override
    public void clear() {
        JooqUtil.truncateTable(metaDbConnProvider, META_RETENTION_TRACKER);
//        JooqUtil.context(metaDbConnProvider, context -> context
//                .truncate(META_RETENTION_TRACKER)
//                .execute());
    }
}
