package stroom.pipeline.refdata.store.offheapstore.databases;


import stroom.pipeline.refdata.store.BasicValueStoreHashAlgorithmImpl;
import stroom.pipeline.refdata.store.ByteBufferPoolFactory;
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
import stroom.pipeline.refdata.util.PooledByteBuffer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
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
    private ValueStoreDb valueStoreDb = null;

    private static final int NCHAR = 5;
    private static final char[] chars = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
            'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N',
            'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
    };
    private static final int ALPHAS = chars.length;

    @BeforeEach
    void setup() {
        // the default
        valueStoreDb = new ValueStoreDb(
                lmdbEnv,
                new ByteBufferPoolFactory().getByteBufferPool(),
                new ValueStoreKeySerde(),
                new GenericRefDataValueSerde(refDataValueSerdeFactory),
                xxHashAlgorithm);
    }

    private void setupValueStoreDb(final ValueStoreHashAlgorithm valueStoreHashAlgorithm) {
        valueStoreDb = new ValueStoreDb(
                lmdbEnv,
                new ByteBufferPoolFactory().getByteBufferPool(),
                new ValueStoreKeySerde(),
                new GenericRefDataValueSerde(refDataValueSerdeFactory),
                valueStoreHashAlgorithm);
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
                ByteBuffer valueBuffer = valueStoreDb.getAsBytes(txn, keySerde.serialize(valueStoreKey)).get();
                RefDataValue val = refDataValueSerdeFactory.deserialize(valueBuffer, StringValue.TYPE_ID);
                assertThat(val).isInstanceOf(StringValue.class);
                assertThat(((StringValue) val).getValue()).isEqualTo(valueStr);
            });

            // now try and get a value that doesn't exist
            Optional<RefDataValue> optRefDataValue = valueStoreDb.get(
                    txn,
                    keySerde.serialize(new ValueStoreKey(123456, (short) 99)),
                    StringValue.TYPE_ID);

            assertThat(optRefDataValue).isEmpty();
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
            valueToKeyMap.forEach((valueStr, valueStoreKey) -> {
                ByteBuffer valueBuffer = valueStoreDb.getAsBytes(txn, keySerde.serialize(valueStoreKey)).get();
                RefDataValue val = refDataValueSerdeFactory.deserialize(valueBuffer, StringValue.TYPE_ID);
                assertThat(val).isInstanceOf(StringValue.class);
                assertThat(((StringValue) val).getValue()).isEqualTo(valueStr);
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

    private void doAreValuesEqualAssert(final StringValue value1, final StringValue value2, final boolean expectedResult) {
        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            ValueStoreKey valueStoreKey1 = getOrCreate(writeTxn, value1);

            ByteBuffer valueStoreKeyBuffer = valueStoreDb.getPooledKeyBuffer().getByteBuffer();
            valueStoreDb.serializeKey(valueStoreKeyBuffer, valueStoreKey1);

            boolean areValuesEqual = valueStoreDb.areValuesEqual(writeTxn, valueStoreKeyBuffer, value2);
            assertThat(areValuesEqual).isEqualTo(expectedResult);
        });
    }

    @Disabled
    @Test
    void findHashClashes() {
        Map<Long, String> map = new HashMap<>();
        int[] index = new int[NCHAR];
        char[] buf = new char[NCHAR];
        while (true) {
            for (int i = 0; i < NCHAR; ++i) {
                buf[i] = chars[index[i]];
            }
            String str = new String(buf);
            long hash = basicHashAlgorithm.hash(str);
            String dupStr = map.putIfAbsent(hash, str);
            if (dupStr != null) {
                System.out.println("clash " + str + " " + dupStr);
            }
            int carry = 1;
            for (int i = 0; i < NCHAR; ++i) {
                index[i] = index[i] + carry;
                carry = index[i] / ALPHAS;
                index[i] %= ALPHAS;
            }
            if (carry > 0) break;
        }

//        for (Map.Entry<Long,Collection<String>> group : map.entrySet()) {
//            Collection<String> strings = group.getValue();
//            if (strings.size() >= 2) {
//                System.out.println("" + group.getKey() + ":");
//                for (String str: strings) {
//                    System.out.println("\t" + str);
//                }
//            }
//        }
    }
}