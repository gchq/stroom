package stroom.config.global.impl;

import stroom.config.global.impl.validation.ValidationModule;
import stroom.job.api.RunnableWrapper;
import stroom.job.api.ScheduledJobsBinder;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.HasHealthCheckBinder;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;
import io.dropwizard.lifecycle.Managed;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

public class GlobalConfigModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(AppConfigMonitor.class).asEagerSingleton();

        HasHealthCheckBinder.create(binder())
                .bind(AppConfigMonitor.class);

        GuiceUtil.buildMultiBinder(binder(), Managed.class)
                .addBinding(AppConfigMonitor.class);

        RestResourcesBinder.create(binder())
                .bind(GlobalConfigResourceImpl.class);

        install(new ValidationModule());

        ScheduledJobsBinder.create(binder())
                .bindJobTo(PropertyCacheReload.class, builder -> builder
                        .withName("Property Cache Reload")
                        .withDescription("Reload properties in the cluster")
                        .withSchedule(PERIODIC, "1m"));
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

    private static class PropertyCacheReload extends RunnableWrapper {
        @Inject
        PropertyCacheReload(final GlobalConfigService globalConfigService) {
            super(globalConfigService::updateConfigObjects);
        }
    }
}
