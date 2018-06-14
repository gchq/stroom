package stroom.refdata.offheapstore.serdes;

import org.apache.hadoop.hbase.util.ByteBufferUtils;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.offheapstore.ByteArrayUtils;
import stroom.refdata.offheapstore.FastInfosetValue;
import stroom.refdata.offheapstore.StringValue;

import java.nio.ByteBuffer;

public class TestStringValueSerde extends AbstractSerdeTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestStringValueSerde.class);

    @Test
    public void extractValue() {

        String val = "This is my String";
        StringValue stringValue = new StringValue(9, val);

        RefDataValueSerde refDataValueSerde = RefDataValueSerdeFactory.create();

        ByteBuffer byteBuffer = refDataValueSerde.serialize(stringValue);
        LOGGER.debug("byteBuffer {}", ByteArrayUtils.byteBufferInfo(byteBuffer));
        ByteBuffer byteBufferClone = byteBuffer.duplicate();

        ByteBuffer subBuffer = refDataValueSerde.getSubBuffer(byteBuffer);
        LOGGER.debug("subBuffer {}", ByteArrayUtils.byteBufferInfo(subBuffer));

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
        LOGGER.debug("byteBuffer {}", ByteArrayUtils.byteBufferInfo(byteBuffer));

        ByteBuffer subBuffer = serde.getSubBuffer(byteBuffer);
        ByteBuffer subBufferClone = subBuffer.duplicate();
        LOGGER.debug("subBuffer {}", ByteArrayUtils.byteBufferInfo(byteBuffer));

        int refCountFound = serde.getSubSerde(byteBuffer).extractReferenceCount(subBuffer);

        LOGGER.debug("subBuffer {}", ByteArrayUtils.byteBufferInfo(byteBuffer));
        LOGGER.debug("byteBuffer {}", ByteArrayUtils.byteBufferInfo(byteBuffer));

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
        LOGGER.debug("byteBuffer {}", ByteArrayUtils.byteBufferInfo(byteBuffer));

        ByteBuffer subBuffer = serde.getSubBuffer(byteBuffer);
        ByteBuffer subBufferClone = subBuffer.duplicate();
        LOGGER.debug("subBuffer {}", ByteArrayUtils.byteBufferInfo(byteBuffer));

        int refCountFound = serde.getSubSerde(byteBuffer).getReferenceCount(subBuffer);

        LOGGER.debug("subBuffer {}", ByteArrayUtils.byteBufferInfo(byteBuffer));
        LOGGER.debug("byteBuffer {}", ByteArrayUtils.byteBufferInfo(byteBuffer));

        Assertions.assertThat(refCountFound).isEqualTo(refCount);

        // buffer's position should have moved
        Assertions.assertThat(subBuffer).isNotEqualTo(subBufferClone);
        Assertions.assertThat(byteBuffer).isEqualTo(byteBufferClone);
    }

    @Test
    public void testSerialisationDeserialisation() {

        StringValue stringValue = new StringValue(9, "this is my String");
        doSerialisationDeserialisationTest(stringValue, StringValueSerde::new);
    }
}