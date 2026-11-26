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
import stroom.index.impl.IndexShardPurge;
import stroom.index.impl.IndexShardPurgeDao;
import stroom.index.impl.db.IndexDbConnProvider;
import stroom.index.impl.db.jooq.tables.records.IndexShardPurgeRecord;
import stroom.index.shared.IndexShard.IndexShardStatus;

import jakarta.inject.Inject;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static stroom.index.impl.db.jooq.tables.IndexShard.INDEX_SHARD;
import static stroom.index.impl.db.jooq.tables.IndexShardPurge.INDEX_SHARD_PURGE;

class IndexShardPurgeDaoImpl implements IndexShardPurgeDao {

    private final IndexDbConnProvider indexDbConnProvider;

    @Inject
    IndexShardPurgeDaoImpl(final IndexDbConnProvider indexDbConnProvider) {
        this.indexDbConnProvider = indexDbConnProvider;
    }

    @Override
    public Optional<IndexShardPurge> fetch(final long indexShardId, final long streamId) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                        .selectFrom(INDEX_SHARD_PURGE)
                        .where(INDEX_SHARD_PURGE.INDEX_SHARD_ID.eq(indexShardId))
                        .and(INDEX_SHARD_PURGE.STREAM_ID.eq(streamId))
                        .fetchOptional())
                .map(IndexShardPurgeDaoImpl::toPurge);
    }

    @Override
    public List<IndexShardPurge> findByShard(final long indexShardId) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                        .selectFrom(INDEX_SHARD_PURGE)
                        .where(INDEX_SHARD_PURGE.INDEX_SHARD_ID.eq(indexShardId))
                        .orderBy(INDEX_SHARD_PURGE.STREAM_ID)
                        .fetch())
                .map(IndexShardPurgeDaoImpl::toPurge);
    }

    @Override
    public void request(final long indexShardId, final long streamId) {
        JooqUtil.context(indexDbConnProvider, context ->
                request(context, indexShardId, streamId, context.select(DbTime.NOW_MS).fetchOne(DbTime.NOW_MS)));
    }

    // Shared with the task rows, which ask for purges in their own transactions.
    static void request(final DSLContext context,
                        final long indexShardId,
                        final long streamId,
                        final long nowMs) {
        context.insertInto(INDEX_SHARD_PURGE)
                .set(INDEX_SHARD_PURGE.INDEX_SHARD_ID, indexShardId)
                .set(INDEX_SHARD_PURGE.STREAM_ID, streamId)
                .setNull(INDEX_SHARD_PURGE.WRITER_ID)
                .set(INDEX_SHARD_PURGE.VERSION, 1)
                .set(INDEX_SHARD_PURGE.CREATE_TIME_MS, nowMs)
                .onDuplicateKeyUpdate()
                // Done again, even if a writer has deleted the stream but not yet committed, as a run may
                // have written to the shard since.
                .setNull(INDEX_SHARD_PURGE.WRITER_ID)
                .set(INDEX_SHARD_PURGE.VERSION, INDEX_SHARD_PURGE.VERSION.plus(1))
                .execute();
    }

    @Override
    public Optional<IndexShardPurge> markDeleted(final IndexShardPurge expected, final String writerId) {
        Objects.requireNonNull(writerId);
        final int count = JooqUtil.contextResult(indexDbConnProvider, context -> context
                .update(INDEX_SHARD_PURGE)
                .set(INDEX_SHARD_PURGE.WRITER_ID, writerId)
                .set(INDEX_SHARD_PURGE.VERSION, INDEX_SHARD_PURGE.VERSION.plus(1))
                .where(INDEX_SHARD_PURGE.INDEX_SHARD_ID.eq(expected.indexShardId()))
                .and(INDEX_SHARD_PURGE.STREAM_ID.eq(expected.streamId()))
                .and(INDEX_SHARD_PURGE.VERSION.eq(expected.version()))
                .and(INDEX_SHARD_PURGE.WRITER_ID.isNull())
                .execute());
        if (count == 0) {
            return Optional.empty();
        }
        return Optional.of(new IndexShardPurge(
                expected.indexShardId(),
                expected.streamId(),
                writerId,
                expected.version() + 1,
                expected.createTimeMs()));
    }

    @Override
    public int resetLost() {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .update(INDEX_SHARD_PURGE)
                .setNull(INDEX_SHARD_PURGE.WRITER_ID)
                .set(INDEX_SHARD_PURGE.VERSION, INDEX_SHARD_PURGE.VERSION.plus(1))
                .where(INDEX_SHARD_PURGE.WRITER_ID.isNotNull())
                .andNotExists(DSL
                        .selectOne()
                        .from(INDEX_SHARD)
                        .where(INDEX_SHARD.ID.eq(INDEX_SHARD_PURGE.INDEX_SHARD_ID))
                        .and(INDEX_SHARD.WRITER_ID.eq(INDEX_SHARD_PURGE.WRITER_ID)))
                .execute());
    }

    @Override
    public List<Long> findShardsToSweep(final int limit) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .selectDistinct(INDEX_SHARD_PURGE.INDEX_SHARD_ID)
                .from(INDEX_SHARD_PURGE)
                .join(INDEX_SHARD).on(INDEX_SHARD.ID.eq(INDEX_SHARD_PURGE.INDEX_SHARD_ID))
                .where(INDEX_SHARD_PURGE.WRITER_ID.isNull())
                .and(INDEX_SHARD.WRITER_ID.isNull())
                .and(INDEX_SHARD.STATUS.eq(IndexShardStatus.CLOSED.getPrimitiveValue()))
                .orderBy(INDEX_SHARD_PURGE.INDEX_SHARD_ID)
                .limit(limit)
                .fetch(INDEX_SHARD_PURGE.INDEX_SHARD_ID));
    }

    @Override
    public int deleteForUnusableShards() {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .deleteFrom(INDEX_SHARD_PURGE)
                .where(INDEX_SHARD_PURGE.INDEX_SHARD_ID.in(context
                        .select(INDEX_SHARD.ID)
                        .from(INDEX_SHARD)
                        .where(INDEX_SHARD.STATUS.in(
                                        IndexShardStatus.DELETED.getPrimitiveValue(),
                                        IndexShardStatus.CORRUPT.getPrimitiveValue())
                                .or(INDEX_SHARD.WRITER_ID.isNull().and(INDEX_SHARD.COMMIT_MS.isNull())))))
                .execute());
    }

    @Override
    public Optional<Long> findOldestCreateTimeMs() {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .select(DSL.min(INDEX_SHARD_PURGE.CREATE_TIME_MS))
                .from(INDEX_SHARD_PURGE)
                .fetchOptional(0, Long.class));
    }

    @Override
    public long count() {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .fetchCount(INDEX_SHARD_PURGE));
    }

    private static IndexShardPurge toPurge(final IndexShardPurgeRecord record) {
        return new IndexShardPurge(
                record.getIndexShardId(),
                record.getStreamId(),
                record.getWriterId(),
                record.getVersion(),
                record.getCreateTimeMs());
    }
}
