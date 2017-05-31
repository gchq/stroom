package stroom.servicediscovery;

import com.codahale.metrics.health.HealthCheck;
import com.google.common.base.Preconditions;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.ServiceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import stroom.resources.RegisteredService;
import stroom.resources.ResourcePaths;

import javax.inject.Inject;
import java.util.Arrays;

@Component
public class ServiceDiscoveryRegistrar {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceDiscoveryRegistrar.class);

    private HealthCheck.Result health;
    private final ServiceDiscoveryManager serviceDiscoveryManager;
    private final String hostNameOrIpAddress;
    private final String basePath;

    @Inject
    public ServiceDiscoveryRegistrar(
            final ServiceDiscoveryManager serviceDiscoveryManager,
            @Value("#{propertyConfigurer.getProperty('stroom.serviceDiscovery.hostNameOrIpAddress')}") String hostNameOrIpAddress,
            @Value("#{propertyConfigurer.getProperty('stroom.serviceDiscovery.basePath')}") String basePath) {

        this.serviceDiscoveryManager = serviceDiscoveryManager;
        this.hostNameOrIpAddress = hostNameOrIpAddress;
        this.basePath = basePath;

        health = HealthCheck.Result.unhealthy("Waiting for Curator connection");
        this.serviceDiscoveryManager.registerStartupListener(this::curatorStartupListener);
    }

    private void curatorStartupListener(ServiceDiscovery<String> serviceDiscovery) {
        try {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Successfully registered the following services: ");

            Arrays.stream(RegisteredService.values())
                    .forEach(registeredService -> {
                        registerResource(
                                registeredService,
                                serviceDiscovery);
                        stringBuilder.append(registeredService.getVersionedServiceName());
                        stringBuilder.append(", ");
                    });

            health = HealthCheck.Result.healthy(stringBuilder.toString().replaceAll(", $", ""));
            LOGGER.info("All service instances created successfully.");
        } catch (Exception e) {
            health = HealthCheck.Result.unhealthy("Service instance creation failed!", e);
            LOGGER.error("Service instance creation failed!", e);
            throw new RuntimeException("Service instance creation failed!", e);
        }
    }

    private void registerResource(final RegisteredService registeredService,
                                  final ServiceDiscovery<String> serviceDiscovery) {
        try {
            ServiceInstance<String> serviceInstance = ServiceInstance.<String>builder()
                    .serviceType(ServiceType.PERMANENT)
                    .name(registeredService.getVersionedServiceName())
                    .address(hostNameOrIpAddress + ResourcePaths.ROOT_PATH + registeredService.getVersionedPath())
                    //port currently included in the address
    //                .port(8080)
                    .build();

            Preconditions.checkNotNull(serviceDiscovery).registerService(serviceInstance);

            LOGGER.info("Successfully registered '{}' service.", registeredService.getVersionedServiceName());
        } catch (Exception e) {
            throw new RuntimeException("Failed to register service " + registeredService.getVersionedServiceName(), e);
        }
    }

    public HealthCheck.Result getHealth() {
        return health;
    }
}
