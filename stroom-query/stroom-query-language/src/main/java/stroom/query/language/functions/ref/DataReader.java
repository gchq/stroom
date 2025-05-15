package stroom.query.language.functions.ref;

import java.nio.ByteBuffer;

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

    ByteBuffer read();

    @Override
    void close();
}
