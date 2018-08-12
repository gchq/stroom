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

import org.junit.Before;
import org.junit.Test;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.lmdb.LmdbUtils;
import stroom.refdata.offheapstore.databases.AbstractLmdbDbTest;
import stroom.refdata.offheapstore.databases.ValueStoreDb;
import stroom.refdata.offheapstore.databases.ValueStoreMetaDb;
import stroom.refdata.offheapstore.serdes.GenericRefDataValueSerde;
import stroom.refdata.offheapstore.serdes.RefDataValueSerdeFactory;
import stroom.refdata.offheapstore.serdes.ValueStoreKeySerde;
import stroom.refdata.offheapstore.serdes.ValueStoreMetaSerde;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

public class TestValueStore extends AbstractLmdbDbTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestValueStore.class);

    private ValueStore valueStore = null;
    private ValueStoreDb valueStoreDb = null;
    private ValueStoreMetaDb valueStoreMetaDb = null;

    private final RefDataValueSerdeFactory refDataValueSerdeFactory = new RefDataValueSerdeFactory();

    private final ByteBufferPool byteBufferPool = new ByteBufferPool();

    @Before
    @Override
    public void setup() {
        super.setup();

        valueStoreDb = new ValueStoreDb(
                lmdbEnv,
                byteBufferPool,
                new ValueStoreKeySerde(),
                new GenericRefDataValueSerde(refDataValueSerdeFactory));


        valueStoreMetaDb = new ValueStoreMetaDb(
                lmdbEnv,
                byteBufferPool,
                new ValueStoreKeySerde(),
                new ValueStoreMetaSerde());

        valueStore = new ValueStore(lmdbEnv, valueStoreDb, valueStoreMetaDb);
    }

    private ValueStoreKey getOrCreate(Txn<ByteBuffer> writeTxn, RefDataValue refDataValue) {
        try (PooledByteBuffer valueStoreKeyPooledBuffer = valueStore.getPooledKeyBuffer()) {
            ByteBuffer valueStoreKeyBuffer = valueStore.getOrCreateKey(
                    writeTxn,
                    valueStoreKeyPooledBuffer,
                    refDataValue,
                    false);

            return valueStoreDb.deserializeKey(valueStoreKeyBuffer);
        }
    }

    @Test
    public void testGetOrCreate() {

        // 1 & 2 have the same hashcode, 3 has a different hashcode
        final String stringValueStr1 = "Aa";
        final String stringValueStr2 = "BB";
        final String stringValueStr3 = "SomethingDifferent";

        assertThat(stringValueStr1.hashCode()).isEqualTo(stringValueStr2.hashCode());
        assertThat(stringValueStr1.hashCode()).isNotEqualTo(stringValueStr3.hashCode());

        assertThat(valueStoreDb.getEntryCount()).isEqualTo(0);
        assertThat(valueStoreMetaDb.getEntryCount()).isEqualTo(0);

        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            ValueStoreKey valueStoreKey = getOrCreate(writeTxn, StringValue.of(stringValueStr1));

            assertThat(valueStoreKey).isNotNull();
            assertThat(valueStoreKey.getUniqueId()).isEqualTo((short) 0);
            assertRefCount(writeTxn, valueStoreKey, 1);
        });

        logContents();

        assertThat(valueStoreDb.getEntryCount()).isEqualTo(1);
        assertThat(valueStoreMetaDb.getEntryCount()).isEqualTo(1);

        LOGGER.debug("----------------------------");

        // now put the same value again. Entry count should not change as we already have the value
        // returned valueStoreKey should also be the same.
        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            ValueStoreKey valueStoreKey = getOrCreate(writeTxn, StringValue.of(stringValueStr1));

            assertThat(valueStoreKey).isNotNull();
            assertThat(valueStoreKey.getUniqueId()).isEqualTo((short) 0);

            // ref count increases as two things have an interest in the value
            assertRefCount(writeTxn, valueStoreKey, 2);
        });
        logContents();
        assertThat(valueStoreDb.getEntryCount()).isEqualTo(1);
        assertThat(valueStoreMetaDb.getEntryCount()).isEqualTo(1);

        LOGGER.debug("----------------------------");

        // now put a different value with same hashcode. Entry count should increase and the
        // returned valueStoreKey should have an id of 1 as it has same hashcode as last one
        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            ValueStoreKey valueStoreKey = getOrCreate(writeTxn, StringValue.of(stringValueStr2));

            assertThat(valueStoreKey).isNotNull();
            assertThat(valueStoreKey.getUniqueId()).isEqualTo((short) 1);
            assertRefCount(writeTxn, valueStoreKey, 1);
        });
        logContents();
        assertThat(valueStoreDb.getEntryCount()).isEqualTo(2);
        assertThat(valueStoreMetaDb.getEntryCount()).isEqualTo(2);

        LOGGER.debug("----------------------------");

        // get the same value again, no change to DB or returned values
        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            ValueStoreKey valueStoreKey = getOrCreate(writeTxn, StringValue.of(stringValueStr2));

            assertThat(valueStoreKey).isNotNull();
            assertThat(valueStoreKey.getUniqueId()).isEqualTo((short) 1);
            // ref count increases as two things have an interest in the value
            assertRefCount(writeTxn, valueStoreKey, 2);
        });
        logContents();
        assertThat(valueStoreDb.getEntryCount()).isEqualTo(2);
        assertThat(valueStoreMetaDb.getEntryCount()).isEqualTo(2);

        LOGGER.debug("----------------------------");

        // now put a different value with a different hashcode. Entry count should increase and the
        // returned valueStoreKey should have an id of 0 as it has a different hashcode.
        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            ValueStoreKey valueStoreKey = getOrCreate(writeTxn, StringValue.of(stringValueStr3));

            assertThat(valueStoreKey).isNotNull();
            assertThat(valueStoreKey.getUniqueId()).isEqualTo((short) 0);
            assertRefCount(writeTxn, valueStoreKey, 1);
        });
        logContents();
        assertThat(valueStoreDb.getEntryCount()).isEqualTo(3);
        assertThat(valueStoreMetaDb.getEntryCount()).isEqualTo(3);

        LOGGER.debug("----------------------------");

        // get the same value again, no change to DB or returned values
        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            ValueStoreKey valueStoreKey = getOrCreate(writeTxn, StringValue.of(stringValueStr3));

            assertThat(valueStoreKey).isNotNull();
            assertThat(valueStoreKey.getUniqueId()).isEqualTo((short) 0);
            // ref count increases as two things have an interest in the value
            assertRefCount(writeTxn, valueStoreKey, 2);
        });
        logContents();
        assertThat(valueStoreDb.getEntryCount()).isEqualTo(3);
        assertThat(valueStoreMetaDb.getEntryCount()).isEqualTo(3);
    }

    private void logContents() {
        valueStoreDb.logRawDatabaseContents(LOGGER::debug);
        valueStoreDb.logDatabaseContents(LOGGER::debug);
        valueStoreMetaDb.logRawDatabaseContents(LOGGER::debug);
        valueStoreMetaDb.logDatabaseContents(LOGGER::debug);
    }

    @Test
    public void testDereference() {

        StringValue value1 = StringValue.of("1111");
        StringValue value2 = StringValue.of("2222");

        // ensure hashcode don't clash
        assertThat(value1.getValue().hashCode()).isNotEqualTo(value2.getValue().hashCode());

        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            StringValue stringValue;
            ValueStoreKey valueStoreKey1a = getOrCreate(writeTxn, value1);
            assertThat(valueStoreDb.getEntryCount(writeTxn)).isEqualTo(1);
            stringValue = (StringValue) valueStore.get(writeTxn, valueStoreKey1a).get();
            assertThat(getRefCount(writeTxn, valueStoreKey1a)).isEqualTo(1);
            assertThat(stringValue.getValue()).isEqualTo(value1.getValue());

            // getOrCreate same value, should no new records, but ref count will have increased
            ValueStoreKey valueStoreKey1b = getOrCreate(writeTxn, value1);
            assertThat(valueStoreDb.getEntryCount(writeTxn)).isEqualTo(1);
            assertThat(valueStoreKey1b).isEqualTo(valueStoreKey1a);
            stringValue = (StringValue) valueStore.get(writeTxn, valueStoreKey1b).get();
            assertThat(getRefCount(writeTxn, valueStoreKey1b)).isEqualTo(2);
            assertThat(stringValue.getValue()).isEqualTo(value1.getValue());

            // getOrCreate same value, should no new records, but ref count will have increased
            ValueStoreKey valueStoreKey1c = getOrCreate(writeTxn, value1);
            assertThat(valueStoreDb.getEntryCount(writeTxn)).isEqualTo(1);
            assertThat(valueStoreKey1c).isEqualTo(valueStoreKey1a);
            stringValue = (StringValue) valueStore.get(writeTxn, valueStoreKey1c).get();
            assertThat(getRefCount(writeTxn, valueStoreKey1c)).isEqualTo(3);
            assertThat(stringValue.getValue()).isEqualTo(value1.getValue());

            // getOrCreate a different value, so 1 new entry, ref count is 1
            ValueStoreKey valueStoreKey2a = getOrCreate(writeTxn, value2);
            assertThat(valueStoreDb.getEntryCount(writeTxn)).isEqualTo(2);
            stringValue = (StringValue) valueStore.get(writeTxn, valueStoreKey2a).get();
            assertThat(getRefCount(writeTxn, valueStoreKey2a)).isEqualTo(1);
            assertThat(stringValue.getValue()).isEqualTo(value2.getValue());

            valueStoreDb.logRawDatabaseContents();
            valueStoreDb.logDatabaseContents();

            LOGGER.info("-----------------------------------------------------------------");

            // now dereference value1
            deReferenceOrDeleteValue(writeTxn, valueStoreKey1a);
            stringValue = (StringValue) valueStore.get(writeTxn, valueStoreKey1a).get();
            assertThat(getRefCount(writeTxn, valueStoreKey1a)).isEqualTo(2);
            assertThat(valueStoreDb.getEntryCount(writeTxn)).isEqualTo(2);

            // now dereference value1 again
            deReferenceOrDeleteValue(writeTxn, valueStoreKey1a);
            stringValue = (StringValue) valueStore.get(writeTxn, valueStoreKey1a).get();
            assertThat(getRefCount(writeTxn, valueStoreKey1a)).isEqualTo(1);
            assertThat(valueStoreDb.getEntryCount(writeTxn)).isEqualTo(2);

            // now dereference value1 again, entry is deleted
            deReferenceOrDeleteValue(writeTxn, valueStoreKey1a);
            assertThat(valueStoreDb.get(writeTxn, valueStoreKey1a)).isEmpty();
            assertThat(valueStoreDb.getEntryCount(writeTxn)).isEqualTo(1);

            // now dereference value2, entry is deleted
            deReferenceOrDeleteValue(writeTxn, valueStoreKey2a);
            assertThat(valueStoreDb.get(writeTxn, valueStoreKey2a)).isEmpty();
            assertThat(valueStoreDb.getEntryCount(writeTxn)).isEqualTo(0);

        });

        valueStoreDb.logRawDatabaseContents();
        valueStoreDb.logDatabaseContents();
    }

    private void deReferenceOrDeleteValue(final Txn<ByteBuffer> writeTxn, final ValueStoreKey valueStoreKey) {
        try (PooledByteBuffer pooledByteBuffer = valueStoreDb.getPooledKeyBuffer()) {
            ByteBuffer valueStoreKeyBuffer = pooledByteBuffer.getByteBuffer();

            valueStoreDb.serializeKey(valueStoreKeyBuffer, valueStoreKey);
            valueStore.deReferenceOrDeleteValue(writeTxn, valueStoreKeyBuffer);
        }
    }


    private int getRefCount(Txn<ByteBuffer> txn, ValueStoreKey valueStoreKey) {
        ValueStoreMeta valueStoreMeta = valueStoreMetaDb.get(txn, valueStoreKey).get();
        return valueStoreMeta.getReferenceCount();
    }

    private void assertRefCount(Txn<ByteBuffer> txn, final ValueStoreKey valueStoreKey, final int expectedRefCount) {
        ValueStoreMeta valueStoreMeta = valueStoreMetaDb.get(txn, valueStoreKey).get();
        int foundRefCount = valueStoreMeta.getReferenceCount();
        assertThat(foundRefCount).isEqualTo(expectedRefCount);
    }

}