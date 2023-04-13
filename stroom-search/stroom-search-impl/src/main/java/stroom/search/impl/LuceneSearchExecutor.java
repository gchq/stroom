package stroom.search.impl;

import stroom.node.api.NodeInfo;
import stroom.query.api.v2.SearchTaskProgress;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.SearchProcess;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.api.TaskTerminatedException;
import stroom.task.shared.TaskProgress;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Provider;

public class LuceneSearchExecutor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LuceneSearchExecutor.class);
    private static final String TASK_NAME = "AsyncSearchTask";

    private final NodeInfo nodeInfo;
    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final Provider<AsyncSearchTaskHandler> asyncSearchTaskHandlerProvider;
    private final TaskManager taskManager;

    @Inject
    LuceneSearchExecutor(final NodeInfo nodeInfo,
                         final Executor executor,
                         final TaskContextFactory taskContextFactory,
                         final Provider<AsyncSearchTaskHandler> asyncSearchTaskHandlerProvider,
                         final TaskManager taskManager) {
        this.nodeInfo = nodeInfo;
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.asyncSearchTaskHandlerProvider = asyncSearchTaskHandlerProvider;
        this.taskManager = taskManager;
    }

    public void start(final AsyncSearchTask asyncSearchTask,
                      final ResultStore resultStore) {
        // Tell the task where results will be collected.
        asyncSearchTask.setResultStore(resultStore);

        // Start asynchronous search execution.
        final Runnable runnable = taskContextFactory.context(TASK_NAME, taskContext -> {
            final AtomicBoolean destroyed = new AtomicBoolean();
            final AsyncSearchTaskHandler asyncSearchTaskHandler = asyncSearchTaskHandlerProvider.get();

            final SearchProcess searchProcess = new SearchProcess() {
                @Override
                public SearchTaskProgress getSearchTaskProgress() {
                    final TaskProgress taskProgress =
                            taskManager.getTaskProgress(taskContext);
                    if (taskProgress != null) {
                        return new SearchTaskProgress(
                                taskProgress.getTaskName(),
                                taskProgress.getTaskInfo(),
                                taskProgress.getUserName(),
                                taskProgress.getThreadName(),
                                taskProgress.getNodeName(),
                                taskProgress.getSubmitTimeMs(),
                                taskProgress.getTimeNowMs());
                    }
                    return null;
                }

                @Override
                public void onTerminate() {
                    destroyed.set(true);
                    asyncSearchTaskHandler.terminateTasks(asyncSearchTask, taskContext.getTaskId());
                }
            };

            // Set the search process.
            resultStore.setSearchProcess(searchProcess);

            // Don't begin execution if we have been asked to complete already.
            if (!destroyed.get()) {
                asyncSearchTaskHandler.exec(taskContext, asyncSearchTask);
            }
        });

        CompletableFuture
                .runAsync(runnable, executor)
                .whenComplete((result, t) -> {
                    if (t != null) {
                        while (t instanceof CompletionException) {
                            t = t.getCause();
                        }

                        // We can expect some tasks to throw a task terminated exception
                        // as they may be terminated before we even try to execute them.
                        if (!(t instanceof TaskTerminatedException)) {
                            LOGGER.error(t::getMessage, t);
                            resultStore.onFailure(nodeInfo.getThisNodeName(), t);
                            resultStore.signalComplete();
                            throw new RuntimeException(t.getMessage(), t);
                        }

                        resultStore.signalComplete();
                    }
                });
    }
}
