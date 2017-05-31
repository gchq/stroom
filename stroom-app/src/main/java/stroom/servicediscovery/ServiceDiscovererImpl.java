package stroom.servicediscovery;

import com.codahale.metrics.health.HealthCheck;
import org.apache.curator.x.discovery.ServiceDiscovery;
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
import java.util.stream.Collectors;

@Component
@Singleton
public class ServiceDiscovererImpl implements ServiceDiscoverer {

    private final Logger LOGGER = LoggerFactory.getLogger(ServiceDiscovererImpl.class);

    private final ServiceDiscoveryManager serviceDiscoveryManager;
    /*
    Note: When using Curator 2.x (Zookeeper 3.4.x) it's essential that service provider objects are cached by your
    application and reused. Since the internal NamespaceWatcher objects added by the service provider cannot be
    removed in Zookeeper 3.4.x, creating a fresh service provider for each call to the same service will
    eventually exhaust the memory of the JVM.
     */
    private Map<ExternalService, ServiceProvider<String>> serviceProviders = new HashMap<>();

    @SuppressWarnings("unused")
    public ServiceDiscovererImpl(final ServiceDiscoveryManager serviceDiscoveryManager) {

        this.serviceDiscoveryManager = serviceDiscoveryManager;

        serviceDiscoveryManager.registerStartupListener(this::initProviders);
    }

    @Override
    public Optional<String> getAddress(ExternalService externalService) {
        LOGGER.debug("Getting address for {}", externalService.getServiceKey());
        ServiceInstance<String> instance = getServiceInstance(externalService);

        return Optional.ofNullable(instance)
                .map(ServiceInstance::getAddress);
    }

    private void initProviders(final ServiceDiscovery<String> serviceDiscovery) {

        //Attempt to
        Arrays.stream(ExternalService.values()).forEach(externalService -> {
            ServiceProvider<String> serviceProvider = createProvider(
                    serviceDiscovery,
                    externalService.getVersionedServiceName());
            serviceProviders.put(externalService, serviceProvider);
        });

    }


    private ServiceProvider<String> createProvider(ServiceDiscovery<String> serviceDiscovery, String serviceName) {
        ServiceProvider<String> provider = serviceDiscovery.serviceProviderBuilder()
                .serviceName(serviceName)
                .build();
        try {
            provider.start();
        } catch (Exception e) {
            LOGGER.error("Unable to start service provider for " + serviceName + "!", e);
        }

        return provider;
    }

    private ServiceInstance<String> getServiceInstance(ExternalService externalService) {
        try {
            return serviceProviders.get(externalService).getInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @StroomShutdown
    public void shutdown() {
        serviceProviders.entrySet().forEach(entry -> {
            try {
                entry.getValue().close();
            } catch (IOException e) {
                LOGGER.error("Failed to close serviceProvider {} with error",
                        entry.getKey().getVersionedServiceName(), e);
            }
        });
    }

    public HealthCheck.Result getHealth() {
        if (serviceProviders.isEmpty()) {
            return HealthCheck.Result.unhealthy("No service providers found");
        } else {
            String providers = null;
            try {
                providers = serviceProviders.entrySet().stream()
                        .map(entry -> {
                            try {
                                return entry.getKey().getVersionedServiceName() + " - " + entry.getValue().getAllInstances().size();
                            } catch (Exception e) {
                                throw new RuntimeException(String.format("Error querying instances for service %s",
                                        entry.getKey().getVersionedServiceName()), e);
                            }
                        })
                        .collect(Collectors.joining(","));
            } catch (Exception e) {
                return HealthCheck.Result.unhealthy("Error getting service provider details, error: " + e.getCause().getMessage());
            }

            return HealthCheck.Result.healthy("Running. Services providers: " + providers);
        }
    }
}
