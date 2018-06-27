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

package stroom.refdata.offheapstore;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.Range;
import stroom.properties.MockStroomPropertyService;
import stroom.properties.StroomPropertyService;
import stroom.refdata.offheapstore.databases.AbstractLmdbDbTest;
import stroom.util.ByteSizeUnit;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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

import static org.assertj.core.api.Assertions.assertThat;

public class TestRefDataOffHeapStore extends AbstractLmdbDbTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestRefDataOffHeapStore.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(TestRefDataOffHeapStore.class);

    private static final long DB_MAX_SIZE = ByteSizeUnit.MEBIBYTE.longBytes(5);

    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder();

    @Inject
    private RefDataStore refDataStore;

    private final MockStroomPropertyService mockStroomPropertyService = new MockStroomPropertyService();

    @Override
    protected long getMaxSizeBytes() {
        return DB_MAX_SIZE;
    }

    @Before
    public void setup() {

        Path dbDir = tmpDir.getRoot().toPath();
        LOGGER.debug("Creating LMDB environment in dbDir {}", dbDir.toAbsolutePath().toString());

        mockStroomPropertyService.setProperty(RefDataStoreProvider.OFF_HEAP_STORE_DIR_PROP_KEY,
                dbDir.toAbsolutePath().toString());
        mockStroomPropertyService.setProperty(RefDataStoreProvider.MAX_STORE_SIZE_BYTES_PROP_KEY,
                Long.toString(DB_MAX_SIZE));

        final Injector injector = Guice.createInjector(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(StroomPropertyService.class).toInstance(mockStroomPropertyService);
                        install(new RefDataStoreModule());
                    }
                });
        injector.injectMembers(this);
    }

    @Test
    public void getProcessingInfo() {
    }

    @Test
    public void isDataLoaded_true() {
    }

    @Test
    public void isDataLoaded_false() {
        byte version = 0;
        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                UUID.randomUUID().toString(),
                version,
                123456L);

        boolean isLoaded = refDataStore.isDataLoaded(refStreamDefinition);

        assertThat(isLoaded).isFalse();
    }

    @Test
    public void getValueProxy() {
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

        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                UUID.randomUUID().toString(),
                (byte) 0,
                123456L);
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
        ((RefDataOffHeapStore) refDataStore).logAllContents();

        assertThat((StringValue) refDataStore.getValue(mapDefinition, key).get()).isEqualTo(expectedFinalValue);

        assertThat(refDataStore.getKeyValueEntryCount()).isEqualTo(1);
    }

    private void doKeyRangeValueOverwriteTest(final boolean overwriteExisting,
                                              final StringValue value1,
                                              final StringValue value2,
                                              final StringValue expectedFinalValue) throws Exception {

        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                UUID.randomUUID().toString(),
                (byte) 0,
                123456L);
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

        ((RefDataOffHeapStore) refDataStore).logAllContents();
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

        RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                UUID.randomUUID().toString(),
                (byte) 0,
                123456L);

        // same refStreamDefinition twice to imitate a re-load
        List<RefStreamDefinition> refStreamDefinitions = Arrays.asList(
                refStreamDefinition, refStreamDefinition);

        bulkLoadAndAssert(refStreamDefinitions, false, commitInterval);
    }

    @Test
    public void loader_overwriteWithDuplicateData() throws Exception {
        int commitInterval = Integer.MAX_VALUE;

        RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                UUID.randomUUID().toString(),
                (byte) 0,
                123456L);

        // same refStreamDefinition twice to imitate a re-load
        List<RefStreamDefinition> refStreamDefinitions = Arrays.asList(
                refStreamDefinition, refStreamDefinition);

        bulkLoadAndAssert(refStreamDefinitions, true, commitInterval);
    }


    @Test
    public void testLoaderConcurrency() {

        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                UUID.randomUUID().toString(),
                (byte) 0,
                123456L);
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
                    RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                            UUID.randomUUID().toString(),
                            (byte) 0,
                            123456L);
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


    @Test
    public void testDoWithRefStreamDefinitionLock() {

        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                UUID.randomUUID().toString(),
                (byte) 0,
                123456L);

        // ensure reentrance works
        refDataStore.doWithRefStreamDefinitionLock(refStreamDefinition, () -> {
            LOGGER.debug("Got lock");
            refDataStore.doWithRefStreamDefinitionLock(refStreamDefinition, () -> {
                LOGGER.debug("Got inner lock");
            });
        });
    }

    private void bulkLoadAndAssert(final boolean overwriteExisting,
                                   final int commitInterval) {
        // two different ref stream definitions
        List<RefStreamDefinition> refStreamDefinitions = Arrays.asList(
                new RefStreamDefinition(
                        UUID.randomUUID().toString(),
                        (byte) 0,
                        123456L),
                new RefStreamDefinition(
                        UUID.randomUUID().toString(),
                        (byte) 0,
                        123456L));

        bulkLoadAndAssert(refStreamDefinitions, overwriteExisting, commitInterval);
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

            RefDataProcessingInfo refDataProcessingInfo = refDataStore.getProcessingInfo(refStreamDefinition).get();

            assertThat(refDataProcessingInfo.getProcessingState()).isEqualTo(RefDataProcessingInfo.ProcessingState.COMPLETE);

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

            // now consume the proxied value in a txn
            valueProxy.consumeBytes(typedByteBuffer -> {
                assertThat(typedByteBuffer.getTypeId()).isEqualTo(StringValue.TYPE_ID);
                String foundStrVal = StandardCharsets.UTF_8.decode(typedByteBuffer.getByteBuffer()).toString();
                assertThat(foundStrVal).isEqualTo(tuple3._3.getValue());
            });
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

                    valueProxy.consumeBytes(typedByteBuffer -> {
                        assertThat(typedByteBuffer.getTypeId()).isEqualTo(StringValue.TYPE_ID);
                        String foundStrVal = StandardCharsets.UTF_8.decode(typedByteBuffer.getByteBuffer()).toString();
                        assertThat(foundStrVal).isEqualTo(tuple3._3.getValue());
                    });
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
                                    String key = "key" + cnt;
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

        RefDataProcessingInfo processingInfo = refDataStore.getProcessingInfo(refStreamDefinition).get();

        assertThat(processingInfo.getProcessingState()).isEqualTo(RefDataProcessingInfo.ProcessingState.COMPLETE);

        boolean isDataLoaded = refDataStore.isDataLoaded(refStreamDefinition);
        assertThat(isDataLoaded).isTrue();

        return entriesPerMapDef * mapNames.size();
    }
}