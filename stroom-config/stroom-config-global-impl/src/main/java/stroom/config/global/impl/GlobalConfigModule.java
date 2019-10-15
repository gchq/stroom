package stroom.config.global.impl;

import com.google.inject.AbstractModule;
import io.dropwizard.lifecycle.Managed;
import stroom.config.global.shared.FetchGlobalConfigAction;
import stroom.config.global.shared.FindGlobalConfigAction;
import stroom.config.global.shared.ListGlobalConfigAction;
import stroom.config.global.shared.UpdateGlobalConfigAction;
import stroom.task.api.TaskHandlerBinder;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.HealthCheckBinder;

public class GlobalConfigModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(AppConfigMonitor.class).asEagerSingleton();

        TaskHandlerBinder.create(binder())
                .bind(FindGlobalConfigAction.class, FindGlobalConfigHandler.class)
                .bind(ListGlobalConfigAction.class, ListGlobalConfigHandler.class)
                .bind(FetchGlobalConfigAction.class, FetchGlobalConfigHandler.class)
                .bind(UpdateGlobalConfigAction.class, UpdateGlobalConfigHandler.class);

        HealthCheckBinder.create(binder())
                .bind(AppConfigMonitor.class);

        GuiceUtil.buildMultiBinder(binder(), Managed.class)
                .addBinding(AppConfigMonitor.class);
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
