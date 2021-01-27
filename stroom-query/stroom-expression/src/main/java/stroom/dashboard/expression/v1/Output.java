package stroom.dashboard.expression.v1;

import java.nio.ByteBuffer;

public interface Output extends AutoCloseable {
    void writeByte(final byte value);

    void writeBoolean(final boolean value);

    void writeShort(final short value);

    void writeInt(final int value);

    void writeLong(final long value);

    void writeFloat(final float value);

    void writeDouble(final double value);

    void writeString(final String value);

    void writeBytes(final byte[] src, final int offset, final int length);

    void writeBytes(final byte[] src);

    byte[] toBytes();

    ByteBuffer toByteBuffer();

    @Override
    void close();
}
