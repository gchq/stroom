package stroom.query.common.v2;

import stroom.dashboard.expression.v1.ref.ErrorConsumer;
import stroom.dashboard.expression.v1.ref.MyByteBufferOutput;
import stroom.dashboard.expression.v1.ref.OutputFactory;
import stroom.dashboard.expression.v1.ref.StoredValues;
import stroom.dashboard.expression.v1.ref.ValueReferenceIndex;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class LmdbRowValueFactory {

    private final ValueReferenceIndex valueReferenceIndex;
    private final OutputFactory outputFactory;
    private final ErrorConsumer errorConsumer;

    private int bufferSize = 128;

    public LmdbRowValueFactory(final ValueReferenceIndex valueReferenceIndex,
                               final OutputFactory outputFactory,
                               final ErrorConsumer errorConsumer) {
        this.valueReferenceIndex = valueReferenceIndex;
        this.outputFactory = outputFactory;
        this.errorConsumer = errorConsumer;
    }

    public ByteBuffer useOutput(final Consumer<MyByteBufferOutput> consumer) {
        try (final MyByteBufferOutput output = outputFactory.createOutput(bufferSize, errorConsumer)) {
            consumer.accept(output);
            output.flush();

            final ByteBuffer byteBuffer = output.getByteBuffer().flip();
            bufferSize = Math.max(bufferSize, byteBuffer.capacity());
            return byteBuffer;
        }
    }

    public ByteBuffer create(final StoredValues storedValues) {
        return useOutput(output -> write(storedValues, output));
    }

    private void write(final StoredValues storedValues,
                       final MyByteBufferOutput output) {
        valueReferenceIndex.write(storedValues, output);
    }
}
