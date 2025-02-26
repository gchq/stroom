package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.DataDirProvider;
import stroom.proxy.repo.ProxyServices;
import stroom.util.NullSafe;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.SimplePathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.time.StroomDuration;
import stroom.util.time.TimeUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class ForwardHttpPostDestination implements ForwardDestination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ForwardHttpPostDestination.class);

    private static final String ERROR_LOG_FILENAME = "error.log";
    private static final String RETRY_STATE_FILENAME = "retry.state";
    private static final int ONE_SECOND = 1_000;

    private final StreamDestination destination;
    private final DirQueue forwardQueue;
    private final DirQueue retryQueue;
    private final CleanupDirQueue cleanupDirQueue;
    private final ForwardHttpPostConfig forwardHttpPostConfig;
    private final String destinationName;
    private final ForwardFileDestination failureDestination;
    private final ProxyServices proxyServices;

    public ForwardHttpPostDestination(final String destinationName,
                                      final StreamDestination destination,
                                      final CleanupDirQueue cleanupDirQueue,
                                      final ForwardHttpPostConfig forwardHttpPostConfig,
                                      final ProxyServices proxyServices,
                                      final DirQueueFactory dirQueueFactory,
                                      final ThreadConfig threadConfig,
                                      final DataDirProvider dataDirProvider,
                                      final SimplePathCreator simplePathCreator) {
        this.destination = destination;
        this.cleanupDirQueue = cleanupDirQueue;
        this.destinationName = destinationName;
        this.forwardHttpPostConfig = forwardHttpPostConfig;
        this.proxyServices = proxyServices;

        // TODO Add a health URL to check the forward dest every minute or so
        //  If it returns false we stop consuming from the forwardQueue/retryQueue until
        //  it returns true.

        final String safeDirName = DirUtil.makeSafeName(destinationName);
        final Path forwardingDir = dataDirProvider.get()
                .resolve(DirNames.FORWARDING).resolve(safeDirName);
        DirUtil.ensureDirExists(forwardingDir);

        forwardQueue = dirQueueFactory.create(
                forwardingDir.resolve("01_forward"),
                50,
                "forward - " + destinationName);
        retryQueue = dirQueueFactory.create(
                forwardingDir.resolve("02_retry"),
                51,
                "retry - " + destinationName);
        final DirQueueTransfer forwarding = new DirQueueTransfer(forwardQueue::next, this::forwardDir);
        final DirQueueTransfer retrying = new DirQueueTransfer(retryQueue::next, this::retryDir);
        proxyServices.addParallelExecutor(
                "forward - " + destinationName,
                () -> forwarding,
                threadConfig.getForwardThreadCount());
        proxyServices.addParallelExecutor(
                "retry - " + destinationName,
                () -> retrying,
                threadConfig.getForwardRetryThreadCount());

        // Create failure destination.
        failureDestination = setupFailureDestination(
                forwardHttpPostConfig, simplePathCreator, forwardingDir);
    }

    private ForwardFileDestination setupFailureDestination(final ForwardHttpPostConfig forwardHttpPostConfig,
                                                           final SimplePathCreator simplePathCreator,
                                                           final Path forwardingDir) {
        final ForwardFileDestination failureDestination;
        final Path failureDir = forwardingDir.resolve("03_failure");
        final String errorSubPathTemplate = forwardHttpPostConfig.getErrorSubPathTemplate();
        DirUtil.ensureDirExists(failureDir);
        failureDestination = new ForwardFileDestinationImpl(
                failureDir,
                getName() + " (failures)",
                errorSubPathTemplate,
                ForwardFileConfig.DEFAULT_TEMPLATING_MODE,
                simplePathCreator);
        return failureDestination;
    }

    @Override
    public void add(final Path sourceDir) {
        LOGGER.debug("'{}' - add(), dir: {}", destinationName, sourceDir);
        forwardQueue.add(sourceDir);
    }

    @Override
    public String getName() {
        return forwardHttpPostConfig.getName();
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

    /**
     * For testing
     */
    Path getFailureDir() {
        return failureDestination.getStoreDir();
    }

    private boolean forwardDir(final Path dir) {
        LOGGER.debug("'{}' - forwardDir(), dir: {}", destinationName, dir);
        try {
            try {
                final FileGroup fileGroup = new FileGroup(dir);
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
                cleanupDirQueue.add(dir);

                // Return true for success.
                return true;

            } catch (final Exception e) {
                LOGGER.error(() ->
                        "Error sending '" + FileUtil.getCanonicalPath(dir)
                        + "' to '" + destinationName + "': "
                        + LogUtil.exceptionMessage(getCause(e)) + ". " +
                        "(Enable DEBUG for stack trace.)");
                LOGGER.debug(e::getMessage, e);

                // Add to the errors
                addError(dir, e);

                // Have to assume we can retry
                boolean canRetry = true;
                if (e instanceof ForwardException forwardException) {
                    canRetry = forwardException.isRecoverable();
                }

                // Count errors.
                if (canRetry) {
                    final StroomDuration maxRetryAge = forwardHttpPostConfig.getMaxRetryAge();
                    if (maxRetryAge.isZero()) {
                        // No point creating the state file
                        canRetry = false;
                    } else {
                        // Read the state file
                        // If it exists get previous state and update it for next time
                        // If not exists, create it
                        final RetryState retryState = getAndUpdateRetryState(dir);
                        if (retryState != null) {
                            final Duration timeSinceFirstAttempt = retryState.getTimeSinceFirstAttempt();
                            LOGGER.debug("'{}' - maxRetries: {}, retryState: {}, timeSinceFirstAttempt: {}",
                                    destinationName, maxRetryAge, retryState, timeSinceFirstAttempt);
                            canRetry = TimeUtils.isLessThan(
                                    retryState.getTimeSinceFirstAttempt(),
                                    maxRetryAge.getDuration());
                        }
                    }
                }
                if (canRetry) {
                    LOGGER.debug("Retrying {}", dir);
                    // TODO Make a LoopingDirQueue that can go back to the head of the queue
                    addToRetryQueue(dir);
                } else {
                    LOGGER.debug("Adding {} to failure queue", dir);
                    // If we exceeded the max number of retries then move the data to the failure destination.
                    failureDestination.add(dir);
                }
            }
        } catch (final Throwable t) {
            LOGGER.error(t::getMessage, t);
        }

        // Failed, return false.
        return false;
    }

    private void addToRetryQueue(final Path dir) {
        // Add the dir to the retry queue ready to be tried again.
        retryQueue.add(dir);
    }

    private Throwable getCause(final Throwable e) {
        return e instanceof ForwardException forwardException
               && forwardException.getCause() != null
                ? forwardException.getCause()
                : e;
    }

    private void addError(final Path dir, final Exception e) {
        try {
            final StringBuilder sb = new StringBuilder(getCause(e).getClass().getSimpleName());
            if (e.getMessage() != null) {
                sb.append(" ");
                sb.append(e.getMessage().replace('\n', ' '));
            }
            sb.append("\n");
            final Path errorPath = getErrorLogFile(dir);
            final String line = sb.toString();
            Files.writeString(errorPath, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (final IOException e2) {
            LOGGER.error(e2::getMessage, e2);
        }
    }

    private RetryState getAndUpdateRetryState(final Path dir) {
        final Path retryStateFile = getRetryStateFile(dir);
        RetryState retryState = null;
        if (Files.isRegularFile(retryStateFile)) {
            try (RandomAccessFile reader = new RandomAccessFile(retryStateFile.toFile(), "rwd");
                    FileChannel channel = reader.getChannel()) {
                final ByteBuffer byteBuffer = ByteBuffer.allocate(RetryState.TOTAL_BYTES);
                // First read the existing value
                channel.read(byteBuffer);
                byteBuffer.flip();
                retryState = RetryState.deserialise(byteBuffer);
                final RetryState newRetryState = retryState.cloneAndUpdate();
                LOGGER.debug("'{}' - retryStateFile: {}, retryState: {}, newRetryState: {}",
                        destinationName, retryStateFile, retryState, newRetryState);
                retryState = newRetryState;

                // Get ready for writing back to the file
                channel.position(0);
                byteBuffer.flip();
                retryState.serialise(byteBuffer);
                byteBuffer.flip();
                final int writeCount = channel.write(byteBuffer);
                if (writeCount != RetryState.TOTAL_BYTES) {
                    throw new IllegalStateException(LogUtil.message("Unexpected writeCount {}, expecting {}",
                            writeCount, RetryState.TOTAL_BYTES));
                }
            } catch (IOException e) {
                LOGGER.error(() ->
                        LogUtil.message("Error updating retry file {}: {}", e.getMessage(), e));
            }
        } else {
            if (Files.exists(retryStateFile)) {
                LOGGER.error(() -> LogUtil.message("{} exists but is not a file, ignoring", retryStateFile));
            } else {
                // Not exists so write a new one
                retryState = RetryState.initial();
                try {
                    Files.write(retryStateFile,
                            retryState.serialise(),
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE);
                } catch (IOException e) {
                    LOGGER.error(e::getMessage, e);
                }
            }
        }
        return retryState;
    }

    private RetryState getRetryState(final Path dir) {
        final Path retryStateFile = getRetryStateFile(dir);
        if (Files.isRegularFile(retryStateFile)) {
            try {
                final byte[] bytes = Files.readAllBytes(retryStateFile);
                return RetryState.deserialise(bytes);
            } catch (IOException e) {
                LOGGER.error(() ->
                        LogUtil.message("Error reading retry file {}: {}", LogUtil.exceptionMessage(e), e));
                return null;
            }
        } else {
            if (Files.exists(retryStateFile)) {
                LOGGER.error("{} exists but is not a file, ignoring", retryStateFile);
            } else {
                LOGGER.error("{} does not exist, ignoring", retryStateFile);
            }
            return null;
        }
    }

//    private RetryState getRetryState(final Path dir) {
//        final Path retryStateFile = dir.resolve(RETRY_STATE_FILE);
//        try {
//            if (Files.isRegularFile(retryStateFile)) {
//                final byte[] bytes = Files.readAllBytes(retryStateFile);
//                return RetryState.deserialise(bytes);
//            } else {
//                if (Files.exists(retryStateFile)) {
//                    throw new RuntimeException(LogUtil.message("{} exists but is not a file", retryStateFile));
//                }
//                return null;
//            }
//        } catch (final IOException e) {
//            LOGGER.error(e::getMessage, e);
//            return null;
//        }
//    }

//    private void updateRetryState(final Path dir, final RetryState existingRetryState) {
//        final Path retryStateFile = dir.resolve(RETRY_STATE_FILE);
//        try {
//            final RetryState newRetryState = RetryState.cloneAndUpdate(existingRetryState);
//            final byte[] bytes = newRetryState.serialise();
//            Files.write(retryStateFile,
//                    bytes,
//                    StandardOpenOption.CREATE,
//                    StandardOpenOption.WRITE,
//                    StandardOpenOption.TRUNCATE_EXISTING);
//
//            if (Files.isRegularFile(retryStateFile)) {
//                final byte[] bytes = Files.readAllBytes(retryStateFile);
//                return RetryState.deserialise(bytes);
//            } else {
//                if (Files.exists(retryStateFile)) {
//                    throw new RuntimeException(LogUtil.message("{} exists but is not a file", retryStateFile));
//                }
//                return null;
//            }
//        } catch (final IOException e) {
//            LOGGER.error(e::getMessage, e);
//            return null;
//        }
//    }

//    private int countErrors(final Path dir) {
//        int count = 0;
//        try {
//            final Path errorPath = dir.resolve(ERROR_LOG);
//            if (Files.isRegularFile(errorPath)) {
//                try (final BufferedReader bufferedReader = Files.newBufferedReader(errorPath)) {
//                    String line = bufferedReader.readLine();
//                    while (line != null) {
//                        count++;
//                        line = bufferedReader.readLine();
//                    }
//                }
//            }
//        } catch (final IOException e) {
//            LOGGER.error(e::getMessage, e);
//        }
//        return count;
//    }

    private void retryDir(final Path dir) {
        LOGGER.debug("'{}' - retryDir(), dir: {}", destinationName, dir);

        delayRetry(dir);

        if (!proxyServices.isShuttingDown()) {
            forwardDir(dir);
        } else {
            // No point trying to forward now
            throw new RuntimeException("Proxy is shutting down");
        }
    }

    private void delayRetry(final Path dir) {
        final RetryState retryState = getRetryState(dir);
        final long lastAttemptEpochMs = NullSafe.getOrElse(
                retryState,
                RetryState::lastAttemptEpochMs,
                0L);
        final int attempts = NullSafe.getOrElse(retryState, RetryState::attempts, -1);
        final StroomDuration retryDelay = forwardHttpPostConfig.getRetryDelay();
        final double retryDelayGrowthFactor = forwardHttpPostConfig.getRetryDelayGrowthFactor();
        final long retryDelayMs;
        if (retryDelayGrowthFactor > 1 && retryState != null) {
            retryDelayMs = Math.min(
                    (long) (retryDelay.toMillis() * Math.pow(retryDelayGrowthFactor, attempts)),
                    forwardHttpPostConfig.getMaxRetryDelay().toMillis());
        } else {
            retryDelayMs = retryDelay.toMillis();
        }

        final long notBeforeEpochMs = lastAttemptEpochMs + retryDelayMs;
        long delay = notBeforeEpochMs - System.currentTimeMillis();

        LOGGER.debug(() -> LogUtil.message("'{}' - notBefore {}, retryDelayMs {}, attempts: {}",
                destinationName,
                Instant.ofEpochMilli(notBeforeEpochMs),
                Duration.ofMillis(retryDelayMs),
                attempts));

        while (delay > 0 && !proxyServices.isShuttingDown()) {
            final long sleepMs = Math.min(ONE_SECOND, delay);
            LOGGER.debug("Sleeping for {}ms", sleepMs);
            ThreadUtil.sleep(sleepMs);
            if (sleepMs == ONE_SECOND) {
                delay = notBeforeEpochMs - System.currentTimeMillis();
            } else {
                break;
            }
        }
    }

    private Path getRetryStateFile(final Path dir) {
        return Objects.requireNonNull(dir).resolve(RETRY_STATE_FILENAME);
    }

    private Path getErrorLogFile(final Path dir) {
        return Objects.requireNonNull(dir).resolve(ERROR_LOG_FILENAME);
    }
}
