package stroom.cluster.task.api;

import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;

public class ClusterTaskHandlerBinder {
    private final MapBinder<ClusterTaskType, ClusterTaskHandler> mapBinder;

    private ClusterTaskHandlerBinder(final Binder binder) {
        mapBinder = MapBinder.newMapBinder(binder, ClusterTaskType.class, ClusterTaskHandler.class);
    }

    public static ClusterTaskHandlerBinder create(final Binder binder) {
        return new ClusterTaskHandlerBinder(binder);
    }

    public <T extends ClusterTask<?>, H extends ClusterTaskHandler<?, ?>> ClusterTaskHandlerBinder bind(
            final Class<T> task,
            final Class<H> handler) {

        mapBinder.addBinding(new ClusterTaskType(task)).to(handler);
        return this;
    }
}
