package stroom.startup;

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
import stroom.Config;

public class ServiceDiscoveryManager {
    private final Logger LOGGER = LoggerFactory.getLogger(ServiceDiscoveryManager.class);

    private final ServiceDiscovery<String> serviceDiscovery;
    private final ServiceInstance<String> thisInstance;

    //TODO add health check

    public ServiceDiscoveryManager(Config config){
        //TODO validate the URL we get passed - we don't want to register with something duff, e.g. missing the protocol
        LOGGER.info("Starting Curator client using Zookeeper at '{}'...", config.getZookeeperUrl());
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        CuratorFramework client = CuratorFrameworkFactory.newClient(config.getZookeeperUrl(), retryPolicy);
        client.start();

        try {
            LOGGER.info("Setting up instance for '{}' service, running on '{}'...", "stroom", config.getHostNameOrIpAddress());

            thisInstance = ServiceInstance.<String>builder()
                .serviceType(ServiceType.PERMANENT)
                .name("stroom")
                .address(config.getHostNameOrIpAddress())
                .port(8080)
                .build();

            serviceDiscovery = ServiceDiscoveryBuilder
                .builder(String.class)
                .client(client)
                .basePath("stroom-services")
                .thisInstance(thisInstance)
                .build();
            serviceDiscovery.start();
            LOGGER.info("Service instance created successfully!");
        } catch (Exception e){
            LOGGER.error("Service instance creation failed!", e);
            throw new RuntimeException("Service instance creation failed!", e);
        }
    }
}
