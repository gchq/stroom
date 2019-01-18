package stroom.config.global.impl.db;

import com.google.inject.AbstractModule;
import stroom.config.global.api.FetchGlobalConfigAction;
import stroom.config.global.api.LoadGlobalConfigAction;
import stroom.config.global.api.SaveGlobalConfigAction;
import stroom.task.api.TaskHandlerBinder;
import stroom.util.lifecycle.jobmanagement.ScheduledJobsBinder;

public class GlobalConfigModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ConfigMapper.class).toProvider(ConfigMapper.ConfigMapperFactory.class);
        bind(ConfigInitialiser.class).asEagerSingleton();

        ScheduledJobsBinder.create(binder()).bind(GlobalConfigJobs.class);

        TaskHandlerBinder.create(binder())
                .bind(FetchGlobalConfigAction.class, FetchGlobalConfigHandler.class)
                .bind(LoadGlobalConfigAction.class, LoadGlobalConfigHandler.class)
                .bind(SaveGlobalConfigAction.class, SaveGlobalConfigHandler.class);
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
