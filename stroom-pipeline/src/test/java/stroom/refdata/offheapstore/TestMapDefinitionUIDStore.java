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

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.lmdb.LmdbUtils;
import stroom.refdata.offheapstore.databases.AbstractLmdbDbTest;
import stroom.refdata.offheapstore.databases.MapUidForwardDb;
import stroom.refdata.offheapstore.databases.MapUidReverseDb;
import stroom.refdata.offheapstore.serdes.MapDefinitionSerde;
import stroom.refdata.offheapstore.serdes.UIDSerde;

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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

public class TestMapDefinitionUIDStore extends AbstractLmdbDbTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestMapDefinitionUIDStore.class);

    private MapDefinitionUIDStore mapDefinitionUIDStore = null;
    private MapUidForwardDb mapUidForwardDb;
    private MapUidReverseDb mapUidReverseDb;

    @Before
    @Override
    public void setup() {
        super.setup();

        final MapDefinitionSerde mapDefinitionSerde = new MapDefinitionSerde();
        final UIDSerde uidSerde = new UIDSerde();
        final ByteBufferPool byteBufferPool = new ByteBufferPool();
        mapUidForwardDb = new MapUidForwardDb(lmdbEnv, byteBufferPool, mapDefinitionSerde, uidSerde);
        mapUidReverseDb = new MapUidReverseDb(lmdbEnv, byteBufferPool, uidSerde, mapDefinitionSerde);

        mapDefinitionUIDStore = new MapDefinitionUIDStore(
                lmdbEnv,
                mapUidForwardDb,
                mapUidReverseDb);
    }


    /**
     * Noddy test to verify how LMDB copes with different buffer capacities
     */
    @Test
    public void testSmallDbKey() {
        Dbi<ByteBuffer> dbi = lmdbEnv.openDbi("testDb", DbiFlags.MDB_CREATE);
        String keyStr = "greeting";
        final ByteBuffer key = ByteBuffer.allocateDirect(keyStr.length());
        final ByteBuffer value = ByteBuffer.allocateDirect(20);
        key.put(keyStr.getBytes(StandardCharsets.UTF_8)).flip();
        value.put("Hello world".getBytes(StandardCharsets.UTF_8)).flip();

        LOGGER.debug("key {}", ByteBufferUtils.byteBufferInfo(key));
        LOGGER.debug("value {}", ByteBufferUtils.byteBufferInfo(value));

        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            long entryCount = LmdbUtils.getEntryCount(lmdbEnv, dbi);
            assertThat(entryCount).isEqualTo(0);
            dbi.put(writeTxn, key, value);
            entryCount = LmdbUtils.getEntryCount(lmdbEnv, writeTxn, dbi);
            assertThat(entryCount).isEqualTo(1);
        });

        final long entryCount = LmdbUtils.getEntryCount(lmdbEnv, dbi);
        assertThat(entryCount).isEqualTo(1);
        LmdbUtils.logRawDatabaseContents(lmdbEnv, dbi);

        final Long entries = LmdbUtils.getWithReadTxn(lmdbEnv, txn ->
                dbi.stat(txn).entries);

        assertThat(entries).isEqualTo(1);

        final ByteBuffer searchKey = ByteBuffer.allocateDirect(30);
        searchKey.put("greeting".getBytes(StandardCharsets.UTF_8)).flip();
        LOGGER.debug("searchKey {}", ByteBufferUtils.byteBufferInfo(searchKey));

        final ByteBuffer foundValue = LmdbUtils.getWithReadTxn(lmdbEnv, txn ->
                dbi.get(txn, searchKey));
        LOGGER.debug("foundValue {}", ByteBufferUtils.byteBufferInfo(foundValue));
    }

    @Test
    public void getOrCreateId_emptyDB() {

        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                123456L);
        final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition,
                "MyMapName");

        final UID uid1 = LmdbUtils.getWithWriteTxn(lmdbEnv, writeTxn -> {
            UID uid = mapDefinitionUIDStore.getOrCreateUid(writeTxn, mapDefinition);

            assertThat(uid).isNotNull();

            long id = UnsignedBytes.get(uid.getBackingBuffer());

            // empty store so should get back the first id value of 0
            assertThat(id).isEqualTo(0);
            return uid;
        });

        assertThat(mapDefinitionUIDStore.getEntryCount()).isEqualTo(1);

        LmdbUtils.logRawDatabaseContents(lmdbEnv, mapUidForwardDb.getLmdbDbi());
        LmdbUtils.logRawDatabaseContents(lmdbEnv, mapUidReverseDb.getLmdbDbi());

        // now try again with the same mapDefinition, which should give the same UID
        final UID uid2 = LmdbUtils.getWithWriteTxn(lmdbEnv, writeTxn -> {
            UID uid = mapDefinitionUIDStore.getOrCreateUid(writeTxn, mapDefinition);

            assertThat(uid).isNotNull();

            long id = UnsignedBytes.get(uid.getBackingBuffer());

            // empty store so should get back the first id value of 0
            assertThat(id).isEqualTo(0);
            return uid;
        });

        assertThat(uid1).isEqualTo(uid2);
    }

    @Test
    public void getOrCreateId_multiple() {

        int putCount = 5;
        String uuidStr = UUID.randomUUID().toString();
        final List<MapDefinition> mapDefinitions = IntStream.rangeClosed(1, putCount)
                .boxed()
                .map(i -> {
                    String versionUuid = UUID.randomUUID().toString();
                    // each one is different by the streamId
                    final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                            uuidStr,
                            versionUuid,
                            i);
                    return new MapDefinition(refStreamDefinition, "MyMapName");

                })
                .collect(Collectors.toList());

        final Map<UID, MapDefinition> loadedEntries = loadEntries(mapDefinitions);

        assertThat(loadedEntries.size()).isEqualTo(mapDefinitions.size());

        final List<Long> values = loadedEntries.entrySet().stream()
                .map(Map.Entry::getKey)
                .map(UID::getValue)
                .sorted()
                .collect(Collectors.toList());

        // convert the UIDs back to values and check we have all we expect
        assertThat(values)
                .containsExactly(LongStream
                        .range(0, putCount)
                        .boxed()
                        .toArray(Long[]::new));

        // now try and call get() for each mapDefinition and check it gives us the right UID

        loadedEntries.entrySet().forEach(entry -> {
            UID uidLoaded = entry.getKey();
            MapDefinition mapDefinitionLoaded = entry.getValue();
            UID uidFromGet = mapDefinitionUIDStore.getUid(mapDefinitionLoaded)
                    .orElseGet(() -> {
                        Assertions.fail("Expecting to get a value back but didn't");
                        return null;
                    });
            assertThat(uidFromGet).isEqualTo(uidLoaded);
        });
    }

    @Test
    public void testGet_notFound() {

        String uuidStr = UUID.randomUUID().toString();
        String versionUuidStr = UUID.randomUUID().toString();
        // each one is different by the streamNo
        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                uuidStr,
                versionUuidStr,
                123456L);

        loadEntries(Collections.singletonList(new MapDefinition(refStreamDefinition, "MyMapName")));

        Optional<UID> optUid = mapDefinitionUIDStore.getUid(
                new MapDefinition(refStreamDefinition, "DifferentMapName"));

        assertThat(optUid).isEmpty();
    }

    @Test
    public void testGet_found() {

        String uuidStr = UUID.randomUUID().toString();
        String versionUuidStr = UUID.randomUUID().toString();
        // each one is different by the streamNo
        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                uuidStr,
                versionUuidStr,
                123456L);
        MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, "MyMapName");

        loadEntries(Collections.singletonList(mapDefinition));

        Optional<UID> optUid = mapDefinitionUIDStore.getUid(mapDefinition);

        assertThat(optUid).isNotEmpty();
        assertThat(optUid.get()).isNotNull();
    }

    @Test
    public void testDeletePair() {
        String pipelineUuid = UUID.randomUUID().toString();
        String pipelineVersion = UUID.randomUUID().toString();
        RefStreamDefinition refStreamDefinition = new RefStreamDefinition(pipelineUuid, pipelineVersion, 1);

        MapDefinition mapDefinition1 = new MapDefinition(refStreamDefinition, "map1");
        MapDefinition mapDefinition2 = new MapDefinition(refStreamDefinition, "map2");
        MapDefinition mapDefinition3 = new MapDefinition(refStreamDefinition, "map3");

        final Map<UID, MapDefinition> loadedEntries = loadEntries(mapDefinition1, mapDefinition2, mapDefinition3);

        assertThat(mapUidForwardDb.getEntryCount()).isEqualTo(3);
        assertThat(mapUidReverseDb.getEntryCount()).isEqualTo(3);

        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            mapDefinitionUIDStore.deletePair(writeTxn, loadedEntries.entrySet().iterator().next().getKey());
        });

        assertThat(mapUidForwardDb.getEntryCount()).isEqualTo(2);
        assertThat(mapUidReverseDb.getEntryCount()).isEqualTo(2);
    }

    @Test
    public void testGetNextMapDefinition() {

        String pipelineUuid = UUID.randomUUID().toString();
        String pipelineVersion = UUID.randomUUID().toString();
        RefStreamDefinition refStreamDefinition1 = new RefStreamDefinition(pipelineUuid, pipelineVersion, 1);
        RefStreamDefinition refStreamDefinition2 = new RefStreamDefinition(pipelineUuid, pipelineVersion, 2);
        RefStreamDefinition refStreamDefinition3 = new RefStreamDefinition(pipelineUuid, pipelineVersion, 3);

        MapDefinition mapDefinition11 = new MapDefinition(refStreamDefinition1, "map1");
        MapDefinition mapDefinition21 = new MapDefinition(refStreamDefinition2, "map1");
        MapDefinition mapDefinition22 = new MapDefinition(refStreamDefinition2, "map2");
        MapDefinition mapDefinition31 = new MapDefinition(refStreamDefinition3, "map1");

        loadEntries(Arrays.asList(
                mapDefinition11,
                mapDefinition21,
                mapDefinition22,
                mapDefinition31));

        assertThat(mapUidReverseDb.getEntryCount()).isEqualTo(4);
        assertThat(mapUidForwardDb.getEntryCount()).isEqualTo(4);

        LOGGER.info("--------------------------------------------------------------------------------");


        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {

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

        Optional<UID> optMapUid = mapDefinitionUIDStore.getNextMapDefinition(
                writeTxn, inputRefStreamDefinition, () -> ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH));

        assertThat(optMapUid.isPresent()).isEqualTo(optExpectedMapDefinition.isPresent());

        if (optMapUid.isPresent()) {

            MapDefinition actualMapDefinition = mapUidReverseDb.get(optMapUid.get()).get();

            assertThat(actualMapDefinition).isEqualTo(optExpectedMapDefinition.get());

            if (deletePairAfterwards) {
                mapDefinitionUIDStore.deletePair(writeTxn, optMapUid.get());
            }
        }
    }

    private Map<UID, MapDefinition> loadEntries(MapDefinition... mapDefinitions) {
        return loadEntries(Arrays.asList(mapDefinitions));
    }

    private Map<UID, MapDefinition> loadEntries(List<MapDefinition> mapDefinitions) {
        List<UID> uids = new ArrayList<>();

        Map<UID, MapDefinition> loadedEntries = new HashMap<>();
        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            mapDefinitions.stream()
                    .forEach(mapDefinition -> {
                        final UID uid = mapDefinitionUIDStore.getOrCreateUid(writeTxn, mapDefinition);
                        assertThat(uid).isNotNull();

                        // we are going to leave the txn so need to clone the UIDs
                        loadedEntries.put(uid.clone(), mapDefinition);
                    });
        });

        // make sure we each mapDefinition has resulted in a unique UID
        assertThat(loadedEntries.size()).isEqualTo(mapDefinitions.size());
        assertThat(mapDefinitionUIDStore.getEntryCount()).isEqualTo(mapDefinitions.size());

        mapUidForwardDb.logDatabaseContents();
        mapUidReverseDb.logDatabaseContents();

        mapUidForwardDb.logRawDatabaseContents();
        mapUidReverseDb.logRawDatabaseContents();

        // now verify all the uid<->mapDef mappings we are about to return
        LmdbUtils.doWithReadTxn(lmdbEnv, readTxn -> {
            loadedEntries.forEach(((uid, mapDefinition) -> {
                UID uid2 = mapUidForwardDb.get(readTxn, mapDefinition).get();

                int cmpResult = ByteBufferUtils.compare(uid.getBackingBuffer(), uid2.getBackingBuffer());
                assertThat(cmpResult).isEqualTo(0);

                MapDefinition mapDefinition2 = mapUidReverseDb.get(readTxn, uid).get();

                assertThat(mapDefinition2).isEqualTo(mapDefinition);
            }));
        });

        return loadedEntries;
    }
}