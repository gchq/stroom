package stroom.task.server;

import org.springframework.context.annotation.Scope;
import stroom.util.shared.VoidResult;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskMonitor;

import javax.inject.Inject;

@TaskHandlerBean(task = GenericServerTask.class)
@Scope(value = StroomScope.TASK)
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
