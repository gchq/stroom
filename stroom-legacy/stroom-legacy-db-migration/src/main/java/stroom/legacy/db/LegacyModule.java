package stroom.legacy.db;

import stroom.util.guice.HasHealthCheckBinder;

import com.google.inject.AbstractModule;

public class LegacyModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        HasHealthCheckBinder.create(binder())
                .bind(DbHealthCheck.class);
    }
}
