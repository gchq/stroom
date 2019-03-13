package stroom.processor.api;

import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;

public class DataProcessorBinder {
    private final MapBinder<TaskType, DataProcessorTaskExecutor> mapBinder;

    private DataProcessorBinder(final Binder binder) {
        mapBinder = MapBinder.newMapBinder(binder, TaskType.class, DataProcessorTaskExecutor.class);
    }

    public static DataProcessorBinder create(final Binder binder) {
        return new DataProcessorBinder(binder);
    }

    public <H extends DataProcessorTaskExecutor> DataProcessorBinder bind(final String name, final Class<H> handler) {
        mapBinder.addBinding(new TaskType(name)).to(handler);
        return this;
    }
}
