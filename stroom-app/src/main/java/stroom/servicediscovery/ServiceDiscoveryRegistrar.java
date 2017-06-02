package stroom.servicediscovery;

import com.codahale.metrics.health.HealthCheck;
import com.google.common.base.Preconditions;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.ServiceType;
import org.apache.curator.x.discovery.UriSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.node.server.StroomPropertyService;
import stroom.resources.RegisteredService;
import stroom.resources.ResourcePaths;

import javax.inject.Inject;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

@Component
public class ServiceDiscoveryRegistrar {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceDiscoveryRegistrar.class);

    private static final String PROP_KEY_SERVICE_HOST_OR_IP = "stroom.serviceDiscovery.servicesHostNameOrIpAddress";
    private static final String PROP_KEY_SERVICE_PORT = "stroom.serviceDiscovery.servicesPort";

    private HealthCheck.Result health;
    private final ServiceDiscoveryManager serviceDiscoveryManager;
    private final String hostNameOrIpAddress;
    private final int servicePort;

    @Inject
    public ServiceDiscoveryRegistrar(final ServiceDiscoveryManager serviceDiscoveryManager,
                                     final StroomPropertyService stroomPropertyService) {

        this.serviceDiscoveryManager = serviceDiscoveryManager;
        this.hostNameOrIpAddress = getHostOrIp(stroomPropertyService);
        this.servicePort = stroomPropertyService.getIntProperty(PROP_KEY_SERVICE_PORT, 8080);

        health = HealthCheck.Result.unhealthy("Waiting for Curator connection...");
        this.serviceDiscoveryManager.registerStartupListener(this::curatorStartupListener);
    }

    private String getHostOrIp(final StroomPropertyService stroomPropertyService) {
        String hostOrIp = stroomPropertyService.getProperty(PROP_KEY_SERVICE_HOST_OR_IP);
        if (hostOrIp == null || hostOrIp.isEmpty()) {
            try {
                hostOrIp = InetAddress.getLocalHost().getCanonicalHostName();
            } catch (UnknownHostException e) {
                LOGGER.warn("Unable to determine hostname of this instance due to error. Will try to get IP address instead", e);
            }

            if (hostOrIp == null || hostOrIp.isEmpty()) {
                try {
                    hostOrIp = InetAddress.getLocalHost().getHostAddress();
                } catch (UnknownHostException e) {
                    throw new RuntimeException(String.format("Error establishing hostname or IP address of this instance"), e);
                }
            }
        }
        return hostOrIp;
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
            UriSpec uriSpec = new UriSpec("{scheme}://{address}:{port}" +
                    ResourcePaths.ROOT_PATH +
                    registeredService.getVersionedPath());

            ServiceInstance<String> serviceInstance = ServiceInstance.<String>builder()
                    .serviceType(ServiceType.DYNAMIC) //==ephemeral zk nodes so instance will disappear if we lose zk conn
                    .uriSpec(uriSpec)
                    .name(registeredService.getVersionedServiceName())
                    .address(hostNameOrIpAddress)
                    .port(servicePort)
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
