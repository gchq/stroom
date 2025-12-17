/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.analytics.shared.AnalyticProcessType;
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
import stroom.util.shared.UserDependency;
import stroom.util.shared.UserRef;
import stroom.util.shared.scheduler.Schedule;

import jakarta.inject.Provider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

abstract class AbstractScheduledQueryExecutor<T extends AbstractAnalyticRuleDoc>
        implements HasUserDependencies {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractScheduledQueryExecutor.class);

    private final ExecutorProvider executorProvider;
    private final Provider<AnalyticErrorWriter> analyticErrorWriterProvider;
    private final TaskContextFactory taskContextFactory;
    private final NodeInfo nodeInfo;
    private final SecurityContext securityContext;
    private final ExecutionScheduleDao executionScheduleDao;
    private final Provider<DocRefInfoService> docRefInfoServiceProvider;
    private final String processType;

    AbstractScheduledQueryExecutor(final ExecutorProvider executorProvider,
                                   final Provider<AnalyticErrorWriter> analyticErrorWriterProvider,
                                   final TaskContextFactory taskContextFactory,
                                   final NodeInfo nodeInfo,
                                   final SecurityContext securityContext,
                                   final ExecutionScheduleDao executionScheduleDao,
                                   final Provider<DocRefInfoService> docRefInfoServiceProvider,
                                   final String processType) {
        this.executorProvider = executorProvider;
        this.analyticErrorWriterProvider = analyticErrorWriterProvider;
        this.taskContextFactory = taskContextFactory;
        this.nodeInfo = nodeInfo;
        this.securityContext = securityContext;
        this.executionScheduleDao = executionScheduleDao;
        this.docRefInfoServiceProvider = docRefInfoServiceProvider;
        this.processType = processType;
    }

    public void exec() {
        final TaskContext taskContext = taskContextFactory.current();
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        try {
            info(() -> "Starting scheduled " + processType + " processing");

            // Load rules.
            final List<T> docs = loadScheduledRules();

            info(() -> "Processing " + LogUtil.namedCount("scheduled " + processType, NullSafe.size(docs)));
            final WorkQueue workQueue = new WorkQueue(executorProvider.get(), 1, 1);
            for (final T doc : docs) {
                final Runnable runnable = createRunnable(doc, taskContext);
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

            postExecuteTidyUp(docs);

            info(() ->
                    LogUtil.message("Finished scheduled {} processing in {}", processType, logExecutionTime));
        } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
            LOGGER.debug("Task terminated", e);
            LOGGER.debug(() ->
                    LogUtil.message("Scheduled {} processing terminated after {}", processType, logExecutionTime));
        } catch (final RuntimeException e) {
            LOGGER.error(() ->
                    LogUtil.message("Error during scheduled {} processing: {}", processType, e.getMessage()), e);
        }
    }

    /**
     * Called at the end of execution to perform any tidy up operations.
     *
     * @param analyticDocs The list of all known analyticDocs for this executor.
     */
    abstract void postExecuteTidyUp(final List<T> analyticDocs);

    private Runnable createRunnable(final T doc,
                                    final TaskContext parentTaskContext) {
        return () -> {
            if (!parentTaskContext.isTerminated()) {
                try {
                    execRule(doc, parentTaskContext);
                } catch (final RuntimeException e) {
                    LOGGER.error(() ->
                            LogUtil.message("Error executing {}: {}",
                                    processType,
                                    RuleUtil.getRuleIdentity(doc)), e);
                }
            }
        };
    }

    private void execRule(final T doc,
                          final TaskContext parentTaskContext) {
        final DocRef docRef = doc.asDocRef();

        // Load schedules for the rule. Do this as the processing user as permission is required to load associated
        // users.
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
                            success = executeIfScheduled(doc, parentTaskContext, executionSchedule);
                        }
                    }));
                } catch (final RuntimeException e) {
                    LOGGER.error(() ->
                            LogUtil.message("Error executing {}: {}",
                                    processType,
                                    RuleUtil.getRuleIdentity(doc)), e);
                }
            };
            workQueue.exec(runnable);
        }
        workQueue.join();
    }

    private boolean executeIfScheduled(final T doc,
                                       final TaskContext parentTaskContext,
                                       final ExecutionSchedule executionSchedule) {
        final Optional<ExecutionSchedule> optionalSchedule = executionScheduleDao
                .fetchScheduleById(executionSchedule.getId());
        if (optionalSchedule.isEmpty()) {
            return false;
        }
        final ExecutionSchedule schedule = optionalSchedule.get();
        if (!schedule.isEnabled()) {
            return false;
        }

        // Reload the rule in case it has changed since last executed.
        final DocRef docRef = doc.asDocRef();
        final T reloaded = load(docRef);

        if (reloaded == null || !AnalyticProcessType.SCHEDULED_QUERY.equals(reloaded.getAnalyticProcessType())) {
            return false;
        }

        return taskContextFactory.childContextResult(
                parentTaskContext,
                "Scheduled " + processType + ": " +
                RuleUtil.getRuleIdentity(reloaded),
                taskContext -> execute(
                        reloaded,
                        schedule,
                        taskContext)).get();
    }

    private boolean execute(final T doc,
                            final ExecutionSchedule executionSchedule,
                            final TaskContext taskContext) {
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
            final String errorFeedName = getErrorFeedName(doc);
            final AnalyticErrorWriter analyticErrorWriter = analyticErrorWriterProvider.get();
            return analyticErrorWriter.exec(
                    errorFeedName,
                    null,
                    taskContext,
                    (t) -> process(
                            doc,
                            trigger,
                            executionTime,
                            effectiveExecutionTime,
                            executionSchedule,
                            currentTracker));
        }
        return false;
    }

    abstract boolean process(final T doc,
                             final Trigger trigger,
                             final Instant executionTime,
                             final Instant effectiveExecutionTime,
                             final ExecutionSchedule executionSchedule,
                             final ExecutionTracker currentTracker);

    @Override
    public List<UserDependency> getUserDependencies(final UserRef userRef) {
        Objects.requireNonNull(userRef);

        if (!securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)
            && !securityContext.isCurrentUser(userRef)) {
            throw new PermissionException(
                    userRef,
                    "You do not have permission to view the " + processType + "s that have scheduled executors " +
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

    void addExecutionHistory(final ExecutionSchedule executionSchedule,
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

    private List<T> loadScheduledRules() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        info(() -> LogUtil.message("Loading {}s", processType));
        final List<T> list = new ArrayList<>();
        final List<T> rules = getRules();
        for (final T rule : rules) {
            if (AnalyticProcessType.SCHEDULED_QUERY.equals(rule.getAnalyticProcessType())) {
                list.add(rule);
            }
        }
        info(() -> LogUtil.message("Finished loading {}s in {}", processType, logExecutionTime));
        return list;
    }

    abstract T load(DocRef docRef);

    abstract List<T> getRules();

    abstract String getErrorFeedName(T doc);

    private void info(final Supplier<String> messageSupplier) {
        LOGGER.info(messageSupplier);
        taskContextFactory.current().info(messageSupplier);
    }


    // --------------------------------------------------------------------------------


    public record ExecutionResult(String status, String message) {

    }
}
