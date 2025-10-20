package stroom.test.common;

import stroom.test.common.DynamicTestBuilder.InitialBuilder;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.io.FileUtil;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.AsciiTable.Column;
import stroom.util.logging.AsciiTable.TableBuilder;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.inject.Provider;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
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

    public static List<Path> createPaths(final Path rootDir, final Path... paths) {
        return NullSafe.stream(paths)
                .map(aPath -> {
                    final Path path = aPath.isAbsolute()
                            ? aPath
                            : rootDir.resolve(aPath);
                    FileUtil.ensureDirExists(path);
                    return path;
                })
                .toList();
    }

    /**
     * Build a {@link Provider} that will provide a mock for the supplied class.
     * Useful for constructors whose arguments are all providers.
     */
    public static <T> Provider<T> mockProvider(final Class<T> type) {
        return () -> Mockito.mock(type);
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

    /**
     * See {@link TestUtil#testSerialisation(Object, Class, BiConsumer, ObjectMapper)}
     */
    public static <T> T testSerialisation(final T object,
                                          final Class<T> clazz) {
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        return testSerialisation(object, clazz, null, objectMapper);
    }

    /**
     * See {@link TestUtil#testSerialisation(Object, Class, BiConsumer, ObjectMapper)}
     */
    public static <T> T testSerialisation(final T object,
                                          final Class<T> clazz,
                                          final BiConsumer<ObjectMapper, String> jsonConsumer) {
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        return testSerialisation(object, clazz, jsonConsumer, objectMapper);
    }

    /**
     * Does a basic serialise - de-serialise test with an equality check on the initial
     * and final objects. The optional jsonConsumer allows assertions to be performed
     * by the caller on the serialised form.
     *
     * @return The de-serialised object for further inspection by the caller.
     */
    public static <T> T testSerialisation(final T object,
                                          final Class<T> clazz,
                                          final BiConsumer<ObjectMapper, String> jsonConsumer,
                                          final ObjectMapper objectMapper) {
        Objects.requireNonNull(object);
        Objects.requireNonNull(clazz);
        Objects.requireNonNull(objectMapper);

        final String json;
        try {
            json = objectMapper.writeValueAsString(object);
        } catch (final JsonProcessingException e) {
            throw new RuntimeException(LogUtil.message(
                    "Error serialising {}: {}", object, e.getMessage()), e);
        }

        LOGGER.debug(LogUtil.message("json for {}:\n{}", clazz.getSimpleName(), json));

        if (jsonConsumer != null) {
            jsonConsumer.accept(objectMapper, json);
        }

        final T object2;
        try {
            object2 = objectMapper.readValue(json, clazz);
        } catch (final JsonProcessingException e) {
            throw new RuntimeException(LogUtil.message(
                    "Error deserialising {}: {}", json, e.getMessage()), e);
        }

        Assertions.assertThat(object2)
                .isEqualTo(object);

        return object2;
    }

    public static void multiThread(final int threads,
                                   final Runnable work) {

        final CountDownLatch startLatch = new CountDownLatch(threads);
        final CountDownLatch endLatch = new CountDownLatch(threads);
        try (final ExecutorService executorService = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                executorService.submit(() -> {
//                    LOGGER.trace("Starting thread");
                    startLatch.countDown();
                    try {
                        startLatch.await();
                    } catch (final InterruptedException e) {
                        throw UncheckedInterruptedException.create(e);
                    }

                    work.run();
                    endLatch.countDown();
//                    LOGGER.trace("Ending task");
                });
            }
        }
        try {
            endLatch.await();
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }

    public static void comparePerformance(final int rounds,
                                          final long iterations,
                                          final Consumer<String> outputConsumer,
                                          final TimedCase... testCases) {
        comparePerformance(rounds, iterations, null, outputConsumer, testCases);
    }

    /**
     * Compares the performance of one or more {@link TimedCase}s, repeating each testCase
     * over n iterations and repeating all that over x rounds.
     *
     * @param rounds         Number of rounds to perform. A round runs n iterations for each testCase.
     *                       If rounds >1 then the results for the first round are treated as a JVM warm-up
     *                       and are not counted in the aggregate stats.
     * @param iterations     Number of times to run each testCase in a round.
     * @param setup          Run before each test case in each round
     * @param outputConsumer The consumer for the tabular results data
     * @param testCases      The test cases to run in each round.
     */
    public static void comparePerformance(final int rounds,
                                          final long iterations,
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

        final int warmedUpRounds = rounds > 1
                ? rounds - 1
                : rounds;
        int idx = 0;

        if (rounds > 1) {
            final int idxCopy = idx++;
            tableBuilder.withColumn(Column.durationNanos("Warmup Round", entry ->
                    entry.getValue().get(idxCopy)));
        }
        // Rest of the rounds
        for (int round = 1; round <= warmedUpRounds; round++) {
            final int idxCopy = idx++;
            tableBuilder.withColumn(Column.durationNanos("Round " + round, entry ->
                    entry.getValue().get(idxCopy)));
        }
        // If we have multiple rounds then ignore first round for jvm warm-up
        final int skipCount = rounds > 1
                ? 1
                : 0;
        final String tableStr = tableBuilder
                .withColumn(Column.durationNanos("Min", entry ->
                        entry.getValue().stream().skip(skipCount).min(Duration::compareTo).get()))
                .withColumn(Column.durationNanos("Max", entry ->
                        entry.getValue().stream().skip(skipCount).max(Duration::compareTo).get()))
                .withColumn(Column.durationNanos("Avg over rounds", entry ->
                        Duration.ofNanos((long) entry.getValue()
                                .stream()
                                .skip(skipCount)
                                .mapToLong(Duration::toNanos)
                                .average()
                                .getAsDouble())))
                .withColumn(Column.decimal("Per iter (last round)", entry ->
                        entry.getValue().get(rounds - 1).toNanos() / (double) iterations, 6))
                .build();
        outputConsumer.accept(LogUtil.message("Summary (iterations: {}, values in nanos):\n{}",
                ModelStringUtil.formatCsv(iterations),
                tableStr));
    }

    /**
     * Will create the passed files as empty files, ensuring their parent directories exist first.
     * Will throw if the file already exists.
     */
    public static void createFiles(final Path... files) {
        NullSafe.stream(files)
                .forEach(file -> {
                    try {
                        final Path parent = Objects.requireNonNull(
                                file.getParent(),
                                file + " has no parent");
                        Files.createDirectories(parent);
                        Files.createFile(file);
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    } catch (final Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }


    // --------------------------------------------------------------------------------


    public static interface TestSetup {

        void run(final int rounds, final long iterations);
    }


    // --------------------------------------------------------------------------------


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
        void run(final int round, final long iterations);
    }
}
