package stroom.proxy.app.handler;

import stroom.proxy.app.DataDirProvider;
import stroom.proxy.app.ProxyConfig;
import stroom.proxy.repo.ProxyServices;
import stroom.util.NullSafe;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class Forwarder {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Forwarder.class);

    private final List<Consumer<Path>> destinations = new ArrayList<>();
    private final NumberedDirProvider copiesDirProvider;

    @Inject
    public Forwarder(final CleanupDirQueue cleanupDirQueue,
                     final DataDirProvider dataDirProvider,
                     final ProxyConfig proxyConfig,
                     final HttpSenderFactory httpSenderFactory,
                     final ForwardFileDestinationFactory forwardFileDestinationFactory,
                     final ProxyServices proxyServices,
                     final DirQueueFactory sequentialDirQueueFactory) {

        // Find out how many forward destinations are enabled.
        final long enabledForwardCount = Stream
                .concat(NullSafe.list(proxyConfig.getForwardHttpDestinations())
                                .stream()
                                .filter(ForwardHttpPostConfig::isEnabled),
                        NullSafe.list(proxyConfig.getForwardFileDestinations())
                                .stream()
                                .filter(ForwardFileConfig::isEnabled))
                .count();

        if (enabledForwardCount == 0) {
            throw new RuntimeException("No forward destinations are configured");
        }

        // Add HTTP POST destinations.
        proxyConfig.getForwardHttpDestinations().forEach(forwardHttpPostConfig -> {
            if (forwardHttpPostConfig.isEnabled()) {
                final StreamDestination streamDestination = httpSenderFactory.create(forwardHttpPostConfig);
                final ForwardHttpPostDestination forwardDestination = new ForwardHttpPostDestination(
                        forwardHttpPostConfig.getName(),
                        streamDestination,
                        cleanupDirQueue,
                        forwardHttpPostConfig.getRetryDelay(),
                        forwardHttpPostConfig.getMaxRetries(),
                        proxyServices,
                        sequentialDirQueueFactory,
                        proxyConfig.getThreadConfig().getForwardThreadCount(),
                        proxyConfig.getThreadConfig().getForwardRetryThreadCount(),
                        dataDirProvider);
                this.destinations.add(forwardDestination::add);
            }
        });

        // Add file destinations.
        proxyConfig.getForwardFileDestinations().forEach(forwardFileConfig -> {
            if (forwardFileConfig.isEnabled()) {
                final ForwardFileDestination forwardFileDestination = forwardFileDestinationFactory
                        .create(forwardFileConfig);
                this.destinations.add(forwardFileDestination::add);
            }
        });

        // Make receiving zip dir.
        final Path copiesDir = dataDirProvider.get().resolve("temp_forward_copies");
        DirUtil.ensureDirExists(copiesDir);

        // This is a temporary location and can be cleaned completely on startup.
        if (!FileUtil.deleteContents(copiesDir)) {
            LOGGER.error(() -> "Failed to delete contents of " + FileUtil.getCanonicalPath(copiesDir));
        }
        copiesDirProvider = new NumberedDirProvider(copiesDir);
    }

    public void add(final Path dir) {
        try {
            final List<Path> paths = new ArrayList<>();

            // Create copies.
            for (int i = 0; i < destinations.size() - 1; i++) {
                final Path copy = copiesDirProvider.get();
                copyContents(dir, copy);
                paths.add(copy);
            }

            // Move with the final one.
            paths.add(dir);

            // Add all items to outgoing queues.
            for (int i = 0; i < destinations.size(); i++) {
                final Consumer<Path> destination = destinations.get(i);
                final Path path = paths.get(i);
                destination.accept(path);
            }

        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }
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
