package stroom.proxy.app.forwarder;

import stroom.proxy.app.ProxyConfig;
import stroom.proxy.repo.ForwarderDestinations;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.receive.common.StreamHandlers;
import stroom.util.io.PathCreator;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Handler class that forwards the request to a URL.
 */
@Singleton
public class ForwarderDestinationsImpl implements ForwarderDestinations {

    private static final Logger LOGGER = LoggerFactory.getLogger(ForwarderDestinationsImpl.class);

    private final Map<String, StreamHandlers> providers;
    private final PathCreator pathCreator;

    @Inject
    public ForwarderDestinationsImpl(final ProxyConfig proxyConfig,
                                     final ProxyRepoConfig proxyRepoConfig,
                                     final ForwardHttpPostHandlersFactory forwardHttpPostHandlersFactory,
                                     final ForwardFileHandlersFactory forwardFileHandlersFactory,
                                     final PathCreator pathCreator) {
        this.pathCreator = pathCreator;
        // Get forwarding destinations.
        List<ForwardConfig> forwardDestinations = proxyConfig.getForwardDestinations();
        if (forwardDestinations != null) {
            // Ensure unique names.
            final Set<String> names = new HashSet<>();
            forwardDestinations.forEach(dest -> {
                if (dest.getName() == null || dest.getName().isBlank()) {
                    throw new RuntimeException("A destination has a null or empty name.");
                }
                if (names.contains(dest.getName())) {
                    throw new RuntimeException("Duplicate destination name \"" + dest.getName() + "\" found.");
                }
                names.add(dest.getName());
            });

            // Filter out disabled.
            forwardDestinations = forwardDestinations.stream()
                    .filter(ForwardConfig::isEnabled)
                    .toList();
        } else {
            forwardDestinations = Collections.emptyList();
        }

        if (forwardDestinations.size() > 0) {
            providers = forwardDestinations
                    .stream()
                    .collect(Collectors.toMap(ForwardConfig::getName, f -> {
                        if (f instanceof final ForwardHttpPostConfig forwardHttpPostConfig) {
                            return forwardHttpPostHandlersFactory.create(forwardHttpPostConfig);
                        } else if (f instanceof final ForwardFileConfig forwardFileConfig) {
                            return forwardFileHandlersFactory.create(forwardFileConfig, pathCreator);
                        } else {
                            throw new RuntimeException("Unknown config type");
                        }
                    }));
        } else {
            LOGGER.info("Forwarding of streams is disabled");
            this.providers = Collections.emptyMap();
        }

        if (proxyRepoConfig.isStoringEnabled() && Strings.isNullOrEmpty(proxyRepoConfig.getRepoDir())) {
            throw new RuntimeException("Storing is enabled but no repo directory have been provided in 'repoDir'");
        }
    }

    @Override
    public List<String> getDestinationNames() {
        return new ArrayList<>(providers.keySet());
    }

    @Override
    public StreamHandlers getProvider(final String name) {
        return providers.get(name);
    }
}
