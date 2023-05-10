package stroom.query.common.v2;

import stroom.bytebuffer.ByteBufferUtils;

import java.nio.ByteBuffer;

class LmdbRowKey {

    private final ByteBuffer byteBuffer;

    LmdbRowKey(final ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }

    ByteBuffer getByteBuffer() {
        return this.byteBuffer;
    }

    @Override
    public String toString() {
        return ByteBufferUtils.byteBufferToString(byteBuffer);
    }
}
