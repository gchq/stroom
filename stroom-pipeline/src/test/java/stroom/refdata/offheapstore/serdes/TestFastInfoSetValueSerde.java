package stroom.refdata.offheapstore.serdes;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.offheapstore.FastInfosetValue;
import stroom.refdata.offheapstore.RefDataValue;

import java.util.function.Supplier;

public class TestFastInfoSetValueSerde extends AbstractSerdeTest<RefDataValue, RefDataValueSerde> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestFastInfoSetValueSerde.class);

//    @Test
//    public void extractValueBuffer() {
//
//        byte[] valueBytes = new byte[]{0, 1, 2, 3, 4};
//        FastInfosetValue fastInfosetValue = new FastInfosetValue(valueBytes);
//
//
//        ByteBuffer byteBuffer = serialize(fastInfosetValue);
//        LOGGER.debug("byteBuffer {}", ByteBufferUtils.byteBufferInfo(byteBuffer));
//        ByteBuffer byteBufferClone = byteBuffer.duplicate();
//
//
////        ByteBuffer valueByteBuffer = getSerde().extractValueBuffer(byteBuffer);
////        LOGGER.debug("byteBuffer {}", ByteBufferUtils.byteBufferInfo(byteBuffer));
////        LOGGER.debug("valueByteBuffer {}", ByteBufferUtils.byteBufferInfo(valueByteBuffer));
//
//        byte[] valueBytesFound = new byte[valueBytes.length];
//
//        Assertions.assertThat(valueByteBuffer.remaining()).isEqualTo(5);
//
//        valueByteBuffer.get(valueBytesFound);
//
//        Assertions.assertThat(valueBytesFound).isEqualTo(valueBytes);
//
//        // buffer should be unchanged
//        Assertions.assertThat(byteBuffer).isEqualTo(byteBufferClone);
//    }

//    @Test
//    public void extractReferenceCount() {
//
//        byte[] valueBytes = new byte[]{0, 1, 2, 3, 4};
//        int refCount = 9;
//        FastInfosetValue fastInfosetValue = new FastInfosetValue(refCount, valueBytes);
//
//        FastInfoSetValueSerde serde = new FastInfoSetValueSerde();
//
//        ByteBuffer byteBuffer = serde.serialize(fastInfosetValue);
//        ByteBuffer byteBufferClone = byteBuffer.duplicate();
//
//        int refCountFound = new FastInfoSetValueSerde().getReferenceCount(byteBuffer);
//
//        Assertions.assertThat(refCountFound).isEqualTo(refCount);
//
//        // buffer's position should have moved
//        Assertions.assertThat(byteBuffer).isNotEqualTo(byteBufferClone);
//    }

    @Test
    public void testSerialisationDeserialisation() {

        FastInfosetValue fastInfosetValue = new FastInfosetValue(new byte[]{0, 1, 2, 3, 4});
        doSerialisationDeserialisationTest(fastInfosetValue);
    }

    @Override
    Class<RefDataValueSerde> getSerdeType() {
        return RefDataValueSerde.class;
    }

    @Override
    Supplier<RefDataValueSerde> getSerdeSupplier() {
        return () -> new RefDataValueSerdeFactory().get(FastInfosetValue.TYPE_ID);
    }
}