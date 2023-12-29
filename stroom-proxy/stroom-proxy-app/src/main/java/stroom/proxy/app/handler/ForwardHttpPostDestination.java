package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.repo.ProxyServices;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.time.StroomDuration;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ForwardHttpPostDestination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ForwardHttpPostDestination.class);

    private final StreamDestination destination;
    private final DirQueue forwardQueue;
    private final DirQueue retryQueue;
    private final CleanupDirQueue cleanupDirQueue;
    private final StroomDuration retryDelay;
    private final String destinationName;

    public ForwardHttpPostDestination(final String destinationName,
                                      final StreamDestination destination,
                                      final CleanupDirQueue cleanupDirQueue,
                                      final StroomDuration retryDelay,
                                      final ProxyServices proxyServices,
                                      final DirQueueFactory sequentialDirQueueFactory,
                                      final int forwardThreads,
                                      final int retryThreads) {
        this.destination = destination;
        this.cleanupDirQueue = cleanupDirQueue;
        this.destinationName = destinationName;
        this.retryDelay = retryDelay;
        forwardQueue = sequentialDirQueueFactory.create(
                "20_forward_" + destinationName,
                20,
                "forward - " + destinationName);
        retryQueue = sequentialDirQueueFactory.create(
                "21_retry_" + destinationName,
                21,
                "retry - " + destinationName);
        final DirQueueTransfer forwarding = new DirQueueTransfer(forwardQueue::next, this::forwardDir);
        final DirQueueTransfer retrying = new DirQueueTransfer(retryQueue::next, this::retryDir);
        proxyServices.addParallelExecutor(
                "forward - " + destinationName,
                () -> forwarding,
                forwardThreads);
        proxyServices.addParallelExecutor(
                "retry - " + destinationName,
                () -> retrying,
                retryThreads);
    }

    public void add(final Path sourceDir) {
        forwardQueue.add(sourceDir);
    }

    private boolean forwardDir(final Path dir) {
        try {
            final FileGroup fileGroup = new FileGroup(dir);
            final AttributeMap attributeMap = new AttributeMap();
            AttributeMapUtil.read(fileGroup.getMeta(), attributeMap);
            // Make sure we tell the destination we are sending zip data.
            attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_ZIP);

            // Send the data.
            try (final InputStream inputStream = new BufferedInputStream(Files.newInputStream(fileGroup.getZip()))) {
                destination.send(attributeMap, inputStream);
            }

            // We have completed sending so can delete the data.
            cleanupDirQueue.add(dir);

            // Return true for success.
            return true;

        } catch (final IOException e) {
            LOGGER.error(
                    () -> "Error sending '" + FileUtil.getCanonicalPath(dir) + "' to '" + destinationName + "'.", e);
            LOGGER.debug(e::getMessage, e);
            retryQueue.add(dir);
        }

        // Failed, return false.
        return false;
    }

    private void retryDir(final Path dir) {
        if (!forwardDir(dir)) {
            // If we failed to send then wait for a bit.
            if (!retryDelay.isZero()) {
                LOGGER.trace("'{}' - adding delay {}", destinationName, retryDelay);
                ThreadUtil.sleep(retryDelay);
            }
        }
    }
}
