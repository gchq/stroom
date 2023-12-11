package stroom.proxy.repo.dao.lmdb.serde;

import stroom.util.concurrent.UncheckedInterruptedException;

import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingDeque;

public class ByteBufferPool {

    private final int size;
    private final LinkedBlockingDeque<ByteBuffer> pool = new LinkedBlockingDeque<>();

    public ByteBufferPool(final int size) {
        this.size = size;
    }

    public PooledByteBuffer createOrBorrowBuffer() {
        final ByteBuffer byteBuffer = getOrCreateBuffer();
        return new PooledByteBuffer() {
            @Override
            public ByteBuffer get() {
                return byteBuffer;
            }

            @Override
            public void release() {
                returnBuffer(byteBuffer);
            }
        };
    }

    private ByteBuffer getOrCreateBuffer() {
        final ByteBuffer byteBuffer = pool.poll();
        if (byteBuffer != null) {
            return byteBuffer;
        }
        return ByteBuffer.allocateDirect(size);
    }

    public void returnBuffer(final ByteBuffer byteBuffer) {
        try {
            pool.put(byteBuffer);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        }
    }
}
