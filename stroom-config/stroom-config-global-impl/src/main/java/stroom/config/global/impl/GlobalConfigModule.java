package stroom.config.global.impl;

import stroom.config.global.impl.validation.ValidationModule;
import stroom.job.api.ScheduledJobsBinder;
import stroom.util.RunnableWrapper;
import stroom.util.config.AbstractFileChangeMonitor;
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
        bind(AbstractFileChangeMonitor.class).asEagerSingleton();

        HasHealthCheckBinder.create(binder())
                .bind(AbstractFileChangeMonitor.class);

        GuiceUtil.buildMultiBinder(binder(), Managed.class)
                .addBinding(AppConfigMonitor.class);

        RestResourcesBinder.create(binder())
                .bind(GlobalConfigResourceImpl.class);

        install(new ValidationModule());

        ScheduledJobsBinder.create(binder())
                .bindJobTo(PropertyCacheReload.class, builder -> builder
                        .name("Property Cache Reload")
                        .description("Reload properties in the cluster")
                        .schedule(PERIODIC, "1m"));
    }

    @SuppressWarnings("checkstyle:needbraces")
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
