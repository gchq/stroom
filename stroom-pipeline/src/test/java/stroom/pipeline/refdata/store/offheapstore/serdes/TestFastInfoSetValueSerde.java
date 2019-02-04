package stroom.pipeline.refdata.store.offheapstore.serdes;


import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.RefDataValue;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

class TestFastInfoSetValueSerde extends AbstractSerdeTest<RefDataValue, RefDataValueSerde> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestFastInfoSetValueSerde.class);

    @Test
    void testSerialisationDeserialisation() {

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