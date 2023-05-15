package stroom.query.common.v2;

import stroom.bytebuffer.ByteBufferUtils;

import java.nio.ByteBuffer;

class LmdbRowValue {

    private final ByteBuffer byteBuffer;

    LmdbRowValue(final ByteBuffer byteBuffer) {
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
