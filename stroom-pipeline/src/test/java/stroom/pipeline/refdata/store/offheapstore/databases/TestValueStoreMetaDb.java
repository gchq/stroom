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

package stroom.pipeline.refdata.store.offheapstore.databases;

import stroom.bytebuffer.ByteBufferPoolFactory;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.lmdb.KeyConsumer;
import stroom.pipeline.refdata.store.StagingValue;
import stroom.pipeline.refdata.store.StringValue;
import stroom.pipeline.refdata.store.ValueStoreHashAlgorithm;
import stroom.pipeline.refdata.store.XxHashValueStoreHashAlgorithm;
import stroom.pipeline.refdata.store.offheapstore.ValueStoreKey;
import stroom.pipeline.refdata.store.offheapstore.ValueStoreMeta;
import stroom.pipeline.refdata.store.offheapstore.serdes.StagingValueSerde;
import stroom.pipeline.refdata.store.offheapstore.serdes.ValueStoreKeySerde;
import stroom.pipeline.refdata.store.offheapstore.serdes.ValueStoreMetaSerde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class TestValueStoreMetaDb extends AbstractStoreDbTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestValueStoreMetaDb.class);

    private final ValueStoreHashAlgorithm xxHashAlgorithm = new XxHashValueStoreHashAlgorithm();

    private ValueStoreMetaDb valueStoreMetaDb = null;

    @BeforeEach
    void setup() {
        // the default
        valueStoreMetaDb = new ValueStoreMetaDb(
                refDataLmdbEnv,
                new ByteBufferPoolFactory().getByteBufferPool(),
                new ValueStoreKeySerde(),
                new ValueStoreMetaSerde());
    }

    @Test
    void incrementReferenceCount1() {
        final String value = "foo";
        final long valueHash = xxHashAlgorithm.hash(value);
        final short id = 123;

        try (final PooledByteBuffer pooledKeyBuffer = valueStoreMetaDb.getPooledKeyBuffer()) {
            refDataLmdbEnv.doWithWriteTxn(writeTxn -> {
                final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();


                valueStoreMetaDb.serializeKey(keyBuffer, new ValueStoreKey(valueHash, id));

                final StagingValue stagingValue = StagingValueSerde.convert(
                        ByteBuffer::allocateDirect,
                        xxHashAlgorithm,
                        StringValue.of(value));

                valueStoreMetaDb.createMetaEntryForValue(
                        writeTxn,
                        keyBuffer,
                        stagingValue);

                final long refCount = valueStoreMetaDb.getReferenceCount(writeTxn, keyBuffer)
                        .orElseThrow();

                assertThat(refCount)
                        .isEqualTo(1);

                valueStoreMetaDb.incrementReferenceCount(writeTxn, keyBuffer);

                final long newRefCount = valueStoreMetaDb.getReferenceCount(writeTxn, keyBuffer)
                        .orElseThrow();

                assertThat(newRefCount)
                        .isEqualTo(refCount + 1);
            });
        }
    }

    @Test
    void incrementReferenceCount2() {
        final String value = "foo";
        final long valueHash = xxHashAlgorithm.hash(value);
        final short id = 123;

        try (final PooledByteBuffer pooledKeyBuffer = valueStoreMetaDb.getPooledKeyBuffer();
                final PooledByteBuffer pooledValueBuffer = valueStoreMetaDb.getPooledValueBuffer()) {
            refDataLmdbEnv.doWithWriteTxn(writeTxn -> {
                final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
                final ByteBuffer valByteBuffer = pooledValueBuffer.getByteBuffer();

                final long maxReferenceCount = ValueStoreMetaSerde.getMaxReferenceCount();

                valueStoreMetaDb.serializeKey(keyBuffer, new ValueStoreKey(valueHash, id));
                valueStoreMetaDb.serializeValue(valByteBuffer, new ValueStoreMeta(
                        StringValue.TYPE_ID,
                        (int) (maxReferenceCount - 1)));

                // Put a ref count just below max
                valueStoreMetaDb.put(writeTxn, keyBuffer, valByteBuffer, true);

                long refCount = valueStoreMetaDb.getReferenceCount(writeTxn, keyBuffer)
                        .orElseThrow();

                assertThat(refCount)
                        .isEqualTo(maxReferenceCount - 1);

                // Increment to max value
                refCount = incrementAndGetRefCount(writeTxn, keyBuffer);

                assertThat(refCount)
                        .isEqualTo(maxReferenceCount);

                // Increment above max value
                Assertions.assertThatThrownBy(() ->
                                incrementAndGetRefCount(writeTxn, keyBuffer))
                        .isInstanceOf(RuntimeException.class)
                        .hasMessageContaining("Can't increment without overflowing")
                        .hasMessageContaining(ModelStringUtil.formatCsv(maxReferenceCount));
            });
        }
    }

    @Test
    void decrementReferenceCount1() {
        final String value = "foo";
        final long valueHash = xxHashAlgorithm.hash(value);
        final short id = 123;

        try (final PooledByteBuffer pooledKeyBuffer = valueStoreMetaDb.getPooledKeyBuffer();
                final PooledByteBuffer pooledValueBuffer = valueStoreMetaDb.getPooledValueBuffer()) {
            refDataLmdbEnv.doWithWriteTxn(writeTxn -> {
                final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
                final ByteBuffer valByteBuffer = pooledValueBuffer.getByteBuffer();

                final long maxReferenceCount = ValueStoreMetaSerde.getMaxReferenceCount();

                valueStoreMetaDb.serializeKey(keyBuffer, new ValueStoreKey(valueHash, id));
                final int initialRefCount = 2;
                valueStoreMetaDb.serializeValue(valByteBuffer, new ValueStoreMeta(
                        StringValue.TYPE_ID,
                        initialRefCount));

                // Put a ref count of 2
                valueStoreMetaDb.put(writeTxn, keyBuffer, valByteBuffer, true);

                long refCount = valueStoreMetaDb.getReferenceCount(writeTxn, keyBuffer)
                        .orElseThrow();

                assertThat(refCount)
                        .isEqualTo(initialRefCount);

                final AtomicBoolean didDelete = new AtomicBoolean(false);

                // Increment to max value
                refCount = decrementAndGetRefCount(writeTxn, keyBuffer, (txn, keyBuffer1) -> {
                    didDelete.set(true);
                });

                assertThat(refCount)
                        .isEqualTo(initialRefCount - 1);
                assertThat(didDelete)
                        .isFalse();

                valueStoreMetaDb.deReferenceOrDeleteValue(writeTxn, keyBuffer, (txn, keyBuffer1) -> {
                    didDelete.set(true);
                });

                // Was deleted
                assertThat(valueStoreMetaDb.getReferenceCount(writeTxn, keyBuffer))
                        .isEmpty();
                assertThat(didDelete)
                        .isTrue();
            });
        }
    }

    @Test
    void decrementReferenceCount2() {
        final String value = "foo";
        final long valueHash = xxHashAlgorithm.hash(value);
        final short id = 123;

        try (final PooledByteBuffer pooledKeyBuffer = valueStoreMetaDb.getPooledKeyBuffer();
                final PooledByteBuffer pooledValueBuffer = valueStoreMetaDb.getPooledValueBuffer()) {
            refDataLmdbEnv.doWithWriteTxn(writeTxn -> {
                final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
                final ByteBuffer valByteBuffer = pooledValueBuffer.getByteBuffer();

                final long maxReferenceCount = ValueStoreMetaSerde.getMaxReferenceCount();

                valueStoreMetaDb.serializeKey(keyBuffer, new ValueStoreKey(valueHash, id));
                final int initialRefCount = (int) maxReferenceCount;
                valueStoreMetaDb.serializeValue(valByteBuffer, new ValueStoreMeta(
                        StringValue.TYPE_ID,
                        initialRefCount));

                // Put a ref count of max value
                valueStoreMetaDb.put(writeTxn, keyBuffer, valByteBuffer, true);

                long refCount = valueStoreMetaDb.getReferenceCount(writeTxn, keyBuffer)
                        .orElseThrow();

                assertThat(refCount)
                        .isEqualTo(initialRefCount);

                final AtomicBoolean didDelete = new AtomicBoolean(false);

                // Increment to max value
                refCount = decrementAndGetRefCount(writeTxn, keyBuffer, (txn, keyBuffer1) -> {
                    didDelete.set(true);
                });

                assertThat(refCount)
                        .isEqualTo(initialRefCount - 1);
                assertThat(didDelete)
                        .isFalse();
            });
        }
    }

    private long incrementAndGetRefCount(final Txn<ByteBuffer> writeTxn, final ByteBuffer keyBuffer) {
        valueStoreMetaDb.incrementReferenceCount(writeTxn, keyBuffer);

        return valueStoreMetaDb.getReferenceCount(writeTxn, keyBuffer)
                .orElseThrow();
    }

    private long decrementAndGetRefCount(final Txn<ByteBuffer> writeTxn,
                                         final ByteBuffer keyBuffer,
                                         final KeyConsumer keyConsumer) {
        valueStoreMetaDb.deReferenceOrDeleteValue(writeTxn, keyBuffer, keyConsumer);

        return valueStoreMetaDb.getReferenceCount(writeTxn, keyBuffer)
                .orElseThrow();
    }
}
