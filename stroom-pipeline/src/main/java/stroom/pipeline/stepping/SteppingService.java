package stroom.pipeline.stepping;

import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

public class SteppingService {
    static final ThreadPool THREAD_POOL = new ThreadPoolImpl(
            "Stepping",
            5,
            0,
            Integer.MAX_VALUE);

    private final TaskContextFactory taskContextFactory;
    private final Provider<SteppingRequestHandler> steppingRequestHandlerProvider;
    private final ExecutorProvider executorProvider;

    @Inject
    public SteppingService(final TaskContextFactory taskContextFactory,
                           final Provider<SteppingRequestHandler> steppingRequestHandlerProvider,
                           final ExecutorProvider executorProvider) {
        this.taskContextFactory = taskContextFactory;
        this.steppingRequestHandlerProvider = steppingRequestHandlerProvider;
        this.executorProvider = executorProvider;
    }

    public SteppingResult step(final PipelineStepRequest request) {
        // Execute the stepping task.
        final Function<TaskContext, SteppingResult> function = taskContext -> {
            final SteppingRequestHandler steppingRequestHandler = steppingRequestHandlerProvider.get();
            return steppingRequestHandler.exec(taskContext, request);
        };
        final Supplier<SteppingResult> supplier = taskContextFactory.contextResult("Translation stepping", function);
        final Executor executor = executorProvider.get(THREAD_POOL);
        return CompletableFuture.supplyAsync(supplier, executor).join();
    }
}
