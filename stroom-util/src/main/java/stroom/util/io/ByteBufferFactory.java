package stroom.util.io;

import stroom.util.logging.Metrics;

import java.nio.ByteBuffer;

public class ByteBufferFactory {
    public static ByteBuffer allocateDirect(final int capacity) {
        return Metrics.measure("allocateDirect", () -> ByteBuffer.allocateDirect(capacity));
    }

    public static ByteBuffer allocate(final int capacity) {
        return Metrics.measure("allocate", () -> ByteBuffer.allocate(capacity));
    }

    public static ByteBuffer wrap(final byte[] array) {
        return Metrics.measure("wrap", () -> ByteBuffer.wrap(array));
    }

    public static ByteBuffer wrap(final byte[] array,
                                  final int offset,
                                  final int length) {
        return Metrics.measure("wrap2", () -> ByteBuffer.wrap(array, offset, length));
    }
}
