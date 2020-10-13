package stroom.pipeline.refdata.store;

import stroom.pipeline.refdata.ReferenceDataConfig;
import stroom.pipeline.refdata.util.ByteBufferPool;
import stroom.pipeline.refdata.util.ByteBufferPoolImpl4;

import java.util.function.Function;

/**
 * Provides a single point for tests that don't want to use guice
 * to get an instance of a {@link ByteBufferPool}.
 */
public class ByteBufferPoolFactory {

    private final ByteBufferPool byteBufferPool;

    // Impl4 is the current pool du jour based on performance tests
    private final Function<ReferenceDataConfig, ByteBufferPool> poolProvider = ByteBufferPoolImpl4::new;

    public ByteBufferPoolFactory() {
        this.byteBufferPool = poolProvider.apply(new ReferenceDataConfig());
    }

    public ByteBufferPoolFactory(final ReferenceDataConfig referenceDataConfig) {
        this.byteBufferPool = poolProvider.apply(referenceDataConfig);
    }

    public ByteBufferPool getByteBufferPool() {
       return byteBufferPool;
    }
}
