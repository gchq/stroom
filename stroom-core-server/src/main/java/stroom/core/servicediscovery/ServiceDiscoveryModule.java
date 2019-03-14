package stroom.core.servicediscovery;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.util.HasHealthCheck;

public class ServiceDiscoveryModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ServiceDiscoverer.class).to(ServiceDiscovererImpl.class);

        final Multibinder<HasHealthCheck> hasHealthCheckBinder = Multibinder.newSetBinder(binder(), HasHealthCheck.class);
        hasHealthCheckBinder.addBinding().to(ServiceDiscoveryRegistrar.class);
        hasHealthCheckBinder.addBinding().to(ServiceDiscovererImpl.class);
    }
}
