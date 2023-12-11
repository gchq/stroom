package stroom.proxy.repo.dao.lmdb.serde;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class NativeIntegerSerde extends IntegerSerde {

    public ByteBuffer serialise(final ByteBuffer byteBuffer, final Integer value) {
        byteBuffer.order(ByteOrder.nativeOrder());
        super.serialise(byteBuffer, value);
        byteBuffer.order(ByteOrder.BIG_ENDIAN);
        return byteBuffer;
    }

    @Override
    public Integer deserialise(final ByteBuffer byteBuffer) {
        byteBuffer.order(ByteOrder.nativeOrder());
        final int result = super.deserialise(byteBuffer);
        byteBuffer.order(ByteOrder.BIG_ENDIAN);
        return result;
    }
}
