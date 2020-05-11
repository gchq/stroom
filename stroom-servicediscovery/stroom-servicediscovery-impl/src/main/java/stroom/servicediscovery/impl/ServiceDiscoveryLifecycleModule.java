package stroom.servicediscovery.impl;

import stroom.lifecycle.api.LifecycleBinder;
import stroom.util.RunnableWrapper;

import com.google.inject.AbstractModule;

import javax.inject.Inject;

public class ServiceDiscoveryLifecycleModule extends AbstractModule {

    @Override
    protected void configure() {

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
