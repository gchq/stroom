package stroom.test.common;

import stroom.test.common.DynamicTestBuilder.InitialBuilder;
import stroom.util.NullSafe;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.AsciiTable.Column;
import stroom.util.logging.AsciiTable.TableBuilder;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;

import org.junit.jupiter.api.DynamicTest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
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
        LOGGER.info("{}:\n{}", message, AsciiTable.fromMap(map));
    }

    /**
     * Logs to debug message followed by ':\n', followed by an ASCII table
     * of the map entries
     */
    public static <K, V> void dumpMapToDebug(final String message,
                                             final Map<K, V> map) {
        LOGGER.debug("{}:\n{}", message, AsciiTable.fromMap(map));
    }

    /**
     * Returns map but without keysToRemove
     */
    public static <K, V> Map<K, V> mapWithoutKeys(final Map<K, V> map, final K... keysToRemove) {
        Objects.requireNonNull(map);
        if (keysToRemove == null) {
            return map;
        } else {
            final Set<K> removeKeySet = Set.of(keysToRemove);
            return map.entrySet()
                    .stream()
                    .filter(entry -> !removeKeySet.contains(entry.getKey()))
                    .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
        }
    }

    /**
     * Repeatedly call test with pollFrequency until it returns true, or timeout is reached.
     * If timeout is reached before test returns true a {@link RuntimeException} is thrown.
     *
     * @param valueSupplier         Supplier of the value to test. This will be called repeatedly until
     *                              its return value match requiredValue, or timeout is reached.
     * @param requiredValue         The value that valueSupplier is required to ultimately return.
     * @param messageSupplier       Supplier of the name of the thing being waited for.
     * @param timeout               The timeout duration after which waitForIt will give up and throw
     *                              a {@link RuntimeException}.
     * @param pollFrequency         The time between calls to valueSupplier.
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
                Duration.ofMinutes(1),
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

    public static void comparePerformance(final int rounds,
                                          final int iterations,
                                          final Consumer<String> outputConsumer,
                                          final TimedCase... testCases) {
        comparePerformance(rounds, iterations, null, outputConsumer, testCases);
    }

    /**
     * @param rounds
     * @param iterations
     * @param setup          Run before each test case in each round
     * @param outputConsumer
     * @param testCases
     */
    public static void comparePerformance(final int rounds,
                                          final int iterations,
                                          final TestSetup setup,
                                          final Consumer<String> outputConsumer,
                                          final TimedCase... testCases) {

        final Map<String, List<Duration>> summaryMap = new HashMap<>();
        Arrays.stream(testCases)
                .map(TimedCase::getName)
                .forEach(name ->
                        summaryMap.computeIfAbsent(name, k -> new ArrayList<>()));

        Objects.requireNonNull(testCases);
        for (int i = 0; i < rounds; i++) {
            final int round = i + 1;
            outputConsumer.accept("Starting round " + round);
            for (final TimedCase testCase : testCases) {
                final String name = testCase.getName();
                final MeasuredWork work = testCase.getWork();
                if (setup != null) {
                    LOGGER.debug("Running setup");
                    setup.run(round, iterations);
                }
                final Duration duration = DurationTimer.measure(() -> {
                    work.run(round, iterations);
                });
                summaryMap.get(name).add(duration);
                outputConsumer.accept(LogUtil.message("Completed '{}' (round {}) in {}", name, round, duration));
            }
        }
        final List<Entry<String, List<Duration>>> summaryData = Arrays.stream(testCases)
                .map(testCase -> Map.entry(testCase.name, summaryMap.get(testCase.name)))
                .toList();

        final TableBuilder<Entry<String, List<Duration>>> tableBuilder = AsciiTable.builder(summaryData)
                .withColumn(Column.of("Name", Entry::getKey));

        for (int round = 1; round <= rounds; round++) {
            final int idx = round - 1;
            tableBuilder.withColumn(Column.durationNanos("Round " + round, entry ->
                    entry.getValue().get(idx)));
        }
        final String tableStr = tableBuilder
                .withColumn(Column.durationNanos("Min", entry ->
                        entry.getValue().stream().min(Duration::compareTo).get()))
                .withColumn(Column.durationNanos("Max", entry ->
                        entry.getValue().stream().max(Duration::compareTo).get()))
                .withColumn(Column.durationNanos("Avg over rounds", entry ->
                        Duration.ofNanos((long) entry.getValue()
                                .stream()
                                .mapToLong(Duration::toNanos)
                                .average()
                                .getAsDouble())))
                .withColumn(Column.decimal("Per iter (last round)", entry ->
                        entry.getValue().get(rounds - 1).toNanos() / iterations, 0))
                .build();
        outputConsumer.accept(LogUtil.message("Summary (iterations: {}, values in nanos):\n{}",
                ModelStringUtil.formatCsv(iterations),
                tableStr));
    }

    public static interface TestSetup {

        void run(final int rounds, final int iterations);
    }

    public static class TimedCase {

        private final String name;
        private final MeasuredWork work;

        private TimedCase(final String name, final MeasuredWork work) {
            this.name = name;
            this.work = work;
        }

        public static TimedCase of(final String name, final MeasuredWork work) {
            return new TimedCase(name, work);
        }

        public String getName() {
            return name;
        }

        public MeasuredWork getWork() {
            return work;
        }
    }


    // --------------------------------------------------------------------------------


    public interface MeasuredWork {

        /**
         * @param round      One based
         * @param iterations Number of iterations to perform in the work
         */
        void run(final int round, final int iterations);
    }
}
