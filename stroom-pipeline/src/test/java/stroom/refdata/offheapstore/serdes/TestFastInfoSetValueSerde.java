package stroom.refdata.offheapstore.serdes;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.offheapstore.FastInfosetValue;
import stroom.refdata.offheapstore.RefDataValue;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

public class TestFastInfoSetValueSerde extends AbstractSerdeTest<RefDataValue, RefDataValueSerde> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestFastInfoSetValueSerde.class);

    @Test
    public void testSerialisationDeserialisation() {

        FastInfosetValue fastInfosetValue = new FastInfosetValue(ByteBuffer.wrap(new byte[]{0, 1, 2, 3, 4}));
//        FastInfosetValue fastInfosetValue = new FastInfosetValue(new byte[]{0, 1, 2, 3, 4});
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