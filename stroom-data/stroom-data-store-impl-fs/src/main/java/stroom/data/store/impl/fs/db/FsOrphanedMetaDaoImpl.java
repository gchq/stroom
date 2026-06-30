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

package stroom.data.store.impl.fs.db;

import stroom.data.store.impl.fs.FsOrphanedMetaDao;
import stroom.data.store.impl.fs.db.jooq.tables.records.FsOrphanedMetaTrackerRecord;
import stroom.db.util.JooqUtil;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static stroom.data.store.impl.fs.db.jooq.tables.FsOrphanedMetaTracker.FS_ORPHANED_META_TRACKER;

@Singleton // So ensureTrackerRow is run once
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
    public long getMetaIdTrackerValue() {
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
    public void updateMetaIdTracker(final long lastMinMetaId) {
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
