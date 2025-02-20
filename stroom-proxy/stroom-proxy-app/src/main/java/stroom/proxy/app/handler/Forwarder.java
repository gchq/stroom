package stroom.proxy.app.handler;

import stroom.proxy.app.DataDirProvider;
import stroom.proxy.app.ProxyConfig;
import stroom.util.NullSafe;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
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

    @Inject
    public Forwarder(final DataDirProvider dataDirProvider,
                     final Provider<ProxyConfig> proxyConfigProvider,
                     final ForwardFileDestinationFactory forwardFileDestinationFactory,
                     final ForwardHttpPostDestinationFactory forwardHttpPostDestinationFactory) {

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
            forwardDestinationFacade = new MultiForwardDestination(destinations, copiesDirProvider);
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


    // --------------------------------------------------------------------------------


    private record MultiForwardDestination(
            List<ForwardDestination> destinations,
            NumberedDirProvider copiesDirProvider) implements ForwardDestination {

        @Override
        public void add(final Path sourceDir) {
            try {
                final List<Path> paths = new ArrayList<>(destinations.size());

                // Create copies for all but the last destination
                for (int i = 0; i < destinations.size() - 1; i++) {
                    final Path copy = copiesDirProvider.get();
                    copyContents(sourceDir, copy);
                    paths.add(copy);
                }

                // The last destination gets the original, so it can do the atomic move
                paths.add(sourceDir);

                // Add all items to outgoing queues.
                for (int i = 0; i < destinations.size(); i++) {
                    final ForwardDestination destination = destinations.get(i);
                    final Path path = paths.get(i);
                    try {
                        destination.add(path);
                    } catch (Exception e) {
                        throw new RuntimeException(LogUtil.message(
                                "Error adding {} to destination {}",
                                path, destination.getDestinationDescription()));
                    }
                }
            } catch (final IOException e) {
                LOGGER.error(e::getMessage, e);
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public String getName() {
            return "Multi forward destination";
        }

        @Override
        public String getDestinationDescription() {
            return "Facade for " + destinations.size() + " destinations";
        }

        private void copyContents(final Path source, final Path target) {
            try (final Stream<Path> stream = Files.list(source)) {
                stream.forEach(path -> {
                    try {
                        Files.copy(path, target.resolve(path.getFileName()));
                    } catch (final IOException e) {
                        LOGGER.error(e::getMessage, e);
                        throw new UncheckedIOException(e);
                    }
                });
            } catch (final IOException e) {
                LOGGER.error(e::getMessage, e);
                throw new UncheckedIOException(e);
            }
        }
    }
}
