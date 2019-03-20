package stroom.servicediscovery.impl;

import com.google.inject.AbstractModule;
import stroom.servicediscovery.api.ServiceDiscoverer;
import stroom.util.guice.HealthCheckBinder;

public class ServiceDiscoveryModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ServiceDiscoverer.class).to(ServiceDiscovererImpl.class);

        HealthCheckBinder.create(binder())
                .bind(ServiceDiscoveryRegistrar.class)
                .bind(ServiceDiscovererImpl.class);
    }
}
