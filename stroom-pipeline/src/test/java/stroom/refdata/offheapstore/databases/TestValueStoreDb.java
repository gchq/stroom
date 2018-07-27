package stroom.refdata.offheapstore.databases;

import org.junit.Before;
import org.junit.Test;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.lmdb.LmdbUtils;
import stroom.refdata.offheapstore.ByteBufferPool;
import stroom.refdata.offheapstore.RefDataValue;
import stroom.refdata.offheapstore.StringValue;
import stroom.refdata.offheapstore.ValueStoreKey;
import stroom.refdata.offheapstore.serdes.RefDataValueSerde;
import stroom.refdata.offheapstore.serdes.RefDataValueSerdeFactory;
import stroom.refdata.offheapstore.serdes.ValueStoreKeySerde;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;


public class TestValueStoreDb extends AbstractLmdbDbTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestValueStoreDb.class);

    private ValueStoreDb valueStoreDb = null;

    //TODO should really spin up guice rather than use this factory class
    private final RefDataValueSerde refDataValueSerde = RefDataValueSerdeFactory.create();

    @Before
    @Override
    public void setup() {
        super.setup();

        valueStoreDb = new ValueStoreDb(
                lmdbEnv,
                new ByteBufferPool(),
                new ValueStoreKeySerde(),
                refDataValueSerde);
    }


    private ValueStoreKey getOrCreate(Txn<ByteBuffer> writeTxn, RefDataValue refDataValue) {
        final Supplier<ByteBuffer> valueStoreKeyBufferSupplier = () ->
                valueStoreDb.getPooledKeyBuffer().getByteBuffer();

        ByteBuffer valueStoreKeyBuffer = valueStoreDb.getOrCreate(
                writeTxn, refDataValue, valueStoreKeyBufferSupplier);

        return valueStoreDb.deserializeKey(valueStoreKeyBuffer);
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
            stringValue = (StringValue) valueStoreDb.get(writeTxn, valueStoreKey1a).get();
            assertThat(stringValue.getReferenceCount()).isEqualTo(1);
            assertThat(stringValue.getValue()).isEqualTo(value1.getValue());

            // getOrCreate same value, should no new records, but ref count will have increased
            ValueStoreKey valueStoreKey1b = getOrCreate(writeTxn, value1);
            assertThat(valueStoreDb.getEntryCount(writeTxn)).isEqualTo(1);
            assertThat(valueStoreKey1b).isEqualTo(valueStoreKey1a);
            stringValue = (StringValue) valueStoreDb.get(writeTxn, valueStoreKey1b).get();
            assertThat(stringValue.getReferenceCount()).isEqualTo(2);
            assertThat(stringValue.getValue()).isEqualTo(value1.getValue());

            // getOrCreate same value, should no new records, but ref count will have increased
            ValueStoreKey valueStoreKey1c = getOrCreate(writeTxn, value1);
            assertThat(valueStoreDb.getEntryCount(writeTxn)).isEqualTo(1);
            assertThat(valueStoreKey1b).isEqualTo(valueStoreKey1a);
            stringValue = (StringValue) valueStoreDb.get(writeTxn, valueStoreKey1c).get();
            assertThat(stringValue.getReferenceCount()).isEqualTo(3);
            assertThat(stringValue.getValue()).isEqualTo(value1.getValue());

            // getOrCreate a different value, so 1 new entry, ref count is 1
            ValueStoreKey valueStoreKey2a = getOrCreate(writeTxn, value2);
            assertThat(valueStoreDb.getEntryCount(writeTxn)).isEqualTo(2);
            stringValue = (StringValue) valueStoreDb.get(writeTxn, valueStoreKey2a).get();
            assertThat(stringValue.getReferenceCount()).isEqualTo(1);
            assertThat(stringValue.getValue()).isEqualTo(value2.getValue());

            valueStoreDb.logRawDatabaseContents();
            valueStoreDb.logDatabaseContents();

            LOGGER.info("-----------------------------------------------------------------");

            // now dereference value1
            valueStoreDb.deReferenceOrDeleteValue(writeTxn, valueStoreKey1a);
            stringValue = (StringValue) valueStoreDb.get(writeTxn, valueStoreKey1a).get();
            assertThat(stringValue.getReferenceCount()).isEqualTo(2);
            assertThat(valueStoreDb.getEntryCount(writeTxn)).isEqualTo(2);

            // now dereference value1 again
            valueStoreDb.deReferenceOrDeleteValue(writeTxn, valueStoreKey1a);
            stringValue = (StringValue) valueStoreDb.get(writeTxn, valueStoreKey1a).get();
            assertThat(stringValue.getReferenceCount()).isEqualTo(1);
            assertThat(valueStoreDb.getEntryCount(writeTxn)).isEqualTo(2);

            // now dereference value1 again, entry is deleted
            valueStoreDb.deReferenceOrDeleteValue(writeTxn, valueStoreKey1a);
            assertThat(valueStoreDb.get(writeTxn, valueStoreKey1a)).isEmpty();
            assertThat(valueStoreDb.getEntryCount(writeTxn)).isEqualTo(1);

            // now dereference value2, entry is deleted
            valueStoreDb.deReferenceOrDeleteValue(writeTxn, valueStoreKey2a);
            assertThat(valueStoreDb.get(writeTxn, valueStoreKey2a)).isEmpty();
            assertThat(valueStoreDb.getEntryCount(writeTxn)).isEqualTo(0);

        });

        valueStoreDb.logRawDatabaseContents();
        valueStoreDb.logDatabaseContents();
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

        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            ValueStoreKey valueStoreKey = getOrCreate(writeTxn, StringValue.of(stringValueStr1));

            assertThat(valueStoreKey).isNotNull();
            assertThat(valueStoreKey.getUniqueId()).isEqualTo((short) 0);
            assertRefCount(writeTxn, valueStoreKey, 1);
        });
        assertThat(valueStoreDb.getEntryCount()).isEqualTo(1);

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
        assertThat(valueStoreDb.getEntryCount()).isEqualTo(1);

        LOGGER.debug("----------------------------");

        // now put a different value with same hashcode. Entry count should increase and the
        // returned valueStoreKey should have an id of 1 as it has same hashcode as last one
        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            ValueStoreKey valueStoreKey = getOrCreate(writeTxn, StringValue.of(stringValueStr2));

            assertThat(valueStoreKey).isNotNull();
            assertThat(valueStoreKey.getUniqueId()).isEqualTo((short) 1);
            assertRefCount(writeTxn, valueStoreKey, 1);
        });
        assertThat(valueStoreDb.getEntryCount()).isEqualTo(2);

        LOGGER.debug("----------------------------");

        // get the same value again, no change to DB or returned values
        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            ValueStoreKey valueStoreKey = getOrCreate(writeTxn, StringValue.of(stringValueStr2));

            assertThat(valueStoreKey).isNotNull();
            assertThat(valueStoreKey.getUniqueId()).isEqualTo((short) 1);
            // ref count increases as two things have an interest in the value
            assertRefCount(writeTxn, valueStoreKey, 2);
        });
        assertThat(valueStoreDb.getEntryCount()).isEqualTo(2);

        LOGGER.debug("----------------------------");

        // now put a different value with a different hashcode. Entry count should increase and the
        // returned valueStoreKey should have an id of 0 as it has a different hashcode.
        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            ValueStoreKey valueStoreKey = getOrCreate(writeTxn, StringValue.of(stringValueStr3));

            assertThat(valueStoreKey).isNotNull();
            assertThat(valueStoreKey.getUniqueId()).isEqualTo((short) 0);
            assertRefCount(writeTxn, valueStoreKey, 1);
        });
        assertThat(valueStoreDb.getEntryCount()).isEqualTo(3);

        LOGGER.debug("----------------------------");

        // get the same value again, no change to DB or returned values
        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            ValueStoreKey valueStoreKey = getOrCreate(writeTxn, StringValue.of(stringValueStr3));

            assertThat(valueStoreKey).isNotNull();
            assertThat(valueStoreKey.getUniqueId()).isEqualTo((short) 0);
            // ref count increases as two things have an interest in the value
            assertRefCount(writeTxn, valueStoreKey, 2);
        });
        assertThat(valueStoreDb.getEntryCount()).isEqualTo(3);
    }

    @Test
    public void testGetOrCreateSparseIds() {
        final List<String> stringsWithSameHash = generateHashClashes(10);

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
        assertThat(valueStoreKeysMap.get(0).getUniqueId()).isEqualTo((short)0);
        assertThat(valueStoreKeysMap.get(1).getUniqueId()).isEqualTo((short)1); //since deleted
        assertThat(valueStoreKeysMap.get(2).getUniqueId()).isEqualTo((short)2); //since deleted
        assertThat(valueStoreKeysMap.get(3).getUniqueId()).isEqualTo((short)3); //since deleted
        assertThat(valueStoreKeysMap.get(4).getUniqueId()).isEqualTo((short)4);
        assertThat(valueStoreKeysMap.get(5).getUniqueId()).isEqualTo((short)1); //used empty space after delete
        assertThat(valueStoreKeysMap.get(6).getUniqueId()).isEqualTo((short)2); //used empty space after delete
        assertThat(valueStoreKeysMap.get(7).getUniqueId()).isEqualTo((short)3); //used empty space after delete
        assertThat(valueStoreKeysMap.get(8).getUniqueId()).isEqualTo((short)5);
        assertThat(valueStoreKeysMap.get(9).getUniqueId()).isEqualTo((short)6);
    }

    /**
     * Generate a list (of size desiredRecords) of strings that all share the same hashcode
     * Adapted from https://gist.github.com/vaskoz/5703423
     */
    public List<String> generateHashClashes(final int desiredRecords) {

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
                .map(String::hashCode)
                .distinct()
                .count()).isEqualTo(1);

        return strings;
    }

    private void assertRefCount(Txn<ByteBuffer> txn, final ValueStoreKey valueStoreKey, final int expectedRefCount) {
        final RefDataValue refDataValue = valueStoreDb.get(txn, valueStoreKey).get();
        int foundRefCount = refDataValue.getReferenceCount();
        assertThat(foundRefCount).isEqualTo(expectedRefCount);
    }

    @Test
    public void testGet_simple() {
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
                RefDataValue val = valueStoreDb.get(txn, keySerde.serialize(valueStoreKey)).get();
                assertThat(val).isInstanceOf(StringValue.class);
                assertThat(((StringValue) val).getValue()).isEqualTo(valueStr);
            });

            // now try and get a value that doesn't exist
            Optional<RefDataValue> optRefDataValue = valueStoreDb.get(
                    txn,
                    keySerde.serialize(new ValueStoreKey(123456, (short) 99)));

            assertThat(optRefDataValue).isEmpty();
        });
    }

    @Test
    public void testGet_sameHashCodes() {
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
                RefDataValue val = valueStoreDb.get(txn, keySerde.serialize(valueStoreKey)).get();
                assertThat(val).isInstanceOf(StringValue.class);
                assertThat(((StringValue) val).getValue()).isEqualTo(valueStr);
            });
        });
    }

    @Test
    public void testAreValueEqual_twoEqualValues() {
        doAreValuesEqualAssert(StringValue.of("value1"), StringValue.of("value1"), true);
    }

    @Test
    public void testAreValueEqual_twoDifferentValues() {
        doAreValuesEqualAssert(StringValue.of("value1"), StringValue.of("value2"), false);
    }

    @Test
    public void testAreValueEqual_notFound() {
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
}