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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/// Keeps the rows of `index_shard_task` for one run of a processor task. The node remembers which shards and
/// writers the run has rows for, so it saves one row for each shard, not one for each document.
class TaskIndexRunTracker implements IndexRunTracker {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TaskIndexRunTracker.class);

    // Tries at saving a row, when another node changes it at the same moment.
    private static final int MAX_TRIES = 5;
    // The longest a stop waits for a document being added.
    private static final long STOP_WAIT_MS = 30_000;

    private final IndexShardTaskDao taskDao;
    private final HeldProcessorTaskService heldProcessorTaskService;
    private final SecurityContext securityContext;
    private final long taskId;
    private final int run;
    private final long streamId;
    private final RunIndexing runIndexing;
    private final IndexShardPurger purger;
    // The documents being given to writers now. Counted before the run's stop flag is looked at, so that a stop
    // can wait for them.
    private final AtomicInteger adding = new AtomicInteger();
    // Set while the row for a shard is being saved, before anything is given to the shard's writer. A stop does
    // not wait for that, as it may be waiting for the same writer, and the run looks again once it has saved.
    private volatile boolean saving;
    private volatile boolean stopped;
    // The row saved for each shard, with what it was before and whether the writer has had a document. Read
    // without the tracker's lock by purges on other threads, which may hold a writer's lock that the run is
    // waiting for while it holds the tracker's.
    private final Map<Long, Saved> saved = new ConcurrentHashMap<>();
    private boolean written;

    TaskIndexRunTracker(final IndexShardTaskDao taskDao,
                        final HeldProcessorTaskService heldProcessorTaskService,
                        final SecurityContext securityContext,
                        final ProcessorTask task,
                        final RunIndexing runIndexing,
                        final IndexShardPurger purger) {
        this.taskDao = taskDao;
        this.heldProcessorTaskService = heldProcessorTaskService;
        this.securityContext = securityContext;
        this.taskId = task.getId();
        this.run = task.getRunCount();
        this.streamId = Objects.requireNonNull(task.getMetaId());
        this.runIndexing = runIndexing;
        this.purger = purger;
    }

    @Override
    public void beforeAdd(final long indexShardId, final IndexShardWriter writer) {
        adding.incrementAndGet();
        try {
            checkNotStopped();
            saving = true;
            try {
                saveFirst(indexShardId, writer);
            } finally {
                saving = false;
            }
            // Again, as a stop made while saving did not wait.
            checkNotStopped();
        } catch (final RuntimeException e) {
            adding.decrementAndGet();
            throw e;
        }
    }

    private void checkNotStopped() {
        if (stopped) {
            // Replaced, and what it wrote is being purged.
            runIndexing.documentsLost();
            throw new RunStoppedException("Run " + run + " of processor task " + taskId
                                          + " has been replaced, so must add nothing more");
        }
    }

    private synchronized void saveFirst(final long indexShardId, final IndexShardWriter writer) {
        final String writerId = Objects.requireNonNull(writer.getWriterId(),
                "A writer of a run's documents must have an id");
        final Saved current = saved.get(indexShardId);
        if (current != null && current.row().writerId().equals(writerId)) {
            return;
        }
        securityContext.asProcessingUser(() -> {
            saved.put(indexShardId, save(indexShardId, writerId));
            // The task may have been taken from this run since it started, e.g. by the reaper, and run again
            // elsewhere, which turned this run's rows into purges. A row saved now would then be missed.
            checkStillThisRuns();
            // Looked for only once the row is saved, as a purge made in between would otherwise later delete
            // what this run writes.
            purger.purgeBeforeWriting(indexShardId, streamId, taskId, writer);
        });
    }

    /// Stops the run adding any more documents, as it has been replaced, and waits for any it is adding now.
    /// It waits only for a document being given to a writer, never for the run itself, nor for a run that is
    /// still saving its row, which looks at the stop once it has.
    void stop() {
        stopped = true;
        final long giveUpAtMs = System.currentTimeMillis() + STOP_WAIT_MS;
        while (adding.get() > 0 && !saving && System.currentTimeMillis() < giveUpAtMs) {
            Thread.onSpinWait();
        }
        LOGGER.info("Stopped run {} of processor task {} as it has been replaced", run, taskId);
    }

    /// Gets the id of the task this is a run of.
    ///
    /// @return The id.
    long getTaskId() {
        return taskId;
    }

    /// Gets the task's run count for this run.
    ///
    /// @return The run count.
    int getRun() {
        return run;
    }

    /// Gets the id of the stream the run indexes.
    ///
    /// @return The id.
    long getStreamId() {
        return streamId;
    }

    /// Says whether the run has saved a row for a shard. Takes no lock, so that a purge holding a writer's
    /// lock can ask without waiting for the run.
    ///
    /// @param indexShardId The id of the shard.
    /// @return True if it has.
    boolean hasWrittenTo(final long indexShardId) {
        return saved.containsKey(indexShardId);
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
        // Not stopped, as how the run is to end has not been said. It is tried again, uncounted.
        throw new IndexRunTrackingException("Unable to record that index shard " + indexShardId
                                            + " is being written to by processor task " + taskId
                                            + " as the record kept changing", null);
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
    public void added(final long indexShardId, final String writerId) {
        try {
            synchronized (this) {
                final Saved current = saved.get(indexShardId);
                if (current != null && current.row().writerId().equals(writerId) && !current.documentAdded()) {
                    saved.put(indexShardId, new Saved(current.row(), current.previous(), true));
                }
                written = true;
            }
        } finally {
            adding.decrementAndGet();
        }
    }

    @Override
    public void addFailed(final long indexShardId, final String writerId) {
        try {
            putBack(indexShardId, writerId);
        } finally {
            adding.decrementAndGet();
        }
    }

    private synchronized void putBack(final long indexShardId, final String writerId) {
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
