package stroom.pipeline.refdata.store.offheapstore.serdes;

import stroom.pipeline.refdata.store.offheapstore.UnsignedBytes;
import stroom.pipeline.refdata.store.offheapstore.UnsignedBytesInstances;
import stroom.pipeline.refdata.store.offheapstore.UnsignedLong;
import stroom.pipeline.refdata.store.offheapstore.lmdb.serde.Serde;

import java.nio.ByteBuffer;

public class UnsignedLongSerde implements Serde<UnsignedLong> {

    private final int len;
    private final UnsignedBytes unsignedBytes;

    public UnsignedLongSerde(final int len) {
        this.len = len;
        this.unsignedBytes = UnsignedBytesInstances.of(len);
    }

    public UnsignedLongSerde(final int len, final UnsignedBytes unsignedBytes) {
        this.len = len;
        this.unsignedBytes = unsignedBytes;
    }

    @Override
    public UnsignedLong deserialize(final ByteBuffer byteBuffer) {
        UnsignedLong unsignedLong = new UnsignedLong(unsignedBytes.get(byteBuffer), len);
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
    public String toString() {
        return "UnsignedLongSerde{" +
                "len=" + len +
                '}';
    }
}
