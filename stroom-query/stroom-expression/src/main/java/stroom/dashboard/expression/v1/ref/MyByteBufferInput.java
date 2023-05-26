package stroom.dashboard.expression.v1.ref;

import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferInput;

import java.nio.ByteBuffer;

public class MyByteBufferInput extends UnsafeByteBufferInput {

    public MyByteBufferInput(final ByteBuffer buffer) {
        super(buffer);
    }
}
