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

import stroom.index.shared.IndexShard;
import stroom.processor.api.HeldProcessorTaskService;
import stroom.processor.api.ProcessorTaskState;
import stroom.processor.shared.TaskStatus;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/// The work of the one-minute job *Index Shard Stream Recovery*. Any node can do it, as it only uses the
/// database, and everything it changes is changed only if it is unchanged since it was read, so nodes doing it at
/// the same time do no harm. It puts right whatever was missed when it should have happened, e.g. as a node died.
@Singleton
public class IndexShardStreamRecovery {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardStreamRecovery.class);

    static final String REASON_DOCUMENTS_LOST = "Documents it indexed were lost before they were committed";

    // The most rows, tasks or shards looked at in one go.
    private static final int BATCH_SIZE = 1_000;
    // Rows that have changed more recently than this are left alone, as their runs may be under way.
    private static final Duration QUIET_PERIOD = Duration.ofMinutes(10);
    // How long a task is held before the job looks at it, so that it does not race the run that held it.
    private static final Duration HELD_GRACE = Duration.ofMinutes(1);
    // How long tasks may be Committing, or purges waiting, before they are warned of.
    private static final Duration WARN_AFTER = Duration.ofHours(1);

    private final IndexShardDao indexShardDao;
    private final IndexShardTaskDao taskDao;
    private final IndexShardPurgeDao purgeDao;
    private final HeldProcessorTaskService heldProcessorTaskService;
    private final IndexShardTaskCompletion completion;
    private final IndexShardWriterCache indexShardWriterCache;
    private final IndexShardResponsibility indexShardResponsibility;
    private final SecurityContext securityContext;
    private final Provider<IndexWriterConfig> indexWriterConfigProvider;
    private volatile long lastWarningMs;

    /// Creates the job's work.
    ///
    /// @param indexShardDao             Reads and writes the shards' leases.
    /// @param taskDao                   Reads and changes the tasks' rows.
    /// @param purgeDao                  Reads and changes the purges.
    /// @param heldProcessorTaskService  Reads, completes and puts back the tasks.
    /// @param completion                Completes the tasks whose rows are all committed.
    /// @param indexShardWriterCache     Opens the shards that are swept.
    /// @param indexShardResponsibility  Says which shards this node sweeps.
    /// @param securityContext           Runs the job as the processing user.
    /// @param indexWriterConfigProvider Supplies the takeover grace period and replaced commit retention.
    @Inject
    IndexShardStreamRecovery(final IndexShardDao indexShardDao,
                             final IndexShardTaskDao taskDao,
                             final IndexShardPurgeDao purgeDao,
                             final HeldProcessorTaskService heldProcessorTaskService,
                             final IndexShardTaskCompletion completion,
                             final IndexShardWriterCache indexShardWriterCache,
                             final IndexShardResponsibility indexShardResponsibility,
                             final SecurityContext securityContext,
                             final Provider<IndexWriterConfig> indexWriterConfigProvider) {
        this.indexShardDao = indexShardDao;
        this.taskDao = taskDao;
        this.purgeDao = purgeDao;
        this.heldProcessorTaskService = heldProcessorTaskService;
        this.completion = completion;
        this.indexShardWriterCache = indexShardWriterCache;
        this.indexShardResponsibility = indexShardResponsibility;
        this.securityContext = securityContext;
        this.indexWriterConfigProvider = indexWriterConfigProvider;
    }

    /// Runs every part of the job once. The entry point of the job.
    public void exec() {
        securityContext.asProcessingUser(() -> {
            run("releaseLapsedLeases", this::releaseLapsedLeases);
            run("dropRowsOfDeletedShards", this::dropRowsOfDeletedShards);
            run("resetLostPurges", purgeDao::resetLost);
            run("putBackLostTasks", this::putBackLostTasks);
            run("finishHeldTasks", this::finishHeldTasks);
            run("tidyRows", this::tidyRows);
            run("sweepPurges", this::sweepPurges);
            run("warnOfOldRows", this::warnOfOldRows);
        });
    }

    private void run(final String name, final Runnable runnable) {
        try {
            LOGGER.logDurationIfDebugEnabled(runnable, name);
        } catch (final RuntimeException e) {
            LOGGER.error(() -> LogUtil.message("Error in index shard stream recovery ({}): {}",
                    name, e.getMessage()), e);
        }
    }

    /// Takes shards on shared volumes from nodes that stopped renewing their leases long enough ago, e.g.
    /// because they died. The shards are left closed, for any node to write to.
    void releaseLapsedLeases() {
        final IndexWriterConfig config = indexWriterConfigProvider.get();
        final int count = indexShardDao.releaseLapsedLeases(
                config.getShardLeaseTakeoverGrace().toMillis(),
                config.getReplacedCommitRetention().toMillis());
        if (count > 0) {
            LOGGER.warn(() -> LogUtil.message(
                    "Took {} index shard(s) from nodes whose leases on them expired more than {} ago. What those " +
                    "nodes had not committed will be indexed again.",
                    count, config.getShardLeaseTakeoverGrace()));
        }
    }

    /// Drops the rows and purges of shards that are deleted, so that deleting a shard never makes a task wait
    /// or run again, and the purges that can never be done or need not be.
    void dropRowsOfDeletedShards() {
        taskDao.deleteForDeletedShards();
        purgeDao.deleteForUnusableShards();
    }

    /// Puts back each `Committing` task that has a row whose writer has gone, as the documents its run gave
    /// that writer were lost, or might have been. Lost rows of a run that has been replaced become purges.
    void putBackLostTasks() {
        List<IndexShardTaskRow> lost = taskDao.findLost(null, BATCH_SIZE);
        while (!lost.isEmpty()) {
            final Map<Long, List<IndexShardTaskRow>> byTask = byTask(lost);
            final Map<Long, ProcessorTaskState> states = heldProcessorTaskService.getStates(byTask.keySet());
            byTask.forEach((taskId, rows) -> {
                final ProcessorTaskState state = states.get(taskId);
                if (state == null) {
                    return;
                }
                if (TaskStatus.COMMITTING.equals(state.status())
                    && rows.stream().anyMatch(row -> row.run() == state.runCount())
                    && heldProcessorTaskService.putBack(taskId, state.runCount(), REASON_DOCUMENTS_LOST)) {
                    LOGGER.info("Put back processor task {} as documents it indexed were lost", taskId);
                }
                toPurges(rows, row -> row.run() < state.runCount());
            });
            lost = taskDao.findLost(lost.getLast(), BATCH_SIZE);
        }
    }

    /// Makes up for anything missed when runs ended as `Committing`: rows still *writing* are marked *written*,
    /// and tasks with nothing left to commit, including any whose rows have all gone, are completed.
    void finishHeldTasks() {
        final Instant olderThan = Instant.now().minus(HELD_GRACE);
        List<ProcessorTaskState> held = heldProcessorTaskService.findHeld(olderThan, 0, BATCH_SIZE);
        while (!held.isEmpty()) {
            for (final ProcessorTaskState state : held) {
                try {
                    taskDao.markWritten(state.taskId(), state.runCount());
                    completion.completeIfCommitted(state.taskId(), state.runCount());
                } catch (final RuntimeException e) {
                    LOGGER.error(() -> LogUtil.message("Unable to finish held processor task {}: {}",
                            state.taskId(), LogUtil.exceptionMessage(e)), e);
                }
            }
            held = heldProcessorTaskService.findHeld(olderThan, held.getLast().taskId(), BATCH_SIZE);
        }
    }

    /// Removes or purges the rows that are finished with: those of a run that completed, and of tasks that
    /// were aborted or deleted, or replaced by a later run. Only rows that have not changed for a while are
    /// looked at, in batches.
    void tidyRows() {
        final long beforeMs = taskDao.getDatabaseTimeMs() - QUIET_PERIOD.toMillis();
        List<IndexShardTaskRow> rows = taskDao.findNotUpdatedSince(beforeMs, null, BATCH_SIZE);
        while (!rows.isEmpty()) {
            final Map<Long, List<IndexShardTaskRow>> byTask = byTask(rows);
            final Map<Long, ProcessorTaskState> states = heldProcessorTaskService.getStates(byTask.keySet());
            byTask.forEach((taskId, taskRows) -> {
                final ProcessorTaskState state = states.get(taskId);
                if (state != null) {
                    tidy(state, taskRows);
                }
            });
            rows = taskDao.findNotUpdatedSince(beforeMs, rows.getLast(), BATCH_SIZE);
        }
    }

    private void tidy(final ProcessorTaskState state, final List<IndexShardTaskRow> rows) {
        switch (state.status()) {
            case COMPLETE -> {
                // The node died between completing the task and removing its rows.
                rows.stream()
                        .filter(row -> row.run() == state.runCount())
                        .forEach(taskDao::delete);
                toPurges(rows, row -> row.run() < state.runCount());
            }
            case ABORTED -> toPurges(rows, row -> true);
            case DELETED -> {
                // A task deleted unfinished has what it indexed purged. One that completed has no row left that
                // is not committed, as completed tasks become deleted with their filter.
                final int latestRun = rows.stream().mapToInt(IndexShardTaskRow::run).max().orElse(0);
                final boolean unfinished = rows.stream()
                        .anyMatch(row -> row.run() == latestRun
                                         && !IndexShardTaskState.COMMITTED.equals(row.state()));
                if (unfinished) {
                    toPurges(rows, row -> true);
                } else {
                    rows.stream().filter(row -> row.run() == latestRun).forEach(taskDao::delete);
                    toPurges(rows, row -> row.run() < latestRun);
                }
            }
            // Rows of runs that have been replaced. Those of the task's latest run are left to that run.
            default -> toPurges(rows, row -> row.run() < state.runCount());
        }
    }

    /// Purges the shards that have purges waiting and that nobody has open, of those this node looks after,
    /// so that a shard that is no longer written to is still purged (rule 3). Each is opened and committed
    /// once, which purges everything waiting in it.
    void sweepPurges() {
        final Predicate<IndexShard> isResponsible = indexShardResponsibility.createTest();
        for (final Long indexShardId : purgeDao.findShardsToSweep(BATCH_SIZE)) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            try {
                final Optional<IndexShard> indexShard = indexShardDao.fetch(indexShardId);
                if (indexShard.isPresent() && isResponsible.test(indexShard.get())) {
                    indexShardWriterCache.getOrOpenWriter(indexShardId);
                    // Its commit purges everything waiting first.
                    indexShardWriterCache.flush(indexShardId);
                }
            } catch (final RuntimeException e) {
                // Tried again next time, e.g. as another node has the shard open, which purges it itself.
                LOGGER.debug(() -> LogUtil.message("Unable to sweep the purges of index shard {}: {}",
                        indexShardId, LogUtil.exceptionMessage(e)), e);
            }
        }
    }

    /// Warns, at most once an hour, of tasks that have been `Committing`, and purges that have been waiting,
    /// for over an hour, e.g. as commits are failing or a shard can't be written to.
    void warnOfOldRows() {
        final long nowMs = System.currentTimeMillis();
        if (nowMs - lastWarningMs < WARN_AFTER.toMillis()) {
            return;
        }
        final List<ProcessorTaskState> oldHeld = heldProcessorTaskService.findHeld(
                Instant.ofEpochMilli(nowMs).minus(WARN_AFTER), 0, BATCH_SIZE);
        final Optional<Long> oldestPurgeMs = purgeDao.findOldestCreateTimeMs();
        final long dbNowMs = taskDao.getDatabaseTimeMs();
        final boolean oldPurges = oldestPurgeMs.isPresent()
                                  && dbNowMs - oldestPurgeMs.get() > WARN_AFTER.toMillis();
        if (!oldHeld.isEmpty()) {
            LOGGER.warn(() -> LogUtil.message("{}{} processor tasks have been Committing for over {}, waiting for "
                                              + "index shards to commit. Check that the job Index Writer Flush is "
                                              + "enabled and that commits are not failing.",
                    oldHeld.size() == BATCH_SIZE
                            ? "At least "
                            : "",
                    oldHeld.size(), WARN_AFTER));
        }
        if (oldPurges) {
            LOGGER.warn(() -> LogUtil.message("{} streams are waiting to be purged from index shards, the oldest "
                                              + "for {}. Check that the shards can be written to.",
                    purgeDao.count(), Duration.ofMillis(dbNowMs - oldestPurgeMs.get())));
        }
        if (!oldHeld.isEmpty() || oldPurges) {
            lastWarningMs = nowMs;
        }
    }

    private void toPurges(final List<IndexShardTaskRow> rows, final Predicate<IndexShardTaskRow> filter) {
        final List<IndexShardTaskRow> selected = rows.stream().filter(filter).toList();
        if (!selected.isEmpty()) {
            taskDao.toPurges(selected);
        }
    }

    private static Map<Long, List<IndexShardTaskRow>> byTask(final List<IndexShardTaskRow> rows) {
        return rows.stream().collect(Collectors.groupingBy(
                IndexShardTaskRow::taskId,
                LinkedHashMap::new,
                Collectors.toCollection(ArrayList::new)));
    }
}
