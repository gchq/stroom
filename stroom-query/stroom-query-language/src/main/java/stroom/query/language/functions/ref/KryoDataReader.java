package stroom.query.language.functions.ref;

import com.esotericsoftware.kryo.io.Input;

public class KryoDataReader implements DataReader, AutoCloseable {

    private final Input input;

    public KryoDataReader(final Input input) {
        this.input = input;
    }

    @Override
    public int readByteUnsigned() {
        return input.readByteUnsigned();
    }

    @Override
    public byte readByte() {
        return input.readByte();
    }

    @Override
    public boolean readBoolean() {
        return input.readBoolean();
    }

    @Override
    public short readShort() {
        return input.readShort();
    }

    @Override
    public int readInt() {
        return input.readInt();
    }

    @Override
    public long readLong() {
        return input.readLong();
    }

    @Override
    public float readFloat() {
        return input.readFloat();
    }

    @Override
    public double readDouble() {
        return input.readDouble();
    }

    @Override
    public String readString() {
        return input.readString();
    }

    @Override
    public byte[] readBytes() {
        final int length = input.readInt();
        final byte[] bytes = new byte[length];
        input.readBytes(bytes);
        return bytes;
    }

    @Override
    public void close() {
        input.close();
    }
}
