package stroom.query.language.functions.ref;

import com.esotericsoftware.kryo.io.Output;

public class KryoDataWriter implements DataWriter {

    private final Output output;

    public KryoDataWriter(final Output output) {
        this.output = output;
    }

    @Override
    public void writeByteUnsigned(final int value) {
        output.writeByte(value);
    }

    @Override
    public void writeByte(final byte value) {
        output.writeByte(value);
    }

    @Override
    public void writeBoolean(final boolean value) {
        output.writeBoolean(value);
    }

    @Override
    public void writeShort(final short value) {
        output.writeShort(value);
    }

    @Override
    public void writeInt(final int value) {
        output.writeInt(value);
    }

    @Override
    public void writeLong(final long value) {
        output.writeLong(value);
    }

    @Override
    public void writeFloat(final float value) {
        output.writeFloat(value);
    }

    @Override
    public void writeDouble(final double value) {
        output.writeDouble(value);
    }

    @Override
    public void writeString(final String value) {
        output.writeString(value);
    }

    @Override
    public void writeBytes(final byte[] bytes) {
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    @Override
    public void close() {
        output.close();
    }
}
