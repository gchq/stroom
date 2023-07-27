package stroom.pipeline.refdata.store;

import stroom.bytebuffer.ByteBufferUtils;

import java.nio.ByteBuffer;
import java.util.Objects;

public class BasicValueStoreHashAlgorithmImpl implements ValueStoreHashAlgorithm {

    private static final long NULL_HASH = ByteBufferUtils.basicHashCode(ByteBuffer.wrap(new byte[0]));

    @Override
    public long hash(final ByteBuffer byteBuffer) {
        Objects.requireNonNull(byteBuffer);
        if (byteBuffer.remaining() == 0) {
            return NULL_HASH;
        } else {
            return ByteBufferUtils.basicHashCode(byteBuffer);
        }
    }
}
