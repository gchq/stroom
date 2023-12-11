package stroom.proxy.repo.dao.lmdb.serde;

import java.nio.ByteBuffer;

public interface PooledByteBuffer {

    /**
     * Get the wrapped byte buffer.
     *
     * @return The wrapped byte buffer.
     */
    ByteBuffer get();

    /**
     * Release the wrapped byte buffer back to the pool it came from.
     */
    void release();
}
