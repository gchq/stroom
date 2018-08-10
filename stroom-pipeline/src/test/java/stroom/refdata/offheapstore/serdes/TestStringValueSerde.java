package stroom.refdata.offheapstore.serdes;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.offheapstore.RefDataValue;
import stroom.refdata.offheapstore.StringValue;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

public class TestStringValueSerde extends AbstractSerdeTest<RefDataValue, RefDataValueSubSerde> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestStringValueSerde.class);

    @Test
    public void extractValue() {

        String val = "This is my String";
        StringValue stringValue = new StringValue(9, val);

        RefDataValueSerde refDataValueSerde = RefDataValueSerdeFactory.create();

        ByteBuffer byteBuffer = refDataValueSerde.serialize(stringValue);
        LOGGER.debug("byteBuffer {}", stroom.refdata.offheapstore.ByteBufferUtils.byteBufferInfo(byteBuffer));
        ByteBuffer byteBufferClone = byteBuffer.duplicate();

        ByteBuffer subBuffer = refDataValueSerde.getSubBuffer(byteBuffer);
        LOGGER.debug("subBuffer {}", stroom.refdata.offheapstore.ByteBufferUtils.byteBufferInfo(subBuffer));

        Assertions.assertThat(subBuffer.remaining()).isEqualTo(byteBuffer.remaining() - 1);


        String valFound = StringValueSerde.extractStringValue(subBuffer);


        Assertions.assertThat(valFound).isEqualTo(val);

        // buffer should be unchanged
        Assertions.assertThat(byteBuffer).isEqualTo(byteBufferClone);
    }

    @Test
    public void extractReferenceCount() {

        String val = "This is my String";
        int refCount = 9;
        StringValue stringValue = new StringValue(refCount, val);

        RefDataValueSerde serde = RefDataValueSerdeFactory.create();

        ByteBuffer byteBuffer = serde.serialize(stringValue);
        ByteBuffer byteBufferClone = byteBuffer.duplicate();
        LOGGER.debug("byteBuffer {}", stroom.refdata.offheapstore.ByteBufferUtils.byteBufferInfo(byteBuffer));

        ByteBuffer subBuffer = serde.getSubBuffer(byteBuffer);
        ByteBuffer subBufferClone = subBuffer.duplicate();
        LOGGER.debug("subBuffer {}", stroom.refdata.offheapstore.ByteBufferUtils.byteBufferInfo(byteBuffer));

        int refCountFound = serde.getSubSerde(byteBuffer).extractReferenceCount(subBuffer);

        LOGGER.debug("subBuffer {}", stroom.refdata.offheapstore.ByteBufferUtils.byteBufferInfo(byteBuffer));
        LOGGER.debug("byteBuffer {}", stroom.refdata.offheapstore.ByteBufferUtils.byteBufferInfo(byteBuffer));

        Assertions.assertThat(refCountFound).isEqualTo(refCount);

        // buffer's position should have moved
        Assertions.assertThat(subBuffer).isEqualTo(subBufferClone);
        Assertions.assertThat(byteBuffer).isEqualTo(byteBufferClone);
    }

    @Test
    public void getReferenceCount() {

        String val = "This is my String";
        int refCount = 9;
        StringValue stringValue = new StringValue(refCount, val);

        RefDataValueSerde serde = RefDataValueSerdeFactory.create();

        ByteBuffer byteBuffer = serde.serialize(stringValue);
        ByteBuffer byteBufferClone = byteBuffer.duplicate();
        LOGGER.debug("byteBuffer {}", stroom.refdata.offheapstore.ByteBufferUtils.byteBufferInfo(byteBuffer));

        ByteBuffer subBuffer = serde.getSubBuffer(byteBuffer);
        ByteBuffer subBufferClone = subBuffer.duplicate();
        LOGGER.debug("subBuffer {}", stroom.refdata.offheapstore.ByteBufferUtils.byteBufferInfo(byteBuffer));

        int refCountFound = serde.getSubSerde(byteBuffer).getReferenceCount(subBuffer);

        LOGGER.debug("subBuffer {}", stroom.refdata.offheapstore.ByteBufferUtils.byteBufferInfo(byteBuffer));
        LOGGER.debug("byteBuffer {}", stroom.refdata.offheapstore.ByteBufferUtils.byteBufferInfo(byteBuffer));

        Assertions.assertThat(refCountFound).isEqualTo(refCount);

        // buffer's position should have moved
        Assertions.assertThat(subBuffer).isNotEqualTo(subBufferClone);
        Assertions.assertThat(byteBuffer).isEqualTo(byteBufferClone);
    }

    @Test
    public void testSerialisationDeserialisation() {

        StringValue stringValue = new StringValue(9, "this is my String");
        doSerialisationDeserialisationTest(stringValue);
    }

    @Override
    Class<RefDataValueSubSerde> getSerdeType() {
        return RefDataValueSubSerde.class;
    }

    @Override
    Supplier<RefDataValueSubSerde> getSerdeSupplier() {
        return StringValueSerde::new;
    }
}