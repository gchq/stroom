package stroom.proxy.app.forwarder;

import stroom.proxy.app.ProxyConfig;
import stroom.proxy.repo.FailureDestinations;
import stroom.proxy.repo.ForwardRetryConfig;
import stroom.receive.common.StreamHandlers;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.logging.LogUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
public class FailureDestinationsImpl implements FailureDestinations {

    private static final Logger LOGGER = LoggerFactory.getLogger(FailureDestinationsImpl.class);

    private final Map<String, StreamHandlers> providers;
    private final PathCreator pathCreator;

    @Inject
    public FailureDestinationsImpl(final ProxyConfig proxyConfig,
                                   final ForwardRetryConfig forwardRetryConfig,
                                   final PathCreator pathCreator,
                                   final ForwardFileHandlersFactory forwardFileHandlersFactory) {
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
            forwardDestinations = forwardDestinations.stream().filter(ForwardConfig::isEnabled).toList();
        } else {
            forwardDestinations = Collections.emptyList();
        }

        if (forwardDestinations.size() > 0) {
            // Create failed dest dir.
            final Path failedForwardPath = pathCreator.toAppPath(forwardRetryConfig.getFailedForwardDir());
            try {
                Files.createDirectories(failedForwardPath);
            } catch (final IOException e) {
                LOGGER.error(LogUtil.message(
                        "Failed to create directory '{}' to store failed forward data. This is configured using " +
                                "property {}. {}",
                        FileUtil.getCanonicalPath(failedForwardPath),
                        forwardRetryConfig.getFullPathStr(ForwardRetryConfig.PROP_NAME_FAILED_FORWARD_DIR),
                        e.getMessage()));
                throw new UncheckedIOException(e);
            }

            this.providers = forwardDestinations.stream().collect(Collectors.toMap(ForwardConfig::getName, f -> {
                final String subDir = f.getName();
                final Path path = failedForwardPath.resolve(subDir);
                try {
                    Files.createDirectories(path);
                } catch (final IOException e) {
                    LOGGER.error("Failed to create directory to store failed forward data: " +
                            FileUtil.getCanonicalPath(path));
                    throw new UncheckedIOException(e);
                }
                final ForwardFileConfig forwardFileConfig = new ForwardFileConfig(true,
                        f.getName(),
                        FileUtil.getCanonicalPath(path));

                return forwardFileHandlersFactory.create(forwardFileConfig, pathCreator);
            }));
        } else {
            this.providers = Collections.emptyMap();
        }
    }

    @Override
    public StreamHandlers getProvider(final String name) {
        return providers.get(name);
    }
}
