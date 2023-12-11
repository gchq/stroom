package stroom.proxy.repo.dao.lmdb.serde;

import com.esotericsoftware.kryo.io.Input;

import java.nio.ByteBuffer;

public class StringSerde implements Serde<String> {

    @Override
    public PooledByteBuffer serialise(final String value) {
        return ByteBuffers.write(output -> output.writeString(value));
    }

    @Override
    public String deserialise(final ByteBuffer byteBuffer) {
        return ByteBuffers.read(byteBuffer, Input::readString);
    }
}
