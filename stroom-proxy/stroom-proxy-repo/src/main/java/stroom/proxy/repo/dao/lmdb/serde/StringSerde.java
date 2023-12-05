package stroom.proxy.repo.dao.lmdb.serde;

import stroom.proxy.repo.dao.lmdb.MyByteBufferOutput;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferInput;

import java.nio.ByteBuffer;

public class StringSerde implements Serde<String> {

    @Override
    public ByteBuffer serialise(final String value) {
        final ByteBuffer byteBuffer;
        try (final MyByteBufferOutput output = new MyByteBufferOutput(100, -1)) {
            output.writeString(value);
            output.flush();
            byteBuffer = output.getByteBuffer().flip();
        }
        return byteBuffer;
    }

    @Override
    public String deserialise(final ByteBuffer byteBuffer) {
        try (final Input input = new UnsafeByteBufferInput(byteBuffer.duplicate())) {
            return input.readString();
        }
    }
}
