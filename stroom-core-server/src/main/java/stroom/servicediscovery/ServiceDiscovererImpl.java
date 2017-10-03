package stroom.servicediscovery;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheck.Result;
import io.vavr.Tuple2;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.ServiceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.util.spring.StroomShutdown;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
public class ServiceDiscovererImpl implements ServiceDiscoverer {
    private final Logger LOGGER = LoggerFactory.getLogger(ServiceDiscovererImpl.class);

    /*
    Note: When using Curator 2.x (Zookeeper 3.4.x) it's essential that service provider objects are cached by your
    application and reused. Since the internal NamespaceWatcher objects added by the service provider cannot be
    removed in Zookeeper 3.4.x, creating a fresh service provider for each call to the same service will
    eventually exhaust the memory of the JVM.
     */
    private Map<ExternalService, ServiceProvider<String>> serviceProviders = new HashMap<>();

    @Inject
    public ServiceDiscovererImpl(final ServiceDiscoveryManager serviceDiscoveryManager) {
        //create the service providers once service discovery has started up
        serviceDiscoveryManager.registerStartupListener(this::initProviders);
    }

    @Override
    public Optional<ServiceInstance<String>> getServiceInstance(final ExternalService externalService) {
        try {
            LOGGER.trace("Getting service instance for {}", externalService.getServiceKey());
            return Optional.ofNullable(serviceProviders.get(externalService))
                    .flatMap(stringServiceProvider -> {
                        try {
                            return Optional.ofNullable(stringServiceProvider.getInstance());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void initProviders(final ServiceDiscovery<String> serviceDiscovery) {

        //Attempt to create ServiceProviders for each of the ExternalServices
        Arrays.stream(ExternalService.values())
                .filter(externalService -> externalService.getType().equals(ExternalService.Type.CLIENT) ||
                        externalService.getType().equals(ExternalService.Type.CLIENT_AND_SERVER))
                .forEach(externalService -> {
                    ServiceProvider<String> serviceProvider = createProvider(serviceDiscovery, externalService);
                    LOGGER.debug("Adding service provider {}", externalService.getVersionedServiceName());
                    serviceProviders.put(externalService, serviceProvider);
                });
    }

    private ServiceProvider<String> createProvider(final ServiceDiscovery<String> serviceDiscovery,
                                                   final ExternalService externalService) {
        ServiceProvider<String> provider = serviceDiscovery.serviceProviderBuilder()
                .serviceName(externalService.getVersionedServiceName())
                .providerStrategy(externalService.getProviderStrategy())
                .build();
        try {
            provider.start();
        } catch (Exception e) {
            LOGGER.error("Unable to start service provider for {}", externalService.getVersionedServiceName(), e);
        }

        return provider;
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

    @Override
    public Result getHealth() {
        if (serviceProviders.isEmpty()) {
            return HealthCheck.Result.unhealthy("No service providers found");
        } else {
            try {
                Map<String, List<String>> serviceInstanceMap = serviceProviders.entrySet().stream()
                        .flatMap(entry -> {
                            try {
                                return entry.getValue().getAllInstances().stream();
                            } catch (Exception e) {
                                throw new RuntimeException(String.format("Error querying instances for service %s",
                                        entry.getKey().getVersionedServiceName()), e);
                            }
                        })
                        .map(serviceInstance -> new Tuple2<>(serviceInstance.getName(), serviceInstance.buildUriSpec()))
                        .collect(Collectors.groupingBy(
                                Tuple2::_1,
                                TreeMap::new,
                                Collectors.mapping(Tuple2::_2, Collectors.toList())));

                //ensure the instances are sorted in a sensible way
                serviceInstanceMap.values().forEach(Collections::sort);

                long deadServiceCount = serviceInstanceMap.entrySet().stream()
                        .filter(entry -> entry.getValue().isEmpty())
                        .count();

                HealthCheck.ResultBuilder builder = HealthCheck.Result.builder();

                if (deadServiceCount > 0) {
                    builder.unhealthy()
                            .withMessage("%s service(s) have no registered instances");
                } else {
                    builder.healthy()
                            .withMessage("All services (local and remote) available");
                }
                return builder.withDetail("discovered-service-instances", serviceInstanceMap)
                        .build();

            } catch (Exception e) {
                return HealthCheck.Result.unhealthy("Error getting service provider details, error: " +
                        e.getCause().getMessage());
            }
        }
    }
}
