package stroom.dashboard.expression.v1;

import javax.inject.Singleton;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;

@Singleton
public class OutputFactory {
    private static final LinkedBlockingQueue<Pair> pool = new LinkedBlockingQueue<>(1000);

    public Output create() {
        Pair pair = pool.poll();
        if (pair == null) {
            pair = new Pair(null, ByteBuffer.allocateDirect(4096));
        } else {
            final Pair bb = pair;
            bb.clear();
        }

        return new OutputImpl(pair);
    }


    private static class OutputImpl implements Output {
        private ByteBuffer byteBuffer;
        private Pair pair;

        public OutputImpl(final Pair pair) {
            this.byteBuffer = pair.heap;
            this.pair = pair;
        }

        public void writeByte(final byte value) {
            byteBuffer.put(value);
        }

        public void writeBoolean(final boolean value) {
            byteBuffer.put((byte) (value ? 1 : 0));
        }

        public void writeShort(final short value) {
            byteBuffer.putShort(value);
        }

        public void writeInt(final int value) {
            byteBuffer.putInt(value);
        }

        public void writeLong(final long value) {
            byteBuffer.putLong(value);
        }

        public void writeFloat(final float value) {
            byteBuffer.putFloat(value);
        }

        public void writeDouble(final double value) {
            byteBuffer.putDouble(value);
        }

        public void writeString(final String value) {
            final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            byteBuffer.putInt(bytes.length);
            byteBuffer.put(bytes);
        }

        public void writeBytes(final byte[] src, final int offset, final int length) {
            byteBuffer.put(src, offset, length);
        }

        public void writeBytes(final byte[] src) {
            byteBuffer.put(src);
        }

        @Override
        public void close() {
            pool.offer(pair);
            byteBuffer = null;
            pair = null;
        }

        public byte[] toBytes() {
            byteBuffer.flip();
            final byte[] bytes = new byte[byteBuffer.limit()];
            byteBuffer.get(bytes);
            return bytes;
        }

        @Override
        public ByteBuffer toByteBuffer() {
            byteBuffer.flip();

            return byteBuffer;

//            pair.direct.put(byteBuffer);
//            pair.direct.flip();
//
//            return pair.direct;
        }
    }

    private static class Pair {
        private final ByteBuffer direct;
        private final ByteBuffer heap;

        public Pair(final ByteBuffer direct, final ByteBuffer heap) {
            this.direct = direct;
            this.heap = heap;
        }

        void clear() {
//            direct.clear();
            heap.clear();
        }
    }
}
