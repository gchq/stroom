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

import stroom.bytebuffer.PooledByteBufferOutputStream;
import stroom.pipeline.refdata.ReferenceDataConfig;
import stroom.pipeline.refdata.ReferenceDataLmdbConfig;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.RefDataStore;
import stroom.pipeline.refdata.store.RefDataStoreFactory;
import stroom.pipeline.refdata.store.RefDataStoreTestModule;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.StagingValue;
import stroom.pipeline.refdata.store.StagingValueOutputStream;
import stroom.pipeline.refdata.store.StringValue;
import stroom.pipeline.refdata.store.ValueStoreHashAlgorithm;
import stroom.pipeline.refdata.store.offheapstore.serdes.RefDataValueSerde;
import stroom.pipeline.refdata.store.offheapstore.serdes.RefDataValueSerdeFactory;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.common.base.Strings;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.function.BiConsumer;

class TestOffHeapStagingStore extends StroomUnitTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestOffHeapStagingStore.class);
    private static final String MAP1 = "map1";
    private static final String MAP2 = "map2";
    private static final List<String> MAPS = List.of(MAP1, MAP2);

    @Inject
    private OffHeapStagingStoreFactory offHeapStagingStoreFactory;
    @Inject
    private ValueStoreHashAlgorithm valueStoreHashAlgorithm;
    @Inject
    private PooledByteBufferOutputStream.Factory pooledByteBufferOutputStreamFactory;
    @Inject
    private RefDataStoreFactory refDataStoreFactory;
    @Inject
    private MapDefinitionUIDStore.Factory mapDefinitionUidStoreFactory;

    private ReferenceDataConfig referenceDataConfig = new ReferenceDataConfig();
    private Injector injector;
    private Path dbDir = null;
    private OffHeapStagingStore offHeapStagingStore;
    private RefDataStore refDataStore;
    private RefDataLmdbEnv refDataLmdbEnv;

    private final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            RefDataStoreTestModule.REF_STREAM_1_ID);

    @BeforeEach
    void setup() throws IOException {
        LOGGER.debug("setup() started");
        dbDir = Files.createTempDirectory("stroom");
//        dbDir = Paths.get("/home/dev/tmp/ref_test");
        Files.createDirectories(dbDir);
        FileUtil.deleteContents(dbDir);

        LOGGER.info("Creating LMDB environment in dbDir {}", dbDir.toAbsolutePath().toString());

        // This should ensure batching is exercised, including partial batches
//        final int batchSize = Math.max(1, ENTRIES_PER_MAP_DEF / 2) - 1;
        final int batchSize = 10;
        LOGGER.debug("Using batchSize {}", batchSize);
        referenceDataConfig = new ReferenceDataConfig()
                .withLmdbConfig(new ReferenceDataLmdbConfig()
                        .withLocalDir(dbDir.toAbsolutePath().toString())
                        .withReaderBlockedByWriter(false))
                .withMaxPutsBeforeCommit(batchSize)
                .withMaxPurgeDeletesBeforeCommit(batchSize);

        injector = Guice.createInjector(
                new AbstractModule() {
                    @Override
                    protected void configure() {
//                        bind(ReferenceDataConfig.class).toProvider(() -> getReferenceDataConfig());
//                        bind(HomeDirProvider.class).toInstance(() -> getCurrentTestDir());
//                        bind(TempDirProvider.class).toInstance(() -> getCurrentTestDir());
//                        bind(PathCreator.class).to(SimplePathCreator.class);
//                        install(new RefDataStoreModule());
//                        install(new PipelineScopeModule());
                        install(new RefDataStoreTestModule(
                                () -> getReferenceDataConfig(),
                                () -> getCurrentTestDir(),
                                () -> getCurrentTestDir()));
                    }
                });

        injector.injectMembers(this);
        refDataStore = refDataStoreFactory.getOffHeapStore(refStreamDefinition);
        refDataLmdbEnv = ((RefDataOffHeapStore) refDataStore).getLmdbEnvironment();
        final MapDefinitionUIDStore mapDefinitionUIDStore = mapDefinitionUidStoreFactory.create(refDataLmdbEnv);
        offHeapStagingStore = offHeapStagingStoreFactory.create(refStreamDefinition, mapDefinitionUIDStore);

        LOGGER.debug("setup() finished");
    }

    @AfterEach
    void tearDown() throws Exception {
        LOGGER.debug("teardown() started");
        offHeapStagingStore.close();
        LOGGER.debug("teardown() finished");
    }

    @Test
    void put_keyValues() {
        final byte typeId = StringValue.TYPE_ID;
        putKeyValueData(5, typeId);
        offHeapStagingStore.completeLoad();
        offHeapStagingStore.logAllContents(LOGGER::debug);

        final List<Entry<KeyValueStoreKey, StagingValue>> entryList = new ArrayList<>();
        offHeapStagingStore.forEachKeyValueEntry(entryList::add);

        Assertions.assertThat(entryList)
                .extracting(entry ->
                        entry.getKey().getMapUid().getValue()
                                + "-" + entry.getKey().getKey())
                .containsExactly(
                        "0-key-000",
                        "0-key-001",
                        "0-key-002",
                        "0-key-003",
                        "0-key-004",
                        "1-key-000",
                        "1-key-001",
                        "1-key-002",
                        "1-key-003",
                        "1-key-004");

        final RefDataValueSerde valueSerde = new RefDataValueSerdeFactory().get(typeId);
//
//        Assertions.assertThat(entryList)
//                .extracting(entry ->
//                        ((StringValue) valueSerde.deserialize(entry.getValue().getValueBuffer())).getValue())
//                .containsExactly(
//                        "value-001",
//                        "value-002",
//                        "value-003",
//                        "value-004",
//                        "value-005");

        LOGGER.info("--------------------------------------------------------------------------------");
//        refDataStore.logAllContents(LOGGER::debug);
    }

    private void putKeyValueData(final int count, final byte typeId) {
        doWithMapDef((stagingValueOutputStream, mapDefinition) -> {
            for (int i = 0; i < count; i++) {
                try {
                    final String numberPart = Strings.padStart(
                            Integer.toString(i), 3, '0');
                    stagingValueOutputStream.clear();
                    stagingValueOutputStream.write("value-" + numberPart);
                    stagingValueOutputStream.setTypeId(typeId);
                    final String key = "key-" + numberPart;
                    offHeapStagingStore.put(mapDefinition, key, stagingValueOutputStream);
                } catch (final IOException e) {
                    throw new RuntimeException(LogUtil.message("Error: {}", e.getMessage()), e);
                }
            }
        });
    }

    private void doWithMapDef(final BiConsumer<StagingValueOutputStream, MapDefinition> work) {
        try (final StagingValueOutputStream stagingValueOutputStream = new StagingValueOutputStream(
                valueStoreHashAlgorithm,
                pooledByteBufferOutputStreamFactory)) {

            for (final String mapName : MAPS) {
                final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, mapName);
                work.accept(stagingValueOutputStream, mapDefinition);
            }
        }
    }

    @Test
    void testPut() {
    }

    @Test
    void forEachKeyValueEntry() {
    }

    @Test
    void forEachRangeValueEntry() {
    }

    @Test
    void getMapNames() {
    }

    @Test
    void getMapDefinition() {
    }

    protected ReferenceDataConfig getReferenceDataConfig() {
        return referenceDataConfig;
    }
}
