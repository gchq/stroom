package stroom.dashboard.expression.v1.ref;

import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferOutput;

import java.nio.ByteBuffer;

public class MyByteBufferOutput extends UnsafeByteBufferOutput {

    public MyByteBufferOutput(final int bufferSize, final int maxBufferSize) {
        super(bufferSize, maxBufferSize);
    }

    public void writeByteBuffer(final ByteBuffer byteBuffer) {
        final int length = byteBuffer.remaining();
        require(length);
        getByteBuffer().put(byteBuffer);
        position += length;
    }

    public void writeIntDirect(final int i) {
        require(Integer.BYTES);
        getByteBuffer().putInt(i);
        position += Integer.BYTES;
    }
}
