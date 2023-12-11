package stroom.proxy.repo.dao.lmdb.serde;

import stroom.proxy.repo.dao.lmdb.MyByteBufferOutput;
import stroom.util.concurrent.UncheckedInterruptedException;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferInput;

import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;
import java.util.function.Function;

public class ByteBuffers {

    private static final LinkedBlockingDeque<ByteBuffer> pool = new LinkedBlockingDeque<>();

    private static ByteBuffer getOrCreateBuffer(final int size) {
        final ByteBuffer byteBuffer = pool.poll();
        if (byteBuffer != null) {
            byteBuffer.clear();
            return byteBuffer;
        }
        return ByteBuffer.allocateDirect(size);
    }

    private static void returnBuffer(final ByteBuffer byteBuffer) {
        try {
            pool.put(byteBuffer);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        }
    }

    public static PooledByteBuffer write(final Consumer<MyByteBufferOutput> consumer) {
        final ByteBuffer byteBuffer = ByteBuffers.getOrCreateBuffer(128);
        final ByteBuffer out;
        try (final MyByteBufferOutput output = new MyByteBufferOutput(byteBuffer, -1)) {
            consumer.accept(output);
            output.flush();
            out = output.getByteBuffer().flip();
        }
        return new PooledByteBufferImpl(out);
    }

    public static <T> T read(final ByteBuffer byteBuffer, final Function<Input, T> function) {
        try (final Input input = new UnsafeByteBufferInput(byteBuffer)) {
            return function.apply(input);
        }
    }

    private static class PooledByteBufferImpl implements PooledByteBuffer {

        private final ByteBuffer byteBuffer;

        public PooledByteBufferImpl(final ByteBuffer byteBuffer) {
            this.byteBuffer = byteBuffer;
        }

        @Override
        public ByteBuffer get() {
            return byteBuffer;
        }

        @Override
        public void release() {
            ByteBuffers.returnBuffer(byteBuffer);
        }
    }
}
