package stroom.config.global.impl;

import stroom.job.api.ScheduledJobsBinder;
import stroom.util.RunnableWrapper;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.HasHealthCheckBinder;
import stroom.util.guice.HasSystemInfoBinder;
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

        HasSystemInfoBinder.create(binder())
                .bind(AppConfigSystemInfo.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(PropertyCacheReload.class, builder -> builder
                        .name("Property Cache Reload")
                        .description("Reload properties in the cluster")
                        .schedule(PERIODIC, "1m"));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        return o != null && getClass() == o.getClass();
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
