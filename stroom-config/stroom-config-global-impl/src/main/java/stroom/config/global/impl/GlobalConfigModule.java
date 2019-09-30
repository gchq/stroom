package stroom.config.global.impl;

import com.google.inject.AbstractModule;
import stroom.config.global.api.FetchGlobalConfigAction;
import stroom.config.global.api.ListGlobalConfigAction;
import stroom.config.global.api.UpdateGlobalConfigAction;
import stroom.task.api.TaskHandlerBinder;

public class GlobalConfigModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(AppConfigMonitor.class).asEagerSingleton();
        bind(ConfigMapper.class).toProvider(ConfigMapper.ConfigMapperFactory.class);
        bind(ConfigInitialiser.class).asEagerSingleton();

        TaskHandlerBinder.create(binder())
                .bind(ListGlobalConfigAction.class, ListGlobalConfigHandler.class)
                .bind(FetchGlobalConfigAction.class, FetchGlobalConfigHandler.class)
                .bind(UpdateGlobalConfigAction.class, UpdateGlobalConfigHandler.class);
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
