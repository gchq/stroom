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
import stroom.bytebuffer.PooledByteBuffer;
import stroom.bytebuffer.PooledByteBufferOutputStream;
import stroom.bytebuffer.PooledByteBufferOutputStream.Factory;
import stroom.pipeline.refdata.store.BasicValueStoreHashAlgorithmImpl;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.StagingValue;
import stroom.pipeline.refdata.store.StringValue;
import stroom.pipeline.refdata.store.ValueStoreHashAlgorithm;
import stroom.pipeline.refdata.store.XxHashValueStoreHashAlgorithm;
import stroom.pipeline.refdata.store.offheapstore.databases.AbstractStoreDbTest;
import stroom.pipeline.refdata.store.offheapstore.databases.ValueStoreDb;
import stroom.pipeline.refdata.store.offheapstore.databases.ValueStoreMetaDb;
import stroom.pipeline.refdata.store.offheapstore.serdes.GenericRefDataValueSerde;
import stroom.pipeline.refdata.store.offheapstore.serdes.RefDataValueSerdeFactory;
import stroom.pipeline.refdata.store.offheapstore.serdes.StagingValueSerde;
import stroom.pipeline.refdata.store.offheapstore.serdes.ValueStoreKeySerde;
import stroom.pipeline.refdata.store.offheapstore.serdes.ValueStoreMetaSerde;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TestValueStore extends AbstractStoreDbTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestValueStore.class);
    private final RefDataValueSerdeFactory refDataValueSerdeFactory = new RefDataValueSerdeFactory();
    private final ValueStoreHashAlgorithm xxHashAlgorithm = new XxHashValueStoreHashAlgorithm();
    private final ValueStoreHashAlgorithm basicHashAlgorithm = new BasicValueStoreHashAlgorithmImpl();
    private final ByteBufferPool byteBufferPool = new ByteBufferPoolFactory().getByteBufferPool();
    private final PooledByteBufferOutputStream.Factory pooledByteBufferOutputStreamFactory = new Factory() {
        @Override
        public PooledByteBufferOutputStream create(final int initialCapacity) {
            return new PooledByteBufferOutputStream(byteBufferPool, initialCapacity);
        }
    };
    private ValueStore valueStore = null;
    private ValueStoreDb valueStoreDb = null;
    private ValueStoreMetaDb valueStoreMetaDb = null;

    @BeforeEach
    void setup() {
        valueStoreDb = new ValueStoreDb(
                refDataLmdbEnv,
                byteBufferPool,
                new ValueStoreKeySerde(),
                new GenericRefDataValueSerde(refDataValueSerdeFactory),
                xxHashAlgorithm,
                pooledByteBufferOutputStreamFactory);


        valueStoreMetaDb = new ValueStoreMetaDb(
                refDataLmdbEnv,
                byteBufferPool,
                new ValueStoreKeySerde(),
                new ValueStoreMetaSerde());

        valueStore = new ValueStore(refDataLmdbEnv, valueStoreDb, valueStoreMetaDb);
    }

    private void setupValueStoreDb(final ValueStoreHashAlgorithm valueStoreHashAlgorithm) {
        valueStoreDb = new ValueStoreDb(
                refDataLmdbEnv,
                new ByteBufferPoolFactory().getByteBufferPool(),
                new ValueStoreKeySerde(),
                new GenericRefDataValueSerde(refDataValueSerdeFactory),
                valueStoreHashAlgorithm,
                pooledByteBufferOutputStreamFactory);

        valueStore = new ValueStore(refDataLmdbEnv, valueStoreDb, valueStoreMetaDb);
    }

    private ValueStoreKey getOrCreate(final Txn<ByteBuffer> writeTxn, final RefDataValue refDataValue) {
        try (final PooledByteBuffer valueStoreKeyPooledBuffer = valueStore.getPooledKeyBuffer()) {
            final StagingValue stagingValue = StagingValueSerde.convert(
                    ByteBuffer::allocateDirect,
                    basicHashAlgorithm,
                    refDataValue);
            final ByteBuffer valueStoreKeyBuffer = valueStore.getOrCreateKey(
                    writeTxn,
                    valueStoreKeyPooledBuffer,
                    stagingValue);

            return valueStoreDb.deserializeKey(valueStoreKeyBuffer);
        }
    }

    @Test
    void testGetOrCreate() {

        // We have to set up the DB with the basic hash func so we can be assured of hash clashes
        setupValueStoreDb(basicHashAlgorithm);

        final ValueStoreHashAlgorithm hashAlgorithm = valueStoreDb.getValueStoreHashAlgorithm();

        // 1 & 2 have the same hashcode, 3 has a different hashcode
        final String stringValueStr1 = "Aa";
        final String stringValueStr2 = "BB";
        final String stringValueStr3 = "SomethingDifferent";

        assertThat(hashAlgorithm.hash(stringValueStr1)).isEqualTo(hashAlgorithm.hash(stringValueStr2));
        assertThat(hashAlgorithm.hash(stringValueStr1)).isNotEqualTo(hashAlgorithm.hash(stringValueStr3));

        assertThat(valueStoreDb.getEntryCount()).isEqualTo(0);
        assertThat(valueStoreMetaDb.getEntryCount()).isEqualTo(0);

        lmdbEnv.doWithWriteTxn(writeTxn -> {
            final ValueStoreKey valueStoreKey = getOrCreate(writeTxn, StringValue.of(stringValueStr1));

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
        lmdbEnv.doWithWriteTxn(writeTxn -> {
            final ValueStoreKey valueStoreKey = getOrCreate(writeTxn, StringValue.of(stringValueStr1));

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
        lmdbEnv.doWithWriteTxn(writeTxn -> {
            final ValueStoreKey valueStoreKey = getOrCreate(writeTxn, StringValue.of(stringValueStr2));

            assertThat(valueStoreKey).isNotNull();
            assertThat(valueStoreKey.getUniqueId()).isEqualTo((short) 1);
            assertRefCount(writeTxn, valueStoreKey, 1);
        });
        logContents();
        assertThat(valueStoreDb.getEntryCount()).isEqualTo(2);
        assertThat(valueStoreMetaDb.getEntryCount()).isEqualTo(2);

        LOGGER.debug("----------------------------");

        // get the same value again, no change to DB or returned values
        lmdbEnv.doWithWriteTxn(writeTxn -> {
            final ValueStoreKey valueStoreKey = getOrCreate(writeTxn, StringValue.of(stringValueStr2));

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
        lmdbEnv.doWithWriteTxn(writeTxn -> {
            final ValueStoreKey valueStoreKey = getOrCreate(writeTxn, StringValue.of(stringValueStr3));

            assertThat(valueStoreKey).isNotNull();
            assertThat(valueStoreKey.getUniqueId()).isEqualTo((short) 0);
            assertRefCount(writeTxn, valueStoreKey, 1);
        });
        logContents();
        assertThat(valueStoreDb.getEntryCount()).isEqualTo(3);
        assertThat(valueStoreMetaDb.getEntryCount()).isEqualTo(3);

        LOGGER.debug("----------------------------");

        // get the same value again, no change to DB or returned values
        lmdbEnv.doWithWriteTxn(writeTxn -> {
            final ValueStoreKey valueStoreKey = getOrCreate(writeTxn, StringValue.of(stringValueStr3));

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
    void testDereference() {

        final StringValue value1 = StringValue.of("1111");
        final StringValue value2 = StringValue.of("2222");

        // ensure hashcodes don't clash
        assertThat(value1.getValue().hashCode())
                .isNotEqualTo(value2.getValue().hashCode());

        final int iterations = 10;

        final AtomicReference<ValueStoreKey> valueStoreKey1aRef = new AtomicReference<>();
        final AtomicReference<ValueStoreKey> valueStoreKey2aRef = new AtomicReference<>();

        // insert 10 of the same values, should have one entry with a ref count going up to 10
        for (int i = 1; i <= iterations; i++) {
            final int expectedRefCount = i;
            lmdbEnv.doWithWriteTxn(writeTxn -> {
                final ValueStoreKey valueStoreKey1a = getOrCreate(writeTxn, value1);

                // value should always be the same
                valueStoreKey1aRef.accumulateAndGet(valueStoreKey1a, (currVal, newVal) -> {
                    if (currVal != null) {

                        assertThat(newVal)
                                .isEqualTo(currVal);
                    }
                    return newVal;
                });

                assertThat(valueStoreDb.getEntryCount(writeTxn)).isEqualTo(1);

                final StringValue stringValue1 = (StringValue) valueStore.get(writeTxn, valueStoreKey1a).get();
                assertThat(getRefCount(writeTxn, valueStoreKey1a))
                        .isEqualTo(expectedRefCount);
                assertThat(stringValue1.getValue())
                        .isEqualTo(value1.getValue());
            });
        }

        valueStoreDb.logRawDatabaseContents();
        valueStoreDb.logDatabaseContents();

        // insert 10 of the same values, should now have one entry (plus the one from above)
        // with a ref count going up to 10
        for (int i = 1; i <= iterations; i++) {
            final int expectedRefCount = i;
            lmdbEnv.doWithWriteTxn(writeTxn -> {
                final ValueStoreKey valueStoreKey2a = getOrCreate(writeTxn, value2);
                valueStoreKey2aRef.accumulateAndGet(valueStoreKey2a, (currVal, newVal) -> {
                    if (currVal != null) {

                        assertThat(newVal)
                                .isEqualTo(currVal);
                    }
                    return newVal;
                });
                assertThat(valueStoreDb.getEntryCount(writeTxn)).isEqualTo(2);
                final StringValue stringValue2 = (StringValue) valueStore.get(writeTxn, valueStoreKey2a).get();
                assertThat(getRefCount(writeTxn, valueStoreKey2a))
                        .isEqualTo(expectedRefCount);
                assertThat(stringValue2.getValue())
                        .isEqualTo(value2.getValue());
            });
        }

        valueStoreDb.logRawDatabaseContents();
        valueStoreDb.logDatabaseContents();

        // Now keep trying to delete value 1, ref count should go down until the delete happens
        for (int i = iterations; i >= 1; i--) {
            final int expectedPreDeleteRefCount = i;
            lmdbEnv.doWithWriteTxn(writeTxn -> {
                assertThat(getRefCount(writeTxn, valueStoreKey1aRef.get()))
                        .isEqualTo(expectedPreDeleteRefCount);
                // now dereference value1
                deReferenceOrDeleteValue(writeTxn, valueStoreKey1aRef.get());

                final Optional<RefDataValue> optValue = valueStore.get(writeTxn, valueStoreKey1aRef.get());
                if (expectedPreDeleteRefCount == 1) {
                    // entry should actually be deleted here
                    assertThat(optValue)
                            .isEmpty();
                    assertThat(valueStoreDb.getEntryCount(writeTxn))
                            .isEqualTo(1);
                } else {
                    assertThat(optValue)
                            .isPresent();
                    assertThat(getRefCount(writeTxn, valueStoreKey1aRef.get()))
                            .isEqualTo(expectedPreDeleteRefCount - 1);

                    assertThat(valueStoreDb.getEntryCount(writeTxn))
                            .isEqualTo(2);
                }
            });
        }

        valueStoreDb.logRawDatabaseContents();
        valueStoreDb.logDatabaseContents();

        // Now keep trying to delete value 2, ref count should go down until the delete happens
        for (int i = iterations; i >= 1; i--) {
            final int expectedPreDeleteRefCount = i;
            lmdbEnv.doWithWriteTxn(writeTxn -> {
                assertThat(getRefCount(writeTxn, valueStoreKey2aRef.get()))
                        .isEqualTo(expectedPreDeleteRefCount);
                // now dereference value1
                deReferenceOrDeleteValue(writeTxn, valueStoreKey2aRef.get());

                final Optional<RefDataValue> optValue = valueStore.get(
                        writeTxn,
                        valueStoreKey2aRef.get());
                if (expectedPreDeleteRefCount == 1) {
                    // entry should actually be deleted here
                    assertThat(optValue)
                            .isEmpty();
                    assertThat(valueStoreDb.getEntryCount(writeTxn))
                            .isEqualTo(0);
                } else {
                    assertThat(optValue)
                            .isPresent();
                    assertThat(getRefCount(writeTxn, valueStoreKey2aRef.get()))
                            .isEqualTo(expectedPreDeleteRefCount - 1);

                    assertThat(valueStoreDb.getEntryCount(writeTxn))
                            .isEqualTo(1);
                }
            });
        }
    }

    private void deReferenceOrDeleteValue(final Txn<ByteBuffer> writeTxn, final ValueStoreKey valueStoreKey) {
        try (final PooledByteBuffer pooledByteBuffer = valueStoreDb.getPooledKeyBuffer()) {
            final ByteBuffer valueStoreKeyBuffer = pooledByteBuffer.getByteBuffer();

            valueStoreDb.serializeKey(valueStoreKeyBuffer, valueStoreKey);
            valueStore.deReferenceOrDeleteValue(writeTxn, valueStoreKeyBuffer);
        }
    }


    private int getRefCount(final Txn<ByteBuffer> txn, final ValueStoreKey valueStoreKey) {
        final ValueStoreMeta valueStoreMeta = valueStoreMetaDb.get(txn, valueStoreKey).get();
        final int referenceCount = valueStoreMeta.getReferenceCount();
        LOGGER.info("Ref count: {}", referenceCount);
        return referenceCount;
    }

    private void assertRefCount(final Txn<ByteBuffer> txn,
                                final ValueStoreKey valueStoreKey,
                                final int expectedRefCount) {
        final ValueStoreMeta valueStoreMeta = valueStoreMetaDb.get(txn, valueStoreKey).get();
        final int foundRefCount = valueStoreMeta.getReferenceCount();
        assertThat(foundRefCount).isEqualTo(expectedRefCount);
    }

}
