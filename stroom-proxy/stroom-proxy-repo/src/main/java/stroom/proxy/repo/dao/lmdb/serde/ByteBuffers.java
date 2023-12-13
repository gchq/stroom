package stroom.proxy.repo.dao.lmdb.serde;

import stroom.bytebuffer.PooledByteBuffer;
import stroom.bytebuffer.PooledByteBufferOutput;
import stroom.bytebuffer.PooledByteBufferOutputFactory;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferInput;
import jakarta.inject.Inject;

import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.function.Function;

public class ByteBuffers {

    private final PooledByteBufferOutputFactory pooledByteBufferOutputFactory;

    @Inject
    ByteBuffers(final PooledByteBufferOutputFactory pooledByteBufferOutputFactory) {
        this.pooledByteBufferOutputFactory = pooledByteBufferOutputFactory;
    }

    public PooledByteBuffer write(final Consumer<PooledByteBufferOutput> consumer) {
        final PooledByteBuffer pooledByteBuffer;
        try (final PooledByteBufferOutput output = pooledByteBufferOutputFactory.create(128)) {
            consumer.accept(output);
            pooledByteBuffer = output.getPooledByteBuffer();
        }
        pooledByteBuffer.getByteBuffer().flip();
        return pooledByteBuffer;
    }

    public <T> T read(final ByteBuffer byteBuffer, final Function<Input, T> function) {
        try (final Input input = new UnsafeByteBufferInput(byteBuffer)) {
            return function.apply(input);
        }
    }
}
