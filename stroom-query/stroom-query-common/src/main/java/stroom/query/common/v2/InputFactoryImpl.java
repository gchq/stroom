package stroom.query.common.v2;

import stroom.dashboard.expression.v1.ref.InputFactory;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferInput;

import java.nio.ByteBuffer;

public class InputFactoryImpl implements InputFactory {

    @Override
    public Input create(final byte[] bytes) {
        return new Input(bytes);
    }

//    @Override
//    public Input create(final InputStream inputStream) {
//        return new Input(inputStream);
//    }

    @Override
    public UnsafeByteBufferInput createByteBufferInput(final ByteBuffer byteBuffer) {
        return new UnsafeByteBufferInput(byteBuffer);
    }
}
