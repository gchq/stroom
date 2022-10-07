package stroom.data.store.impl.fs.db;

import stroom.data.store.impl.fs.FsOrphanedMetaDao;
import stroom.data.store.impl.fs.db.jooq.tables.records.FsOrphanedMetaTrackerRecord;
import stroom.db.util.JooqUtil;
import stroom.util.logging.LogUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.data.store.impl.fs.db.jooq.tables.FsOrphanedMetaTracker.FS_ORPHANED_META_TRACKER;

@Singleton
public class FsOrphanedMetaDaoImpl implements FsOrphanedMetaDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(FsOrphanedMetaDaoImpl.class);

    private static final int TRACKER_ID = 1;

    private final FsDataStoreDbConnProvider fsDataStoreDbConnProvider;

    @Inject
    public FsOrphanedMetaDaoImpl(final FsDataStoreDbConnProvider fsDataStoreDbConnProvider) {
        this.fsDataStoreDbConnProvider = fsDataStoreDbConnProvider;
        ensureTrackerRow();
    }

    @Override
    public long getLastMinMetaId() {
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context ->
                context
                        .select(FS_ORPHANED_META_TRACKER.MIN_META_ID)
                        .from(FS_ORPHANED_META_TRACKER)
                        .where(FS_ORPHANED_META_TRACKER.ID.eq(TRACKER_ID))
                        .fetchOptional(FS_ORPHANED_META_TRACKER.MIN_META_ID)
                        .orElseThrow(() -> new RuntimeException(LogUtil.message(
                                "{} row with ID {} not found",
                                FS_ORPHANED_META_TRACKER.getName(),
                                TRACKER_ID))));
    }

    @Override
    public void updateLastMinMetaId(final long lastMinMetaId) {
        final int result = JooqUtil.contextResult(fsDataStoreDbConnProvider, context ->
                context
                        .update(FS_ORPHANED_META_TRACKER)
                        .set(FS_ORPHANED_META_TRACKER.MIN_META_ID, lastMinMetaId)
                        .where(FS_ORPHANED_META_TRACKER.ID.eq(TRACKER_ID))
                        .execute());
        if (result != 1) {
            throw new RuntimeException(LogUtil.message("Expected to update one record, but updated {}, ID: {}",
                    result, TRACKER_ID));
        }
    }

    private void ensureTrackerRow() {
        final FsOrphanedMetaTrackerRecord record = new FsOrphanedMetaTrackerRecord(TRACKER_ID, 0L);
        JooqUtil.tryCreate(fsDataStoreDbConnProvider, record, FS_ORPHANED_META_TRACKER.ID);
    }
}
