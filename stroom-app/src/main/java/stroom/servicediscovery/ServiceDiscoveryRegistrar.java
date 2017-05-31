package stroom.servicediscovery;

import com.codahale.metrics.health.HealthCheck;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.ServiceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

@Component
public class ServiceDiscoveryRegistrar {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceDiscoveryRegistrar.class);

    private HealthCheck.Result health;
    private final CuratorConnection curatorConnection;
    private final String hostNameOrIpAddress;

    @Inject
    public ServiceDiscoveryRegistrar(
            final CuratorConnection curatorConnection,
            @Value("#{propertyConfigurer.getProperty('stroom.serviceDiscovery.hostNameOrIpAddress')}") String hostNameOrIpAddress){

        this.curatorConnection = curatorConnection;
        this.hostNameOrIpAddress = hostNameOrIpAddress;

        health = HealthCheck.Result.unhealthy("Waiting for Curator connection");
        this.curatorConnection.registerStartupListener(this::curatorStartupListener);
    }

    private void curatorStartupListener(CuratorFramework curatorFramework) {
        try {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Successfully registered the following services: ");

            registerResource(
                    "authentication",
                    hostNameOrIpAddress + "/api/authentication/",
                    curatorFramework);
            stringBuilder.append("authentication");

            registerResource(
                    "authorisation",
                    hostNameOrIpAddress + "/api/authorisation/",
                    curatorFramework);
            stringBuilder.append(", authorisation");

            registerResource(
                    "index",
                    hostNameOrIpAddress + "/api/index/",
                    curatorFramework);
            stringBuilder.append(", index.");

            health = HealthCheck.Result.healthy(stringBuilder.toString());
            LOGGER.info("All service instances created successfully.");
        } catch (Exception e){
            health = HealthCheck.Result.unhealthy("Service instance creation failed!", e);
            LOGGER.error("Service instance creation failed!", e);
            throw new RuntimeException("Service instance creation failed!", e);
        }
    }

    private static void registerResource(
            String name, String address, CuratorFramework client) throws Exception {
        ServiceInstance<String> serviceInstance = ServiceInstance.<String>builder()
                .serviceType(ServiceType.PERMANENT)
                .name(name)
                .address(address)
                //port currently included in the address
//                .port(8080)
                .build();

        ServiceDiscovery<String> serviceDiscovery = ServiceDiscoveryBuilder
                .builder(String.class)
                .client(client)
                .basePath("stroom-services")
                .thisInstance(serviceInstance)
                .build();
        serviceDiscovery.start();
        LOGGER.info("Successfully registered '{}' service.", name);
    }

    public HealthCheck.Result getHealth() {
        return health;
    }
}
