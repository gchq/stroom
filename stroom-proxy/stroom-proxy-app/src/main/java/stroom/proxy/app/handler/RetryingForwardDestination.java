package stroom.proxy.app.handler;

import stroom.proxy.app.DataDirProvider;
import stroom.proxy.repo.ParallelExecutor;
import stroom.proxy.repo.ProxyServices;
import stroom.proxy.repo.store.FileStores;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.date.DateUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.time.StroomDuration;
import stroom.util.time.TimeUtils;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Wraps another {@link ForwardDestination}, adding forwarding and retry queues plus the retry logic.
 * Directories passed to {@link RetryingForwardDestination#add(Path)} will simply be placed on the
 * forward queue. A pool of threads will consume from this queue, isolating the caller from any failures
 * when calling {@link ForwardDestination#add(Path)} on the delegate. Failures will result in the directory
 * being placed on the retry queue, depending on the {@link ForwardQueueConfig}.
 */
public class RetryingForwardDestination implements ForwardDestination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RetryingForwardDestination.class);
    private static final int FORWARD_ORDER = 50;
    private static final int RETRY_ORDER = 51;

    /**
     * File to hold the log of all forwarding errors from all forward attempts for this {@link FileGroup}
     */
    static final String ERROR_LOG_FILENAME = "error.log";
    /**
     * Holds the state relating to retries. Held in binary form.
     */
    private static final String RETRY_STATE_FILENAME = "retry.state";
    private static final int ONE_SECOND_IN_MS = 1_000;

    private final ForwardQueueConfig forwardQueueConfig;
    private final ForwardDestination delegateDestination;
    private final ProxyServices proxyServices;

    private final DirQueue forwardQueue;
    private final DirQueue retryQueue;
    private final ParallelExecutor forwardExecutor;
    private final ParallelExecutor retryExecutor;
    private final ForwardFileDestination failureDestination;
    private final String destinationName;
    // Assumed to be live on boot
    private final AtomicBoolean lastLiveCheckResult = new AtomicBoolean(true);
    private final Runnable delayForwardingFunc;
    private final FileStores fileStores;

    public RetryingForwardDestination(final ForwardQueueConfig forwardQueueConfig,
                                      final ForwardDestination delegateDestination,
                                      final DataDirProvider dataDirProvider,
                                      final PathCreator pathCreator,
                                      final DirQueueFactory dirQueueFactory,
                                      final ProxyServices proxyServices,
                                      final FileStores fileStores) {

        this.forwardQueueConfig = Objects.requireNonNull(forwardQueueConfig);
        this.delegateDestination = Objects.requireNonNull(delegateDestination);
        this.proxyServices = Objects.requireNonNull(proxyServices);
        this.fileStores = Objects.requireNonNull(fileStores);

        this.destinationName = Objects.requireNonNull(delegateDestination.getName());
        final String safeDirName = DirUtil.makeSafeName(destinationName);
        final Path forwardingDir = dataDirProvider.get()
                .resolve(DirNames.FORWARDING).resolve(safeDirName);
        DirUtil.ensureDirExists(forwardingDir);

        forwardQueue = dirQueueFactory.create(
                forwardingDir.resolve("01_forward"),
                FORWARD_ORDER,
                "forward - " + destinationName);
        retryQueue = dirQueueFactory.create(
                forwardingDir.resolve("02_retry"),
                RETRY_ORDER,
                "retry - " + destinationName);

        final DirQueueTransfer forwarding = new DirQueueTransfer(
                forwardQueue::next, this::forwardDir);
        final DirQueueTransfer retrying = new DirQueueTransfer(
                retryQueue::next, this::retryDir);
        forwardExecutor = proxyServices.addParallelExecutor(
                "forward - " + destinationName,
                () -> forwarding,
                forwardQueueConfig.getForwardThreadCount());
        retryExecutor = proxyServices.addParallelExecutor(
                "retry - " + destinationName,
                () -> retrying,
                forwardQueueConfig.getForwardRetryThreadCount());

        // Create failure destination.
        failureDestination = setupFailureDestination(
                forwardQueueConfig, pathCreator, forwardingDir);
        delayForwardingFunc = createForwardDelayFunc(forwardQueueConfig);

        if (delegateDestination.hasLivenessCheck()) {
            proxyServices.addFrequencyExecutor(
                    "liveness - " + destinationName,
                    () -> this::doLivenessCheck,
                    forwardQueueConfig.getLivenessCheckInterval().toMillis());
        }
    }

    private Runnable createForwardDelayFunc(final ForwardQueueConfig forwardQueueConfig) {
        final Runnable delayForwardingFunc;
        final StroomDuration forwardDelay = Objects.requireNonNullElse(
                forwardQueueConfig.getForwardDelay(), StroomDuration.ZERO);
        if (forwardDelay.isZero()) {
            delayForwardingFunc = () -> {
                // No delay, so noop
            };
        } else {
            LOGGER.warn("'{}' - Using a forwarding delay of {} on every forward. " +
                        "If you see this message in production, set the property 'forwardDelay' to zero, " +
                        "unless you actually want the delay.",
                    destinationName, forwardDelay);
            delayForwardingFunc = this::delayPost;
        }
        return delayForwardingFunc;
    }

    private void delayPost() {
        try {
            final Instant startTime = Instant.now();
            final Duration forwardDelay = forwardQueueConfig.getForwardDelay().getDuration();
            LOGGER.trace("'{}' - adding delay {}", destinationName, forwardDelay);
            final long notBeforeEpochMs = startTime.plus(forwardDelay).toEpochMilli();
            long delay = notBeforeEpochMs - System.currentTimeMillis();

            // Loop in case the delay is long and proxy shuts down part way through
            while (delay > 0
                   && !proxyServices.isShuttingDown()
                   && !Thread.currentThread().isInterrupted()) {

                final long sleepMs = Math.min(ONE_SECOND_IN_MS, delay);
                ThreadUtil.sleep(sleepMs);
                if (sleepMs == ONE_SECOND_IN_MS) {
                    delay = notBeforeEpochMs - System.currentTimeMillis();
                } else {
                    break;
                }
            }
        } catch (final Exception e) {
            // Swallow as this is only for testing
            LOGGER.error("Error while delaying the forward: {}", LogUtil.exceptionMessage(e), e);
        }
    }

    private synchronized void doLivenessCheck() {
        boolean isLive;
        String msg = null;
        try {
            isLive = delegateDestination.performLivenessCheck();
            LOGGER.debug("'{}' - isLive: {}", destinationName, isLive);
        } catch (final Exception e) {
            LOGGER.debug("Error performing liveness check", e);
            isLive = false;
            msg = e.getMessage();
        }

        final boolean hasChanged = lastLiveCheckResult.compareAndSet(!isLive, isLive);
        if (hasChanged) {
            if (isLive) {
                LOGGER.info("'{}' - destination liveness check passed, resuming all forwarding and retries",
                        destinationName);
            } else {
                LOGGER.warn("'{}' - destination liveness check failed, pausing all forwarding and retries. {}",
                        destinationName, msg);
            }
        } else {
            if (!isLive) {
                LOGGER.warn("'{}' - destination liveness check still failing. {}", destinationName, msg);
            }
        }
        // Let the executor worry if it has already been set or not
        forwardExecutor.setPauseState(!isLive);
        retryExecutor.setPauseState(!isLive);
    }

    @Override
    public void add(final Path sourceDir) {
        if (forwardQueueConfig.isQueueAndRetryEnabled()) {
            addWithQueueAndRetry(sourceDir);
        } else {
            addDirect(sourceDir);
        }
    }

    private void addWithQueueAndRetry(final Path sourceDir) {
        LOGGER.debug("'{}' - addWithQueueAndRetry(), dir: {}", destinationName, sourceDir);
        // Add to the forward queue to isolate the caller from failures in the delegate.
        // This will move sourceDir into the queue dir
        forwardQueue.add(sourceDir);
    }

    private void addDirect(final Path sourceDir) {
        LOGGER.debug("'{}' - addDirect(), dir: {}", destinationName, sourceDir);
        // Delay the forward, if one is configured. If not configured, this is a noop.
        delayForwardingFunc.run();
        // Now pass it directly to the delegate destination with no queuing/retrying
        try {
            delegateDestination.add(sourceDir);
        } catch (final Exception e) {
            LOGGER.error(
                    "Error sending '" + FileUtil.getCanonicalPath(sourceDir)
                    + "' to " + getDestinationType() + " forward destination '"
                    + destinationName + "' "
                    + LogUtil.exceptionMessage(getCause(e))
                    + (e instanceof final ForwardException fe
                            ? " Feed: '" + fe.getFeedName() + "'. HTTP code: " + fe.getHttpResponseCode() + ". "
                            : "")
                    + " Will not retry, moving to failure destination "
                    + failureDestination.getStoreDir(), e);
            failureDestination.add(sourceDir);
        }
    }

    @Override
    public String getName() {
        return destinationName;
    }

    @Override
    public DestinationType getDestinationType() {
        return delegateDestination.getDestinationType();
    }

    @Override
    public String getDestinationDescription() {
        return delegateDestination.getDestinationDescription();
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

    private ForwardFileDestination setupFailureDestination(final ForwardQueueConfig forwardQueueConfig,
                                                           final PathCreator simplePathCreator,
                                                           final Path forwardingDir) {
        final ForwardFileDestination failureDestination;
        final Path failureDir = forwardingDir.resolve("03_failure");
        final PathTemplateConfig errorSubPathTemplate = forwardQueueConfig.getErrorSubPathTemplate();
        DirUtil.ensureDirExists(failureDir);
        failureDestination = new ForwardFileDestinationImpl(
                failureDir,
                destinationName + " (failures)",
                errorSubPathTemplate,
                null,
                null,
                simplePathCreator);
        fileStores.add(FORWARD_ORDER, "forward - " + destinationName + " - failure", failureDir);
        return failureDestination;
    }

    private boolean forwardDir(final Path dir) {
        LOGGER.debug("'{}' - forwardDir(), dir: {}", destinationName, dir);
        try {
            // Delay the forward, if one is configured. If not configured, this is a noop.
            delayForwardingFunc.run();
            try {
                delegateDestination.add(dir);
                // Return true for success.
                return true;
            } catch (final Exception e) {
                // Add to the errors
                addError(dir, e);

                // Have to assume we can retry
                boolean canRetry = true;
                if (e instanceof final ForwardException forwardException) {
                    canRetry = forwardException.isRecoverable();
                }
                final int attempts;
                final Duration retryAge;

                // Count errors.
                if (canRetry) {
                    final StroomDuration maxRetryAge = forwardQueueConfig.getMaxRetryAge();
                    if (maxRetryAge.isZero()) {
                        // No point creating the state file
                        canRetry = false;
                        attempts = 1;
                        retryAge = Duration.ZERO;
                    } else {
                        // Read the state file
                        // If it exists get previous state and update it for next time
                        // If not exists, create it
                        final RetryState retryState = getAndUpdateRetryState(dir);
                        attempts = retryState.attempts();
                        retryAge = retryState.getTimeSinceFirstAttempt();
                        if (retryState.hasReachMaxAttempts()) {
                            LOGGER.debug("'{}' - Max retries of {} reached, adding {} to failure queue",
                                    destinationName, RetryState.MAX_ATTEMPTS, dir);
                            canRetry = false;
                        } else {
                            final Duration timeSinceFirstAttempt = retryState.getTimeSinceFirstAttempt();
                            LOGGER.debug("'{}' - maxRetries: {}, retryState: {}, timeSinceFirstAttempt: {}",
                                    destinationName, maxRetryAge, retryState, timeSinceFirstAttempt);
                            canRetry = TimeUtils.isLessThan(
                                    retryState.getTimeSinceFirstAttempt(),
                                    maxRetryAge.getDuration());
                        }
                    }
                } else {
                    attempts = 1;
                    retryAge = Duration.ZERO;
                }
                final Supplier<String> msgSupplier = () ->
                        "Error sending '" + FileUtil.getCanonicalPath(dir)
                        + "' to " + getDestinationType() + " forward destination '"
                        + destinationName + "' "
                        + "(attempts: " + attempts + ", retryAge: " + retryAge + "): "
                        + e.getMessage() + ". "
                        + (e instanceof final ForwardException fe
                                ? "Feed: '" + fe.getFeedName() + "'. HTTP code: " + fe.getHttpResponseCode() + ". "
                                : "")
                        + "(Enable DEBUG for stack trace.) ";

                if (canRetry) {
                    // TODO Consider making a LoopingDirQueue that can go back to the head of the queue,
                    //  e.g. when we have variable delay times due to the growth factor, rather than
                    //  just sleeping till the delay ends, move on to the next item in the queue in case
                    //  it is shorter. Or we hava a java delayQueue.
                    addToRetryQueue(dir);
                    LOGGER.error(() -> msgSupplier.get()
                                       + " Adding to retry queue.");
                    LOGGER.debug(() -> msgSupplier.get()
                                       + " Adding to retry queue.", e);
                } else {
                    // If we exceeded the max number of retries then move the data to the failure destination.
                    moveToFailureDestination(dir, msgSupplier, e);
                }
            }
        } catch (final Throwable t) {
            LOGGER.error(t::getMessage, t);
        }
        // Failed, return false.
        return false;
    }

    private void moveToFailureDestination(final Path dir,
                                          final Supplier<String> msgSupplier,
                                          final Throwable e) {
        try {
            final Path retryStateFile = getRetryStateFile(dir);
            try {
                FileUtil.deleteFile(retryStateFile);
            } catch (final Exception e2) {
                // Only deleting as it is no longer needed. The error.log file contains info about each attempt
                // and retry.state is binary. Thus, we don't really care if we can't delete it.
                LOGGER.debug("Unable to delete retry state file {}: {}",
                        retryStateFile, LogUtil.exceptionMessage(e2), e2);
            }
            failureDestination.add(dir);
            LOGGER.error(() -> msgSupplier.get()
                               + " Will not retry, moving to failure destination "
                               + failureDestination.getStoreDir());
            LOGGER.debug(() -> msgSupplier.get()
                               + " Will not retry, moving to failure destination "
                               + failureDestination.getStoreDir(), e);
        } catch (final Exception e3) {
            LOGGER.error("Error moving '{}' to {}", dir, failureDestination, e3);
        }
    }

    private void addToRetryQueue(final Path dir) {
        // Add the dir to the retry queue ready to be tried again.
        retryQueue.add(dir);
    }

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
        // Read the state file, so we know when the item was last retried and how many
        // goes we have already had.
        final RetryState retryState = getRetryState(dir);
        final long lastAttemptEpochMs = NullSafe.getOrElse(
                retryState,
                RetryState::lastAttemptEpochMs,
                0L);
        final short attempts = NullSafe.getOrElse(retryState, RetryState::attempts, (short) -1);
        final StroomDuration retryDelay = forwardQueueConfig.getRetryDelay();
        final double retryDelayGrowthFactor = forwardQueueConfig.getRetryDelayGrowthFactor();
        final long retryDelayMs;
        if (retryDelayGrowthFactor > 1 && retryState != null) {
            retryDelayMs = Math.min(
                    (long) (retryDelay.toMillis() * Math.pow(retryDelayGrowthFactor, attempts)),
                    forwardQueueConfig.getMaxRetryDelay().toMillis());
        } else {
            retryDelayMs = retryDelay.toMillis();
        }

        final long nowEpochMs = System.currentTimeMillis();
        final long notBeforeEpochMs = lastAttemptEpochMs + retryDelayMs;
        long delay = notBeforeEpochMs > nowEpochMs
                ? notBeforeEpochMs - nowEpochMs
                : 0L;

        LOGGER.debug(() -> LogUtil.message("'{}' - notBefore {}, retryDelayMs {}, attempts: {}",
                destinationName,
                Instant.ofEpochMilli(notBeforeEpochMs),
                Duration.ofMillis(retryDelayMs),
                attempts));

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Sleeping for {}ms, lastAttemptEpoch: {}, dir: {}",
                    Duration.ofMillis(delay), Instant.ofEpochMilli(lastAttemptEpochMs), dir);
        }
        while (delay > 0
               && !proxyServices.isShuttingDown()
               && !Thread.currentThread().isInterrupted()) {

            final long sleepMs = Math.min(ONE_SECOND_IN_MS, delay);
            try {
                Thread.sleep(sleepMs);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.debug("Interpreted during sleep");
                break;
            }
            if (sleepMs == ONE_SECOND_IN_MS) {
                delay = notBeforeEpochMs - System.currentTimeMillis();
            } else {
                break;
            }
        }
    }

    private Throwable getCause(final Throwable e) {
        return e instanceof final ForwardException forwardException
               && forwardException.getCause() != null
                ? forwardException.getCause()
                : e;
    }

    private void addError(final Path dir, final Exception e) {
        try {
            final StringBuilder sb = new StringBuilder();
            sb.append(DateUtil.createNormalDateTimeString());
            sb.append(" - ");
            sb.append(getCause(e).getClass().getSimpleName());
            if (e.getMessage() != null) {
                sb.append(" - ");
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

    private RetryState getRetryState(final Path dir) {
        final Path retryStateFile = getRetryStateFile(dir);
        if (Files.isRegularFile(retryStateFile)) {
            try {
                final byte[] bytes = Files.readAllBytes(retryStateFile);
                return RetryState.deserialise(bytes);
            } catch (final IOException e) {
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

    /**
     * @return The {@link RetryState} after initialising/updating it for the attempt that has just
     * happened.
     */
    private RetryState getAndUpdateRetryState(final Path dir) {
        final Path retryStateFile = getRetryStateFile(dir);
        RetryState updatedRetryState;
        if (Files.isRegularFile(retryStateFile)) {
            try (final RandomAccessFile reader = new RandomAccessFile(retryStateFile.toFile(), "rwd");
                    final FileChannel channel = reader.getChannel()) {
                final ByteBuffer byteBuffer = ByteBuffer.allocate(RetryState.TOTAL_BYTES);
                // First read the existing value
                channel.read(byteBuffer);
                byteBuffer.flip();
                final RetryState existingRetryState = RetryState.deserialise(byteBuffer);
                updatedRetryState = existingRetryState.cloneAndUpdate();
                LOGGER.debug("'{}' - retryStateFile: {}, existingRetryState: {}, updatedRetryState: {}",
                        destinationName, retryStateFile, existingRetryState, updatedRetryState);

                // Get ready for writing back to the file
                channel.position(0);
                byteBuffer.flip();
                updatedRetryState.serialise(byteBuffer);
                byteBuffer.flip();
                final int writeCount = channel.write(byteBuffer);
                if (writeCount != RetryState.TOTAL_BYTES) {
                    throw new IllegalStateException(LogUtil.message("Unexpected writeCount {}, expecting {}",
                            writeCount, RetryState.TOTAL_BYTES));
                }
            } catch (final IOException e) {
                LOGGER.error("'{}' - Error updating retry file '{}'. " +
                             "Retry state cannot be updated so this directory will be " +
                             "retried indefinitely. Error: {}",
                        destinationName, retryStateFile, LogUtil.exceptionMessage(e), e);
                updatedRetryState = RetryState.initial();
            }
        } else {
            updatedRetryState = RetryState.initial();
            if (Files.exists(retryStateFile)) {
                LOGGER.error("'{}' - Retry state file '{}' exists but is not a file. " +
                             "Retry state cannot be updated so this directory will be " +
                             "retried indefinitely. ", destinationName, retryStateFile);
                // Means we will never update the state, but not a lot we can do
            } else {
                try {
                    Files.write(retryStateFile,
                            updatedRetryState.serialise(),
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE);
                } catch (final IOException e) {
                    LOGGER.error(e::getMessage, e);
                }
            }
        }
        return updatedRetryState;
    }

    private Path getRetryStateFile(final Path dir) {
        return Objects.requireNonNull(dir).resolve(RETRY_STATE_FILENAME);
    }

    private Path getErrorLogFile(final Path dir) {
        return Objects.requireNonNull(dir).resolve(ERROR_LOG_FILENAME);
    }
}
