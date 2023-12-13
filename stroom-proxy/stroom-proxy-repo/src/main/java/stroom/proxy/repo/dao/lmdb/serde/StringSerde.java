package stroom.proxy.repo.dao.lmdb.serde;

import stroom.bytebuffer.PooledByteBuffer;
import stroom.lmdb.serde.Serde;

import com.esotericsoftware.kryo.io.Input;
import jakarta.inject.Inject;

import java.nio.ByteBuffer;

public class StringSerde implements Serde<String> {

    private final ByteBuffers byteBuffers;

    @Inject
    StringSerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final String object) {
        throw new UnsupportedOperationException("Not implemented for this serializer");
    }

    @Override
    public PooledByteBuffer serialize(final String value) {
        return byteBuffers.write(output -> output.writeString(value));
    }

    @Override
    public String deserialize(final ByteBuffer byteBuffer) {
        return byteBuffers.read(byteBuffer, Input::readString);
    }
}
