package stroom.search.resultsender;

import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;

import javax.inject.Inject;
import java.util.concurrent.Executor;

public class ResultSenderFactory {
    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl(
            "Search Result Sender",
            5,
            0,
            Integer.MAX_VALUE);

    private final Executor executor;
    private final TaskContextFactory taskContextFactory;

    @Inject
    ResultSenderFactory(final ExecutorProvider executorProvider,
                        final TaskContextFactory taskContextFactory) {
        this.executor = executorProvider.get(THREAD_POOL);
        this.taskContextFactory = taskContextFactory;
    }

    public ResultSender create(final TaskContext parentContext) {
        return new ResultSenderImpl(executor, taskContextFactory, parentContext);
    }
}
