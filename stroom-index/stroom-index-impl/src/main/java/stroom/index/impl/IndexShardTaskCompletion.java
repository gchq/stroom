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
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/// Completes the processor tasks that are `Committing` once every shard their run wrote to has committed.
/// Tasks and their rows may be in different databases, so they can't change in one transaction. What makes
/// this safe is the order: the task's status is read first, and no row of a run can be added once its task
/// is `Committing`, so the rows read next are all the run will ever have.
@Singleton
public class IndexShardTaskCompletion {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardTaskCompletion.class);

    private final IndexShardTaskDao taskDao;
    private final HeldProcessorTaskService heldProcessorTaskService;
    private final SecurityContext securityContext;

    /// Creates the completion check.
    ///
    /// @param taskDao                  Reads and removes the tasks' rows.
    /// @param heldProcessorTaskService Reads and completes the tasks.
    /// @param securityContext          Completes them as the processing user.
    @Inject
    IndexShardTaskCompletion(final IndexShardTaskDao taskDao,
                             final HeldProcessorTaskService heldProcessorTaskService,
                             final SecurityContext securityContext) {
        this.taskDao = taskDao;
        this.heldProcessorTaskService = heldProcessorTaskService;
        this.securityContext = securityContext;
    }

    /// Completes each of the tasks that is `Committing` and whose run's rows are all *committed*. A task that
    /// is missed, e.g. as the database could not be reached, is found later by the one-minute job.
    ///
    /// @param taskIds The ids of the tasks, e.g. those whose rows a commit has just marked.
    public void check(final Collection<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return;
        }
        securityContext.asProcessingUser(() -> {
            final Map<Long, ProcessorTaskState> states;
            try {
                // 1. The task's status and run, read first.
                states = heldProcessorTaskService.getStates(taskIds);
            } catch (final RuntimeException e) {
                LOGGER.error(() -> LogUtil.message("Unable to read the status of {} processor tasks: {}",
                        taskIds.size(), LogUtil.exceptionMessage(e)), e);
                return;
            }
            for (final ProcessorTaskState state : states.values()) {
                if (TaskStatus.COMMITTING.equals(state.status())) {
                    try {
                        completeIfCommitted(state.taskId(), state.runCount());
                    } catch (final RuntimeException e) {
                        LOGGER.error(() -> LogUtil.message("Unable to complete processor task {}: {}",
                                state.taskId(), LogUtil.exceptionMessage(e)), e);
                    }
                }
            }
        });
    }

    /// Completes a task that was `Committing` on a run when it was last read, if every row of that run is
    /// *committed*, and then removes those rows.
    ///
    /// @param taskId The id of the task.
    /// @param run    The run the task was `Committing` on.
    /// @return True if the task was completed.
    public boolean completeIfCommitted(final long taskId, final int run) {
        // 2. That run's rows, all of which must be committed.
        final List<IndexShardTaskRow> rows = taskDao.findByTask(taskId)
                .stream()
                .filter(row -> row.run() == run)
                .toList();
        final boolean committed = rows
                .stream()
                .allMatch(row -> IndexShardTaskState.COMMITTED.equals(row.state()));
        if (!committed) {
            return false;
        }
        // 3. Complete the task, which fails if it is no longer Committing on that run.
        if (!heldProcessorTaskService.completeHeld(taskId, run)) {
            LOGGER.debug("Processor task {} was not completed as it is no longer Committing on run {}", taskId, run);
            return false;
        }
        LOGGER.debug("Completed processor task {} on run {}", taskId, run);
        // 4. Remove that run's rows, and no others. If this fails the one-minute job removes them.
        taskDao.deleteRun(taskId, run);
        return true;
    }
}
