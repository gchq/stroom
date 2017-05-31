package stroom.servicediscovery;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.ServiceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.ExternalService;
import stroom.ServiceDiscoverer;
import stroom.util.spring.StroomShutdown;

import javax.inject.Singleton;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Singleton
public class ServiceDiscovererImpl implements ServiceDiscoverer {

    private final Logger LOGGER = LoggerFactory.getLogger(ServiceDiscovererImpl.class);

    private final CuratorConnection curatorConnection;
    /*
    Note: When using Curator 2.x (Zookeeper 3.4.x) it's essential that service provider objects are cached by your
    application and reused. Since the internal NamespaceWatcher objects added by the service provider cannot be
    removed in Zookeeper 3.4.x, creating a fresh service provider for each call to the same service will
    eventually exhaust the memory of the JVM.
     */
    private Map<ExternalService, ServiceProvider<String>> serviceProviders = new HashMap<>();
    private volatile ServiceDiscovery<String> serviceDiscovery;

    @SuppressWarnings("unused")
    public ServiceDiscovererImpl(final CuratorConnection curatorConnection){

        this.curatorConnection = curatorConnection;

        curatorConnection.registerStartupListener(this::initProviders);
    }

    @Override
    public Optional<String> getAddress(ExternalService externalService) {
        LOGGER.debug("Getting address for {}", externalService.getServiceKey());
        ServiceInstance<String> instance = getServiceInstance(externalService);

        return Optional.ofNullable(instance)
                .map(ServiceInstance::getAddress);
    }

    private synchronized void initProviders(final CuratorFramework curatorFramework) {

        serviceDiscovery = ServiceDiscoveryBuilder
                .builder(String.class)
                .client(curatorFramework)
                .basePath("stroom-services")
                .build();
        try {
            serviceDiscovery.start();

            //Attempt to
            Arrays.stream(ExternalService.values()).forEach(externalService -> {
                ServiceProvider<String> serviceProvider = createProvider(externalService.getServiceName());
                serviceProviders.put(externalService, serviceProvider);
            });

        } catch (Exception e) {
            LOGGER.error("There was a problem accessing service discovery!", e);
        }
    }

    private ServiceDiscovery<String> getServiceDiscovery() {
        if (serviceDiscovery == null) {
            synchronized (this) {
                if (serviceDiscovery == null) {

                }
            }
        }
        return serviceDiscovery;
    }

    private ServiceProvider<String> createProvider(String name){
        ServiceProvider<String> provider = serviceDiscovery.serviceProviderBuilder()
                .serviceName(name)
                .build();
        try {
            provider.start();
        } catch (Exception e) {
            LOGGER.error("Unable to start service provider for " + name + "!", e);
        }

        return provider;
    }

    private ServiceInstance<String> getServiceInstance(ExternalService externalService){
        try {
            return serviceProviders.get(externalService).getInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @StroomShutdown
    public void shutdown() {
        if (serviceDiscovery != null) {
            try {
                serviceDiscovery.close();
            } catch (IOException e) {
                LOGGER.error("Failed to close serviceDiscovery with error", e);
            }
        }
    }
}
