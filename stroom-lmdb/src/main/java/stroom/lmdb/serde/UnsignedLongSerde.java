package stroom.lmdb.serde;

import java.nio.ByteBuffer;

public class UnsignedLongSerde implements Serde<UnsignedLong> {

    private final int len;
    private final UnsignedBytes unsignedBytes;

    public UnsignedLongSerde(final int len) {
        this.len = len;
        this.unsignedBytes = UnsignedBytesInstances.ofLength(len);
    }

    public UnsignedLongSerde(final int len, final UnsignedBytes unsignedBytes) {
        if (len != unsignedBytes.length()) {
            throw new RuntimeException("Length mismatch, " + len + " vs " + unsignedBytes.length());
        }
        this.len = len;
        this.unsignedBytes = unsignedBytes;
    }

    @Override
    public UnsignedLong deserialize(final ByteBuffer byteBuffer) {
        final UnsignedLong unsignedLong = new UnsignedLong(unsignedBytes.get(byteBuffer), len);
        byteBuffer.flip();
        return unsignedLong;
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final UnsignedLong unsignedLong) {
        unsignedBytes.put(byteBuffer, unsignedLong.getValue());
        byteBuffer.flip();
    }

    public int getLength() {
        return len;
    }

    @Override
    public int getBufferCapacity() {
        return len;
    }

    @Override
    public String toString() {
        return "UnsignedLongSerde{" +
                "len=" + len +
                '}';
    }
}
