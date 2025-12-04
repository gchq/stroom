/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.pipeline.refdata.store.onheapstore;

import stroom.bytebuffer.PooledByteBufferOutputStream;
import stroom.lmdb.PutOutcome;
import stroom.pipeline.refdata.ReferenceDataConfig;
import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.NullValue;
import stroom.pipeline.refdata.store.ProcessingState;
import stroom.pipeline.refdata.store.RefDataLoader;
import stroom.pipeline.refdata.store.RefDataStore;
import stroom.pipeline.refdata.store.RefDataStoreFactory;
import stroom.pipeline.refdata.store.RefDataStoreTestModule;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.RefDataValueProxy;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.StagingValueOutputStream;
import stroom.pipeline.refdata.store.StringValue;
import stroom.pipeline.refdata.store.ValueStoreHashAlgorithm;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Range;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // For the mocks in RefDataStoreTestModule
class TestRefDataOnHeapStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestRefDataOnHeapStore.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(TestRefDataOnHeapStore.class);

    private static final String FIXED_PIPELINE_UUID = UUID.randomUUID().toString();
    private static final String FIXED_PIPELINE_VERSION = UUID.randomUUID().toString();

    private static final String KV_TYPE = "KV";
    private static final String RANGE_TYPE = "Range";
    private static final String PADDING = IntStream.rangeClosed(1,
            300).boxed().map(i -> "-").collect(Collectors.joining());

    private ReferenceDataConfig referenceDataConfig = new ReferenceDataConfig();

    @Inject
    private RefDataStoreFactory refDataStoreFactory;
    @Inject
    private PooledByteBufferOutputStream.Factory pooledByteBufferOutputStreamFactory;
    @Inject
    private ValueStoreHashAlgorithm valueStoreHashAlgorithm;
    @Inject
    private StagingValueOutputStream stagingValueOutputStream;

    private RefDataStore refDataStore;

    @BeforeEach
    void setUp(@TempDir final Path tempDir) {
        final Injector injector = Guice.createInjector(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        install(new RefDataStoreTestModule(
                                () -> getReferenceDataConfig(),
                                () -> tempDir,
                                () -> tempDir));
                    }
                });
        injector.injectMembers(this);
        refDataStore = refDataStoreFactory.createOnHeapStore();
    }

    @Test
    void testTreeMapReverseOrdering() {
        final Comparator<Long> comparator = Comparator.reverseOrder();
        final NavigableMap<Long, String> treeMap = new TreeMap<>(comparator);

        treeMap.put(1L, "one");
        treeMap.put(3L, "three");
        treeMap.put(5L, "five");
        treeMap.put(7L, "seven");

        treeMap.forEach((key, value) ->
                LOGGER.info("{} => {}", key, value));

        assertThat(treeMap.ceilingEntry(3L).getValue())
                .isEqualTo("three");
        assertThat(treeMap.ceilingEntry(4L).getValue())
                .isEqualTo("three");
        assertThat(treeMap.ceilingEntry(9L).getValue())
                .isEqualTo("seven");

        final Map.Entry<Long, String> entryThree = treeMap.ceilingEntry(4L);

        LOGGER.info("------------------");

        final SortedMap<Long, String> partMap = treeMap.tailMap(4L);
        partMap.forEach((key, value) ->
                LOGGER.info("{} => {}", key, value));


        assertThat(treeMap.floorEntry(-1L).getValue())
                .isEqualTo("one");
        assertThat(treeMap.floorEntry(3L).getValue())
                .isEqualTo("three");
        assertThat(treeMap.floorEntry(4L).getValue())
                .isEqualTo("five");
    }

    @Test
    void getProcessingInfo() {
    }

    @Test
    void isDataLoaded_true() {
    }

    @Test
    void isDataLoaded_false() {
        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();

        final boolean isLoaded = refDataStore.isDataLoaded(refStreamDefinition);

        assertThat(isLoaded)
                .isFalse();
    }

    @Test
    void testOverwrite_doOverwrite_keyValueStore() {
        final StringValue value1 = StringValue.of("myValue1");
        final StringValue value2 = StringValue.of("myValue2");

        // overwriting so value changes to value2
        final StringValue expectedFinalValue = value2;

        doKeyValueOverwriteTest(true, value1, value2, expectedFinalValue);
    }

    @Test
    void testOverwrite_doOverwrite_rangeValueStore() {
        final StringValue value1 = StringValue.of("myValue1");
        final StringValue value2 = StringValue.of("myValue2");

        // overwriting so value changes to value2
        final StringValue expectedFinalValue = value2;

        doKeyRangeValueOverwriteTest(true, value1, value2, expectedFinalValue);
    }

    @Test
    void testOverwrite_doNotOverwrite_keyValueStore() {
        final StringValue value1 = StringValue.of("myValue1");
        final StringValue value2 = StringValue.of("myValue2");

        // no overwriting so value stays as value1
        final StringValue expectedFinalValue = value1;

        doKeyValueOverwriteTest(false, value1, value2, expectedFinalValue);
    }

    @Test
    void testOverwrite_doNotOverwrite_rangeValueStore() {
        final StringValue value1 = StringValue.of("myValue1");
        final StringValue value2 = StringValue.of("myValue2");

        // no overwriting so value stays as value1
        final StringValue expectedFinalValue = value1;

        doKeyRangeValueOverwriteTest(false, value1, value2, expectedFinalValue);
    }

    @Test
    void testGetWithNoLoad_stringKey() {
        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();
        final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, "map1");
        final String key = "myKey";

        assertThat(refDataStore.getKeyValueEntryCount())
                .isEqualTo(0);

        assertThat(refDataStore.getValue(mapDefinition, key))
                .isEmpty();
    }

    @Test
    void testGetWithNoLoad_longKey() {
        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();
        final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, "map1");
        final String key = "123";

        assertThat(refDataStore.getKeyValueEntryCount())
                .isEqualTo(0);

        assertThat(refDataStore.getValue(mapDefinition, key))
                .isEmpty();
    }

    private void doKeyValueOverwriteTest(final boolean overwriteExisting,
                                         final StringValue value1,
                                         final StringValue value2,
                                         final StringValue expectedFinalValue) {

        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();
        final long effectiveTimeMs = System.currentTimeMillis();
        final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, "map1");
        final String key = "myKey";

        assertThat(refDataStore.getKeyValueEntryCount())
                .isEqualTo(0);

        refDataStore.doWithLoaderUnlessComplete(refStreamDefinition, effectiveTimeMs, loader -> {
            loader.initialise(overwriteExisting);
            final List<PutOutcome> putOutcomes = handleKeyOutcomes(loader);

            doLoaderPut(loader, mapDefinition, key, value1);
            // second put for same key, should only succeed if overwriteExisting is enabled
            doLoaderPut(loader, mapDefinition, key, value2);
            loader.markPutsComplete();
            loader.completeProcessing();

            assertPutOutcome(putOutcomes.get(0), true, false);
            assertPutOutcome(putOutcomes.get(1), overwriteExisting, true);
        });
        refDataStore.logAllContents();

        assertThat((StringValue) refDataStore.getValue(mapDefinition, key).get())
                .isEqualTo(expectedFinalValue);

        assertThat(refDataStore.getKeyValueEntryCount())
                .isEqualTo(1);
    }

    private void doKeyRangeValueOverwriteTest(final boolean overwriteExisting,
                                              final StringValue value1,
                                              final StringValue value2,
                                              final StringValue expectedFinalValue) {

        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();
        final long effectiveTimeMs = System.currentTimeMillis();
        final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, "map1");
        final Range<Long> range = new Range<>(1L, 100L);
        final String key = "50";

        assertThat(refDataStore.getRangeValueEntryCount())
                .isEqualTo(0);


        refDataStore.doWithLoaderUnlessComplete(refStreamDefinition, effectiveTimeMs, loader -> {
            loader.initialise(overwriteExisting);
            final List<PutOutcome> putOutcomes = handleRangeOutcomes(loader);

            doLoaderPut(loader, mapDefinition, range, value1);
            // second put for same key, should only succeed if overwriteExisting is enabled
            doLoaderPut(loader, mapDefinition, range, value2);
            loader.markPutsComplete();
            loader.completeProcessing();

            assertPutOutcome(putOutcomes.get(0), true, false);
            assertPutOutcome(putOutcomes.get(1), overwriteExisting, true);
        });

        refDataStore.logAllContents();
        assertThat((StringValue) refDataStore.getValue(mapDefinition, key).get())
                .isEqualTo(expectedFinalValue);

        assertThat(refDataStore.getRangeValueEntryCount())
                .isEqualTo(1);
    }

    @Test
    void loader_nullValue_keyValue_overwrite() {
        final Optional<RefDataValue> optValue = doKeyValueNullValueTest(true, 0);

        assertThat(optValue)
                .isEmpty();
    }

    @Test
    void loader_nullValue_keyValue_noOverwrite() {
        final Optional<RefDataValue> optValue = doKeyValueNullValueTest(false, 1);

        assertThat(optValue)
                .isPresent()
                .hasValue(StringValue.of("foo"));
    }

    Optional<RefDataValue> doKeyValueNullValueTest(final boolean overwriteExisting,
                                                   final int expectedEntryCount) {

        final StringValue nonNullValue = StringValue.of("foo");
        final NullValue nullValue = NullValue.getInstance();

        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();
        final long effectiveTimeMs = System.currentTimeMillis();
        final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, "map1");
        final String key = "myKey";

        assertThat(refDataStore.getKeyValueEntryCount())
                .isEqualTo(0);

        refDataStore.doWithLoaderUnlessComplete(refStreamDefinition, effectiveTimeMs, loader -> {
            loader.initialise(overwriteExisting);
            final List<PutOutcome> putOutcomes = handleKeyOutcomes(loader);

            doLoaderPut(loader, mapDefinition, key, nonNullValue);
            // second put for same key, if overwrite should remove prev entry, else leaves it
            doLoaderPut(loader, mapDefinition, key, nullValue);
            loader.markPutsComplete();
            loader.completeProcessing();

            assertPutOutcome(putOutcomes.get(0), true, false);
            assertPutOutcome(putOutcomes.get(1), overwriteExisting, true);
        });
        refDataStore.logAllContents();

        final Optional<RefDataValue> optValue = refDataStore.getValue(mapDefinition, key);

        assertThat(refDataStore.getKeyValueEntryCount())
                .isEqualTo(expectedEntryCount);
        return optValue;
    }

    @Test
    void loader_nullValue_rangeValue_overwrite() {
        final Optional<RefDataValue> optValue = doRangeValueNullValueTest(true, 0);

        assertThat(optValue)
                .isEmpty();
    }

    @Test
    void loader_nullValue_rangeValue_noOverwrite() {
        final Optional<RefDataValue> optValue = doRangeValueNullValueTest(false, 1);

        assertThat(optValue)
                .isPresent()
                .hasValue(StringValue.of("foo"));
    }

    Optional<RefDataValue> doRangeValueNullValueTest(final boolean overwriteExisting,
                                                     final int expectedEntryCount) {

        final StringValue nonNullValue = StringValue.of("foo");
        final NullValue nullValue = NullValue.getInstance();

        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();
        final long effectiveTimeMs = System.currentTimeMillis();
        final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, "map1");
        final Range<Long> range = new Range<>(1L, 100L);

        assertThat(refDataStore.getRangeValueEntryCount())
                .isEqualTo(0);

        refDataStore.doWithLoaderUnlessComplete(refStreamDefinition, effectiveTimeMs, loader -> {
            loader.initialise(overwriteExisting);
            final List<PutOutcome> putOutcomes = handleRangeOutcomes(loader);

            doLoaderPut(loader, mapDefinition, range, nonNullValue);
            // second put for same key, if overwrite should remove prev entry, else leaves it
            doLoaderPut(loader, mapDefinition, range, nullValue);

            loader.markPutsComplete();
            loader.completeProcessing();

            assertPutOutcome(putOutcomes.get(0), true, false);
            assertPutOutcome(putOutcomes.get(1), overwriteExisting, true);
        });
        refDataStore.logAllContents();

        final Optional<RefDataValue> optValue = refDataStore.getValue(mapDefinition, "50");

        assertThat(refDataStore.getRangeValueEntryCount())
                .isEqualTo(expectedEntryCount);
        return optValue;
    }

    @Test
    void loader_noOverwriteBigCommitInterval() {
        final boolean overwriteExisting = false;
        final int commitInterval = Integer.MAX_VALUE;

        bulkLoadAndAssert(overwriteExisting, commitInterval);
    }

    @Test
    void loader_noOverwriteSmallCommitInterval() {
        final boolean overwriteExisting = false;
        final int commitInterval = 2;

        bulkLoadAndAssert(overwriteExisting, commitInterval);
    }

    @Test
    void loader_noOverwriteWithDuplicateData() {
        final int commitInterval = Integer.MAX_VALUE;

        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();

        // same refStreamDefinition twice to imitate a re-load
        final List<RefStreamDefinition> refStreamDefinitions = Arrays.asList(
                refStreamDefinition, refStreamDefinition);

        bulkLoadAndAssert(refStreamDefinitions, false, commitInterval);
    }

    @Test
    void loader_overwriteWithDuplicateData() {
        final int commitInterval = Integer.MAX_VALUE;

        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();

        // same refStreamDefinition twice to imitate a re-load
        final List<RefStreamDefinition> refStreamDefinitions = Arrays.asList(
                refStreamDefinition, refStreamDefinition);

        bulkLoadAndAssert(refStreamDefinitions, true, commitInterval);
    }


    @Disabled // The on heap store will probably not be thread safe as we will have one store per pipeline process
    // for loading transient context data into
    @Test
    void testLoaderConcurrency() {

        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();
        final long effectiveTimeMs = System.currentTimeMillis();

        final MapDefinition mapDefinitionKey = new MapDefinition(refStreamDefinition, "MyKeyMap");
        final MapDefinition mapDefinitionRange = new MapDefinition(refStreamDefinition, "MyRangeMap");
        final int recCount = 1_000;

        final Runnable loadTask = () -> {
            LOGGER.debug("Running loadTask on thread {}", Thread.currentThread().getName());
            try {
                refDataStore.doWithLoaderUnlessComplete(refStreamDefinition, effectiveTimeMs, loader -> {
                    loader.setCommitInterval(200);
                    loader.initialise(false);

                    long rangeStartInc = 0;
                    long rangeEndExc;
                    for (int i = 0; i < recCount; i++) {
                        doLoaderPut(loader, mapDefinitionKey, "key" + i, StringValue.of("Value" + i));

                        rangeEndExc = rangeStartInc + 10;
                        final Range<Long> range = new Range<>(rangeStartInc, rangeEndExc);
                        rangeStartInc = rangeEndExc;
                        doLoaderPut(loader, mapDefinitionRange, range, StringValue.of("Value" + i));
                        //                        ThreadUtil.sleepAtLeastIgnoreInterrupts(50);
                    }
                    loader.completeProcessing();
                    LOGGER.debug("Finished loading data");

                });

                LOGGER.debug("Getting values");
                LAMBDA_LOGGER.logDurationIfDebugEnabled(() -> {
                    IntStream.range(0, recCount)
                            .boxed()
                            .sorted(Comparator.reverseOrder())
                            .forEach(i -> {
                                Optional<RefDataValue> optValue = refDataStore.getValue(mapDefinitionKey, "key" + i);
                                assertThat(optValue.isPresent());

                                optValue = refDataStore.getValue(mapDefinitionKey, Integer.toString((i * 10) + 5));
                                assertThat(optValue.isPresent());
                            });
                }, () -> LogUtil.message("Getting {} entries, twice", recCount));

            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
            LOGGER.debug("Finished running loadTask on thread {}", Thread.currentThread().getName());
        };

        final ExecutorService executorService = Executors.newFixedThreadPool(6);
        final List<CompletableFuture<Void>> futures = IntStream.rangeClosed(1, 10)
                .boxed()
                .map(i -> {
                    LOGGER.debug("Running async task on thread {}", Thread.currentThread().getName());
                    final CompletableFuture<Void> future = CompletableFuture.runAsync(loadTask, executorService);
                    LOGGER.debug("Got future");
                    return future;
                })
                .collect(Collectors.toList());

        futures.forEach(voidCompletableFuture -> {
            try {
                voidCompletableFuture.get();
            } catch (final InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
        LOGGER.debug("Finished all");
    }

    /**
     * Each task is trying to load data for a different {@link RefStreamDefinition} so they will all be fighting over
     * the write txn
     */
    @Disabled // The on heap store will probably not be thread safe as we will have one store per pipeline process
    // for loading transient context data into
    @Test
    void testLoaderConcurrency_multipleStreamDefs() {

        final long effectiveTimeMs = System.currentTimeMillis();
        final int recCount = 1_000;

        final Consumer<RefStreamDefinition> loadTask = refStreamDefinition -> {
            final MapDefinition mapDefinitionKey = new MapDefinition(refStreamDefinition, "MyKeyMap");
            final MapDefinition mapDefinitionRange = new MapDefinition(refStreamDefinition, "MyRangeMap");
            LOGGER.debug("Running loadTask on thread {}", Thread.currentThread().getName());
            try {
                refDataStore.doWithLoaderUnlessComplete(refStreamDefinition, effectiveTimeMs, loader -> {
                    loader.setCommitInterval(200);
                    loader.initialise(false);

                    long rangeStartInc = 0;
                    long rangeEndExc;
                    for (int i = 0; i < recCount; i++) {
                        doLoaderPut(loader, mapDefinitionKey, "key" + i, StringValue.of("Value" + i));

                        rangeEndExc = rangeStartInc + 10;
                        final Range<Long> range = new Range<>(rangeStartInc, rangeEndExc);
                        rangeStartInc = rangeEndExc;
                        doLoaderPut(loader, mapDefinitionRange, range, StringValue.of("Value" + i));
                        //                        ThreadUtil.sleepAtLeastIgnoreInterrupts(50);
                    }
                    loader.completeProcessing();
                    LOGGER.debug("Finished loading data");

                });

                LOGGER.debug("Getting values");
                LAMBDA_LOGGER.logDurationIfDebugEnabled(() -> {
                    IntStream.range(0, recCount)
                            .boxed()
                            .sorted(Comparator.reverseOrder())
                            .forEach(i -> {
                                Optional<RefDataValue> optValue = refDataStore.getValue(mapDefinitionKey, "key" + i);
                                assertThat(optValue.isPresent());

                                optValue = refDataStore.getValue(mapDefinitionKey, Integer.toString((i * 10) + 5));
                                assertThat(optValue.isPresent());
                            });
                }, () -> LogUtil.message("Getting {} entries, twice", recCount));

            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
            LOGGER.debug("Finished running loadTask on thread {}", Thread.currentThread().getName());
        };

        final ExecutorService executorService = Executors.newFixedThreadPool(6);
        final List<CompletableFuture<Void>> futures = IntStream.rangeClosed(1, 10)
                .boxed()
                .map(i -> {
                    LOGGER.debug("Running async task on thread {}", Thread.currentThread().getName());
                    final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();
                    final CompletableFuture<Void> future = CompletableFuture.runAsync(
                            () ->
                                    loadTask.accept(refStreamDefinition),
                            executorService);
                    LOGGER.debug("Got future");
                    return future;
                })
                .collect(Collectors.toList());

        futures.forEach(voidCompletableFuture -> {
            try {
                voidCompletableFuture.get();
            } catch (final InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
        LOGGER.debug("Finished all");
    }


    /**
     * Make entryCount very big for manual performance testing or profiling
     */
    @Test
    void testBigLoadForPerfTesting() {

        final MapNamFunc mapNamFunc = this::buildMapNameWithoutRefStreamDef;

//        setPurgeAgeProperty("1d");
        final int refStreamDefCount = 5;
        final int keyValueMapCount = 2;
        final int rangeValueMapCount = 2;
        final int entryCount = 50;
        final int totalKeyValueEntryCount = refStreamDefCount * keyValueMapCount * entryCount;
        final int totalRangeValueEntryCount = refStreamDefCount * rangeValueMapCount * entryCount;
        final int totalValueEntryCount = (totalKeyValueEntryCount + totalRangeValueEntryCount) / refStreamDefCount;

        LOGGER.info("-------------------------load starts here--------------------------------------");
        final List<RefStreamDefinition> refStreamDefs1 = loadBulkData(
                refStreamDefCount, keyValueMapCount, rangeValueMapCount, entryCount, 0, mapNamFunc);

        assertDbCounts(
                refStreamDefCount,
                totalKeyValueEntryCount,
                totalRangeValueEntryCount);

        // here to aid debugging problems at low volumes
        if (entryCount < 10) {
            refDataStore.logAllContents(LOGGER::info);
        }


        LOGGER.info("-----------------------second-load starts here----------------------------------");

        final List<RefStreamDefinition> refStreamDefs2 = loadBulkData(
                refStreamDefCount, keyValueMapCount, rangeValueMapCount, entryCount, refStreamDefCount, mapNamFunc);

        assertDbCounts(
                refStreamDefCount * 2,
                totalKeyValueEntryCount * 2,
                totalRangeValueEntryCount * 2);

        LOGGER.info("-------------------------gets start here---------------------------------------");

        final Random random = new Random();
        // for each ref stream def & map def, have N goes at picking a random key and getting the value for it
        Stream.concat(refStreamDefs1.stream(), refStreamDefs2.stream()).forEach(refStreamDef -> {
            final Instant startTime = Instant.now();
            Stream.of(KV_TYPE, RANGE_TYPE).forEach(valueType -> {
                for (int i = 0; i < entryCount; i++) {

                    final String mapName = mapNamFunc.buildMapName(
                            refStreamDef,
                            valueType,
                            random.nextInt(keyValueMapCount));
                    final MapDefinition mapDefinition = new MapDefinition(refStreamDef, mapName);
                    final int entryIdx = random.nextInt(entryCount);

                    final String queryKey;
                    final String expectedValue;
                    if (valueType.equals(KV_TYPE)) {
                        queryKey = buildKey(entryIdx);
                        expectedValue = buildKeyStoreValue(mapName, entryIdx, queryKey);
                    } else {
                        final Range<Long> range = buildRangeKey(entryIdx);
                        // in the DB teh keys are ranges so we need to pick a value in that range
                        queryKey = Long.toString(random.nextInt(range.size().intValue()) + range.getFrom());
                        expectedValue = buildRangeStoreValue(mapName, entryIdx, range);
                    }

                    // get the proxy then get the value
                    final RefDataValueProxy valueProxy = refDataStore.getValueProxy(mapDefinition, queryKey);
                    Optional<RefDataValue> optRefDataValue = valueProxy.supplyValue();

                    assertThat(optRefDataValue).isNotEmpty();
                    String value = ((StringValue) (optRefDataValue.get())).getValue();
                    assertThat(value)
                            .isEqualTo(expectedValue);

                    //now do it in one hit
                    optRefDataValue = refDataStore.getValue(mapDefinition, queryKey);
                    assertThat(optRefDataValue).isNotEmpty();
                    value = ((StringValue) (optRefDataValue.get())).getValue();
                    assertThat(value)
                            .isEqualTo(expectedValue);
                }
            });
            LOGGER.info("Done {} queries in {} for {}",
                    entryCount * 2, Duration.between(startTime, Instant.now()).toString(), refStreamDef);
        });
    }

    private void assertDbCounts(final int refStreamDefCount,
                                final int totalKeyValueEntryCount,
                                final int totalRangeValueEntryCount) {

        assertThat(refDataStore.getProcessingInfoEntryCount())
                .isEqualTo(refStreamDefCount);
        assertThat(refDataStore.getKeyValueEntryCount())
                .isEqualTo(totalKeyValueEntryCount);
        assertThat(refDataStore.getRangeValueEntryCount())
                .isEqualTo(totalRangeValueEntryCount);
    }

    private void setLastAccessedTime(final RefStreamDefinition refStreamDef, final long newLastAccessedTimeMs) {
        ((RefDataOnHeapStore) refDataStore).setLastAccessedTime(refStreamDef, newLastAccessedTimeMs);
    }

    private RefStreamDefinition buildUniqueRefStreamDefinition(final long streamId) {
        return new RefStreamDefinition(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                streamId);
    }

    private RefStreamDefinition buildUniqueRefStreamDefinition() {
        return new RefStreamDefinition(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                123456L);
    }

    private void bulkLoadAndAssert(final boolean overwriteExisting,
                                   final int commitInterval) {
        // two different ref stream definitions
        final List<RefStreamDefinition> refStreamDefinitions = Arrays.asList(
                buildUniqueRefStreamDefinition(),
                buildUniqueRefStreamDefinition());

        bulkLoadAndAssert(refStreamDefinitions, overwriteExisting, commitInterval);
    }

    private List<RefStreamDefinition> loadBulkData(
            final int refStreamDefinitionCount,
            final int keyValueMapCount,
            final int rangeValueMapCount,
            final int entryCount) {
        return loadBulkData(
                refStreamDefinitionCount,
                keyValueMapCount,
                rangeValueMapCount,
                entryCount,
                0,
                this::buildMapNameWithRefStreamDef);
    }

    /**
     * @param refStreamDefinitionCount  Number of {@link RefStreamDefinition}s to create.
     * @param keyValueMapCount          Number of KeyValue type maps to create per {@link RefStreamDefinition}
     * @param rangeValueMapCount        Number of RangeValue type maps to create per {@link RefStreamDefinition}
     * @param entryCount                Number of map entries to create per map
     * @param refStreamDefinitionOffset The offset from zero for the refStreamDefinition partIndex
     * @return The created {@link RefStreamDefinition} objects
     */
    private List<RefStreamDefinition> loadBulkData(
            final int refStreamDefinitionCount,
            final int keyValueMapCount,
            final int rangeValueMapCount,
            final int entryCount,
            final int refStreamDefinitionOffset,
            final MapNamFunc mapNamFunc) {

        assertThat(refStreamDefinitionCount)
                .isGreaterThan(0);
        assertThat(keyValueMapCount)
                .isGreaterThanOrEqualTo(0);
        assertThat(rangeValueMapCount)
                .isGreaterThanOrEqualTo(0);
        assertThat(entryCount)
                .isGreaterThan(0);

        final List<RefStreamDefinition> refStreamDefinitions = new ArrayList<>();

        for (int i = 0; i < refStreamDefinitionCount; i++) {

            final RefStreamDefinition refStreamDefinition = buildRefStreamDefintion(i + refStreamDefinitionOffset);

            refStreamDefinitions.add(refStreamDefinition);

            refDataStore.doWithLoaderUnlessComplete(
                    refStreamDefinition,
                    System.currentTimeMillis(),
                    loader -> {
                        loader.initialise(false);
                        loader.setCommitInterval(1000);

                        loadKeyValueData(keyValueMapCount, entryCount, refStreamDefinition, loader, mapNamFunc);
                        loadRangeValueData(keyValueMapCount, entryCount, refStreamDefinition, loader, mapNamFunc);

                        loader.completeProcessing();
                    });
        }
        return refStreamDefinitions;
    }

    private RefStreamDefinition buildRefStreamDefintion(final long i) {
        return new RefStreamDefinition(
                FIXED_PIPELINE_UUID,
                FIXED_PIPELINE_VERSION,
                i);
    }

    private void loadRangeValueData(final int keyValueMapCount,
                                    final int entryCount,
                                    final RefStreamDefinition refStreamDefinition,
                                    final RefDataLoader loader) {
        loadRangeValueData(keyValueMapCount,
                entryCount,
                refStreamDefinition,
                loader,
                this::buildMapNameWithRefStreamDef);
    }

    private void loadRangeValueData(final int keyValueMapCount,
                                    final int entryCount,
                                    final RefStreamDefinition refStreamDefinition,
                                    final RefDataLoader loader,
                                    final MapNamFunc mapNamFunc) {
        // load the range/value data
        for (int j = 0; j < keyValueMapCount; j++) {
            final String mapName = mapNamFunc.buildMapName(refStreamDefinition, RANGE_TYPE, j);
            final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, mapName);

            for (int k = 0; k < entryCount; k++) {
                final Range<Long> range = buildRangeKey(k);
                final String value = buildRangeStoreValue(mapName, k, range);
                doLoaderPut(loader, mapDefinition, range, StringValue.of(value));
            }
        }
    }

    private Range<Long> buildRangeKey(final int k) {
        return Range.of((long) k * 10, (long) (k * 10) + 10);
    }


    private void loadKeyValueData(final int keyValueMapCount,
                                  final int entryCount,
                                  final RefStreamDefinition refStreamDefinition,
                                  final RefDataLoader loader) {
        loadKeyValueData(keyValueMapCount, entryCount, refStreamDefinition, loader, this::buildMapNameWithRefStreamDef);
    }

    private void loadKeyValueData(final int keyValueMapCount,
                                  final int entryCount,
                                  final RefStreamDefinition refStreamDefinition,
                                  final RefDataLoader loader,
                                  final MapNamFunc mapNamFunc) {
        // load the key/value data
        for (int j = 0; j < keyValueMapCount; j++) {
            final String mapName = mapNamFunc.buildMapName(refStreamDefinition, KV_TYPE, j);
            final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, mapName);

            for (int k = 0; k < entryCount; k++) {
                final String key = buildKey(k);
                final String value = buildKeyStoreValue(mapName, k, key);
                doLoaderPut(loader, mapDefinition, key, StringValue.of(value));
            }
        }
    }

    private String buildRangeStoreValue(final String mapName, final int i, final Range<Long> range) {
        // pad the values out to make them more realistic in length to see impact on writes
        return LogUtil.message("{}-{}-{}-value{}{}",
                mapName, range.getFrom(), range.getTo(), i, PADDING);
    }

    private String buildMapNameWithRefStreamDef(
            final RefStreamDefinition refStreamDefinition,
            final String type,
            final int i) {
        return LogUtil.message("refStreamDef{}-{}map{}",
                refStreamDefinition.getStreamId(), type, i);
    }

    private String buildMapNameWithoutRefStreamDef(
            final RefStreamDefinition refStreamDefinition,
            final String type,
            final int i) {
        return LogUtil.message("{}map{}",
                type, i);
    }

    private String buildKeyStoreValue(final String mapName,
                                      final int i,
                                      final String key) {
        // pad the values out to make them more realistic in length to see impact on writes
        return LogUtil.message("{}-{}-value{}{}", mapName, key, i, PADDING);
    }

    private String buildKey(final int k) {
        return "key" + k;
    }


    private void bulkLoadAndAssert(final List<RefStreamDefinition> refStreamDefinitions,
                                   final boolean overwriteExisting,
                                   final int commitInterval) {


        final long effectiveTimeMs = System.currentTimeMillis();
        final AtomicInteger counter = new AtomicInteger();

        final List<String> mapNames = Arrays.asList("map1", "map2");

        final List<Tuple3<MapDefinition, String, StringValue>> keyValueLoadedData = new ArrayList<>();
        final List<Tuple3<MapDefinition, Range<Long>, StringValue>> keyRangeValueLoadedData = new ArrayList<>();

        final AtomicReference<RefStreamDefinition> lastRefStreamDefinition = new AtomicReference<>(null);
        final AtomicInteger lastCounterStartVal = new AtomicInteger();

        refStreamDefinitions.forEach(refStreamDefinition -> {
            try {
                final Tuple2<Long, Long> startEntryCounts = Tuple.of(
                        refDataStore.getKeyValueEntryCount(),
                        refDataStore.getRangeValueEntryCount());

                boolean isLoadExpectedToHappen = true;
                //Same stream def as last time so
                if (refStreamDefinition.equals(lastRefStreamDefinition.get())) {
                    counter.set(lastCounterStartVal.get());
                    isLoadExpectedToHappen = false;
                }
                lastCounterStartVal.set(counter.get());

                final int putAttempts = loadData(refStreamDefinition,
                        effectiveTimeMs,
                        commitInterval,
                        mapNames,
                        overwriteExisting,
                        counter,
                        keyValueLoadedData,
                        keyRangeValueLoadedData,
                        isLoadExpectedToHappen);


                final Tuple2<Long, Long> endEntryCounts = Tuple.of(
                        refDataStore.getKeyValueEntryCount(),
                        refDataStore.getRangeValueEntryCount());

                final int expectedNewEntries;
                if (refStreamDefinition.equals(lastRefStreamDefinition.get())) {
                    expectedNewEntries = 0;
                } else {
                    expectedNewEntries = putAttempts;
                }

                assertThat(endEntryCounts._1)
                        .isEqualTo(startEntryCounts._1 + expectedNewEntries);
                assertThat(endEntryCounts._2)
                        .isEqualTo(startEntryCounts._2 + expectedNewEntries);

                lastRefStreamDefinition.set(refStreamDefinition);

            } catch (final Exception e) {
                throw new RuntimeException(e);
            }

            final ProcessingState processingState = refDataStore.getLoadState(refStreamDefinition)
                    .get();

            assertThat(processingState)
                    .isEqualTo(ProcessingState.COMPLETE);

        });
        assertLoadedKeyValueData(keyValueLoadedData);
        assertLoadedKeyRangeValueData(keyRangeValueLoadedData);
    }

    private void assertLoadedKeyValueData(final List<Tuple3<MapDefinition, String, StringValue>> keyValueLoadedData) {
        // query all values from the key/value store
        keyValueLoadedData.forEach(tuple3 -> {
            // get the proxy object
            final RefDataValueProxy valueProxy = refDataStore.getValueProxy(tuple3._1, tuple3._2);


            final RefDataValue refDataValue = valueProxy.supplyValue().get();

            assertThat(refDataValue).isInstanceOf(StringValue.class);
            assertThat((StringValue) refDataValue)
                    .isEqualTo(tuple3._3);
        });
    }

    private void assertLoadedKeyRangeValueData(
            final List<Tuple3<MapDefinition, Range<Long>, StringValue>> keyRangeValueLoadedData) {
        keyRangeValueLoadedData.forEach(tuple3 -> {

            // build a variety of keys from the supplied range
            final String keyAtStartOfRange = tuple3._2.getFrom().toString();
            final String keyAtEndOfRange = Long.toString(tuple3._2.getTo() - 1);
            final String keyInsideRange = Long.toString(tuple3._2.getFrom() + 5);
            final String keyBelowRange = Long.toString(tuple3._2.getFrom() - 1);
            final String keyAboveRange = Long.toString(tuple3._2.getTo() + 1);

            // define the expected result for each key
            final List<Tuple2<String, Boolean>> keysAndExpectedResults = Arrays.asList(
                    Tuple.of(keyAtStartOfRange, true),
                    Tuple.of(keyAtEndOfRange, true),
                    Tuple.of(keyInsideRange, true),
                    Tuple.of(keyBelowRange, false),
                    Tuple.of(keyAboveRange, false));

            keysAndExpectedResults.forEach(tuple2 -> {
                LOGGER.debug("range {}, key {}, expected {}", tuple3._2, tuple2._1, tuple2._2);

                // get the proxy object
                final RefDataValueProxy valueProxy = refDataStore.getValueProxy(tuple3._1, tuple2._1);

                final boolean isValueExpected = tuple2._2;

                final Optional<RefDataValue> optRefDataValue = valueProxy.supplyValue();

                assertThat(optRefDataValue.isPresent())
                        .isEqualTo(isValueExpected);

                optRefDataValue.ifPresent(refDataValue -> {
                    assertThat(refDataValue).isInstanceOf(StringValue.class);
                    assertThat((StringValue) refDataValue)
                            .isEqualTo(tuple3._3);
                });
            });
        });
    }

    private int loadData(
            final RefStreamDefinition refStreamDefinition,
            final long effectiveTimeMs,
            final int commitInterval,
            final List<String> mapNames,
            final boolean overwriteExisting,
            final AtomicInteger counter,
            final List<Tuple3<MapDefinition, String, StringValue>> keyValueLoadedData,
            final List<Tuple3<MapDefinition, Range<Long>, StringValue>> keyRangeValueLoadedData,
            final boolean isLoadExpectedToHappen) throws Exception {


        final int entriesPerMapDef = 1;
        final boolean didLoadHappen = refDataStore.doWithLoaderUnlessComplete(
                refStreamDefinition,
                effectiveTimeMs,
                loader -> {
                    loader.initialise(overwriteExisting);
                    loader.setCommitInterval(commitInterval);

                    for (int i = 0; i < entriesPerMapDef; i++) {
                        // put key/values into each mapDef
                        mapNames.stream()
                                .map(name -> new MapDefinition(refStreamDefinition, name))
                                .forEach(mapDefinition -> {
                                    final int cnt = counter.incrementAndGet();
                                    final String key = buildKey(cnt);
                                    final StringValue value = StringValue.of("value" + cnt);
                                    LOGGER.debug("Putting cnt {}, key {}, value {}", cnt, key, value);
                                    doLoaderPut(loader, mapDefinition, key, value);

                                    keyValueLoadedData.add(Tuple.of(mapDefinition, key, value));
                                });

                        // put keyrange/values into each mapDef
                        mapNames.stream()
                                .map(name -> new MapDefinition(refStreamDefinition, name))
                                .forEach(mapDefinition -> {
                                    final int cnt = counter.incrementAndGet();
                                    final Range<Long> keyRange = new Range<>(
                                            (long) (cnt * 10),
                                            (long) ((cnt * 10) + 10));
                                    final StringValue value = StringValue.of("value" + cnt);
                                    LOGGER.debug("Putting cnt {}, key-range {}, value {}", cnt, keyRange, value);
                                    doLoaderPut(loader, mapDefinition, keyRange, value);
                                    keyRangeValueLoadedData.add(Tuple.of(mapDefinition, keyRange, value));
                                });
                    }

                    loader.completeProcessing();
                });

        assertThat(didLoadHappen)
                .isEqualTo(isLoadExpectedToHappen);

        final ProcessingState processingState = refDataStore.getLoadState(refStreamDefinition).get();

        assertThat(processingState)
                .isEqualTo(ProcessingState.COMPLETE);

        final boolean isDataLoaded = refDataStore.isDataLoaded(refStreamDefinition);
        assertThat(isDataLoaded)
                .isTrue();

        return entriesPerMapDef * mapNames.size();
    }

    stroom.pipeline.refdata.store.RefDataStore getRefDataStore() {
        return refDataStoreFactory.createOnHeapStore();
    }

    private interface MapNamFunc {

        String buildMapName(final RefStreamDefinition refStreamDefinition, final String type, final int i);

    }

    private void doLoaderPut(final RefDataLoader refDataLoader,
                             final MapDefinition mapDefinition,
                             final String key,
                             final RefDataValue refDataValue) {
        writeValue(refDataValue);
        refDataLoader.put(mapDefinition, key, stagingValueOutputStream);
    }

    private void doLoaderPut(final RefDataLoader refDataLoader,
                             final MapDefinition mapDefinition,
                             final Range<Long> range,
                             final RefDataValue refDataValue) {
        writeValue(refDataValue);
        refDataLoader.put(mapDefinition, range, stagingValueOutputStream);
    }

    private void writeValue(final RefDataValue refDataValue) {
        stagingValueOutputStream.clear();
        try {
            if (refDataValue instanceof StringValue) {
                final StringValue stringValue = (StringValue) refDataValue;
                stagingValueOutputStream.write(stringValue.getValue());
                stagingValueOutputStream.setTypeId(StringValue.TYPE_ID);
            } else if (refDataValue instanceof FastInfosetValue) {
                stagingValueOutputStream.write(((FastInfosetValue) refDataValue).getByteBuffer());
                stagingValueOutputStream.setTypeId(FastInfosetValue.TYPE_ID);
            } else if (refDataValue instanceof NullValue) {
                stagingValueOutputStream.setTypeId(NullValue.TYPE_ID);
            } else {
                throw new RuntimeException("Unexpected type " + refDataValue.getClass().getSimpleName());
            }
        } catch (final IOException e) {
            throw new RuntimeException(LogUtil.message("Error writing value: {}", e.getMessage()), e);
        }
    }

    private List<PutOutcome> handleKeyOutcomes(final RefDataLoader refDataLoader) {
        final List<PutOutcome> putOutcomes = new ArrayList<>();
        refDataLoader.setKeyPutOutcomeHandler((mapDefinitionSupplier, key, putOutcome) -> {
            putOutcomes.add(putOutcome);
        });
        return putOutcomes;
    }

    private List<PutOutcome> handleRangeOutcomes(final RefDataLoader refDataLoader) {
        final List<PutOutcome> putOutcomes = new ArrayList<>();
        refDataLoader.setRangePutOutcomeHandler((mapDefinitionSupplier, range, putOutcome) -> {
            putOutcomes.add(putOutcome);
        });
        return putOutcomes;
    }

    private void assertPutOutcome(final PutOutcome putOutcome,
                                  final boolean expectedIsSuccess,
                                  final boolean expectedIsDuplicate) {
        assertThat(putOutcome.isSuccess())
                .isEqualTo(expectedIsSuccess);
        assertThat(putOutcome.isDuplicate())
                .hasValue(expectedIsDuplicate);
    }

    private void assertPutOutcome(final PutOutcome putOutcome,
                                  final boolean expectedIsSuccess,
                                  final Optional<Boolean> expectedIsDuplicate) {
        assertThat(putOutcome.isSuccess())
                .isEqualTo(expectedIsSuccess);
        assertThat(putOutcome.isDuplicate())
                .isEqualTo(expectedIsDuplicate);
    }

    private ReferenceDataConfig getReferenceDataConfig() {
        return referenceDataConfig;
    }

}
