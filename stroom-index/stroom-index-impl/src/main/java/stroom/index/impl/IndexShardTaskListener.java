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

import stroom.processor.api.ProcessorTaskListener;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.TaskStatus;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;

import java.util.List;
import java.util.function.Predicate;

/// Keeps the rows of `index_shard_task` in step with how each run of a processor task goes. The processor
/// decides the task's status. This only changes the rows, and completes tasks once their rows are committed.
/// A call that is missed, e.g. as the node dies, is made up for by the one-minute job.
class IndexShardTaskListener implements ProcessorTaskListener {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardTaskListener.class);

    // Tries at marking a held run's rows as written before leaving it to the one-minute job.
    private static final int MAX_TRIES = 3;
    private static final long RETRY_WAIT_MS = 1_000;

    private final IndexShardTaskDao taskDao;
    private final IndexShardTaskCompletion completion;

    @Inject
    IndexShardTaskListener(final IndexShardTaskDao taskDao,
                           final IndexShardTaskCompletion completion) {
        this.taskDao = taskDao;
        this.completion = completion;
    }

    /// Turns the rows of earlier runs into purges, so that what they wrote is deleted before the shards are
    /// written to again, and the run starts with no rows.
    ///
    /// @param task The task, with the run's run count.
    @Override
    public void beforeRerun(final ProcessorTask task) {
        toPurges(task, row -> row.run() < task.getRunCount());
    }

    /// Marks the run's rows as *written*, as it has ended and its task is `Committing`, and completes the task
    /// if every shard it wrote to has committed already.
    ///
    /// @param task The task, with the run's run count.
    @Override
    public void held(final ProcessorTask task) {
        for (int i = 1; i <= MAX_TRIES; i++) {
            try {
                taskDao.markWritten(task.getId(), task.getRunCount());
                // Every shard may have closed before the run ended.
                completion.check(List.of(task.getId()));
                return;
            } catch (final RuntimeException e) {
                if (i == MAX_TRIES) {
                    // The one-minute job does it for any held task whose rows are still being written.
                    LOGGER.error(() -> LogUtil.message("Unable to mark the rows of processor task {} as written: {}",
                            task.getId(), LogUtil.exceptionMessage(e)), e);
                } else {
                    ThreadUtil.sleep(RETRY_WAIT_MS * i);
                }
            }
        }
    }

    /// Turns all the task's rows into purges if it has been aborted. Rows of a task that is to be run again
    /// are left for that run to deal with.
    ///
    /// @param task   The task, with the run's run count.
    /// @param status The status it has been set to.
    @Override
    public void ended(final ProcessorTask task, final TaskStatus status) {
        if (TaskStatus.ABORTED.equals(status)) {
            toPurges(task, row -> true);
        }
    }

    /// Turns the rows the run still has into purges, as its task has been taken from it.
    ///
    /// @param task The task, with the run's run count.
    @Override
    public void replaced(final ProcessorTask task) {
        toPurges(task, row -> row.run() == task.getRunCount());
    }

    private void toPurges(final ProcessorTask task, final Predicate<IndexShardTaskRow> filter) {
        final List<IndexShardTaskRow> rows = taskDao.findByTask(task.getId())
                .stream()
                .filter(filter)
                .toList();
        if (!rows.isEmpty()) {
            final List<IndexShardTaskRow> purged = taskDao.toPurges(rows);
            LOGGER.debug("Turned {} of {} rows of processor task {} into purges",
                    purged.size(), rows.size(), task.getId());
        }
    }
}
