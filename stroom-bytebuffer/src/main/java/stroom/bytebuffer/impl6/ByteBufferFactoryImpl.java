package stroom.bytebuffer.impl6;

import stroom.bytebuffer.ByteBufferFactory;
import stroom.bytebuffer.ByteBufferSupport;
import stroom.util.io.ByteSize;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.stream.Collectors;

public class ByteBufferFactoryImpl implements ByteBufferFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ByteBufferFactoryImpl.class);

    private static final double LOG2 = Math.log(2);
    static final int MAX_CACHED_BUFFER_SIZE = 8_192;

    // Cache buffers big enough for single integers and above.
    private static final int MIN_CACHED_BUFFER_SIZE = Integer.BYTES;
    private final int minExponent;

    final Pool[] pools;

    public ByteBufferFactoryImpl() {
        LOGGER.debug("Initialising {}", this.getClass().getSimpleName());
        minExponent = getMinExponent(MIN_CACHED_BUFFER_SIZE);
        final int exponent = getExponent(MAX_CACHED_BUFFER_SIZE);
        pools = new Pool[exponent + 1];
        for (int i = minExponent; i < pools.length; i++) {
            pools[i] = new Pool(1000, pow2(i));
        }
    }

    @Override
    public ByteBuffer acquire(final int size) {
        if (size <= MAX_CACHED_BUFFER_SIZE) {
            final int exponent = getExponent(size);
            final Pool pool = pools[exponent];
            ByteBuffer byteBuffer = pool.poll();
            if (byteBuffer != null) {
                if (byteBuffer.capacity() >= size) {
                    byteBuffer.clear();
                    return byteBuffer;
                } else {
                    unmap(byteBuffer);
                }
            }

            final int roundedSize = pow2(exponent);
            return ByteBufferFactory.super.acquire(roundedSize);
        }

        return ByteBufferFactory.super.acquire(size);
    }

    @Override
    public void release(final ByteBuffer byteBuffer) {
        if (byteBuffer.capacity() > MAX_CACHED_BUFFER_SIZE) {
            unmap(byteBuffer);
        } else {
            final int exponent = getExponent(byteBuffer.capacity());
            final Pool pool = pools[exponent];
            if (!pool.offer(byteBuffer)) {
                unmap(byteBuffer);
            }
        }
    }

    int getExponent(final int size) {
        return Math.max(minExponent, getMinExponent(size));
    }

    private static int getMinExponent(int n) {
        return (int) Math.ceil(log2(n));
    }

    public static int pow2(final int exponent) {
        return (int) Math.pow(2, exponent);
    }

    private static double log2(int n) {
        return Math.log(n) / LOG2;
    }

    private void unmap(final ByteBuffer byteBuffer) {
        ByteBufferSupport.unmap(byteBuffer);
    }

    @Override
    public String toString() {
        final int bufferCount = Arrays.stream(pools)
                .filter(Objects::nonNull)
                .mapToInt(Pool::size)
                .sum();
        final String poolInfo = Arrays.stream(pools)
                .filter(Objects::nonNull)
                .filter(queue -> queue.size() > 0)
                .map(queue -> queue.getBufferSize() + ":" + queue.size())
                .collect(Collectors.joining(", "));
        final ByteSize totalByteSize = ByteSize.ofBytes(Arrays.stream(pools)
                .filter(Objects::nonNull)
                .filter(queue -> queue.size() > 0)
                .mapToInt(queue -> queue.bufferSize * queue.size())
                .sum());

        return getClass().getSimpleName()
                + " - Pooled buffer count: " + bufferCount
                + ", pooled size: " + totalByteSize
                + ", pools: {" + poolInfo + "}";
    }

    // --------------------------------------------------------------------------------


    static class Pool {

        private final ArrayBlockingQueue<ByteBuffer> queue;
        private final int bufferSize;

        public Pool(final int capacity, final int bufferSize) {
            queue = new ArrayBlockingQueue<>(capacity);
            this.bufferSize = bufferSize;
        }

        public ByteBuffer poll() {
            return queue.poll();
        }

        public boolean offer(final ByteBuffer byteBuffer) {
            return queue.offer(byteBuffer);
        }

        private int size() {
            return queue.size();
        }

        private int getBufferSize() {
            return bufferSize;
        }
    }
}
