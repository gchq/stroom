package stroom.processor.api;

import stroom.processor.shared.ProcessorType;

import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;

public class ProcessorTaskExecutorBinder {

    private final MapBinder<ProcessorType, ProcessorTaskExecutor> mapBinder;

    private ProcessorTaskExecutorBinder(final Binder binder) {
        mapBinder = MapBinder.newMapBinder(binder, ProcessorType.class, ProcessorTaskExecutor.class);
    }

    public static ProcessorTaskExecutorBinder create(final Binder binder) {
        return new ProcessorTaskExecutorBinder(binder);
    }

    public <H extends ProcessorTaskExecutor> ProcessorTaskExecutorBinder bind(final ProcessorType processorType,
                                                                              final Class<H> handler) {
        mapBinder.addBinding(processorType).to(handler);
        return this;
    }
}
