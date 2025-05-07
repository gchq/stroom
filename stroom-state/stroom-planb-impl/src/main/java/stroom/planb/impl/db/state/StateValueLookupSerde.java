package stroom.planb.impl.db.state;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class StateValueLookupSerde {

    private final ByteBuffers byteBuffers;

    public StateValueLookupSerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    public StateValue read(final ByteBuffer byteBuffer) {
        final byte typeId = byteBuffer.get(0);
        final ByteBuffer slice = byteBuffer.slice(Byte.BYTES, byteBuffer.limit() - Byte.BYTES);
        final byte[] valueBytes = ByteBufferUtils.toBytes(slice);
        return new StateValue(typeId, ByteBuffer.wrap(valueBytes));
    }

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
