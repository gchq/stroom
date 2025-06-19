package stroom.pipeline.refdata.store.offheapstore.serdes;


import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.StringValue;

import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

class TestStringValueSerde extends AbstractSerdeTest<RefDataValue, RefDataValueSerde> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestStringValueSerde.class);

    @Test
    void testSerialisationDeserialisation() {

        final StringValue stringValue = new StringValue("this is my String");
        doSerialisationDeserialisationTest(stringValue);
    }

    @Override
    TypeLiteral<RefDataValueSerde> getSerdeType() {
        return new TypeLiteral<RefDataValueSerde>(){};
    }

    @Override
    Supplier<RefDataValueSerde> getSerdeSupplier() {
        return () -> new RefDataValueSerdeFactory().get(StringValue.TYPE_ID);
    }
}
