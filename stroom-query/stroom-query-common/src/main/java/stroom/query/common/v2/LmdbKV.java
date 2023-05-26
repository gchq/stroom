package stroom.query.common.v2;

import stroom.bytebuffer.ByteBufferUtils;

import java.nio.ByteBuffer;

class LmdbKV implements LmdbQueueItem {

    private final CurrentDbState currentDbState;
    private final ByteBuffer rowKey;
    private final ByteBuffer rowValue;

    public LmdbKV(final CurrentDbState currentDbState,
                  final ByteBuffer rowKey,
                  final ByteBuffer rowValue) {
        this.currentDbState = currentDbState;
        this.rowKey = rowKey;
        this.rowValue = rowValue;
    }

    public CurrentDbState getCurrentDbState() {
        return currentDbState;
    }

    public ByteBuffer getRowKey() {
        return rowKey;
    }

    public ByteBuffer getRowValue() {
        return rowValue;
    }

    @Override
    public String toString() {
        return "LmdbKV{" +
                "currentDbState=" + currentDbState +
                ", rowKey=" + ByteBufferUtils.byteBufferToString(rowKey) +
                ", rowValue=" + ByteBufferUtils.byteBufferToString(rowValue) +
                '}';
    }
}
