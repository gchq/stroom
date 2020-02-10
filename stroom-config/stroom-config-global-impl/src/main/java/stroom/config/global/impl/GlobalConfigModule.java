package stroom.config.global.impl;

import com.google.inject.AbstractModule;
import io.dropwizard.lifecycle.Managed;
import stroom.config.global.impl.validation.ValidationModule;
import stroom.util.BuildInfoProvider;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.HealthCheckBinder;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.RestResource;

public class GlobalConfigModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(AppConfigMonitor.class).asEagerSingleton();
        bind(BuildInfo.class).toProvider(BuildInfoProvider.class);

        HealthCheckBinder.create(binder())
                .bind(AppConfigMonitor.class);

        GuiceUtil.buildMultiBinder(binder(), Managed.class)
                .addBinding(AppConfigMonitor.class);

        GuiceUtil.buildMultiBinder(binder(), RestResource.class)
                .addBinding(ConfigResourceImpl.class);

        install(new ValidationModule());
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
