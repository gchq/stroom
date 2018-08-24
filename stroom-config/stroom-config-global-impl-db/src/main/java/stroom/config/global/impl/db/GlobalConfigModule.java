package stroom.config.global.impl.db;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.task.api.TaskHandler;

public class GlobalConfigModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ConfigInitialiser.class).asEagerSingleton();

        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(FetchGlobalConfigHandler.class);
        taskHandlerBinder.addBinding().to(LoadGlobalConfigHandler.class);
        taskHandlerBinder.addBinding().to(SaveGlobalConfigHandler.class);
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
