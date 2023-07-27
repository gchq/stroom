package stroom.pipeline.refdata.store.offheapstore.serdes;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.ByteBufferPoolFactory;
import stroom.bytebuffer.PooledByteBufferOutputStream;
import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.StagingValue;
import stroom.pipeline.refdata.store.StagingValueImpl;
import stroom.pipeline.refdata.store.StagingValueOutputStream;
import stroom.pipeline.refdata.store.ValueStoreHashAlgorithm;
import stroom.pipeline.refdata.store.XxHashValueStoreHashAlgorithm;

import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class TestStagingValueSerde extends AbstractSerdeTest<StagingValue, StagingValueSerde> {

    private final ByteBufferPool byteBufferPool = new ByteBufferPoolFactory().getByteBufferPool();
    private final ValueStoreHashAlgorithm valueStoreHashAlgorithm = new XxHashValueStoreHashAlgorithm();


    @Test
    void testSerDeSer() throws IOException {
        final StagingValueOutputStream stagingValueOutputStream = buildStagingValueOutputStream(
                FastInfosetValue.TYPE_ID);

        final ByteBuffer byteBuffer = getSerde().serialize((Supplier<ByteBuffer>) null, stagingValueOutputStream);

        final StagingValue output = getSerde().deserialize(byteBuffer);

        // We serialise from a StagingValueOutputStream then out to a StagingValueImpl
        assertThat(output)
                .isInstanceOf(StagingValueImpl.class);

        assertThat(output.getTypeId())
                .isEqualTo(stagingValueOutputStream.getTypeId());
        assertThat(output.getValueHashCode())
                .isEqualTo(stagingValueOutputStream.getValueHashCode());
        assertThat(output.getFullByteBuffer())
                .isEqualTo(stagingValueOutputStream.getFullByteBuffer());
        assertThat(output.getValueBuffer())
                .isEqualTo(stagingValueOutputStream.getValueBuffer());
    }

    private StagingValueOutputStream buildStagingValueOutputStream(final int typeId) throws IOException {
        final byte[] valueBytes = {0, 1, 2, 3, 4};
        final StagingValueOutputStream stagingValueOutputStream = new StagingValueOutputStream(
                valueStoreHashAlgorithm,
                capacity -> new PooledByteBufferOutputStream(byteBufferPool, capacity));

        stagingValueOutputStream.write(valueBytes);
        stagingValueOutputStream.setTypeId(typeId);
        return stagingValueOutputStream;
    }

    @Test
    void testTypeIdExtraction() throws IOException {
        final StagingValue stagingValueOutputStream = buildStagingValueOutputStream(
                FastInfosetValue.TYPE_ID);
        doExtractionTest(stagingValueOutputStream, StagingValueSerde::extractTypeId, StagingValue::getTypeId);
    }

    @Test
    void testValueHashExtraction() throws IOException {
        final StagingValue stagingValueOutputStream = buildStagingValueOutputStream(
                FastInfosetValue.TYPE_ID);
        doExtractionTest(stagingValueOutputStream, StagingValueSerde::extractValueHash, StagingValue::getValueHashCode);
    }

    @Override
    TypeLiteral<StagingValueSerde> getSerdeType() {
        return new TypeLiteral<StagingValueSerde>(){};
    }

    @Override
    Supplier<StagingValueSerde> getSerdeSupplier() {
        return StagingValueSerde::new;
    }
}
