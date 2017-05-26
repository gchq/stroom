package stroom.servicediscovery;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.ServiceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import stroom.ExternalService;
import stroom.ServiceDiscoverer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class ServiceDiscovererImpl implements ServiceDiscoverer {

    private final Logger LOGGER = LoggerFactory.getLogger(ServiceDiscovererImpl.class);

    private Map<ExternalService, ServiceProvider<String>> serviceProviders = new HashMap<>();
    private ServiceDiscovery<String> serviceDiscovery;

    public ServiceDiscovererImpl(
            @Value("#{propertyConfigurer.getProperty('stroom.serviceDiscovery.zookeeperUrl')}") String zookeeperUrl){

        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        CuratorFramework client = CuratorFrameworkFactory.newClient(zookeeperUrl, retryPolicy);
        client.start();

        serviceDiscovery = ServiceDiscoveryBuilder
                .builder(String.class)
                .client(client)
                .basePath("stroom-services")
                .build();
        try {
            serviceDiscovery.start();

            Arrays.stream(ExternalService.values()).forEach(externalService -> {
                ServiceProvider<String> serviceProvider = createProvider(externalService.getServiceName());
                serviceProviders.put(externalService, serviceProvider);
            });

        } catch (Exception e) {
            LOGGER.error("There was a problem accessing service discovery!", e);
            e.printStackTrace();
        }
    }

    @Override
    public Optional<String> getAddress(ExternalService externalService) {
        LOGGER.debug("Getting address for {}", externalService.getServiceKey());
        ServiceInstance<String> instance = getServiceInstance(externalService);

        return Optional.ofNullable(instance)
                .map(ServiceInstance::getAddress);
    }

    private ServiceProvider<String> createProvider(String name){
        ServiceProvider<String> provider = serviceDiscovery.serviceProviderBuilder()
                .serviceName(name)
                .build();
        try {
            provider.start();
        } catch (Exception e) {
            LOGGER.error("Unable to start service provider for " + name + "!", e);
            e.printStackTrace();
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
}
