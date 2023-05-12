package stroom.dashboard.expression.v1.ref;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class MyByteBufferInput implements AutoCloseable {

    private final ByteBuffer byteBuffer;

    public MyByteBufferInput(final ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }

    public boolean readBoolean() {
        return byteBuffer.get() > 0;
    }

    public byte readByte() {
        return byteBuffer.get();
    }

    public int readByteUnsigned() {
        return byteBuffer.get() & 0xFF;
    }

    public int readInt() {
        return byteBuffer.getInt();
    }

    public long readLong() {
        return byteBuffer.getLong();
    }

    public double readDouble() {
        return byteBuffer.getDouble();
    }

    public float readFloat() {
        return byteBuffer.getFloat();
    }

    public String readString() {
        final int len = byteBuffer.getInt();
        final byte[] bytes = new byte[len];
        byteBuffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
    }
}
