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
import stroom.index.impl.IndexShardTaskDao;
import stroom.index.impl.IndexShardTaskRow;
import stroom.index.impl.IndexShardTaskState;
import stroom.index.impl.db.IndexDbConnProvider;
import stroom.index.impl.db.jooq.tables.records.IndexShardTaskRecord;
import stroom.index.shared.IndexShard.IndexShardStatus;

import jakarta.inject.Inject;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Row2;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static stroom.index.impl.db.jooq.tables.IndexShard.INDEX_SHARD;
import static stroom.index.impl.db.jooq.tables.IndexShardPurge.INDEX_SHARD_PURGE;
import static stroom.index.impl.db.jooq.tables.IndexShardTask.INDEX_SHARD_TASK;

class IndexShardTaskDaoImpl implements IndexShardTaskDao {

    // The most rows or purges named in one statement.
    private static final int BATCH_SIZE = 500;

    private final IndexDbConnProvider indexDbConnProvider;

    @Inject
    IndexShardTaskDaoImpl(final IndexDbConnProvider indexDbConnProvider) {
        this.indexDbConnProvider = indexDbConnProvider;
    }

    @Override
    public Optional<IndexShardTaskRow> fetch(final long taskId, final long indexShardId) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                        .selectFrom(INDEX_SHARD_TASK)
                        .where(INDEX_SHARD_TASK.TASK_ID.eq(taskId))
                        .and(INDEX_SHARD_TASK.INDEX_SHARD_ID.eq(indexShardId))
                        .fetchOptional())
                .map(IndexShardTaskDaoImpl::toRow);
    }

    @Override
    public List<IndexShardTaskRow> findByTask(final long taskId) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                        .selectFrom(INDEX_SHARD_TASK)
                        .where(INDEX_SHARD_TASK.TASK_ID.eq(taskId))
                        .orderBy(INDEX_SHARD_TASK.INDEX_SHARD_ID)
                        .fetch())
                .map(IndexShardTaskDaoImpl::toRow);
    }

    @Override
    public List<IndexShardTaskRow> findByShardAndStream(final long indexShardId, final long streamId) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                        .selectFrom(INDEX_SHARD_TASK)
                        .where(INDEX_SHARD_TASK.INDEX_SHARD_ID.eq(indexShardId))
                        .and(INDEX_SHARD_TASK.STREAM_ID.eq(streamId))
                        .orderBy(INDEX_SHARD_TASK.TASK_ID)
                        .fetch())
                .map(IndexShardTaskDaoImpl::toRow);
    }

    @Override
    public Optional<IndexShardTaskRow> insertWriting(final long taskId,
                                                     final long indexShardId,
                                                     final long streamId,
                                                     final int run,
                                                     final String writerId) {
        Objects.requireNonNull(writerId);
        return JooqUtil.contextResult(indexDbConnProvider, context -> {
            final long nowMs = getDatabaseTimeMs(context);
            final int count = context
                    .insertInto(INDEX_SHARD_TASK)
                    .set(INDEX_SHARD_TASK.TASK_ID, taskId)
                    .set(INDEX_SHARD_TASK.INDEX_SHARD_ID, indexShardId)
                    .set(INDEX_SHARD_TASK.STREAM_ID, streamId)
                    .set(INDEX_SHARD_TASK.RUN, run)
                    .set(INDEX_SHARD_TASK.WRITER_ID, writerId)
                    .set(INDEX_SHARD_TASK.STATE, IndexShardTaskState.WRITING.getPrimitiveValue())
                    .set(INDEX_SHARD_TASK.UPDATE_TIME_MS, nowMs)
                    .onDuplicateKeyIgnore()
                    .execute();
            if (count == 0) {
                return Optional.empty();
            }
            return Optional.of(new IndexShardTaskRow(
                    taskId, indexShardId, streamId, run, writerId, IndexShardTaskState.WRITING, nowMs));
        });
    }

    @Override
    public Optional<IndexShardTaskRow> update(final IndexShardTaskRow expected,
                                              final int run,
                                              final String writerId,
                                              final IndexShardTaskState state) {
        Objects.requireNonNull(writerId);
        Objects.requireNonNull(state);
        return JooqUtil.contextResult(indexDbConnProvider, context -> {
            final long nowMs = getDatabaseTimeMs(context);
            final int count = context
                    .update(INDEX_SHARD_TASK)
                    .set(INDEX_SHARD_TASK.RUN, run)
                    .set(INDEX_SHARD_TASK.WRITER_ID, writerId)
                    .set(INDEX_SHARD_TASK.STATE, state.getPrimitiveValue())
                    .set(INDEX_SHARD_TASK.UPDATE_TIME_MS, nowMs)
                    .where(isUnchanged(expected))
                    .execute();
            if (count == 0) {
                return Optional.empty();
            }
            return Optional.of(new IndexShardTaskRow(
                    expected.taskId(),
                    expected.indexShardId(),
                    expected.streamId(),
                    run,
                    writerId,
                    state,
                    nowMs));
        });
    }

    @Override
    public boolean delete(final IndexShardTaskRow expected) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .deleteFrom(INDEX_SHARD_TASK)
                .where(isUnchanged(expected))
                .execute()) > 0;
    }

    @Override
    public int markWritten(final long taskId, final int run) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .update(INDEX_SHARD_TASK)
                .set(INDEX_SHARD_TASK.STATE, IndexShardTaskState.WRITTEN.getPrimitiveValue())
                .set(INDEX_SHARD_TASK.UPDATE_TIME_MS, DbTime.NOW_MS)
                .where(INDEX_SHARD_TASK.TASK_ID.eq(taskId))
                .and(INDEX_SHARD_TASK.RUN.eq(run))
                .and(INDEX_SHARD_TASK.STATE.eq(IndexShardTaskState.WRITING.getPrimitiveValue()))
                .execute());
    }

    @Override
    public List<IndexShardTaskRow> findWritten(final long indexShardId, final String writerId) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                        .selectFrom(INDEX_SHARD_TASK)
                        .where(INDEX_SHARD_TASK.INDEX_SHARD_ID.eq(indexShardId))
                        .and(INDEX_SHARD_TASK.WRITER_ID.eq(writerId))
                        .and(INDEX_SHARD_TASK.STATE.eq(IndexShardTaskState.WRITTEN.getPrimitiveValue()))
                        .orderBy(INDEX_SHARD_TASK.TASK_ID)
                        .fetch())
                .map(IndexShardTaskDaoImpl::toRow);
    }

    @Override
    public boolean markCommitted(final long indexShardId,
                                 final String writerId,
                                 final Collection<IndexShardTaskRow> rows,
                                 final Collection<IndexShardPurge> purges) {
        Objects.requireNonNull(writerId);
        rows.forEach(row -> checkShard(row.indexShardId(), indexShardId));
        purges.forEach(purge -> checkShard(purge.indexShardId(), indexShardId));

        return JooqUtil.transactionResult(indexDbConnProvider, context -> {
            // Locked until the transaction ends, so the shard can't change writer while this is recorded.
            final Optional<String> shardWriterId = context
                    .select(INDEX_SHARD.WRITER_ID)
                    .from(INDEX_SHARD)
                    .where(INDEX_SHARD.ID.eq(indexShardId))
                    .forUpdate()
                    .fetchOptional(INDEX_SHARD.WRITER_ID);
            if (shardWriterId.isEmpty() || !writerId.equals(shardWriterId.get())) {
                return false;
            }

            final long nowMs = getDatabaseTimeMs(context);
            for (final List<IndexShardTaskRow> batch : batches(rows)) {
                final List<Row2<Long, Integer>> runs = batch
                        .stream()
                        .map(row -> DSL.row(row.taskId(), row.run()))
                        .toList();
                context.update(INDEX_SHARD_TASK)
                        .set(INDEX_SHARD_TASK.STATE, IndexShardTaskState.COMMITTED.getPrimitiveValue())
                        .set(INDEX_SHARD_TASK.UPDATE_TIME_MS, nowMs)
                        .where(INDEX_SHARD_TASK.INDEX_SHARD_ID.eq(indexShardId))
                        .and(INDEX_SHARD_TASK.WRITER_ID.eq(writerId))
                        .and(INDEX_SHARD_TASK.STATE.eq(IndexShardTaskState.WRITTEN.getPrimitiveValue()))
                        .and(DSL.row(INDEX_SHARD_TASK.TASK_ID, INDEX_SHARD_TASK.RUN).in(runs))
                        .execute();
            }

            for (final List<IndexShardPurge> batch : batches(purges)) {
                final List<Row2<Long, Integer>> versions = batch
                        .stream()
                        .map(purge -> DSL.row(purge.streamId(), purge.version()))
                        .toList();
                context.deleteFrom(INDEX_SHARD_PURGE)
                        .where(INDEX_SHARD_PURGE.INDEX_SHARD_ID.eq(indexShardId))
                        .and(INDEX_SHARD_PURGE.WRITER_ID.eq(writerId))
                        .and(DSL.row(INDEX_SHARD_PURGE.STREAM_ID, INDEX_SHARD_PURGE.VERSION).in(versions))
                        .execute();
            }
            return true;
        });
    }

    @Override
    public int deleteRun(final long taskId, final int run) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .deleteFrom(INDEX_SHARD_TASK)
                .where(INDEX_SHARD_TASK.TASK_ID.eq(taskId))
                .and(INDEX_SHARD_TASK.RUN.eq(run))
                .execute());
    }

    @Override
    public List<IndexShardTaskRow> toPurges(final Collection<IndexShardTaskRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        return JooqUtil.transactionResult(indexDbConnProvider, context -> {
            final long nowMs = getDatabaseTimeMs(context);
            final List<IndexShardTaskRow> purged = new ArrayList<>();
            for (final IndexShardTaskRow row : rows) {
                final int deleted = context
                        .deleteFrom(INDEX_SHARD_TASK)
                        .where(isUnchanged(row))
                        .execute();
                if (deleted > 0) {
                    IndexShardPurgeDaoImpl.request(context, row.indexShardId(), row.streamId(), nowMs);
                    purged.add(row);
                }
            }
            return purged;
        });
    }

    @Override
    public List<IndexShardTaskRow> findLost(final IndexShardTaskRow after, final int limit) {
        // A shard marked corrupt counts as having no writer.
        final Condition writerGone = INDEX_SHARD.WRITER_ID.isNull()
                .or(INDEX_SHARD.WRITER_ID.ne(INDEX_SHARD_TASK.WRITER_ID))
                .or(INDEX_SHARD.STATUS.eq(IndexShardStatus.CORRUPT.getPrimitiveValue()));
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                        .select(INDEX_SHARD_TASK.fields())
                        .from(INDEX_SHARD_TASK)
                        .join(INDEX_SHARD).on(INDEX_SHARD.ID.eq(INDEX_SHARD_TASK.INDEX_SHARD_ID))
                        .where(INDEX_SHARD_TASK.STATE.ne(IndexShardTaskState.COMMITTED.getPrimitiveValue()))
                        .and(INDEX_SHARD.STATUS.ne(IndexShardStatus.DELETED.getPrimitiveValue()))
                        .and(writerGone)
                        .and(isAfter(after))
                        .orderBy(INDEX_SHARD_TASK.TASK_ID, INDEX_SHARD_TASK.INDEX_SHARD_ID)
                        .limit(limit)
                        .fetch())
                .map(IndexShardTaskDaoImpl::toRow);
    }

    @Override
    public List<IndexShardTaskRow> findNotUpdatedSince(final long updateTimeMs,
                                                       final IndexShardTaskRow after,
                                                       final int limit) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                        .selectFrom(INDEX_SHARD_TASK)
                        .where(INDEX_SHARD_TASK.UPDATE_TIME_MS.lt(updateTimeMs))
                        .and(isAfter(after))
                        .orderBy(INDEX_SHARD_TASK.TASK_ID, INDEX_SHARD_TASK.INDEX_SHARD_ID)
                        .limit(limit)
                        .fetch())
                .map(IndexShardTaskDaoImpl::toRow);
    }

    @Override
    public int deleteForDeletedShards() {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .deleteFrom(INDEX_SHARD_TASK)
                .where(INDEX_SHARD_TASK.INDEX_SHARD_ID.in(context
                        .select(INDEX_SHARD.ID)
                        .from(INDEX_SHARD)
                        .where(INDEX_SHARD.STATUS.eq(IndexShardStatus.DELETED.getPrimitiveValue()))))
                .execute());
    }

    @Override
    public long getDatabaseTimeMs() {
        return JooqUtil.contextResult(indexDbConnProvider, IndexShardTaskDaoImpl::getDatabaseTimeMs);
    }

    private static long getDatabaseTimeMs(final DSLContext context) {
        return Objects.requireNonNull(context.select(DbTime.NOW_MS).fetchOne(DbTime.NOW_MS));
    }

    // The row, only if it still holds what it held when read.
    private static Condition isUnchanged(final IndexShardTaskRow row) {
        return INDEX_SHARD_TASK.TASK_ID.eq(row.taskId())
                .and(INDEX_SHARD_TASK.INDEX_SHARD_ID.eq(row.indexShardId()))
                .and(INDEX_SHARD_TASK.RUN.eq(row.run()))
                .and(INDEX_SHARD_TASK.WRITER_ID.eq(row.writerId()))
                .and(INDEX_SHARD_TASK.STATE.eq(row.state().getPrimitiveValue()));
    }

    private static Condition isAfter(final IndexShardTaskRow after) {
        return after == null
                ? DSL.trueCondition()
                : DSL.row(INDEX_SHARD_TASK.TASK_ID, INDEX_SHARD_TASK.INDEX_SHARD_ID)
                        .gt(after.taskId(), after.indexShardId());
    }

    private static void checkShard(final long indexShardId, final long expectedIndexShardId) {
        if (indexShardId != expectedIndexShardId) {
            throw new IllegalArgumentException("Index shard " + indexShardId + " given when committing index shard "
                                               + expectedIndexShardId);
        }
    }

    private static <T> List<List<T>> batches(final Collection<T> items) {
        final List<List<T>> batches = new ArrayList<>();
        List<T> batch = new ArrayList<>();
        for (final T item : items) {
            batch.add(item);
            if (batch.size() == BATCH_SIZE) {
                batches.add(batch);
                batch = new ArrayList<>();
            }
        }
        if (!batch.isEmpty()) {
            batches.add(batch);
        }
        return batches;
    }

    private static IndexShardTaskRow toRow(final Record record) {
        final IndexShardTaskRecord taskRecord = record.into(INDEX_SHARD_TASK);
        return new IndexShardTaskRow(
                taskRecord.getTaskId(),
                taskRecord.getIndexShardId(),
                taskRecord.getStreamId(),
                taskRecord.getRun(),
                taskRecord.getWriterId(),
                IndexShardTaskState.fromPrimitiveValue(taskRecord.getState()),
                taskRecord.getUpdateTimeMs());
    }
}
