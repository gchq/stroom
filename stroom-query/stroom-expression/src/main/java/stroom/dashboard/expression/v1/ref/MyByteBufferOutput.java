package stroom.dashboard.expression.v1.ref;

import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.unsafe.UnsafeUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class MyByteBufferOutput implements AutoCloseable {

    ByteBuffer byteBuffer;

    public MyByteBufferOutput(final int bufferSize) {
        byteBuffer = ByteBuffer.allocateDirect(bufferSize);
    }

    public void writeByteBuffer(final ByteBuffer in) {
        final int length = byteBuffer.remaining();
        require(length);
        byteBuffer.put(in);
    }

    public void writeBoolean(final boolean b) {
        require(Byte.BYTES);
        byteBuffer.put((byte) (b ? 1 : 0));
    }

    public void writeByte(final int b) {
        require(Byte.BYTES);
        byteBuffer.put((byte) b);
    }

    public void writeByte(final byte b) {
        require(Byte.BYTES);
        byteBuffer.put(b);
    }

    public void writeInt(final int i) {
        require(Integer.BYTES);
        byteBuffer.putInt(i);
    }

    public void writeLong(final long l) {
        require(Long.BYTES);
        byteBuffer.putLong(l);
    }

    public void writeDouble(final double d) {
        require(Double.BYTES);
        byteBuffer.putDouble(d);
    }

    public void writeFloat(final float f) {
        require(Float.BYTES);
        byteBuffer.putFloat(f);
    }

    public void writeString(final String s) {
        final byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        require(Integer.BYTES + bytes.length);
        byteBuffer.putInt(bytes.length);
        byteBuffer.put(bytes);
    }

    protected void require(int required) throws KryoException {
        int position = byteBuffer.position();
        int capacity = byteBuffer.capacity();
        if (byteBuffer.capacity() - byteBuffer.position() < required) {
            if (capacity == 0) {
                capacity = 16;
            }
            do {
                capacity = capacity * 2;
            } while (capacity - position < required);
            ByteBuffer newBuffer = !byteBuffer.isDirect()
                    ? ByteBuffer.allocate(capacity)
                    : ByteBuffer.allocateDirect(capacity);
            byteBuffer.position(0);
            byteBuffer.limit(position);
            newBuffer.put(byteBuffer);
            newBuffer.order(byteBuffer.order());
            byteBuffer = newBuffer;

            // TODO : return to pool????
            UnsafeUtil.dispose(byteBuffer);
        }
    }

    public void flush() {

    }

    @Override
    public void close() {

    }

    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }
}
