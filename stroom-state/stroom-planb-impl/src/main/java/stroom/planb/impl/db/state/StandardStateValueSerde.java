package stroom.planb.impl.db.state;

import stroom.bytebuffer.impl6.ByteBuffers;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class StandardStateValueSerde implements StateValueSerde {

    private final ByteBuffers byteBuffers;

    public StandardStateValueSerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    @Override
    public StateValue read(final ByteBuffer byteBuffer) {
        final byte typeId = byteBuffer.get(0);
        final ByteBuffer slice = byteBuffer.slice(Byte.BYTES, byteBuffer.limit() - Byte.BYTES);
        return new StateValue(typeId, slice);
    }

    @Override
    public void write(final StateValue value,
                      final Consumer<ByteBuffer> consumer) {
        byteBuffers.use(Byte.BYTES + value.getByteBuffer().limit(), valueByteBuffer -> {
            valueByteBuffer.put(value.getTypeId());
            valueByteBuffer.put(value.getByteBuffer());
            valueByteBuffer.flip();
            consumer.accept(valueByteBuffer);
        });
    }
}
