package stroom.search.elastic.search;

import stroom.query.common.v2.ResultStore;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskTerminatedException;
import stroom.task.api.TerminateHandlerFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Provider;

public class ElasticSearchExecutor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticSearchExecutor.class);
    private static final String TASK_NAME = "Elasticsearch Cluster Search";

    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final Provider<ElasticAsyncSearchTaskHandler> elasticAsyncSearchTaskHandlerProvider;

    @Inject
    ElasticSearchExecutor(final Executor executor,
                          final TaskContextFactory taskContextFactory,
                          final Provider<ElasticAsyncSearchTaskHandler> elasticAsyncSearchTaskHandlerProvider) {
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.elasticAsyncSearchTaskHandlerProvider = elasticAsyncSearchTaskHandlerProvider;
    }

    public void start(final ElasticAsyncSearchTask task,
                      final ResultStore resultStore) {
        // Start asynchronous search execution.
        final Runnable runnable = taskContextFactory.context(
                TASK_NAME,
                TerminateHandlerFactory.NOOP_FACTORY,
                taskContext -> {
                    final AtomicBoolean destroyed = new AtomicBoolean();
                    final ElasticAsyncSearchTaskHandler asyncSearchTaskHandler =
                            elasticAsyncSearchTaskHandlerProvider.get();

                    // Set the task terminator.
                    resultStore.setTerminateHandler(() -> {
                        destroyed.set(true);
                        asyncSearchTaskHandler.terminateTasks(task, taskContext.getTaskId());
                    });

                    // Don't begin execution if we have been asked to complete already.
                    if (!destroyed.get()) {
                        asyncSearchTaskHandler.search(taskContext, task, resultStore.getCoprocessors(), resultStore);
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
                            LOGGER.error(t.getMessage(), t);
                            resultStore.addError(t);
                            resultStore.signalComplete();
                            throw new RuntimeException(t.getMessage(), t);
                        }

                        resultStore.signalComplete();
                    }
                });
    }
}
