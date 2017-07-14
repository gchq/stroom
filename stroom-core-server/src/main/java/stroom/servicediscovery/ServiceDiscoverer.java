package stroom.servicediscovery;

import org.apache.curator.x.discovery.ServiceInstance;

import java.util.Optional;

public interface ServiceDiscoverer {

    /**
     * Get a {@link ServiceInstance} object for the passed {@link ExternalService} definition. The instance of the service
     * provider chosen will depend on the strategy defined in {@link ExternalService}. The instance will not be marked
     * as disabled and will also not be deemed to be 'down' by Curator
     * (see {@link org.apache.curator.x.discovery.details.DownInstanceManager)}
     * @param externalService The definition of the service to get an instance of
     * @return A {@link ServiceInstance} object representing one of the instances of the external service.  You MUST not
     * hold onto this object, use it to call the service then throw it away.
     */
    Optional<ServiceInstance<String>> getServiceInstance(final ExternalService externalService);
}
