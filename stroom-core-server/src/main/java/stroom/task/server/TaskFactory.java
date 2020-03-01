package stroom.task.server;

import com.google.common.base.Strings;
import event.logging.BaseAdvancedQueryItem;
import org.springframework.stereotype.Component;
import stroom.entity.server.CriteriaLoggingUtil;
import stroom.entity.shared.BaseResultList;
import stroom.node.server.NodeCache;
import stroom.security.SecurityContext;
import stroom.security.SecurityHelper;
import stroom.security.shared.UserIdentity;
import stroom.task.shared.FindTaskCriteria;
import stroom.task.shared.FindTaskProgressCriteria;
import stroom.task.shared.TaskProgress;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.Monitor;
import stroom.util.shared.Task;
import stroom.util.shared.TaskId;
import stroom.util.task.HasMonitor;
import stroom.util.task.MonitorInfoUtil;
import stroom.util.task.TaskScopeContextHolder;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Singleton
@Component
public class TaskFactory {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TaskManagerImpl.class);

    private final TaskHandlerBeanRegistry taskHandlerBeanRegistry;
    private final NodeCache nodeCache;
    private final SecurityContext securityContext;
    private final Provider<TaskMonitorImpl> taskMonitorProvider;

    private final Map<TaskId, TaskThread<?>> currentTasks = new ConcurrentHashMap<>(1024, 0.75F, 1024);
    private final AtomicBoolean stop = new AtomicBoolean();

    @Inject
    public TaskFactory(final TaskHandlerBeanRegistry taskHandlerBeanRegistry,
                       final NodeCache nodeCache,
                       final SecurityContext securityContext,
                       final Provider<TaskMonitorImpl> taskMonitorProvider) {
        this.taskHandlerBeanRegistry = taskHandlerBeanRegistry;
        this.nodeCache = nodeCache;
        this.securityContext = securityContext;
        this.taskMonitorProvider = taskMonitorProvider;
    }

    <R> Supplier<R> createSupplier(final Task<R> task) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        final SyncTaskCallback<R> callback = new SyncTaskCallback<>();

        final Supplier<R> supplier = () -> {
            // Get the task handler that will deal with this task.
            final TaskHandler<Task<R>, R> taskHandler = taskHandlerBeanRegistry.findHandler(task);
            taskHandler.exec(task, callback);

            if (callback.getThrowable() != null) {
                if (callback.getThrowable() instanceof RuntimeException) {
                    throw (RuntimeException) callback.getThrowable();
                }
                throw new RuntimeException(callback.getThrowable().getMessage(), callback.getThrowable());
            }

            return callback.getResult();
        };

        return wrapSupplier(task, supplier, logExecutionTime);
    }

    <R> Runnable createRunnable(final Task<R> task, TaskCallback<R> callback) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        final Supplier<Void> supplier = () -> {
            // Get the task handler that will deal with this task.
            final TaskHandler<Task<R>, R> taskHandler = taskHandlerBeanRegistry.findHandler(task);
            taskHandler.exec(task, callback);
            return null;
        };

        return () -> {
            try {
                final Supplier<Void> wrappedSupplier = wrapSupplier(task, supplier, logExecutionTime);
                wrappedSupplier.get();
            } catch (final Throwable t) {
                try {
                    callback.onFailure(t);
                } catch (final Throwable t2) {
                    LOGGER.debug(t2::getMessage, t2);
                }
            }
        };
    }

    <R> Supplier<R> wrapSupplier(final Task<?> task, final Supplier<R> supplier, final LogExecutionTime logExecutionTime) {
        return () -> {
            R result;

            // Do not execute the task if we are no longer supposed to be running.
            if (stop.get() || task.isTerminated()) {
                throw new TaskTerminatedException(stop.get());
            }
            if (task.getId() == null) {
                throw new IllegalStateException("All tasks must have a pre-allocated id");
            }
            final UserIdentity userIdentity = task.getUserIdentity();
            if (userIdentity == null) {
                LOGGER.debug(() -> "Task has null user token: " + task.getClass().getSimpleName());
            }

            final Thread currentThread = Thread.currentThread();
            final String oldThreadName = currentThread.getName();

            currentThread.setName(oldThreadName + " - " + task.getClass().getSimpleName());

            final TaskThread<R> taskThread = new TaskThread<>(task);
            taskThread.setThread(currentThread);

            TaskScopeContextHolder.addContext(task);
            try {
                currentTasks.put(task.getId(), taskThread);
                LOGGER.debug(() -> "execAsync()->exec() - " + task.getClass().getSimpleName() + " " + task.getTaskName() + " took " + logExecutionTime.toString());

                try (final SecurityHelper securityHelper = SecurityHelper.asUser(securityContext, userIdentity)) {
                    // Create a task monitor bean to be injected inside the handler.
                    final TaskMonitorImpl taskMonitor = taskMonitorProvider.get();
                    if (task instanceof HasMonitor) {
                        final HasMonitor hasMonitor = (HasMonitor) task;
                        taskMonitor.setMonitor(hasMonitor.getMonitor());
                    }

                    CurrentTaskState.pushState(task, taskMonitor);
                    try {
                        LOGGER.debug(() -> "doExec() - exec >> '" + task.getClass().getName() + "' " + task);
                        result = supplier.get();
                        LOGGER.debug(() -> "doExec() - exec << '" + task.getClass().getName() + "' " + task);

                    } finally {
                        CurrentTaskState.popState();
                    }
                }

            } catch (final Throwable t) {
                try {
                    if (t instanceof ThreadDeath || t instanceof TaskTerminatedException) {
                        LOGGER.warn(() -> "exec() - Task killed! (" + task.getClass().getSimpleName() + ")");
                        LOGGER.debug(() -> "exec() (" + task.getClass().getSimpleName() + ")", t);
                    } else {
                        LOGGER.error(() -> t.getMessage() + " (" + task.getClass().getSimpleName() + ")", t);
                    }

                } catch (final Throwable t2) {
                    LOGGER.debug(t2::getMessage, t2);
                }

                throw t;

            } finally {
                taskThread.setThread(null);
                currentTasks.remove(task.getId());
                TaskScopeContextHolder.removeContext();
                currentThread.setName(oldThreadName);
            }

            return result;
        };
    }

    void startup() {
        stop.set(false);
    }

    void shutdown() {
        stop.set(true);
        // Stop all of the current tasks.
        currentTasks.values().forEach(TaskThread::terminate);
    }

    boolean isStopping() {
        return stop.get();
    }

    String getRunningTasks() {
        return currentTasks.values().stream()
                .map(t -> t.getTask().getTaskName())
                .collect(Collectors.joining(", "));
    }

    BaseResultList<TaskProgress> terminate(final FindTaskCriteria criteria, final boolean kill) {
        // This can change a little between servers
        final long timeNowMs = System.currentTimeMillis();

        final List<TaskProgress> taskProgressList = new ArrayList<>();

        if (criteria != null && criteria.isConstrained()) {
            final Iterator<TaskThread<?>> iter = currentTasks.values().iterator();

            final List<TaskThread<?>> terminateList = new ArrayList<>();

            // Loop over all of the tasks that this node knows about and see if
            // it should be terminated.
            iter.forEachRemaining(taskThread -> {
                final Task<?> task = taskThread.getTask();

                // Terminate it?
                if (kill || !task.isTerminated()) {
                    if (criteria.isMatch(task)) {
                        terminateList.add(taskThread);
                    }
                }
            });

            // Now terminate the relevant tasks.
            doTerminated(kill, timeNowMs, taskProgressList, terminateList);
        }

        return BaseResultList.createUnboundedList(taskProgressList);
    }

    private void doTerminated(final boolean kill, final long timeNowMs, final List<TaskProgress> taskProgressList,
                              final List<TaskThread<?>> itemsToKill) {
        LOGGER.trace(() ->
                LambdaLogger.buildMessage("doTerminated() - itemsToKill.size() {}", itemsToKill.size()));

        for (final TaskThread<?> taskThread : itemsToKill) {
            final Task<?> task = taskThread.getTask();
            // First try and terminate the task.
            if (!task.isTerminated()) {
                LOGGER.trace(() -> "terminating task " + task);
                taskThread.terminate();
            }

            // If we are forced to kill then kill the associated thread.
            if (kill) {
                LOGGER.trace(() ->
                        LambdaLogger.buildMessage("killing task {} on thread {}", task, taskThread.getThreadName()));
                taskThread.kill();
            }
            final TaskProgress taskProgress = buildTaskProgress(timeNowMs, taskThread, taskThread.getTask());
            taskProgressList.add(taskProgress);
        }
    }

    public BaseResultList<TaskProgress> find(final FindTaskProgressCriteria findTaskProgressCriteria) {
        LOGGER.debug(() -> "getTaskProgressMap()");
        // This can change a little between servers.
        final long timeNowMs = System.currentTimeMillis();

        final List<TaskProgress> taskProgressList = new ArrayList<>();

        final Iterator<TaskThread<?>> iter = currentTasks.values().iterator();
        iter.forEachRemaining(taskThread -> {
            final Task<?> task = taskThread.getTask();

            // Only add this task progress if it matches the supplied criteria.
            if (findTaskProgressCriteria.isMatch(task)) {
                final TaskProgress taskProgress = buildTaskProgress(timeNowMs, taskThread, task);
                taskProgressList.add(taskProgress);
            }
        });

        return BaseResultList.createUnboundedList(taskProgressList);
    }

    private TaskProgress buildTaskProgress(final long timeNowMs, final TaskThread<?> taskThread, final Task<?> task) {
        final TaskProgress taskProgress = new TaskProgress();
        taskProgress.setId(task.getId());
        taskProgress.setTaskName(taskThread.getName());
        taskProgress.setUserName(task.getUserIdentity().getId());
        taskProgress.setThreadName(taskThread.getThreadName());
        taskProgress.setTaskInfo(taskThread.getInfo());
        taskProgress.setSubmitTimeMs(taskThread.getSubmitTimeMs());
        taskProgress.setTimeNowMs(timeNowMs);
        taskProgress.setNode(nodeCache.getDefaultNode());
        return taskProgress;
    }

    public Task<?> getTaskById(final TaskId taskId) {
        final TaskThread<?> taskThread = currentTasks.get(taskId);
        if (taskThread != null) {
            return taskThread.getTask();
        }
        return null;
    }

    @Override
    public String toString() {
        final StringBuilder nonServerTasksSb = new StringBuilder();
        final List<Monitor> monitorList = new ArrayList<>();
        final Iterator<TaskThread<?>> iter = currentTasks.values().iterator();
        iter.forEachRemaining(taskThread -> {
            final Task<?> task = taskThread.getTask();

            if (task instanceof HasMonitor) {
                final HasMonitor hasMonitor = (HasMonitor) task;
                monitorList.add(hasMonitor.getMonitor());
            } else {
                nonServerTasksSb.append(task.getTaskName());
                nonServerTasksSb.append(" ");
                nonServerTasksSb.append(task.getId().toString());
                nonServerTasksSb.append("\n");
            }
        });

        final String nonServerTasks = nonServerTasksSb.toString();
        final String serverTasks = MonitorInfoUtil.getInfo(monitorList);

        final StringBuilder sb = new StringBuilder();
        if (serverTasks.length() > 0) {
            sb.append("Server Tasks:\n");
            sb.append(serverTasks);
            sb.append("\n");
        }

        if (nonServerTasks.length() > 0) {
            sb.append("Non server Tasks:\n");
            sb.append(nonServerTasks);
            sb.append("\n");
        }

        return sb.toString();
    }

    public FindTaskProgressCriteria createCriteria() {
        return new FindTaskProgressCriteria();
    }

    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final FindTaskProgressCriteria criteria) {
        if (!Strings.isNullOrEmpty(criteria.getNameFilter())) {
            CriteriaLoggingUtil.appendStringTerm(items, "name", criteria.getNameFilter());
        }
    }

    private static class SyncTaskCallback<R> extends TaskCallbackAdaptor<R> {
        private R result;
        private Throwable throwable;

        @Override
        public void onSuccess(final R result) {
            this.result = result;
        }

        @Override
        public void onFailure(final Throwable throwable) {
            this.throwable = throwable;
        }

        public R getResult() {
            return result;
        }

        public Throwable getThrowable() {
            return throwable;
        }
    }
}
