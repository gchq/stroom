package stroom.pipeline.refdata.store;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public interface ValueStoreHashAlgorithm {

    long hash(final ByteBuffer byteBuffer);

    default long hash(final String value) {
        Objects.requireNonNull(value);
        // Hash strings in their byte form, so we can compare hashes that were done
        // on strings and on byte buffers
        return hash(StandardCharsets.UTF_8.encode(value));
    }
}
