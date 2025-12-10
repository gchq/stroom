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

package stroom.pipeline.refdata.store.offheapstore;

import stroom.lmdb.PutOutcome;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.NullValue;
import stroom.pipeline.refdata.store.ProcessingState;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.RefDataValueProxy;
import stroom.pipeline.refdata.store.RefStoreEntry;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.StringValue;
import stroom.pipeline.refdata.store.offheapstore.databases.ProcessingInfoDb;
import stroom.pipeline.refdata.test.RefTestUtil;
import stroom.pipeline.refdata.test.RefTestUtil.KeyOutcomeMap;
import stroom.pipeline.refdata.test.RefTestUtil.RangeOutcomeMap;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Range;
import stroom.util.sysinfo.SystemInfoResult;
import stroom.util.time.StroomDuration;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestRefDataOffHeapStore extends AbstractRefDataOffHeapStoreTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestRefDataOffHeapStore.class);

    @Test
    void isDataLoaded_false() {
        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();

        final boolean isLoaded = refDataStore.isDataLoaded(refStreamDefinition);

        assertThat(isLoaded)
                .isFalse();
    }

    @Test
    void testNoReadAhead() {
        referenceDataConfig = referenceDataConfig.withLmdbConfig(referenceDataConfig.getLmdbConfig()
                .withReadAheadEnabled(false));

        // ensure loading and reading works with the NOREADAHEAD flag set
        bulkLoadAndAssert(true, 100);
    }

    @Test
    void testConsumeEntryStream() {

        bulkLoadAndAssert(true, 100);

        final List<RefStoreEntry> entries = new ArrayList<>();
        refDataStore.consumeEntries(
                refStoreEntry ->
                        refStoreEntry.getKey().equals("key5")
                        || refStoreEntry.getKey().equals("key2"),
                null,
                entries::add);

        // 2 because we have filtered on two unique entries
        assertThat(entries)
                .hasSize(2);
    }

    @Test
    void testOverwrite_doOverwrite_keyValueStore() throws Exception {
        final StringValue value1 = StringValue.of("myValue1");
        final StringValue value2 = StringValue.of("myValue2");

        // overwriting so value changes to value2
        final StringValue expectedFinalValue = value2;

        doKeyValueOverwriteTest(true, value1, value2, expectedFinalValue);
    }

    @Test
    void testOverwrite_doOverwrite_rangeValueStore() throws Exception {
        final StringValue value1 = StringValue.of("myValue1");
        final StringValue value2 = StringValue.of("myValue2");

        // overwriting so value changes to value2
        final StringValue expectedFinalValue = value2;

        doKeyRangeValueOverwriteTest(true, value1, value2, expectedFinalValue);
    }

    @Test
    void testOverwrite_doNotOverwrite_keyValueStore() throws Exception {
        final StringValue value1 = StringValue.of("myValue1");
        final StringValue value2 = StringValue.of("myValue2");

        // no overwriting so value stays as value1
        final StringValue expectedFinalValue = value1;

        doKeyValueOverwriteTest(false, value1, value2, expectedFinalValue);
    }

    @Test
    void testOverwrite_doNotOverwrite_rangeValueStore() throws Exception {
        final StringValue value1 = StringValue.of("myValue1");
        final StringValue value2 = StringValue.of("myValue2");

        // no overwriting so value stays as value1
        final StringValue expectedFinalValue = value1;

        doKeyRangeValueOverwriteTest(false, value1, value2, expectedFinalValue);
    }

    private void doKeyValueOverwriteTest(final boolean overwriteExisting,
                                         final StringValue value1,
                                         final StringValue value2,
                                         final StringValue expectedFinalValue) throws Exception {

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
            doLoaderPut(loader, mapDefinition, key, value2);
            loader.markPutsComplete();
            loader.completeProcessing();

            assertPutOutcome(putOutcomes.get(0), true, false);
            assertPutOutcome(putOutcomes.get(1), overwriteExisting, true);
        });
        refDataStore.logAllContents(LOGGER::debug);

        assertThat((StringValue) refDataStore.getValue(mapDefinition, key).get())
                .isEqualTo(expectedFinalValue);

        assertThat(refDataStore.getKeyValueEntryCount())
                .isEqualTo(1);
    }

    private void doKeyRangeValueOverwriteTest(final boolean overwriteExisting,
                                              final StringValue value1,
                                              final StringValue value2,
                                              final StringValue expectedFinalValue) throws Exception {

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

        refDataStore.logAllContents(LOGGER::debug);
        assertThat((StringValue) refDataStore.getValue(mapDefinition, key).get())
                .isEqualTo(expectedFinalValue);

        assertThat(refDataStore.getRangeValueEntryCount())
                .isEqualTo(1);
    }

    @Test
    void loader_nullValue_keyValue_overwrite() {
        final Optional<RefDataValue> optValue = doKeyValueNullValueTest(true, 1);

        assertThat(optValue)
                .isEmpty();
    }

    @Test
    void loader_nullValue_keyValue_noOverwrite() {
        final Optional<RefDataValue> optValue = doKeyValueNullValueTest(false, 2);

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
        final String key1 = "myKey1";
        final String key2 = "myKey2";

        assertThat(refDataStore.getKeyValueEntryCount())
                .isEqualTo(0);

        refDataStore.doWithLoaderUnlessComplete(refStreamDefinition, effectiveTimeMs, loader -> {
            final KeyOutcomeMap outcomeMap = RefTestUtil.handleKeyOutcomes(loader);
            loader.initialise(overwriteExisting);

            doLoaderPut(loader, mapDefinition, key1, nonNullValue);
            doLoaderPut(loader, mapDefinition, key2, nullValue);
            // second set of puts for same keys, if overwrite should remove prev entry, else leaves it
            // Values are swapped, so null => nonnull and vice versa
            doLoaderPut(loader, mapDefinition, key1, nullValue);
            doLoaderPut(loader, mapDefinition, key2, nonNullValue);

            loader.markPutsComplete();
            loader.completeProcessing();

            outcomeMap.assertPutOutcome(mapDefinition, key1, 0, true, false);
            outcomeMap.assertPutOutcome(mapDefinition, key2, 0, true, Optional.empty());
            outcomeMap.assertPutOutcome(mapDefinition, key1, 1, overwriteExisting, true);
            outcomeMap.assertPutOutcome(mapDefinition, key2, 1, true, false);
        });
        refDataStore.logAllContents(LOGGER::debug);

        final Optional<RefDataValue> optValue = refDataStore.getValue(mapDefinition, key1);

        assertThat(refDataStore.getKeyValueEntryCount())
                .isEqualTo(expectedEntryCount);
        return optValue;
    }

    @Test
    void loader_allNulls() {

        final NullValue nullValue = NullValue.getInstance();

        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();
        final long effectiveTimeMs = System.currentTimeMillis();
        final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, "map1");
        final String keyPrefix = "myKey";

        assertThat(refDataStore.getKeyValueEntryCount())
                .isEqualTo(0);

        refDataStore.doWithLoaderUnlessComplete(refStreamDefinition, effectiveTimeMs, loader -> {
            loader.initialise(true);
            final List<PutOutcome> putOutcomes = handleKeyOutcomes(loader);

            for (int i = 1; i <= 5; i++) {
                final String key = keyPrefix + i;
                doLoaderPut(loader, mapDefinition, key, nullValue);
            }
            loader.markPutsComplete();
            loader.completeProcessing();

            for (int i = 0; i < 5; i++) {
                assertPutOutcome(putOutcomes.get(0), true, Optional.empty());
            }
        });
        refDataStore.logAllContents(LOGGER::debug);

        assertThat(refDataStore.getKeyValueEntryCount())
                .isEqualTo(0); // all nulls so none loaded
    }

    @Test
    void loader_nullValue_rangeValue_overwrite() {
        final Optional<RefDataValue> optValue = doRangeValueNullValueTest(true, 1);

        assertThat(optValue)
                .isEmpty();
    }

    @Test
    void loader_nullValue_rangeValue_noOverwrite() {
        final Optional<RefDataValue> optValue = doRangeValueNullValueTest(false, 2);

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
        final Range<Long> range1 = new Range<>(1L, 100L);
        final Range<Long> range2 = new Range<>(100L, 200L);

        assertThat(refDataStore.getRangeValueEntryCount())
                .isEqualTo(0);

        refDataStore.doWithLoaderUnlessComplete(refStreamDefinition, effectiveTimeMs, loader -> {
            loader.initialise(overwriteExisting);
//            final List<PutOutcome> putOutcomes = handleRangeOutcomes(loader);
            final RangeOutcomeMap outcomeMap = RefTestUtil.handleRangeOutcomes(loader);

            doLoaderPut(loader, mapDefinition, range1, nonNullValue);
            doLoaderPut(loader, mapDefinition, range2, nullValue);
            // second put for same key, if overwrite should remove prev entry, else leaves it
            doLoaderPut(loader, mapDefinition, range1, nullValue);
            doLoaderPut(loader, mapDefinition, range2, nonNullValue);

            loader.markPutsComplete();
            loader.completeProcessing();

            outcomeMap.assertPutOutcome(mapDefinition, range1, 0, true, false);
            outcomeMap.assertPutOutcome(mapDefinition, range2, 0, true, Optional.empty());
            outcomeMap.assertPutOutcome(mapDefinition, range1, 1, overwriteExisting, true);
            outcomeMap.assertPutOutcome(mapDefinition, range2, 1, true, false);
        });
        refDataStore.logAllContents(LOGGER::debug);

        final Optional<RefDataValue> optValue = refDataStore.getValue(mapDefinition, "50");

        assertThat(refDataStore.getRangeValueEntryCount())
                .isEqualTo(expectedEntryCount);
        return optValue;
    }

    @Test
    void loader_reloadAfterFailure() {

        final StringValue val1 = StringValue.of("foo");
        final StringValue val2 = StringValue.of("bar");
        final boolean overwriteExisting = true;

        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();
        final long effectiveTimeMs = System.currentTimeMillis();
        final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, "map1");
        final String key1 = "key1";
        final String key2 = "key2";

        assertThat(refDataStore.getKeyValueEntryCount())
                .isEqualTo(0);

        refDataStore.doWithLoaderUnlessComplete(refStreamDefinition, effectiveTimeMs, loader -> {
            loader.initialise(overwriteExisting);
            doLoaderPut(loader, mapDefinition, key1, val1);
            loader.markPutsComplete();
            loader.completeProcessing(ProcessingState.FAILED);
        });

        LOGGER.debug(LogUtil.inSeparatorLine("Dumping contents 1"));

        refDataStore.logAllContents(LOGGER::debug);

        final AtomicBoolean wasWorkDone = new AtomicBoolean(false);
        Assertions.assertThatThrownBy(
                        () -> {
                            refDataStore.doWithLoaderUnlessComplete(refStreamDefinition, effectiveTimeMs, loader -> {
                                wasWorkDone.set(true);
                                loader.initialise(overwriteExisting);
                                doLoaderPut(loader, mapDefinition, key1, val1);
                                doLoaderPut(loader, mapDefinition, key2, val2);
                                loader.markPutsComplete();
                                loader.completeProcessing(ProcessingState.COMPLETE);
                            });
                        })
                .isInstanceOf(RuntimeException.class);

        assertThat(wasWorkDone)
                .isFalse();

        LOGGER.debug(LogUtil.inSeparatorLine("Dumping contents 2"));

        refDataStore.logAllContents(LOGGER::debug);

        assertThat(refDataStore.getValue(mapDefinition, key1))
                .isPresent();
        assertThat(refDataStore.getValue(mapDefinition, key2))
                .isEmpty();
    }

    @Test
    void loader_reloadAfterCompleted() {

        final StringValue val1 = StringValue.of("foo");
        final StringValue val2 = StringValue.of("bar");
        final boolean overwriteExisting = true;

        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();
        final long effectiveTimeMs = System.currentTimeMillis();
        final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, "map1");
        final String key1 = "key1";
        final String key2 = "key2";

        assertThat(refDataStore.getKeyValueEntryCount())
                .isEqualTo(0);

        final AtomicBoolean wasWorkDone = new AtomicBoolean(false);
        refDataStore.doWithLoaderUnlessComplete(refStreamDefinition, effectiveTimeMs, loader -> {
            wasWorkDone.set(true);
            loader.initialise(overwriteExisting);
            doLoaderPut(loader, mapDefinition, key1, val1);
            loader.markPutsComplete();
            loader.completeProcessing(ProcessingState.COMPLETE);
        });

        assertThat(wasWorkDone)
                .isTrue();

        refDataStore.logAllContents(LOGGER::debug);
        wasWorkDone.set(false);

        // Last one was complete so this won't do anything
        refDataStore.doWithLoaderUnlessComplete(refStreamDefinition, effectiveTimeMs, loader -> {
            wasWorkDone.set(true);
            loader.initialise(overwriteExisting);
            doLoaderPut(loader, mapDefinition, key1, val1);
            doLoaderPut(loader, mapDefinition, key2, val2);
            loader.markPutsComplete();
            loader.completeProcessing(ProcessingState.COMPLETE);
        });
        assertThat(wasWorkDone)
                .isFalse();

        refDataStore.logAllContents(LOGGER::debug);

        assertThat(refDataStore.getValue(mapDefinition, key1))
                .isPresent();
        assertThat(refDataStore.getValue(mapDefinition, key2))
                .isEmpty();
    }

    @Test
    void testNoEntries() {
        final boolean overwriteExisting = true;
        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();
        final long effectiveTimeMs = System.currentTimeMillis();

        assertThat(refDataStore.getLoadState(refStreamDefinition))
                .isEmpty();

        assertThat(refDataStore.getKeyValueEntryCount())
                .isEqualTo(0);

        final AtomicBoolean wasWorkDone = new AtomicBoolean(false);
        refDataStore.doWithLoaderUnlessComplete(refStreamDefinition, effectiveTimeMs, loader -> {
            wasWorkDone.set(true);
            loader.initialise(overwriteExisting);

            // No puts at all

            loader.markPutsComplete();
            loader.completeProcessing(ProcessingState.COMPLETE);
        });

        assertThat(wasWorkDone)
                .isTrue();

        refDataStore.logAllContents(LOGGER::debug);

        assertThat(refDataStore.getLoadState(refStreamDefinition))
                .hasValue(ProcessingState.COMPLETE);
    }

    @TestFactory
    Stream<DynamicTest> loader_reloadAfterOtherStates() {

        return Stream.of(
                        ProcessingState.LOAD_IN_PROGRESS,
                        ProcessingState.TERMINATED)
                .map(processingState ->
                        DynamicTest.dynamicTest(processingState.getDisplayName(), () -> {
                            final StringValue val1 = StringValue.of("foo");
                            final StringValue val2 = StringValue.of("bar");
                            final boolean overwriteExisting = true;

                            final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();
                            final long effectiveTimeMs = System.currentTimeMillis();
                            final MapDefinition mapDefinition = new MapDefinition(
                                    refStreamDefinition, "map1");
                            final String key1 = "key1";
                            final String key2 = "key2";

                            assertThat(refDataStore.getKeyValueEntryCount())
                                    .isEqualTo(0);

                            final AtomicBoolean wasWorkDone = new AtomicBoolean(false);
                            refDataStore.doWithLoaderUnlessComplete(refStreamDefinition,
                                    effectiveTimeMs,
                                    loader -> {
                                        wasWorkDone.set(true);
                                        loader.initialise(overwriteExisting);
                                        doLoaderPut(loader, mapDefinition, key1, val1);

                                        // if we don't complete it will be left as load in progress
                                        if (!ProcessingState.LOAD_IN_PROGRESS.equals(processingState)) {
                                            loader.markPutsComplete();
                                            loader.completeProcessing(processingState);
                                        }
                                    });

                            assertThat(wasWorkDone)
                                    .isTrue();

                            refDataStore.logAllContents(LOGGER::debug);
                            wasWorkDone.set(false);

                            // Last one was in-complete so reload over the top
                            refDataStore.doWithLoaderUnlessComplete(refStreamDefinition,
                                    effectiveTimeMs,
                                    loader -> {
                                        wasWorkDone.set(true);
                                        loader.initialise(overwriteExisting);
                                        // Put two this time
                                        doLoaderPut(loader, mapDefinition, key1, val1);
                                        doLoaderPut(loader, mapDefinition, key2, val2);
                                        loader.markPutsComplete();
                                        loader.completeProcessing(ProcessingState.COMPLETE);
                                    });
                            assertThat(wasWorkDone)
                                    .isTrue();

                            refDataStore.logAllContents(LOGGER::debug);

                            assertThat(refDataStore.getValue(mapDefinition, key1))
                                    .isPresent();
                            assertThat(refDataStore.getValue(mapDefinition, key2))
                                    .isPresent();

                            // Purge all for next run
                            refDataStore.purgeOldData(StroomDuration.ZERO);
                        }));
    }

    @Test
    void loader_noOverwriteBigCommitInterval() throws Exception {
        final boolean overwriteExisting = false;
        final int commitInterval = Integer.MAX_VALUE;

        bulkLoadAndAssert(overwriteExisting, commitInterval);
    }

    @Test
    void loader_noOverwriteSmallCommitInterval() throws Exception {
        final boolean overwriteExisting = false;
        final int commitInterval = 2;

        bulkLoadAndAssert(overwriteExisting, commitInterval);
    }

    @Test
    void loader_noOverwriteWithDuplicateData() throws Exception {
        final int commitInterval = Integer.MAX_VALUE;

        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();

        // same refStreamDefinition twice to imitate a re-load
        final List<RefStreamDefinition> refStreamDefinitions = Arrays.asList(
                refStreamDefinition, refStreamDefinition);

        bulkLoadAndAssert(refStreamDefinitions, false, commitInterval);
    }

    @Test
    void loader_overwriteWithDuplicateData() throws Exception {
        final int commitInterval = Integer.MAX_VALUE;

        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();

        // same refStreamDefinition twice to imitate a re-load
        final List<RefStreamDefinition> refStreamDefinitions = Arrays.asList(
                refStreamDefinition, refStreamDefinition);

        bulkLoadAndAssert(refStreamDefinitions, true, commitInterval);
    }

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
                    loader.markPutsComplete();
                    loader.completeProcessing();
                    LOGGER.debug("Finished loading data");

                });

                LOGGER.debug("Getting values");
                LOGGER.logDurationIfDebugEnabled(() -> {
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

        final ExecutorService executorService = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors());
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
     * Test to make sure that multiple threads trying to load and purge the same data do not clash
     */
    @Test
    void testConcurrentLoadAndPurge() {
        final int refStreamDefCount = 5;
        final long effectiveTimeMs = System.currentTimeMillis();
        final int recCount = 1_000;
        final int iterationCount = 200;
        final int maxTaskSleepMs = 50;

        final List<RefStreamDefinition> refStreamDefinitions = new ArrayList<>(refStreamDefCount);
        final List<MapDefinition> mapDefinitions = new ArrayList<>(refStreamDefCount);

        for (int strmId = 0; strmId < refStreamDefCount; strmId++) {
            final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition(strmId);
            refStreamDefinitions.add(refStreamDefinition);
            mapDefinitions.add(new MapDefinition(refStreamDefinition, "MyMap"));
        }
        final BiConsumer<RefStreamDefinition, MapDefinition> loadTask = (refStreamDefinition, mapDefinitionKey) -> {
            LOGGER.debug("Running loadTask on thread {}", Thread.currentThread().getName());
            try {
                refDataStore.doWithLoaderUnlessComplete(refStreamDefinition, effectiveTimeMs, loader -> {
                    // Add a cheeky sleep to make the task take a bit longer
                    ThreadUtil.sleep(ThreadLocalRandom.current().nextInt(maxTaskSleepMs));
                    try {
                        loader.setCommitInterval(200);
                        final PutOutcome putOutcome = loader.initialise(true);
                        assertThat(putOutcome.isSuccess())
                                .isTrue();

                        for (int i = 0; i < recCount; i++) {
                            doLoaderPut(loader, mapDefinitionKey, "key" + i, StringValue.of("Value" + i));
                        }
                        loader.markPutsComplete();
                        loader.completeProcessing();
                        LOGGER.debug("Finished loading data");
                    } catch (final Exception e) {
                        Assertions.fail("Error: " + e.getMessage(), e);
                    }

                    LOGGER.debug("Getting values under lock");
                    LOGGER.logDurationIfDebugEnabled(() -> {
                        IntStream.range(0, recCount)
                                .boxed()
                                .sorted(Comparator.reverseOrder())
                                .forEach(i -> {
                                    final Optional<RefDataValue> optValue = refDataStore.getValue(
                                            mapDefinitionKey, "key" + i);
                                    assertThat(optValue.isPresent())
                                            .isTrue();
                                });
                    }, () -> LogUtil.message("Getting {} entries, twice", recCount));
                });


            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
            LOGGER.debug("Finished running loadTask on thread {}", Thread.currentThread().getName());
        };

        final Consumer<RefStreamDefinition> purgeTask = (refStreamDefinition) -> {
            ThreadUtil.sleep(ThreadLocalRandom.current().nextInt(maxTaskSleepMs));
            LOGGER.debug("Running purgeTask on thread {}", Thread.currentThread().getName());
            refDataStore.purge(refStreamDefinition.getStreamId());
            LOGGER.debug("Finished running purgeTask on thread {}", Thread.currentThread().getName());
        };

        final ExecutorService executorService = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors());

        final Random random = new Random();
        final List<CompletableFuture<Void>> futures = IntStream.rangeClosed(1, iterationCount)
                .boxed()
                .flatMap(i -> {
                    final int randomInt = random.nextInt(refStreamDefCount);
                    final RefStreamDefinition refStreamDefinition = refStreamDefinitions.get(randomInt);
                    final MapDefinition mapDefinition = mapDefinitions.get(randomInt);

                    final CompletableFuture<Void> loadFuture = CompletableFuture.runAsync(
                            () -> loadTask.accept(refStreamDefinition, mapDefinition),
                            executorService);
                    final CompletableFuture<Void> purgeFuture = CompletableFuture.runAsync(
                            () -> purgeTask.accept(refStreamDefinition),
                            executorService);

                    return Stream.of(loadFuture, purgeFuture);
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
    @Test
    void testLoaderConcurrency_multipleStreamDefs() {

        final long effectiveTimeMs = System.currentTimeMillis();
        final int recCount = 1_000;

        final Consumer<RefStreamDefinition> loadTask = refStreamDefinition -> {
            LOGGER.info("Running task for refStreamDef: {}", refStreamDefinition);
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
                    loader.markPutsComplete();
                    loader.completeProcessing();
                    LOGGER.debug("Finished loading data");

                });

                LOGGER.debug("Getting values");
                LOGGER.logDurationIfDebugEnabled(() -> {
                    IntStream.range(0, recCount)
                            .boxed()
                            .sorted(Comparator.reverseOrder())
                            .forEach(i -> {
                                Optional<RefDataValue> optValue =
                                        refDataStore.getValue(mapDefinitionKey, "key" + i);
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


    @Test
    void testDoWithRefStreamDefinitionLock() {
        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();
        final RefDataOffHeapStore effectiveStore = getEffectiveStore(refStreamDefinition);

        // ensure reentrance works
        effectiveStore.doWithRefStreamDefinitionLock(refStreamDefinition, () -> {
            LOGGER.debug("Got lock");
            effectiveStore.doWithRefStreamDefinitionLock(refStreamDefinition, () -> {
                LOGGER.debug("Got inner lock");
            });
        });
    }

    @Test
    void testPurgeOldData_all() {

        // two different ref stream definitions
        final List<RefStreamDefinition> refStreamDefinitions = IntStream.rangeClosed(1, REF_STREAM_DEF_COUNT)
                .boxed()
                .map(this::buildUniqueRefStreamDefinition)
                .collect(Collectors.toList());

        bulkLoadAndAssert(refStreamDefinitions, false, 0);

        referenceDataConfig = referenceDataConfig.withPurgeAge(StroomDuration.ZERO);

        assertThat(refDataStore.getProcessingInfoEntryCount())
                .isEqualTo(REF_STREAM_DEF_COUNT);
        assertThat(refDataStore.getKeyValueEntryCount())
                .isGreaterThan(0);
        assertThat(refDataStore.getRangeValueEntryCount())
                .isGreaterThan(0);

        refDataStore.logAllContents(LOGGER::debug);

        LOGGER.info("------------------------purge-starts-here--------------------------------------");

        refStreamDefinitions.forEach(refStreamDefinition -> {
            final RefDataOffHeapStore effectiveStore = getEffectiveStore(refStreamDefinition);
            LOGGER.info("\n{}", AsciiTable.fromCollection(effectiveStore.listProcessingInfo(Integer.MAX_VALUE)));
        });

        refDataStore.purgeOldData();

        assertThat(refDataStore.getProcessingInfoEntryCount())
                .isEqualTo(0);
        assertThat(refDataStore.getKeyValueEntryCount())
                .isEqualTo(0);
        assertThat(refDataStore.getRangeValueEntryCount())
                .isEqualTo(0);
    }


    @Test
    void testPurgeOldData_partial() {

        setPurgeAgeProperty(StroomDuration.ofDays(1));
        final int refStreamDefCount = 4;
        final int keyValueMapCount = 2;
        final int rangeValueMapCount = 2;
        final int entryCount = 2;
        final int totalMapEntries = (refStreamDefCount * keyValueMapCount) + (refStreamDefCount * rangeValueMapCount);
        final int totalKeyValueEntryCount = refStreamDefCount * keyValueMapCount * entryCount;
        final int totalRangeValueEntryCount = refStreamDefCount * rangeValueMapCount * entryCount;
        final int totalValueEntryCount = totalKeyValueEntryCount + totalRangeValueEntryCount;

        final List<RefStreamDefinition> refStreamDefs = loadBulkData(
                refStreamDefCount, keyValueMapCount, rangeValueMapCount, entryCount);

        refDataStore.logAllContents(LOGGER::debug);

        assertDbCounts(
                refStreamDefCount,
                totalMapEntries,
                totalKeyValueEntryCount,
                totalRangeValueEntryCount,
                totalValueEntryCount);

        final long twoDaysAgoMs = Instant.now().minus(2, ChronoUnit.DAYS).toEpochMilli();

        // set two of the refStreamDefs to be two days old so they should get purged
        setLastAccessedTime(refStreamDefs.get(1), twoDaysAgoMs);
        setLastAccessedTime(refStreamDefs.get(3), twoDaysAgoMs);

        LOGGER.info("------------------------purge-starts-here--------------------------------------");

        // do the purge
        refDataStore.purgeOldData();

        refDataStore.logAllContents(LOGGER::debug);

        final int expectedRefStreamDefCount = 2;
        assertDbCounts(
                expectedRefStreamDefCount,
                (expectedRefStreamDefCount * keyValueMapCount) + (expectedRefStreamDefCount * rangeValueMapCount),
                expectedRefStreamDefCount * keyValueMapCount * entryCount,
                expectedRefStreamDefCount * rangeValueMapCount * entryCount,
                (expectedRefStreamDefCount * rangeValueMapCount * entryCount) +
                (expectedRefStreamDefCount * rangeValueMapCount * entryCount));
    }

    @Test
    void testPurgeOldData_partial_2() {

        setPurgeAgeProperty(StroomDuration.ofDays(1));
        final int refStreamDefCount = 8;
        final int keyValueMapCount = 2;
        final int rangeValueMapCount = 2;
        final int entryCount = 2;
        final int totalMapEntries = (refStreamDefCount * keyValueMapCount) + (refStreamDefCount * rangeValueMapCount);
        final int totalKeyValueEntryCount = refStreamDefCount * keyValueMapCount * entryCount;
        final int totalRangeValueEntryCount = refStreamDefCount * rangeValueMapCount * entryCount;
        final int totalValueEntryCount = totalKeyValueEntryCount + totalRangeValueEntryCount;

        final List<RefStreamDefinition> refStreamDefs = loadBulkData(
                refStreamDefCount, keyValueMapCount, rangeValueMapCount, entryCount);

        refDataStore.logAllContents(LOGGER::debug);

        assertDbCounts(
                refStreamDefCount,
                totalMapEntries,
                totalKeyValueEntryCount,
                totalRangeValueEntryCount,
                totalValueEntryCount);

        refDataStore.purgeOldData();

        // do the purge - nothing is old/partial so no change expected
        assertDbCounts(
                refStreamDefCount,
                totalMapEntries,
                totalKeyValueEntryCount,
                totalRangeValueEntryCount,
                totalValueEntryCount);

        int expectedRefStreamDefCount = refStreamDefCount;

        assertThat(((DelegatingRefDataOffHeapStore) refDataStore).getEntryCount(ProcessingInfoDb.DB_NAME))
                .isEqualTo(expectedRefStreamDefCount);

        // Now change the states

        // These three should be purged
        setProcessingState(refStreamDefs.get(1), ProcessingState.LOAD_IN_PROGRESS);
        setProcessingState(refStreamDefs.get(3), ProcessingState.PURGE_IN_PROGRESS);
        setProcessingState(refStreamDefs.get(5), ProcessingState.TERMINATED);

        // These two won't be purged
        setProcessingState(refStreamDefs.get(6), ProcessingState.FAILED);
        setProcessingState(refStreamDefs.get(7), ProcessingState.PURGE_FAILED);

        LOGGER.info("------------------------purge-starts-here--------------------------------------");

        // do the purge
        refDataStore.purgeOldData();

        refDataStore.logAllContents(LOGGER::debug);

        expectedRefStreamDefCount = refStreamDefCount - 3;
        assertDbCounts(
                expectedRefStreamDefCount,
                (expectedRefStreamDefCount * keyValueMapCount) + (expectedRefStreamDefCount * rangeValueMapCount),
                expectedRefStreamDefCount * keyValueMapCount * entryCount,
                expectedRefStreamDefCount * rangeValueMapCount * entryCount,
                (expectedRefStreamDefCount * rangeValueMapCount * entryCount) +
                (expectedRefStreamDefCount * rangeValueMapCount * entryCount));
    }

    @Test
    void testPurgeOldData_nothingToPurge() {

        setPurgeAgeProperty(StroomDuration.ofDays(1));
        final int refStreamDefCount = 4;
        final int keyValueMapCount = 2;
        final int rangeValueMapCount = 2;
        final int entryCount = 2;
        final int totalMapEntries = (refStreamDefCount * keyValueMapCount) + (refStreamDefCount * rangeValueMapCount);
        final int totalKeyValueEntryCount = refStreamDefCount * keyValueMapCount * entryCount;
        final int totalRangeValueEntryCount = refStreamDefCount * rangeValueMapCount * entryCount;
        final int totalValueEntryCount = totalKeyValueEntryCount + totalRangeValueEntryCount;

        final List<RefStreamDefinition> refStreamDefs = loadBulkData(
                refStreamDefCount, keyValueMapCount, rangeValueMapCount, entryCount);

        refDataStore.logAllContents(LOGGER::debug);

        assertDbCounts(
                refStreamDefCount,
                totalMapEntries,
                totalKeyValueEntryCount,
                totalRangeValueEntryCount,
                totalValueEntryCount);

        LOGGER.info("------------------------purge-starts-here--------------------------------------");

        // do the purge
        refDataStore.purgeOldData();

        refDataStore.logAllContents(LOGGER::debug);

        // same as above as nothing is old enough for a purge
        assertDbCounts(
                refStreamDefCount,
                totalMapEntries,
                totalKeyValueEntryCount,
                totalRangeValueEntryCount,
                totalValueEntryCount);

    }

    @Test
    void testPurgeOldData_deReferenceValues() {

        setPurgeAgeProperty(StroomDuration.ofDays(1));
        final int refStreamDefCount = 1;
        final int keyValueMapCount = 1;
        final int rangeValueMapCount = 1;
        final int entryCount = 1;
        final int totalMapEntries = (refStreamDefCount * keyValueMapCount) + (refStreamDefCount * rangeValueMapCount);
        final int totalKeyValueEntryCount = refStreamDefCount * keyValueMapCount * entryCount;
        final int totalRangeValueEntryCount = refStreamDefCount * rangeValueMapCount * entryCount;
        final int totalValueEntryCount = totalKeyValueEntryCount + totalRangeValueEntryCount;

        final List<RefStreamDefinition> refStreamDefs = loadBulkData(
                refStreamDefCount,
                keyValueMapCount,
                rangeValueMapCount,
                entryCount,
                0,
                this::buildMapNameWithoutRefStreamDef);

        refDataStore.logAllContents(LOGGER::debug);

        assertDbCounts(
                refStreamDefCount,
                totalMapEntries,
                totalKeyValueEntryCount,
                totalRangeValueEntryCount,
                totalValueEntryCount);

        LOGGER.info("---------------------second-load-starts-here----------------------------------");

        loadBulkData(
                refStreamDefCount,
                keyValueMapCount,
                rangeValueMapCount,
                entryCount,
                4,
                this::buildMapNameWithoutRefStreamDef);

        refDataStore.logAllContents(LOGGER::debug);

        // as we have run again with different refStreamDefs we should get 2x of everything
        // except the value entries as those will be the same (except with high reference counts)
        assertDbCounts(
                refStreamDefCount * 2,
                totalMapEntries * 2,
                totalKeyValueEntryCount * 2,
                totalRangeValueEntryCount * 2,
                totalValueEntryCount);


        final long twoDaysAgoMs = Instant.now().minus(2, ChronoUnit.DAYS).toEpochMilli();

        // set two of the refStreamDefs to be two days old so they should get purged
        setLastAccessedTime(refStreamDefs.get(0), twoDaysAgoMs);

        LOGGER.info("------------------------purge-starts-here--------------------------------------");
        // do the purge
        refDataStore.purgeOldData();

        refDataStore.logAllContents(LOGGER::debug);

        // back to how it was after first load with no change to value entry count as they have just been de-referenced
        assertDbCounts(
                refStreamDefCount,
                totalMapEntries,
                totalKeyValueEntryCount,
                totalRangeValueEntryCount,
                totalValueEntryCount);
    }

    @Test
    void testPurgeRefStream() {

        // two different ref stream definitions
        final List<RefStreamDefinition> refStreamDefinitions = Arrays.asList(
                buildUniqueRefStreamDefinition(1),
                buildUniqueRefStreamDefinition(2),
                buildUniqueRefStreamDefinition(3),
                buildUniqueRefStreamDefinition(4));

        bulkLoadAndAssert(refStreamDefinitions, false, 1000);

        referenceDataConfig = referenceDataConfig.withPurgeAge(StroomDuration.ZERO);

        final int entriesPerRefStream = MAPS_PER_REF_STREAM_DEF * ENTRIES_PER_MAP_DEF;

        assertThat(refDataStore.getProcessingInfoEntryCount())
                .isEqualTo(4);
        assertThat(refDataStore.getKeyValueEntryCount())
                .isEqualTo((long) refStreamDefinitions.size() * entriesPerRefStream);
        assertThat(refDataStore.getRangeValueEntryCount())
                .isEqualTo((long) refStreamDefinitions.size() * entriesPerRefStream);

        refDataStore.logAllContents(LOGGER::debug);

        LOGGER.info("------------------------purge-starts-here--------------------------------------");
        refDataStore.purge(2, 0);

        // We have purged one ref stream so there is now one less
        final int postPurgeRefStreamCount = refStreamDefinitions.size() - 1;

        assertThat(refDataStore.getProcessingInfoEntryCount())
                .isEqualTo(postPurgeRefStreamCount);
        assertThat(refDataStore.getKeyValueEntryCount())
                .isEqualTo((long) postPurgeRefStreamCount * entriesPerRefStream);
        assertThat(refDataStore.getRangeValueEntryCount())
                .isEqualTo((long) postPurgeRefStreamCount * entriesPerRefStream);
    }

    /**
     * Make entryCount very big for manual performance testing or profiling
     * 50_000 takes about 4mins and makes a 250Mb db file.
     */
    @Test
    void testBigLoadForPerfTesting() {

        // Wait for visualvm to spin up
        try {
            Thread.sleep(0_000);
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }

        doBigLoadGetAndPurgeForPerfTesting(5, true, false, false, true);
    }

    @Test
    void testBigLoadAndPurgeForPerfTesting() {

        // Wait for visualvm to spin up
        try {
            Thread.sleep(0_000);
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }

        doBigLoadGetAndPurgeForPerfTesting(5, true, false, true, true);
    }

    @Test
    void testConcurrentBigLoadAndGet() {
        // Wait for visualvm to spin up
        try {
            Thread.sleep(0);
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }

        final int entryCount = 5;
        final int threads = 6;

        final ExecutorService executorService = Executors.newFixedThreadPool(threads);

        final List<CompletableFuture<Void>> futures = IntStream.rangeClosed(1, threads)
                .boxed()
                .map(i -> {
                    LOGGER.info("Creating future {}", i);
                    return CompletableFuture.runAsync(() -> {
                        LOGGER.info("Running load {} on thread {}", i, Thread.currentThread().getName());
                        doBigLoadGetAndPurgeForPerfTesting(
                                entryCount, true, true, false, false);
                    }, executorService);
                })
                .collect(Collectors.toList());

        futures.forEach(cf -> {
            try {
                cf.get();
            } catch (final InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });

        final SystemInfoResult systemInfo = byteBufferPool.getSystemInfo();

        LOGGER.info(systemInfo.toString());
    }

    @Test
    void testLoadAndConcurrentGets() {
        // Wait for visualvm to spin up
        try {
            Thread.sleep(0);
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }

        final int entryCount = 5;
        final int threads = 6;

        // Load the data
        doBigLoadGetAndPurgeForPerfTesting(entryCount, true, false, false, true);

        final ExecutorService executorService = Executors.newFixedThreadPool(threads);

        final List<CompletableFuture<Void>> futures = IntStream.rangeClosed(1, threads)
                .boxed()
                .map(i -> {
                    LOGGER.info("Creating future {}", i);
                    return CompletableFuture.runAsync(() -> {
                        LOGGER.info("Running load {} on thread {}", i, Thread.currentThread().getName());
                        // Now query the data
                        doBigLoadGetAndPurgeForPerfTesting(
                                entryCount, false, true, false, true);
                    }, executorService);
                })
                .collect(Collectors.toList());

        futures
                .forEach(cf -> {
                    try {
                        cf.get();
                    } catch (final InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                });

        final SystemInfoResult systemInfo = byteBufferPool.getSystemInfo();

        LOGGER.info(systemInfo.toString());
    }

    /**
     * Make entryCount very big for manual performance testing or profiling
     */
    @Test
    void testBigLoadAndGetForPerfTesting() {
        // Wait for visualvm to spin up
        try {
            Thread.sleep(00_000);
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }

        doBigLoadGetAndPurgeForPerfTesting(5, true, true, false, true);

    }

    /**
     * Make entryCount very big for manual performance testing or profiling
     */
    @Disabled // manual perf testing only
    @Test
    void testBigLoadGetAndPurgeForPerfTesting() {
        referenceDataConfig = referenceDataConfig.withMaxPurgeDeletesBeforeCommit(200_000);
        doPurgePerfTest(50_000);
    }


    /**
     * Make entryCount very big for manual performance testing or profiling
     */
    private void doBigLoadGetAndPurgeForPerfTesting(final int entryCount,
                                                    final boolean doLoad,
                                                    final boolean doGets,
                                                    final boolean doPurges,
                                                    final boolean doAsserts) {

        final Instant fullTestStartTime = Instant.now();

        final MapNameFunc mapNameFunc = this::buildMapNameWithoutRefStreamDef;

        setPurgeAgeProperty(StroomDuration.ofDays(1));
        final int refStreamDefCount = 5;
        final int keyValueMapCount = 2;
        final int rangeValueMapCount = 2;
        final int totalMapEntries = (refStreamDefCount * keyValueMapCount) + (refStreamDefCount * rangeValueMapCount);

        final int totalKeyValueEntryCount = refStreamDefCount * keyValueMapCount * entryCount;
        final int totalRangeValueEntryCount = refStreamDefCount * rangeValueMapCount * entryCount;
        final int totalValueEntryCount = (totalKeyValueEntryCount + totalRangeValueEntryCount) / refStreamDefCount;

        List<RefStreamDefinition> refStreamDefs1 = null;
        List<RefStreamDefinition> refStreamDefs2 = null;

        final Instant startInstant = Instant.now();

        if (doLoad) {
            LOGGER.info("-------------------------load starts here--------------------------------------");
            refStreamDefs1 = loadBulkData(
                    refStreamDefCount,
                    keyValueMapCount,
                    rangeValueMapCount,
                    entryCount,
                    0,
                    mapNameFunc);

            if (doAsserts) {
                assertDbCounts(
                        refStreamDefCount,
                        totalMapEntries,
                        totalKeyValueEntryCount,
                        totalRangeValueEntryCount,
                        totalValueEntryCount);
            }

            // here to aid debugging problems at low volumes
            if (entryCount < 10) {
                refDataStore.logAllContents(LOGGER::debug);
            }

            LOGGER.info("-----------------------second-load starts here----------------------------------");

            refStreamDefs2 = loadBulkData(
                    refStreamDefCount,
                    keyValueMapCount,
                    rangeValueMapCount,
                    entryCount,
                    refStreamDefCount,
                    mapNameFunc);

            LOGGER.info("Completed both loads in {}",
                    Duration.between(startInstant, Instant.now()).toString());
        }

        if (doAsserts) {
            assertDbCounts(
                    refStreamDefCount * 2,
                    totalMapEntries * 2,
                    totalKeyValueEntryCount * 2,
                    totalRangeValueEntryCount * 2,
                    totalValueEntryCount);
        }

        if (doGets) {
            LOGGER.info("-------------------------gets start here---------------------------------------");

            // In case the load was done elsewhere
            if (refStreamDefs1 == null) {
                refStreamDefs1 = buildRefStreamDefs(refStreamDefCount, 0);
            }
            if (refStreamDefs2 == null) {
                refStreamDefs2 = buildRefStreamDefs(refStreamDefCount, refStreamDefCount);
            }

            final Random random = new Random();
            // for each ref stream def & map def, have N goes at picking a random key and getting the value for it
            Stream.concat(refStreamDefs1.stream(), refStreamDefs2.stream()).forEach(refStreamDef -> {
                final Instant startTime = Instant.now();
                Stream.of(KV_TYPE, RANGE_TYPE).forEach(valueType -> {
                    for (int i = 0; i < entryCount; i++) {

                        final String mapName = mapNameFunc.buildMapName(refStreamDef,
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

        if (doPurges) {

            LOGGER.info("------------------------purge-starts-here--------------------------------------");

            final long twoDaysAgoMs = Instant.now().minus(2, ChronoUnit.DAYS).toEpochMilli();

            refStreamDefs1.forEach(refStreamDefinition -> setLastAccessedTime(refStreamDefinition, twoDaysAgoMs));

            // do the purge
            refDataStore.purgeOldData();

            if (doAsserts) {
                assertDbCounts(
                        refStreamDefCount,
                        totalMapEntries,
                        totalKeyValueEntryCount,
                        totalRangeValueEntryCount,
                        totalValueEntryCount);
            }

            LOGGER.info("--------------------second-purge-starts-here------------------------------------");

            refStreamDefs2.forEach(refStreamDefinition -> setLastAccessedTime(refStreamDefinition, twoDaysAgoMs));

            // do the purge
            refDataStore.purgeOldData();

            if (doAsserts) {
                assertDbCounts(
                        0,
                        0,
                        0,
                        0,
                        0);
            }
        }

        final SystemInfoResult systemInfo = byteBufferPool.getSystemInfo();

        LOGGER.info(systemInfo.toString());

        LOGGER.info("Full test time {}", Duration.between(fullTestStartTime, Instant.now()));
    }

    /**
     * Make entryCount very big for manual performance testing or profiling
     */
    private void doPurgePerfTest(final int entryCount) {
        final Scanner scan = new Scanner(System.in);

        System.out.print("Press any key to continue . . . ");
        scan.nextLine();

        final Instant fullTestStartTime = Instant.now();

        final MapNameFunc mapNameFunc = this::buildMapNameWithoutRefStreamDef;

        setPurgeAgeProperty(StroomDuration.ofDays(1));
        final int refStreamDefCount = 30;
        final int keyValueMapCount = 2;
        final int rangeValueMapCount = 0;
        final int totalMapEntries = (refStreamDefCount * keyValueMapCount) + (refStreamDefCount * rangeValueMapCount);

        final int totalKeyValueEntryCount = refStreamDefCount * keyValueMapCount * entryCount;
        final int totalRangeValueEntryCount = refStreamDefCount * rangeValueMapCount * entryCount;
        final int totalValueEntryCount = (totalKeyValueEntryCount + totalRangeValueEntryCount) / refStreamDefCount;

        List<RefStreamDefinition> refStreamDefs1 = null;

        final Instant startInstant = Instant.now();

        LOGGER.info("-------------------------load starts here--------------------------------------");
        refStreamDefs1 = loadBulkDataWithLargeValues(
                refStreamDefCount,
                keyValueMapCount,
                entryCount,
                0,
                mapNameFunc);

        // here to aid debugging problems at low volumes
        if (entryCount < 10) {
            refDataStore.logAllContents(LOGGER::info);
        }

        LOGGER.info("Size on disk: {}", ModelStringUtil.formatIECByteSizeString(refDataStore.getSizeOnDisk()));
        LOGGER.info("KV entry count: {}, value count: {}",
                ModelStringUtil.formatCsv(refDataStore.getKeyValueEntryCount()),
                ModelStringUtil.formatCsv(((RefDataOffHeapStore) refDataStore).getValueStoreCount()));

        System.out.print("Press any key to continue . . . ");
        scan.nextLine();

        LOGGER.info("------------------------purge-starts-here--------------------------------------");

        final long twoDaysAgoMs = Instant.now().minus(2, ChronoUnit.DAYS).toEpochMilli();

        refStreamDefs1.forEach(refStreamDefinition -> setLastAccessedTime(refStreamDefinition, twoDaysAgoMs));

        // do the purge
        refDataStore.purgeOldData();

        final SystemInfoResult systemInfo = byteBufferPool.getSystemInfo();

        LOGGER.info(systemInfo.toString());

        LOGGER.info("Full test time {}", Duration.between(fullTestStartTime, Instant.now()));
    }

    @Disabled // Manual run only
    @Test
    void testLookupPerf() {
        final MapNameFunc mapNameFunc = this::buildMapNameWithoutRefStreamDef;

        final int entryCount = 5_000;
        final int refStreamDefCount = 5;
        final int keyValueMapCount = 20;
        final int rangeValueMapCount = 0;

        final int totalKeyValueEntryCount = refStreamDefCount * keyValueMapCount * entryCount;

        final List<RefStreamDefinition> refStreamDefinitions = loadBulkData(
                refStreamDefCount,
                keyValueMapCount,
                rangeValueMapCount,
                entryCount,
                0,
                mapNameFunc);

        assertThat(refStreamDefinitions)
                .hasSize(refStreamDefCount);
        final RefStreamDefinition refStreamDefinition = refStreamDefinitions.get(0);

        final long keyValueEntryCount = refDataStore.getKeyValueEntryCount();

        assertThat(keyValueEntryCount)
                .isEqualTo(totalKeyValueEntryCount);

        final AtomicInteger cnt = new AtomicInteger();
        refDataStore.consumeEntries(val -> true, val -> cnt.incrementAndGet() <= 10, entry -> {
            LOGGER.info("map: {}, key: {}, val: {}",
                    entry.getMapDefinition().getMapName(),
                    entry.getKey(),
                    entry.getValue());
        });

        // refStrmIdx => mapDefs
        final Map<Integer, List<MapDefinition>> mapDefinitionsMap = new HashMap<>(refStreamDefCount);

        for (int refStrmIdx = 0; refStrmIdx < refStreamDefCount; refStrmIdx++) {
            final List<MapDefinition> mapDefs = mapDefinitionsMap.computeIfAbsent(
                    refStrmIdx,
                    k -> new ArrayList<>(keyValueMapCount));
            for (int mapIdx = 0; mapIdx < keyValueMapCount; mapIdx++) {
                final String mapName = mapNameFunc.buildMapName(refStreamDefinition, KV_TYPE, mapIdx);
                final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, mapName);
                mapDefs.add(mapDefinition);
            }
        }

        final Random random = new Random(892374809);

        final Runnable work = () -> {
            final int refStrmIdx = random.nextInt(refStreamDefCount);
            final int mapIdx = random.nextInt(keyValueMapCount);
            final int keyIdx = random.nextInt(entryCount);
            final MapDefinition mapDef = mapDefinitionsMap.get(refStrmIdx).get(mapIdx);
            final StringValue value = (StringValue) refDataStore.getValue(mapDef, buildKey(keyIdx))
                    .orElseThrow();

            Objects.requireNonNull(value.getValue());
        };
        DurationTimer timer;

        LOGGER.info("Starting multi thread lookups");
        timer = DurationTimer.start();
        IntStream.rangeClosed(0, totalKeyValueEntryCount)
                .boxed()
                .parallel()
                .forEach(i -> work.run());

        LOGGER.info("Completed {} multi thread lookups in {}",
                ModelStringUtil.formatCsv(totalKeyValueEntryCount),
                timer);

        LOGGER.info("Starting single thread lookups");
        timer = DurationTimer.start();
        for (int i = 0; i < totalKeyValueEntryCount; i++) {
            work.run();
        }

        LOGGER.info("Completed {} single thread lookups in {}",
                ModelStringUtil.formatCsv(totalKeyValueEntryCount),
                timer);
    }
}
