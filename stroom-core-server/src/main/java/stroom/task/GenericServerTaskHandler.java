package stroom.task;

import stroom.util.shared.VoidResult;
import stroom.task.TaskContext;

import javax.inject.Inject;

@TaskHandlerBean(task = GenericServerTask.class)
class GenericServerTaskHandler extends AbstractTaskHandler<GenericServerTask, VoidResult> {
    private final TaskContext taskContext;

    @Inject
    GenericServerTaskHandler(final TaskContext taskContext) {
        this.taskContext = taskContext;
    }

    @Override
    public VoidResult exec(final GenericServerTask task) {
        taskContext.info(task.getMessage());
        task.getRunnable().run();
        return VoidResult.INSTANCE;
    }
}
