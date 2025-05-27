package stroom.query.language.functions.ref;

public interface DataReader extends AutoCloseable {

    int readByteUnsigned();

    byte readByte();

    boolean readBoolean();

    short readShort();

    int readInt();

    long readLong();

    float readFloat();

    double readDouble();

    String readString();

    byte[] readBytes();

    @Override
    void close();
}
