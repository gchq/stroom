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

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.PooledByteBufferOutputStream;
import stroom.lmdb.PutOutcome;
import stroom.pipeline.refdata.ReferenceDataConfig;
import stroom.pipeline.refdata.ReferenceDataLmdbConfig;
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
import stroom.pipeline.refdata.store.offheapstore.databases.KeyValueStoreDb;
import stroom.pipeline.refdata.store.offheapstore.databases.MapUidForwardDb;
import stroom.pipeline.refdata.store.offheapstore.databases.MapUidReverseDb;
import stroom.pipeline.refdata.store.offheapstore.databases.ProcessingInfoDb;
import stroom.pipeline.refdata.store.offheapstore.databases.RangeStoreDb;
import stroom.pipeline.refdata.store.offheapstore.databases.ValueStoreDb;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Range;
import stroom.util.time.StroomDuration;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractRefDataOffHeapStoreTest extends StroomUnitTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractRefDataOffHeapStoreTest.class);

    protected static final String KV_TYPE = "KV";
    protected static final String RANGE_TYPE = "Range";
    protected static final String PADDING = IntStream.rangeClosed(1, 300)
            .boxed()
            .map(i -> "-")
            .collect(Collectors.joining());

    protected static final int REF_STREAM_DEF_COUNT = 2;
    protected static final int ENTRIES_PER_MAP_DEF = 10;
    protected static final int MAPS_PER_REF_STREAM_DEF = 2;

    @SuppressWarnings("checkstyle:LineLength")
    // 1916 bytes
    protected static final String LOREM_IPSUM = """
            Lorem ipsum dolor sit amet, consectetur adipiscing elit. Cras commodo sit amet sem non posuere. Sed nec venenatis mi, vel facilisis nisl. Maecenas commodo id nulla quis gravida. Morbi nec lacinia ante, quis iaculis risus. Nulla at leo quis orci rutrum congue at id lorem. Vivamus venenatis porta mauris, pellentesque porta risus maximus eget. Sed molestie id lectus vel fermentum. Integer quis metus quis ante elementum auctor eget eget augue.

            Aenean fringilla porta ultrices. Nullam mollis rhoncus commodo. Nunc scelerisque ex velit, eu dignissim purus eleifend vitae. Sed sit amet hendrerit tortor, et feugiat turpis. Etiam eu lacinia ipsum. Pellentesque tincidunt elit odio. Donec venenatis eros eget sem congue finibus. Aenean at euismod ante. Vestibulum pharetra vehicula libero, ac aliquam purus imperdiet ac. Aenean viverra posuere sapien, ac tempus libero bibendum tempor. Sed sed pretium mauris. Nunc ullamcorper, urna vel congue blandit, tellus diam gravida elit, sed scelerisque mi dui quis dui. Fusce sem justo, tincidunt sit amet sagittis ut, malesuada ultricies dui. Nam lacinia diam vel ex vestibulum, ac sodales magna aliquet.

            Aenean egestas, sapien nec interdum mattis, purus ex euismod enim, sit amet blandit ante libero quis nisl. Duis malesuada quis ex a feugiat. Sed in cursus ex. Fusce massa leo, interdum vitae nibh at, imperdiet sagittis ante. Aenean volutpat mauris eu orci scelerisque rhoncus. Suspendisse non nisi vel mi luctus dignissim id in ipsum. Sed pellentesque elit nulla, rhoncus luctus dui commodo ullamcorper. Vivamus aliquam felis eu lorem elementum, vel lacinia ante pellentesque. Donec at rutrum metus. Nullam ut turpis malesuada, volutpat est at, consectetur lectus. Nunc pretium bibendum ante sed imperdiet. Suspendisse a est id neque consectetur malesuada. Morbi in ligula ut magna porta pulvinar vitae non mi. Phasellus cursus turpis nulla, non placerat mi faucibus maximus.""";


    @Inject
    protected RefDataStoreFactory refDataStoreFactory;
    @Inject
    protected ByteBufferPool byteBufferPool;
    @Inject
    protected PooledByteBufferOutputStream.Factory pooledByteBufferOutputStreamFactory;
    @Inject
    protected ValueStoreHashAlgorithm valueStoreHashAlgorithm;

    protected RefDataStoreTestModule refDataStoreTestModule;
    protected ReferenceDataConfig referenceDataConfig = new ReferenceDataConfig();
    protected Injector injector;
    protected RefDataStore refDataStore;
//    protected Path dbDir = null;

    @BeforeEach
    void setup() throws IOException {
//        dbDir = Files.createTempDirectory("stroom");
//        Files.createDirectories(dbDir);
//        FileUtil.deleteContents(dbDir);

//        LOGGER.info("Creating LMDB environment in dbDir {}", dbDir.toAbsolutePath().toString());
        LOGGER.info("Creating LMDB environment in dbDir {}", getCurrentTestDir().toAbsolutePath());

        // This should ensure batching is exercised, including partial batches
        final int batchSize = Math.max(1, ENTRIES_PER_MAP_DEF / 2) - 1;
        LOGGER.debug("Using batchSize {}", batchSize);
        referenceDataConfig = new ReferenceDataConfig()
                .withLmdbConfig(new ReferenceDataLmdbConfig()
//                        .withLocalDir(dbDir.toAbsolutePath().toString())
                        .withLocalDir(getCurrentTestDir().toAbsolutePath().toString())
                        .withReaderBlockedByWriter(false))
                .withMaxPutsBeforeCommit(batchSize)
                .withMaxPurgeDeletesBeforeCommit(batchSize);

        refDataStoreTestModule = new RefDataStoreTestModule(
                this::getReferenceDataConfig,
                this::getCurrentTestDir,
                this::getCurrentTestDir);

        injector = Guice.createInjector(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        install(refDataStoreTestModule);
                    }
                });

        injector.injectMembers(this);
        refDataStore = refDataStoreFactory.getOffHeapStore();
    }

    protected void assertDbCounts(final int refStreamDefCount,
                                  final int totalMapEntries,
                                  final int totalKeyValueEntryCount,
                                  final int totalRangeValueEntryCount,
                                  final int totalValueEntryCount) {

        assertThat(((DelegatingRefDataOffHeapStore) refDataStore).getEntryCount(ProcessingInfoDb.DB_NAME))
                .isEqualTo(refStreamDefCount);
        assertThat(((DelegatingRefDataOffHeapStore) refDataStore).getEntryCount(MapUidForwardDb.DB_NAME))
                .isEqualTo(totalMapEntries);
        assertThat(((DelegatingRefDataOffHeapStore) refDataStore).getEntryCount(MapUidReverseDb.DB_NAME))
                .isEqualTo(totalMapEntries);
        assertThat(((DelegatingRefDataOffHeapStore) refDataStore).getEntryCount(KeyValueStoreDb.DB_NAME))
                .isEqualTo(totalKeyValueEntryCount);
        assertThat(((DelegatingRefDataOffHeapStore) refDataStore).getEntryCount(RangeStoreDb.DB_NAME))
                .isEqualTo(totalRangeValueEntryCount);
        assertThat(((DelegatingRefDataOffHeapStore) refDataStore).getEntryCount(ValueStoreDb.DB_NAME))
                .isEqualTo(totalValueEntryCount);
    }

    protected RefDataOffHeapStore getEffectiveStore(final RefStreamDefinition refStreamDefinition) {
        return ((DelegatingRefDataOffHeapStore) refDataStore).getEffectiveStore(
                refStreamDefinition);
    }

    protected void setLastAccessedTime(final RefStreamDefinition refStreamDef, final long newLastAccessedTimeMs) {
        getEffectiveStore(refStreamDef).setLastAccessedTime(refStreamDef, newLastAccessedTimeMs);
    }

    protected void setProcessingState(final RefStreamDefinition refStreamDef, final ProcessingState processingState) {
        getEffectiveStore(refStreamDef).setProcessingState(refStreamDef, processingState);
    }

    protected RefStreamDefinition buildUniqueRefStreamDefinition(final long streamId) {
        refDataStoreTestModule.addMetaFeedAssociation(streamId, RefDataStoreTestModule.FEED_1_NAME);

        return new RefStreamDefinition(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                streamId);
    }

    protected RefStreamDefinition buildUniqueRefStreamDefinition() {
        // This is a default mapping so no need to add it
        return new RefStreamDefinition(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                RefDataStoreTestModule.REF_STREAM_1_ID);
    }

    protected void bulkLoadAndAssert(final boolean overwriteExisting,
                                     final int commitInterval) {
        final List<RefStreamDefinition> refStreamDefinitions = IntStream.rangeClosed(1, REF_STREAM_DEF_COUNT)
                .boxed()
                .map(i -> buildUniqueRefStreamDefinition())
                .collect(Collectors.toList());

        bulkLoadAndAssert(refStreamDefinitions, overwriteExisting, commitInterval, true);
    }

    protected List<RefStreamDefinition> loadBulkData(
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

    protected List<RefStreamDefinition> buildRefStreamDefs(final int count, final int offset) {

        return IntStream.rangeClosed(1, count)
                .boxed()
                .map(i -> buildRefStreamDefinition(i + offset))
                .collect(Collectors.toList());
    }

    /**
     * @param refStreamDefinitionCount  Number of {@link RefStreamDefinition}s to create.
     * @param keyValueMapCount          Number of KeyValue type maps to create per {@link RefStreamDefinition}
     * @param entryCount                Number of map entries to create per map
     * @param refStreamDefinitionOffset The offset from zero for the refStreamDefinition partIndex
     * @return The created {@link RefStreamDefinition} objects
     */
    protected List<RefStreamDefinition> loadBulkDataWithLargeValues(
            final int refStreamDefinitionCount,
            final int keyValueMapCount,
            final int entryCount,
            final int refStreamDefinitionOffset,
            final MapNameFunc mapNameFunc) {

        assertThat(refStreamDefinitionCount)
                .isGreaterThan(0);
        assertThat(keyValueMapCount)
                .isGreaterThanOrEqualTo(0);
        assertThat(entryCount)
                .isGreaterThan(0);

        final List<RefStreamDefinition> refStreamDefinitions = new ArrayList<>();

        final Instant startInstant = Instant.now();

        buildRefStreamDefs(refStreamDefinitionCount, refStreamDefinitionOffset)
                .forEach(refStreamDefinition -> {
                    refStreamDefinitions.add(refStreamDefinition);

                    refDataStore.doWithLoaderUnlessComplete(
                            refStreamDefinition,
                            System.currentTimeMillis(),
                            loader -> {
                                loader.initialise(false);
                                loader.setCommitInterval(32_000);

                                loadKeyValueDataWithLargeValues(
                                        keyValueMapCount,
                                        entryCount,
                                        refStreamDefinition,
                                        loader,
                                        mapNameFunc);

                                loader.completeProcessing();
                            });
                });

        LOGGER.info("Loaded {} ref stream definitions in {}",
                refStreamDefinitionCount, Duration.between(startInstant, Instant.now()).toString());

        LOGGER.info("Counts:, KeyValue: {}, KeyRangeValue: {}, ProcInfo: {}",
                refDataStore.getKeyValueEntryCount(),
                refDataStore.getRangeValueEntryCount(),
                refDataStore.getProcessingInfoEntryCount());

        return refStreamDefinitions;
    }

    /**
     * @param refStreamDefinitionCount  Number of {@link RefStreamDefinition}s to create.
     * @param keyValueMapCount          Number of KeyValue type maps to create per {@link RefStreamDefinition}
     * @param rangeValueMapCount        Number of RangeValue type maps to create per {@link RefStreamDefinition}
     * @param entryCount                Number of map entries to create per map
     * @param refStreamDefinitionOffset The offset from zero for the refStreamDefinition partIndex
     * @return The created {@link RefStreamDefinition} objects
     */
    protected List<RefStreamDefinition> loadBulkData(
            final int refStreamDefinitionCount,
            final int keyValueMapCount,
            final int rangeValueMapCount,
            final int entryCount,
            final int refStreamDefinitionOffset,
            final MapNameFunc mapNameFunc) {

        assertThat(refStreamDefinitionCount)
                .isGreaterThan(0);
        assertThat(keyValueMapCount)
                .isGreaterThanOrEqualTo(0);
        assertThat(rangeValueMapCount)
                .isGreaterThanOrEqualTo(0);
        assertThat(entryCount)
                .isGreaterThan(0);

        final List<RefStreamDefinition> refStreamDefinitions = new ArrayList<>();

        final Instant startInstant = Instant.now();

        buildRefStreamDefs(refStreamDefinitionCount, refStreamDefinitionOffset)
                .forEach(refStreamDefinition -> {
                    refStreamDefinitions.add(refStreamDefinition);

                    refDataStore.doWithLoaderUnlessComplete(
                            refStreamDefinition,
                            System.currentTimeMillis(),
                            loader -> {
                                loader.initialise(false);
                                loader.setCommitInterval(32_000);

                                loadKeyValueData(
                                        keyValueMapCount,
                                        entryCount,
                                        refStreamDefinition,
                                        loader,
                                        mapNameFunc);

                                loadRangeValueData(keyValueMapCount,
                                        entryCount,
                                        refStreamDefinition,
                                        loader,
                                        mapNameFunc);

                                loader.completeProcessing();
                            });
                });

        LOGGER.info("Loaded {} ref stream definitions in {}",
                refStreamDefinitionCount, Duration.between(startInstant, Instant.now()).toString());

        LOGGER.info("Counts:, KeyValue: {}, KeyRangeValue: {}, ProcInfo: {}",
                refDataStore.getKeyValueEntryCount(),
                refDataStore.getRangeValueEntryCount(),
                refDataStore.getProcessingInfoEntryCount());

        return refStreamDefinitions;
    }

    protected RefStreamDefinition buildRefStreamDefinition(final long i) {
        refDataStoreTestModule.addMetaFeedAssociation(i, RefDataStoreTestModule.FEED_1_NAME);
        return new RefStreamDefinition(
                RefDataStoreTestModule.PIPE_1_UUID,
                RefDataStoreTestModule.PIPE_1_VER_1,
                i);
    }

    protected void loadRangeValueData(final int keyValueMapCount,
                                      final int entryCount,
                                      final RefStreamDefinition refStreamDefinition,
                                      final RefDataLoader loader) {
        loadRangeValueData(keyValueMapCount,
                entryCount,
                refStreamDefinition,
                loader,
                this::buildMapNameWithRefStreamDef);
    }

    protected void loadRangeValueData(final int keyValueMapCount,
                                      final int entryCount,
                                      final RefStreamDefinition refStreamDefinition,
                                      final RefDataLoader loader,
                                      final MapNameFunc mapNameFunc) {
        // load the range/value data
        for (int j = 0; j < keyValueMapCount; j++) {
            final String mapName = mapNameFunc.buildMapName(refStreamDefinition, RANGE_TYPE, j);
            final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, mapName);

            for (int k = 0; k < entryCount; k++) {
                final Range<Long> range = buildRangeKey(k);
                final String value = buildRangeStoreValue(mapName, k, range);
                doLoaderPut(loader, mapDefinition, range, StringValue.of(value));
            }
        }
    }

    protected Range<Long> buildRangeKey(final int k) {
        return Range.of((long) k * 10, (long) (k * 10) + 10);
    }


    protected void loadKeyValueData(final int keyValueMapCount,
                                    final int entryCount,
                                    final RefStreamDefinition refStreamDefinition,
                                    final RefDataLoader loader) {
        loadKeyValueData(keyValueMapCount, entryCount, refStreamDefinition, loader, this::buildMapNameWithRefStreamDef);
    }

    protected void loadKeyValueData(final int keyValueMapCount,
                                    final int entryCount,
                                    final RefStreamDefinition refStreamDefinition,
                                    final RefDataLoader loader,
                                    final MapNameFunc mapNameFunc) {
        // load the key/value data
        for (int j = 0; j < keyValueMapCount; j++) {
            final String mapName = mapNameFunc.buildMapName(refStreamDefinition, KV_TYPE, j);
            final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, mapName);

            for (int k = 0; k < entryCount; k++) {
                final String key = buildKey(k);
                final String value = buildKeyStoreValue(mapName, k, key);
                doLoaderPut(loader, mapDefinition, key, StringValue.of(value));
            }
        }
    }

    protected void loadKeyValueDataWithLargeValues(final int keyValueMapCount,
                                                   final int entryCount,
                                                   final RefStreamDefinition refStreamDefinition,
                                                   final RefDataLoader loader,
                                                   final MapNameFunc mapNameFunc) {
        // load the key/value data
        for (int j = 0; j < keyValueMapCount; j++) {
            final String mapName = mapNameFunc.buildMapName(refStreamDefinition, KV_TYPE, j);
            final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, mapName);

            for (int k = 0; k < entryCount; k++) {
                final String key = buildKey(k);
                final String value = LogUtil.message("{}-{}-value{}{}",
                        mapName, key, k, LOREM_IPSUM);
                doLoaderPut(loader, mapDefinition, key, StringValue.of(value));
            }
        }
    }

    protected String buildRangeStoreValue(final String mapName, final int i, final Range<Long> range) {
        // pad the values out to make them more realistic in length to see impact on writes
        return LogUtil.message("{}-{}-{}-value{}{}",
                mapName, range.getFrom(), range.getTo(), i, PADDING);
    }

    protected String buildMapNameWithRefStreamDef(
            final RefStreamDefinition refStreamDefinition,
            final String type,
            final int i) {
        return LogUtil.message("refStreamDef{}-{}map{}",
                refStreamDefinition.getStreamId(), type, i);
    }

    protected String buildMapNameWithoutRefStreamDef(
            final RefStreamDefinition refStreamDefinition,
            final String type,
            final int i) {
        return LogUtil.message("{}map{}",
                type, i);
    }

    protected String buildKeyStoreValue(final String mapName,
                                        final int i,
                                        final String key) {
        // pad the values out to make them more realistic in length to see impact on writes
        return LogUtil.message("{}-{}-value{}{}", mapName, key, i, PADDING);
    }

    protected String buildKey(final int k) {
        return "key" + k;
    }

    protected void bulkLoad(final List<RefStreamDefinition> refStreamDefinitions,
                            final boolean overwriteExisting,
                            final int commitInterval) {
        bulkLoadAndAssert(refStreamDefinitions, overwriteExisting, commitInterval, false, refDataStore);

    }

    protected void bulkLoadAndAssert(final List<RefStreamDefinition> refStreamDefinitions,
                                     final boolean overwriteExisting,
                                     final int commitInterval) {
        bulkLoadAndAssert(refStreamDefinitions, overwriteExisting, commitInterval, true, refDataStore);
    }

    protected void bulkLoadAndAssert(final List<RefStreamDefinition> refStreamDefinitions,
                                     final boolean overwriteExisting,
                                     final int commitInterval,
                                     final boolean doAsserts) {
        bulkLoadAndAssert(refStreamDefinitions, overwriteExisting, commitInterval, doAsserts, refDataStore);

    }

    protected String buildDefaultMapName(final int i) {
        return "Map" + i;
    }

    protected void bulkLoadAndAssert(final List<RefStreamDefinition> refStreamDefinitions,
                                     final boolean overwriteExisting,
                                     final int commitInterval,
                                     final boolean doAsserts,
                                     final RefDataStore refDataStore) {

        final long effectiveTimeMs = System.currentTimeMillis();
        final AtomicInteger counter = new AtomicInteger();

        final List<String> mapNames = IntStream.rangeClosed(1, MAPS_PER_REF_STREAM_DEF)
                .boxed()
                .map(this::buildDefaultMapName)
                .collect(Collectors.toList());

        final List<Tuple3<MapDefinition, String, StringValue>> keyValueLoadedData = new ArrayList<>();
        final List<Tuple3<MapDefinition, Range<Long>, StringValue>> keyRangeValueLoadedData = new ArrayList<>();

        final AtomicReference<RefStreamDefinition> lastRefStreamDefinition = new AtomicReference<>(null);
        final AtomicInteger lastCounterStartVal = new AtomicInteger();

        refStreamDefinitions.forEach(refStreamDefinition -> {
            try {
                LOGGER.debug("Loading data for refStreamDef: {}", refStreamDefinition);
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

                final int putAttempts = loadData(
                        refDataStore,
                        refStreamDefinition,
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

//            refDataStore.logAllContents();

            final ProcessingState processingState = refDataStore.getLoadState(refStreamDefinition)
                    .get();

            assertThat(processingState)
                    .isEqualTo(ProcessingState.COMPLETE);

        });

        if (ENTRIES_PER_MAP_DEF < 10) {
            refDataStore.logAllContents(LOGGER::info);
        }

        if (doAsserts) {
            LOGGER.info("Starting key value asserts");
            assertLoadedKeyValueData(keyValueLoadedData);
            LOGGER.info("Starting range value asserts");
            assertLoadedKeyRangeValueData(keyRangeValueLoadedData);
            LOGGER.info("Completed asserts");
        }
    }

    protected void assertLoadedKeyValueData(final List<Tuple3<MapDefinition, String, StringValue>> keyValueLoadedData) {

        LOGGER.info("Starting key value asserts on {} entries", keyValueLoadedData.size());

        final Instant startTime = Instant.now();
        final AtomicInteger counter = new AtomicInteger();
        final int tenPercent = keyValueLoadedData.size() / 10;

        // query all values from the key/value store
        keyValueLoadedData.forEach(tuple3 -> {
            final MapDefinition mapDefinition = tuple3._1;
            final String key = tuple3._2;
            final StringValue expectedValue = tuple3._3;

            if (keyValueLoadedData.size() > 10_000) {
                if (counter.incrementAndGet() % tenPercent == 0) {
                    // Show progress for big loads
                    final Duration duration = Duration.between(startTime, Instant.now());
                    LOGGER.info("Count so far: {}, in {}, gets/ms: {}",
                            counter.get(),
                            duration,
                            counter.get() / (double) duration.toMillis());
                }
            }
            // get the proxy object
            final RefDataValueProxy valueProxy = refDataStore.getValueProxy(mapDefinition, key);

            // Trigger the lookup
            final RefDataValue refDataValue = valueProxy.supplyValue().get();

            assertThat(refDataValue).isInstanceOf(StringValue.class);
            assertThat((StringValue) refDataValue)
                    .isEqualTo(expectedValue);

            // now consume the proxied value in a txn
            valueProxy.consumeBytes(typedByteBuffer -> {
                assertThat(typedByteBuffer.getTypeId())
                        .isEqualTo(StringValue.TYPE_ID);
                final String foundStrVal = StandardCharsets.UTF_8.decode(typedByteBuffer.getByteBuffer()).toString();
                assertThat(foundStrVal)
                        .isEqualTo(expectedValue.getValue());
            });
        });
    }

    protected void assertLoadedKeyRangeValueData(
            final List<Tuple3<MapDefinition, Range<Long>, StringValue>> keyRangeValueLoadedData) {

        LOGGER.info("Starting range value asserts on {} entries", keyRangeValueLoadedData.size());

        final Instant startTime = Instant.now();
        final AtomicInteger counter = new AtomicInteger();
        final int tenPercent = keyRangeValueLoadedData.size() / 10;

        keyRangeValueLoadedData.forEach(tuple3 -> {
            final MapDefinition mapDefinition = tuple3._1;
            final Range<Long> keyRange = tuple3._2;
            final StringValue expectedValue = tuple3._3;

            if (keyRangeValueLoadedData.size() > 10_000) {
                if (counter.incrementAndGet() % tenPercent == 0) {
                    final Duration duration = Duration.between(startTime, Instant.now());
                    // *5 because we do 5 different gets for each tuple3 entry
                    LOGGER.info("Count so far: {}, in {}, gets/ms: {}",
                            counter.get(),
                            duration,
                            (counter.get() * 5L) / (double) duration.toMillis());
                }
            }

            // build a variety of keys from the supplied range
            final String keyAtStartOfRange = keyRange.getFrom().toString();
            final String keyAtEndOfRange = Long.toString(keyRange.getTo() - 1);
            final String keyInsideRange = Long.toString(keyRange.getFrom() + 5);
            final String keyBelowRange = Long.toString(keyRange.getFrom() - 1);
            final String keyAboveRange = Long.toString(keyRange.getTo() + 1);

            // define the expected result for each key
            final List<Tuple2<String, Boolean>> keysAndExpectedResults = Arrays.asList(
                    Tuple.of(keyAtStartOfRange, true),
                    Tuple.of(keyAtEndOfRange, true),
                    Tuple.of(keyInsideRange, true),
                    Tuple.of(keyBelowRange, false),
                    Tuple.of(keyAboveRange, false));

            keysAndExpectedResults.forEach(tuple2 -> {
                final String key = tuple2._1;
                final boolean isValueExpected = tuple2._2;

                LOGGER.debug(() -> LogUtil.message("range {}, key {}, expected {}",
                        keyRange, key, isValueExpected));

                // get the proxy object
                final RefDataValueProxy valueProxy = refDataStore.getValueProxy(mapDefinition, key);

                // Trigger the lookup
                final Optional<RefDataValue> optRefDataValue = valueProxy.supplyValue();

                assertThat(optRefDataValue.isPresent())
                        .isEqualTo(isValueExpected);

                optRefDataValue.ifPresent(refDataValue -> {

                    assertThat(refDataValue).isInstanceOf(StringValue.class);
                    assertThat((StringValue) refDataValue)
                            .isEqualTo(expectedValue);

                    valueProxy.consumeBytes(typedByteBuffer -> {
                        assertThat(typedByteBuffer.getTypeId())
                                .isEqualTo(StringValue.TYPE_ID);
                        final String foundStrVal = StandardCharsets.UTF_8.decode(typedByteBuffer.getByteBuffer())
                                .toString();
                        assertThat(foundStrVal)
                                .isEqualTo(expectedValue.getValue());
                    });
                });
            });
        });
    }

    protected int loadData(
            final RefDataStore refDataStore,
            final RefStreamDefinition refStreamDefinition,
            final long effectiveTimeMs,
            final int commitInterval,
            final List<String> mapNames,
            final boolean overwriteExisting,
            final AtomicInteger counter,
            final List<Tuple3<MapDefinition, String, StringValue>> keyValueLoadedData,
            final List<Tuple3<MapDefinition, Range<Long>, StringValue>> keyRangeValueLoadedData,
            final boolean isLoadExpectedToHappen) throws Exception {


        final boolean didLoadHappen = refDataStore.doWithLoaderUnlessComplete(
                refStreamDefinition,
                effectiveTimeMs,
                loader -> {
                    loader.initialise(overwriteExisting);
                    loader.setCommitInterval(commitInterval);

                    for (int i = 0; i < ENTRIES_PER_MAP_DEF; i++) {
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

        final ProcessingState processingInfo = refDataStore.getLoadState(refStreamDefinition).get();

        assertThat(processingInfo)
                .isEqualTo(ProcessingState.COMPLETE);

        final boolean isDataLoaded = refDataStore.isDataLoaded(refStreamDefinition);
        assertThat(isDataLoaded)
                .isTrue();

        return ENTRIES_PER_MAP_DEF * mapNames.size();
    }

    protected ReferenceDataConfig getReferenceDataConfig() {
        return referenceDataConfig;
    }

    protected void setPurgeAgeProperty(final StroomDuration purgeAge) {
        referenceDataConfig = referenceDataConfig.withPurgeAge(purgeAge);
    }

    protected void doLoaderPut(final RefDataLoader refDataLoader,
                               final MapDefinition mapDefinition,
                               final String key,
                               final RefDataValue refDataValue) {
        try (final StagingValueOutputStream stagingValueOutputStream = new StagingValueOutputStream(
                valueStoreHashAlgorithm,
                pooledByteBufferOutputStreamFactory)) {
            writeValue(refDataValue, stagingValueOutputStream);
            refDataLoader.put(mapDefinition, key, stagingValueOutputStream);
        }
    }

    protected void doLoaderPut(final RefDataLoader refDataLoader,
                               final MapDefinition mapDefinition,
                               final Range<Long> range,
                               final RefDataValue refDataValue) {
        try (final StagingValueOutputStream stagingValueOutputStream = new StagingValueOutputStream(
                valueStoreHashAlgorithm,
                pooledByteBufferOutputStreamFactory)) {
            writeValue(refDataValue, stagingValueOutputStream);
            refDataLoader.put(mapDefinition, range, stagingValueOutputStream);
        }
    }

    protected static void writeValue(final RefDataValue refDataValue,
                                     final StagingValueOutputStream stagingValueOutputStream) {
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

    protected static List<PutOutcome> handleKeyOutcomes(final RefDataLoader refDataLoader) {
        final List<PutOutcome> putOutcomes = new ArrayList<>();
        refDataLoader.setKeyPutOutcomeHandler((mapDefinitionSupplier, key, putOutcome) -> {
            LOGGER.debug(() -> LogUtil.message("Got outcome: {}, map: {}, key: {}",
                    putOutcome,
                    mapDefinitionSupplier.get().getMapName(),
                    key));
            putOutcomes.add(putOutcome);
        });
        return putOutcomes;
    }

    protected static List<PutOutcome> handleRangeOutcomes(final RefDataLoader refDataLoader) {
        final List<PutOutcome> putOutcomes = new ArrayList<>();
        refDataLoader.setRangePutOutcomeHandler((mapDefinitionSupplier, range, putOutcome) -> {
            LOGGER.debug(() -> LogUtil.message("Got outcome: {}, map: {}, key: {}",
                    putOutcome,
                    mapDefinitionSupplier.get().getMapName(),
                    range));
            putOutcomes.add(putOutcome);
        });
        return putOutcomes;
    }

    protected static void assertPutOutcome(final PutOutcome putOutcome,
                                           final boolean expectedIsSuccess,
                                           final boolean expectedIsDuplicate) {
        assertThat(putOutcome.isSuccess())
                .isEqualTo(expectedIsSuccess);
        assertThat(putOutcome.isDuplicate())
                .hasValue(expectedIsDuplicate);
    }

    protected static void assertPutOutcome(final PutOutcome putOutcome,
                                           final boolean expectedIsSuccess,
                                           final Optional<Boolean> expectedIsDuplicate) {
        assertThat(putOutcome.isSuccess())
                .isEqualTo(expectedIsSuccess);
        assertThat(putOutcome.isDuplicate())
                .isEqualTo(expectedIsDuplicate);
    }

    protected static List<Long> getRefStreamIds(final RefDataStore refDataStore) {
        return refDataStore.listProcessingInfo(Integer.MAX_VALUE)
                .stream()
                .map(info -> info.getRefStreamDefinition().getStreamId())
                .collect(Collectors.toList());
    }


    // --------------------------------------------------------------------------------


    protected interface MapNameFunc {

        String buildMapName(final RefStreamDefinition refStreamDefinition, final String type, final int i);
    }
}
