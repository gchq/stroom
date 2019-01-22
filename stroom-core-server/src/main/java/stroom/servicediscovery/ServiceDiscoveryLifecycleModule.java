package stroom.servicediscovery;

import stroom.lifecycle.api.AbstractLifecycleModule;
import stroom.lifecycle.api.RunnableWrapper;

import javax.inject.Inject;

public class ServiceDiscoveryLifecycleModule extends AbstractLifecycleModule {
    @Override
    protected void configure() {
        super.configure();
        bindShutdown().to(ServiceDiscovererShutdown.class);
        bindShutdown().to(ServiceDiscoveryManagerShutdown.class);
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
