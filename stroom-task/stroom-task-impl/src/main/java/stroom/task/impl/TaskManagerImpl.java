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
import stroom.util.filter.FilterFieldMapper;
import stroom.util.filter.FilterFieldMappers;
import stroom.util.filter.QuickFilterPredicateFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.servlet.SessionIdProvider;
import stroom.util.shared.ResultPage;

import io.vavr.Tuple;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
                    LOGGER.info("shutdown() - Waiting for {} tasks to complete. {}", currentCount, taskRegistry.list().stream()
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

//    private Set<TaskContextImpl> getAncestorTaskSet(final TaskId parentTask) {
//        // Get the parent task thread if there is one.
//        final Set<TaskContextImpl> ancestorTaskSet = new HashSet<>();
//        TaskId ancestor = parentTask;
//        while (ancestor != null) {
//            TaskContextImpl ancestorTaskState = currentTasks.get(ancestor);
//            if (ancestorTaskState != null) {
//                ancestorTaskSet.add(ancestorTaskState);
//            }
//            ancestor = ancestor.getParentId();
//        }
//        return ancestorTaskSet;
//    }
//
//    private TaskId getParentTaskId(final TaskContext parentContext) {
//        if (parentContext != null) {
//            return parentContext.getTaskId();
//        }
//        return null;
//    }
//
//    private UserIdentity getUserIdentity(final TaskContext parentContext) {
//        if (parentContext != null) {
//            return ((TaskContextImpl) parentContext).getUserIdentity();
//        }
//        return securityContext.getUserIdentity();
//    }
//
//    <R> Supplier<R> wrap(final TaskContext parentContext, final String taskName, final Function<TaskContext, R> function) {
//        final LogExecutionTime logExecutionTime = new LogExecutionTime();
//        final TaskId parentTaskId = getParentTaskId(parentContext);
//        final TaskId taskId = TaskIdFactory.create(parentTaskId);
//        final UserIdentity userIdentity = getUserIdentity(parentContext);
//        final TaskContextImpl subTaskContext = new TaskContextImpl(taskId, taskName, userIdentity);
//
//        return () -> {
//            R result;
//
//            // Make sure this thread is not interrupted.
//            if (Thread.interrupted()) {
//                LOGGER.warn("This thread was previously interrupted");
//            }
//            // Do not execute the task if we are no longer supposed to be running.
//            if (stop.get()) {
//                throw new TaskTerminatedException(stop.get());
//            }
//            if (taskName == null) {
//                throw new IllegalStateException("All tasks must have a name");
//            }
//            if (userIdentity == null) {
//                throw new IllegalStateException("Null user identity: " + taskName);
//            }
//
//            // Get the parent task thread if there is one.
//            final Set<TaskContextImpl> ancestorTaskSet = getAncestorTaskSet(parentTaskId);
//
//            final Thread currentThread = Thread.currentThread();
//            final String oldThreadName = currentThread.getName();
//
//            currentThread.setName(oldThreadName + " - " + taskName);
//
//            subTaskContext.setThread(currentThread);
//
//            try {
//                // Let every ancestor know this descendant task is being executed.
//                ancestorTaskSet.forEach(ancestorTask -> ancestorTask.addChild(subTaskContext));
//
//                currentTasks.put(taskId, subTaskContext);
//                LOGGER.debug(() -> "execAsync()->exec() - " + taskName + " took " + logExecutionTime.toString());
//
//                if (stop.get() || currentThread.isInterrupted()) {
//                    throw new TaskTerminatedException(stop.get());
//                }
//
//                result = securityContext.asUserResult(userIdentity, () -> pipelineScopeRunnable.scopeResult(() -> {
//                    CurrentTaskContext.pushContext(subTaskContext);
//                    try {
//                        return LOGGER.logDurationIfDebugEnabled(() -> function.apply(subTaskContext), () -> taskName);
//                    } finally {
//                        CurrentTaskContext.popContext();
//                    }
//                }));
//
//            } catch (final Throwable t) {
//                try {
//                    if (t instanceof ThreadDeath || t instanceof TaskTerminatedException) {
//                        LOGGER.warn(() -> "exec() - Task killed! (" + taskName + ")");
//                        LOGGER.debug(() -> "exec() (" + taskName + ")", t);
//                    } else {
//                        LOGGER.error(() -> t.getMessage() + " (" + taskName + ")", t);
//                    }
//
//                } catch (final Throwable t2) {
//                    LOGGER.debug(t2::getMessage, t2);
//                }
//
//                throw t;
//
//            } finally {
//                currentTasks.remove(taskId);
//
//                // Let every ancestor know this descendant task has completed.
//                ancestorTaskSet.forEach(ancestorTask -> ancestorTask.removeChild(subTaskContext));
//
//                subTaskContext.setThread(null);
//                currentThread.setName(oldThreadName);
//            }
//
//            return result;
//        };
//    }

    ResultPage<TaskProgress> terminate(final FindTaskCriteria criteria, final boolean kill) {
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
                    if (kill || !taskContext.isTerminated()) {
                        if (criteria.isMatch(taskId, taskContext.getSessionId())) {
                            terminateList.add(taskContext);
                        }
                    }
                }

                // Now terminate the relevant tasks.
                doTerminated(kill, timeNowMs, taskProgressList, terminateList);
            }

            return ResultPage.createUnboundedList(taskProgressList);
        });
    }

    private void doTerminated(final boolean kill, final long timeNowMs, final List<TaskProgress> taskProgressList,
                              final List<TaskContextImpl> itemsToKill) {
        LOGGER.trace(() -> LogUtil.message("doTerminated() - itemsToKill.size() {}", itemsToKill.size()));

        for (final TaskContextImpl taskContext : itemsToKill) {
            final TaskId taskId = taskContext.getTaskId();
            // First try and terminate the task.
            if (!taskContext.isTerminated()) {
                LOGGER.trace(() -> LogUtil.message("terminating task {}", taskId));
                taskContext.terminate();
            }

            // If we are forced to kill then kill the associated thread.
            if (kill) {
                LOGGER.trace(() -> LogUtil.message("killing task {} on thread {}", taskId, taskContext.getThreadName()));
                taskContext.kill();
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
            // Always allow a user to see tasks for their own session if tasks for the current session have been requested.
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
        if (findTaskProgressCriteria != null ) {
            fuzzyMatchPredicate = QuickFilterPredicateFactory.createFuzzyMatchPredicate(
                    findTaskProgressCriteria.getNameFilter(),
                    FIELD_MAPPERS);
        } else {
            fuzzyMatchPredicate = taskProgress -> true;
        }

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
//        final List<TaskProgress> taskProgressList = buildDummyTaskDataForTesting(fuzzyMatchPredicate);
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
        taskProgress.setFilteredOut(!fuzzyMatchPredicate.test(taskProgress));

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
     */
    private List<TaskProgress> buildDummyTaskDataForTesting(final Predicate<TaskProgress> fuzzyMatchPredicate) {

        AtomicInteger id = new AtomicInteger(0);
        List<String> taskNames = List.of(
                "Red",
                "Blue",
                "Green",
                "Yellow",
                "Brown",
                "Pink",
                "Black");

        List<String> users = List.of(
                "joebloggs",
                "johndoe");

        Instant startTime = Instant.EPOCH;
        Instant now = Instant.now();

        return taskNames.stream()
                .flatMap(taskname -> {
                    TaskId grandparentTaskId = new TaskId(String.valueOf(id.incrementAndGet()), null);
                    TaskId parentTaskId = new TaskId(String.valueOf(id.incrementAndGet()), grandparentTaskId);
                    TaskId childTaskId = new TaskId(String.valueOf(id.incrementAndGet()), parentTaskId);
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

                    taskProgress.setFilteredOut(!fuzzyMatchPredicate.test(taskProgress));
                    return taskProgress;

                })
                .collect(Collectors.toList());
    }
}
