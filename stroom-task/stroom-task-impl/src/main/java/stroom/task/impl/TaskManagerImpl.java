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

package stroom.task.impl;

import stroom.node.api.NodeInfo;
import stroom.query.api.DateTimeSettings;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.FieldProviderImpl;
import stroom.query.common.v2.SimpleStringExpressionParser.FieldProvider;
import stroom.query.common.v2.ValueFunctionFactoriesImpl;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.shared.AppPermission;
import stroom.security.shared.HasUserRef;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskManager;
import stroom.task.shared.FindTaskCriteria;
import stroom.task.shared.FindTaskProgressCriteria;
import stroom.task.shared.TaskId;
import stroom.task.shared.TaskProgress;
import stroom.task.shared.TaskProgress.FilterMatchState;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.servlet.SessionIdProvider;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import io.vavr.Tuple;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
class TaskManagerImpl implements TaskManager {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TaskManagerImpl.class);

    private static final ValueFunctionFactoriesImpl<TaskProgress> VALUE_FUNCTION_FACTORIES =
            new ValueFunctionFactoriesImpl<TaskProgress>()
                    .put(FindTaskProgressCriteria.FIELD_DEF_NODE, TaskProgress::getNodeName)
                    .put(FindTaskProgressCriteria.FIELD_DEF_NAME, TaskProgress::getTaskName)
                    .put(FindTaskProgressCriteria.FIELD_DEF_USER, (TaskProgress taskProgress) ->
                            NullSafe.get(taskProgress, TaskProgress::getUserRef, UserRef::getDisplayName))
                    .put(FindTaskProgressCriteria.FIELD_DEF_SUBMIT_TIME, (TaskProgress taskProgress) ->
                            Instant.ofEpochMilli(taskProgress.getSubmitTimeMs()).toString())
                    .put(FindTaskProgressCriteria.FIELD_DEF_INFO, TaskProgress::getTaskInfo);
    private static final FieldProvider FIELD_PROVIDER =
            new FieldProviderImpl(FindTaskProgressCriteria.FIELD_DEFINITIONS);


    private final SessionIdProvider sessionIdProvider;
    private final SecurityContext securityContext;
    private final ExecutorProviderImpl executorProvider;
    private final TaskRegistry taskRegistry;
    private final String thisNodeName;
    private final ExpressionPredicateFactory expressionPredicateFactory;

    @Inject
    TaskManagerImpl(final NodeInfo nodeInfo,
                    final SessionIdProvider sessionIdProvider,
                    final SecurityContext securityContext,
                    final ExecutorProviderImpl executorProvider,
                    final TaskRegistry taskRegistry,
                    final ExpressionPredicateFactory expressionPredicateFactory) {
        this.sessionIdProvider = sessionIdProvider;
        this.securityContext = securityContext;
        this.executorProvider = executorProvider;
        this.taskRegistry = taskRegistry;
        this.expressionPredicateFactory = expressionPredicateFactory;

        // Node name is from read-only config, so we can hold it as an instance variable
        // on this singleton
        this.thisNodeName = nodeInfo.getThisNodeName();

        // When we are running unit tests we need to make sure that all Stroom
        // threads complete and are shutdown between tests.
        ExternalShutdownController.addTerminateHandler(TaskManagerImpl.class, this::shutdown);
    }

    @Override
    public synchronized void startup() {
        LOGGER.info("startup()");
        executorProvider.setStop(false);
    }

    /**
     * Tells tasks to terminate and waits for all tasks to terminate before
     * cleaning up the executors.
     */
    @Override
    public synchronized void shutdown() {
        LOGGER.info("shutdown()");
        executorProvider.setStop(true);

        try {
            // Wait for all tasks to stop executing.
            boolean waiting = true;
            while (waiting) {
                // Stop all of the current tasks.
                taskRegistry.list().forEach(TaskContextImpl::terminate);

                final int currentCount = taskRegistry.list().size();
                waiting = currentCount > 0;
                if (waiting) {
                    // Output some debug to list the tasks that are executing
                    // and queued.
                    LOGGER.info("shutdown() - Waiting for {} tasks to complete. {}",
                            currentCount,
                            taskRegistry.list().stream()
                                    .map(TaskContextImpl::toString)
                                    .collect(Collectors.joining(", ")));

                    // Wait 1 second.
                    Thread.sleep(1000);
                }
            }

            // Shut down all executors.
            executorProvider.shutdownExecutors();
        } catch (final InterruptedException e) {
            LOGGER.error(e.getMessage(), e);

            // Continue to interrupt this thread.
            Thread.currentThread().interrupt();
        }

        executorProvider.setStop(false);
        LOGGER.info("shutdown() - Complete");
    }

    ResultPage<TaskProgress> terminate(final FindTaskCriteria criteria) {
        return securityContext.secureResult(AppPermission.MANAGE_TASKS_PERMISSION, () -> {
            // This can change a little between servers
            final long timeNowMs = System.currentTimeMillis();

            final List<TaskProgress> taskProgressList = new ArrayList<>();

            if (criteria != null && criteria.isConstrained()) {
                final List<TaskContextImpl> terminateList = new ArrayList<>();

                // Loop over all of the tasks that this node knows about and see if
                // it should be terminated.
                for (final TaskContextImpl taskContext : taskRegistry.list()) {
                    final TaskId taskId = taskContext.getTaskId();

                    // Terminate it?
                    if (!taskContext.isTerminated()) {
                        if (criteria.isMatch(taskId, taskContext.getSessionId())) {
                            terminateList.add(taskContext);
                        }
                    }
                }

                // Now terminate the relevant tasks.
                doTerminated(timeNowMs, taskProgressList, terminateList);
            }

            return ResultPage.createUnboundedList(taskProgressList);
        });
    }

    private void doTerminated(final long timeNowMs, final List<TaskProgress> taskProgressList,
                              final List<TaskContextImpl> itemsToKill) {
        LOGGER.trace(() -> LogUtil.message("doTerminated() - itemsToKill.size() {}", itemsToKill.size()));

        for (final TaskContextImpl taskContext : itemsToKill) {
            // Try and terminate the task.
            if (!taskContext.isTerminated()) {
                LOGGER.trace(() -> LogUtil.message("terminating task {}", taskContext.getTaskId()));
                taskContext.terminate();
            }
            final TaskProgress taskProgress = buildTaskProgress(timeNowMs, taskContext);
            taskProgressList.add(taskProgress);
        }
    }

    public ResultPage<TaskProgress> find(final FindTaskProgressCriteria findTaskProgressCriteria) {
        final boolean sessionMatch = findTaskProgressCriteria != null &&
                                     findTaskProgressCriteria.getSessionId() != null &&
                                     findTaskProgressCriteria.getSessionId().equals(sessionIdProvider.get());

        if (sessionMatch) {
            // Always allow a user to see tasks for their own session if tasks for the current session
            // have been requested.
            return doFind(findTaskProgressCriteria);
        } else {
            return securityContext.secureResult(AppPermission.MANAGE_TASKS_PERMISSION, () ->
                    doFind(findTaskProgressCriteria));
        }
    }

    private ResultPage<TaskProgress> doFind(final FindTaskProgressCriteria findTaskProgressCriteria) {
        LOGGER.debug("getTaskProgressMap() - {}", findTaskProgressCriteria);
        // This can change a little between servers.
        final long timeNowMs = System.currentTimeMillis();

        final Predicate<TaskProgress> fuzzyMatchPredicate;
        final String nameFilter = NullSafe.get(findTaskProgressCriteria, FindTaskProgressCriteria::getNameFilter);
        if (!NullSafe.isBlankString(nameFilter)) {
            LOGGER.debug("Using nameFilter: '{}'", nameFilter);
            fuzzyMatchPredicate = expressionPredicateFactory.create(
                    nameFilter,
                    FIELD_PROVIDER,
                    VALUE_FUNCTION_FACTORIES,
                    DateTimeSettings.builder().build());
        } else {
            LOGGER.debug("No nameFilter, match all");
            fuzzyMatchPredicate = taskProgress -> true;
        }

        final String criteriaSessionId = NullSafe.get(findTaskProgressCriteria,
                FindTaskProgressCriteria::getSessionId);
        // Only add this task progress if it matches the supplied criteria.
        final Predicate<TaskContextImpl> sessionIdPredicate;
        if (criteriaSessionId == null) {
            LOGGER.debug("No criteriaSessionId, match all");
            sessionIdPredicate = taskContext -> true;
        } else {
            LOGGER.debug("Match on criteriaSessionId: {}", criteriaSessionId);
            sessionIdPredicate = taskContext ->
                    Objects.equals(criteriaSessionId, taskContext.getSessionId());
        }

        // TODO @AT We can't sort server side as the UI is collating results from multiple nodes
        //  this means we would need to return the MatchInfo back to the client for it to use in its
        //  sort
        final List<TaskProgress> taskProgressList = taskRegistry.list()
                .stream()
                .filter(sessionIdPredicate)
                .map(taskContext ->
                        buildFilteredTaskProgress(timeNowMs, taskContext, fuzzyMatchPredicate))
                .toList();

        // For DEV testing uncomment this line to send dummy data to UI so you have some thing to
        // look at in the UI.
//        taskProgressList.addAll(buildDummyTaskDataForTesting(fuzzyMatchPredicate));
//        if (LOGGER.isDebugEnabled()) {
//            LOGGER.debug("taskProgressList:\n" + AsciiTable.from(taskProgressList));
//        }

        return ResultPage.createUnboundedList(taskProgressList);
    }

    @Override
    public TaskProgress getTaskProgress(final TaskContext taskContext) {
        if (taskContext instanceof final TaskContextImpl taskContextImpl) {
            return buildTaskProgress(System.currentTimeMillis(), taskContextImpl);
        }
        return null;
    }

    private TaskProgress buildTaskProgress(
            final long timeNowMs,
            final TaskContextImpl taskContext) {

        final UserRef userRef;
        final UserIdentity userIdentity = taskContext.getUserIdentity();
        if (userIdentity instanceof final HasUserRef hasUserRef) {
            userRef = hasUserRef.getUserRef();
        } else {
            userRef = UserRef.builder().subjectId(userIdentity.subjectId()).build();
        }

        final TaskProgress taskProgress = new TaskProgress();
        taskProgress.setId(taskContext.getTaskId());
        taskProgress.setTaskName(taskContext.getName());
        taskProgress.setUserRef(userRef);
        taskProgress.setThreadName(taskContext.getThreadName());
        taskProgress.setTaskInfo(taskContext.getInfo());
        taskProgress.setSubmitTimeMs(taskContext.getSubmitTimeMs());
        taskProgress.setTimeNowMs(timeNowMs);
        taskProgress.setNodeName(thisNodeName);
        return taskProgress;
    }

    private TaskProgress buildFilteredTaskProgress(
            final long timeNowMs,
            final TaskContextImpl taskContext,
            final Predicate<TaskProgress> fuzzyMatchPredicate) {
        final TaskProgress taskProgress = buildTaskProgress(timeNowMs, taskContext);

        // Because the UI needs to display the ancestors of filtered in tasks and the ancestor tasks
        // may exist on another node, we can't just remove filtered out tasks. We need to let the UI
        // aggregate all the tasks from each node and then remove tasks that are filtered out or
        // are an ancestor of a filtered in task.
        // Filter state is worked out server side as GWT can't use the matching code due to all the
        // regex involved.
        taskProgress.setFilterMatchState(FilterMatchState.fromBoolean(fuzzyMatchPredicate.test(taskProgress)));

        return taskProgress;
    }

    @Override
    public boolean isTerminated(final TaskId taskId) {
        final TaskContextImpl taskContext = taskRegistry.get(taskId);
        if (taskContext != null) {
            return taskContext.isTerminated();
        }
        return true;
    }

    @Override
    public void terminate(final TaskId taskId) {
        securityContext.secure(AppPermission.MANAGE_TASKS_PERMISSION, () -> {
            final TaskContextImpl taskContext = taskRegistry.get(taskId);
            if (taskContext != null) {
                taskContext.terminate();
            }
        });
    }

    @Override
    public String toString() {
        final String serverTasks = taskRegistry.toString();
        final StringBuilder sb = new StringBuilder();
        if (!serverTasks.isEmpty()) {
            sb.append("Server Tasks:\n");
            sb.append(serverTasks);
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * This code is here to help UI testing in dev when you have no tasks to look at in the UI.
     * It produces a load of made up tasks
     * Used in {@link TaskManagerImpl#doFind(FindTaskProgressCriteria)}
     */
    @SuppressWarnings("unused") // Used in commented out debugging code
    private List<TaskProgress> buildDummyTaskDataForTesting(final Predicate<TaskProgress> fuzzyMatchPredicate) {

        final AtomicInteger id = new AtomicInteger(10_000);
        final AtomicInteger timeDelta = new AtomicInteger(0);
        final List<String> taskNames = List.of(
                "Red",
                "Blue",
                "Green",
                "Yellow",
                "Brown",
                "Pink",
                "Orange");

        final List<String> users = List.of(
                "joebloggs",
                "johndoe");

        final Instant now = Instant.now();

        final List<TaskProgress> colourTasks = taskNames.stream()
                .flatMap(taskname -> {
                    // Need to make sure task IDs are unique over the cluster
                    final TaskId grandparentTaskId = new TaskId(thisNodeName + "-" + id.incrementAndGet(), null);
                    final TaskId parentTaskId = new TaskId(thisNodeName + "-" + id.incrementAndGet(),
                            grandparentTaskId);
                    final TaskId childTaskId = new TaskId(thisNodeName + "-" + id.incrementAndGet(),
                            parentTaskId);
                    return Stream.of(
                            Tuple.of(taskname + "-grandparent", grandparentTaskId),
                            Tuple.of(taskname + "-parent", parentTaskId),
                            Tuple.of(taskname + "-child", childTaskId));
                })
                .map(tuple2 -> {
                    final String taskName = tuple2._1();
                    final TaskId taskId = tuple2._2();
                    String taskInfo = "taskInfo-" + taskName;
                    // Make a long taskInfo so we can test cell wrapping
                    for (int i = 0; i < 3; i++) {
                        taskInfo = taskInfo + " " + taskInfo;
                    }
                    final TaskProgress taskProgress = new TaskProgress();
                    taskProgress.setId(taskId);
                    taskProgress.setTaskName(taskName);
                    taskProgress.setUserRef(UserRef.builder().subjectId(users.get(id.get() % 2)).build());
                    taskProgress.setThreadName("thread-" + (ThreadLocalRandom.current().nextInt(20) + 1));
                    taskProgress.setTaskInfo(taskInfo);
                    taskProgress.setSubmitTimeMs(now.minus(timeDelta.incrementAndGet(), ChronoUnit.DAYS)
                            .toEpochMilli());
                    taskProgress.setTimeNowMs(now.toEpochMilli());
                    taskProgress.setNodeName(thisNodeName);

                    taskProgress.setFilterMatchState(
                            FilterMatchState.fromBoolean(fuzzyMatchPredicate.test(taskProgress)));
                    return taskProgress;

                })
                .toList();

        // If a parent task has died for some reason that leaves an orphaned task so the UI
        // needs to be able to cope with those too.
        final TaskId missingParentTask = new TaskId(thisNodeName + "-" + id.incrementAndGet(), null);
        final TaskProgress orphanedTask = new TaskProgress(
                new TaskId(thisNodeName + "-" + id.incrementAndGet(), missingParentTask),
                "Orphaned-task",
                "taskInfo-Orphaned-task",
                UserRef.builder().subjectId("janedoe").build(),
                "threadY",
                thisNodeName,
                now.minus(timeDelta.get(), ChronoUnit.DAYS).toEpochMilli(),
                now.toEpochMilli(),
                null,
                null);

        return Stream.concat(colourTasks.stream(), Stream.of(orphanedTask))
                .toList();
    }
}
