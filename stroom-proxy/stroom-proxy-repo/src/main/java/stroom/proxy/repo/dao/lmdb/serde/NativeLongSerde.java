package stroom.proxy.repo.dao.lmdb.serde;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class NativeLongSerde extends LongSerde {

    public ByteBuffer serialise(final ByteBuffer byteBuffer, final Long value) {
        byteBuffer.order(ByteOrder.nativeOrder());
        super.serialise(byteBuffer, value);
        byteBuffer.order(ByteOrder.BIG_ENDIAN);
        return byteBuffer;
    }

    @Override
    public Long deserialise(final ByteBuffer byteBuffer) {
        byteBuffer.order(ByteOrder.nativeOrder());
        final long result = super.deserialise(byteBuffer);
        byteBuffer.order(ByteOrder.BIG_ENDIAN);
        return result;
    }
}
