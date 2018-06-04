package stroom.refdata.offheapstore.serdes;

import org.junit.Test;
import stroom.refdata.offheapstore.ValueStoreKey;

public class TestValueStoreKeySerde extends AbstractSerdeTest {

    @Test
    public void serializeDeserialize() {
        final ValueStoreKey valueStoreKey = new ValueStoreKey(
                123456789,
                (short) 1);

        doSerialisationDeserialisationTest(valueStoreKey, ValueStoreKeySerde::new);
    }
}
