package stroom.bytebuffer.impl6;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.function.Function;

@Singleton
public class ByteBuffers {

    private final ByteBufferFactory byteBufferFactory;

    @Inject
    public ByteBuffers(final ByteBufferFactory byteBufferFactory) {
        this.byteBufferFactory = byteBufferFactory;
    }

    public <R> R use(int size, Function<ByteBuffer, R> function) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(size);
        try {
            return function.apply(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public void use(int size, Consumer<ByteBuffer> consumer) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(size);
        try {
            consumer.accept(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public <R> R useByte(byte b, Function<ByteBuffer, R> function) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Byte.BYTES);
        try {
            byteBuffer.put(b);
            byteBuffer.flip();
            return function.apply(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public void useByte(byte b, Consumer<ByteBuffer> consumer) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Byte.BYTES);
        try {
            byteBuffer.put(b);
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public <R> R useChar(char c, Function<ByteBuffer, R> function) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Character.BYTES);
        try {
            byteBuffer.putChar(c);
            byteBuffer.flip();
            return function.apply(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public void useChar(char c, Consumer<ByteBuffer> consumer) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Character.BYTES);
        try {
            byteBuffer.putChar(c);
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public <R> R useShort(short s, Function<ByteBuffer, R> function) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Short.BYTES);
        try {
            byteBuffer.putShort(s);
            byteBuffer.flip();
            return function.apply(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public void useShort(short s, Consumer<ByteBuffer> consumer) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Short.BYTES);
        try {
            byteBuffer.putShort(s);
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public <R> R useInt(int i, Function<ByteBuffer, R> function) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Integer.BYTES);
        try {
            byteBuffer.putInt(i);
            byteBuffer.flip();
            return function.apply(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public void useInt(int i, Consumer<ByteBuffer> consumer) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Integer.BYTES);
        try {
            byteBuffer.putInt(i);
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public <R> R useLong(long l, Function<ByteBuffer, R> function) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Long.BYTES);
        try {
            byteBuffer.putLong(l);
            byteBuffer.flip();
            return function.apply(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public void useLong(long l, Consumer<ByteBuffer> consumer) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Long.BYTES);
        try {
            byteBuffer.putLong(l);
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public <R> R useFloat(float f, Function<ByteBuffer, R> function) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Float.BYTES);
        try {
            byteBuffer.putFloat(f);
            byteBuffer.flip();
            return function.apply(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public void useFloat(float f, Consumer<ByteBuffer> consumer) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Float.BYTES);
        try {
            byteBuffer.putFloat(f);
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public <R> R useDouble(double d, Function<ByteBuffer, R> function) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Double.BYTES);
        try {
            byteBuffer.putDouble(d);
            byteBuffer.flip();
            return function.apply(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public void useDouble(double d, Consumer<ByteBuffer> consumer) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Double.BYTES);
        try {
            byteBuffer.putDouble(d);
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public <R> R useBytes(byte[] bytes, Function<ByteBuffer, R> function) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(bytes.length);
        try {
            byteBuffer.put(bytes);
            byteBuffer.flip();
            return function.apply(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public void useBytes(byte[] bytes, Consumer<ByteBuffer> consumer) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(bytes.length);
        try {
            byteBuffer.put(bytes);
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public <R> R useCopy(ByteBuffer in, Function<ByteBuffer, R> function) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(in.remaining());
        try {
            byteBuffer.put(in);
            byteBuffer.flip();
            return function.apply(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public void useCopy(ByteBuffer in, Consumer<ByteBuffer> consumer) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(in.remaining());
        try {
            byteBuffer.put(in);
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }
}
