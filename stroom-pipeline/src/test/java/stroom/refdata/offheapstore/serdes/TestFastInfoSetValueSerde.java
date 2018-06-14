package stroom.refdata.offheapstore.serdes;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.offheapstore.ByteArrayUtils;
import stroom.refdata.offheapstore.FastInfosetValue;

import java.nio.ByteBuffer;

public class TestFastInfoSetValueSerde extends AbstractSerdeTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestFastInfoSetValueSerde.class);

    @Test
    public void extractValueBuffer() {

        byte[] valueBytes = new byte[]{0, 1, 2, 3, 4};
        FastInfosetValue fastInfosetValue = new FastInfosetValue(9, valueBytes);

        RefDataValueSerde refDataValueSerde = RefDataValueSerdeFactory.create();


        ByteBuffer byteBuffer = refDataValueSerde.serialize(fastInfosetValue);
        LOGGER.debug("byteBuffer {}", ByteArrayUtils.byteBufferInfo(byteBuffer));
        ByteBuffer byteBufferClone = byteBuffer.duplicate();


        ByteBuffer valueByteBuffer = refDataValueSerde.extractValueBuffer(byteBuffer);
        LOGGER.debug("byteBuffer {}", ByteArrayUtils.byteBufferInfo(byteBuffer));
        LOGGER.debug("valueByteBuffer {}", ByteArrayUtils.byteBufferInfo(valueByteBuffer));

        byte[] valueBytesFound = new byte[valueBytes.length];

        Assertions.assertThat(valueByteBuffer.remaining()).isEqualTo(5);

        valueByteBuffer.get(valueBytesFound);

        Assertions.assertThat(valueBytesFound).isEqualTo(valueBytes);

        // buffer should be unchanged
        Assertions.assertThat(byteBuffer).isEqualTo(byteBufferClone);
    }

    @Test
    public void extractReferenceCount() {

        byte[] valueBytes = new byte[]{0, 1, 2, 3, 4};
        int refCount = 9;
        FastInfosetValue fastInfosetValue = new FastInfosetValue(refCount, valueBytes);

        FastInfoSetValueSerde serde = new FastInfoSetValueSerde();

        ByteBuffer byteBuffer = serde.serialize(fastInfosetValue);
        ByteBuffer byteBufferClone = byteBuffer.duplicate();

        int refCountFound = new FastInfoSetValueSerde().getReferenceCount(byteBuffer);

        Assertions.assertThat(refCountFound).isEqualTo(refCount);

        // buffer's position should have moved
        Assertions.assertThat(byteBuffer).isNotEqualTo(byteBufferClone);
    }

    @Test
    public void testSerialisationDeserialisation() {

        FastInfosetValue fastInfosetValue = new FastInfosetValue(9, new byte[]{0, 1, 2, 3, 4});
        doSerialisationDeserialisationTest(fastInfosetValue, RefDataValueSerdeFactory::create);
    }
}