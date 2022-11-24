package stroom.query.common.v2;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferInput;

import java.io.InputStream;
import java.nio.ByteBuffer;

public class InputFactory {

    public Input create(final byte[] bytes) {
        return new Input(bytes);
    }

    public Input create(final InputStream inputStream) {
        return new Input(inputStream);
    }

    public UnsafeByteBufferInput createByteBufferInput(final ByteBuffer byteBuffer) {
        return new UnsafeByteBufferInput(byteBuffer);
    }
}
