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
import stroom.bytebuffer.ByteBufferPoolFactory;
import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.lmdb.LmdbUtils;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.offheapstore.databases.AbstractStoreDbTest;
import stroom.pipeline.refdata.store.offheapstore.databases.MapUidForwardDb;
import stroom.pipeline.refdata.store.offheapstore.databases.MapUidReverseDb;
import stroom.pipeline.refdata.store.offheapstore.serdes.MapDefinitionSerde;
import stroom.pipeline.refdata.store.offheapstore.serdes.UIDSerde;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class TestMapDefinitionUIDStore extends AbstractStoreDbTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestMapDefinitionUIDStore.class);
    private final ByteBufferPool byteBufferPool = new ByteBufferPoolFactory().getByteBufferPool();
    private MapDefinitionUIDStore mapDefinitionUIDStore = null;
    private MapUidForwardDb mapUidForwardDb;
    private MapUidReverseDb mapUidReverseDb;

    @BeforeEach
    void setup() {
        final MapDefinitionSerde mapDefinitionSerde = new MapDefinitionSerde();
        final UIDSerde uidSerde = new UIDSerde();
        final ByteBufferPool byteBufferPool = new ByteBufferPoolFactory().getByteBufferPool();
        mapUidForwardDb = new MapUidForwardDb(refDataLmdbEnv, byteBufferPool, mapDefinitionSerde, uidSerde);
        mapUidReverseDb = new MapUidReverseDb(refDataLmdbEnv, byteBufferPool, uidSerde, mapDefinitionSerde);

        mapDefinitionUIDStore = new MapDefinitionUIDStore(
                refDataLmdbEnv,
                mapUidForwardDb,
                mapUidReverseDb);
    }

    /**
     * Noddy test to verify how LMDB copes with different buffer capacities
     */
    @Test
    void testSmallDbKey() {
        final Dbi<ByteBuffer> dbi = lmdbEnv.openDbi("testDb", DbiFlags.MDB_CREATE);
        final String keyStr = "greeting";
        final ByteBuffer key = ByteBuffer.allocateDirect(keyStr.length());
        final ByteBuffer value = ByteBuffer.allocateDirect(20);
        key.put(keyStr.getBytes(StandardCharsets.UTF_8)).flip();
        value.put("Hello world".getBytes(StandardCharsets.UTF_8)).flip();

        LOGGER.debug("key {}", ByteBufferUtils.byteBufferInfo(key));
        LOGGER.debug("value {}", ByteBufferUtils.byteBufferInfo(value));

        lmdbEnv.doWithWriteTxn(writeTxn -> {
            long entryCount = LmdbUtils.getEntryCount(lmdbEnv, writeTxn, dbi);
            assertThat(entryCount).isEqualTo(0);
            dbi.put(writeTxn, key, value);
            entryCount = LmdbUtils.getEntryCount(lmdbEnv, writeTxn, dbi);
            assertThat(entryCount).isEqualTo(1);
        });

        final long entryCount = LmdbUtils.getEntryCount(lmdbEnv, dbi);
        assertThat(entryCount).isEqualTo(1);
        LmdbUtils.logRawDatabaseContents(lmdbEnv, dbi, LOGGER::info);

        final Long entries = lmdbEnv.getWithReadTxn(txn ->
                dbi.stat(txn).entries);

        assertThat(entries).isEqualTo(1);

        final ByteBuffer searchKey = ByteBuffer.allocateDirect(30);
        searchKey.put("greeting".getBytes(StandardCharsets.UTF_8)).flip();
        LOGGER.debug("searchKey {}", ByteBufferUtils.byteBufferInfo(searchKey));

        final ByteBuffer foundValue = lmdbEnv.getWithReadTxn(txn ->
                dbi.get(txn, searchKey));
        LOGGER.debug("foundValue {}", ByteBufferUtils.byteBufferInfo(foundValue));
    }

    @Test
    void getOrCreateId_emptyDB() {

        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                123456L);
        final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition,
                "MyMapName");

        final UID uid1 = lmdbEnv.getWithWriteTxn(writeTxn -> {
            final UID uid = mapDefinitionUIDStore.getOrCreateUid(
                    writeTxn,
                    mapDefinition,
                    mapDefinitionUIDStore.getUidPooledByteBuffer());

            assertThat(uid).isNotNull();

            final long id = UID.UNSIGNED_BYTES.get(uid.getBackingBuffer());

            // empty store so should get back the first id value of 0
            assertThat(id).isEqualTo(0);
            return uid;
        });

        assertThat(mapDefinitionUIDStore.getEntryCount()).isEqualTo(1);

        mapUidForwardDb.logRawDatabaseContents();
        mapUidReverseDb.logRawDatabaseContents();

        // now try again with the same mapDefinition, which should give the same UID
        final UID uid2 = lmdbEnv.getWithWriteTxn(writeTxn -> {
            final UID uid = mapDefinitionUIDStore.getOrCreateUid(
                    writeTxn,
                    mapDefinition,
                    mapDefinitionUIDStore.getUidPooledByteBuffer());

            assertThat(uid).isNotNull();

            final long id = UID.UNSIGNED_BYTES.get(uid.getBackingBuffer());

            // empty store so should get back the first id value of 0
            assertThat(id).isEqualTo(0);
            return uid;
        });

        assertThat(uid1.getBackingBuffer())
                .isEqualByComparingTo(uid2.getBackingBuffer());
    }

    @Test
    void getOrCreateId_multiple() {

        final int putCount = 5;
        final String uuidStr = UUID.randomUUID().toString();
        final List<MapDefinition> mapDefinitions = IntStream.rangeClosed(1, putCount)
                .boxed()
                .map(i -> {
                    final String versionUuid = UUID.randomUUID().toString();
                    // each one is different by the streamId
                    final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                            uuidStr,
                            versionUuid,
                            i);
                    return new MapDefinition(refStreamDefinition, "MyMapName");

                })
                .toList();

        final Map<UID, MapDefinition> loadedEntries = loadEntries(mapDefinitions);

        assertThat(loadedEntries).hasSize(mapDefinitions.size());

        final List<Long> values = loadedEntries.entrySet().stream()
                .map(Map.Entry::getKey)
                .map(UID::getValue)
                .sorted()
                .toList();

        // convert the UIDs back to values and check we have all we expect
        assertThat(values)
                .containsExactly(LongStream
                        .range(0, putCount)
                        .boxed()
                        .toArray(Long[]::new));

        // now try and call get() for each mapDefinition and check it gives us the right UID

        try (final PooledByteBuffer uidPooledBuffer = mapDefinitionUIDStore.getUidPooledByteBuffer()) {
            loadedEntries.entrySet().forEach(entry -> {
                final UID uidLoaded = entry.getKey();
                final MapDefinition mapDefinitionLoaded = entry.getValue();
                final UID uidFromGet = mapDefinitionUIDStore.getUid(
                                mapDefinitionLoaded, uidPooledBuffer.getByteBuffer())
                        .orElseGet(() -> {
                            fail("Expecting to get a value back but didn't");
                            return null;
                        });
                assertThat(uidFromGet).isEqualTo(uidLoaded);
            });
        }
    }

    @Test
    void testGet_notFound() {

        final String uuidStr = UUID.randomUUID().toString();
        final String versionUuidStr = UUID.randomUUID().toString();
        // each one is different by the partIndex
        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                uuidStr,
                versionUuidStr,
                123456L);

        loadEntries(Collections.singletonList(new MapDefinition(refStreamDefinition, "MyMapName")));

        try (final PooledByteBuffer uidPooledBuffer = mapDefinitionUIDStore.getUidPooledByteBuffer()) {
            final Optional<UID> optUid = mapDefinitionUIDStore.getUid(
                    new MapDefinition(refStreamDefinition, "DifferentMapName"),
                    uidPooledBuffer.getByteBuffer());

            assertThat(optUid).isEmpty();
        }
    }

    @Test
    void testGet_found() {

        final String uuidStr = UUID.randomUUID().toString();
        final String versionUuidStr = UUID.randomUUID().toString();
        // each one is different by the partIndex
        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                uuidStr,
                versionUuidStr,
                123456L);
        final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, "MyMapName");

        loadEntries(Collections.singletonList(mapDefinition));

        try (final PooledByteBuffer uidPooledBuffer = mapDefinitionUIDStore.getUidPooledByteBuffer()) {
            final Optional<UID> optUid = mapDefinitionUIDStore.getUid(
                    mapDefinition,
                    uidPooledBuffer.getByteBuffer());

            assertThat(optUid).isNotEmpty();
            assertThat(optUid.get()).isNotNull();
        }
    }

    @Test
    void testDeletePair() {
        final String pipelineUuid = UUID.randomUUID().toString();
        final String pipelineVersion = UUID.randomUUID().toString();
        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(pipelineUuid, pipelineVersion, 1);

        final MapDefinition mapDefinition1 = new MapDefinition(refStreamDefinition, "map1");
        final MapDefinition mapDefinition2 = new MapDefinition(refStreamDefinition, "map2");
        final MapDefinition mapDefinition3 = new MapDefinition(refStreamDefinition, "map3");

        final Map<UID, MapDefinition> loadedEntries = loadEntries(mapDefinition1, mapDefinition2, mapDefinition3);

        assertThat(mapUidForwardDb.getEntryCount()).isEqualTo(3);
        assertThat(mapUidReverseDb.getEntryCount()).isEqualTo(3);

        lmdbEnv.doWithWriteTxn(writeTxn -> {
            mapDefinitionUIDStore.deletePair(
                    writeTxn,
                    loadedEntries.entrySet().iterator().next().getKey());
        });

        assertThat(mapUidForwardDb.getEntryCount()).isEqualTo(2);
        assertThat(mapUidReverseDb.getEntryCount()).isEqualTo(2);
    }

    @Test
    void testGetNextMapDefinition() {

        final String pipelineUuid = UUID.randomUUID().toString();
        final String pipelineVersion = UUID.randomUUID().toString();
        final RefStreamDefinition refStreamDefinition1 = new RefStreamDefinition(
                pipelineUuid, pipelineVersion, 1);
        final RefStreamDefinition refStreamDefinition2 = new RefStreamDefinition(
                pipelineUuid, pipelineVersion, 2);
        final RefStreamDefinition refStreamDefinition3 = new RefStreamDefinition(
                pipelineUuid, pipelineVersion, 3);

        final MapDefinition mapDefinition11 = new MapDefinition(refStreamDefinition1, "map1");
        final MapDefinition mapDefinition21 = new MapDefinition(refStreamDefinition2, "map1");
        final MapDefinition mapDefinition22 = new MapDefinition(refStreamDefinition2, "map2");
        final MapDefinition mapDefinition31 = new MapDefinition(refStreamDefinition3, "map1");

        loadEntries(Arrays.asList(
                mapDefinition11,
                mapDefinition21,
                mapDefinition22,
                mapDefinition31));

        assertThat(mapUidReverseDb.getEntryCount()).isEqualTo(4);
        assertThat(mapUidForwardDb.getEntryCount()).isEqualTo(4);

        LOGGER.info("--------------------------------------------------------------------------------");


        lmdbEnv.doWithWriteTxn(writeTxn -> {

            doGetNextMapDefinitionTest(
                    writeTxn, refStreamDefinition1, Optional.of(mapDefinition11), false);
            // same match as nothing has changed
            doGetNextMapDefinitionTest(
                    writeTxn, refStreamDefinition1, Optional.of(mapDefinition11), false);

            doGetNextMapDefinitionTest(
                    writeTxn, refStreamDefinition2, Optional.of(mapDefinition21), false);
            // same match as nothing has changed
            doGetNextMapDefinitionTest(
                    writeTxn, refStreamDefinition2, Optional.of(mapDefinition21), false);

            doGetNextMapDefinitionTest(
                    writeTxn, refStreamDefinition3, Optional.of(mapDefinition31), false);
            // same match as nothing has changed
            doGetNextMapDefinitionTest(
                    writeTxn, refStreamDefinition3, Optional.of(mapDefinition31), false);

            //now delete the match after we find it

            doGetNextMapDefinitionTest(
                    writeTxn, refStreamDefinition1, Optional.of(mapDefinition11), true);
            // same match as nothing has changed
            doGetNextMapDefinitionTest(
                    writeTxn, refStreamDefinition1, Optional.empty(), true);

            doGetNextMapDefinitionTest(
                    writeTxn, refStreamDefinition2, Optional.of(mapDefinition21), true);
            // same match as nothing has changed
            doGetNextMapDefinitionTest(
                    writeTxn, refStreamDefinition2, Optional.of(mapDefinition22), true);

            doGetNextMapDefinitionTest(
                    writeTxn, refStreamDefinition3, Optional.of(mapDefinition31), true);
            // same match as nothing has changed
            doGetNextMapDefinitionTest(
                    writeTxn, refStreamDefinition3, Optional.empty(), true);
        });
    }

    private void doGetNextMapDefinitionTest(final Txn<ByteBuffer> writeTxn,
                                            final RefStreamDefinition inputRefStreamDefinition,
                                            final Optional<MapDefinition> optExpectedMapDefinition,
                                            final boolean deletePairAfterwards) {

        final Optional<UID> optMapUid = mapDefinitionUIDStore.getNextMapDefinition(
                writeTxn, inputRefStreamDefinition, () -> ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH));

        assertThat(optMapUid.isPresent()).isEqualTo(optExpectedMapDefinition.isPresent());

        if (optMapUid.isPresent()) {

            final MapDefinition actualMapDefinition = mapUidReverseDb.get(writeTxn, optMapUid.get()).get();

            assertThat(actualMapDefinition).isEqualTo(optExpectedMapDefinition.get());

            if (deletePairAfterwards) {
                mapDefinitionUIDStore.deletePair(writeTxn, optMapUid.get());
            }
        }
    }

    private Map<UID, MapDefinition> loadEntries(final MapDefinition... mapDefinitions) {
        return loadEntries(Arrays.asList(mapDefinitions));
    }

    private Map<UID, MapDefinition> loadEntries(final List<MapDefinition> mapDefinitions) {
        final List<UID> uids = new ArrayList<>();

        final Map<UID, MapDefinition> loadedEntries = new HashMap<>();
        lmdbEnv.doWithWriteTxn(writeTxn -> {
            mapDefinitions.stream()
                    .forEach(mapDefinition -> {
                        final UID uid = mapDefinitionUIDStore.getOrCreateUid(
                                writeTxn,
                                mapDefinition,
                                mapDefinitionUIDStore.getUidPooledByteBuffer());
                        assertThat(uid).isNotNull();

                        // we are going to leave the txn so need to clone the UIDs
                        loadedEntries.put(uid.cloneToNewBuffer(), mapDefinition);
                    });
        });

        // make sure we each mapDefinition has resulted in a unique UID
        assertThat(loadedEntries).hasSize(mapDefinitions.size());
        assertThat(mapDefinitionUIDStore.getEntryCount()).isEqualTo(mapDefinitions.size());

        mapUidForwardDb.logDatabaseContents();
        mapUidReverseDb.logDatabaseContents();

        mapUidForwardDb.logRawDatabaseContents();
        mapUidReverseDb.logRawDatabaseContents();

        // now verify all the uid<->mapDef mappings we are about to return
        lmdbEnv.doWithReadTxn(readTxn -> {
            loadedEntries.forEach(((uid, mapDefinition) -> {
                final UID uid2 = mapUidForwardDb.get(readTxn, mapDefinition).get();

                assertThat(uid.getBackingBuffer()).isEqualTo(uid2.getBackingBuffer());

                final MapDefinition mapDefinition2 = mapUidReverseDb.get(readTxn, uid).get();

                assertThat(mapDefinition2).isEqualTo(mapDefinition);
            }));
        });

        return loadedEntries;
    }
}
