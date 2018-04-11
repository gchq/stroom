package stroom.servicediscovery;

import com.google.inject.AbstractModule;

public class ServiceDiscoveryModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ServiceDiscoverer.class).to(ServiceDiscovererImpl.class);
    }
}
