package stroom.pipeline.refdata.store.offheapstore.serdes;


import stroom.pipeline.refdata.store.offheapstore.ValueStoreKey;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

class TestValueStoreKeySerde extends AbstractSerdeTest<ValueStoreKey, ValueStoreKeySerde> {

    @Test
    void testSerializeDeserialize() {
        final ValueStoreKey valueStoreKey = new ValueStoreKey(
                123456789L,
                (short) 1);

        doSerialisationDeserialisationTest(valueStoreKey);
    }

    @Test
    void testIncrementId() {
        ValueStoreKey originalValueStoreKey = new ValueStoreKey(1234567L, (short) 123);

        ByteBuffer byteBuffer = serialize(originalValueStoreKey);

        ValueStoreKeySerde.incrementId(byteBuffer);

        ValueStoreKey newValueStoreKey = deserialize(byteBuffer);

        assertThat(newValueStoreKey.getValueHashCode()).isEqualTo(originalValueStoreKey.getValueHashCode());
        assertThat(newValueStoreKey.getUniqueId()).isEqualTo((short) (originalValueStoreKey.getUniqueId() + 1));
    }

    @Test
    void testUpdateId() {
        ValueStoreKey originalValueStoreKey = new ValueStoreKey(1234567L, (short) 123);

        ValueStoreKeySerde serde = new ValueStoreKeySerde();

        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(serde.getBufferCapacity());

        serde.serialize(byteBuffer, originalValueStoreKey);

        ValueStoreKeySerde.updateId(byteBuffer, (short) 456);

        ValueStoreKey newValueStoreKey = serde.deserialize(byteBuffer);

        assertThat(newValueStoreKey.getValueHashCode()).isEqualTo(originalValueStoreKey.getValueHashCode());
        assertThat(newValueStoreKey.getUniqueId()).isEqualTo((short) 456);
    }

    @Test
    void testExtractId() {
        short id = 123;
        ValueStoreKey originalValueStoreKey = new ValueStoreKey(1234567L, id);

        ValueStoreKeySerde serde = new ValueStoreKeySerde();

        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(serde.getBufferCapacity());
        serde.serialize(byteBuffer, originalValueStoreKey);

        short extractedId = ValueStoreKeySerde.extractId(byteBuffer);

        assertThat(extractedId).isEqualTo(id);
    }

    @Override
    Class<ValueStoreKeySerde> getSerdeType() {
        return ValueStoreKeySerde.class;
    }
}
