/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.refdata.store.onheapstore;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.Range;
import stroom.properties.api.PropertyService;
import stroom.properties.impl.mock.MockPropertyService;
import stroom.refdata.store.MapDefinition;
import stroom.refdata.store.ProcessingState;
import stroom.refdata.store.RefDataLoader;
import stroom.refdata.store.RefDataProcessingInfo;
import stroom.refdata.store.RefDataStore;
import stroom.refdata.store.RefDataStoreModule;
import stroom.refdata.store.RefDataStoreProvider;
import stroom.refdata.store.RefDataValue;
import stroom.refdata.store.RefDataValueProxy;
import stroom.refdata.store.RefStreamDefinition;
import stroom.refdata.store.StringValue;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class TestRefDataOnHeapStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestRefDataOnHeapStore.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(TestRefDataOnHeapStore.class);

    public static final String FIXED_PIPELINE_UUID = UUID.randomUUID().toString();
    public static final String FIXED_PIPELINE_VERSION = UUID.randomUUID().toString();

    private static final String KV_TYPE = "KV";
    private static final String RANGE_TYPE = "Range";
    private static final String PADDING = IntStream.rangeClosed(1, 300).boxed().map(i -> "-").collect(Collectors.joining());

    private final PropertyService mockPropertyService = new MockPropertyService();

    @Inject
    private RefDataStoreProvider refDataStoreProvider;
    private RefDataStore refDataStore;

    @Before
    public void setUp() throws Exception {
        final Injector injector = Guice.createInjector(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(PropertyService.class).toInstance(mockPropertyService);
                        install(new RefDataStoreModule());
                    }
                });
        injector.injectMembers(this);
        refDataStore = refDataStoreProvider.createOnHeapStore();
//        refDataStoreProvider = new RefDataOnHeapStore(genericRefDataValueSerde);
    }

    @Test
    public void testTreeMapReverseOrdering() {
        Comparator<Long> comparator = Comparator.reverseOrder();
        NavigableMap<Long, String> treeMap = new TreeMap<>(comparator);

        treeMap.put(1L, "one");
        treeMap.put(3L, "three");
        treeMap.put(5L, "five");
        treeMap.put(7L, "seven");

        treeMap.forEach((key, value) ->
                LOGGER.info(LambdaLogger.buildMessage("{} => {}", key, value)));

        assertThat(treeMap.ceilingEntry(3L).getValue()).isEqualTo("three");
        assertThat(treeMap.ceilingEntry(4L).getValue()).isEqualTo("three");
        assertThat(treeMap.ceilingEntry(9L).getValue()).isEqualTo("seven");

        Map.Entry<Long, String> entryThree = treeMap.ceilingEntry(4L);

        LOGGER.info("------------------");

        SortedMap<Long, String> partMap = treeMap.tailMap(4L);
        partMap.forEach((key, value) ->
                LOGGER.info(LambdaLogger.buildMessage("{} => {}", key, value)));


        assertThat(treeMap.floorEntry(-1L).getValue()).isEqualTo("one");
        assertThat(treeMap.floorEntry(3L).getValue()).isEqualTo("three");
        assertThat(treeMap.floorEntry(4L).getValue()).isEqualTo("five");
    }

    @Test
    public void getProcessingInfo() {
    }

    @Test
    public void isDataLoaded_true() {
    }

    @Test
    public void isDataLoaded_false() {
        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();

        boolean isLoaded = refDataStore.isDataLoaded(refStreamDefinition);

        assertThat(isLoaded).isFalse();
    }

    @Test
    public void testOverwrite_doOverwrite_keyValueStore() throws Exception {
        StringValue value1 = StringValue.of("myValue1");
        StringValue value2 = StringValue.of("myValue2");

        // overwriting so value changes to value2
        StringValue expectedFinalValue = value2;

        doKeyValueOverwriteTest(true, value1, value2, expectedFinalValue);
    }

    @Test
    public void testOverwrite_doOverwrite_rangeValueStore() throws Exception {
        StringValue value1 = StringValue.of("myValue1");
        StringValue value2 = StringValue.of("myValue2");

        // overwriting so value changes to value2
        StringValue expectedFinalValue = value2;

        doKeyRangeValueOverwriteTest(true, value1, value2, expectedFinalValue);
    }

    @Test
    public void testOverwrite_doNotOverwrite_keyValueStore() throws Exception {
        StringValue value1 = StringValue.of("myValue1");
        StringValue value2 = StringValue.of("myValue2");

        // no overwriting so value stays as value1
        StringValue expectedFinalValue = value1;

        doKeyValueOverwriteTest(false, value1, value2, expectedFinalValue);
    }

    @Test
    public void testOverwrite_doNotOverwrite_rangeValueStore() throws Exception {
        StringValue value1 = StringValue.of("myValue1");
        StringValue value2 = StringValue.of("myValue2");

        // no overwriting so value stays as value1
        StringValue expectedFinalValue = value1;

        doKeyRangeValueOverwriteTest(false, value1, value2, expectedFinalValue);
    }

    private void doKeyValueOverwriteTest(final boolean overwriteExisting,
                                         final StringValue value1,
                                         final StringValue value2,
                                         final StringValue expectedFinalValue) throws Exception {

        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();
        long effectiveTimeMs = System.currentTimeMillis();
        MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, "map1");
        String key = "myKey";

        assertThat(refDataStore.getKeyValueEntryCount()).isEqualTo(0);

        AtomicBoolean didPutSucceed = new AtomicBoolean(false);
        refDataStore.doWithLoaderUnlessComplete(refStreamDefinition, effectiveTimeMs, loader -> {
            loader.initialise(overwriteExisting);

            didPutSucceed.set(loader.put(mapDefinition, key, value1));
            assertThat(didPutSucceed).isTrue();

            didPutSucceed.set(loader.put(mapDefinition, key, value2));
            assertThat(didPutSucceed.get()).isEqualTo(overwriteExisting);

            loader.completeProcessing();
        });
        refDataStore.logAllContents();

        assertThat((StringValue) refDataStore.getValue(mapDefinition, key).get()).isEqualTo(expectedFinalValue);

        assertThat(refDataStore.getKeyValueEntryCount()).isEqualTo(1);
    }

    private void doKeyRangeValueOverwriteTest(final boolean overwriteExisting,
                                              final StringValue value1,
                                              final StringValue value2,
                                              final StringValue expectedFinalValue) throws Exception {

        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();
        long effectiveTimeMs = System.currentTimeMillis();
        MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, "map1");
        Range<Long> range = new Range<>(1L, 100L);
        String key = "50";

        assertThat(refDataStore.getKeyRangeValueEntryCount()).isEqualTo(0);

        final AtomicBoolean didPutSucceed = new AtomicBoolean(false);

        refDataStore.doWithLoaderUnlessComplete(refStreamDefinition, effectiveTimeMs, loader -> {
            loader.initialise(overwriteExisting);

            didPutSucceed.set(loader.put(mapDefinition, range, value1));
            assertThat(didPutSucceed).isTrue();

            didPutSucceed.set(loader.put(mapDefinition, range, value2));

            // second put for same key, should only succeed if overwriteExisting is enabled
            assertThat(didPutSucceed.get()).isEqualTo(overwriteExisting);

            loader.completeProcessing();
        });

        refDataStore.logAllContents();
        assertThat((StringValue) refDataStore.getValue(mapDefinition, key).get()).isEqualTo(expectedFinalValue);

        assertThat(refDataStore.getKeyRangeValueEntryCount()).isEqualTo(1);
    }

    @Test
    public void loader_noOverwriteBigCommitInterval() throws Exception {
        boolean overwriteExisting = false;
        int commitInterval = Integer.MAX_VALUE;

        bulkLoadAndAssert(overwriteExisting, commitInterval);
    }

    @Test
    public void loader_noOverwriteSmallCommitInterval() throws Exception {
        boolean overwriteExisting = false;
        int commitInterval = 2;

        bulkLoadAndAssert(overwriteExisting, commitInterval);
    }

    @Test
    public void loader_noOverwriteWithDuplicateData() throws Exception {
        int commitInterval = Integer.MAX_VALUE;

        RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();

        // same refStreamDefinition twice to imitate a re-load
        List<RefStreamDefinition> refStreamDefinitions = Arrays.asList(
                refStreamDefinition, refStreamDefinition);

        bulkLoadAndAssert(refStreamDefinitions, false, commitInterval);
    }

    @Test
    public void loader_overwriteWithDuplicateData() throws Exception {
        int commitInterval = Integer.MAX_VALUE;

        RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();

        // same refStreamDefinition twice to imitate a re-load
        List<RefStreamDefinition> refStreamDefinitions = Arrays.asList(
                refStreamDefinition, refStreamDefinition);

        bulkLoadAndAssert(refStreamDefinitions, true, commitInterval);
    }


    @Ignore // The on heap store will probably not be thread safe as we will have one store per pipeline process
    // for loading transient context data into
    @Test
    public void testLoaderConcurrency() {

        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();
        final long effectiveTimeMs = System.currentTimeMillis();

        final MapDefinition mapDefinitionKey = new MapDefinition(refStreamDefinition, "MyKeyMap");
        final MapDefinition mapDefinitionRange = new MapDefinition(refStreamDefinition, "MyRangeMap");
        final int recCount = 1_000;

        Runnable loadTask = () -> {
            LOGGER.debug("Running loadTask on thread {}", Thread.currentThread().getName());
            try {
                refDataStore.doWithLoaderUnlessComplete(refStreamDefinition, effectiveTimeMs, refDataLoader -> {
                    refDataLoader.setCommitInterval(200);
                    refDataLoader.initialise(false);

                    long rangeStartInc = 0;
                    long rangeEndExc;
                    for (int i = 0; i < recCount; i++) {
                        refDataLoader.put(mapDefinitionKey, "key" + i, StringValue.of("Value" + i));

                        rangeEndExc = rangeStartInc + 10;
                        Range<Long> range = new Range<>(rangeStartInc, rangeEndExc);
                        rangeStartInc = rangeEndExc;
                        refDataLoader.put(mapDefinitionRange, range, StringValue.of("Value" + i));
                        //                        ThreadUtil.sleepAtLeastIgnoreInterrupts(50);
                    }
                    refDataLoader.completeProcessing();
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
                }, () -> LambdaLogger.buildMessage("Getting {} entries, twice", recCount));

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            LOGGER.debug("Finished running loadTask on thread {}", Thread.currentThread().getName());
        };

        ExecutorService executorService = Executors.newFixedThreadPool(6);
        List<CompletableFuture<Void>> futures = IntStream.rangeClosed(1, 10)
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
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
        LOGGER.debug("Finished all");
    }

    /**
     * Each task is trying to load data for a different {@link RefStreamDefinition} so they will all be fighting over
     * the write txn
     */
    @Ignore // The on heap store will probably not be thread safe as we will have one store per pipeline process
    // for loading transient context data into
    @Test
    public void testLoaderConcurrency_multipleStreamDefs() {

        final long effectiveTimeMs = System.currentTimeMillis();
        final int recCount = 1_000;

        Consumer<RefStreamDefinition> loadTask = refStreamDefinition -> {
            final MapDefinition mapDefinitionKey = new MapDefinition(refStreamDefinition, "MyKeyMap");
            final MapDefinition mapDefinitionRange = new MapDefinition(refStreamDefinition, "MyRangeMap");
            LOGGER.debug("Running loadTask on thread {}", Thread.currentThread().getName());
            try {
                refDataStore.doWithLoaderUnlessComplete(refStreamDefinition, effectiveTimeMs, refDataLoader -> {
                    refDataLoader.setCommitInterval(200);
                    refDataLoader.initialise(false);

                    long rangeStartInc = 0;
                    long rangeEndExc;
                    for (int i = 0; i < recCount; i++) {
                        refDataLoader.put(mapDefinitionKey, "key" + i, StringValue.of("Value" + i));

                        rangeEndExc = rangeStartInc + 10;
                        Range<Long> range = new Range<>(rangeStartInc, rangeEndExc);
                        rangeStartInc = rangeEndExc;
                        refDataLoader.put(mapDefinitionRange, range, StringValue.of("Value" + i));
                        //                        ThreadUtil.sleepAtLeastIgnoreInterrupts(50);
                    }
                    refDataLoader.completeProcessing();
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
                }, () -> LambdaLogger.buildMessage("Getting {} entries, twice", recCount));

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            LOGGER.debug("Finished running loadTask on thread {}", Thread.currentThread().getName());
        };

        ExecutorService executorService = Executors.newFixedThreadPool(6);
        List<CompletableFuture<Void>> futures = IntStream.rangeClosed(1, 10)
                .boxed()
                .map(i -> {
                    LOGGER.debug("Running async task on thread {}", Thread.currentThread().getName());
                    RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();
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
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
        LOGGER.debug("Finished all");
    }


    /**
     * Make entryCount very big for manual performance testing or profiling
     */
    @Test
    public void testBigLoadForPerfTesting() {

        MapNamFunc mapNamFunc = this::buildMapNameWithoutRefStreamDef;

//        setPurgeAgeProperty("1d");
        int refStreamDefCount = 5;
        int keyValueMapCount = 2;
        int rangeValueMapCount = 2;
        int entryCount = 50;
        int totalKeyValueEntryCount = refStreamDefCount * keyValueMapCount * entryCount;
        int totalRangeValueEntryCount = refStreamDefCount * rangeValueMapCount * entryCount;
        int totalValueEntryCount = (totalKeyValueEntryCount + totalRangeValueEntryCount) / refStreamDefCount;

        LOGGER.info("-------------------------load starts here--------------------------------------");
        List<RefStreamDefinition> refStreamDefs1 = loadBulkData(
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

        List<RefStreamDefinition> refStreamDefs2 = loadBulkData(
                refStreamDefCount, keyValueMapCount, rangeValueMapCount, entryCount, refStreamDefCount, mapNamFunc);

        assertDbCounts(
                refStreamDefCount * 2,
                totalKeyValueEntryCount * 2,
                totalRangeValueEntryCount * 2);

        LOGGER.info("-------------------------gets start here---------------------------------------");

        Random random = new Random();
        // for each ref stream def & map def, have N goes at picking a random key and getting the value for it
        Stream.concat(refStreamDefs1.stream(), refStreamDefs2.stream()).forEach(refStreamDef -> {
            Instant startTime = Instant.now();
            Stream.of(KV_TYPE, RANGE_TYPE).forEach(valueType -> {
                for (int i = 0; i < entryCount; i++) {

                    String mapName = mapNamFunc.buildMapName(refStreamDef, valueType, random.nextInt(keyValueMapCount));
                    MapDefinition mapDefinition = new MapDefinition(refStreamDef, mapName);
                    int entryIdx = random.nextInt(entryCount);

                    String queryKey;
                    String expectedValue;
                    if (valueType.equals(KV_TYPE)) {
                        queryKey = buildKey(entryIdx);
                        expectedValue = buildKeyStoreValue(mapName, entryIdx, queryKey);
                    } else {
                        Range<Long> range = buildRangeKey(entryIdx);
                        // in the DB teh keys are ranges so we need to pick a value in that range
                        queryKey = Long.toString(random.nextInt(range.size().intValue()) + range.getFrom());
                        expectedValue = buildRangeStoreValue(mapName, entryIdx, range);
                    }

                    // get the proxy then get the value
                    RefDataValueProxy valueProxy = refDataStore.getValueProxy(mapDefinition, queryKey);
                    Optional<RefDataValue> optRefDataValue = valueProxy.supplyValue();

                    assertThat(optRefDataValue).isNotEmpty();
                    String value = ((StringValue) (optRefDataValue.get())).getValue();
                    assertThat(value).isEqualTo(expectedValue);

                    //now do it in one hit
                    optRefDataValue = refDataStore.getValue(mapDefinition, queryKey);
                    assertThat(optRefDataValue).isNotEmpty();
                    value = ((StringValue) (optRefDataValue.get())).getValue();
                    assertThat(value).isEqualTo(expectedValue);
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
        assertThat(refDataStore.getKeyRangeValueEntryCount())
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
        List<RefStreamDefinition> refStreamDefinitions = Arrays.asList(
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
     * @param refStreamDefinitionOffset The offset from zero for the refStreamDefinition streamNo
     * @return The created {@link RefStreamDefinition} objects
     */
    private List<RefStreamDefinition> loadBulkData(
            final int refStreamDefinitionCount,
            final int keyValueMapCount,
            final int rangeValueMapCount,
            final int entryCount,
            final int refStreamDefinitionOffset,
            final MapNamFunc mapNamFunc) {

        assertThat(refStreamDefinitionCount).isGreaterThan(0);
        assertThat(keyValueMapCount).isGreaterThanOrEqualTo(0);
        assertThat(rangeValueMapCount).isGreaterThanOrEqualTo(0);
        assertThat(entryCount).isGreaterThan(0);

        List<RefStreamDefinition> refStreamDefinitions = new ArrayList<>();

        for (int i = 0; i < refStreamDefinitionCount; i++) {

            RefStreamDefinition refStreamDefinition = buildRefStreamDefintion(i + refStreamDefinitionOffset);

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
        loadRangeValueData(keyValueMapCount, entryCount, refStreamDefinition, loader, this::buildMapNameWithRefStreamDef);
    }

    private void loadRangeValueData(final int keyValueMapCount,
                                    final int entryCount,
                                    final RefStreamDefinition refStreamDefinition,
                                    final RefDataLoader loader,
                                    final MapNamFunc mapNamFunc) {
        // load the range/value data
        for (int j = 0; j < keyValueMapCount; j++) {
            String mapName = mapNamFunc.buildMapName(refStreamDefinition, RANGE_TYPE, j);
            MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, mapName);

            for (int k = 0; k < entryCount; k++) {
                Range<Long> range = buildRangeKey(k);
                String value = buildRangeStoreValue(mapName, k, range);
                loader.put(mapDefinition, range, StringValue.of(value));
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
            String mapName = mapNamFunc.buildMapName(refStreamDefinition, KV_TYPE, j);
            MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, mapName);

            for (int k = 0; k < entryCount; k++) {
                String key = buildKey(k);
                String value = buildKeyStoreValue(mapName, k, key);
                loader.put(mapDefinition, key, StringValue.of(value));
            }
        }
    }

    private String buildRangeStoreValue(final String mapName, final int i, final Range<Long> range) {
        // pad the values out to make them more realistic in length to see impact on writes
        return LambdaLogger.buildMessage("{}-{}-{}-value{}{}",
                mapName, range.getFrom(), range.getTo(), i, PADDING);
    }

    private String buildMapNameWithRefStreamDef(
            final RefStreamDefinition refStreamDefinition,
            final String type,
            final int i) {
        return LambdaLogger.buildMessage("refStreamDef{}-{}map{}",
                refStreamDefinition.getStreamId(), type, i);
    }

    private String buildMapNameWithoutRefStreamDef(
            final RefStreamDefinition refStreamDefinition,
            final String type,
            final int i) {
        return LambdaLogger.buildMessage("{}map{}",
                type, i);
    }

    private String buildKeyStoreValue(final String mapName,
                                      final int i,
                                      final String key) {
        // pad the values out to make them more realistic in length to see impact on writes
        return LambdaLogger.buildMessage("{}-{}-value{}{}", mapName, key, i, PADDING);
    }

    private String buildKey(final int k) {
        return "key" + k;
    }


    private void bulkLoadAndAssert(final List<RefStreamDefinition> refStreamDefinitions,
                                   final boolean overwriteExisting,
                                   final int commitInterval) {


        long effectiveTimeMs = System.currentTimeMillis();
        AtomicInteger counter = new AtomicInteger();

        List<String> mapNames = Arrays.asList("map1", "map2");

        List<Tuple3<MapDefinition, String, StringValue>> keyValueLoadedData = new ArrayList<>();
        List<Tuple3<MapDefinition, Range<Long>, StringValue>> keyRangeValueLoadedData = new ArrayList<>();

        final AtomicReference<RefStreamDefinition> lastRefStreamDefinition = new AtomicReference<>(null);
        final AtomicInteger lastCounterStartVal = new AtomicInteger();

        refStreamDefinitions.forEach(refStreamDefinition -> {
            try {
                final Tuple2<Long, Long> startEntryCounts = Tuple.of(
                        refDataStore.getKeyValueEntryCount(),
                        refDataStore.getKeyRangeValueEntryCount());

                boolean isLoadExpectedToHappen = true;
                //Same stream def as last time so
                if (refStreamDefinition.equals(lastRefStreamDefinition.get())) {
                    counter.set(lastCounterStartVal.get());
                    isLoadExpectedToHappen = false;
                }
                lastCounterStartVal.set(counter.get());

                int putAttempts = loadData(refStreamDefinition,
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
                        refDataStore.getKeyRangeValueEntryCount());

                int expectedNewEntries;
                if (refStreamDefinition.equals(lastRefStreamDefinition.get())) {
                    expectedNewEntries = 0;
                } else {
                    expectedNewEntries = putAttempts;
                }

                assertThat(endEntryCounts._1).isEqualTo(startEntryCounts._1 + expectedNewEntries);
                assertThat(endEntryCounts._2).isEqualTo(startEntryCounts._2 + expectedNewEntries);

                lastRefStreamDefinition.set(refStreamDefinition);

            } catch (Exception e) {
                throw new RuntimeException(e);
            }

//            ((RefDataOffHeapStore) refDataStore).logAllContents();

            RefDataProcessingInfo refDataProcessingInfo = refDataStore.getAndTouchProcessingInfo(refStreamDefinition).get();

            assertThat(refDataProcessingInfo.getProcessingState()).isEqualTo(ProcessingState.COMPLETE);

        });
        assertLoadedKeyValueData(keyValueLoadedData);
        assertLoadedKeyRangeValueData(keyRangeValueLoadedData);
    }

    private void assertLoadedKeyValueData(final List<Tuple3<MapDefinition, String, StringValue>> keyValueLoadedData) {
        // query all values from the key/value store
        keyValueLoadedData.forEach(tuple3 -> {
            // get the proxy object
            RefDataValueProxy valueProxy = refDataStore.getValueProxy(tuple3._1, tuple3._2);


            RefDataValue refDataValue = valueProxy.supplyValue().get();

            assertThat(refDataValue).isInstanceOf(StringValue.class);
            assertThat((StringValue) refDataValue).isEqualTo(tuple3._3);
        });
    }

    private void assertLoadedKeyRangeValueData(final List<Tuple3<MapDefinition, Range<Long>, StringValue>> keyRangeValueLoadedData) {
        keyRangeValueLoadedData.forEach(tuple3 -> {

            // build a variety of keys from the supplied range
            String keyAtStartOfRange = tuple3._2.getFrom().toString();
            String keyAtEndOfRange = Long.toString(tuple3._2.getTo() - 1);
            String keyInsideRange = Long.toString(tuple3._2.getFrom() + 5);
            String keyBelowRange = Long.toString(tuple3._2.getFrom() - 1);
            String keyAboveRange = Long.toString(tuple3._2.getTo() + 1);

            // define the expected result for each key
            List<Tuple2<String, Boolean>> keysAndExpectedResults = Arrays.asList(
                    Tuple.of(keyAtStartOfRange, true),
                    Tuple.of(keyAtEndOfRange, true),
                    Tuple.of(keyInsideRange, true),
                    Tuple.of(keyBelowRange, false),
                    Tuple.of(keyAboveRange, false));

            keysAndExpectedResults.forEach(tuple2 -> {
                LOGGER.debug("range {}, key {}, expected {}", tuple3._2, tuple2._1, tuple2._2);

                // get the proxy object
                RefDataValueProxy valueProxy = refDataStore.getValueProxy(tuple3._1, tuple2._1);

                boolean isValueExpected = tuple2._2;

                Optional<RefDataValue> optRefDataValue = valueProxy.supplyValue();

                assertThat(optRefDataValue.isPresent()).isEqualTo(isValueExpected);

                optRefDataValue.ifPresent(refDataValue -> {
                    assertThat(refDataValue).isInstanceOf(StringValue.class);
                    assertThat((StringValue) refDataValue).isEqualTo(tuple3._3);
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
        boolean didLoadHappen = refDataStore.doWithLoaderUnlessComplete(
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
                                    int cnt = counter.incrementAndGet();
                                    String key = buildKey(cnt);
                                    StringValue value = StringValue.of("value" + cnt);
                                    LOGGER.debug("Putting cnt {}, key {}, value {}", cnt, key, value);
                                    loader.put(mapDefinition, key, value);

                                    keyValueLoadedData.add(Tuple.of(mapDefinition, key, value));
                                });

                        // put keyrange/values into each mapDef
                        mapNames.stream()
                                .map(name -> new MapDefinition(refStreamDefinition, name))
                                .forEach(mapDefinition -> {
                                    int cnt = counter.incrementAndGet();
                                    Range<Long> keyRange = new Range<>((long) (cnt * 10), (long) ((cnt * 10) + 10));
                                    StringValue value = StringValue.of("value" + cnt);
                                    LOGGER.debug("Putting cnt {}, key-range {}, value {}", cnt, keyRange, value);
                                    loader.put(mapDefinition, keyRange, value);
                                    keyRangeValueLoadedData.add(Tuple.of(mapDefinition, keyRange, value));
                                });
                    }

                    loader.completeProcessing();
                });

        assertThat(didLoadHappen).isEqualTo(isLoadExpectedToHappen);

        RefDataProcessingInfo processingInfo = refDataStore.getAndTouchProcessingInfo(refStreamDefinition).get();

        assertThat(processingInfo.getProcessingState()).isEqualTo(ProcessingState.COMPLETE);

        boolean isDataLoaded = refDataStore.isDataLoaded(refStreamDefinition);
        assertThat(isDataLoaded).isTrue();

        return entriesPerMapDef * mapNames.size();
    }

    private interface MapNamFunc {
        String buildMapName(final RefStreamDefinition refStreamDefinition, final String type, final int i);

    }

}