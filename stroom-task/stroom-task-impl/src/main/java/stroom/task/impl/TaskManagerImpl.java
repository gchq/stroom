/*
 * Copyright 2017 Crown Copyright
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
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.task.api.TaskManager;
import stroom.task.shared.FindTaskCriteria;
import stroom.task.shared.FindTaskProgressCriteria;
import stroom.task.shared.TaskId;
import stroom.task.shared.TaskProgress;
import stroom.task.shared.TaskProgress.FilterMatchState;
import stroom.util.filter.FilterFieldMapper;
import stroom.util.filter.FilterFieldMappers;
import stroom.util.filter.QuickFilterPredicateFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.servlet.SessionIdProvider;
import stroom.util.shared.ResultPage;

import io.vavr.Tuple;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class TaskManagerImpl implements TaskManager {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TaskManagerImpl.class);

    public static final FilterFieldMappers<TaskProgress> FIELD_MAPPERS = FilterFieldMappers.of(
            FilterFieldMapper.of(FindTaskProgressCriteria.FIELD_DEF_NODE, TaskProgress::getNodeName),
            FilterFieldMapper.of(FindTaskProgressCriteria.FIELD_DEF_NAME, TaskProgress::getTaskName),
            FilterFieldMapper.of(FindTaskProgressCriteria.FIELD_DEF_USER, TaskProgress::getUserName),
            FilterFieldMapper.of(FindTaskProgressCriteria.FIELD_DEF_SUBMIT_TIME, (TaskProgress taskProgress) ->
                    Instant.ofEpochMilli(taskProgress.getSubmitTimeMs()).toString()),
            FilterFieldMapper.of(FindTaskProgressCriteria.FIELD_DEF_INFO, TaskProgress::getTaskInfo)
    );

    private final NodeInfo nodeInfo;
    private final SessionIdProvider sessionIdProvider;
    private final SecurityContext securityContext;
    private final ExecutorProviderImpl executorProvider;
    private final TaskContextFactoryImpl taskContextFactory;
    private final TaskRegistry taskRegistry;

    @Inject
    TaskManagerImpl(final NodeInfo nodeInfo,
                    final SessionIdProvider sessionIdProvider,
                    final SecurityContext securityContext,
                    final ExecutorProviderImpl executorProvider,
                    final TaskContextFactoryImpl taskContextFactory,
                    final TaskRegistry taskRegistry) {
        this.nodeInfo = nodeInfo;
        this.sessionIdProvider = sessionIdProvider;
        this.securityContext = securityContext;
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.taskRegistry = taskRegistry;

        // When we are running unit tests we need to make sure that all Stroom
        // threads complete and are shutdown between tests.
        ExternalShutdownController.addTerminateHandler(TaskManagerImpl.class, this::shutdown);
    }

    @Override
    public synchronized void startup() {
        LOGGER.info("startup()");
        taskContextFactory.setStop(false);
        executorProvider.setStop(false);
    }

    /**
     * Tells tasks to terminate and waits for all tasks to terminate before
     * cleaning up the executors.
     */
    @Override
    public synchronized void shutdown() {
        LOGGER.info("shutdown()");
        taskContextFactory.setStop(true);
        executorProvider.setStop(true);

        try {
            // Wait for all tasks to stop executing.
            boolean waiting = true;
            while (waiting) {
                // Stop all of the current tasks.
                taskRegistry.list().forEach(TaskContextImpl::terminate);

                final int currentCount = executorProvider.getCurrentTaskCount();
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
        taskContextFactory.setStop(false);
        LOGGER.info("shutdown() - Complete");
    }

    ResultPage<TaskProgress> terminate(final FindTaskCriteria criteria) {
        return securityContext.secureResult(PermissionNames.MANAGE_TASKS_PERMISSION, () -> {
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
            final TaskId taskId = taskContext.getTaskId();
            // Try and terminate the task.
            if (!taskContext.isTerminated()) {
                LOGGER.trace(() -> LogUtil.message("terminating task {}", taskId));
                taskContext.terminate();
            }
            final TaskProgress taskProgress = buildTaskProgress(timeNowMs, taskContext, taskId);
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
            return securityContext.secureResult(PermissionNames.MANAGE_TASKS_PERMISSION, () ->
                    doFind(findTaskProgressCriteria));
        }
    }

    private ResultPage<TaskProgress> doFind(final FindTaskProgressCriteria findTaskProgressCriteria) {
        LOGGER.debug("getTaskProgressMap()");
        // This can change a little between servers.
        final long timeNowMs = System.currentTimeMillis();

        final Predicate<TaskProgress> fuzzyMatchPredicate;
        if (findTaskProgressCriteria != null) {
            fuzzyMatchPredicate = QuickFilterPredicateFactory.createFuzzyMatchPredicate(
                    findTaskProgressCriteria.getNameFilter(),
                    FIELD_MAPPERS);
        } else {
            fuzzyMatchPredicate = taskProgress -> true;
        }

        // TODO @AT We can't sort server side as the UI is collating results from multiple nodes
        //  this means we would need to return the MatchInfo back to the client for it to use in its
        //  sort
        final List<TaskProgress> taskProgressList = taskRegistry.list().stream()
                .filter(taskContext -> {
                    // Only add this task progress if it matches the supplied criteria.
                    return (findTaskProgressCriteria == null ||
                            findTaskProgressCriteria.getSessionId() == null ||
                            findTaskProgressCriteria.getSessionId().equals(taskContext.getSessionId()));
                })
                .map(taskContext -> {
                    final TaskId taskId = taskContext.getTaskId();
                    return buildFilteredTaskProgress(timeNowMs, taskContext, taskId, fuzzyMatchPredicate);
                })
                .collect(Collectors.toList());

        // For DEV testing uncomment this line to send dummy data to UI so you have some thing to
        // look at in the UI.
//        taskProgressList.addAll(buildDummyTaskDataForTesting(fuzzyMatchPredicate));
//        if (LOGGER.isDebugEnabled()) {
//            LOGGER.debug("taskProgressList:\n" + AsciiTable.from(taskProgressList));
//        }

        return ResultPage.createUnboundedList(taskProgressList);
    }


    private TaskProgress buildTaskProgress(
            final long timeNowMs,
            final TaskContextImpl taskContext,
            final TaskId taskId) {

        final TaskProgress taskProgress = new TaskProgress();
        taskProgress.setId(taskId);
        taskProgress.setTaskName(taskContext.getName());
        taskProgress.setUserName(taskContext.getUserId());
        taskProgress.setThreadName(taskContext.getThreadName());
        taskProgress.setTaskInfo(taskContext.getInfo());
        taskProgress.setSubmitTimeMs(taskContext.getSubmitTimeMs());
        taskProgress.setTimeNowMs(timeNowMs);
        taskProgress.setNodeName(nodeInfo.getThisNodeName());
        return taskProgress;
    }

    private TaskProgress buildFilteredTaskProgress(
            final long timeNowMs,
            final TaskContextImpl taskContext,
            final TaskId taskId,
            final Predicate<TaskProgress> fuzzyMatchPredicate) {
        final TaskProgress taskProgress = buildTaskProgress(timeNowMs, taskContext, taskId);

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
        securityContext.secure(PermissionNames.MANAGE_TASKS_PERMISSION, () -> {
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
        if (serverTasks.length() > 0) {
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

        AtomicInteger id = new AtomicInteger(0);
        List<String> taskNames = List.of(
                "Red",
                "Blue",
                "Green",
                "Yellow",
                "Brown",
                "Pink",
                "Orange");

        List<String> users = List.of(
                "joebloggs",
                "johndoe");

        Instant startTime = Instant.EPOCH;
        Instant now = Instant.now();
        String nodeName = nodeInfo.getThisNodeName();

        return taskNames.stream()
                .flatMap(taskname -> {
                    // Need to make sure task IDs are unique over the cluster
                    TaskId grandparentTaskId = new TaskId(nodeName + "-" + id.incrementAndGet(), null);
                    TaskId parentTaskId = new TaskId(nodeName + "-" + id.incrementAndGet(),
                            grandparentTaskId);
                    TaskId childTaskId = new TaskId(nodeName + "-" + id.incrementAndGet(),
                            parentTaskId);
                    return Stream.of(
                            Tuple.of(taskname + "-grandparent", grandparentTaskId),
                            Tuple.of(taskname + "-parent", parentTaskId),
                            Tuple.of(taskname + "-child", childTaskId));
                })
                .map(tuple2 -> {
                    String taskName = tuple2._1();
                    TaskId taskId = tuple2._2();
                    final TaskProgress taskProgress = new TaskProgress();
                    taskProgress.setId(taskId);
                    taskProgress.setTaskName(taskName);
                    taskProgress.setUserName(users.get(id.get() % 2));
                    taskProgress.setThreadName("threadX");
                    taskProgress.setTaskInfo("taskInfo-" + taskName);
                    taskProgress.setSubmitTimeMs(startTime.plus(id.get() * 100, ChronoUnit.DAYS)
                            .toEpochMilli());
                    taskProgress.setTimeNowMs(now.toEpochMilli());
                    taskProgress.setNodeName(nodeInfo.getThisNodeName());

                    taskProgress.setFilterMatchState(
                            FilterMatchState.fromBoolean(fuzzyMatchPredicate.test(taskProgress)));
                    return taskProgress;

                })
                .collect(Collectors.toList());
    }
}
