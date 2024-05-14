package stroom.query.common.v2;

import stroom.bytebuffer.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferPoolOutput;
import stroom.query.language.functions.ref.DataWriter;
import stroom.query.language.functions.ref.KryoDataWriter;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class LmdbRowValueFactory {

    private final ByteBufferFactory byteBufferFactory;
    private final ValueReferenceIndex valueReferenceIndex;
    private final DataWriterFactory writerFactory;

    private int bufferSize = 128;

    public LmdbRowValueFactory(final ByteBufferFactory byteBufferFactory,
                               final ValueReferenceIndex valueReferenceIndex,
                               final DataWriterFactory writerFactory) {
        this.byteBufferFactory = byteBufferFactory;
        this.valueReferenceIndex = valueReferenceIndex;
        this.writerFactory = writerFactory;
    }

    public ByteBuffer useOutput(final Consumer<ByteBufferPoolOutput> consumer) {
        try (final ByteBufferPoolOutput output =
                new ByteBufferPoolOutput(byteBufferFactory, bufferSize, -1)) {
            consumer.accept(output);
            final ByteBuffer byteBuffer = output.getByteBuffer().flip();
            bufferSize = Math.max(bufferSize, byteBuffer.capacity());
            return byteBuffer;
        }
    }

    public ByteBuffer create(final StoredValues storedValues) {
        return useOutput(output -> {
            try (final KryoDataWriter writer = writerFactory.create(output)) {
                write(storedValues, writer);
            }
        });
    }

    private void write(final StoredValues storedValues,
                       final DataWriter writer) {
        valueReferenceIndex.write(storedValues, writer);
    }
}
