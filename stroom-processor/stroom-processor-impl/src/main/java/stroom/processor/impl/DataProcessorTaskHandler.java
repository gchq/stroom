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
import stroom.processor.api.ProcessorResult;
import stroom.processor.api.ProcessorResultImpl;
import stroom.processor.api.ProcessorTaskExecutor;
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
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class DataProcessorTaskHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataProcessorTaskHandler.class);

    static final String REASON_NODE_STOPPED = "Put back as the node running it was stopped";
    static final String REASON_HEARTBEAT_OVERDUE = "Put back as the node running it could not renew its heartbeat";

    private final Map<ProcessorType, Provider<ProcessorTaskExecutor>> executorProviders;
    private final ProcessorFilterCache processorFilterCache;
    private final ProcessorTaskDao processorTaskDao;
    private final Store streamStore;
    private final MetaService metaService;
    private final NodeInfo nodeInfo;
    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;
    private final ProcessorTaskHeartbeat processorTaskHeartbeat;
    private final Provider<ProcessorConfig> processorConfigProvider;

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
                             final Provider<ProcessorConfig> processorConfigProvider) {
        this.executorProviders = executorProviders;
        this.processorFilterCache = processorFilterCache;
        this.processorTaskDao = processorTaskDao;
        this.streamStore = streamStore;
        this.metaService = metaService;
        this.nodeInfo = nodeInfo;
        this.securityContext = securityContext;
        this.taskContextFactory = taskContextFactory;
        this.processorTaskHeartbeat = processorTaskHeartbeat;
        this.processorConfigProvider = processorConfigProvider;
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
            setFinalStatus(run, Optional.empty());

        } catch (final Error e) {
            if (!run.started) {
                run.fault = new Fault("Unexpected error", e, true);
                setFinalStatus(run, Optional.empty());
            }
            throw e;

        } finally {
            // Unconditional so the registry cannot leak a task and heartbeat it forever, even if the status could not
            // be set. A no-op if we never registered (the task was never moved to PROCESSING by us).
            processorTaskHeartbeat.deregister(task.getId());
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
            setFinalStatus(run, taskContext.getTerminationCause());
        }
    }

    private void process(final TaskContext taskContext, final Run run) throws IOException {
        // Open the stream source.
        try (final Source source = openSource(run.task)) {
            final Meta meta = source.getMeta();
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
                // gh-5699 Heartbeat while we hold this task in PROCESSING; the stroom task
                // id lets self-fencing terminate this work if the heartbeat cannot renew. A
                // claimed task is already registered, without an id because it had not
                // started; registering again is what supplies the id.
                processorTaskHeartbeat.register(
                        run.task.getId(), processorFilter.getId(), taskContext.getTaskId());
                // Avoid having to do another fetch
                run.task = run.task.copy().processorFilter(processorFilter).build();

                final ProcessorTaskExecutor processorTaskExecutor =
                        executorProviders.get(processor.getProcessorType()).get();
                try {
                    run.result = processorTaskExecutor
                            .exec(taskContext, processor, processorFilter, run.task, source);
                } catch (final RuntimeException e) {
                    throw new Fault("Processing failed", e, true);
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
            throw new Fault("Unable to open source stream " + task.getMetaId(), cause, true);
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
                return false;
            }
            run.task = change.task();
        }
        return true;
    }

    // Sets how a run ended. The first of these that applies decides it, so the order matters, e.g. a run that was
    // terminated because its node is stopping is likely to have hit a fault too, which must not be counted.
    private void setFinalStatus(final Run run, final Optional<TerminationCause> terminationCause) {
        try {
            if (run.notStarted) {
                // The task isn't ours.
                return;
            }

            final String nodeName = nodeInfo.getThisNodeName();
            final long endTime = System.currentTimeMillis();

            if (terminationCause.isPresent()) {
                final TerminationCause cause = terminationCause.get();
                switch (cause.type()) {
                    case NODE_STOPPING -> putBack(run, REASON_NODE_STOPPED);
                    case HEARTBEAT_OVERDUE -> putBack(run, REASON_HEARTBEAT_OVERDUE);
                    // A run that is terminated without being told why is treated as if a user had done it, so that
                    // nothing can terminate a task by accident and have it run forever.
                    case USER, NOT_GIVEN -> changeTaskStatus(
                            run.task, nodeName, TaskStatus.ABORTED, run.startTime, endTime,
                            "Terminated by " + Objects.requireNonNullElse(cause.terminatedBy(), "a user"), false);
                }

            } else if (run.fault != null && !isSourceDeleted(run)) {
                final Fault fault = run.fault;
                LOGGER.error(() -> LogUtil.message("Processor task {} hit a fault - {}",
                        run.task.getId(), fault.getReason()), fault);
                final boolean abort = fault.counted && run.task.getFaultCount() + 1
                                                       >= processorConfigProvider.get().getMaxTaskFaults();
                changeTaskStatus(
                        run.task,
                        nodeName,
                        abort
                                ? TaskStatus.ABORTED
                                : TaskStatus.RETRY,
                        run.startTime,
                        endTime,
                        fault.getReason(),
                        fault.counted);

            } else {
                changeTaskStatus(
                        run.task, nodeName, TaskStatus.COMPLETE, run.startTime, endTime, null, false);
            }
        } catch (final RuntimeException e) {
            LOGGER.error(() -> LogUtil.message("Unable to set the final status of processor task {} - {}",
                    run.task.getId(), LogUtil.exceptionMessage(e)), e);
        }
    }

    private void putBack(final Run run, final String reason) {
        LOGGER.info(() -> LogUtil.message("Putting back processor task {} - {}", run.task.getId(), reason));
        changeTaskStatus(run.task, null, TaskStatus.CREATED, null, null, reason, false);
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
        // True once this is running as the filter's user and has a task context.
        private boolean started;
        // True if the task could not be changed to PROCESSING, so must be left alone.
        private boolean notStarted;
        private Fault fault;
        private ProcessorResult result = new ProcessorResultImpl(0, 0, Collections.emptyMap());

        private Run(final ProcessorTask task, final boolean alreadyClaimed) {
            this.task = task;
            this.alreadyClaimed = alreadyClaimed;
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
