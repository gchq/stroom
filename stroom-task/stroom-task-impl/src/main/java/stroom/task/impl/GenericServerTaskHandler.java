package stroom.task.impl;

import stroom.security.api.SecurityContext;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.GenericServerTask;
import stroom.task.api.TaskContext;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;


class GenericServerTaskHandler extends AbstractTaskHandler<GenericServerTask, VoidResult> {
    private final TaskContext taskContext;
    private final SecurityContext securityContext;

    @Inject
    GenericServerTaskHandler(final TaskContext taskContext,
                             final SecurityContext securityContext) {
        this.taskContext = taskContext;
        this.securityContext = securityContext;
    }

    @Override
    public VoidResult exec(final GenericServerTask task) {
        return securityContext.secureResult(() -> {
            taskContext.info(task.getMessage());
            task.getRunnable().run();
            return VoidResult.INSTANCE;
        });
    }
}
