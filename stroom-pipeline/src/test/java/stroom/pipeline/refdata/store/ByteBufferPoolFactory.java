package stroom.pipeline.refdata.store;

import stroom.pipeline.refdata.util.ByteBufferPool;
import stroom.pipeline.refdata.util.ByteBufferPoolImpl4;

/**
 * Provides a single point for tests that don't want to use guice
 * to get an instance of a {@link ByteBufferPool}.
 */
public class ByteBufferPoolFactory {

    private final ByteBufferPool byteBufferPool;

    public ByteBufferPoolFactory() {
        this.byteBufferPool = new ByteBufferPoolImpl4();
    }

    public ByteBufferPool getByteBufferPool() {
       return byteBufferPool;
    }
}
