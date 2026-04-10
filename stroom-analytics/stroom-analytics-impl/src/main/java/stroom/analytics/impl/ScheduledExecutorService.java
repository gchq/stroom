/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.analytics.impl;

import stroom.analytics.shared.ExecutionHistory;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionScheduleRequest;
import stroom.analytics.shared.ExecutionTracker;
import stroom.analytics.shared.ScheduleBounds;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskTerminatedException;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.concurrent.WorkQueue;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.LogUtil;
import stroom.util.scheduler.Trigger;
import stroom.util.scheduler.TriggerFactory;
import stroom.util.shared.HasUserDependencies;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Severity;
import stroom.util.shared.UserDependency;
import stroom.util.shared.UserRef;
import stroom.util.shared.scheduler.Schedule;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Service responsible for executing {@link ScheduledExecutable} instances based on
 * {@link stroom.analytics.shared.ExecutionSchedule}.
 *
 * <p>
 * This service coordinates:
 *     <ul>
 *         <li>Loading scheduled documents</li>
 *         <li>Evaluating schedules and execution bounds</li>
 *         <li>Running executions under the correct security context</li>
 *         <li>Recording execution history and tracker updates</li>
 *     </ul>
 * </p>
 *
 * <p>
 *     Execution is performed asynchronously using a {@link stroom.task.api.ExecutorProvider}
 *     but constrained to a single-threaded execution per document and per schedule to ensure
 *     deterministic behaviour.
 * </p>
 *
 * <p>
 *     The service also implements {@link stroom.util.shared.HasUserDependencies} to expose
 *     run-as user dependencies for user management and auditing.
 * </p>
 *
 * @param <T> The document type being scheduled and executed.
 */
@Singleton
public final class ScheduledExecutorService<T> implements HasUserDependencies {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ScheduledExecutorService.class);

    private final ExecutionScheduleDao executionScheduleDao;
    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final NodeInfo nodeInfo;
    private final SecurityContext securityContext;
    private final Provider<DocRefInfoService> docRefInfoServiceProvider;

    /**
     * Creates a new scheduled executor service.
     *
     * @param executorProvider          Provider for executor services.
     * @param taskContextFactory        Factory for creating task contexts.
     * @param nodeInfo                  Information about the current node.
     * @param securityContext           Security context used for permission checks and run-as execution.
     * @param executionScheduleDao      DAO for execution schedules, trackers, and history.
     * @param docRefInfoServiceProvider Provider for document reference decoration.
     */
    @Inject
    ScheduledExecutorService(final ExecutorProvider executorProvider,
                             final TaskContextFactory taskContextFactory,
                             final NodeInfo nodeInfo,
                             final SecurityContext securityContext,
                             final ExecutionScheduleDao executionScheduleDao,
                             final Provider<DocRefInfoService> docRefInfoServiceProvider) {
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.nodeInfo = nodeInfo;
        this.securityContext = securityContext;
        this.executionScheduleDao = executionScheduleDao;
        this.docRefInfoServiceProvider = docRefInfoServiceProvider;
    }

    /**
     * Executes all scheduled items for the supplied {@link ScheduledExecutable}
     *
     * <p>
     * This method:
     *     <ul>
     *         <li>Loads all candidate documents</li>
     *         <li>Processes each document sequentially</li>
     *         <li>Delegates execution to schedule-specific tasks</li>
     *         <li>Performs post-execution tidy-up</li>
     *     </ul>
     * </p>
     *
     * <p>
     *     Execution is resilient to individual document failures and will continue
     *     processing remaining documents where possible.
     * </p>
     *
     * @param scheduledExecutable The executable providing scheduling logic and execution behaviour.
     */
    public void exec(final ScheduledExecutable<T> scheduledExecutable) {
        final TaskContext taskContext = taskContextFactory.current();
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        try {
            info(() -> "Starting scheduled " + scheduledExecutable.getProcessType() + " processing");

            // Load scheduled items.
            final List<T> docs = loadScheduledItems(scheduledExecutable);

            info(() -> "Processing " + LogUtil.namedCount("scheduled " + scheduledExecutable.getProcessType(),
                    NullSafe.size(docs)));
            final WorkQueue workQueue = new WorkQueue(executorProvider.get(), 1, 1);
            for (final T doc : docs) {
                final Runnable runnable = createRunnable(doc, taskContext, scheduledExecutable);
                try {
                    workQueue.exec(runnable);
                } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
                    LOGGER.debug(e::getMessage, e);
                    throw e;
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                }
            }

            // Join.
            workQueue.join();

            scheduledExecutable.postExecuteTidyUp(docs);

            info(() ->
                    LogUtil.message("Finished scheduled {} processing in {}",
                            scheduledExecutable.getProcessType(),
                            logExecutionTime));
        } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
            LOGGER.debug("Task terminated", e);
            LOGGER.debug(() ->
                    LogUtil.message("Scheduled {} processing terminated after {}",
                            scheduledExecutable.getProcessType(),
                            logExecutionTime));
        } catch (final RuntimeException e) {
            LOGGER.error(() ->
                    LogUtil.message("Error during scheduled {} processing: {}",
                            scheduledExecutable.getProcessType(),
                            e.getMessage()), e);
        }
    }

    /**
     * Executes a single scheduled run for a document.
     * <p>
     * This method:
     *     <ul>
     *         <li>Invokes the executable's processing logic</li>
     *         <li>Calculates and persists the next execution time</li>
     *         <li>Updates execution trackers</li>
     *         <li>Disables schedules on unrecoverable errors</li>
     *         <li>Records execution history</li>
     *     </ul>
     * </p>
     *
     * @param doc                    The document being executed.
     * @param trigger                The trigger defining the schedule.
     * @param executionTime          The actual execution time.
     * @param effectiveExecutionTime The logical execution time for the schedule.
     * @param executionSchedule      The execution schedule.
     * @param currentTracker         The current execution tracker, if any.
     * @param scheduledExecutable    The executable implementation.
     */
    private void process(final T doc,
                         final Trigger trigger,
                         final Instant executionTime,
                         final Instant effectiveExecutionTime,
                         final ExecutionSchedule executionSchedule,
                         final ExecutionTracker currentTracker,
                         final ScheduledExecutable<T> scheduledExecutable) {
        LOGGER.debug(() -> LogUtil.message(
                "Executing: {} with executionTime: {}, effectiveExecutionTime: {}, currentTracker: {}",
                scheduledExecutable.getDocRef(doc).toShortString(),
                executionTime,
                effectiveExecutionTime,
                currentTracker));

        ExecutionResult executionResult = new ExecutionResult(null, null);

        try {
            executionResult = scheduledExecutable.run(doc,
                    trigger,
                    executionTime,
                    effectiveExecutionTime,
                    executionSchedule,
                    currentTracker,
                    executionResult);

            // Remember last successful execution time and compute next execution time.
            final Instant now = Instant.now();
            final Instant nextExecutionTime;
            if (executionSchedule.isContiguous()) {
                nextExecutionTime = trigger.getNextExecutionTimeAfter(effectiveExecutionTime);
            } else {
                nextExecutionTime = trigger.getNextExecutionTimeAfter(now);
            }

            // Update tracker.
            final ExecutionTracker executionTracker = new ExecutionTracker(
                    now.toEpochMilli(),
                    effectiveExecutionTime.toEpochMilli(),
                    nextExecutionTime.toEpochMilli());
            if (currentTracker != null) {
                executionScheduleDao.updateTracker(executionSchedule, executionTracker);
            } else {
                executionScheduleDao.createTracker(executionSchedule, executionTracker);
            }

            if (executionResult.status() == null) {
                executionResult = new ExecutionResult("Complete", executionResult.message());
            }

        } catch (final Exception e) {
            executionResult = new ExecutionResult("Error", e.getMessage());

            try {
                LOGGER.debug(e::getMessage, e);
                scheduledExecutable.log(Severity.ERROR, e.getMessage(), e);
            } catch (final RuntimeException e2) {
                LOGGER.error(e2::getMessage, e2);
            }

            // Disable future execution if the error was not an interrupted exception.
            if (!(e instanceof UncheckedInterruptedException)) {
                // Disable future execution.
                LOGGER.info(() -> LogUtil.message("Disabling: {}", scheduledExecutable.getIdentity(doc)));
                executionScheduleDao.updateExecutionSchedule(executionSchedule.copy().enabled(false).build());
            }

        } finally {
            // Record the execution.
            addExecutionHistory(executionSchedule,
                    executionTime,
                    effectiveExecutionTime,
                    executionResult);
        }
    }

    /**
     * Creates a runnable task for executing a scheduled document.
     *
     * @param doc                 The document to execute.
     * @param parentTaskContext   The parent task context.
     * @param scheduledExecutable The executable implementation.
     * @return A runnable that performs scheduled execution.
     */
    private Runnable createRunnable(final T doc,
                                    final TaskContext parentTaskContext,
                                    final ScheduledExecutable<T> scheduledExecutable) {
        return () -> {
            if (!parentTaskContext.isTerminated()) {
                try {
                    execScheduledItem(doc, parentTaskContext, scheduledExecutable);
                } catch (final RuntimeException e) {
                    LOGGER.error(() ->
                            LogUtil.message("Error executing {}: {}",
                                    scheduledExecutable.getProcessType(),
                                    scheduledExecutable.getIdentity(doc)), e);
                }
            }
        };
    }

    /**
     * Executes all schedules associated with a single document.
     *
     * <p>
     * Schedules are executed under their configured run-as user and
     * within isolated task contexts.
     * </p>
     *
     * @param doc                 The document being scheduled.
     * @param parentTaskContext   The parent task context.
     * @param scheduledExecutable The executable implementation.
     */
    private void execScheduledItem(final T doc,
                                   final TaskContext parentTaskContext,
                                   final ScheduledExecutable<T> scheduledExecutable) {
        final DocRef docRef = scheduledExecutable.getDocRef(doc);

        // Load schedules for the scheduled items. Do this as the processing user as permission is required to
        // load associated users.
        final ResultPage<ExecutionSchedule> executionSchedules = securityContext.asProcessingUserResult(() -> {
            final ExecutionScheduleRequest request = ExecutionScheduleRequest
                    .builder()
                    .ownerDocRef(docRef)
                    .enabled(true)
                    .nodeName(nodeInfo.getThisNodeName())
                    .build();
            return executionScheduleDao.fetchExecutionSchedule(request);
        });

        final WorkQueue workQueue = new WorkQueue(executorProvider.get(), 1, 1);
        for (final ExecutionSchedule executionSchedule : executionSchedules.getValues()) {
            final Runnable runnable = () -> {
                try {
                    // We need to set the user again here as it will have been lost from the parent context as we are
                    // running within a new thread.
                    LOGGER.debug(() -> LogUtil.message("DocRef: {}, running as user: {}",
                            docRef.toShortString(), executionSchedule.getRunAsUser()));

                    securityContext.asUser(executionSchedule.getRunAsUser(), () -> securityContext.useAsRead(() -> {
                        boolean success = true;
                        while (success && !parentTaskContext.isTerminated()) {
                            success = executeIfScheduled(doc,
                                    parentTaskContext,
                                    executionSchedule,
                                    scheduledExecutable);
                        }
                    }));
                } catch (final RuntimeException e) {
                    LOGGER.error(() ->
                            LogUtil.message("Error executing {}: {}",
                                    scheduledExecutable.getProcessType(),
                                    scheduledExecutable.getIdentity(doc)), e);
                }
            };
            workQueue.exec(runnable);
        }
        workQueue.join();
    }

    /**
     * Determines whether a schedule should execute and performs execution if required.
     *
     * @param doc                 The document being executed.
     * @param parentTaskContext   The parent task context.
     * @param executionSchedule   The execution schedule.
     * @param scheduledExecutable The executable implementation.
     * @return {@code true} if execution should continue, otherwise {@code false}.
     */
    private boolean executeIfScheduled(final T doc,
                                       final TaskContext parentTaskContext,
                                       final ExecutionSchedule executionSchedule,
                                       final ScheduledExecutable<T> scheduledExecutable) {
        final Optional<ExecutionSchedule> optionalSchedule = executionScheduleDao
                .fetchScheduleByUuid(executionSchedule.getUuid());
        if (optionalSchedule.isEmpty()) {
            return false;
        }
        final ExecutionSchedule schedule = optionalSchedule.get();
        if (!schedule.isEnabled()) {
            return false;
        }

        // Reload the scheduled item in case it has changed since last executed.
        final T reloaded = scheduledExecutable.reload(doc);

        if (reloaded == null || !scheduledExecutable.shouldRun(doc)) {
            return false;
        }

        return taskContextFactory.childContextResult(
                parentTaskContext,
                "Scheduled " + scheduledExecutable.getProcessType() + ": " +
                scheduledExecutable.getIdentity(reloaded),
                taskContext -> execute(
                        reloaded,
                        schedule,
                        taskContext,
                        scheduledExecutable)).get();
    }

    public void executeNow(final ExecutionSchedule executionSchedule,
                              final ScheduledExecutable<T> scheduledExecutable) {
        final WorkQueue workQueue = new WorkQueue(executorProvider.get(), 1, 1);
        final Runnable runnable = () -> {
            try {
                securityContext.asUser(executionSchedule.getRunAsUser(), () -> securityContext.useAsRead(() -> {
                    boolean success = true;
                    while (success) {
                        success = executeNowIfScheduled(executionSchedule,
                                scheduledExecutable);
                    }
                }));
            } catch (final RuntimeException e) {
                LOGGER.error(() ->
                        LogUtil.message("Error executing {}: {}",
                                scheduledExecutable.getProcessType()), e);
            }
        };
        workQueue.exec(runnable);
        workQueue.join();
    }


    /**
     * Determines whether a schedule should execute and performs execution if required.
     *
     * @param executionSchedule   The execution schedule.
     * @param scheduledExecutable The executable implementation.
     * @return {@code true} if execution should continue, otherwise {@code false}.
     */
    private boolean executeNowIfScheduled(final ExecutionSchedule executionSchedule,
                                          final ScheduledExecutable<T> scheduledExecutable) {
        final Optional<ExecutionSchedule> optionalSchedule = executionScheduleDao
                .fetchScheduleByUuid(executionSchedule.getUuid());
        if (optionalSchedule.isEmpty()) {
            return false;
        }
        final ExecutionSchedule schedule = optionalSchedule.get();
        if (!schedule.isEnabled()) {
            return false;
        }

        // Reload the scheduled item in case it has changed since last executed.
        final T reloaded = scheduledExecutable.load(executionSchedule.getOwningDoc());
        if (reloaded == null) {
            return false;
        }

        return taskContextFactory.contextResult(
                "Scheduled " + scheduledExecutable.getProcessType() + ": " +
                scheduledExecutable.getIdentity(reloaded),
                taskContext -> execute(
                        reloaded,
                        executionSchedule,
                        taskContext,
                        scheduledExecutable)).get();
    }

    /**
     * Executes a schedule within a child task context.
     *
     * @param doc                 The document being executed.
     * @param executionSchedule   The execution schedule.
     * @param taskContext         The task context.
     * @param scheduledExecutable The executable implementation.
     * @return {@code true} if execution should continue, otherwise {@code false}.
     */
    private boolean execute(final T doc,
                            final ExecutionSchedule executionSchedule,
                            final TaskContext taskContext,
                            final ScheduledExecutable<T> scheduledExecutable) {
        final ExecutionTracker currentTracker = executionScheduleDao.getTracker(executionSchedule).orElse(null);
        final Schedule schedule = executionSchedule.getSchedule();
        final ScheduleBounds scheduleBounds = executionSchedule.getScheduleBounds();

        // See if it is time to execute this query.
        final Instant executionTime = Instant.now();
        final Trigger trigger = TriggerFactory.create(schedule);

        final Instant effectiveExecutionTime;
        if (currentTracker != null) {
            effectiveExecutionTime = Instant.ofEpochMilli(currentTracker.getNextEffectiveExecutionTimeMs());
        } else {
            if (scheduleBounds != null && scheduleBounds.getStartTimeMs() != null) {
                effectiveExecutionTime = Instant.ofEpochMilli(scheduleBounds.getStartTimeMs());
            } else {
                effectiveExecutionTime = trigger.getNextExecutionTimeAfter(executionTime);
            }
        }

        // Calculate end bounds.
        Instant endTime = Instant.MAX;
        if (scheduleBounds != null && scheduleBounds.getEndTimeMs() != null) {
            endTime = Instant.ofEpochMilli(scheduleBounds.getEndTimeMs());
        }

        if (!effectiveExecutionTime.isAfter(executionTime) && !effectiveExecutionTime.isAfter(endTime)) {
            taskContext.info(() -> "Executing schedule '" +
                                   executionSchedule.getName() +
                                   "' with effective time: " +
                                   DateUtil.createNormalDateTimeString(effectiveExecutionTime.toEpochMilli()));

            scheduledExecutable.beforeProcess(doc,
                    trigger,
                    executionTime,
                    effectiveExecutionTime,
                    executionSchedule,
                    currentTracker,
                    taskContext,
                    (t) -> {
                        process(doc,
                                trigger,
                                executionTime,
                                effectiveExecutionTime,
                                executionSchedule,
                                currentTracker,
                                scheduledExecutable);
                        return doc;
                    });
            return true;
        }
        return false;
    }

    /**
     * Returns user dependencies for scheduled executors configured to run as the given user.
     *
     * <p>
     * Permission checks are enforced to ensure only authorised users may view dependencies.
     * </p>
     *
     * @param userRef The user to check dependencies for.
     * @return A list of user dependencies.
     * @throws stroom.util.shared.PermissionException if the caller lacks permission.
     */
    @Override
    public List<UserDependency> getUserDependencies(final UserRef userRef) {
        Objects.requireNonNull(userRef);

        if (!securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)
            && !securityContext.isCurrentUser(userRef)) {
            throw new PermissionException(
                    userRef,
                    "You do not have permission to view scheduled executors " +
                    "configured to run-as user "
                    + userRef.toInfoString());
        }

        final DocRefInfoService docRefInfoService = docRefInfoServiceProvider.get();
        return NullSafe.stream(executionScheduleDao.fetchSchedulesByRunAsUser(userRef.getUuid()))
                .map(executionSchedule -> {
                    DocRef owningDocRef = executionSchedule.getOwningDoc();
                    owningDocRef = docRefInfoService.decorate(owningDocRef);
                    final String details = LogUtil.message(
                            "{} '{}' has as a scheduled executor named '{}' " +
                            "with a run-as dependency.",
                            owningDocRef.getType(),
                            owningDocRef.getName(),
                            executionSchedule.getName());
                    return new UserDependency(userRef, details, executionSchedule.getOwningDoc());
                })
//                    .filter(userDependency ->
//                            NullSafe.getOrElse(
//                                    userDependency.getDocRef(),
//                                    docRef -> securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW),
//                                    true))
                .toList();
    }

    /**
     * Records execution history for a completed execution.
     *
     * @param executionSchedule      The execution schedule.
     * @param executionTime          The execution time.
     * @param effectiveExecutionTime The effective execution time.
     * @param executionResult        The execution result.
     */
    private void addExecutionHistory(final ExecutionSchedule executionSchedule,
                                     final Instant executionTime,
                                     final Instant effectiveExecutionTime,
                                     final ExecutionResult executionResult) {
        try {
            final ExecutionHistory executionHistory = ExecutionHistory
                    .builder()
                    .executionSchedule(executionSchedule)
                    .executionTimeMs(executionTime.toEpochMilli())
                    .effectiveExecutionTimeMs(effectiveExecutionTime.toEpochMilli())
                    .status(executionResult.status)
                    .message(executionResult.message)
                    .build();
            executionScheduleDao.addExecutionHistory(executionHistory);
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    /**
     * Loads all documents eligible for scheduled execution.
     *
     * @param scheduledExecutable The executable providing document access.
     * @return The list of scheduled documents.
     */
    private List<T> loadScheduledItems(final ScheduledExecutable<T> scheduledExecutable) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        info(() -> LogUtil.message("Loading {}s", scheduledExecutable.getProcessType()));
        final List<T> list = scheduledExecutable.getDocs();
        info(() -> LogUtil.message("Finished loading {}s in {}",
                scheduledExecutable.getProcessType(),
                logExecutionTime));
        return list;
    }

    /**
     * Logs an informational message to both the logger and the current task context.
     *
     * @param messageSupplier Supplier providing the log message.
     */
    private void info(final Supplier<String> messageSupplier) {
        LOGGER.info(messageSupplier);
        taskContextFactory.current().info(messageSupplier);
    }


    // --------------------------------------------------------------------------------


    /**
     * Represents the result of a single execution attempt.
     *
     * @param status  A short status string.
     * @param message Optional descriptive message.
     */
    public record ExecutionResult(String status, String message) {

        private static final ExecutionResult EMPTY = new ExecutionResult(null, null);

        public static final String STATUS_COMPLETE = ExecutionHistory.STATUS_COMPLETE;
        public static final String STATUS_ERROR = ExecutionHistory.STATUS_ERROR;

        public ExecutionResult {
            if (status != null) {
                if (!STATUS_COMPLETE.equals(status) && !STATUS_ERROR.equals(status)) {
                    throw new IllegalArgumentException("Invalid status: " + status);
                }
            }
            if (message != null && status == null) {
                throw new IllegalArgumentException(LogUtil.message(
                        "Can't have non-null message '{}' with no status", message));
            }
        }

        public static ExecutionResult empty() {
            return EMPTY;
        }

        public static ExecutionResult complete(final String message) {
            return new ExecutionResult(STATUS_COMPLETE, message);
        }

        public static ExecutionResult error(final String message) {
            return new ExecutionResult(STATUS_ERROR, message);
        }
    }
}
