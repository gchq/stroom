package stroom.test.common;

import stroom.test.common.DynamicTestBuilder.InitialBuilder;
import stroom.util.NullSafe;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.junit.jupiter.api.DynamicTest;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Useful utility methods for junit tests
 */
public class TestUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestUtil.class);

    private TestUtil() {
        // Static Utils only
    }

    /**
     * A builder for creating a Junit5 {@link DynamicTest} {@link Stream} for use with the
     * {@link org.junit.jupiter.api.TestFactory} annotation.
     * Simplifies the testing of multiple inputs to the same test method.
     * See TestTestUtil for examples of how to use this builder.
     * NOTE: @{@link org.junit.jupiter.api.BeforeEach} and @{@link org.junit.jupiter.api.AfterEach}
     * are only called at the {@link org.junit.jupiter.api.TestFactory} level, NOT for each
     * {@link DynamicTest}.
     */
    public static InitialBuilder buildDynamicTestStream() {
        return new InitialBuilder();
    }

    /**
     * Logs to info message followed by ':\n', followed by an ASCII table
     * of the map entries
     */
    public static <K, V> void dumpMapToInfo(final String message,
                                            final Map<K, V> map) {
        LOGGER.info("{}:\n{}", message, AsciiTable.from(map));
    }

    /**
     * Logs to debug message followed by ':\n', followed by an ASCII table
     * of the map entries
     */
    public static <K, V> void dumpMapToDebug(final String message,
                                             final Map<K, V> map) {
        LOGGER.debug("{}:\n{}", message, AsciiTable.from(map));
    }

    /**
     * Repeatedly call test with pollFrequency until it returns true, or timeout is reached.
     * If timeout is reached before test returns true a {@link RuntimeException} is thrown.
     *
     * @param valueSupplier   Supplier of the value to test. This will be called repeatedly until
     *                        its return value match requiredValue, or timeout is reached.
     * @param requiredValue   The value that valueSupplier is required to ultimately return.
     * @param messageSupplier Supplier of the name of the thing being waited for.
     * @param timeout         The timeout duration after which waitForIt will give up and throw
     *                        a {@link RuntimeException}.
     * @param pollFrequency   The time between calls to valueSupplier.
     * @param showProgressFrequency The time between progress update logged to info, set to zero to not show any.
     * @throws stroom.util.concurrent.UncheckedInterruptedException If sleep is interrupted
     */
    public static <T> void waitForIt(final Supplier<T> valueSupplier,
                                     final T requiredValue,
                                     final Supplier<String> messageSupplier,
                                     final Duration timeout,
                                     final Duration pollFrequency,
                                     final Duration showProgressFrequency) {

        final Instant startTime = Instant.now();
        final Instant endTime = startTime.plus(timeout);
        Instant lastProgressUpdateTime = null;
        T currValue = null;
        boolean firstIteration = true;
        while (Instant.now().isBefore(endTime)) {
            currValue = valueSupplier.get();
            if (Objects.equals(currValue, requiredValue)) {
                LOGGER.debug("Waited {}", Duration.between(startTime, Instant.now()));
                return;
            } else {
                if (firstIteration) {
                    firstIteration = false;
                } else {
                    if (!showProgressFrequency.equals(Duration.ZERO)) {
                        final Duration timeSinceLastUpdate = Duration.between(
                                NullSafe.coalesce(lastProgressUpdateTime, startTime)
                                        .orElseThrow(), // startTime never null, so should not happen
                                Instant.now());

                        if (timeSinceLastUpdate.compareTo(showProgressFrequency) > 0) {
                            LOGGER.info("Still waiting for '{}' to be '{} ({})'. " +
                                            "Last value '{} ({})'. Waited {} so far.",
                                    messageSupplier.get(),
                                    requiredValue,
                                    NullSafe.getOrElse(
                                            requiredValue, Object::getClass, Class::getSimpleName, "null"),
                                    currValue,
                                    NullSafe.getOrElse(
                                            currValue, Object::getClass, Class::getSimpleName, "null"),
                                    Duration.between(startTime, Instant.now()));
                            lastProgressUpdateTime = Instant.now();
                        }
                    }
                }
                ThreadUtil.sleep(pollFrequency.toMillis());
            }
        }

        // Timed out so throw
        throw new RuntimeException(LogUtil.message("Timed out (timeout: {}) waiting for '{}' to be '{}'. " +
                        "Last value '{}'",
                timeout,
                messageSupplier.get(),
                requiredValue,
                currValue));
    }

    /**
     * Repeatedly call test with pollFrequency until it returns true, or timeout is reached.
     * If timeout is reached before test returns true a {@link RuntimeException} is thrown.
     * A default timeout of 5s is used with a default pollFrequency of 1ms.
     *
     * @param valueSupplier   Supplier of the value to test. This will be called repeatedly until
     *                        its return value match requiredValue, or timeout is reached.
     * @param requiredValue   The value that valueSupplier is required to ultimately return.
     * @param messageSupplier Supplier of the name of the thing being waited for.
     */
    public static <T> void waitForIt(final Supplier<T> valueSupplier,
                                     final T requiredValue,
                                     final Supplier<String> messageSupplier) {
        waitForIt(
                valueSupplier,
                requiredValue,
                messageSupplier,
                Duration.ofSeconds(5),
                Duration.ofMillis(1),
                Duration.ofSeconds(1));
    }

    /**
     * Repeatedly call test with pollFrequency until it returns true, or timeout is reached.
     * If timeout is reached before test returns true a {@link RuntimeException} is thrown.
     * A default timeout of 5s is used with a default pollFrequency of 1ms.
     *
     * @param valueSupplier Supplier of the value to test. This will be called repeatedly until
     *                      its return value match requiredValue, or timeout is reached.
     * @param requiredValue The value that valueSupplier is required to ultimately return.
     * @param message       The name of the thing being waited for.
     */
    public static <T> void waitForIt(final Supplier<T> valueSupplier,
                                     final T requiredValue,
                                     final String message) {
        waitForIt(
                valueSupplier,
                requiredValue,
                () -> message,
                Duration.ofSeconds(5),
                Duration.ofMillis(1),
                Duration.ofSeconds(1));
    }
}
