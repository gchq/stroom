package stroom.query.common.v2;

import stroom.bytebuffer.ByteBufferFactory;
import stroom.util.concurrent.CompletableQueue;

public class LmdbWriteQueue extends CompletableQueue<LmdbQueueItem> {

    private final ByteBufferFactory byteBufferFactory;

    public LmdbWriteQueue(final int capacity, final ByteBufferFactory byteBufferFactory) {
        super(capacity);
        this.byteBufferFactory = byteBufferFactory;
    }

    @Override
    protected void destroy(final Object item) {
        if (item instanceof final LmdbKV lmdbKV) {
            byteBufferFactory.release(lmdbKV.getRowKey());
            byteBufferFactory.release(lmdbKV.getRowValue());
        }
    }
}
