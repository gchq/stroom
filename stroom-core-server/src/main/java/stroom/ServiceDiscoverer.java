package stroom;

import java.util.Optional;

public interface ServiceDiscoverer {
    Optional<String> getAddress(ExternalService externalService);
}
