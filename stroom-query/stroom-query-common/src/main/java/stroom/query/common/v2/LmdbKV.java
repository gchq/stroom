package stroom.query.common.v2;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.lmdb2.KV;

import java.nio.ByteBuffer;

public class LmdbKV extends KV<ByteBuffer, ByteBuffer> implements LmdbQueueItem {

    private final CurrentDbState currentDbState;

    public LmdbKV(final CurrentDbState currentDbState,
                  final ByteBuffer rowKey,
                  final ByteBuffer rowValue) {
        super(rowKey, rowValue);
        this.currentDbState = currentDbState;
    }

    public CurrentDbState getCurrentDbState() {
        return currentDbState;
    }

    @Override
    public String toString() {
        return "LmdbKV{" +
               "currentDbState=" + currentDbState +
               ", rowKey=" + ByteBufferUtils.byteBufferInfo(key()) +
               ", rowValue=" + ByteBufferUtils.byteBufferInfo(val()) +
               '}';
    }
}
