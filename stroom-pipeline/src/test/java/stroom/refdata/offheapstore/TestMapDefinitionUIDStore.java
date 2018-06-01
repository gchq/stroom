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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        mapUidForwardDb = new MapUidForwardDb(lmdbEnv, mapDefinitionSerde, uidSerde);
        mapUidReverseDb = new MapUidReverseDb(lmdbEnv, uidSerde, mapDefinitionSerde);

        mapDefinitionUIDStore = new MapDefinitionUIDStore(
                lmdbEnv,
                mapUidForwardDb,
                mapUidReverseDb);
    }

    @Test
    public void getId() {
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

        LOGGER.debug("key {}", ByteArrayUtils.byteBufferInfo(key));
        LOGGER.debug("value {}", ByteArrayUtils.byteBufferInfo(value));

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
        LOGGER.debug("searchKey {}", ByteArrayUtils.byteBufferInfo(searchKey));

        final ByteBuffer foundValue = LmdbUtils.getWithReadTxn(lmdbEnv, txn ->
                dbi.get(txn, searchKey));
        LOGGER.debug("foundValue {}", ByteArrayUtils.byteBufferInfo(foundValue));
    }

    @Test
    public void getOrCreateId_emptyDB() {

        byte version = 0;
        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                UUID.randomUUID().toString(),
                version,
                123456L,
                1);
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
                    byte version = 0;
                    // each one is different by the streamNo
                    final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                            uuidStr,
                            version,
                            123456L,
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

        return loadedEntries;
    }
}