package stroom.query.language.functions.ref;

public interface DataWriter extends AutoCloseable {

    void writeByteUnsigned(int value);

    void writeByte(byte value);

    void writeBoolean(boolean value);

    void writeShort(short value);

    void writeInt(int value);

    void writeLong(long value);

    void writeFloat(float value);

    void writeDouble(double value);

    void writeString(String value);

    void writeBytes(byte[] bytes);

    @Override
    void close();
}
