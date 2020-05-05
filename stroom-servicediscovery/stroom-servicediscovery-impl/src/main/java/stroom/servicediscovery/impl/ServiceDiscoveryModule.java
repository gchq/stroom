package stroom.servicediscovery.impl;

import stroom.servicediscovery.api.ServiceDiscoverer;
import stroom.util.guice.HasHealthCheckBinder;

import com.google.inject.AbstractModule;

public class ServiceDiscoveryModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ServiceDiscoverer.class).to(ServiceDiscovererImpl.class);

        HasHealthCheckBinder.create(binder())
                .bind(ServiceDiscoveryRegistrar.class)
                .bind(ServiceDiscovererImpl.class);
    }
}
