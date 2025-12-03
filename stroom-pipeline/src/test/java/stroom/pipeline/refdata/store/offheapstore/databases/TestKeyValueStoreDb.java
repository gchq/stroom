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
import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.lmdb.LmdbEnv.BatchingWriteTxn;
import stroom.pipeline.refdata.store.offheapstore.KeyValueStoreKey;
import stroom.pipeline.refdata.store.offheapstore.UID;
import stroom.pipeline.refdata.store.offheapstore.ValueStoreKey;
import stroom.pipeline.refdata.store.offheapstore.serdes.KeyValueStoreKeySerde;
import stroom.pipeline.refdata.store.offheapstore.serdes.ValueStoreKeySerde;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TestKeyValueStoreDb extends AbstractStoreDbTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestKeyValueStoreDb.class);

    private KeyValueStoreDb keyValueStoreDb = null;

    @BeforeEach
    void setup() {
        keyValueStoreDb = new KeyValueStoreDb(
                refDataLmdbEnv,
                new ByteBufferPoolFactory().getByteBufferPool(),
                new KeyValueStoreKeySerde(),
                new ValueStoreKeySerde());
    }

    @Test
    void forEachEntry() throws Exception {

        final UID uid1 = UID.of(ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH), 1, 0, 0, 1);
        final UID uid2 = UID.of(ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH), 2, 0, 0, 2);
        final UID uid3 = UID.of(ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH), 3, 0, 0, 3);

        final KeyValueStoreKey keyValueStoreKey11 = new KeyValueStoreKey(uid1, "key11");
        final KeyValueStoreKey keyValueStoreKey21 = new KeyValueStoreKey(uid2, "key21");
        final KeyValueStoreKey keyValueStoreKey22 = new KeyValueStoreKey(uid2, "key22");
        final KeyValueStoreKey keyValueStoreKey31 = new KeyValueStoreKey(uid3, "key31");

        final ValueStoreKey valueStoreKey11 = new ValueStoreKey(11, (short) 11);
        final ValueStoreKey valueStoreKey21 = new ValueStoreKey(21, (short) 21);
        final ValueStoreKey valueStoreKey22 = new ValueStoreKey(22, (short) 22);
        final ValueStoreKey valueStoreKey31 = new ValueStoreKey(31, (short) 31);

        lmdbEnv.doWithWriteTxn(writeTxn -> {
            keyValueStoreDb.put(writeTxn, keyValueStoreKey11, valueStoreKey11, false);
            keyValueStoreDb.put(writeTxn, keyValueStoreKey21, valueStoreKey21, false);
            keyValueStoreDb.put(writeTxn, keyValueStoreKey22, valueStoreKey22, false);
            keyValueStoreDb.put(writeTxn, keyValueStoreKey31, valueStoreKey31, false);

            assertThat(keyValueStoreDb.getEntryCount(writeTxn)).isEqualTo(4);
        });

        doForEachTest(uid1, 1);
        doForEachTest(uid2, 2);
        doForEachTest(uid3, 1);
    }

    @Test
    void testDeleteMapEntries() throws Exception {

        lmdbEnv.doWithWriteTxn(writeTxn -> {
            putEntry(writeTxn, 1, "key11", 1, (short) 1);
            putEntry(writeTxn, 1, "key12", 1, (short) 1);
            putEntry(writeTxn, 1, "key13", 1, (short) 1);

            putEntry(writeTxn, 2, "key21", 1, (short) 1);
            putEntry(writeTxn, 2, "key22", 1, (short) 1);
            putEntry(writeTxn, 2, "key23", 1, (short) 1);

            putEntry(writeTxn, 3, "key31", 1, (short) 1);
            putEntry(writeTxn, 3, "key32", 1, (short) 1);
            putEntry(writeTxn, 3, "key33", 1, (short) 1);

            assertThat(keyValueStoreDb.getEntryCount(writeTxn))
                    .isEqualTo(9);
        });

        try (final BatchingWriteTxn batchingWriteTxn = lmdbEnv.openBatchingWriteTxn(2)) {
            keyValueStoreDb.deleteMapEntries(
                    batchingWriteTxn,
                    UID.of(2, ByteBuffer.allocateDirect(10)),
                    (writeTxn, keyBuffer, valueBuffer) -> {
                        LOGGER.info("{} - {}",
                                ByteBufferUtils.byteBufferInfo(keyBuffer),
                                ByteBufferUtils.byteBufferInfo(keyBuffer));
                    });

            assertThat(keyValueStoreDb.getEntryCount(batchingWriteTxn.getTxn()))
                    .isEqualTo(6);
        }
    }


    @Test
    void testGetEntryCount() {

        final UID uid1 = UID.of(ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH), 0, 0, 0, 0);
        final UID uid2 = UID.of(ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH), 0, 0, 0, 1);
        final UID uid3 = UID.of(ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH), 0, 0, 0, 2);

        final KeyValueStoreKey keyValueStoreKey11 = new KeyValueStoreKey(uid1, "key11");
        final KeyValueStoreKey keyValueStoreKey12 = new KeyValueStoreKey(uid1, "key12");
        final KeyValueStoreKey keyValueStoreKey21 = new KeyValueStoreKey(uid2, "key21");
        final KeyValueStoreKey keyValueStoreKey22 = new KeyValueStoreKey(uid2, "key22");
        final KeyValueStoreKey keyValueStoreKey31 = new KeyValueStoreKey(uid3, "key31");

        final ValueStoreKey valueStoreKey11 = new ValueStoreKey(11, (short) 11);
        final ValueStoreKey valueStoreKey12 = new ValueStoreKey(12, (short) 12);
        final ValueStoreKey valueStoreKey21 = new ValueStoreKey(21, (short) 21);
        final ValueStoreKey valueStoreKey22 = new ValueStoreKey(22, (short) 22);
        final ValueStoreKey valueStoreKey31 = new ValueStoreKey(31, (short) 31);

        lmdbEnv.doWithWriteTxn(writeTxn -> {
            keyValueStoreDb.put(writeTxn, keyValueStoreKey11, valueStoreKey11, false);
            keyValueStoreDb.put(writeTxn, keyValueStoreKey12, valueStoreKey12, false);
            keyValueStoreDb.put(writeTxn, keyValueStoreKey21, valueStoreKey21, false);
            keyValueStoreDb.put(writeTxn, keyValueStoreKey22, valueStoreKey22, false);
            keyValueStoreDb.put(writeTxn, keyValueStoreKey31, valueStoreKey31, false);

            assertThat(keyValueStoreDb.getEntryCount(writeTxn)).isEqualTo(5);
        });

        keyValueStoreDb.logRawDatabaseContents();

        lmdbEnv.doWithReadTxn(readTxn -> {
            final long entryCount = keyValueStoreDb.getEntryCount(uid2, readTxn);
            assertThat(entryCount).isEqualTo(2);
        });
    }

    @Test
    void testGetMaxUid() {

        final UID uid1 = UID.of(ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH), 0, 0, 0, 0);
        final UID uid2 = UID.of(ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH), 0, 0, 0, 1);
        final UID uid3 = UID.of(ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH), 0, 0, 0, 2);

        final KeyValueStoreKey keyValueStoreKey11 = new KeyValueStoreKey(uid1, "key11");
        final KeyValueStoreKey keyValueStoreKey12 = new KeyValueStoreKey(uid1, "key12");
        final KeyValueStoreKey keyValueStoreKey21 = new KeyValueStoreKey(uid2, "key21");
        final KeyValueStoreKey keyValueStoreKey22 = new KeyValueStoreKey(uid2, "key22");
        final KeyValueStoreKey keyValueStoreKey31 = new KeyValueStoreKey(uid3, "key31");

        final ValueStoreKey valueStoreKey11 = new ValueStoreKey(11, (short) 11);
        final ValueStoreKey valueStoreKey12 = new ValueStoreKey(12, (short) 12);
        final ValueStoreKey valueStoreKey21 = new ValueStoreKey(21, (short) 21);
        final ValueStoreKey valueStoreKey22 = new ValueStoreKey(22, (short) 22);
        final ValueStoreKey valueStoreKey31 = new ValueStoreKey(31, (short) 31);

        lmdbEnv.doWithWriteTxn(writeTxn -> {
            keyValueStoreDb.put(writeTxn, keyValueStoreKey11, valueStoreKey11, false);
            keyValueStoreDb.put(writeTxn, keyValueStoreKey12, valueStoreKey12, false);
            keyValueStoreDb.put(writeTxn, keyValueStoreKey21, valueStoreKey21, false);
            keyValueStoreDb.put(writeTxn, keyValueStoreKey22, valueStoreKey22, false);
            keyValueStoreDb.put(writeTxn, keyValueStoreKey31, valueStoreKey31, false);

            assertThat(keyValueStoreDb.getEntryCount(writeTxn)).isEqualTo(5);
        });

        keyValueStoreDb.logRawDatabaseContents();

        lmdbEnv.doWithReadTxn(readTxn -> {
            try (final PooledByteBuffer pooledKeyBuffer = keyValueStoreDb.getPooledKeyBuffer()) {
                final Optional<UID> optMaxUid = keyValueStoreDb.getMaxUid(readTxn, pooledKeyBuffer);
                assertThat(optMaxUid)
                        .hasValue(uid3);
            }
        });
    }

    @Test
    void testGetMaxUid_empty() {

        // Empty DB
        keyValueStoreDb.logRawDatabaseContents();

        lmdbEnv.doWithReadTxn(readTxn -> {
            try (final PooledByteBuffer pooledKeyBuffer = keyValueStoreDb.getPooledKeyBuffer()) {
                final Optional<UID> optMaxUid = keyValueStoreDb.getMaxUid(readTxn, pooledKeyBuffer);
                assertThat(optMaxUid)
                        .isEmpty();
            }
        });
    }

    private void putEntry(final Txn<ByteBuffer> writeTxn,
                          final long uidVal,
                          final String key,
                          final long valueHash,
                          final short valueUniqueId) {

        // Don't ca
        keyValueStoreDb.put(
                writeTxn,
                new KeyValueStoreKey(
                        UID.of(uidVal, ByteBuffer.allocateDirect(10)),
                        key),
                new ValueStoreKey(valueHash, valueUniqueId),
                false);
    }

    private void doForEachTest(final UID uid, final int expectedEntryCount) throws Exception {
        try (final BatchingWriteTxn batchingWriteTxn = lmdbEnv.openBatchingWriteTxn(2)) {
            final AtomicInteger cnt = new AtomicInteger(0);
            keyValueStoreDb.deleteMapEntries(batchingWriteTxn, uid, (writeTxn, keyBuf, valBuf) -> {
                cnt.incrementAndGet();
                LOGGER.info("{} {}",
                        ByteBufferUtils.byteBufferInfo(keyBuf),
                        ByteBufferUtils.byteBufferInfo(valBuf));
            });

            assertThat(cnt).hasValue(expectedEntryCount);
        }
    }
}
