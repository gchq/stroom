package stroom.query.common.v2;

import stroom.bytebuffer.ByteBufferUtils;

import java.nio.ByteBuffer;
import java.util.Arrays;

class LmdbRowValue {

    private final ByteBuffer byteBuffer;

    LmdbRowValue(final ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }

    ByteBuffer getByteBuffer() {
        return this.byteBuffer.slice();
    }

    ByteBuffer getKey() {
        final int pos = byteBuffer.position();
        final int keyLength = byteBuffer.getInt(pos);
        return byteBuffer.slice(pos + Integer.BYTES, keyLength);
    }

    ByteBuffer getValue() {
        int pos = byteBuffer.position();
        int keyLength = byteBuffer.getInt(pos);
        final int valueLength = byteBuffer.getInt(pos + Integer.BYTES + keyLength);
        return byteBuffer.slice(pos + Integer.BYTES + keyLength + Integer.BYTES, valueLength);
    }

    @Override
    public String toString() {
        return "LmdbRowValue{" +
                "keyBytes=" + Arrays.toString(ByteBufferUtils.toBytes(getKey().slice())) +
                ", valueBytes=" + Arrays.toString(ByteBufferUtils.toBytes(getValue().slice())) +
                '}';
    }
}
