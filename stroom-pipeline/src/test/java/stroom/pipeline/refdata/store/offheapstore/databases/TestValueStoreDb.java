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
import stroom.bytebuffer.PooledByteBufferOutputStream;
import stroom.bytebuffer.PooledByteBufferOutputStream.Factory;
import stroom.lmdb.EntryConsumer;
import stroom.pipeline.refdata.store.BasicValueStoreHashAlgorithmImpl;
import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.StagingValue;
import stroom.pipeline.refdata.store.StringValue;
import stroom.pipeline.refdata.store.ValueStoreHashAlgorithm;
import stroom.pipeline.refdata.store.XxHashValueStoreHashAlgorithm;
import stroom.pipeline.refdata.store.offheapstore.ValueStoreKey;
import stroom.pipeline.refdata.store.offheapstore.serdes.GenericRefDataValueSerde;
import stroom.pipeline.refdata.store.offheapstore.serdes.RefDataValueSerdeFactory;
import stroom.pipeline.refdata.store.offheapstore.serdes.StagingValueSerde;
import stroom.pipeline.refdata.store.offheapstore.serdes.ValueStoreKeySerde;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestValueStoreDb extends AbstractStoreDbTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestValueStoreDb.class);

    private final RefDataValueSerdeFactory refDataValueSerdeFactory = new RefDataValueSerdeFactory();
    private final ValueStoreHashAlgorithm xxHashAlgorithm = new XxHashValueStoreHashAlgorithm();
    private final ValueStoreHashAlgorithm basicHashAlgorithm = new BasicValueStoreHashAlgorithmImpl();
    private final ByteBufferPoolFactory byteBufferPoolFactory = new ByteBufferPoolFactory();
    private final PooledByteBufferOutputStream.Factory pooledByteBufferOutputStreamFactory = new Factory() {
        @Override
        public PooledByteBufferOutputStream create(final int initialCapacity) {
            return new PooledByteBufferOutputStream(byteBufferPoolFactory.getByteBufferPool(), initialCapacity);
        }
    };
    private ValueStoreDb valueStoreDb = null;
    private ValueStoreHashAlgorithm currValueStoreHashAlgorithm;


    @BeforeEach
    void setup() {
        // the default
        valueStoreDb = new ValueStoreDb(
                refDataLmdbEnv,
                new ByteBufferPoolFactory().getByteBufferPool(),
                new ValueStoreKeySerde(),
                new GenericRefDataValueSerde(refDataValueSerdeFactory),
                xxHashAlgorithm,
                pooledByteBufferOutputStreamFactory);
        currValueStoreHashAlgorithm = xxHashAlgorithm;
    }

    private void setupValueStoreDb(final ValueStoreHashAlgorithm valueStoreHashAlgorithm) {
        valueStoreDb = new ValueStoreDb(
                refDataLmdbEnv,
                new ByteBufferPoolFactory().getByteBufferPool(),
                new ValueStoreKeySerde(),
                new GenericRefDataValueSerde(refDataValueSerdeFactory),
                valueStoreHashAlgorithm,
                pooledByteBufferOutputStreamFactory);
        currValueStoreHashAlgorithm = valueStoreHashAlgorithm;
    }

    ValueStoreHashAlgorithm getCurrValueStoreHashAlgorithm() {
        return currValueStoreHashAlgorithm;
    }

    private ValueStoreKey getOrCreate(final Txn<ByteBuffer> writeTxn,
                                      final RefDataValue refDataValue) {
        try (final PooledByteBuffer valueStoreKeyPooledBuffer = valueStoreDb.getPooledKeyBuffer()) {
            final StagingValue stagingValue = StagingValueSerde.convert(
                    ByteBuffer::allocateDirect,
                    getCurrValueStoreHashAlgorithm(),
                    refDataValue);
            final ByteBuffer valueStoreKeyBuffer = valueStoreDb.getOrCreateKey(
                    writeTxn,
                    stagingValue,
                    valueStoreKeyPooledBuffer,
                    EntryConsumer.doNothingConsumer(),
                    EntryConsumer.doNothingConsumer());

            return valueStoreDb.deserializeKey(valueStoreKeyBuffer);
        }
    }

    @Test
    void testGetOrCreateSparseIds() {
        // We have to set up the DB with the basic hash func so we can be assured of hash clashes
        setupValueStoreDb(basicHashAlgorithm);

        final List<String> stringsWithSameHash = generateHashClashes(
                10, valueStoreDb.getValueStoreHashAlgorithm());

        final List<RefDataValue> refDataValues = stringsWithSameHash.stream()
                .map(StringValue::of)
                .collect(Collectors.toList());

        final Map<Integer, ValueStoreKey> valueStoreKeysMap = new HashMap<>();

        lmdbEnv.doWithWriteTxn(writeTxn -> {
            ValueStoreKey valueStoreKey;

            // insert the first five values, all have same hash so should get increasing
            // id values.
            for (int i = 0; i < 5; i++) {
                valueStoreKey = getOrCreate(writeTxn, refDataValues.get(i));
                LOGGER.debug("i: {}, valueStoreKey: {}", i, valueStoreKey);
                valueStoreKeysMap.put(i, valueStoreKey);
                assertThat(valueStoreKey.getUniqueId()).isEqualTo((short) i);
            }
        });
        assertThat(valueStoreDb.getEntryCount()).isEqualTo(5);
        valueStoreDb.logDatabaseContents();

        // delete values 1,2,3, leaving ids 0 and 5
        for (int i = 1; i < 4; i++) {
            valueStoreDb.delete(valueStoreKeysMap.get(i));
        }
        assertThat(valueStoreDb.getEntryCount()).isEqualTo(2);
        valueStoreDb.logDatabaseContents();

        // now put the remaining 5 values
        // they should fill up the gaps in the ID sequence
        lmdbEnv.doWithWriteTxn(writeTxn -> {
            ValueStoreKey valueStoreKey;
            for (int i = 5; i < 10; i++) {
                valueStoreKey = getOrCreate(writeTxn, refDataValues.get(i));
                valueStoreKeysMap.put(i, valueStoreKey);
            }
        });
        assertThat(valueStoreDb.getEntryCount()).isEqualTo(5 - 3 + 5);
        valueStoreDb.logDatabaseContents();

        // check the ID value for each of our original values in insertion order
        assertThat(valueStoreKeysMap.get(0).getUniqueId()).isEqualTo((short) 0);
        assertThat(valueStoreKeysMap.get(1).getUniqueId()).isEqualTo((short) 1); //since deleted
        assertThat(valueStoreKeysMap.get(2).getUniqueId()).isEqualTo((short) 2); //since deleted
        assertThat(valueStoreKeysMap.get(3).getUniqueId()).isEqualTo((short) 3); //since deleted
        assertThat(valueStoreKeysMap.get(4).getUniqueId()).isEqualTo((short) 4);
        assertThat(valueStoreKeysMap.get(5).getUniqueId()).isEqualTo((short) 1); //used empty space after delete
        assertThat(valueStoreKeysMap.get(6).getUniqueId()).isEqualTo((short) 2); //used empty space after delete
        assertThat(valueStoreKeysMap.get(7).getUniqueId()).isEqualTo((short) 3); //used empty space after delete
        assertThat(valueStoreKeysMap.get(8).getUniqueId()).isEqualTo((short) 5);
        assertThat(valueStoreKeysMap.get(9).getUniqueId()).isEqualTo((short) 6);
    }

    @Test
    void testGetOrCreateSparseIds2() {
        // We have to set up the DB with the basic hash func so we can be assured of hash clashes
        setupValueStoreDb(basicHashAlgorithm);

        final List<String> stringsWithSameHash = generateHashClashes(
                10, valueStoreDb.getValueStoreHashAlgorithm());

        final List<RefDataValue> refDataValues = stringsWithSameHash.stream()
                .map(StringValue::of)
                .collect(Collectors.toList());

        final Map<Integer, ValueStoreKey> valueStoreKeysMap = new HashMap<>();

        lmdbEnv.doWithWriteTxn(writeTxn -> {
            ValueStoreKey valueStoreKey;

            // insert the first five values, all have same hash so should get increasing
            // id values.
            for (int i = 0; i < 5; i++) {
                valueStoreKey = getOrCreate(writeTxn, refDataValues.get(i));
                valueStoreKeysMap.put(i, valueStoreKey);
                LOGGER.info("Assigned id: {}", valueStoreKey.getUniqueId());
                assertThat(valueStoreKey.getUniqueId()).isEqualTo((short) i);
            }
        });
        assertThat(valueStoreDb.getEntryCount()).isEqualTo(5);
        valueStoreDb.logDatabaseContents();

        // delete values 0,1,2 leaving ids 3,4
        for (int i = 0; i < 3; i++) {
            valueStoreDb.delete(valueStoreKeysMap.get(i));
        }
        assertThat(valueStoreDb.getEntryCount()).isEqualTo(2);
        valueStoreDb.logDatabaseContents();

        // now put the remaining 5 values
        // they should fill up the gaps in the ID sequence
        lmdbEnv.doWithWriteTxn(writeTxn -> {
            ValueStoreKey valueStoreKey;
            for (int i = 5; i < 10; i++) {
                valueStoreKey = getOrCreate(writeTxn, refDataValues.get(i));
                valueStoreKeysMap.put(i, valueStoreKey);
                LOGGER.info("Assigned id: {}", valueStoreKey.getUniqueId());
            }
        });
        assertThat(valueStoreDb.getEntryCount()).isEqualTo(5 - 3 + 5);
        valueStoreDb.logDatabaseContents();

        // check the ID value for each of our original values in insertion order
        assertThat(valueStoreKeysMap.get(0).getUniqueId()).isEqualTo((short) 0); //since deleted
        assertThat(valueStoreKeysMap.get(1).getUniqueId()).isEqualTo((short) 1); //since deleted
        assertThat(valueStoreKeysMap.get(2).getUniqueId()).isEqualTo((short) 2); //since deleted
        assertThat(valueStoreKeysMap.get(3).getUniqueId()).isEqualTo((short) 3);
        assertThat(valueStoreKeysMap.get(4).getUniqueId()).isEqualTo((short) 4);
        assertThat(valueStoreKeysMap.get(5).getUniqueId()).isEqualTo((short) 0); //used empty space after delete
        assertThat(valueStoreKeysMap.get(6).getUniqueId()).isEqualTo((short) 1); //used empty space after delete
        assertThat(valueStoreKeysMap.get(7).getUniqueId()).isEqualTo((short) 2); //used empty space after delete
        assertThat(valueStoreKeysMap.get(8).getUniqueId()).isEqualTo((short) 5);
        assertThat(valueStoreKeysMap.get(9).getUniqueId()).isEqualTo((short) 6);
    }

    /**
     * Generate a list (of size desiredRecords) of strings that all share the same hashcode
     * Adapted from https://gist.github.com/vaskoz/5703423
     */
    public List<String> generateHashClashes(final int desiredRecords,
                                            final ValueStoreHashAlgorithm valueStoreHashAlgorithm) {

        List<String> strings = new ArrayList<>(Arrays.asList("Aa", "BB"));
        List<String> temp = new ArrayList<>();

        complete:
        for (int i = 0; i < 5; i++) {
            final int size = strings.size();
            temp = new ArrayList<>(size * size);
            int count = 0;
            for (final String s : strings) {
                for (final String t : strings) {
                    if (count == desiredRecords) {
                        break complete;
                    }
                    temp.add(s + t);
                    count++;
                }
            }
            strings = temp;
        }
        strings = temp;

        //dbl check all have same hash
        assertThat(strings.stream()
                .peek(str -> {
                    LOGGER.info("{} {} {}", str, str.hashCode(), valueStoreHashAlgorithm.hash(str));
                })
                .map(valueStoreHashAlgorithm::hash)
                .distinct()
                .count()).isEqualTo(1);

        return strings;
    }


    @Test
    void testGet_simple() {
        assertThat(valueStoreDb.getEntryCount()).isEqualTo(0);

        final String val1str = "value one";
        final String val2str = "value two";
        final String val3str = "value three";

        final Map<String, ValueStoreKey> valueToKeyMap = new HashMap<>();

        // load three entries
        lmdbEnv.doWithWriteTxn(writeTxn -> {
            valueToKeyMap.put(val1str, getOrCreate(writeTxn, StringValue.of(val1str)));
            valueToKeyMap.put(val2str, getOrCreate(writeTxn, StringValue.of(val2str)));
            valueToKeyMap.put(val3str, getOrCreate(writeTxn, StringValue.of(val3str)));
        });

        assertThat(valueStoreDb.getEntryCount()).isEqualTo(3);

        final ValueStoreKeySerde keySerde = new ValueStoreKeySerde();

        lmdbEnv.doWithReadTxn(txn -> {
            // lookup our three entries
            valueToKeyMap.forEach((valueStr, valueStoreKey) -> {
                valueStoreDb.getPooledKeyBuffer().doWithByteBuffer(keyBuffer -> {
                    keySerde.serialize(keyBuffer, valueStoreKey);
                    final ByteBuffer valueBuffer = valueStoreDb.getAsBytes(txn, keyBuffer).get();
                    final RefDataValue val = refDataValueSerdeFactory.deserialize(valueBuffer, StringValue.TYPE_ID);
                    assertThat(val).isInstanceOf(StringValue.class);
                    assertThat(((StringValue) val).getValue()).isEqualTo(valueStr);
                });
            });

            // now try and get a value that doesn't exist
            valueStoreDb.getPooledKeyBuffer().doWithByteBuffer(keyBuffer -> {
                keySerde.serialize(keyBuffer, new ValueStoreKey(123456, (short) 99));
                final Optional<RefDataValue> optRefDataValue = valueStoreDb.get(
                        txn,
                        keyBuffer,
                        StringValue.TYPE_ID);
                assertThat(optRefDataValue).isEmpty();
            });

        });
    }

    @Test
    void testGet_sameHashCodes() {
        // We have to set up the DB with the basic hash func so we can be assured of hash clashes
        setupValueStoreDb(basicHashAlgorithm);

        assertThat(valueStoreDb.getEntryCount()).isEqualTo(0);

        final String val1str = "AaAa";
        final String val2str = "BBBB";
        final String val3str = "AaBB";
        final String val4str = "BBAa";

        assertThat(val1str.hashCode()).isEqualTo(val2str.hashCode());
        assertThat(val1str.hashCode()).isEqualTo(val3str.hashCode());
        assertThat(val1str.hashCode()).isEqualTo(val4str.hashCode());

        final Map<String, ValueStoreKey> valueToKeyMap = new HashMap<>();

        // load entries
        lmdbEnv.doWithWriteTxn(writeTxn -> {
            valueToKeyMap.put(val1str, getOrCreate(writeTxn, StringValue.of(val1str)));
            valueToKeyMap.put(val2str, getOrCreate(writeTxn, StringValue.of(val2str)));
            valueToKeyMap.put(val3str, getOrCreate(writeTxn, StringValue.of(val3str)));
            valueToKeyMap.put(val4str, getOrCreate(writeTxn, StringValue.of(val4str)));
        });

        // should have four different id values as all have the same hashcode
        assertThat(
                valueToKeyMap.values().stream()
                        .map(ValueStoreKey::getUniqueId)
                        .collect(Collectors.toList()))
                .contains((short) 0, (short) 1, (short) 2, (short) 3);

        assertThat(valueStoreDb.getEntryCount()).isEqualTo(valueToKeyMap.size());

        final ValueStoreKeySerde keySerde = new ValueStoreKeySerde();

        lmdbEnv.doWithReadTxn(txn -> {
            // lookup our entries
            final List<Integer> ids = new ArrayList<>();
            valueStoreDb.getPooledKeyBuffer().doWithByteBuffer(keyBuffer -> {
                valueToKeyMap.forEach((valueStr, valueStoreKey) -> {
                    keySerde.serialize(keyBuffer, valueStoreKey);
                    final ByteBuffer valueBuffer = valueStoreDb.getAsBytes(txn, keyBuffer).get();
                    final RefDataValue val = refDataValueSerdeFactory.deserialize(valueBuffer, StringValue.TYPE_ID);
                    assertThat(val).isInstanceOf(StringValue.class);
                    assertThat(((StringValue) val).getValue()).isEqualTo(valueStr);
                });
            });
        });
    }

    @Test
    void testAreValueEqual_twoEqualStringValues() {
        doAreValuesEqualAssert(StringValue.of("value1"), StringValue.of("value1"), true);
    }

    @Test
    void testAreValueEqual_twoEqualFastInfosetValues_1() {
        final ByteBuffer valBuff1 = ByteBuffer.allocateDirect(20);
        valBuff1.put("value".getBytes());
        valBuff1.flip();
        final ByteBuffer valBuff2 = ByteBuffer.allocateDirect(20);
        valBuff2.put("value".getBytes());
        valBuff2.flip();
        final FastInfosetValue val1 = FastInfosetValue.wrap(valBuff1);
        final FastInfosetValue val2 = FastInfosetValue.wrap(valBuff2);
        doAreValuesEqualAssert(
                val1,
                val2,
                true);
    }

    @Test
    void testAreValueEqual_twoEqualFastInfosetValues_2() {
        // Same value in both buffers but different size buffers and different positions
        final ByteBuffer valBuff1 = ByteBuffer.allocateDirect(30);
        valBuff1.put(5, "value".getBytes());
        valBuff1.position(5);
        valBuff1.limit(5 + "value".length());
        LOGGER.info("valBuff1: {}", ByteBufferUtils.byteBufferInfo(valBuff1));

        final ByteBuffer valBuff2 = ByteBuffer.allocateDirect(20);
        valBuff2.put("value".getBytes());
        valBuff2.flip();
        LOGGER.info("valBuff2: {}", ByteBufferUtils.byteBufferInfo(valBuff2));

        final FastInfosetValue val1 = FastInfosetValue.wrap(valBuff1);
        final FastInfosetValue val2 = FastInfosetValue.wrap(valBuff2);
        doAreValuesEqualAssert(
                val1,
                val2,
                true);
    }

    @Test
    void testAreValueEqual_twoDifferentStringValuesWithHashCollision() {
        setupValueStoreDb(new ConstantHashHashAlgorithm());
        doAreValuesEqualAssert(
                StringValue.of("value1"),
                StringValue.of("value2"),
                false);
    }

    @Test
    void testAreValueEqual_twoDifferentFastInfosetValues() {
        final ByteBuffer valBuff1 = ByteBuffer.allocateDirect(20);
        valBuff1.put("value1".getBytes());
        valBuff1.flip();
        final ByteBuffer valBuff2 = ByteBuffer.allocateDirect(20);
        valBuff2.put("value2".getBytes());
        valBuff2.flip();
        final FastInfosetValue val1 = FastInfosetValue.wrap(valBuff1);
        final FastInfosetValue val2 = FastInfosetValue.wrap(valBuff2);
        doAreValuesEqualAssert(
                val1,
                val2,
                false);
    }

    @Test
    void testAreValueEqual_twoDifferentFastInfosetValuesWithHashCollision() {
        setupValueStoreDb(new ConstantHashHashAlgorithm());

        // Hash collision so has to do equality on value
        final ByteBuffer valBuff1 = ByteBuffer.allocateDirect(20);
        valBuff1.put("value1".getBytes());
        valBuff1.flip();
        final ByteBuffer valBuff2 = ByteBuffer.allocateDirect(20);
        valBuff2.put("value2".getBytes());
        valBuff2.flip();
        final FastInfosetValue val1 = FastInfosetValue.wrap(valBuff1);
        final FastInfosetValue val2 = FastInfosetValue.wrap(valBuff2);
        doAreValuesEqualAssert(
                val1,
                val2,
                false);
    }

    @Test
    void testAreValueEqual_notFound() {
        final StringValue value2 = StringValue.of("value2");
        final ValueStoreKey unknownValueStoreKey = new ValueStoreKey(123, (short) 0);
        final ByteBuffer unknownValueStoreKeyBuffer = valueStoreDb.getPooledKeyBuffer().getByteBuffer();
        valueStoreDb.serializeKey(unknownValueStoreKeyBuffer, unknownValueStoreKey);

        final StagingValue stagingValue = convert(value2);
        lmdbEnv.doWithWriteTxn(writeTxn -> {
            final boolean areValuesEqual = valueStoreDb.areValuesEqual(
                    writeTxn, unknownValueStoreKeyBuffer, stagingValue);
            assertThat(areValuesEqual).isFalse();
        });
    }

    @Test
    void testKeyOrder() {

        // Ensure entries come back in the right order
        final long hash = 123456789;
        final List<Entry<ValueStoreKey, RefDataValue>> data = IntStream.rangeClosed(0, 1001)
                .boxed()
                .map(i -> {
                    return Map.entry(
                            new ValueStoreKey(hash, Short.parseShort(Integer.toString(i))),
                            (RefDataValue) new StringValue("val" + i));

                })
                .toList();

        lmdbEnv.doWithWriteTxn(writeTxn -> {
            data.forEach(entry -> {
                valueStoreDb.put(writeTxn, entry.getKey(), entry.getValue(), false);
            });
        });

        final KeyRange<ValueStoreKey> keyRangeAll = KeyRange.all();

        final List<ValueStoreKey> output = lmdbEnv.getWithReadTxn(readTxn ->
                valueStoreDb.streamEntries(readTxn, keyRangeAll, stream ->
                        stream
                                .map(Entry::getKey)
                                .collect(Collectors.toList())));

        Assertions.assertThat(output)
                .containsExactlyElementsOf(data.stream()
                        .map(Entry::getKey)
                        .collect(Collectors.toList()));
    }

    private void doAreValuesEqualAssert(final RefDataValue value1,
                                        final RefDataValue value2,
                                        final boolean expectedResult) {
        lmdbEnv.doWithWriteTxn(writeTxn -> {
            final ValueStoreKey valueStoreKey1 = getOrCreate(writeTxn, value1);

            final ByteBuffer valueStoreKeyBuffer = valueStoreDb.getPooledKeyBuffer().getByteBuffer();
            valueStoreDb.serializeKey(valueStoreKeyBuffer, valueStoreKey1);

            final StagingValue stagingValue = convert(value2);

            final boolean areValuesEqual = valueStoreDb.areValuesEqual(writeTxn, valueStoreKeyBuffer, stagingValue);
            assertThat(areValuesEqual).isEqualTo(expectedResult);
        });
    }

    private StagingValue convert(final RefDataValue refDataValue) {
        return StagingValueSerde.convert(ByteBuffer::allocateDirect, xxHashAlgorithm, refDataValue);
    }


    // --------------------------------------------------------------------------------


    private static class ConstantHashHashAlgorithm implements ValueStoreHashAlgorithm {

        @Override
        public long hash(final ByteBuffer byteBuffer) {
            // Always return same hash
            return 0;
        }

        @Override
        public long hash(final String value) {
            // Always return same hash
            return 0;
        }
    }
}
