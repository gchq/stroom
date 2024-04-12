package stroom.query.language.functions.ref;

public interface DataReader extends AutoCloseable {

    int readByteUnsigned();

    byte readByte();

    boolean readBoolean();

    int readInt();

    long readLong();

    float readFloat();

    double readDouble();

    String readString();

    @Override
    void close();
}
