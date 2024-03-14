package stroom.proxy.repo.dao.lmdb.serde;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.bytebuffer.PooledByteBufferOutput;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferInput;

import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.function.Function;

public class ByteBuffers {

    public static PooledByteBuffer write(final ByteBufferPool byteBufferPool,
                                  final Consumer<PooledByteBufferOutput> consumer) {
        return write(byteBufferPool, 128, consumer);
    }

    public static PooledByteBuffer write(final ByteBufferPool byteBufferPool,
                                  final int size,
                                  final Consumer<PooledByteBufferOutput> consumer) {
        final PooledByteBuffer pooledByteBuffer;
        try (final PooledByteBufferOutput output = new PooledByteBufferOutput(byteBufferPool, size, -1)) {
            consumer.accept(output);
            pooledByteBuffer = output.getPooledByteBuffer();
        }
        pooledByteBuffer.getByteBuffer().flip();
        return pooledByteBuffer;
    }

    public static <T> T read(final ByteBuffer byteBuffer, final Function<Input, T> function) {
        try (final Input input = new UnsafeByteBufferInput(byteBuffer)) {
            return function.apply(input);
        }
    }
}
