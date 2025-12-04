/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

    public <R> R use(final int size, final Function<ByteBuffer, R> function) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(size);
        try {
            return function.apply(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public void use(final int size, final Consumer<ByteBuffer> consumer) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(size);
        try {
            consumer.accept(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public <R> R useByte(final byte b, final Function<ByteBuffer, R> function) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Byte.BYTES);
        try {
            byteBuffer.put(b);
            byteBuffer.flip();
            return function.apply(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public void useByte(final byte b, final Consumer<ByteBuffer> consumer) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Byte.BYTES);
        try {
            byteBuffer.put(b);
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public <R> R useChar(final char c, final Function<ByteBuffer, R> function) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Character.BYTES);
        try {
            byteBuffer.putChar(c);
            byteBuffer.flip();
            return function.apply(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public void useChar(final char c, final Consumer<ByteBuffer> consumer) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Character.BYTES);
        try {
            byteBuffer.putChar(c);
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public <R> R useShort(final short s, final Function<ByteBuffer, R> function) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Short.BYTES);
        try {
            byteBuffer.putShort(s);
            byteBuffer.flip();
            return function.apply(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public void useShort(final short s, final Consumer<ByteBuffer> consumer) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Short.BYTES);
        try {
            byteBuffer.putShort(s);
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public <R> R useInt(final int i, final Function<ByteBuffer, R> function) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Integer.BYTES);
        try {
            byteBuffer.putInt(i);
            byteBuffer.flip();
            return function.apply(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public void useInt(final int i, final Consumer<ByteBuffer> consumer) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Integer.BYTES);
        try {
            byteBuffer.putInt(i);
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public <R> R useLong(final long l, final Function<ByteBuffer, R> function) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Long.BYTES);
        try {
            byteBuffer.putLong(l);
            byteBuffer.flip();
            return function.apply(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public void useLong(final long l, final Consumer<ByteBuffer> consumer) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Long.BYTES);
        try {
            byteBuffer.putLong(l);
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public <R> R useFloat(final float f, final Function<ByteBuffer, R> function) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Float.BYTES);
        try {
            byteBuffer.putFloat(f);
            byteBuffer.flip();
            return function.apply(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public void useFloat(final float f, final Consumer<ByteBuffer> consumer) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Float.BYTES);
        try {
            byteBuffer.putFloat(f);
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public <R> R useDouble(final double d, final Function<ByteBuffer, R> function) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Double.BYTES);
        try {
            byteBuffer.putDouble(d);
            byteBuffer.flip();
            return function.apply(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public void useDouble(final double d, final Consumer<ByteBuffer> consumer) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Double.BYTES);
        try {
            byteBuffer.putDouble(d);
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public <R> R useBytes(final byte[] bytes, final Function<ByteBuffer, R> function) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(bytes.length);
        try {
            byteBuffer.put(bytes);
            byteBuffer.flip();
            return function.apply(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public void useBytes(final byte[] bytes, final Consumer<ByteBuffer> consumer) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(bytes.length);
        try {
            byteBuffer.put(bytes);
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public <R> R useCopy(final ByteBuffer in, final Function<ByteBuffer, R> function) {
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(in.remaining());
        try {
            byteBuffer.put(in);
            byteBuffer.flip();
            return function.apply(byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    public void useCopy(final ByteBuffer in, final Consumer<ByteBuffer> consumer) {
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
