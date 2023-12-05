package stroom.proxy.repo.dao.lmdb.serde;

import java.nio.ByteBuffer;

public class IntegerSerde implements Serde<Integer> {

    @Override
    public ByteBuffer serialise(final Integer value) {
        final ByteBuffer hashByteBuffer = ByteBuffer.allocateDirect(Integer.BYTES);
        hashByteBuffer.putInt(value);
        hashByteBuffer.flip();
        return hashByteBuffer;
    }

    @Override
    public Integer deserialise(final ByteBuffer byteBuffer) {
        return byteBuffer.getInt();
    }
}
