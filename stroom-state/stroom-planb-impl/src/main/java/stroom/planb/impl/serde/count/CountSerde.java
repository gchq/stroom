package stroom.planb.impl.serde.count;

import java.nio.ByteBuffer;

public interface CountSerde<T> {

    void put(ByteBuffer byteBuffer, int position, long value);

    void add(ByteBuffer byteBuffer, int position, long value);

    T get(ByteBuffer byteBuffer);

    long getVal(ByteBuffer byteBuffer);

    void merge(ByteBuffer buffer1,
               ByteBuffer buffer2,
               ByteBuffer output);

    /**
     * @return The number of bytes that the count serde occupies
     */
    int length();
}
