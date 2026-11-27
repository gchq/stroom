/*
 * Copyright 2026 Crown Copyright
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

package stroom.index.impl.dao;

import stroom.db.util.JooqUtil;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexVolumeDao;
import stroom.index.impl.IndexVolumeGroupDao;
import stroom.index.impl.db.IndexDbConnProvider;
import stroom.index.shared.AllPartition;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolume.VolumeType;
import stroom.index.shared.IndexVolumeGroup;

import jakarta.inject.Inject;

import java.nio.file.Path;
import java.util.UUID;

import static stroom.index.impl.db.jooq.tables.IndexShard.INDEX_SHARD;

/// Makes index shards for the tests of the DAOs of the tables that refer to them, and sets what nothing
/// else sets yet.
class IndexShardFixtures {

    private final IndexVolumeDao indexVolumeDao;
    private final IndexVolumeGroupDao indexVolumeGroupDao;
    private final IndexShardDao indexShardDao;
    private final IndexDbConnProvider indexDbConnProvider;

    @Inject
    IndexShardFixtures(final IndexVolumeDao indexVolumeDao,
               final IndexVolumeGroupDao indexVolumeGroupDao,
               final IndexShardDao indexShardDao,
               final IndexDbConnProvider indexDbConnProvider) {
        this.indexVolumeDao = indexVolumeDao;
        this.indexVolumeGroupDao = indexVolumeGroupDao;
        this.indexShardDao = indexShardDao;
        this.indexDbConnProvider = indexDbConnProvider;
    }

    /// Creates a closed shard on a shared volume that has been committed to.
    ///
    /// @param tempDir Where the volume goes.
    /// @return The id of the shard.
    long createCommittedShard(final Path tempDir) {
        final IndexVolumeGroup group = indexVolumeGroupDao.getOrCreate(IndexVolumeGroup
                .builder()
                .name(UUID.randomUUID().toString())
                .stampAudit("test")
                .build());
        final IndexVolume volume = indexVolumeDao.create(IndexVolume
                .builder()
                .path(tempDir.resolve(UUID.randomUUID().toString()).toString())
                .volumeType(VolumeType.SHARED)
                .indexVolumeGroupId(group.getId())
                .stampAudit("test")
                .build());
        final IndexShard indexShard = indexShardDao.create(
                IndexShardKey.builder()
                        .indexUuid(UUID.randomUUID().toString())
                        .partition(AllPartition.INSTANCE)
                        .build(),
                volume,
                "node1",
                "1.0-test");
        indexShardDao.setStatus(indexShard.getId(), IndexShardStatus.OPENING);
        indexShardDao.setStatus(indexShard.getId(), IndexShardStatus.CLOSED);
        indexShardDao.update(indexShard.getId(), 1, 1L, System.currentTimeMillis(), 1L, null);
        return indexShard.getId();
    }

    /// Sets the writer that has the shard open, as opening a shard will.
    ///
    /// @param indexShardId The id of the shard.
    /// @param writerId     The writer, or null if nothing has it open.
    void setWriter(final long indexShardId, final String writerId) {
        JooqUtil.context(indexDbConnProvider, context -> context
                .update(INDEX_SHARD)
                .set(INDEX_SHARD.WRITER_ID, writerId)
                .where(INDEX_SHARD.ID.eq(indexShardId))
                .execute());
    }

    /// Sets the status of a shard.
    ///
    /// @param indexShardId The id of the shard.
    /// @param status       The status.
    void setStatus(final long indexShardId, final IndexShardStatus status) {
        JooqUtil.context(indexDbConnProvider, context -> context
                .update(INDEX_SHARD)
                .set(INDEX_SHARD.STATUS, status.getPrimitiveValue())
                .where(INDEX_SHARD.ID.eq(indexShardId))
                .execute());
    }

    /// Clears the time of a shard's last commit, as for one that has never been committed to.
    ///
    /// @param indexShardId The id of the shard.
    void clearCommitTime(final long indexShardId) {
        JooqUtil.context(indexDbConnProvider, context -> context
                .update(INDEX_SHARD)
                .setNull(INDEX_SHARD.COMMIT_MS)
                .where(INDEX_SHARD.ID.eq(indexShardId))
                .execute());
    }

    /// Removes a shard's row, as the shard delete job does once its files have gone.
    ///
    /// @param indexShardId The id of the shard.
    void deleteRow(final long indexShardId) {
        indexShardDao.delete(indexShardId);
    }
}
