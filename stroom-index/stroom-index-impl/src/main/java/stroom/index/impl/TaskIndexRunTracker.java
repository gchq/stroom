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
import stroom.processor.api.RunIndexing;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.TaskStatus;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// Keeps the rows of `index_shard_task` for one run of a processor task. The node remembers which shards and
/// writers the run has rows for, so it saves one row for each shard, not one for each document.
class TaskIndexRunTracker implements IndexRunTracker {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TaskIndexRunTracker.class);

    // Tries at saving a row, when another node changes it at the same moment.
    private static final int MAX_TRIES = 5;

    private final IndexShardTaskDao taskDao;
    private final HeldProcessorTaskService heldProcessorTaskService;
    private final SecurityContext securityContext;
    private final long taskId;
    private final int run;
    private final long streamId;
    private final RunIndexing runIndexing;
    // The row saved for each shard, with what it was before and whether the writer has had a document.
    private final Map<Long, Saved> saved = new HashMap<>();
    private boolean written;

    TaskIndexRunTracker(final IndexShardTaskDao taskDao,
                        final HeldProcessorTaskService heldProcessorTaskService,
                        final SecurityContext securityContext,
                        final ProcessorTask task,
                        final RunIndexing runIndexing) {
        this.taskDao = taskDao;
        this.heldProcessorTaskService = heldProcessorTaskService;
        this.securityContext = securityContext;
        this.taskId = task.getId();
        this.run = task.getRunCount();
        this.streamId = Objects.requireNonNull(task.getMetaId());
        this.runIndexing = runIndexing;
    }

    @Override
    public synchronized void beforeAdd(final long indexShardId, final String writerId) {
        Objects.requireNonNull(writerId, "A writer of a run's documents must have an id");
        final Saved current = saved.get(indexShardId);
        if (current != null && current.row().writerId().equals(writerId)) {
            return;
        }
        securityContext.asProcessingUser(() -> {
            saved.put(indexShardId, save(indexShardId, writerId));
            // The task may have been taken from this run since it started, e.g. by the reaper, and run again
            // elsewhere, which turned this run's rows into purges. A row saved now would then be missed.
            checkStillThisRuns();
        });
    }

    // Saves the row that says this run is writing to a shard, before its first document there.
    private Saved save(final long indexShardId, final String writerId) {
        for (int i = 0; i < MAX_TRIES; i++) {
            final Optional<IndexShardTaskRow> existing = taskDao.fetch(taskId, indexShardId);
            final Optional<Saved> result = existing.isEmpty()
                    ? taskDao.insertWriting(taskId, indexShardId, streamId, run, writerId)
                            .map(row -> new Saved(row, null, false))
                    : saveOver(existing.get(), writerId);
            if (result.isPresent()) {
                return result.get();
            }
            // Changed meanwhile by another node, e.g. a run that replaced this one, so look again.
        }
        throw new RunStoppedException("Unable to record that index shard " + indexShardId
                                      + " is being written to by processor task " + taskId);
    }

    // Saves the row over one the task already has for the shard. Empty if that row changed meanwhile.
    private Optional<Saved> saveOver(final IndexShardTaskRow existing, final String writerId) {
        if (existing.run() > run) {
            // A later run of the task has written to the shard, so this run has been replaced.
            throw new RunStoppedException("Processor task " + taskId + " is being run again by run "
                                          + existing.run() + ", so run " + run + " must stop");
        } else if (existing.run() < run) {
            // Left by an earlier run that was not turned into a purge before this run started, e.g. as it was
            // still going. What it wrote must go, and this run's row replaces it.
            if (taskDao.toPurges(List.of(existing)).isEmpty()) {
                return Optional.empty();
            }
            return taskDao.insertWriting(taskId, existing.indexShardId(), streamId, run, writerId)
                    .map(row -> new Saved(row, null, false));
        } else if (existing.writerId().equals(writerId)) {
            return Optional.of(new Saved(existing, existing, false));
        } else if (IndexShardTaskState.COMMITTED.equals(existing.state())) {
            // The shard closed cleanly, which committed what this run had given it, and has been opened again.
            return taskDao.update(existing, run, writerId, IndexShardTaskState.WRITING)
                    .map(row -> new Saved(row, existing, false));
        } else {
            // The shard was opened again without a clean close, so what this run gave the earlier writer was
            // lost. The run is put back to be run again.
            runIndexing.documentsLost();
            throw new RunStoppedException("Documents that processor task " + taskId + " gave to index shard "
                                          + existing.indexShardId() + " have been lost");
        }
    }

    private void checkStillThisRuns() {
        final ProcessorTaskState state = heldProcessorTaskService.getStates(List.of(taskId)).get(taskId);
        if (state == null || !TaskStatus.PROCESSING.equals(state.status()) || state.runCount() != run) {
            throw new RunStoppedException(LogUtil.message(
                    "Processor task {} is no longer Processing on run {} ({}), so the run must stop",
                    taskId, run, state));
        }
    }

    @Override
    public synchronized void added(final long indexShardId, final String writerId) {
        final Saved current = saved.get(indexShardId);
        if (current != null && current.row().writerId().equals(writerId) && !current.documentAdded()) {
            saved.put(indexShardId, new Saved(current.row(), current.previous(), true));
        }
        written = true;
    }

    @Override
    public synchronized void addFailed(final long indexShardId, final String writerId) {
        final Saved current = saved.get(indexShardId);
        if (current == null || !current.row().writerId().equals(writerId) || current.documentAdded()) {
            return;
        }
        // The writer was given none of this run's documents, so the row must not say that it was. It might
        // otherwise be taken for lost documents, or a row for documents that were committed earlier might be
        // thrown away.
        saved.remove(indexShardId);
        try {
            securityContext.asProcessingUser(() -> {
                final IndexShardTaskRow row = current.row();
                final IndexShardTaskRow previous = current.previous();
                if (previous == null) {
                    taskDao.delete(row);
                } else if (!previous.equals(row)) {
                    taskDao.update(row, previous.run(), previous.writerId(), previous.state());
                }
            });
        } catch (final RuntimeException e) {
            // Left as it is, the row can only cause the task to be run again for nothing.
            LOGGER.error(() -> LogUtil.message("Unable to put back the row of processor task {} for index shard "
                                               + "{}: {}", taskId, indexShardId, LogUtil.exceptionMessage(e)), e);
        }
    }

    @Override
    public synchronized boolean hasWritten() {
        return written;
    }


    // --------------------------------------------------------------------------------


    // A row this run saved, what it was before, or null if the run added it, and whether the writer it names
    // has had a document of the run.
    private record Saved(IndexShardTaskRow row, IndexShardTaskRow previous, boolean documentAdded) {

    }
}
