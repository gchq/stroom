package stroom.refdata.offheapstore.serdes;

import org.junit.Test;
import stroom.refdata.offheapstore.ValueStoreKey;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

public class TestValueStoreKeySerde extends AbstractSerdeTest<ValueStoreKey, ValueStoreKeySerde> {

    @Test
    public void testSerializeDeserialize() {
        final ValueStoreKey valueStoreKey = new ValueStoreKey(
                123456789,
                (short) 1);

        doSerialisationDeserialisationTest(valueStoreKey);
    }

    @Test
    public void testNextId() {

        ValueStoreKey originalValueStoreKey = new ValueStoreKey(1234567, (short) 123);

        ByteBuffer originalBuffer = serialize(originalValueStoreKey);

        ByteBuffer mutatedBuffer = ValueStoreKeySerde.nextId(originalBuffer);

        ValueStoreKey newValueStoreKey = deserialize(mutatedBuffer);

        assertThat(newValueStoreKey.getValueHashCode()).isEqualTo(originalValueStoreKey.getValueHashCode());
        assertThat(newValueStoreKey.getUniqueId()).isEqualTo((short) (originalValueStoreKey.getUniqueId() + 1));
    }

    @Test
    public void testIncrementId() {
        ValueStoreKey originalValueStoreKey = new ValueStoreKey(1234567, (short) 123);

        ByteBuffer byteBuffer = serialize(originalValueStoreKey);

        ValueStoreKeySerde.incrementId(byteBuffer);

        ValueStoreKey newValueStoreKey = deserialize(byteBuffer);

        assertThat(newValueStoreKey.getValueHashCode()).isEqualTo(originalValueStoreKey.getValueHashCode());
        assertThat(newValueStoreKey.getUniqueId()).isEqualTo((short) (originalValueStoreKey.getUniqueId() + 1));
    }

    @Test
    public void testUpdateId() {
        ValueStoreKey originalValueStoreKey = new ValueStoreKey(1234567, (short) 123);

        ValueStoreKeySerde serde = new ValueStoreKeySerde();

        ByteBuffer byteBuffer = serde.serialize(originalValueStoreKey);

        ValueStoreKeySerde.updateId(byteBuffer, (short) 456);

        ValueStoreKey newValueStoreKey = serde.deserialize(byteBuffer);

        assertThat(newValueStoreKey.getValueHashCode()).isEqualTo(originalValueStoreKey.getValueHashCode());
        assertThat(newValueStoreKey.getUniqueId()).isEqualTo((short) 456);
    }

    @Test
    public void testExtractId() {
        short id = 123;
        ValueStoreKey originalValueStoreKey = new ValueStoreKey(1234567, id);

        ValueStoreKeySerde serde = new ValueStoreKeySerde();

        ByteBuffer byteBuffer = serde.serialize(originalValueStoreKey);

        short extractedId = ValueStoreKeySerde.extractId(byteBuffer);

        assertThat(extractedId).isEqualTo(id);
    }

    @Override
    Class<ValueStoreKeySerde> getSerdeType() {
        return ValueStoreKeySerde.class;
    }
}
