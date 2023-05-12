package stroom.query.common.v2;

import stroom.dashboard.expression.v1.ref.ErrorConsumer;
import stroom.dashboard.expression.v1.ref.MyByteBufferOutput;
import stroom.dashboard.expression.v1.ref.OutputFactory;
import stroom.dashboard.expression.v1.ref.StoredValues;
import stroom.dashboard.expression.v1.ref.ValueReferenceIndex;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class LmdbRowValueFactory {

    private final KeyFactory keyFactory;
    private final ValueReferenceIndex valueReferenceIndex;
    private final OutputFactory outputFactory;
    private final ErrorConsumer errorConsumer;

    private int bufferSize = 128;

    public LmdbRowValueFactory(final KeyFactory keyFactory,
                               final ValueReferenceIndex valueReferenceIndex,
                               final OutputFactory outputFactory,
                               final ErrorConsumer errorConsumer) {
        this.keyFactory = keyFactory;
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

    public LmdbRowValue create(final Key key,
                               final StoredValues storedValues) {
        return new LmdbRowValue(useOutput(output -> write(key, storedValues, output)));
    }

    public static LmdbRowValue read(final ByteBuffer byteBuffer) {
        final int start = byteBuffer.position();
        final int keyLength = byteBuffer.getInt(start);
        final int valueLength = byteBuffer.getInt(start + Integer.BYTES + keyLength);
        final int length = Integer.BYTES + keyLength + Integer.BYTES + valueLength;
        final ByteBuffer slice = byteBuffer.slice(start, length);
        byteBuffer.position(start + length);
        return new LmdbRowValue(slice);
    }

    private void write(final Key key,
                       final StoredValues storedValues,
                       final MyByteBufferOutput output) {
        writeKey(key, output);
        writeValue(storedValues, output);
    }

    private void writeKey(final Key key,
                          final MyByteBufferOutput output) {
        addPart(output, o -> keyFactory.write(key, o));
    }

    public void writeValue(final StoredValues storedValues,
                           final MyByteBufferOutput output) {
        addPart(output, o -> valueReferenceIndex.write(storedValues, o));
    }

    private void addPart(final MyByteBufferOutput output,
                         final Consumer<MyByteBufferOutput> consumer) {
        final int pos = output.getByteBuffer().position();
        output.writeIntDirect(0);
        consumer.accept(output);
        output.flush();
        final ByteBuffer byteBuffer = output.getByteBuffer();
        final int length = byteBuffer.position() - pos - Integer.BYTES;
        byteBuffer.putInt(pos, length);
    }

    public void copyPart(final ByteBuffer byteBuffer,
                         final MyByteBufferOutput output) {
        output.writeIntDirect(byteBuffer.remaining());
        output.writeByteBuffer(byteBuffer);
    }
}
