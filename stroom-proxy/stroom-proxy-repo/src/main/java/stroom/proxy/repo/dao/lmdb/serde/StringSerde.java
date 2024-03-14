package stroom.proxy.repo.dao.lmdb.serde;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.PooledByteBuffer;

import com.esotericsoftware.kryo.io.Input;

import java.nio.ByteBuffer;

public class StringSerde implements ExtendedSerde<String> {

    @Override
    public void serialize(final ByteBuffer byteBuffer, final String object) {
        throw new UnsupportedOperationException("Not implemented for this serializer");
    }

    @Override
    public PooledByteBuffer serialize(final String value, final ByteBufferPool byteBufferPool) {
        return ByteBuffers.write(byteBufferPool, output -> output.writeString(value));
    }

    @Override
    public String deserialize(final ByteBuffer byteBuffer) {
        return ByteBuffers.read(byteBuffer, Input::readString);
    }
}
