package stroom.pipeline.refdata.store.offheapstore.databases;


import stroom.pipeline.refdata.store.BasicValueStoreHashAlgorithmImpl;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.StringValue;
import stroom.pipeline.refdata.store.ValueStoreHashAlgorithm;
import stroom.pipeline.refdata.store.XxHashValueStoreHashAlgorithm;
import stroom.pipeline.refdata.store.offheapstore.ValueStoreKey;
import stroom.pipeline.refdata.store.offheapstore.lmdb.EntryConsumer;
import stroom.pipeline.refdata.store.offheapstore.lmdb.LmdbUtils;
import stroom.pipeline.refdata.store.offheapstore.serdes.GenericRefDataValueSerde;
import stroom.pipeline.refdata.store.offheapstore.serdes.RefDataValueSerdeFactory;
import stroom.pipeline.refdata.store.offheapstore.serdes.ValueStoreKeySerde;
import stroom.pipeline.refdata.util.ByteBufferPoolFactory;
import stroom.pipeline.refdata.util.PooledByteBuffer;
import stroom.pipeline.refdata.util.PooledByteBufferOutputStream;
import stroom.pipeline.refdata.util.PooledByteBufferOutputStream.Factory;

import io.vavr.Tuple;
import io.vavr.Tuple2;
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
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestValueStoreDb extends AbstractLmdbDbTest {
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


    @BeforeEach
    void setup() {
        // the default
        valueStoreDb = new ValueStoreDb(
                lmdbEnv,
                new ByteBufferPoolFactory().getByteBufferPool(),
                new ValueStoreKeySerde(),
                new GenericRefDataValueSerde(refDataValueSerdeFactory),
                xxHashAlgorithm,
                pooledByteBufferOutputStreamFactory);
    }

    private void setupValueStoreDb(final ValueStoreHashAlgorithm valueStoreHashAlgorithm) {
        valueStoreDb = new ValueStoreDb(
                lmdbEnv,
                new ByteBufferPoolFactory().getByteBufferPool(),
                new ValueStoreKeySerde(),
                new GenericRefDataValueSerde(refDataValueSerdeFactory),
                valueStoreHashAlgorithm,
                pooledByteBufferOutputStreamFactory);
    }


    private ValueStoreKey getOrCreate(Txn<ByteBuffer> writeTxn, RefDataValue refDataValue) {
        try (PooledByteBuffer valueStoreKeyPooledBuffer = valueStoreDb.getPooledKeyBuffer()) {
            ByteBuffer valueStoreKeyBuffer = valueStoreDb.getOrCreateKey(
                    writeTxn,
                    refDataValue,
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

        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            ValueStoreKey valueStoreKey;

            // insert the first five values, all have same hash so should get increasing
            // id values.
            for (int i = 0; i < 5; i++) {
                valueStoreKey = getOrCreate(writeTxn, refDataValues.get(i));
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
        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
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

        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
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
        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
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
            int size = strings.size();
            temp = new ArrayList<>(size * size);
            int count = 0;
            for (String s : strings) {
                for (String t : strings) {
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
        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            valueToKeyMap.put(val1str, getOrCreate(writeTxn, StringValue.of(val1str)));
            valueToKeyMap.put(val2str, getOrCreate(writeTxn, StringValue.of(val2str)));
            valueToKeyMap.put(val3str, getOrCreate(writeTxn, StringValue.of(val3str)));
        });

        assertThat(valueStoreDb.getEntryCount()).isEqualTo(3);

        ValueStoreKeySerde keySerde = new ValueStoreKeySerde();

        LmdbUtils.doWithReadTxn(lmdbEnv, txn -> {
            // lookup our three entries
            valueToKeyMap.forEach((valueStr, valueStoreKey) -> {
                valueStoreDb.getPooledKeyBuffer().doWithByteBuffer(keyBuffer -> {
                    keySerde.serialize(keyBuffer, valueStoreKey);
                    ByteBuffer valueBuffer = valueStoreDb.getAsBytes(txn, keyBuffer).get();
                    RefDataValue val = refDataValueSerdeFactory.deserialize(valueBuffer, StringValue.TYPE_ID);
                    assertThat(val).isInstanceOf(StringValue.class);
                    assertThat(((StringValue) val).getValue()).isEqualTo(valueStr);
                });
            });

            // now try and get a value that doesn't exist
            valueStoreDb.getPooledKeyBuffer().doWithByteBuffer(keyBuffer -> {
                keySerde.serialize(keyBuffer, new ValueStoreKey(123456, (short) 99));
                Optional<RefDataValue> optRefDataValue = valueStoreDb.get(
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

        String val1str = "AaAa";
        String val2str = "BBBB";
        String val3str = "AaBB";
        String val4str = "BBAa";

        assertThat(val1str.hashCode()).isEqualTo(val2str.hashCode());
        assertThat(val1str.hashCode()).isEqualTo(val3str.hashCode());
        assertThat(val1str.hashCode()).isEqualTo(val4str.hashCode());

        final Map<String, ValueStoreKey> valueToKeyMap = new HashMap<>();

        // load entries
        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
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

        ValueStoreKeySerde keySerde = new ValueStoreKeySerde();

        LmdbUtils.doWithReadTxn(lmdbEnv, txn -> {
            // lookup our entries
            List<Integer> ids = new ArrayList<>();
            valueStoreDb.getPooledKeyBuffer().doWithByteBuffer(keyBuffer -> {
                valueToKeyMap.forEach((valueStr, valueStoreKey) -> {
                    keySerde.serialize(keyBuffer, valueStoreKey);
                    ByteBuffer valueBuffer = valueStoreDb.getAsBytes(txn, keyBuffer).get();
                    RefDataValue val = refDataValueSerdeFactory.deserialize(valueBuffer, StringValue.TYPE_ID);
                    assertThat(val).isInstanceOf(StringValue.class);
                    assertThat(((StringValue) val).getValue()).isEqualTo(valueStr);
                });
            });
        });
    }

    @Test
    void testAreValueEqual_twoEqualValues() {
        doAreValuesEqualAssert(StringValue.of("value1"), StringValue.of("value1"), true);
    }

    @Test
    void testAreValueEqual_twoDifferentValues() {
        doAreValuesEqualAssert(StringValue.of("value1"), StringValue.of("value2"), false);
    }

    @Test
    void testAreValueEqual_notFound() {
        StringValue value2 = StringValue.of("value2");
        ValueStoreKey unknownValueStoreKey = new ValueStoreKey(123, (short) 0);
        ByteBuffer unknownValueStoreKeyBuffer = valueStoreDb.getPooledKeyBuffer().getByteBuffer();
        valueStoreDb.serializeKey(unknownValueStoreKeyBuffer, unknownValueStoreKey);

        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            boolean areValuesEqual = valueStoreDb.areValuesEqual(writeTxn, unknownValueStoreKeyBuffer, value2);
            assertThat(areValuesEqual).isFalse();
        });
    }

    @Test
    void testKeyOrder() {

        // Ensure entries come back in the right order
        long hash = 123456789;
        final List<Tuple2<ValueStoreKey, RefDataValue>> data = List.of(
                Tuple.of(new ValueStoreKey(hash, (short) 0), new StringValue("val1")),
                Tuple.of(new ValueStoreKey(hash, (short) 1), new StringValue("val2")),
                Tuple.of(new ValueStoreKey(hash, (short) 2), new StringValue("val3")),
                Tuple.of(new ValueStoreKey(hash, (short) 3), new StringValue("val4")));

        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            data.forEach(tuple -> {
                valueStoreDb.put(writeTxn, tuple._1(), tuple._2(), false);
            });
        });

        final KeyRange<ValueStoreKey> keyRangeAll = KeyRange.all();

        final List<ValueStoreKey> output = LmdbUtils.getWithReadTxn(lmdbEnv, readTxn ->
                valueStoreDb.streamEntries(readTxn, keyRangeAll, stream ->
                        stream
                                .map(Tuple2::_1)
                                .collect(Collectors.toList())));

        Assertions.assertThat(output)
                .containsExactlyElementsOf(data.stream()
                        .map(Tuple2::_1)
                        .collect(Collectors.toList()));
    }

    private void doAreValuesEqualAssert(final StringValue value1, final StringValue value2, final boolean expectedResult) {
        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            ValueStoreKey valueStoreKey1 = getOrCreate(writeTxn, value1);

            ByteBuffer valueStoreKeyBuffer = valueStoreDb.getPooledKeyBuffer().getByteBuffer();
            valueStoreDb.serializeKey(valueStoreKeyBuffer, valueStoreKey1);

            boolean areValuesEqual = valueStoreDb.areValuesEqual(writeTxn, valueStoreKeyBuffer, value2);
            assertThat(areValuesEqual).isEqualTo(expectedResult);
        });
    }
}