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
import stroom.index.impl.IndexShardPendingStreamDao;
import stroom.index.impl.PendingStream;
import stroom.index.impl.PendingStreamState;
import stroom.index.impl.db.IndexDbConnProvider;
import stroom.index.shared.IndexShard.IndexShardStatus;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Row2;
import org.jooq.impl.DSL;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import static stroom.index.impl.db.jooq.tables.IndexShard.INDEX_SHARD;
import static stroom.index.impl.db.jooq.tables.IndexShardPendingStream.INDEX_SHARD_PENDING_STREAM;

@Singleton
class IndexShardPendingStreamDaoImpl implements IndexShardPendingStreamDao {

    private static final stroom.index.impl.db.jooq.tables.IndexShardPendingStream PENDING =
            INDEX_SHARD_PENDING_STREAM;
    // A second view of the table, for asking about the other rows of a row's stream.
    private static final stroom.index.impl.db.jooq.tables.IndexShardPendingStream OTHER =
            INDEX_SHARD_PENDING_STREAM.as("other");

    private static final int BATCH_SIZE = 500;

    private static final byte WRITING = PendingStreamState.WRITING.getPrimitiveValue();
    private static final byte WRITTEN = PendingStreamState.WRITTEN.getPrimitiveValue();
    private static final byte COMMITTED = PendingStreamState.COMMITTED.getPrimitiveValue();
    private static final byte ABANDONED = PendingStreamState.ABANDONED.getPrimitiveValue();
    private static final byte DELETE = PendingStreamState.DELETE.getPrimitiveValue();
    private static final byte RETRY_PENDING = PendingStreamState.RETRY_PENDING.getPrimitiveValue();
    private static final byte RETRYING = PendingStreamState.RETRYING.getPrimitiveValue();

    // True of the rows that record documents in a shard, i.e. not the row that counts attempts.
    private static final Condition IS_SHARD_ROW = PENDING.INDEX_SHARD_ID.ne(ATTEMPT_ROW_SHARD_ID);

    private static final Function<Record, PendingStream> RECORD_MAPPER = record -> new PendingStream(
            record.get(PENDING.ID),
            record.get(PENDING.INDEX_SHARD_ID),
            record.get(PENDING.STREAM_ID),
            record.get(PENDING.TASK_ID),
            record.get(PENDING.ATTEMPT),
            PendingStreamState.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(record.get(PENDING.STATE)));

    private final IndexDbConnProvider indexDbConnProvider;

    @Inject
    IndexShardPendingStreamDaoImpl(final IndexDbConnProvider indexDbConnProvider) {
        this.indexDbConnProvider = indexDbConnProvider;
    }

    @Override
    public int beginAttempt(final long streamId, final long taskId) {
        // The usual case is a first attempt, which has left nothing. Only this task makes rows for the
        // stream, so none can appear between this look and its first document.
        final boolean hasRows = JooqUtil.contextResult(indexDbConnProvider, context ->
                context.fetchExists(PENDING, isStream(streamId, taskId)));
        if (!hasRows) {
            return 0;
        }
        return transactionResult("beginAttempt", context -> {
            lockStream(context, streamId, taskId);
            markStreamToDelete(context, streamId, taskId);
            // This is the task being run again, which is all the attempt row was waiting for.
            context
                    .update(PENDING)
                    .set(PENDING.STATE, RETRYING)
                    .set(PENDING.UPDATE_TIME_MS, System.currentTimeMillis())
                    .where(isStream(streamId, taskId))
                    .and(PENDING.INDEX_SHARD_ID.eq(ATTEMPT_ROW_SHARD_ID))
                    .and(PENDING.STATE.eq(RETRY_PENDING))
                    .execute();
            return countRowsToDelete(context, streamId, taskId);
        });
    }

    @Override
    public int countRowsToDelete(final long streamId, final long taskId) {
        return JooqUtil.contextResult(indexDbConnProvider, context ->
                countRowsToDelete(context, streamId, taskId));
    }

    private int countRowsToDelete(final DSLContext context, final long streamId, final long taskId) {
        return context.fetchCount(PENDING, isStream(streamId, taskId).and(PENDING.STATE.eq(DELETE)));
    }

    @Override
    public void addShard(final long shardId, final long streamId, final long taskId) {
        final long now = System.currentTimeMillis();
        JooqUtil.context(indexDbConnProvider, context -> context
                .insertInto(PENDING)
                .set(PENDING.INDEX_SHARD_ID, shardId)
                .set(PENDING.STREAM_ID, streamId)
                .set(PENDING.TASK_ID, taskId)
                .set(PENDING.ATTEMPT, 0)
                .set(PENDING.STATE, WRITING)
                .set(PENDING.UPDATE_TIME_MS, now)
                // Two indexing filters in one pipeline may write the same stream to the same shard.
                .onDuplicateKeyUpdate()
                .set(PENDING.UPDATE_TIME_MS, now)
                .execute());
    }

    @Override
    public void removeShard(final long shardId, final long streamId, final long taskId) {
        JooqUtil.context(indexDbConnProvider, context -> context
                .deleteFrom(PENDING)
                .where(isStream(streamId, taskId))
                .and(PENDING.INDEX_SHARD_ID.eq(shardId))
                .and(PENDING.STATE.eq(WRITING))
                .execute());
    }

    @Override
    public boolean endAttempt(final long streamId, final long taskId) {
        return transactionResult("endAttempt", context -> {
            final List<PendingStream> rows = lockStream(context, streamId, taskId);
            final boolean failed = rows
                    .stream()
                    .anyMatch(row -> PendingStreamState.ABANDONED.equals(row.state())
                                     || PendingStreamState.DELETE.equals(row.state()));
            if (failed) {
                // A shard this attempt wrote to lost its documents, so none of the attempt is kept. The
                // task has stopped writing, so the documents can now be removed.
                markStreamToDelete(context, streamId, taskId);
            } else {
                context
                        .update(PENDING)
                        .set(PENDING.STATE, WRITTEN)
                        .set(PENDING.UPDATE_TIME_MS, System.currentTimeMillis())
                        .where(isStream(streamId, taskId))
                        .and(PENDING.STATE.eq(WRITING))
                        .execute();
            }
            return !failed;
        });
    }

    @Override
    public void failAttempt(final long streamId, final long taskId) {
        transactionResult("failAttempt", context -> {
            lockStream(context, streamId, taskId);
            markStreamToDelete(context, streamId, taskId);
            markToRetry(context, streamId, taskId);
            return null;
        });
    }

    @Override
    public void abandonAttempt(final long streamId, final long taskId) {
        transactionResult("abandonAttempt", context -> {
            lockStream(context, streamId, taskId);
            abandonStream(context, streamId, taskId);
            markToRetry(context, streamId, taskId);
            return null;
        });
    }

    @Override
    public boolean failStaleAttempt(final long streamId, final long taskId, final long notChangedSince) {
        return transactionResult("failStaleAttempt", context -> {
            lockStream(context, streamId, taskId);
            final boolean stale = context.fetchExists(context
                    .selectOne()
                    .from(PENDING)
                    .where(isStream(streamId, taskId))
                    .and(PENDING.STATE.in(WRITING, ABANDONED))
                    .and(PENDING.UPDATE_TIME_MS.lt(notChangedSince)));
            if (stale) {
                markStreamToDelete(context, streamId, taskId);
                markToRetry(context, streamId, taskId);
            }
            return stale;
        });
    }

    @Override
    public List<Long> findWrittenRowIds(final long shardId) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .select(PENDING.ID)
                .from(PENDING)
                .where(PENDING.INDEX_SHARD_ID.eq(shardId))
                .and(PENDING.STATE.eq(WRITTEN))
                .fetch(PENDING.ID));
    }

    @Override
    public void markCommitted(final Collection<Long> rowIds) {
        if (rowIds.isEmpty()) {
            return;
        }
        // A shard may commit a great many small streams at once, so this is done in batches of a few
        // statements each and not stream by stream. It runs while the shard's writer is committing.
        final List<Long> ids = List.copyOf(rowIds);
        for (int from = 0; from < ids.size(); from += BATCH_SIZE) {
            final List<Long> batch = ids.subList(from, Math.min(ids.size(), from + BATCH_SIZE));
            JooqUtil.context(indexDbConnProvider, context -> {
                context
                        .update(PENDING)
                        .set(PENDING.STATE, COMMITTED)
                        .set(PENDING.UPDATE_TIME_MS, System.currentTimeMillis())
                        .where(PENDING.ID.in(batch))
                        .and(PENDING.STATE.eq(WRITTEN))
                        .execute();
                // The streams among them that are now committed in every shard they touched.
                final List<Row2<Long, Long>> done = context
                        .select(PENDING.STREAM_ID, PENDING.TASK_ID)
                        .from(PENDING)
                        .where(PENDING.ID.in(batch))
                        .and(PENDING.STATE.eq(COMMITTED))
                        .andNotExists(context
                                .selectOne()
                                .from(OTHER)
                                .where(OTHER.STREAM_ID.eq(PENDING.STREAM_ID))
                                .and(OTHER.TASK_ID.eq(PENDING.TASK_ID))
                                .and(OTHER.STATE.notIn(COMMITTED, RETRYING)))
                        .fetch()
                        .map(record -> DSL.row(record.get(PENDING.STREAM_ID), record.get(PENDING.TASK_ID)));
                if (!done.isEmpty()) {
                    // Only rows that are finished with, so that a row a task has just added is never
                    // removed.
                    context
                            .deleteFrom(PENDING)
                            .where(DSL.row(PENDING.STREAM_ID, PENDING.TASK_ID).in(done))
                            .and(PENDING.STATE.in(COMMITTED, RETRYING))
                            .execute();
                }
            });
        }
    }

    @Override
    public int failShard(final long shardId) {
        return transactionResult("failShard", context -> {
            final List<PendingStream> uncommitted = context
                    .select()
                    .from(PENDING)
                    .where(PENDING.INDEX_SHARD_ID.eq(shardId))
                    .and(PENDING.STATE.in(WRITING, WRITTEN))
                    .forUpdate()
                    .fetch()
                    .map(RECORD_MAPPER::apply);
            for (final PendingStream row : uncommitted) {
                lockStream(context, row.streamId(), row.taskId());
                abandonStream(context, row.streamId(), row.taskId());
                markToRetry(context, row.streamId(), row.taskId());
            }
            return uncommitted.size();
        });
    }

    @Override
    public int markCommittedInClosedShards() {
        // One statement, so that the shard is seen to be closed at the same moment the row is seen to be
        // written. The task had then finished, and a clean close commits everything.
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .update(PENDING
                        .join(INDEX_SHARD).on(INDEX_SHARD.ID.eq(PENDING.INDEX_SHARD_ID)))
                .set(PENDING.STATE, COMMITTED)
                .set(PENDING.UPDATE_TIME_MS, System.currentTimeMillis())
                .where(PENDING.STATE.eq(WRITTEN))
                .and(INDEX_SHARD.STATUS.eq(IndexShardStatus.CLOSED.getPrimitiveValue()))
                .and(INDEX_SHARD.RECOVER_PENDING_STREAMS.eq((byte) 0))
                .execute());
    }

    @Override
    public int deleteRowsOfDeletedShards() {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .deleteFrom(PENDING)
                .where(IS_SHARD_ROW)
                .and(PENDING.INDEX_SHARD_ID.notIn(context
                        .select(INDEX_SHARD.ID)
                        .from(INDEX_SHARD)
                        .where(INDEX_SHARD.STATUS.ne(IndexShardStatus.DELETED.getPrimitiveValue()))))
                .execute());
    }

    @Override
    public int deleteCommittedStreams(final int limit) {
        final List<PendingStream> streams = JooqUtil.contextResult(indexDbConnProvider, context -> context
                .selectDistinct(PENDING.STREAM_ID, PENDING.TASK_ID)
                .from(PENDING)
                .where(PENDING.STATE.eq(COMMITTED))
                .andNotExists(context
                        .selectOne()
                        .from(OTHER)
                        .where(OTHER.STREAM_ID.eq(PENDING.STREAM_ID))
                        .and(OTHER.TASK_ID.eq(PENDING.TASK_ID))
                        .and(OTHER.STATE.notIn(COMMITTED, RETRYING)))
                .limit(limit)
                .fetch()
                .map(record -> new PendingStream(
                        0,
                        0,
                        record.get(PENDING.STREAM_ID),
                        record.get(PENDING.TASK_ID),
                        0,
                        PendingStreamState.COMMITTED)));
        int count = 0;
        for (final PendingStream stream : streams) {
            if (deleteStreamIfCommitted(stream.streamId(), stream.taskId())) {
                count++;
            }
        }
        return count;
    }

    @Override
    public List<Long> findShardIdsWithRowsToDelete() {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .selectDistinct(PENDING.INDEX_SHARD_ID)
                .from(PENDING)
                .where(PENDING.STATE.eq(DELETE))
                .and(IS_SHARD_ROW)
                .fetch(PENDING.INDEX_SHARD_ID));
    }

    @Override
    public List<PendingStream> findRowsToDelete(final long shardId) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .select()
                .from(PENDING)
                .where(PENDING.INDEX_SHARD_ID.eq(shardId))
                .and(PENDING.STATE.eq(DELETE))
                .andNotExists(context
                        .selectOne()
                        .from(OTHER)
                        .where(OTHER.STREAM_ID.eq(PENDING.STREAM_ID))
                        .and(OTHER.TASK_ID.eq(PENDING.TASK_ID))
                        .and(OTHER.STATE.in(WRITING, ABANDONED)))
                .fetch()
                .map(RECORD_MAPPER::apply));
    }

    @Override
    public void deleteRows(final Collection<Long> rowIds) {
        if (!rowIds.isEmpty()) {
            JooqUtil.context(indexDbConnProvider, context -> context
                    .deleteFrom(PENDING)
                    .where(PENDING.ID.in(rowIds))
                    .execute());
        }
    }

    @Override
    public List<PendingStream> findStaleAttempts(final long notChangedSince, final int limit) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .select()
                .from(PENDING)
                .where(PENDING.STATE.in(WRITING, ABANDONED))
                .and(PENDING.UPDATE_TIME_MS.lt(notChangedSince))
                .orderBy(PENDING.ID)
                .limit(limit)
                .fetch()
                .map(RECORD_MAPPER::apply));
    }

    @Override
    public List<PendingStream> findRetriesToFinish(final int limit) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .select()
                .from(PENDING)
                .where(PENDING.STATE.eq(RETRY_PENDING))
                .andNotExists(context
                        .selectOne()
                        .from(OTHER)
                        .where(OTHER.STREAM_ID.eq(PENDING.STREAM_ID))
                        .and(OTHER.TASK_ID.eq(PENDING.TASK_ID))
                        .and(OTHER.INDEX_SHARD_ID.ne(ATTEMPT_ROW_SHARD_ID)))
                .orderBy(PENDING.ID)
                .limit(limit)
                .fetch()
                .map(RECORD_MAPPER::apply));
    }

    @Override
    public void setRetrying(final long attemptRowId) {
        JooqUtil.context(indexDbConnProvider, context -> context
                .update(PENDING)
                .set(PENDING.STATE, RETRYING)
                .set(PENDING.UPDATE_TIME_MS, System.currentTimeMillis())
                .where(PENDING.ID.eq(attemptRowId))
                .and(PENDING.STATE.eq(RETRY_PENDING))
                .execute());
    }

    @Override
    public void deleteStream(final long streamId, final long taskId) {
        JooqUtil.context(indexDbConnProvider, context -> context
                .deleteFrom(PENDING)
                .where(isStream(streamId, taskId))
                .execute());
    }

    @Override
    public List<PendingStream> find(final long streamId, final long taskId) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .select()
                .from(PENDING)
                .where(isStream(streamId, taskId))
                .orderBy(PENDING.INDEX_SHARD_ID)
                .fetch()
                .map(RECORD_MAPPER::apply));
    }

    // Removes a stream's rows if every shard it touched has committed its documents. The row that counts
    // attempts goes too, as an attempt has now succeeded.
    private boolean deleteStreamIfCommitted(final long streamId, final long taskId) {
        return transactionResult("deleteStreamIfCommitted", context -> {
            final List<PendingStream> rows = lockStream(context, streamId, taskId);
            final boolean committed = rows
                    .stream()
                    .anyMatch(row -> PendingStreamState.COMMITTED.equals(row.state()));
            final boolean allDone = rows
                    .stream()
                    .allMatch(row -> PendingStreamState.COMMITTED.equals(row.state())
                                     || PendingStreamState.RETRYING.equals(row.state()));
            if (committed && allDone) {
                context
                        .deleteFrom(PENDING)
                        .where(isStream(streamId, taskId))
                        .execute();
                return true;
            }
            return false;
        });
    }

    private static Condition isStream(final long streamId, final long taskId) {
        return PENDING.STREAM_ID.eq(streamId).and(PENDING.TASK_ID.eq(taskId));
    }

    // Locks a stream's rows for the rest of the transaction, so that changes to a stream happen one at a
    // time.
    private List<PendingStream> lockStream(final DSLContext context, final long streamId, final long taskId) {
        return context
                .select()
                .from(PENDING)
                .where(isStream(streamId, taskId))
                .forUpdate()
                .fetch()
                .map(RECORD_MAPPER::apply);
    }

    // Marks a stream's documents to be removed. A task that is still writing may add more documents, so
    // its rows can't be acted on until it has stopped, which it says when it ends.
    private void abandonStream(final DSLContext context, final long streamId, final long taskId) {
        context
                .update(PENDING)
                .set(PENDING.STATE, DSL
                        .when(PENDING.STATE.eq(WRITING), ABANDONED)
                        .otherwise(DELETE))
                .set(PENDING.UPDATE_TIME_MS, System.currentTimeMillis())
                .where(isStream(streamId, taskId))
                .and(IS_SHARD_ROW)
                .and(PENDING.STATE.in(WRITING, WRITTEN, COMMITTED))
                .execute();
    }

    private void markStreamToDelete(final DSLContext context, final long streamId, final long taskId) {
        context
                .update(PENDING)
                .set(PENDING.STATE, DELETE)
                .set(PENDING.UPDATE_TIME_MS, System.currentTimeMillis())
                .where(isStream(streamId, taskId))
                .and(IS_SHARD_ROW)
                .and(PENDING.STATE.ne(DELETE))
                .execute();
    }

    // Makes or updates the row that counts a stream's failed attempts. The count only goes up when the
    // task has been run again since it was last marked, so one failed attempt is counted once however many
    // of its shards report it.
    private void markToRetry(final DSLContext context, final long streamId, final long taskId) {
        final long now = System.currentTimeMillis();
        context
                .insertInto(PENDING)
                .set(PENDING.INDEX_SHARD_ID, ATTEMPT_ROW_SHARD_ID)
                .set(PENDING.STREAM_ID, streamId)
                .set(PENDING.TASK_ID, taskId)
                .set(PENDING.ATTEMPT, 1)
                .set(PENDING.STATE, RETRY_PENDING)
                .set(PENDING.UPDATE_TIME_MS, now)
                .onDuplicateKeyUpdate()
                // MySQL applies these in order, so the count is worked out from the state as it was.
                .set(PENDING.ATTEMPT, DSL
                        .when(PENDING.STATE.eq(RETRYING), PENDING.ATTEMPT.plus(1))
                        .otherwise(PENDING.ATTEMPT))
                .set(PENDING.STATE, RETRY_PENDING)
                .set(PENDING.UPDATE_TIME_MS, now)
                .execute();
    }

    private <R> R transactionResult(final String name, final Function<DSLContext, R> function) {
        return JooqUtil.withDeadlockRetries(
                () -> JooqUtil.transactionResult(indexDbConnProvider, function),
                () -> name);
    }
}
