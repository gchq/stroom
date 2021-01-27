package stroom.dashboard.expression.v1;

import stroom.util.logging.Metrics;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class Input implements AutoCloseable {
    private final ByteBuffer byteBuffer;

    public Input(final ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }

    public byte readByte() {
        return Metrics.measure("readByte", () -> byteBuffer.get());
    }

    public boolean readBoolean() {
        return Metrics.measure("readBoolean", () -> byteBuffer.get() != 0);
    }

    public int readShort() {
        return Metrics.measure("readShort", () -> byteBuffer.getShort());
    }

    public int readInt() {
        return Metrics.measure("readInt", () -> byteBuffer.getInt());
    }

    public long readLong() {
        return Metrics.measure("readLong", () -> byteBuffer.getLong());
    }

    public double readFloat() {
        return Metrics.measure("readFloat", () -> byteBuffer.getFloat());
    }

    public double readDouble() {
        return Metrics.measure("readDouble", () -> byteBuffer.getDouble());
    }

    public String readString() {
        return Metrics.measure("readString", () -> {
            final int len = byteBuffer.getInt();
            final byte[] buffer = new byte[len];
            byteBuffer.get(buffer);
            return new String(buffer, StandardCharsets.UTF_8);
        });
    }

    public void readBytes(final byte[] dst, final int offset, final int length) {
        Metrics.measure("readBytes", () -> byteBuffer.get(dst, offset, length));
    }

    public void readBytes(final byte[] dst) {
        Metrics.measure("readBytes2", () -> byteBuffer.get(dst));
    }

    public byte[] readBytes(final int len) {
        return Metrics.measure("readBytes3", () -> {
            final byte[] buffer = new byte[len];
            byteBuffer.get(buffer);
            return buffer;
        });
    }

    public byte[] readAllBytes() {
        return Metrics.measure("readAllBytes", () -> {
            final byte[] buffer = new byte[byteBuffer.limit() - byteBuffer.position()];
            byteBuffer.get(buffer);
            return buffer;
        });
    }

    public boolean end() {
        return Metrics.measure("end", () -> byteBuffer.position() == byteBuffer.limit());
    }

    @Override
    public void close() {
    }
}
