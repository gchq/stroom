package stroom.task;

import stroom.security.Security;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskHandlerBean;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;

@TaskHandlerBean(task = GenericServerTask.class)
class GenericServerTaskHandler extends AbstractTaskHandler<GenericServerTask, VoidResult> {
    private final TaskContext taskContext;
    private final Security security;

    @Inject
    GenericServerTaskHandler(final TaskContext taskContext,
                             final Security security) {
        this.taskContext = taskContext;
        this.security = security;
    }

    @Override
    public VoidResult exec(final GenericServerTask task) {
        return security.secureResult(() -> {
            taskContext.info(task.getMessage());
            task.getRunnable().run();
            return VoidResult.INSTANCE;
        });
    }
}
