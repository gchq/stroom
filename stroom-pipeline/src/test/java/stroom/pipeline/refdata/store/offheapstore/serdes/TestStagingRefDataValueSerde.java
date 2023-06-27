package stroom.pipeline.refdata.store.offheapstore.serdes;

import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.NullValue;
import stroom.pipeline.refdata.store.StringValue;
import stroom.pipeline.refdata.store.ValueStoreHashAlgorithm;
import stroom.pipeline.refdata.store.XxHashValueStoreHashAlgorithm;
import stroom.pipeline.refdata.store.offheapstore.StagingRefDataValue;

import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.bytebuffer.ByteBufferUtils.compare;

@Disabled  // TODO: 21/04/2023 Delete class
class TestStagingRefDataValueSerde extends AbstractSerdeTest<StagingRefDataValue, StagingRefDataValueSerde> {

    private final ValueStoreHashAlgorithm valueStoreHashAlgorithm = new XxHashValueStoreHashAlgorithm();

    @Test
    void testSerialisationDeserialisation_fastInfoSet() {

        FastInfosetValue fastInfosetValue = new FastInfosetValue(ByteBuffer.wrap(new byte[]{0, 1, 2, 3, 4}));
        StagingRefDataValue stagingRefDataValue = StagingRefDataValue.wrap(fastInfosetValue);
        final StagingRefDataValue stagingRefDataValue2 = doSerialisationDeserialisationTest(stagingRefDataValue);

        assertThat(stagingRefDataValue2.getTypeId())
                .isEqualTo(FastInfosetValue.TYPE_ID);
        assertThat(stagingRefDataValue2)
                .extracting(StagingRefDataValue::getRefDataValue)
                .isInstanceOf(FastInfosetValue.class);

        FastInfosetValue fastInfosetValue2 = (FastInfosetValue) stagingRefDataValue2.getRefDataValue();

        assertThat(compare(fastInfosetValue.getByteBuffer(), fastInfosetValue2.getByteBuffer()))
                .isZero();

        // Pass in null to getValueHashCode, so we know the hash is a stored one, rather than generated on the fly
        assertThat(stagingRefDataValue2)
                .extracting(val ->
                        val.getRefDataValue().getValueHashCode(null))
                .isEqualTo(stagingRefDataValue.getRefDataValue().getValueHashCode(valueStoreHashAlgorithm));
    }

    @Test
    void testSerialisationDeserialisation_string() {

        StringValue stringValue = new StringValue("foo");
        StagingRefDataValue stagingRefDataValue = StagingRefDataValue.wrap(stringValue);
        final StagingRefDataValue stagingRefDataValue2 = doSerialisationDeserialisationTest(stagingRefDataValue);

        assertThat(stagingRefDataValue2.getTypeId())
                .isEqualTo(StringValue.TYPE_ID);
        assertThat(stagingRefDataValue2)
                .extracting(StagingRefDataValue::getRefDataValue)
                .isInstanceOf(StringValue.class);

        final StringValue stringValue2 = (StringValue) stagingRefDataValue2.getRefDataValue();
        assertThat(stringValue2.getValue())
                .isEqualTo("foo");

        // Pass in null to getValueHashCode, so we know the hash is a stored one, rather than generated on the fly
        assertThat(stagingRefDataValue2)
                .extracting(val ->
                        val.getRefDataValue().getValueHashCode(null))
                .isEqualTo(stagingRefDataValue.getRefDataValue().getValueHashCode(valueStoreHashAlgorithm));
    }

    @Test
    void testSerialisationDeserialisation_null() {

        NullValue nullValue = NullValue.getInstance();
        StagingRefDataValue stagingRefDataValue = StagingRefDataValue.wrap(nullValue);
        final StagingRefDataValue stagingRefDataValue2 = doSerialisationDeserialisationTest(stagingRefDataValue);

        assertThat(stagingRefDataValue2.getTypeId())
                .isEqualTo(NullValue.TYPE_ID);
        assertThat(stagingRefDataValue2.getRefDataValue())
                .isInstanceOf(NullValue.class);
        assertThat(stagingRefDataValue2.getRefDataValue().isNullValue())
                .isTrue();

        // Pass in null to getValueHashCode, so we know the hash is a stored one, rather than generated on the fly
        assertThat(stagingRefDataValue2)
                .extracting(val ->
                        val.getRefDataValue().getValueHashCode(null))
                .isEqualTo(stagingRefDataValue.getRefDataValue().getValueHashCode(valueStoreHashAlgorithm));
    }

    @Test
    void testTypeIdExtraction() {
        StringValue stringValue = new StringValue("foo");
        StagingRefDataValue stagingRefDataValue = StagingRefDataValue.wrap(stringValue);
        doExtractionTest(stagingRefDataValue, getSerde()::extractTypeId, StagingRefDataValue::getTypeId);
    }

    @Test
    void testValueHashExtraction() {
        StringValue stringValue = new StringValue("foo");
        StagingRefDataValue stagingRefDataValue = StagingRefDataValue.wrap(stringValue);
        doExtractionTest(
                stagingRefDataValue,
                getSerde()::extractValueHash,
                val -> val.getRefDataValue().getValueHashCode(valueStoreHashAlgorithm));
    }

    @Override
    TypeLiteral<StagingRefDataValueSerde> getSerdeType() {
        return new TypeLiteral<StagingRefDataValueSerde>(){};
    }

    @Override
    Supplier<StagingRefDataValueSerde> getSerdeSupplier() {
        return () -> new StagingRefDataValueSerde(
                new GenericRefDataValueSerde(new RefDataValueSerdeFactory()),
                valueStoreHashAlgorithm);
    }
}
