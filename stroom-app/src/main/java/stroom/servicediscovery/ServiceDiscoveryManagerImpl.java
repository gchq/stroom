package stroom.servicediscovery;

import com.codahale.metrics.health.HealthCheck;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.ServiceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ServiceDiscoveryManagerImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceDiscoveryManagerImpl.class);

    private HealthCheck.Result health;

    public ServiceDiscoveryManagerImpl(
            @Value("#{propertyConfigurer.getProperty('stroom.serviceDiscovery.zookeeperUrl')}") String zookeeperUrl,
            @Value("#{propertyConfigurer.getProperty('stroom.serviceDiscovery.hostNameOrIpAddress')}") String hostNameOrIpAddress){
        //TODO validate the URL we get passed - we don't want to register with something duff, e.g. missing the protocol
        LOGGER.info("Starting Curator client using Zookeeper at '{}'...", zookeeperUrl);
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        CuratorFramework client = CuratorFrameworkFactory.newClient(zookeeperUrl, retryPolicy);
        client.start();

        try {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Successfully registered the following services: ");

            registerResource(
                    "authentication",
                    hostNameOrIpAddress + "/api/authentication/",
                    client);
            stringBuilder.append("authentication");

            registerResource(
                    "authorisation",
                    hostNameOrIpAddress + "/api/authorisation/",
                    client);
            stringBuilder.append(", authorisation");

            registerResource(
                    "index",
                    hostNameOrIpAddress + "/api/index/",
                    client);
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
                .port(8080)
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
