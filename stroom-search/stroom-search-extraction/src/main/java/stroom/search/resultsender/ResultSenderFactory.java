package stroom.search.resultsender;

import org.springframework.stereotype.Component;
import stroom.task.server.ExecutorProvider;
import stroom.task.server.TaskContext;
import stroom.task.server.ThreadPoolImpl;
import stroom.util.shared.ThreadPool;

import javax.inject.Inject;
import java.util.concurrent.Executor;

@Component
public class ResultSenderFactory {
    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl(
            "Search Result Sender",
            5,
            0,
            Integer.MAX_VALUE);

    private final ExecutorProvider executorProvider;
    private final TaskContext taskContext;

    @Inject
    ResultSenderFactory(final ExecutorProvider executorProvider,
                        final TaskContext taskContext) {
        this.executorProvider = executorProvider;
        this.taskContext = taskContext;
    }

    public ResultSender create() {
        final Executor executor = executorProvider.getExecutor(THREAD_POOL);
        return new ResultSenderImpl(executor, taskContext);
    }
}
