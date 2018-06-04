package stroom.refdata.offheapstore.serdes;

import org.junit.Test;
import stroom.refdata.offheapstore.ValueStoreKey;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

public class TestValueStoreKeySerde extends AbstractSerdeTest {

    @Test
    public void testSerializeDeserialize() {
        final ValueStoreKey valueStoreKey = new ValueStoreKey(
                123456789,
                (short) 1);

        doSerialisationDeserialisationTest(valueStoreKey, ValueStoreKeySerde::new);
    }

    @Test
    public void testNextId() {

        ValueStoreKey originalValueStoreKey = new ValueStoreKey(1234567, (short) 123);

        ValueStoreKeySerde serde = new ValueStoreKeySerde();

        ByteBuffer originalBuffer = serde.serialize(originalValueStoreKey);

        ByteBuffer mutatedBuffer = serde.nextId(originalBuffer);

        ValueStoreKey newValueStoreKey = serde.deserialize(mutatedBuffer);

        assertThat(newValueStoreKey.getValueHashCode()).isEqualTo(originalValueStoreKey.getValueHashCode());
        assertThat(newValueStoreKey.getUniqueId()).isEqualTo((short) (originalValueStoreKey.getUniqueId() + 1));
    }

    @Test
    public void testIncrementId() {
        ValueStoreKey originalValueStoreKey = new ValueStoreKey(1234567, (short) 123);

        ValueStoreKeySerde serde = new ValueStoreKeySerde();

        ByteBuffer byteBuffer = serde.serialize(originalValueStoreKey);

        serde.incrementId(byteBuffer);

        ValueStoreKey newValueStoreKey = serde.deserialize(byteBuffer);

        assertThat(newValueStoreKey.getValueHashCode()).isEqualTo(originalValueStoreKey.getValueHashCode());
        assertThat(newValueStoreKey.getUniqueId()).isEqualTo((short) (originalValueStoreKey.getUniqueId() + 1));

    }
}
