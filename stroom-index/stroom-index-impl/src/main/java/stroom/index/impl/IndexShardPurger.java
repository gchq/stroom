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

package stroom.index.impl;

import stroom.processor.api.HeldProcessorTaskService;
import stroom.processor.api.ProcessorTaskState;
import stroom.processor.shared.TaskStatus;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/// Deletes the streams that are waiting to be purged from a shard, with a writer that has the shard open, and
/// records that it has, so that the writer's next commit removes the purges. No commit is needed between a
/// purge and new documents, as Lucene applies a delete only to the documents added before it.
@Singleton
public class IndexShardPurger {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardPurger.class);

    private final IndexShardPurgeDao purgeDao;
    private final IndexShardTaskDao taskDao;
    private final HeldProcessorTaskService heldProcessorTaskService;
    private final IndexRunRegistry runRegistry;

    /// Creates the purger.
    ///
    /// @param purgeDao                 Reads and marks the purges.
    /// @param taskDao                  Reads the rows of the tasks that wrote the streams.
    /// @param heldProcessorTaskService Reads the status of those tasks.
    /// @param runRegistry              The runs on this node, which may have to be stopped.
    @Inject
    IndexShardPurger(final IndexShardPurgeDao purgeDao,
                     final IndexShardTaskDao taskDao,
                     final HeldProcessorTaskService heldProcessorTaskService,
                     final IndexRunRegistry runRegistry) {
        this.purgeDao = purgeDao;
        this.taskDao = taskDao;
        this.heldProcessorTaskService = heldProcessorTaskService;
        this.runRegistry = runRegistry;
    }

    /// Purges a stream from a shard before a run's first document goes into it (rule 1). First the rows that
    /// other tasks have for the stream in the shard are looked at: a row of a task that is aborted, deleted
    /// unfinished, or on a later run, becomes a purge now, as it might otherwise become one after this run had
    /// written and delete what it wrote. Then every purge of the stream in the shard is done.
    ///
    /// @param indexShardId The id of the shard.
    /// @param streamId     The id of the stream.
    /// @param taskId       The id of the task the run belongs to, whose own row is left alone.
    /// @param writer       The writer the run is about to write to.
    /// @throws IndexRunTrackingException If a purge could not be recorded as done, so that nothing must be
    ///                                   added to the shard.
    public void purgeBeforeWriting(final long indexShardId,
                                   final long streamId,
                                   final long taskId,
                                   final IndexShardWriter writer) {
        final List<IndexShardTaskRow> others = taskDao.findByShardAndStream(indexShardId, streamId)
                .stream()
                .filter(row -> row.taskId() != taskId)
                .toList();
        if (!others.isEmpty()) {
            final Map<Long, ProcessorTaskState> states = heldProcessorTaskService.getStates(
                    others.stream().map(IndexShardTaskRow::taskId).collect(Collectors.toSet()));
            final List<IndexShardTaskRow> stale = others.stream()
                    .filter(row -> isStale(row, states.get(row.taskId())))
                    .toList();
            if (!stale.isEmpty()) {
                taskDao.toPurges(stale);
            }
        }

        final Optional<IndexShardPurge> purge = purgeDao.fetch(indexShardId, streamId);
        if (purge.isPresent() && !writer.getWriterId().equals(purge.get().writerId())) {
            if (!purge(indexShardId, streamId, writer)) {
                throw new IndexRunTrackingException("Unable to record that stream " + streamId
                                                    + " has been purged from index shard " + indexShardId, null);
            }
        }
    }

    /// Purges every stream that is waiting to be purged from a shard, e.g. before its writer commits (rule 2) or
    /// when a shard nobody has open is swept (rule 3).
    ///
    /// @param indexShardId The id of the shard.
    /// @param writer       The writer that has the shard open.
    public void purgeWaiting(final long indexShardId, final IndexShardWriter writer) {
        for (final IndexShardPurge purge : purgeDao.findByShard(indexShardId)) {
            if (purge.isWaiting()) {
                try {
                    purge(indexShardId, purge.streamId(), writer);
                } catch (final RuntimeException e) {
                    // It stays waiting, for the next commit.
                    LOGGER.error(() -> LogUtil.message("Unable to purge stream {} from index shard {}: {}",
                            purge.streamId(), indexShardId, LogUtil.exceptionMessage(e)), e);
                }
            }
        }
    }

    // Purges of one shard take turns on its writer, and each looks at the purge again before it deletes.
    // Returns false if the purge could not be recorded as done.
    private boolean purge(final long indexShardId, final long streamId, final IndexShardWriter writer) {
        synchronized (writer) {
            final String writerId = Objects.requireNonNull(writer.getWriterId());
            Optional<IndexShardPurge> purge = purgeDao.fetch(indexShardId, streamId);
            if (purge.isEmpty() || writerId.equals(purge.get().writerId())) {
                // Done meanwhile, or already done by this writer.
                return true;
            }
            if (!purge.get().isWaiting()) {
                // Deleted by a writer that is no longer the shard's, so it was never committed.
                purgeDao.request(indexShardId, streamId);
                purge = purgeDao.fetch(indexShardId, streamId);
                if (purge.isEmpty()) {
                    return true;
                }
            }
            stopReplacedRuns(indexShardId, streamId);
            writer.deleteStreams(List.of(streamId));
            final boolean recorded = purgeDao.markDeleted(purge.get(), writerId).isPresent();
            LOGGER.debug("Purged stream {} from index shard {}, recorded: {}", streamId, indexShardId, recorded);
            return recorded;
        }
    }

    // A run of this node that wrote the stream to the shard, but no longer has a row there and whose task is no
    // longer on it, has been replaced. Stopped now, it can add nothing after the stream is deleted.
    private void stopReplacedRuns(final long indexShardId, final long streamId) {
        final List<TaskIndexRunTracker> runs = runRegistry.findWriting(indexShardId, streamId);
        if (runs.isEmpty()) {
            return;
        }
        final Set<Long> taskIds = runs.stream().map(TaskIndexRunTracker::getTaskId).collect(Collectors.toSet());
        final Map<Long, ProcessorTaskState> states = heldProcessorTaskService.getStates(taskIds);
        for (final TaskIndexRunTracker run : runs) {
            final boolean hasRow = taskDao.fetch(run.getTaskId(), indexShardId)
                    .map(row -> row.run() == run.getRun())
                    .orElse(false);
            final ProcessorTaskState state = states.get(run.getTaskId());
            final boolean stillOnRun = state != null
                                       && TaskStatus.PROCESSING.equals(state.status())
                                       && state.runCount() == run.getRun();
            if (!hasRow && !stillOnRun) {
                run.stop();
            }
        }
    }

    // A row of another task whose documents must go before a run writes the stream to the shard.
    private static boolean isStale(final IndexShardTaskRow row, final ProcessorTaskState state) {
        if (state == null) {
            return false;
        }
        return switch (state.status()) {
            case ABORTED -> true;
            // Deleted unfinished. A task that completed has no rows left that are not committed.
            case DELETED -> !IndexShardTaskState.COMMITTED.equals(row.state());
            default -> row.run() < state.runCount();
        };
    }
}
