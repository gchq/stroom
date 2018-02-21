package stroom.task;

import stroom.util.shared.VoidResult;
import stroom.util.task.TaskMonitor;

import javax.inject.Inject;

@TaskHandlerBean(task = GenericServerTask.class)
class GenericServerTaskHandler extends AbstractTaskHandler<GenericServerTask, VoidResult> {
    private final TaskMonitor taskMonitor;

    @Inject
    GenericServerTaskHandler(final TaskMonitor taskMonitor) {
        this.taskMonitor = taskMonitor;
    }

    @Override
    public VoidResult exec(final GenericServerTask task) {
        taskMonitor.info(task.getMessage());
        task.getRunnable().run();
        return VoidResult.INSTANCE;
    }
}
