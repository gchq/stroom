package stroom.pipeline.refdata.store.offheapstore;

import java.nio.ByteBuffer;

/**
 * Family of classes for reading/writing unsigned longs from/to bytes.
 */
public interface UnsignedBytes {

    byte[] toBytes(long val);

    /**
     * Puts val as unsigned bytes into arr using a length of len bytes
     */
    void put(byte[] arr, int off, long val);

    /**
     * Puts val as unsigned bytes into destByteBuffer using a length of len bytes
     */
    void put(ByteBuffer destByteBuffer, long val);

    long get(byte[] bytes, int off);

    /**
     * Reads the unsigned bytes from the supplied index and for len bytes.
     * The position of the byteBuffer is unchanged.
     */
    long get(ByteBuffer byteBuffer, int index);

    /**
     * Reads the unsigned bytes for len bytes.
     * The position of the byteBuffer is advanced
     */
    long get(ByteBuffer byteBuffer);

    long getMaxVal();

    /**
     * Treating the content of the passed byteBuffer (from its current position)
     * as an unsigned long, increments the value by one.
     * The byteBuffer's position, limit, marks are unchanged.
     */
    void increment(ByteBuffer byteBuffer);

    /**
     * Treating the content of the passed byteBuffer (from position index)
     * as an unsigned long, increments the value by one.
     * The byteBuffer's position, limit, marks are unchanged.
     */
    void increment(ByteBuffer byteBuffer, int index);

    /**
     * Treating the content of the passed byteBuffer (from its current position)
     * as an unsigned long, decrements the value by one.
     * The byteBuffer's position, limit, marks are unchanged.
     */
    void decrement(ByteBuffer byteBuffer);

    /**
     * Treating the content of the passed byteBuffer (from position index)
     * as an unsigned long, decrements the value by one.
     * The byteBuffer's position, limit, marks are unchanged.
     */
    void decrement(ByteBuffer byteBuffer, int index);

    /**
     * @return The number of bytes that the unsigned value occupies
     */
    int length();

    /**
     * @return The maximum unsigned value that can be written out as bytes.
     */
    long maxValue();
}
