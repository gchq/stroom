package stroom.pipeline.refdata.store;

import net.openhft.hashing.LongHashFunction;

import java.nio.ByteBuffer;
import java.util.Objects;

public class XxHashValueStoreHashAlgorithm implements ValueStoreHashAlgorithm {

    // NOTE if this hash function is changed then it will likely break ref data
    // as that stores hash values in LMDB.
    private static final LongHashFunction XX_HASH = LongHashFunction.xx();
    private static final long NULL_HASH = XX_HASH.hashVoid();

    @Override
    public long hash(final ByteBuffer byteBuffer) {
        Objects.requireNonNull(byteBuffer);
        if (byteBuffer.remaining() == 0) {
            return NULL_HASH;
        } else {
            return XX_HASH.hashBytes(byteBuffer);
        }
    }
}
