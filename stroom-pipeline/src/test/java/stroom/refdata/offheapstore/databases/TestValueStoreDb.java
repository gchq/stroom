package stroom.refdata.offheapstore.databases;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.lmdb.LmdbUtils;
import stroom.refdata.offheapstore.StringValue;
import stroom.refdata.offheapstore.ValueStoreKey;
import stroom.refdata.offheapstore.serdes.RefDataValueSerdeFactory;
import stroom.refdata.offheapstore.serdes.ValueStoreKeySerde;

import static org.assertj.core.api.Assertions.assertThat;


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
}