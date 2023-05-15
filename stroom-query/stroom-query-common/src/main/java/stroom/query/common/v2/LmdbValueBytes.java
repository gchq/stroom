package stroom.query.common.v2;

import stroom.dashboard.expression.v1.ref.ErrorConsumer;
import stroom.dashboard.expression.v1.ref.InputFactory;
import stroom.dashboard.expression.v1.ref.OutputFactory;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferInput;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferOutput;

import java.nio.ByteBuffer;

record LmdbValueBytes(byte[] fullKeyBytes, byte[] valueBytes) {

    public static LmdbValueBytes create(final InputFactory inputFactory, final ByteBuffer byteBuffer) {
        try (final UnsafeByteBufferInput input = inputFactory.createByteBufferInput(byteBuffer.slice())) {
            return create(input);
        }
    }

    public static LmdbValueBytes create(final Input input) {
        final int keyLength = input.readInt();
        final byte[] fullKeyBytes = input.readBytes(keyLength);
        final int valueLength = input.readInt();
        final byte[] valueBytes = input.readBytes(valueLength);
        return new LmdbValueBytes(fullKeyBytes, valueBytes);
    }

    public void write(final Output output) {
        output.writeInt(fullKeyBytes.length);
        output.writeBytes(fullKeyBytes);
        output.writeInt(valueBytes.length);
        output.writeBytes(valueBytes);
    }

    public ByteBuffer toByteBuffer(final OutputFactory outputFactory, final ErrorConsumer errorConsumer) {
        try (final UnsafeByteBufferOutput output = outputFactory
                .createByteBufferOutput(calculateRequiredCapacity(), errorConsumer)) {
            write(output);
            output.flush();
            return output.getByteBuffer().flip();
        }
    }

    private int calculateRequiredCapacity() {
        return Integer.BYTES +
                fullKeyBytes.length +
                Integer.BYTES +
                valueBytes.length;
    }
}
