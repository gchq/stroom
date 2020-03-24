package stroom.cluster.task.impl;

import stroom.cluster.task.api.ClusterDispatchAsyncHelper;
import stroom.cluster.task.api.ClusterTaskTerminator;
import stroom.cluster.task.api.TargetType;
import stroom.cluster.task.api.TerminateTaskClusterTask;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.shared.FindTaskCriteria;
import stroom.task.shared.TaskId;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class ClusterTaskTerminatorImpl implements ClusterTaskTerminator {
    private final Executor executor;
    private final Provider<TaskContext> taskContextProvider;
    private final SecurityContext securityContext;
    private final ClusterDispatchAsyncHelper dispatchHelper;

    @Inject
    ClusterTaskTerminatorImpl(final Executor executor,
                              final Provider<TaskContext> taskContextProvider,
                              final SecurityContext securityContext,
                              final ClusterDispatchAsyncHelper dispatchHelper) {
        this.executor = executor;
        this.taskContextProvider = taskContextProvider;
        this.securityContext = securityContext;
        this.dispatchHelper = dispatchHelper;
    }

    @Override
    public void terminate(final String searchName, final TaskId ancestorId, final String taskName) {
        // We have to wrap the cluster termination task in another task or
        // ClusterDispatchAsyncImpl
        // will not execute it if the parent task is terminated.
        final TaskContext taskContext = taskContextProvider.get();
        securityContext.asProcessingUser(() -> {
            Runnable runnable = () -> {
                taskContext.setName("Terminate: " + taskName);
                taskContext.info(() -> "Terminating cluster tasks");
                taskContext.info(() -> searchName + " - terminating child tasks");
                final FindTaskCriteria findTaskCriteria = new FindTaskCriteria();
                findTaskCriteria.addAncestorId(ancestorId);
                final TerminateTaskClusterTask terminateTask = new TerminateTaskClusterTask("Terminate: " + taskName, findTaskCriteria, false);

                // Terminate matching tasks.
                dispatchHelper.execAsync(terminateTask, TargetType.ACTIVE);
            };
            runnable = taskContext.subTask(runnable);
            CompletableFuture.runAsync(runnable, executor);
        });
    }
}
