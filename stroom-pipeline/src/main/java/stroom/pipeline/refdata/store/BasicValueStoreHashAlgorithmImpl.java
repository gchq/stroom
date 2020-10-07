package stroom.pipeline.refdata.store;

import stroom.pipeline.refdata.util.ByteBufferUtils;

import java.nio.ByteBuffer;

public class BasicValueStoreHashAlgorithmImpl implements ValueStoreHashAlgorithm {

    @Override
    public long hash(final ByteBuffer byteBuffer) {
        return ByteBufferUtils.basicHashCode(byteBuffer);
    }

    @Override
    public long hash(final String value) {
        int hashCode = value.hashCode();
        return hashCode;
    }
}
