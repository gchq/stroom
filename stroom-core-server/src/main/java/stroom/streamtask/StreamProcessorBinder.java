package stroom.streamtask;

import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;

public class StreamProcessorBinder {
    private final MapBinder<TaskType, StreamProcessorTaskExecutor> mapBinder;

    private StreamProcessorBinder(final Binder binder) {
        mapBinder = MapBinder.newMapBinder(binder, TaskType.class, StreamProcessorTaskExecutor.class);
    }

    public static StreamProcessorBinder create(final Binder binder) {
        return new StreamProcessorBinder(binder);
    }

    public <H extends StreamProcessorTaskExecutor> StreamProcessorBinder bind(final String name, final Class<H> handler) {
        mapBinder.addBinding(new TaskType(name)).to(handler);
        return this;
    }
}
