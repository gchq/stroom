package stroom.properties.global.impl.db;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.task.api.TaskHandler;

public class GlobalPropertiesModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ConfigInitialiser.class).asEagerSingleton();

        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(FetchGlobalPropertiesHandler.class);
        taskHandlerBinder.addBinding().to(LoadGlobalPropertyHandler.class);
        taskHandlerBinder.addBinding().to(SaveGlobalPropertyHandler.class);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
