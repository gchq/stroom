package stroom.pipeline.refdata.store.offheapstore.serdes;

import stroom.pipeline.refdata.store.NullValue;
import stroom.pipeline.refdata.store.RefDataValue;

import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

class TestNullValueSerde extends AbstractSerdeTest<RefDataValue, RefDataValueSerde> {

    @Test
    void testSerialisationDeserialisation() {

        NullValue nullValue = NullValue.getInstance();
        doSerialisationDeserialisationTest(nullValue);
    }

    @Override
    TypeLiteral<RefDataValueSerde> getSerdeType() {
        return new TypeLiteral<RefDataValueSerde>(){};
    }

    @Override
    Supplier<RefDataValueSerde> getSerdeSupplier() {
        return NullValueSerde::new;
    }
}
