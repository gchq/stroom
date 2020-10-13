package stroom.pipeline.refdata.store;

import java.nio.ByteBuffer;

public interface ValueStoreHashAlgorithm {

    long hash(final ByteBuffer byteBuffer);

    long hash(final String value);
}
