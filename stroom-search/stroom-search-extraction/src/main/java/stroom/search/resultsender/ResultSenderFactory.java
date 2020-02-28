package stroom.search.resultsender;

import org.springframework.stereotype.Component;
import stroom.util.concurrent.ExecutorProvider;
import stroom.task.server.TaskContext;
import stroom.util.concurrent.ThreadPoolImpl;
import stroom.util.shared.ThreadPool;
import stroom.util.task.TaskWrapper;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.concurrent.Executor;

@Component
public class ResultSenderFactory {
    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl(
            "Search Result Sender",
            5,
            0,
            Integer.MAX_VALUE);

    private final Executor executor;
    private final Provider<TaskWrapper> taskWrapperProvider;
    private final TaskContext taskContext;

    @Inject
    ResultSenderFactory(final ExecutorProvider executorProvider,
                        final Provider<TaskWrapper> taskWrapperProvider,
                        final TaskContext taskContext) {
        this.executor = executorProvider.getExecutor(THREAD_POOL);
        this.taskWrapperProvider = taskWrapperProvider;
        this.taskContext = taskContext;
    }

    public ResultSender create() {
        return new ResultSenderImpl(executor, taskWrapperProvider.get(), taskContext);
    }
}
