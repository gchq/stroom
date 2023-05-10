package stroom.dashboard.expression.v1.ref;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferInput;

import java.nio.ByteBuffer;

public interface InputFactory {

    Input create(byte[] bytes);

//    Input create(InputStream inputStream);
//
    UnsafeByteBufferInput createByteBufferInput(ByteBuffer byteBuffer);

}
