package stroom.pipeline.refdata.store;

import net.openhft.hashing.LongHashFunction;

import java.nio.ByteBuffer;

public class XxHashValueStoreHashAlgorithm implements ValueStoreHashAlgorithm {

    private static final LongHashFunction XX_HASH = LongHashFunction.xx();

    @Override
    public long hash(final ByteBuffer byteBuffer) {
        return XX_HASH.hashBytes(byteBuffer);
    }

    @Override
    public long hash(final String value) {
        return XX_HASH.hashChars(value);
    }
}
