/*
 * Copyright 2016 Crown Copyright
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

package stroom.processor.impl;

import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;
import stroom.node.api.NodeInfo;
import stroom.processor.api.ErrorStreamException;
import stroom.processor.api.ProcessorResult;
import stroom.processor.api.ProcessorResultImpl;
import stroom.processor.api.ProcessorTaskExecutor;
import stroom.processor.api.ProcessorTaskListener;
import stroom.processor.api.RunIndexing;
import stroom.processor.api.RunOutput;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorType;
import stroom.processor.shared.TaskStatus;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;
import stroom.task.api.TerminationCause;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public class DataProcessorTaskHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataProcessorTaskHandler.class);

    static final String REASON_NODE_STOPPED = "Put back as the node running it was stopped";
    static final String REASON_HEARTBEAT_OVERDUE = "Put back as the node running it could not renew its heartbeat";
    static final String REASON_EARLIER_RUN = "An earlier run of it was still going on the same node";
    static final String REASON_DOCUMENTS_LOST = "Put back as documents it indexed were lost";

    // How long a run waits for an earlier run of the same task to stop. An earlier run is told to stop first, so this
    // only has to be long enough for it to notice.
    private static final Duration EARLIER_RUN_TIMEOUT = Duration.ofMinutes(1);

    private final Map<ProcessorType, Provider<ProcessorTaskExecutor>> executorProviders;
    private final ProcessorFilterCache processorFilterCache;
    private final ProcessorTaskDao processorTaskDao;
    private final Store streamStore;
    private final MetaService metaService;
    private final NodeInfo nodeInfo;
    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;
    private final ProcessorTaskHeartbeat processorTaskHeartbeat;
    private final ProcessorTaskOutputCleaner processorTaskOutputCleaner;
    private final Provider<ProcessorConfig> processorConfigProvider;
    private final Set<ProcessorTaskListener> listeners;

    @Inject
    DataProcessorTaskHandler(final Map<ProcessorType, Provider<ProcessorTaskExecutor>> executorProviders,
                             final ProcessorFilterCache processorFilterCache,
                             final ProcessorTaskDao processorTaskDao,
                             final Store streamStore,
                             final MetaService metaService,
                             final NodeInfo nodeInfo,
                             final SecurityContext securityContext,
                             final TaskContextFactory taskContextFactory,
                             final ProcessorTaskHeartbeat processorTaskHeartbeat,
                             final ProcessorTaskOutputCleaner processorTaskOutputCleaner,
                             final Provider<ProcessorConfig> processorConfigProvider,
                             final Set<ProcessorTaskListener> listeners) {
        this.executorProviders = executorProviders;
        this.processorFilterCache = processorFilterCache;
        this.processorTaskDao = processorTaskDao;
        this.streamStore = streamStore;
        this.metaService = metaService;
        this.nodeInfo = nodeInfo;
        this.securityContext = securityContext;
        this.taskContextFactory = taskContextFactory;
        this.processorTaskHeartbeat = processorTaskHeartbeat;
        this.processorTaskOutputCleaner = processorTaskOutputCleaner;
        this.processorConfigProvider = processorConfigProvider;
        this.listeners = listeners;
    }

    public ProcessorResult exec(final ProcessorTask task) {
        return exec(task, false);
    }

    /**
     * Runs a task and then sets its status, whatever happens. Only {@link ProcessorTaskDao#changeTaskStatus} failing
     * leaves it as it was, in which case it has no heartbeat so can be put back.
     *
     * @param alreadyClaimed True if this node took the task straight to PROCESSING when it claimed
     *                       it (gh-5699 worker node claiming), so there is no queued task to
     *                       transition here and no version check to lose. False when the task came
     *                       from the master's queue and still has to be moved to PROCESSING.
     */
    public ProcessorResult exec(final ProcessorTask task, final boolean alreadyClaimed) {
        final Run run = new Run(task, alreadyClaimed);
        try {
            // Perform processing as the filter owner.
            final UserRef runAsUser = getFilterRunAs(task.getProcessorFilter());
            securityContext.asUser(runAsUser, () -> securityContext.useAsRead(() -> {
                // Execute with a task context. The status is set inside it so that a node that is stopping waits for
                // that too.
                taskContextFactory.context(
                        "Data Processor",
                        TerminateHandlerFactory.NOOP_FACTORY,
                        taskContext -> exec(taskContext, run)).run();
            }));
        } catch (final RuntimeException e) {
            if (run.started) {
                throw e;
            }
            run.fault = new Fault("Unable to run as the user of the processor filter", e, false);
            end(run, Optional.empty());

        } catch (final Error e) {
            if (!run.started) {
                run.fault = new Fault("Unexpected error", e, true);
                end(run, Optional.empty());
            }
            throw e;

        } finally {
            // Unconditional so the registry cannot leak a run and heartbeat its task forever, even if the status could
            // not be set. A no-op if we never registered (the task was never moved to PROCESSING by us).
            if (run.registered != null) {
                processorTaskHeartbeat.deregister(run.registered);
            }
        }
        return run.result;
    }

    private UserRef getFilterRunAs(final ProcessorFilter filter) {
        if (filter == null) {
            throw new RuntimeException("The task has no processor filter");
        }
        if (filter.getRunAsUser() == null) {
            throw new RuntimeException(
                    LogUtil.message("No run as user specified for filter uuid: {}", filter.getUuid()));
        }
        return filter.getRunAsUser();
    }

    private void exec(final TaskContext taskContext, final Run run) {
        run.started = true;
        LOGGER.trace(() -> "Executing processor task: " + run.task.getId());
        try {
            process(taskContext, run);
        } catch (final Fault e) {
            run.fault = e;
        } catch (final IOException | RuntimeException e) {
            run.fault = new Fault("Unexpected error", e, true);
        } catch (final Error e) {
            run.fault = new Fault("Unexpected error", e, true);
            throw e;
        } finally {
            end(run, taskContext.getTerminationCause().or(() -> run.stopCause));
        }
    }

    private void process(final TaskContext taskContext, final Run run) throws IOException {
        // Open the stream source.
        try (final Source source = openSource(run.task)) {
            final Meta meta = source.getMeta();
            run.meta = meta;
            final ProcessorFilter processorFilter = loadFilter(run.task);
            final Processor processor = processorFilter.getProcessor();

            log(taskContext, meta, processor);

            // Don't process any streams that we have already created
            if (meta.getProcessorUuid() != null && meta.getProcessorUuid().equals(processor.getUuid())) {
                LOGGER.warn("Skipping data that we seem to have created (avoid processing forever) {} {}",
                        meta, processor);

            } else if (taskContext.isTerminated()) {
                LOGGER.debug(() -> "Terminated before starting to process task: " + run.task.getId());

            } else if (startProcessing(run)) {
                // Avoid having to do another fetch
                run.task = run.task.copy().processorFilter(processorFilter).build();

                if (isReadyToRun(taskContext, run)) {
                    run.processor = processor;
                    final ProcessorTaskExecutor processorTaskExecutor =
                            executorProviders.get(processor.getProcessorType()).get();
                    try {
                        run.result = processorTaskExecutor
                                .exec(taskContext, processor, processorFilter, run.task, source, run.output,
                                        run.indexing);
                    } catch (final ErrorStreamException e) {
                        // Almost always because of where it is written, e.g. a full volume, so every task would hit it.
                        throw new Fault("Unable to write the error stream", e.getCause(), false);
                    } catch (final RuntimeException e) {
                        throw new Fault("Processing failed", e, true);
                    }
                }
            }
        }
    }

    private Source openSource(final ProcessorTask task) {
        Source source = null;
        RuntimeException cause = null;
        try {
            source = streamStore.openSource(task.getMetaId());
        } catch (final RuntimeException e) {
            cause = e;
        }
        if (source == null) {
            // Not counted, as it is almost always because of where the stream is stored, e.g. a volume or S3 store
            // that can't be reached, so every task would hit it. A stream that has been deleted is completed instead.
            throw new Fault("Unable to open source stream " + task.getMetaId(), cause, false);
        }
        return source;
    }

    private ProcessorFilter loadFilter(final ProcessorTask task) {
        final int filterId = task.getProcessorFilter().getId();
        ProcessorFilter processorFilter = null;
        RuntimeException cause = null;
        try {
            processorFilter = processorFilterCache.get(filterId).orElse(null);
        } catch (final RuntimeException e) {
            cause = e;
        }
        if (processorFilter == null || processorFilter.getProcessor() == null) {
            throw new Fault("Unable to load processor filter " + filterId + " and its processor", cause, false);
        }
        return processorFilter;
    }

    // Returns false if the task could not be started, so is no business of this run's.
    private boolean startProcessing(final Run run) {
        if (!run.alreadyClaimed) {
            final TaskStatusChange change = changeTaskStatus(
                    run.task, nodeInfo.getThisNodeName(), TaskStatus.PROCESSING, run.startTime, null, null, false);
            if (!change.isDone()) {
                LOGGER.debug(() -> LogUtil.message(
                        "Not processing task {} as its status could not be changed ({}). It may have been " +
                        "logically/physically deleted.", run.task.getId(), change.result()));
                run.notStarted = true;
                if (TaskStatusChange.Result.UNKNOWN.equals(change.result())) {
                    putBackUnstarted(run.task);
                }
                return false;
            }
            run.task = change.task();
        }
        return true;
    }

    // If this fails too then the master puts it back if it is still queued, or the reaper if it was started.
    private void putBackUnstarted(final ProcessorTask task) {
        try {
            final boolean putBack = securityContext.asProcessingUserResult(() ->
                    processorTaskDao.putBackUnstarted(task, nodeInfo.getThisNodeName()));
            LOGGER.info(() -> LogUtil.message("{} processor task {} as it could not be told whether it had started",
                    putBack
                            ? "Put back"
                            : "Unable to put back",
                    task.getId()));
        } catch (final RuntimeException e) {
            LOGGER.error(() -> LogUtil.message("Unable to put back processor task {} - {}",
                    task.getId(), LogUtil.exceptionMessage(e)), e);
        }
    }

    // Returns false if the run has to end without doing anything. What it has been given says why.
    private boolean isReadyToRun(final TaskContext taskContext, final Run run) {
        // gh-5699 Heartbeat while we hold this task in PROCESSING; the stroom task id lets the run be ended if the
        // heartbeat cannot renew or finds that the task has been lost. A claimed task is already registered, without
        // an id because it had not started.
        run.stopCause = processorTaskHeartbeat.registerStart(
                run.task, run.task.getProcessorFilter().getId(), taskContext.getTaskId());
        run.registered = run.task;
        if (run.stopCause.isPresent()) {
            return false;
        }

        if (run.task.getRunCount() > 1) {
            // Ending another task needs permission that the user the filter runs as needn't have.
            final boolean alone = securityContext.asProcessingUserResult(() -> processorTaskHeartbeat.awaitEarlierRuns(
                    run.task, EARLIER_RUN_TIMEOUT, taskContext::isTerminated));
            if (!alone) {
                run.earlierRunGoing = true;
                return false;
            }
            if (!clearEarlierRuns(run)) {
                run.lost = true;
                return false;
            }
            beforeRerun(run);
        }
        return true;
    }

    // Lets the listeners, e.g. an index, get ready for the task to be run again, before anything of the run is open.
    private void beforeRerun(final Run run) {
        for (final ProcessorTaskListener listener : listeners) {
            try {
                securityContext.asProcessingUser(() -> listener.beforeRerun(run.task));
            } catch (final RuntimeException e) {
                // It has nothing to do with the stream.
                throw new Fault("Unable to get ready for the task to be run again", e, false);
            }
        }
    }

    // Returns false if the run has lost its task.
    private boolean clearEarlierRuns(final Run run) {
        try {
            return securityContext.asProcessingUserResult(() ->
                    processorTaskOutputCleaner.clearEarlierRuns(run.task, () -> isHeld(run.task)));
        } catch (final RuntimeException e) {
            // To carry on would leave duplicates.
            throw new Fault("Unable to clear the output of earlier runs", e, false);
        }
    }

    private boolean isHeld(final ProcessorTask task) {
        return Objects.equals(
                task.getVersion(),
                processorTaskDao.findProcessingVersions(nodeInfo.getThisNodeName(), List.of(task.getId()))
                        .get(task.getId()));
    }

    private void end(final Run run, final Optional<TerminationCause> terminationCause) {
        if (run.notStarted) {
            // The task isn't ours.
            return;
        }
        // So that this isn't taken for a run that has lost its task once it has changed the status.
        if (run.registered != null) {
            processorTaskHeartbeat.registerEnding(run.registered);
        }

        try {
            final Ending ending = decideEnding(run, terminationCause);
            final TaskStatusChange change = setFinalStatus(run, ending);
            // An interrupt must not stop these, so is put off until they have finished.
            final boolean interrupted = Thread.interrupted();
            try {
                try {
                    tellListeners(run, change);
                } finally {
                    // Even if a listener threw an error.
                    clearUp(run, ending, change);
                }
            } finally {
                if (interrupted) {
                    // Continue to interrupt this thread.
                    Thread.currentThread().interrupt();
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(() -> LogUtil.message("Unable to end the run of processor task {} - {}",
                    run.task.getId(), LogUtil.exceptionMessage(e)), e);
        }
    }

    // Tells the listeners how the run ended. If it isn't known whether the status was set then they aren't told, and
    // have to find out for themselves.
    private void tellListeners(final Run run, final TaskStatusChange change) {
        switch (change.result()) {
            case DONE -> {
                final ProcessorTask task = change.task();
                if (TaskStatus.COMMITTING.equals(task.getStatus())) {
                    tellListeners(task, "held", listener -> listener.held(task));
                } else {
                    tellListeners(task, "ended", listener -> listener.ended(task, task.getStatus()));
                }
            }
            case LOST -> tellListeners(run.task, "replaced", listener -> listener.replaced(run.task));
            case UNKNOWN -> LOGGER.debug(() -> LogUtil.message(
                    "Not telling listeners as the status may not have been set: {}", run.task.getId()));
        }
    }

    // A listener that can't be told is left to find out for itself, so nothing else is changed.
    private void tellListeners(final ProcessorTask task,
                               final String call,
                               final Consumer<ProcessorTaskListener> consumer) {
        for (final ProcessorTaskListener listener : listeners) {
            try {
                securityContext.asProcessingUser(() -> consumer.accept(listener));
            } catch (final RuntimeException e) {
                LOGGER.error(() -> LogUtil.message("Unable to tell {} that processor task {} was {} - {}",
                        listener.getClass().getSimpleName(), task.getId(), call, LogUtil.exceptionMessage(e)), e);
            }
        }
    }

    // Decides how a run ended. The first of these that applies decides it, so the order matters, e.g. a run that was
    // terminated because its node is stopping is likely to have hit a fault too, which must not be counted.
    private Ending decideEnding(final Run run, final Optional<TerminationCause> terminationCause) {
        run.fault = addIndexingFault(run.fault, run.indexing);
        if (run.lost) {
            return Ending.LOST;

        } else if (terminationCause.isPresent()) {
            final TerminationCause cause = terminationCause.get();
            return switch (cause.type()) {
                case NODE_STOPPING -> Ending.putBack(REASON_NODE_STOPPED);
                case HEARTBEAT_OVERDUE -> Ending.putBack(REASON_HEARTBEAT_OVERDUE);
                case WORK_LOST -> Ending.LOST;
                // A run that is terminated without being told why is treated as if a user had done it, so that
                // nothing can terminate a task by accident and have it run forever.
                case USER, NOT_GIVEN -> new Ending(
                        TaskStatus.ABORTED,
                        "Terminated by " + Objects.requireNonNullElse(cause.terminatedBy(), "a user"),
                        false);
            };

        } else if (run.earlierRunGoing) {
            // As this node would otherwise claim it again straight away. It isn't a fault of the task's.
            return new Ending(TaskStatus.RETRY, REASON_EARLIER_RUN, false);

        } else if (run.indexing.isDocumentsLost()) {
            // It is to be run again anyway, so it isn't charged with any fault that it hit.
            return Ending.putBack(REASON_DOCUMENTS_LOST);

        } else if (run.fault != null && !isSourceDeleted(run)) {
            final Fault fault = run.fault;
            if (fault.counted) {
                LOGGER.error(() -> LogUtil.message("Processor task {} hit a fault - {}",
                        run.task.getId(), fault.getReason()), fault);
            } else {
                // Every task may hit one of these, e.g. while a volume can't be reached, so they are only told of
                // once an hour, by the reaper, rather than for each task.
                LOGGER.debug(() -> LogUtil.message("Processor task {} hit a fault that isn't counted - {}",
                        run.task.getId(), fault.getReason()), fault);
            }
            final boolean abort = fault.counted && run.task.getFaultCount() + 1
                                                   >= processorConfigProvider.get().getMaxTaskFaults();
            return new Ending(
                    abort
                            ? TaskStatus.ABORTED
                            : TaskStatus.RETRY,
                    fault.getReason(),
                    fault.counted);

        } else if (run.indexing.isHeld()) {
            return new Ending(TaskStatus.COMMITTING, null, false);
        }
        return new Ending(TaskStatus.COMPLETE, null, false);
    }

    // A stream that could not be indexed is a fault. Once the index has said so, it decides whether the fault is
    // counted and what the reason is, as it knows whether the cause is in the stream, and any exception the pipeline
    // then threw is likely to be because of it. That exception is kept as the cause.
    private static Fault addIndexingFault(final Fault fault, final RunIndexing indexing) {
        final Optional<String> reason = indexing.getCouldNotIndexReason();
        if (reason.isEmpty()) {
            return fault;
        }
        return new Fault(
                reason.get(),
                fault == null
                        ? null
                        : fault.getCause(),
                indexing.isCouldNotIndexCounted());
    }

    private TaskStatusChange setFinalStatus(final Run run, final Ending ending) {
        if (ending.isLost()) {
            LOGGER.warn(() -> LogUtil.message("Run {} of processor task {} has lost its task",
                    run.task.getRunCount(), run.task.getId()));
            return TaskStatusChange.lost();

        } else if (TaskStatus.CREATED.equals(ending.status())) {
            LOGGER.info(() -> LogUtil.message("Putting back processor task {} - {}",
                    run.task.getId(), ending.reason()));
            return changeTaskStatus(run.task, null, TaskStatus.CREATED, null, null, ending.reason(), false);
        }
        return changeTaskStatus(
                run.task,
                nodeInfo.getThisNodeName(),
                ending.status(),
                run.startTime,
                System.currentTimeMillis(),
                ending.reason(),
                ending.countFault());
    }

    // Deletes output that must not be kept, now that the status has been set. If it isn't known whether it has been
    // then nothing is deleted, as the task may be complete. If it isn't then it will be run again, which clears up.
    private void clearUp(final Run run, final Ending ending, final TaskStatusChange change) {
        try {
            securityContext.asProcessingUser(() -> {
                switch (change.result()) {
                    case DONE -> {
                        switch (ending.status()) {
                            case CREATED -> processorTaskOutputCleaner.delete(run.output.getAllIds());
                            case RETRY -> processorTaskOutputCleaner.delete(run.output.getOutputIds());
                            case ABORTED -> {
                                // What was written is kept if this is because of the last fault.
                                if (!ending.countFault()) {
                                    processorTaskOutputCleaner.delete(run.output.getOutputIds());
                                }
                            }
                            case COMMITTING -> {
                                // The output it makes out of date is kept until it is complete, as it may yet be put
                                // back.
                            }
                            default -> {
                                if (run.processor != null) {
                                    processorTaskOutputCleaner.clearSuperseded(run.task, run.meta, run.processor);
                                }
                            }
                        }
                    }
                    case LOST -> processorTaskOutputCleaner.delete(run.output.getAllIds());
                    case UNKNOWN -> LOGGER.debug(() ->
                            "Not clearing up as the status may not have been set: " + run.task.getId());
                }
            });
        } catch (final RuntimeException e) {
            LOGGER.error(() -> LogUtil.message("Unable to clear up after processor task {} - {}",
                    run.task.getId(), LogUtil.exceptionMessage(e)), e);
        }
    }

    private TaskStatusChange changeTaskStatus(final ProcessorTask task,
                                              final String nodeName,
                                              final TaskStatus status,
                                              final Long startTime,
                                              final Long endTime,
                                              final String reason,
                                              final boolean countFault) {
        // The user the filter runs as needn't have any permission to change tasks.
        return securityContext.asProcessingUserResult(() -> processorTaskDao.changeTaskStatus(
                task, nodeName, status, startTime, endTime, reason, countFault));
    }

    // A run that hit a fault as its source has been deleted is allowed to complete normally.
    private boolean isSourceDeleted(final Run run) {
        try {
            final Meta meta = securityContext.asProcessingUserResult(() ->
                    metaService.getMeta(run.task.getMetaId(), true));
            if (meta == null || Status.DELETED.equals(meta.getStatus())) {
                LOGGER.debug(run.fault::getMessage, run.fault);
                return true;
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
        return false;
    }

    private void log(final TaskContext taskContext, final Meta meta, final Processor destStreamProcessor) {
        if (destStreamProcessor.getPipelineUuid() != null) {
            taskContext.info(() -> "meta_id=" +
                    meta.getId() +
                    ", created=" +
                    DateUtil.createNormalDateTimeString(meta.getCreateMs()) +
                    ", processor type=" +
                    destStreamProcessor.getProcessorType() +
                    ", pipeline uuid=" +
                    destStreamProcessor.getPipelineUuid());
        } else {
            taskContext.info(() -> "meta_id=" +
                    meta.getId() +
                    ", created=" +
                    DateUtil.createNormalDateTimeString(meta.getCreateMs()) +
                    ", processor type=" +
                    destStreamProcessor.getProcessorType());
        }
    }


    // --------------------------------------------------------------------------------


    private static class Run {

        private final boolean alreadyClaimed;
        private final long startTime = System.currentTimeMillis();

        // The task as this run last knew it.
        private ProcessorTask task;
        // The task as it was when the run was registered with the heartbeat, if it has been. Until it has been, the
        // run has the run count of the run before, which may still be registered.
        private ProcessorTask registered;
        // True once this is running as the filter's user and has a task context.
        private boolean started;
        // True if the task could not be changed to PROCESSING, so must be left alone.
        private boolean notStarted;
        // Why the run must not go on, if its heartbeat found that out before it started.
        private Optional<TerminationCause> stopCause = Optional.empty();
        // True if an earlier run of the task was still going on this node.
        private boolean earlierRunGoing;
        // True if the run found out for itself that it no longer has its task.
        private boolean lost;
        private Meta meta;
        // Only set if the processor has been run.
        private Processor processor;
        private final RunOutput output = new RunOutput();
        private final RunIndexing indexing = new RunIndexing();
        private Fault fault;
        private ProcessorResult result = new ProcessorResultImpl(0, 0, Collections.emptyMap());

        private Run(final ProcessorTask task, final boolean alreadyClaimed) {
            this.task = task;
            this.alreadyClaimed = alreadyClaimed;
            // The claim registered the run.
            this.registered = alreadyClaimed
                    ? task
                    : null;
        }
    }


    // --------------------------------------------------------------------------------


    // How a run ended, with no status if the run has lost its task.
    private record Ending(TaskStatus status, String reason, boolean countFault) {

        private static final Ending LOST = new Ending(null, null, false);

        private static Ending putBack(final String reason) {
            return new Ending(TaskStatus.CREATED, reason, false);
        }

        private boolean isLost() {
            return status == null;
        }
    }


    // --------------------------------------------------------------------------------


    // Something that stopped a run from running to its end, so the task has to be tried again.
    private static class Fault extends RuntimeException {

        // False if it has nothing to do with the stream being processed, so would be hit by every task of the filter.
        private final boolean counted;

        private Fault(final String message, final Throwable cause, final boolean counted) {
            super(message, cause);
            this.counted = counted;
        }

        private String getReason() {
            return getCause() == null
                    ? getMessage()
                    : getMessage() + " - " + LogUtil.exceptionMessage(getCause());
        }
    }
}
