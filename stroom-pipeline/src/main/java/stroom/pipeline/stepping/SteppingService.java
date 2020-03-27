package stroom.pipeline.stepping;

import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.task.api.TaskContext;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.function.Supplier;

public class SteppingService {
    private final Provider<TaskContext> taskContextProvider;
    private final Provider<SteppingRequestHandler> steppingRequestHandlerProvider;

    @Inject
    public SteppingService(final Provider<TaskContext> taskContextProvider,
                           final Provider<SteppingRequestHandler> steppingRequestHandlerProvider) {
        this.taskContextProvider = taskContextProvider;
        this.steppingRequestHandlerProvider = steppingRequestHandlerProvider;
    }

    public SteppingResult step(final PipelineStepRequest request) {
        // Make sure stepping can only happen on streams that are visible to
        // the user.
        // FIXME : Constrain available streams.
        // folderValidator.constrainCriteria(task.getCriteria());

        // Execute the stepping task.
        final TaskContext taskContext = taskContextProvider.get();
        Supplier<SteppingResult> supplier = () -> {
            taskContext.setName("Translation stepping");
            final SteppingRequestHandler steppingRequestHandler = steppingRequestHandlerProvider.get();
            return steppingRequestHandler.exec(request);
        };
        supplier = taskContext.sub(supplier);
        return supplier.get();
    }
}
