package stroom.pipeline.stepping;

import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.function.Function;
import java.util.function.Supplier;

public class SteppingService {
    private final TaskContextFactory taskContextFactory;
    private final Provider<SteppingRequestHandler> steppingRequestHandlerProvider;

    @Inject
    public SteppingService(final TaskContextFactory taskContextFactory,
                           final Provider<SteppingRequestHandler> steppingRequestHandlerProvider) {
        this.taskContextFactory = taskContextFactory;
        this.steppingRequestHandlerProvider = steppingRequestHandlerProvider;
    }

    public SteppingResult step(final PipelineStepRequest request) {
        // Make sure stepping can only happen on streams that are visible to
        // the user.
        // FIXME : Constrain available streams.
        // folderValidator.constrainCriteria(task.getCriteria());

        // Execute the stepping task.
        final Function<TaskContext, SteppingResult> function = taskContext -> {
            final SteppingRequestHandler steppingRequestHandler = steppingRequestHandlerProvider.get();
            return steppingRequestHandler.exec(taskContext, request);
        };
        final Supplier<SteppingResult> supplier = taskContextFactory.contextResult("Translation stepping", function);
        return supplier.get();
    }
}
