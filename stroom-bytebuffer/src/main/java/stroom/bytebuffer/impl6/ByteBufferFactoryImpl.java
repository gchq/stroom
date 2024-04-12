package stroom.bytebuffer.impl6;

import stroom.bytebuffer.ByteBufferSupport;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

public class ByteBufferFactoryImpl extends ByteBufferFactory {

    private static final double LOG2 = Math.log(2);
    private static final int MAX_CACHED_BUFFER_SIZE = 1024;
    private static final int MIN_CACHED_BUFFER_SIZE = Integer.BYTES; // Cache buffers big enough for single integers and above.
    private final int minExponent;

    private final ArrayBlockingQueue<ByteBuffer>[] buffers;

    @SuppressWarnings("unchecked")
    public ByteBufferFactoryImpl() {
        minExponent = getMinExponent(MIN_CACHED_BUFFER_SIZE);
        final int exponent = getExponent(MAX_CACHED_BUFFER_SIZE);
        buffers = new ArrayBlockingQueue[exponent + 1];
        for (int i = minExponent; i < buffers.length; i++) {
            buffers[i] = new ArrayBlockingQueue<>(1000);
        }
    }

    @Override
    public ByteBuffer acquire(final int size) {
        if (size <= MAX_CACHED_BUFFER_SIZE) {
            final int exponent = getExponent(size);
            final ArrayBlockingQueue<ByteBuffer> queue = buffers[exponent];
            ByteBuffer byteBuffer = queue.poll();
            if (byteBuffer != null) {
                if (byteBuffer.capacity() >= size) {
                    byteBuffer.clear();
                    return byteBuffer;
                } else {
                    unmap(byteBuffer);
                }
            }

            final int roundedSize = pow2(exponent);
            return super.acquire(roundedSize);
        }

        return super.acquire(size);
    }

    @Override
    public void release(final ByteBuffer byteBuffer, final boolean destroy) {
        if (byteBuffer.capacity() > MAX_CACHED_BUFFER_SIZE) {
            unmap(byteBuffer);
        } else {
            final int exponent = getExponent(byteBuffer.capacity());
            final ArrayBlockingQueue<ByteBuffer> queue = buffers[exponent];
            if (!queue.offer(byteBuffer)) {
                unmap(byteBuffer);
            }
        }
    }

    private int getExponent(final int size) {
        return Math.max(minExponent, getMinExponent(size));
    }

    public static int getMinExponent(int n) {
        return (int) Math.ceil(log2(n));
    }

    public static int pow2(final int exponent) {
        return (int) Math.pow(2, exponent);
    }

    public static double log2(int n) {
        return Math.log(n) / LOG2;
    }

    private void unmap(final ByteBuffer byteBuffer) {
        ByteBufferSupport.unmap(byteBuffer);
    }
}