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

package stroom.lmdb;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.ByteBufferPoolFactory;
import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.bytebuffer.PooledByteBufferPair;
import stroom.lmdb.UnSortedDupKey.UnsortedDupKeyFactory;
import stroom.lmdb.serde.IntegerSerde;
import stroom.lmdb.serde.Serde;
import stroom.lmdb.serde.StringSerde;
import stroom.lmdb.serde.UnSortedDupKeySerde;
import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.lmdb.serde.UnsignedLong;
import stroom.lmdb.serde.UnsignedLongSerde;
import stroom.test.common.TemporaryPathCreator;
import stroom.test.common.TestUtil;
import stroom.test.common.TestUtil.TimedCase;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.exception.ThrowingConsumer;
import stroom.util.functions.TriConsumer;
import stroom.util.io.ByteSize;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.AsciiTable.Column;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;

import com.google.common.base.Strings;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.KeyRange;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Txn;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class TestBasicLmdbDb extends AbstractLmdbDbTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestBasicLmdbDb.class);
    private static final int UNSIGNED_LONG_LEN = 4;
    private static final UnsignedBytes UNSIGNED_BYTES = UnsignedBytesInstances.ofLength(UNSIGNED_LONG_LEN);
    private final ByteBufferPool byteBufferPool = new ByteBufferPoolFactory().getByteBufferPool();

    private final UnsignedLongSerde unsignedLongSerde = new UnsignedLongSerde(UNSIGNED_LONG_LEN, UNSIGNED_BYTES);

    private BasicLmdbDb<String, String> basicLmdbDb;
    private BasicLmdbDb<String, String> basicLmdbDb2;
    private BasicLmdbDb<Integer, String> basicLmdbDb3;
    private BasicLmdbDb<Integer, String> basicLmdbDb4;
    private BasicLmdbDb<String, UnsignedLong> basicLmdbDb5;

    @BeforeEach
    void setup() {
        basicLmdbDb = new BasicLmdbDb<>(
                lmdbEnv,
                byteBufferPool,
                new StringSerde(),
                new StringSerde(),
                "MyBasicLmdb");

        basicLmdbDb2 = new BasicLmdbDb<>(
                lmdbEnv,
                byteBufferPool,
                new StringSerde(),
                new StringSerde(),
                "MyBasicLmdb2");

        basicLmdbDb3 = new BasicLmdbDb<>(
                lmdbEnv,
                byteBufferPool,
                new IntegerSerde(),
                new StringSerde(),
                "MyBasicLmdb3");

        basicLmdbDb4 = new BasicLmdbDb<>(
                lmdbEnv,
                byteBufferPool,
                Serde.usingNativeOrder(new IntegerSerde()), // MDB_INTEGERKEY needs native byte order
                new StringSerde(),
                "MyBasicLmdb4",
                DbiFlags.MDB_CREATE,
                DbiFlags.MDB_INTEGERKEY);

        basicLmdbDb5 = new BasicLmdbDb<>(
                lmdbEnv,
                byteBufferPool,
                new StringSerde(),
                unsignedLongSerde,
                "MyBasicLmdb5");
    }

    @Test
    void testPutDuplicate_noOverwrite() {
        byteBufferPool.doWithBufferPair(50, 50, (keyBuffer, valueBuffer) -> {
            basicLmdbDb.getKeySerde().serialize(keyBuffer, "MyKey");
            basicLmdbDb.getValueSerde().serialize(valueBuffer, "MyValue");

            lmdbEnv.doWithWriteTxn(writeTxn -> {
                PutOutcome putOutcome = basicLmdbDb.put(
                        writeTxn,
                        keyBuffer,
                        valueBuffer,
                        false);

                assertThat(putOutcome.isSuccess())
                        .isTrue();
                assertThat(putOutcome.isDuplicate())
                        .hasValue(false);

                putOutcome = basicLmdbDb.put(
                        writeTxn,
                        keyBuffer,
                        valueBuffer,
                        false);

                assertThat(putOutcome.isSuccess())
                        .isFalse();
                assertThat(putOutcome.isDuplicate())
                        .hasValue(true);
            });
            assertThat(basicLmdbDb.getEntryCount())
                    .isEqualTo(1);
        });
    }

    @Test
    void testPutDuplicate_overwrite() {
        byteBufferPool.doWithBufferPair(50, 50, (keyBuffer, valueBuffer) -> {
            basicLmdbDb.getKeySerde().serialize(keyBuffer, "MyKey");
            basicLmdbDb.getValueSerde().serialize(valueBuffer, "MyValue");

            lmdbEnv.doWithWriteTxn(writeTxn -> {
                PutOutcome putOutcome = basicLmdbDb.put(
                        writeTxn,
                        keyBuffer,
                        valueBuffer,
                        true);

                assertThat(putOutcome.isSuccess())
                        .isTrue();
                assertThat(putOutcome.isDuplicate())
                        .hasValue(false);

                putOutcome = basicLmdbDb.put(
                        writeTxn,
                        keyBuffer,
                        valueBuffer,
                        true);

                assertThat(putOutcome.isSuccess())
                        .isTrue();
                assertThat(putOutcome.isDuplicate())
                        .hasValue(true);

            });
            assertThat(basicLmdbDb.getEntryCount())
                    .isEqualTo(1);
        });
    }

    @Test
    void testBufferMutationAfterPut() {

        byteBufferPool.doWithBufferPair(50, 50, (keyBuffer, valueBuffer) -> {
            basicLmdbDb.getKeySerde().serialize(keyBuffer, "MyKey");
            basicLmdbDb.getValueSerde().serialize(valueBuffer, "MyValue");

            lmdbEnv.doWithWriteTxn(writeTxn -> {
                basicLmdbDb.put(writeTxn, keyBuffer, valueBuffer, false);
            });
            assertThat(basicLmdbDb.getEntryCount()).isEqualTo(1);

            // it is ok to mutate the buffers used in the put outside of the txn
            basicLmdbDb.getKeySerde().serialize(keyBuffer, "XX");
            basicLmdbDb.getValueSerde().serialize(valueBuffer, "YY");

            // now get the value again and it should be correct
            final String val = basicLmdbDb.get("MyKey").get();

            assertThat(val).isEqualTo("MyValue");
        });
    }

    /**
     * This test is an example of how to abuse LMDB. If run it will crash the JVM
     * as a direct bytebuffer returned from a get() was mutated outside the transaction.
     */
    @Disabled // see javadoc above
    @Test
    void testBufferMutationAfterGet() {

        basicLmdbDb.put("MyKey", "MyValue", false);

        // the buffers have been returned to the pool and cleared
        assertThat(basicLmdbDb.getByteBufferPool().getCurrentPoolSize()).isEqualTo(2);

        assertThat(basicLmdbDb.getEntryCount()).isEqualTo(1);

        final ByteBuffer keyBuffer = ByteBuffer.allocateDirect(50);
        basicLmdbDb.getKeySerde().serialize(keyBuffer, "MyKey");

        final AtomicReference<ByteBuffer> valueBufRef = new AtomicReference<>();
        // now get the value again and it should be correct
        lmdbEnv.getWithReadTxn(txn -> {
            final ByteBuffer valueBuffer = basicLmdbDb.getAsBytes(txn, keyBuffer).get();

            // hold on to the buffer for later
            valueBufRef.set(valueBuffer);

            final String val = basicLmdbDb.getValueSerde().deserialize(valueBuffer);

            assertThat(val).isEqualTo("MyValue");

            return val;
        });

        // now mutate the value buffer outside any txn

        assertThat(valueBufRef.get().position()).isEqualTo(0);


        // This line will crash the JVM as we are mutating a directly allocated ByteBuffer
        // (that points to memory managed by LMDB) outside of a txn.
        basicLmdbDb.getValueSerde().serialize(valueBufRef.get(), "XXX");

    }

    @Test
    void testDupSupport() {
        final BasicLmdbDb<String, String> db = new BasicLmdbDb<>(
                lmdbEnv,
                new ByteBufferPoolFactory().getByteBufferPool(),
                new StringSerde(),
                new StringSerde(),
                "dupDb",
                DbiFlags.MDB_CREATE,
                DbiFlags.MDB_DUPSORT);

        lmdbEnv.doWithWriteTxn(writeTxn -> {
            // Two entries with the same key
            db.put(writeTxn, "key2", "val2c", true);
            db.put(writeTxn, "key2", "val2a", true);
            db.put(writeTxn, "key2", "val2b", true);
            db.put(writeTxn, "key1", "val1", true);
            db.put(writeTxn, "key3", "val3", true);
        });

        db.logDatabaseContents();

        final List<Entry<String, String>> entries = lmdbEnv.getWithReadTxn(readTxn ->
                db.streamEntries(readTxn, KeyRange.all(), stream -> stream
                        .collect(Collectors.toList())));

        LOGGER.info("Entries:\n{}", AsciiTable.builder(entries)
                .withColumn(Column.of("key", Entry::getKey))
                .withColumn(Column.of("value", Entry::getValue))
                .build());

        assertThat(entries)
                .satisfiesExactly(
                        entry1 -> assertThat(entry1)
                                .isEqualTo(Map.entry("key1", "val1")),
                        entry2 -> assertThat(entry2)
                                .isEqualTo(Map.entry("key2", "val2a")),
                        entry3 -> assertThat(entry3)
                                .isEqualTo(Map.entry("key2", "val2b")),
                        entry4 -> assertThat(entry4)
                                .isEqualTo(Map.entry("key2", "val2c")),
                        entry5 -> assertThat(entry5)
                                .isEqualTo(Map.entry("key3", "val3")));
    }

    @Test
    void testDupSupport_unsortedValues() {
        final BasicLmdbDb<UnSortedDupKey<String>, String> db = new BasicLmdbDb<>(
                lmdbEnv,
                new ByteBufferPoolFactory().getByteBufferPool(),
                new UnSortedDupKeySerde<>(new StringSerde()),
                new StringSerde(),
                "dupDb",
                DbiFlags.MDB_CREATE);

        final UnsortedDupKeyFactory<String> keyFactory = UnSortedDupKey.createFactory(String.class);
        lmdbEnv.doWithWriteTxn(writeTxn -> {
            // Three entries with the same key
            db.put(writeTxn, keyFactory.createUnsortedKey("key2"), "val2c", true);
            db.put(writeTxn, keyFactory.createUnsortedKey("key2"), "val2a", true);
            db.put(writeTxn, keyFactory.createUnsortedKey("key2"), "val2b", true);
            db.put(writeTxn, keyFactory.createUnsortedKey("key1"), "val1", true);
            // Two identical entries
            db.put(writeTxn, keyFactory.createUnsortedKey("key3"), "val3", true);
            db.put(writeTxn, keyFactory.createUnsortedKey("key3"), "val3", true);
        });

        db.logDatabaseContents();

        final List<Entry<UnSortedDupKey<String>, String>> entries = lmdbEnv.getWithReadTxn(readTxn ->
                db.streamEntries(readTxn, KeyRange.all(), stream -> stream
                        .collect(Collectors.toList())));

        LOGGER.info("Entries:\n{}", AsciiTable.builder(entries)
                .withColumn(Column.of("key", Entry::getKey))
                .withColumn(Column.of("value", Entry::getValue))
                .build());

        assertThat(entries)
                .extracting(entry -> entry.getKey().getKey())
                .containsExactly("key1",
                        "key2",
                        "key2",
                        "key2",
                        "key3",
                        "key3");

        assertThat(entries)
                .extracting(Entry::getValue)
                .containsExactly("val1",
                        "val2c",
                        "val2a",
                        "val2b",
                        "val3",
                        "val3");
    }

    @Test
    void testDupSupport_noDupData() {
        final BasicLmdbDb<String, String> db = new BasicLmdbDb<>(
                lmdbEnv,
                new ByteBufferPoolFactory().getByteBufferPool(),
                new StringSerde(),
                new StringSerde(),
                "dupDb",
                DbiFlags.MDB_CREATE,
                DbiFlags.MDB_DUPSORT);

        lmdbEnv.doWithWriteTxn(writeTxn -> {
            // Two entries with the same key
            byteBufferPool.doWithBufferPair(10, 10, (keyBuffer, valBuffer) -> {
                keyBuffer.clear();
                db.serializeKey(keyBuffer, "key1");
                valBuffer.clear();
                db.serializeValue(valBuffer, "val1");
                boolean didPut = db.getLmdbDbi().put(writeTxn, keyBuffer, valBuffer, PutFlags.MDB_NODUPDATA);
                Assertions.assertThat(didPut)
                        .isTrue();

                keyBuffer.clear();
                db.serializeKey(keyBuffer, "key2");
                valBuffer.clear();
                db.serializeValue(valBuffer, "val2a");
                didPut = db.getLmdbDbi().put(writeTxn, keyBuffer, valBuffer, PutFlags.MDB_NODUPDATA);
                Assertions.assertThat(didPut)
                        .isTrue();

                keyBuffer.clear();
                db.serializeKey(keyBuffer, "key2");
                valBuffer.clear();
                db.serializeValue(valBuffer, "val2a");
                didPut = db.getLmdbDbi().put(writeTxn, keyBuffer, valBuffer, PutFlags.MDB_NODUPDATA);
                Assertions.assertThat(didPut)
                        .isFalse();
            });
        });

        db.logDatabaseContents();

        final List<Entry<String, String>> entries = lmdbEnv.getWithReadTxn(readTxn ->
                db.streamEntries(readTxn, KeyRange.all(), stream -> stream
                        .collect(Collectors.toList())));

        LOGGER.info("Entries:\n{}", AsciiTable.builder(entries)
                .withColumn(Column.of("key", Entry::getKey))
                .withColumn(Column.of("value", Entry::getValue))
                .build());

        assertThat(entries)
                .satisfiesExactly(
                        entry1 -> assertThat(entry1)
                                .isEqualTo(Map.entry("key1", "val1")),
                        entry2 -> assertThat(entry2)
                                .isEqualTo(Map.entry("key2", "val2a")));
    }

    @Test
    void testGetAsBytes() {

        basicLmdbDb.put("key1", "value1", false);
        basicLmdbDb.put("key2", "value2", false);
        basicLmdbDb.put("key3", "value3", false);

        lmdbEnv.doWithReadTxn(txn -> {
            final Optional<ByteBuffer> optKeyBuffer = basicLmdbDb.getAsBytes(txn, "key2");

            assertThat(optKeyBuffer).isNotEmpty();
        });
    }

    @Test
    void testGetAsBytes2() {

        basicLmdbDb.put("key1", "value1", false);
        basicLmdbDb.put("key2", "value2", false);
        basicLmdbDb.put("key3", "value3", false);

        lmdbEnv.doWithReadTxn(txn -> {

            try (final PooledByteBuffer pooledKeyBuffer = basicLmdbDb.getPooledKeyBuffer()) {
                final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
                basicLmdbDb.serializeKey(keyBuffer, "key2");
                final Optional<ByteBuffer> optValueBuffer = basicLmdbDb.getAsBytes(txn, keyBuffer);

                assertThat(optValueBuffer).isNotEmpty();
                final String val = basicLmdbDb.deserializeKey(optValueBuffer.get());
                assertThat(val).isEqualTo("value2");
            }
        });
    }

    @Test
    void testValueMutation() {
        final int cnt = 10;

        final BasicLmdbDb<String, MultiKey> lmdbDb = new BasicLmdbDb<>(
                lmdbEnv,
                byteBufferPool,
                new StringSerde(),
                new MultiKeySerde(),
                "testValueMutation");

        final List<Entry<String, MultiKey>> entries = new ArrayList<>(cnt);

        IntStream.rangeClosed(1, cnt)
                .boxed()
                .map(i -> new SimpleEntry<>(
                        "key-" + i,
                        new MultiKey(i, i, i, i, "value-" + i)))
                .forEach(entries::add);

        lmdbEnv.doWithWriteTxn(writeTxn -> {
            LOGGER.logDurationIfDebugEnabled(() -> {
                entries
                        .forEach(entry -> {
                            try (final PooledByteBufferPair pooledBufferPair = lmdbDb.getPooledBufferPair()) {
                                final ByteBuffer keyBuff = pooledBufferPair.getKeyBuffer();
                                final ByteBuffer valBuff = pooledBufferPair.getValueBuffer();
                                lmdbDb.serializeKey(keyBuff, entry.getKey());
                                lmdbDb.serializeValue(valBuff, entry.getValue());
                                lmdbDb.getLmdbDbi().put(
                                        writeTxn,
                                        keyBuff,
                                        valBuff,
                                        PutFlags.MDB_NOOVERWRITE);
                            }
                        });
            }, "initial load");
        });

        final TimedCase incrementLongInPlaceCase = TimedCase.of("Increment long in place", (round, iterations) -> {
            if (lmdbEnv.getEnvFlags().contains(EnvFlags.MDB_WRITEMAP)) {
                lmdbEnv.doWithWriteTxn(writeTxn -> {
                    entries
                            .forEach(entry -> {
                                try (final PooledByteBufferPair pooledBufferPair = lmdbDb.getPooledBufferPair()) {
                                    final ByteBuffer keyBuff = pooledBufferPair.getKeyBuffer();
                                    lmdbDb.serializeKey(keyBuff, entry.getKey());
                                    final ByteBuffer valBuff = lmdbDb.getLmdbDbi().get(writeTxn,
                                            keyBuff);

                                    // Mutating the value buffer without a copy only works if MDB_WRITEMAP is set
                                    MultiKeySerde.incrementLong2(valBuff);
                                }
                            });
                });
            } else {
                LOGGER.warn("{} not set on env so can't test increment in place", EnvFlags.MDB_WRITEMAP);
            }
        });

        final TimedCase incUnsignedInPlaceCase = TimedCase.of(
                "Increment unsigned long in place",
                (round, iterations) -> {
                    if (lmdbEnv.getEnvFlags().contains(EnvFlags.MDB_WRITEMAP)) {
                        lmdbEnv.doWithWriteTxn(writeTxn -> {
                            entries.forEach(entry -> {
                                try (final PooledByteBufferPair pooledBufferPair = lmdbDb.getPooledBufferPair()) {
                                    final ByteBuffer keyBuff = pooledBufferPair.getKeyBuffer();
                                    lmdbDb.serializeKey(keyBuff, entry.getKey());
                                    final ByteBuffer valBuff = lmdbDb.getLmdbDbi().get(writeTxn,
                                            keyBuff);

                                    // Mutating the value buffer without a copy only works if MDB_WRITEMAP is set
                                    MultiKeySerde.incrementUnsignedLong(valBuff);
                                }
                            });
                        });
                    } else {
                        LOGGER.warn("{} not set on env so can't test increment in place", EnvFlags.MDB_WRITEMAP);
                    }
                });

        final TimedCase getPutCase = TimedCase.of("Get/Put increment", (round, iterations) -> {
            lmdbEnv.doWithWriteTxn(writeTxn -> {
                entries.forEach(entry -> {
                    try (final PooledByteBufferPair pooledBufferPair = lmdbDb.getPooledBufferPair()) {
                        final ByteBuffer keyBuff = pooledBufferPair.getKeyBuffer();
                        final ByteBuffer newValBuff = pooledBufferPair.getValueBuffer();
                        lmdbDb.serializeKey(keyBuff, entry.getKey());
                        // Mutate the value using get/put via a copy of the buffer. This is the only
                        // way if MDB_WRITEMAP is not set.
                        final ByteBuffer dbValBuff = lmdbDb.getLmdbDbi().get(
                                writeTxn,
                                keyBuff);
                        ByteBufferUtils.copy(dbValBuff, newValBuff);

                        UNSIGNED_BYTES.increment(newValBuff);
                        MultiKeySerde.incrementLong2(newValBuff);
                        lmdbDb.put(writeTxn, keyBuff, newValBuff, false);
                    }
                });
            });
        });

        TestUtil.comparePerformance(
                2,
                cnt,
                LOGGER::info,
                incrementLongInPlaceCase,
                incUnsignedInPlaceCase,
                getPutCase);
    }

    @Test
    void testLoadingSortedKeys() {
        final List<Entry<String, String>> entries = IntStream.rangeClosed(1, 10)
                .boxed()
                .map(i -> new SimpleEntry<>("key-" + i, "value-" + i))
                .collect(Collectors.toList());

        // Random order for 1st load
        Collections.shuffle(entries);

        lmdbEnv.doWithWriteTxn(writeTxn -> {
            LOGGER.logDurationIfDebugEnabled(() -> {
                entries
                        .forEach(entry -> {
                            try (final PooledByteBufferPair pooledBufferPair = basicLmdbDb.getPooledBufferPair()) {
                                final ByteBuffer keyBuff = pooledBufferPair.getKeyBuffer();
                                final ByteBuffer valBuff = pooledBufferPair.getValueBuffer();
                                basicLmdbDb.serializeKey(keyBuff, entry.getKey());
                                basicLmdbDb.serializeValue(valBuff, entry.getValue());
                                basicLmdbDb.getLmdbDbi().put(
                                        writeTxn,
                                        keyBuff,
                                        valBuff,
                                        PutFlags.MDB_NOOVERWRITE);
                            }
                        });
            }, "un-sorted puts");
        });

        // Read all entries back out in lmdb sort order
        final List<Entry<String, String>> sortedEntries = lmdbEnv.getWithReadTxn(readTxn ->
                basicLmdbDb.streamEntries(readTxn, KeyRange.all(), stream ->
                        stream.collect(Collectors.toList())));

        // Now load them into the other db in order using MDB_APPEND to tell LMDB they are in order
        lmdbEnv.doWithWriteTxn(writeTxn -> {
            LOGGER.logDurationIfDebugEnabled(() -> {
                sortedEntries
                        .forEach(entry -> {
                            try (final PooledByteBufferPair pooledBufferPair = basicLmdbDb2.getPooledBufferPair()) {
                                final ByteBuffer keyBuff = pooledBufferPair.getKeyBuffer();
                                final ByteBuffer valBuff = pooledBufferPair.getValueBuffer();
                                basicLmdbDb2.serializeKey(keyBuff, entry.getKey());
                                basicLmdbDb2.serializeValue(valBuff, entry.getValue());
                                basicLmdbDb2.getLmdbDbi().put(
                                        writeTxn,
                                        keyBuff,
                                        valBuff,
                                        PutFlags.MDB_NOOVERWRITE,
                                        PutFlags.MDB_APPEND);
                            }
                        });
            }, "sorted puts");
        });

        // Now do all the puts again overwriting values
        lmdbEnv.doWithWriteTxn(writeTxn -> {
            LOGGER.logDurationIfDebugEnabled(() -> {
                sortedEntries
                        .forEach(entry -> {
                            try (final PooledByteBufferPair pooledBufferPair = basicLmdbDb2.getPooledBufferPair()) {
                                final ByteBuffer keyBuff = pooledBufferPair.getKeyBuffer();
                                final ByteBuffer valBuff = pooledBufferPair.getValueBuffer();
                                basicLmdbDb2.serializeKey(keyBuff, entry.getKey());
                                basicLmdbDb2.serializeValue(valBuff, entry.getValue());
                                basicLmdbDb2.getLmdbDbi().put(
                                        writeTxn,
                                        keyBuff,
                                        valBuff);
                                // Can't user MDB_APPEND now as it will barf when it finds an existing key
                            }
                        });
            }, "sorted puts 2");
        });
    }

    @Test
    void testAppending_success() {

        basicLmdbDb.put("key1", "val1", false, true);
        basicLmdbDb.put("key2", "val2", false, true);
        // Dup
        basicLmdbDb.put("key2", "val2", false, true);

        assertThat(basicLmdbDb.getEntryCount())
                .isEqualTo(2);
        lmdbEnv.doWithReadTxn(readTxn -> {
            final List<Entry<String, String>> entries = basicLmdbDb.streamEntries(
                    readTxn,
                    KeyRange.all(), Stream::toList);

            assertThat(entries)
                    .extracting(Entry::getKey)
                    .containsExactly("key1", "key2");
            assertThat(entries)
                    .extracting(Entry::getValue)
                    .containsExactly("val1", "val2");
        });
    }

    @Test
    void testAppending_outOfOrder_NoOverwrite() {

        basicLmdbDb.put("key2", "val2", false, true);
        // dup
        basicLmdbDb.put("key2", "val2", false, true);
        basicLmdbDb.put("key1", "val1", false, true);

        assertThat(basicLmdbDb.getEntryCount())
                .isEqualTo(1);
        lmdbEnv.doWithReadTxn(readTxn -> {
            final List<Entry<String, String>> entries = basicLmdbDb.streamEntries(
                    readTxn,
                    KeyRange.all(), Stream::toList);

            assertThat(entries)
                    .extracting(Entry::getKey)
                    .containsExactly("key2");
            assertThat(entries)
                    .extracting(Entry::getValue)
                    .containsExactly("val2");
        });
    }

    @Test
    void testAppending_outOfOrder_Overwrite() {

        basicLmdbDb.put("key2", "val2", true, true);
        basicLmdbDb.put("key1", "val1", true, true);
        basicLmdbDb.put("key2", "val2", true, true);

        assertThat(basicLmdbDb.getEntryCount())
                .isEqualTo(2);
        lmdbEnv.doWithReadTxn(readTxn -> {
            final List<Entry<String, String>> entries = basicLmdbDb.streamEntries(
                    readTxn,
                    KeyRange.all(), Stream::toList);

            assertThat(entries)
                    .extracting(Entry::getKey)
                    .containsExactly("key1", "key2");
            assertThat(entries)
                    .extracting(Entry::getValue)
                    .containsExactly("val1", "val2");
        });
    }

    private TimedCase buildPutPerfTestCase(
            final String testName,
            final TriConsumer<Txn<ByteBuffer>, ByteBuffer, ByteBuffer> putFunc) {

        return TimedCase.of(testName, (round, iterations) -> {
            final int roundIdx = round - 1;
            try (final PooledByteBufferPair pooledBufferPair = basicLmdbDb.getPooledBufferPair()) {
                final long fromInc = iterations * roundIdx;
                final long toExc = iterations * (roundIdx + 1);
                lmdbEnv.doWithWriteTxn(writeTxn -> {
                    LongStream.range(fromInc, toExc)
                            .forEach(i -> {
                                final ByteBuffer keyBuff = pooledBufferPair.getKeyBuffer();
                                keyBuff.clear();
                                final ByteBuffer valBuff = pooledBufferPair.getValueBuffer();
                                valBuff.clear();
                                basicLmdbDb.serializeKey(
                                        keyBuff,
                                        "key-" + Strings.padStart(
                                                Long.toString(i), 10, '0'));
                                basicLmdbDb.serializeValue(
                                        valBuff,
                                        "val-" + Strings.padStart(
                                                Long.toString(i), 10, '0'));
                                // Do the put
                                putFunc.accept(writeTxn, keyBuff, valBuff);
                            });
                });
            }
        });
    }

    @Disabled // manual perf test only
    @Test
    void testLoadingSortedKeys_perf() {
        final TimedCase case0 = buildPutPerfTestCase("DB warm up",
                (writeTxn, keyBuffer, valueBuffer) -> {
                    basicLmdbDb.getLmdbDbi().put(writeTxn, keyBuffer, valueBuffer);
                });
        final TimedCase case1 = buildPutPerfTestCase("Dbi no flags",
                (writeTxn, keyBuffer, valueBuffer) -> {
                    basicLmdbDb.getLmdbDbi().put(writeTxn, keyBuffer, valueBuffer);
                });
        final TimedCase case2 = buildPutPerfTestCase("Dbi NOOVERWRITE",
                (writeTxn, keyBuffer, valueBuffer) -> {
                    basicLmdbDb.getLmdbDbi().put(writeTxn, keyBuffer, valueBuffer, PutFlags.MDB_NOOVERWRITE);
                });
        final TimedCase case3 = buildPutPerfTestCase("Dbi NOOVERWRITE APPEND",
                (writeTxn, keyBuffer, valueBuffer) -> {
                    basicLmdbDb.getLmdbDbi().put(writeTxn,
                            keyBuffer,
                            valueBuffer,
                            PutFlags.MDB_NOOVERWRITE, PutFlags.MDB_APPEND);
                });
        final TimedCase case4 = buildPutPerfTestCase("AbstractLmdb noOverwrite noAppend",
                (writeTxn, keyBuffer, valueBuffer) -> {
                    basicLmdbDb.put(writeTxn,
                            keyBuffer,
                            valueBuffer,
                            false,
                            false);
                });
        final TimedCase case5 = buildPutPerfTestCase("AbstractLmdb noOverwrite append",
                (writeTxn, keyBuffer, valueBuffer) -> {
                    basicLmdbDb.put(writeTxn,
                            keyBuffer,
                            valueBuffer,
                            false,
                            true);
                });
        final TimedCase case6 = buildPutPerfTestCase("AbstractLmdb overwrite append",
                (writeTxn, keyBuffer, valueBuffer) -> {
                    basicLmdbDb.put(writeTxn,
                            keyBuffer,
                            valueBuffer,
                            true,
                            true);
                });
        TestUtil.comparePerformance(
                2,
                1_000_000,
                (rounds, iterations) -> basicLmdbDb.drop(),
                LOGGER::debug,
                case0,
                case1,
                case2,
                case3,
                case4,
                case5,
                case6);

        LOGGER.debug("basicLmdbDb count:  {}", ModelStringUtil.formatCsv(basicLmdbDb.getEntryCount()));
    }

    /**
     * Comparing overwriting the same key N times vs putting N entries each with a different key
     */
    @Disabled
    @Test
    void testPutVsAppend_perf() {
        final TimedCase dbWarmUp = TimedCase.of("DB warm up", (round, iterations) -> {
            final int roundIdx = round - 1;
            final long fromInc = iterations * roundIdx;
            final long toExc = iterations * (roundIdx + 1);
            lmdbEnv.doWithWriteTxn(writeTxn -> {
                LongStream.range(fromInc, toExc)
                        .forEach(i -> {
                            basicLmdbDb.put(writeTxn,
                                    "key-" + Strings.padStart(
                                            Long.toString(i), 10, '0'),
                                    "val-" + Strings.padStart(
                                            Long.toString(i), 10, '0'),
                                    false,
                                    false);
                        });
            });
        });

        final TimedCase putToSameKey = TimedCase.of("Put to same key", (round, iterations) -> {
            final int roundIdx = round - 1;
            final long fromInc = iterations * roundIdx;
            final long toExc = iterations * (roundIdx + 1);
            lmdbEnv.doWithWriteTxn(writeTxn -> {
                LongStream.range(fromInc, toExc)
                        .forEach(i -> {
                            // Same key every time, diff val
                            basicLmdbDb.put(writeTxn,
                                    "key-" + Strings.padStart(
                                            Long.toString(fromInc), 10, '0'),
                                    "val-" + Strings.padStart(
                                            Long.toString(i), 10, '0'),
                                    true,
                                    false);
                        });
            });
        });

        final TimedCase putNewKeys = TimedCase.of("Put new keys", (round, iterations) -> {
            final int roundIdx = round - 1;
            final long fromInc = iterations * roundIdx;
            final long toExc = iterations * (roundIdx + 1);
            lmdbEnv.doWithWriteTxn(writeTxn -> {
                LongStream.range(fromInc, toExc)
                        .forEach(i -> {
                            // Same key every time, diff val
                            basicLmdbDb.put(writeTxn,
                                    "key-" + Strings.padStart(
                                            Long.toString(i), 10, '0'),
                                    "val-" + Strings.padStart(
                                            Long.toString(i), 10, '0'),
                                    false,
                                    true);
                        });
            });
        });

        TestUtil.comparePerformance(
                3,
                1_000_000,
                LOGGER::info,
                dbWarmUp,
                putToSameKey,
                putNewKeys);
    }

    @Test
    void testKeyRange() {
        basicLmdbDb.put("key11", "value1", false);
        basicLmdbDb.put("key12", "value1", false);
        basicLmdbDb.put("key13", "value1", false);
        basicLmdbDb.put("key21", "value2", false);
        basicLmdbDb.put("key22", "value2", false);
        basicLmdbDb.put("key23", "value2", false);
        basicLmdbDb.put("key31", "value3", false);
        basicLmdbDb.put("key32", "value3", false);
        basicLmdbDb.put("key33", "value3", false);

        lmdbEnv.doWithReadTxn(txn -> {

            try (final PooledByteBuffer pooledStartKeyBuffer = basicLmdbDb.getPooledKeyBuffer();
                    final PooledByteBuffer pooledEndKeyBuffer = basicLmdbDb.getPooledKeyBuffer()) {

                final ByteBuffer startKeyBuffer = pooledStartKeyBuffer.getByteBuffer();
                final ByteBuffer endKeyBuffer = pooledEndKeyBuffer.getByteBuffer();

                final String startKey = "key2";
                final String endKey = "key3";
                basicLmdbDb.serializeKey(startKeyBuffer, startKey);
                basicLmdbDb.serializeKey(endKeyBuffer, endKey);
                LOGGER.info("{} => {}",
                        ByteBufferUtils.byteBufferInfo(startKeyBuffer),
                        ByteBufferUtils.byteBufferInfo(endKeyBuffer));

                final KeyRange<ByteBuffer> keyRange = KeyRange.closedOpen(startKeyBuffer, endKeyBuffer);

                basicLmdbDb.forEachEntryAsBytes(txn, keyRange, kvTuple -> {
                    LOGGER.info("{} - {}",
                            ByteBufferUtils.byteBufferInfo(kvTuple.key()),
                            ByteBufferUtils.byteBufferInfo(kvTuple.val()));
                });

                final List<String> keysFound = new ArrayList<>();

                basicLmdbDb.forEachEntry(txn, KeyRange.closedOpen(startKey, endKey), kvTuple -> {
                    keysFound.add(kvTuple.getKey());
                });

                assertThat(keysFound)
                        .containsExactly("key21", "key22", "key23");
            }
        });
    }

    @Test
    void testDrop() {
        basicLmdbDb.put("key11", "value1", false);
        basicLmdbDb.put("key12", "value1", false);

        assertThat(basicLmdbDb.getEntryCount())
                .isEqualTo(2);

        lmdbEnv.doWithWriteTxn(basicLmdbDb::drop);

        assertThat(basicLmdbDb.getEntryCount())
                .isEqualTo(0);
    }

    @Test
    void testStreamEntries() {
        final int count = 5;
        populateDb(count);

        final List<Entry<String, String>> entries = lmdbEnv.getWithReadTxn(txn ->
                basicLmdbDb.streamEntries(txn, KeyRange.all(), stream ->
                        stream
                                .peek(entry -> LOGGER.info("key: '{}', value: '{}'",
                                        entry.getKey(), entry.getValue()))
                                .collect(Collectors.toList())));

        assertThat(entries)
                .hasSize(count);
        assertThat(entries)
                .extracting(Entry::getKey)
                .containsExactly(
                        "01",
                        "02",
                        "03",
                        "04",
                        "05");
    }

    @Test
    void testStreamEntriesWithFilter() {
        final int count = 10;
        populateDb(count);

        final List<Entry<String, String>> entries = lmdbEnv.getWithReadTxn(txn ->
                basicLmdbDb.streamEntries(txn, KeyRange.all(), stream ->
                        stream
                                .filter(entry -> {
                                    final int i = Integer.parseInt(entry.getKey());
                                    return i > 3 && i <= 7;
                                })
                                .peek(entry -> LOGGER.info("key: '{}', value: '{}'",
                                        entry.getKey(), entry.getValue()))
                                .collect(Collectors.toList())));

        assertThat(entries)
                .hasSize(4);
        assertThat(entries)
                .extracting(Entry::getKey)
                .containsExactly(
                        "04",
                        "05",
                        "06",
                        "07");
    }

    @Test
    void testStreamEntriesWithKeyRange() {
        final int count = 15;
        populateDb(count);

        final KeyRange<String> keyRange = KeyRange.closed("06", "10");
        final List<Entry<String, String>> entries = lmdbEnv.getWithReadTxn(txn ->
                basicLmdbDb.streamEntries(txn, keyRange, stream ->
                        stream
                                .peek(entry -> LOGGER.info("key: '{}', value: '{}'",
                                        entry.getKey(), entry.getValue()))
                                .collect(Collectors.toList())));

        assertThat(entries)
                .hasSize(5);
        assertThat(entries)
                .extracting(Entry::getKey)
                .containsExactly(
                        "06",
                        "07",
                        "08",
                        "09",
                        "10");
    }

    @Test
    void testKeyReuse() {
        // key 1 => key2 => value2 & value 3
        basicLmdbDb.put("key1", "key2", false);
        basicLmdbDb.put("key2", "value2", false);

        // different DB with same key in it so we can test two lookups using same key
        basicLmdbDb2.put("key2", "value3", false);

        lmdbEnv.doWithReadTxn(txn -> {
            final ByteBuffer keyBuffer = ByteBuffer.allocateDirect(100);
            basicLmdbDb.serializeKey(keyBuffer, "key1");
            final ByteBuffer keyBufferCopy = keyBuffer.asReadOnlyBuffer();

            final ByteBuffer valueBuffer = basicLmdbDb.getAsBytes(txn, keyBuffer).get();
            final ByteBuffer keyBuffer2 = ByteBufferUtils.copyToDirectBuffer(valueBuffer);
            final String value = basicLmdbDb.deserializeValue(valueBuffer);
            final ByteBuffer valueBufferCopy = valueBuffer.asReadOnlyBuffer();

            assertThat(value).isEqualTo("key2");
            assertThat(keyBuffer).isEqualTo(keyBufferCopy);
            assertThat(valueBuffer).isEqualTo(valueBufferCopy);
            assertThat(valueBuffer).isEqualTo(keyBuffer2);

            // now use the value from the last get() as the key for a new get()
            final ByteBuffer valueBuffer2 = basicLmdbDb.getAsBytes(txn, valueBuffer).get();

            final String value2 = basicLmdbDb.deserializeValue(valueBuffer2);
            final String valueBufferDeserialised = basicLmdbDb.deserializeKey(valueBuffer);

            assertThat(value2).isEqualTo("value2");

            // The second get() has overwritten our original valueBuffer with the value
            // of the second get(). This is because the txn essentially holds a cursor
            // whose position is updated by the get and that cursor is bound to the value
            // buffer returned by the get. The value from the get() can be used as a key
            // in another get() once, but that second get() will mutate it prevent it from
            // being used as a key in another get().
            assertThat(valueBufferDeserialised).isEqualTo("value2");
            assertThat(valueBuffer).isEqualTo(valueBuffer2);
            assertThat(valueBuffer).isNotEqualTo(valueBufferCopy);
            assertThat(keyBuffer2).isNotEqualTo(valueBuffer);

            // We can't use valueBuffer for our key here is it now points to "value2"
            final ByteBuffer valueBuffer3 = basicLmdbDb2.getAsBytes(txn, keyBuffer2).get();

            final String value3 = basicLmdbDb2.deserializeValue(valueBuffer3);

            assertThat(value3).isEqualTo("value3");
            assertThat(keyBuffer).isEqualTo(keyBufferCopy);
        });
    }

    @Test
    void testValueReuse() {
        basicLmdbDb.put("key1", "value1", false);
        basicLmdbDb.put("key2", "value2", false);

        lmdbEnv.doWithReadTxn(txn -> {

            final ByteBuffer keyBuffer1 = ByteBuffer.allocateDirect(100);
            basicLmdbDb.serializeKey(keyBuffer1, "key1");

            final ByteBuffer valueBuffer1 = basicLmdbDb.getAsBytes(txn, keyBuffer1).get();
            final ByteBuffer valueBuffer1Copy = valueBuffer1.asReadOnlyBuffer();
            assertThat(valueBuffer1Copy).isEqualTo(valueBuffer1);
            String value1 = basicLmdbDb.deserializeValue(valueBuffer1);

            assertThat(value1).isEqualTo("value1");

            // now do another get on a different key to get a different value
            final ByteBuffer keyBuffer2 = ByteBuffer.allocateDirect(100);
            basicLmdbDb.serializeKey(keyBuffer2, "key2");
            final ByteBuffer valueBuffer2 = basicLmdbDb.getAsBytes(txn, keyBuffer2).get();

            final String value2 = basicLmdbDb.deserializeValue(valueBuffer2);
            assertThat(value2).isEqualTo("value2");

            // The valueBuffer1 is tied to the txn's cursor which has now moved to key2 => value2,
            // thus valueBuffer1 now contains "value2".
            value1 = basicLmdbDb.deserializeValue(valueBuffer1);
            assertThat(value1).isEqualTo("value2");
            assertThat(valueBuffer1Copy).isNotEqualTo(valueBuffer1);
        });
    }

    @Test
    void testKeyUseOutsideCursor() {
        basicLmdbDb.put("key1", "value1", false);
        basicLmdbDb.put("key2", "value2", false);

        lmdbEnv.doWithReadTxn(txn -> {

            final ByteBuffer keyBuffer1 = ByteBuffer.allocateDirect(100);
            basicLmdbDb.serializeKey(keyBuffer1, "key1");

            final ByteBuffer startKeyBuf = ByteBuffer.allocateDirect(100);
            basicLmdbDb.serializeKey(startKeyBuf, "key1");

            // Same start/end key
            final KeyRange<ByteBuffer> keyRange = KeyRange.closed(startKeyBuf, startKeyBuf);

            ByteBuffer foundKeyBuffer = null;
            try (final CursorIterable<ByteBuffer> cursorIterable = basicLmdbDb.iterate(txn, keyRange)) {
                final Iterator<KeyVal<ByteBuffer>> iterator = cursorIterable.iterator();
                if (iterator.hasNext()) {
                    foundKeyBuffer = iterator.next().key();
                }
            }

            // foundKeyBuffer is not out of scope of the cursor
            assertThat(foundKeyBuffer)
                    .isNotNull();

            assertThat(basicLmdbDb.deserializeKey(foundKeyBuffer))
                    .isEqualTo("key1");

            // now do another get on a different key to get a different value
            final ByteBuffer keyBuffer2 = ByteBuffer.allocateDirect(100);
            basicLmdbDb.serializeKey(keyBuffer2, "key2");
            basicLmdbDb.getAsBytes(txn, keyBuffer2).get();

            // Now bake sure the buffer we got in the cursor is still the same
            // and has not been affected by the other get.
            assertThat(basicLmdbDb.deserializeKey(foundKeyBuffer))
                    .isEqualTo("key1");
        });
    }

    @Test
    void testVerifyNumericKeyOrder() {

        // Ensure entries come back in the right order
        final List<Entry<Integer, String>> data = new ArrayList<>(List.of(
                Map.entry(1, "val1"),
                Map.entry(2, "val2"),
                Map.entry(3, "val3"),
                Map.entry(4, "val4")));

        Collections.shuffle(data, new Random(12345L));

        lmdbEnv.doWithWriteTxn(writeTxn -> {
            data.forEach(entry -> {
                basicLmdbDb3.put(writeTxn, entry.getKey(), entry.getValue(), false);
            });
        });

        final KeyRange<Integer> keyRangeAll = KeyRange.all();

        final List<Integer> output = lmdbEnv.getWithReadTxn(readTxn ->
                basicLmdbDb3.streamEntries(readTxn, keyRangeAll, stream ->
                        stream
                                .map(Entry::getKey)
                                .collect(Collectors.toList())));

        // Verify key order
        assertThat(output)
                .containsExactly(
                        1,
                        2,
                        3,
                        4);
    }

    /**
     * This is more of a manual performance test for comparing the difference between
     * puts in integer order vs in random order. Also compares the impact of the INTEGER_KEY
     * dbi flag, which seems to slow things down a fair bit.
     * <p>
     * The following test output was for 10_000_000 entries.
     * </p>
     * <pre>
     * Completed [Puts: Ascending] in PT3.056630567S
     * Completed [Puts: Random] in PT9.910205266S
     * Completed [Puts: Ascending (INTEGER_KEY)] in PT2.41477686S
     * Completed [Puts: Random (INTEGER_KEY)] in PT9.109282535S
     * Completed [Iteration] in PT3.5032606S
     * Completed [Iteration (INTEGER_KEY)] in PT3.531630825S
     * </pre>
     */
    @Test
    void testLoadOrderAndIntKeyPerformance() {

//        final int iterations = 1_000_000;
//        final int iterations = 10_000_000;
        final int iterations = 10;

        LOGGER.info("info {}", basicLmdbDb3.getDbInfo());

        // Ensure entries come back in the right order
        final List<Tuple2<Integer, String>> ascendingData = IntStream
                .range(0, iterations)
                .boxed()
                .map(i -> Tuple.of(i, String.format("Val %010d", i)))
                .collect(Collectors.toList());

        assertThat(ascendingData)
                .hasSize(iterations);

        final List<Tuple2<Integer, String>> randomData = IntStream
                .range(Integer.MAX_VALUE - iterations, Integer.MAX_VALUE)
                .boxed()
                .map(i -> Tuple.of(i, String.format("Val %010d", i)))
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(randomData);

        assertThat(ascendingData)
                .hasSize(iterations);

        LOGGER.logDurationIfInfoEnabled(() -> {
            lmdbEnv.doWithWriteTxn(writeTxn -> {
                ascendingData.forEach(tuple -> {
                    basicLmdbDb3.put(writeTxn, tuple._1(), tuple._2(), false);
                });
            });
        }, "Puts: Ascending");

        LOGGER.logDurationIfInfoEnabled(() -> {
            lmdbEnv.doWithWriteTxn(writeTxn -> {
                randomData.forEach(tuple -> {
                    basicLmdbDb3.put(writeTxn, tuple._1(), tuple._2(), false);
                });
            });
        }, "Puts: Random");


        // If you want to use MDB_INTEGERKEY then you MUST set the byte buffer to nativeOrder before
        // writing/reading. See https://github.com/lmdbjava/lmdbjava/issues/51
        LOGGER.logDurationIfInfoEnabled(() -> {
            lmdbEnv.doWithWriteTxn(writeTxn -> {
                ascendingData.forEach(tuple -> {
                    basicLmdbDb4.put(writeTxn, tuple._1(), tuple._2(), false);
                });
            });
        }, "Puts: Ascending (INTEGER_KEY)");

        LOGGER.logDurationIfInfoEnabled(() -> {
            lmdbEnv.doWithWriteTxn(writeTxn -> {
                randomData.forEach(tuple -> {
                    basicLmdbDb4.put(writeTxn, tuple._1(), tuple._2(), false);
                });
            });
        }, "Puts: Random (INTEGER_KEY)");

        final int iterationRounds = 5;

        LOGGER.logDurationIfInfoEnabled(() -> {
            lmdbEnv.doWithReadTxn(readTxn -> {
                for (int i = 0; i < iterationRounds; i++) {
                    try (final CursorIterable<ByteBuffer> cursorIterable = basicLmdbDb3.iterate(readTxn)) {
                        final Iterator<KeyVal<ByteBuffer>> iterator = cursorIterable.iterator();
                        long sum = 0;
                        while (iterator.hasNext()) {
                            final KeyVal<ByteBuffer> keyVal = iterator.next();
                            final int key = basicLmdbDb3.deserializeKey(keyVal.key());
                            sum += key;
                        }
                    }
                }
            });
        }, "Iteration");

        LOGGER.logDurationIfInfoEnabled(() -> {
            lmdbEnv.doWithReadTxn(readTxn -> {
                for (int i = 0; i < iterationRounds; i++) {
                    try (final CursorIterable<ByteBuffer> cursorIterable = basicLmdbDb4.iterate(readTxn)) {
                        final Iterator<KeyVal<ByteBuffer>> iterator = cursorIterable.iterator();
                        long sum = 0;
                        while (iterator.hasNext()) {
                            final KeyVal<ByteBuffer> keyVal = iterator.next();
                            final int key = basicLmdbDb4.deserializeKey(keyVal.key());
                            sum += key;
                        }
                    }
                }
            });
        }, "Iteration (INTEGER_KEY)");

        if (iterations < 50) {
            basicLmdbDb3.logDatabaseContents(LOGGER::info);
            basicLmdbDb3.logRawDatabaseContents(LOGGER::info);
            basicLmdbDb4.logDatabaseContents(LOGGER::info);
            basicLmdbDb4.logRawDatabaseContents(LOGGER::info);
        }
        LOGGER.info("entry count: " + basicLmdbDb3.getEntryCount());
        LOGGER.info("entry count: " + basicLmdbDb4.getEntryCount());
    }

    @Test
    void testMaxReaders() {
        assertThat(lmdbEnv.info().numReaders)
                .isEqualTo(0);
        IntStream.rangeClosed(1, 20).forEach(i -> {
            basicLmdbDb.put(buildKey(i), buildValue(i), false);

        });
        // Show that writes to the db do not effect the num readers high water mark
        assertThat(lmdbEnv.info().numReaders)
                .isEqualTo(0);

        basicLmdbDb.get(buildKey(1));

        assertThat(lmdbEnv.info().numReaders)
                .isEqualTo(1);
    }

    /**
     * Intended for manual running at high iteration count to test lookup difference
     */
    @Test
    void testGetVsCursorPerformance() {

//        final int iterations = 100_000;
//        final int iterations = 1_000_000;
//        final int iterations = 10_000_000;
        final int iterations = 10;

        LOGGER.info("info {}", basicLmdbDb3.getDbInfo());

        // Ensure entries come back in the right order
        final List<Tuple2<Integer, String>> ascendingData = IntStream
                .range(0, iterations)
                .boxed()
                .map(i -> Tuple.of(i, String.format("Val %010d", i)))
                .collect(Collectors.toList());

        assertThat(ascendingData)
                .hasSize(iterations);

        LOGGER.logDurationIfInfoEnabled(() -> {
            lmdbEnv.doWithWriteTxn(writeTxn -> {
                ascendingData.forEach(tuple -> {
                    basicLmdbDb3.put(writeTxn, tuple._1(), tuple._2(), false);
                });
            });
        }, "Ascending puts");

        if (iterations < 50) {
            basicLmdbDb3.logDatabaseContents(LOGGER::info);
            basicLmdbDb3.logRawDatabaseContents(LOGGER::info);
        }

        LOGGER.info("entry count: " + basicLmdbDb3.getEntryCount());

        final int runCount = 3;
        // Do it a few times so the jvm can warm up
        for (int i = 0; i < runCount; i++) {
            LOGGER.logDurationIfInfoEnabled(() -> {
                lmdbEnv.doWithReadTxn(writeTxn -> {
                    ascendingData.forEach(tuple -> {
                        final Integer key = tuple._1();
                        final String val = basicLmdbDb3.get(writeTxn, key)
                                .orElseThrow(() ->
                                        new RuntimeException("No value for key " + key));
                        assertThat(val)
                                .isEqualTo(tuple._2());
                    });
                });
            }, "Gets");
        }

        for (int i = 0; i < runCount; i++) {
            LOGGER.logDurationIfInfoEnabled(() -> {
                lmdbEnv.doWithReadTxn(readTxn -> {
                    ascendingData.forEach(tuple -> {
                        final Integer key = tuple._1();

                        try (final PooledByteBuffer startKeyBuf = basicLmdbDb3.getPooledKeyBuffer();
                                final PooledByteBuffer endKeyBuf = basicLmdbDb3.getPooledKeyBuffer()) {

                            basicLmdbDb3.serializeKey(startKeyBuf.getByteBuffer(), key);
                            basicLmdbDb3.serializeKey(endKeyBuf.getByteBuffer(), key);

                            final KeyRange<ByteBuffer> keyRange = KeyRange.closed(
                                    startKeyBuf.getByteBuffer(),
                                    endKeyBuf.getByteBuffer());

                            try (final CursorIterable<ByteBuffer> cursorIterable = basicLmdbDb3.iterate(
                                    readTxn, keyRange)) {

                                final Iterator<KeyVal<ByteBuffer>> iterator = cursorIterable.iterator();
                                String val = null;
                                if (iterator.hasNext()) {
                                    val = basicLmdbDb3.deserializeValue(iterator.next().val());
                                } else {
                                    fail(LogUtil.message("Key {} not found", key));
                                }

                                assertThat(val)
                                        .isEqualTo(tuple._2());
                            }
                        }
                    });
                });
            }, "Cursor gets");
        }
    }

    /**
     * Ensure two envs can operate independently of each other, i.e. both hold a write txn open
     * in different threads.
     */
    @Test
    void testConcurrentEnvs() throws IOException, ExecutionException, InterruptedException {

        try (final TemporaryPathCreator temporaryPathCreator = new TemporaryPathCreator()) {
            final EnvFlags[] envFlags = new EnvFlags[]{
                    EnvFlags.MDB_NOTLS
            };
            LOGGER.info("baseDir: {}", temporaryPathCreator.getBaseTempDir().toAbsolutePath().normalize());

            final BasicLmdbDb<String, String> basicLmdb1 = createEnvAndDb(temporaryPathCreator, envFlags, "1");
            final BasicLmdbDb<String, String> basicLmdb2 = createEnvAndDb(temporaryPathCreator, envFlags, "2");
            final CountDownLatch countDownLatch = new CountDownLatch(2);

            final CompletableFuture<Void> future1 = CompletableFuture.runAsync(() -> {
                LOGGER.info("Opening writeTxn1");
                basicLmdb1.getLmdbEnvironment().doWithWriteTxn(writeTxn1 -> {
                    LOGGER.info("writeTxn1 open");
                    basicLmdb1.put(writeTxn1, "1", "one", true);
                    LOGGER.info("put 1");
                    countDownLatch.countDown();
                    try {
                        countDownLatch.await();
                    } catch (final InterruptedException e) {
                        throw new UncheckedInterruptedException(e);
                    }
                    LOGGER.info("Closing writeTxn1");
                });
            });

            final CompletableFuture<Void> future2 = CompletableFuture.runAsync(() -> {
                LOGGER.info("Opening writeTxn2");
                basicLmdb2.getLmdbEnvironment().doWithWriteTxn(writeTxn2 -> {
                    LOGGER.info("writeTxn2 open");
                    basicLmdb2.put(writeTxn2, "2", "two", true);
                    LOGGER.info("put 2");
                    countDownLatch.countDown();
                    try {
                        countDownLatch.await();
                    } catch (final InterruptedException e) {
                        throw new UncheckedInterruptedException(e);
                    }
                    LOGGER.info("Closing writeTxn2");
                });
            });

            future1.get();
            future2.get();

            assertThat(basicLmdb1.get("1"))
                    .hasValue("one");
            assertThat(basicLmdb2.get("2"))
                    .hasValue("two");
        }
    }

    /**
     * Verify that you can do a lookup on one db while in a cursor loop on another
     */
    @Test
    void testGetInsideCursorLoop() {
        putValues(basicLmdbDb3, false, Map.of(
                1, "Jan",
                2, "Feb",
                3, "Mar",
                4, "Apr",
                5, "May",
                6, "Jun"));

        putValues(basicLmdbDb2, false, Map.of(
                "Jan", "January",
                "Feb", "February",
                "Mar", "March",
                "Apr", "April",
                "May", "May",
                "Jun", "June"));

        lmdbEnv.doWithReadTxn(readTxn -> {
            basicLmdbDb3.forEachEntry(readTxn, entry -> {
                final Integer monthNum = entry.getKey();
                final String shortForm = entry.getValue();

                final String longForm = basicLmdbDb2.get(readTxn, shortForm)
                        .orElseThrow();

                LOGGER.info("monthNum: {}, shortForm: {}, longForm: {}", monthNum, shortForm, longForm);
            });
        });
    }

    /**
     * Verify that you can iterate over a cursor on one db while inside a cursor loop on another db
     */
    @Test
    void testCursorInsideCursorLoop() {
        putValues(basicLmdbDb3, false, Map.of(
                1, "Jan",
                2, "Feb",
                3, "Mar",
                4, "Apr",
                5, "May",
                6, "Jun"));

        putValues(basicLmdbDb2, false, Map.of(
                "Jan", "January",
                "Feb", "February",
                "Mar", "March",
                "Apr", "April",
                "May", "May",
                "Jun", "June"));

        lmdbEnv.doWithReadTxn(readTxn -> {
            basicLmdbDb3.forEachEntry(readTxn, entry -> {
                final Integer monthNum = entry.getKey();
                final String shortForm = entry.getValue();

                LOGGER.info("monthNum: {}, shortForm: {}", monthNum, shortForm);

                basicLmdbDb2.forEachEntry(readTxn, KeyRange.atLeast(shortForm), entry2 -> {
                    final String shortForm2 = entry2.getKey();
                    final String longForm = entry2.getValue();
                    LOGGER.info("  shortForm2: {}, longForm: {}", shortForm2, longForm);
                });
            });
        });
    }

    /**
     * See the impact on disk space of doing writes with an open read txn
     */
    @Test
    void testPutsWithConcurrentReadTxn() throws ExecutionException, InterruptedException {
        try (final TemporaryPathCreator temporaryPathCreator = new TemporaryPathCreator()) {
            final BasicLmdbDb<String, String> basicLmdb1 = createEnvAndDb(
                    temporaryPathCreator,
                    new EnvFlags[]{EnvFlags.MDB_NOTLS},
                    false,
                    "1");
            final LmdbEnv lmdbEnv = basicLmdb1.getLmdbEnvironment();

            final AtomicInteger runNo = new AtomicInteger(1);

            final Consumer<Txn<ByteBuffer>> putConsumer = writeTxn -> {
                final int run = runNo.getAndIncrement();
//                LOGGER.info("Putting for run: {}", run);
                for (int i = 1; i <= 1_000; i++) {
                    basicLmdb1.put(
                            writeTxn,
                            Strings.padStart(Integer.toString(i), 6, '0'),
                            "val-"
                            + Strings.padStart(Integer.toString(i), 6, '0')
                            + "_run-"
                            + Strings.padStart(Integer.toString(run), 3, '0'),
                            true);

                }
                final Optional<String> optVal1 = basicLmdb1.get(writeTxn, "000001");
                assertThat(optVal1)
                        .isNotEmpty();
//                LOGGER.info("Val for key 1: {}", optVal1.orElse("[empty]"));
            };

            // Initial rounds of putting the same set of keys but with different values
            // each round.
            for (int i = 0; i < 5; i++) {
                lmdbEnv.doWithWriteTxn(putConsumer);
                LOGGER.info("Run: {}, DB size on disk: {}, entry count: {}",
                        runNo.get() - 1,  // already incremented
                        ByteSize.ofBytes(lmdbEnv.getSizeOnDisk()),
                        basicLmdb1.getEntryCount());
            }

            final CountDownLatch readStartLatch = new CountDownLatch(1);
            final CountDownLatch writeFinishLatch = new CountDownLatch(1);

            final CompletableFuture<Void> readFuture = CompletableFuture.runAsync(() -> {
                LOGGER.info("Read thread started");
                lmdbEnv.doWithReadTxn(ThrowingConsumer.unchecked(readTxn -> {
                    LOGGER.info("Read txn started");
                    readStartLatch.countDown();
                    LOGGER.info("readStartLatch counted down");

                    // Just hold the readTxn open while all the writes happen

                    awaitWithTimeout(writeFinishLatch, 10);
                }));
                LOGGER.info("Finished read thread");
            });

            // Wait for the read txn to be active
            awaitWithTimeout(readStartLatch, 10);

            final CompletableFuture<Void> writeFuture = CompletableFuture.runAsync(() -> {
                LOGGER.info("Write thread started");
                lmdbEnv.doWithWriteTxn(ThrowingConsumer.unchecked(writeTxn -> {
                    LOGGER.info("Write txn started");

                    for (int i = 0; i < 5; i++) {
                        // Initial puts with no read txn
                        putConsumer.accept(writeTxn);
                        LOGGER.info("Run: {}, DB size on disk: {}, entry count: {}",
                                runNo.get() - 1,  // already incremented
                                ByteSize.ofBytes(lmdbEnv.getSizeOnDisk()),
                                basicLmdb1.getEntryCount());
                    }

                    writeFinishLatch.countDown();
                }));
                LOGGER.info("Finished write thread");
            });

            readFuture.get();
            writeFuture.get();
        }
    }

    private BasicLmdbDb<String, String> createEnvAndDb(final TemporaryPathCreator temporaryPathCreator,
                                                       final EnvFlags[] envFlags,
                                                       final String id) {
        return createEnvAndDb(temporaryPathCreator, envFlags, true, id);

    }

    private BasicLmdbDb<String, String> createEnvAndDb(final TemporaryPathCreator temporaryPathCreator,
                                                       final EnvFlags[] envFlags,
                                                       final boolean isReadBlockedByWrite,
                                                       final String id) {
        final LmdbEnv lmdbEnv = new LmdbEnvFactory(
                temporaryPathCreator,
                new LmdbLibrary(temporaryPathCreator,
                        temporaryPathCreator.getTempDirProvider(),
                        LmdbLibraryConfig::new))
                .builder(temporaryPathCreator.getBaseTempDir())
                .withSubDirectory("env" + id)
                .withMapSize(getMaxSizeBytes())
                .withMaxDbCount(10)
                .withEnvFlags(envFlags)
                .setIsReaderBlockedByWriter(isReadBlockedByWrite)
                .build();

        return new BasicLmdbDb<>(
                lmdbEnv,
                new ByteBufferPoolFactory().getByteBufferPool(),
                new StringSerde(),
                new StringSerde(),
                "db" + id);
    }

    private void populateDb(final int count) {
        final Random random = new Random(123);
        final List<Entry<String, String>> entryList = IntStream.rangeClosed(1, count)
                .boxed()
                .map(i ->
                        Map.entry(buildKey(i), buildValue(i)))
                .collect(Collectors.toList());
        // Consistent seeded shuffle
        Collections.shuffle(entryList, random);
        // pad the keys to a fixed length, so they are stored in number order
        entryList.forEach(entry -> {
            basicLmdbDb.put(entry.getKey(), entry.getValue(), false);
        });

        basicLmdbDb.logDatabaseContents(LOGGER::info);
    }

    private String buildKey(final int i) {
        return String.format("%02d", i);
    }

    private String buildValue(final int i) {
        return "value" + i;
    }

    private void awaitWithTimeout(final CountDownLatch latch,
                                  final int timeoutSecs) {
        try {
            final boolean success = latch.await(timeoutSecs, TimeUnit.SECONDS);
            if (!success) {
                throw new RuntimeException("Timed out");
            }
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    // --------------------------------------------------------------------------------


    private record MultiKey(
            int int1,
            long long1,
            long long2,
            long unsignedLong,
            String str) {


    }


    // --------------------------------------------------------------------------------


    private static class MultiKeySerde implements Serde<MultiKey> {

        @Override
        public MultiKey deserialize(final ByteBuffer byteBuffer) {
            final MultiKey multiKey = new MultiKey(
                    byteBuffer.getInt(),
                    byteBuffer.getLong(),
                    byteBuffer.getLong(),
                    UNSIGNED_BYTES.get(byteBuffer),
                    StandardCharsets.UTF_8.decode(byteBuffer).toString());
            byteBuffer.rewind();
            return multiKey;
        }

        @Override
        public void serialize(final ByteBuffer byteBuffer, final MultiKey multiKey) {
            byteBuffer.putInt(multiKey.int1);
            byteBuffer.putLong(multiKey.long1);
            byteBuffer.putLong(multiKey.long2);
            UNSIGNED_BYTES.put(byteBuffer, multiKey.unsignedLong);
            byteBuffer.put(multiKey.str.getBytes(StandardCharsets.UTF_8));
            byteBuffer.flip();
        }

        public static void incrementLong2(final ByteBuffer byteBuffer) {
            byteBuffer.putLong(byteBuffer.getLong(12) + 1);
        }

        public static void incrementUnsignedLong(final ByteBuffer byteBuffer) {
            UNSIGNED_BYTES.increment(byteBuffer, 12);
        }
    }
}
