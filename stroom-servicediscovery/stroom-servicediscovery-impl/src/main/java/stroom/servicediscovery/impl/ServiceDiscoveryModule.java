package stroom.servicediscovery.impl;

import stroom.lifecycle.api.LifecycleBinder;
import stroom.servicediscovery.api.ServiceDiscoverer;
import stroom.util.RunnableWrapper;
import stroom.util.guice.HasHealthCheckBinder;

import com.google.inject.AbstractModule;

import javax.inject.Inject;

public class ServiceDiscoveryModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ServiceDiscoverer.class).to(ServiceDiscovererImpl.class);

        HasHealthCheckBinder.create(binder())
                .bind(ServiceDiscoveryRegistrar.class)
                .bind(ServiceDiscovererImpl.class);

        LifecycleBinder.create(binder())
                .bindShutdownTaskTo(ServiceDiscovererShutdown.class)
                .bindShutdownTaskTo(ServiceDiscoveryManagerShutdown.class);
    }

    private static class ServiceDiscovererShutdown extends RunnableWrapper {
        @Inject
        ServiceDiscovererShutdown(final ServiceDiscovererImpl serviceDiscoverer) {
            super(serviceDiscoverer::shutdown);
        }
    }

    private static class ServiceDiscoveryManagerShutdown extends RunnableWrapper {
        @Inject
        ServiceDiscoveryManagerShutdown(final ServiceDiscoveryManager serviceDiscoveryManager) {
            super(serviceDiscoveryManager::shutdown);
        }
    }
}
