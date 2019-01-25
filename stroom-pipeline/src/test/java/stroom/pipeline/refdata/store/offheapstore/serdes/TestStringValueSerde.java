package stroom.pipeline.refdata.store.offheapstore.serdes;


import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.StringValue;

import java.util.function.Supplier;

class TestStringValueSerde extends AbstractSerdeTest<RefDataValue, RefDataValueSerde> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestStringValueSerde.class);

    @Test
    void testSerialisationDeserialisation() {

        StringValue stringValue = new StringValue("this is my String");
        doSerialisationDeserialisationTest(stringValue);
    }

    @Override
    Class<RefDataValueSerde> getSerdeType() {
        return RefDataValueSerde.class;
    }

    @Override
    Supplier<RefDataValueSerde> getSerdeSupplier() {
        return () -> new RefDataValueSerdeFactory().get(StringValue.TYPE_ID);
    }
}