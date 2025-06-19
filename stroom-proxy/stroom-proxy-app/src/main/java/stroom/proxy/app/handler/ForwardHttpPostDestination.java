package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ForwardHttpPostDestination implements ForwardDestination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ForwardHttpPostDestination.class);

    private final StreamDestination destination;
    private final CleanupDirQueue cleanupDirQueue;
    private final ForwardHttpPostConfig forwardHttpPostConfig;
    private final String destinationName;

    public ForwardHttpPostDestination(final String destinationName,
                                      final StreamDestination destination,
                                      final CleanupDirQueue cleanupDirQueue,
                                      final ForwardHttpPostConfig forwardHttpPostConfig) {
        this.destination = destination;
        this.cleanupDirQueue = cleanupDirQueue;
        this.destinationName = destinationName;
        this.forwardHttpPostConfig = forwardHttpPostConfig;
    }

    @Override
    public void add(final Path sourceDir) {
        LOGGER.debug("'{}' - add(), dir: {}", destinationName, sourceDir);
        try {
            final FileGroup fileGroup = new FileGroup(sourceDir);
            final AttributeMap attributeMap = new AttributeMap();
            AttributeMapUtil.read(fileGroup.getMeta(), attributeMap);
            // Make sure we tell the destination we are sending zip data.
            attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_ZIP);

            // Send the data.
            try (final InputStream inputStream =
                    new BufferedInputStream(Files.newInputStream(fileGroup.getZip()))) {
                destination.send(attributeMap, inputStream);
            }

            // We have completed sending so can delete the data.
            cleanupDirQueue.add(sourceDir);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean hasLivenessCheck() {
        return NullSafe.isNonBlankString(forwardHttpPostConfig.getLivenessCheckUrl());
    }

    @Override
    public boolean performLivenessCheck() throws Exception {
        return !hasLivenessCheck()
               || destination.performLivenessCheck();
    }

    @Override
    public String getName() {
        return forwardHttpPostConfig.getName();
    }

    @Override
    public DestinationType getDestinationType() {
        return DestinationType.HTTP;
    }

    @Override
    public String getDestinationDescription() {
        return forwardHttpPostConfig.getForwardUrl()
               + " (instant=" + forwardHttpPostConfig.isInstant() + ")";
    }

    @Override
    public String toString() {
        return asString();
    }
}
