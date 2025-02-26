package stroom.proxy.app.handler;

import stroom.proxy.app.DataDirProvider;
import stroom.proxy.app.ProxyConfig;
import stroom.util.NullSafe;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
public class Forwarder {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Forwarder.class);

    private final List<ForwardDestination> destinations = new ArrayList<>();
    private final ForwardDestination forwardDestinationFacade;
//    private final CleanupDirQueue cleanupDirQueue;

    @Inject
    public Forwarder(final DataDirProvider dataDirProvider,
                     final Provider<ProxyConfig> proxyConfigProvider,
                     final ForwardFileDestinationFactory forwardFileDestinationFactory,
                     final ForwardHttpPostDestinationFactory forwardHttpPostDestinationFactory,
                     final CleanupDirQueue cleanupDirQueue) {
//        this.cleanupDirQueue = cleanupDirQueue;

        // Find out how many forward destinations are enabled.
        final ProxyConfig proxyConfig = proxyConfigProvider.get();
        final long enabledForwardCount = Stream
                .concat(NullSafe.stream(proxyConfig.getForwardHttpDestinations())
                                .filter(ForwardHttpPostConfig::isEnabled),
                        NullSafe.stream(proxyConfig.getForwardFileDestinations())
                                .filter(ForwardFileConfig::isEnabled))
                .count();

        LOGGER.debug("enabledForwardCount: {}", enabledForwardCount);

        if (enabledForwardCount == 0) {
            throw new RuntimeException("No forward destinations are configured");
        }

        // Add HTTP POST destinations.
        NullSafe.stream(proxyConfig.getForwardHttpDestinations())
                .filter(ForwardHttpPostConfig::isEnabled)
                .map(forwardHttpPostDestinationFactory::create)
                .forEach(destinations::add);

        // Add file destinations.
        NullSafe.stream(proxyConfig.getForwardFileDestinations())
                .filter(ForwardFileConfig::isEnabled)
                .map(forwardFileDestinationFactory::create)
                .forEach(destinations::add);

        final NumberedDirProvider copiesDirProvider = createCopiesDirProvider(dataDirProvider);

        if (destinations.size() == 1) {
            // Most proxies will only have one destination configured so optimise for that
            forwardDestinationFacade = NullSafe.first(destinations);
        } else {
            forwardDestinationFacade = new MultiForwardDestination(
                    destinations,
                    copiesDirProvider,
                    cleanupDirQueue);
        }
    }

    private static NumberedDirProvider createCopiesDirProvider(final DataDirProvider dataDirProvider) {
        // Make receiving zip dir.
        final Path copiesDir = dataDirProvider.get().resolve("temp_forward_copies");
        DirUtil.ensureDirExists(copiesDir);

        // This is a temporary location and can be cleaned completely on startup.
        LOGGER.info("Deleting contents of {}", copiesDir);
        if (!FileUtil.deleteContents(copiesDir)) {
            LOGGER.error(() -> "Failed to delete contents of " + FileUtil.getCanonicalPath(copiesDir));
        }
        return new NumberedDirProvider(copiesDir);
    }

    /**
     * Add dir to all configured forward destinations.
     */
    public void add(final Path dir) {
        forwardDestinationFacade.add(dir);
    }

    @Override
    public String toString() {
        return "Destinations: " + NullSafe.stream(destinations)
                .map(ForwardDestination::getDestinationDescription)
                .collect(Collectors.joining(", "));
    }
}
