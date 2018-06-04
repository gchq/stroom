package stroom.refdata.offheapstore.databases;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.lmdb.LmdbUtils;
import stroom.refdata.offheapstore.RefDataValue;
import stroom.refdata.offheapstore.StringValue;
import stroom.refdata.offheapstore.ValueStoreKey;
import stroom.refdata.offheapstore.serdes.RefDataValueSerdeFactory;
import stroom.refdata.offheapstore.serdes.ValueStoreKeySerde;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.filter;


public class TestValueStoreDb extends AbstractLmdbDbTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestValueStoreDb.class);

    private ValueStoreDb valueStoreDb = null;

    @Before
    @Override
    public void setup() {
        super.setup();

        valueStoreDb = new ValueStoreDb(
                lmdbEnv,
                new ValueStoreKeySerde(),
                RefDataValueSerdeFactory.create());
    }

//    @Test
//    public void getOrCreate() {
//
//        valueStoreDb.put(new ValueStoreKey(3,(short) 1), StringValue.of("3-1"), false);
//        valueStoreDb.put(new ValueStoreKey(3,(short) 0), StringValue.of("3-0"), false);
//        valueStoreDb.put(new ValueStoreKey(3,(short) 3), StringValue.of("3-3"), false);
//        valueStoreDb.put(new ValueStoreKey(3,(short) 2), StringValue.of("3-2"), false);
//        valueStoreDb.put(new ValueStoreKey(1,(short) 1), StringValue.of("1-1"), false);
//        valueStoreDb.put(new ValueStoreKey(1,(short) 0), StringValue.of("1-0"), false);
//        valueStoreDb.put(new ValueStoreKey(1,(short) 3), StringValue.of("1-3"), false);
//        valueStoreDb.put(new ValueStoreKey(1,(short) 2), StringValue.of("1-2"), false);
//        valueStoreDb.put(new ValueStoreKey(2,(short) 3), StringValue.of("2-3"), false);
//        valueStoreDb.put(new ValueStoreKey(2,(short) 1), StringValue.of("2-1"), false);
//        valueStoreDb.put(new ValueStoreKey(2,(short) 0), StringValue.of("2-0"), false);
//        valueStoreDb.put(new ValueStoreKey(2,(short) 2), StringValue.of("2-2"), false);
//
//        valueStoreDb.logRawDatabaseContents();
//        valueStoreDb.logDatabaseContents();
//    }

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
            ValueStoreKey valueStoreKey = valueStoreDb.getOrCreate(writeTxn, StringValue.of(stringValueStr1));

            assertThat(valueStoreKey).isNotNull();
            assertThat(valueStoreKey.getUniqueId()).isEqualTo((short) 0);
        });
        assertThat(valueStoreDb.getEntryCount()).isEqualTo(1);

        LOGGER.debug("----------------------------");

        // now put the same value again. Entry count should not change as we already have the value
        // returned valueStoreKey should also be the same.
        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            ValueStoreKey valueStoreKey = valueStoreDb.getOrCreate(writeTxn, StringValue.of(stringValueStr1));

            assertThat(valueStoreKey).isNotNull();
            assertThat(valueStoreKey.getUniqueId()).isEqualTo((short) 0);
        });
        assertThat(valueStoreDb.getEntryCount()).isEqualTo(1);

        LOGGER.debug("----------------------------");

        // now put a different value with same hashcode. Entry count should increase and the
        // returned valueStoreKey should have an id of 1 as it has same hashcode as last one
        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            ValueStoreKey valueStoreKey = valueStoreDb.getOrCreate(writeTxn, StringValue.of(stringValueStr2));

            assertThat(valueStoreKey).isNotNull();
            assertThat(valueStoreKey.getUniqueId()).isEqualTo((short) 1);
        });
        assertThat(valueStoreDb.getEntryCount()).isEqualTo(2);

        LOGGER.debug("----------------------------");

        // get the same value again, no change to DB or returned values
        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            ValueStoreKey valueStoreKey = valueStoreDb.getOrCreate(writeTxn, StringValue.of(stringValueStr2));

            assertThat(valueStoreKey).isNotNull();
            assertThat(valueStoreKey.getUniqueId()).isEqualTo((short) 1);
        });
        assertThat(valueStoreDb.getEntryCount()).isEqualTo(2);

        LOGGER.debug("----------------------------");

        // now put a different value with a different hashcode. Entry count should increase and the
        // returned valueStoreKey should have an id of 0 as it has a different hashcode.
        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            ValueStoreKey valueStoreKey = valueStoreDb.getOrCreate(writeTxn, StringValue.of(stringValueStr3));

            assertThat(valueStoreKey).isNotNull();
            assertThat(valueStoreKey.getUniqueId()).isEqualTo((short) 0);
        });
        assertThat(valueStoreDb.getEntryCount()).isEqualTo(3);

        LOGGER.debug("----------------------------");

        // get the same value again, no change to DB or returned values
        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn -> {
            ValueStoreKey valueStoreKey = valueStoreDb.getOrCreate(writeTxn, StringValue.of(stringValueStr3));

            assertThat(valueStoreKey).isNotNull();
            assertThat(valueStoreKey.getUniqueId()).isEqualTo((short) 0);
        });
        assertThat(valueStoreDb.getEntryCount()).isEqualTo(3);
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
            valueToKeyMap.put(val1str, valueStoreDb.getOrCreate(writeTxn, StringValue.of(val1str)));
            valueToKeyMap.put(val2str, valueStoreDb.getOrCreate(writeTxn, StringValue.of(val2str)));
            valueToKeyMap.put(val3str, valueStoreDb.getOrCreate(writeTxn, StringValue.of(val3str)));
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
            valueToKeyMap.put(val1str, valueStoreDb.getOrCreate(writeTxn, StringValue.of(val1str)));
            valueToKeyMap.put(val2str, valueStoreDb.getOrCreate(writeTxn, StringValue.of(val2str)));
            valueToKeyMap.put(val3str, valueStoreDb.getOrCreate(writeTxn, StringValue.of(val3str)));
            valueToKeyMap.put(val4str, valueStoreDb.getOrCreate(writeTxn, StringValue.of(val4str)));
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
}