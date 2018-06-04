package stroom.refdata.offheapstore.databases;

import org.junit.Before;
import org.junit.Test;
import stroom.refdata.lmdb.LmdbUtils;
import stroom.refdata.offheapstore.StringValue;
import stroom.refdata.offheapstore.ValueStoreKey;
import stroom.refdata.offheapstore.serdes.RefDataValueSerdeFactory;
import stroom.refdata.offheapstore.serdes.ValueStoreKeySerde;


public class TestValueStoreDb extends AbstractLmdbDbTest {

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

    @Test
    public void getOrCreate() {

        valueStoreDb.put(new ValueStoreKey(3,(short) 1), StringValue.of("3-1"), false);
        valueStoreDb.put(new ValueStoreKey(3,(short) 0), StringValue.of("3-0"), false);
        valueStoreDb.put(new ValueStoreKey(3,(short) 3), StringValue.of("3-3"), false);
        valueStoreDb.put(new ValueStoreKey(3,(short) 2), StringValue.of("3-2"), false);
        valueStoreDb.put(new ValueStoreKey(1,(short) 1), StringValue.of("1-1"), false);
        valueStoreDb.put(new ValueStoreKey(1,(short) 0), StringValue.of("1-0"), false);
        valueStoreDb.put(new ValueStoreKey(1,(short) 3), StringValue.of("1-3"), false);
        valueStoreDb.put(new ValueStoreKey(1,(short) 2), StringValue.of("1-2"), false);
        valueStoreDb.put(new ValueStoreKey(2,(short) 3), StringValue.of("2-3"), false);
        valueStoreDb.put(new ValueStoreKey(2,(short) 1), StringValue.of("2-1"), false);
        valueStoreDb.put(new ValueStoreKey(2,(short) 0), StringValue.of("2-0"), false);
        valueStoreDb.put(new ValueStoreKey(2,(short) 2), StringValue.of("2-2"), false);

        valueStoreDb.logRawDatabaseContents();
        valueStoreDb.logDatabaseContents();
    }
}