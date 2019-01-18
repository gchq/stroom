package stroom.task.api;

import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;
import stroom.task.shared.Task;

public class TaskHandlerBinder {
    private final MapBinder<TaskType, TaskHandler> mapBinder;

    private TaskHandlerBinder(final Binder binder) {
        mapBinder = MapBinder.newMapBinder(binder, TaskType.class, TaskHandler.class);
    }

    public static TaskHandlerBinder create(final Binder binder) {
        return new TaskHandlerBinder(binder);
    }

    public <T extends Task<?>, H extends TaskHandler<?, ?>> TaskHandlerBinder bind(final Class<T> task, final Class<H> handler) {
        mapBinder.addBinding(new TaskType(task)).to(handler);
        return this;
    }
}
