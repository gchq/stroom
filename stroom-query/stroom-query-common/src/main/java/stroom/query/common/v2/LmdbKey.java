package stroom.query.common.v2;

import stroom.bytebuffer.ByteBufferUtils;

import net.openhft.hashing.LongHashFunction;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.Supplier;

class LmdbKey {

    private static final int HASH_SIZE = Long.BYTES * 3;
    private static final int DEPTH_INDEX = 0;
    private static final int PARENT_HASH_INDEX = Integer.BYTES;
    private static final int HASH_INDEX = PARENT_HASH_INDEX + HASH_SIZE;
    private static final int HASH_INDEX_PART_ONE = HASH_INDEX;
    private static final int HASH_INDEX_PART_TWO = HASH_INDEX + Long.BYTES;
    private static final int HASH_INDEX_PART_THREE = HASH_INDEX_PART_TWO + Long.BYTES;
    private static final int UNIQUE_INDEX = HASH_INDEX_PART_THREE;
    private static final int GROUP_INDEX = HASH_INDEX + HASH_SIZE;
    private static final int KEY_SIZE = GROUP_INDEX + 1;
    private static final int KEY_STEM_SIZE = Integer.BYTES + HASH_SIZE;

    private final ByteBuffer byteBuffer;

    LmdbKey(final ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }

    /**
     * Make a unique version of the row key for non grouped keys
     */
    void makeUnique(final Supplier<Long> uniqueLongSupplier) {
        byteBuffer.putLong(UNIQUE_INDEX, uniqueLongSupplier.get());
    }

    boolean isGroup() {
        return byteBuffer.get(GROUP_INDEX) != 0;
    }

    ByteBuffer getByteBuffer() {
        return this.byteBuffer;
    }

    @Override
    public String toString() {
        final int depth = byteBuffer.getInt(DEPTH_INDEX);
        final byte[] parentHash = ByteBufferUtils.toBytes(byteBuffer, PARENT_HASH_INDEX, HASH_SIZE);
        final byte[] hash = ByteBufferUtils.toBytes(byteBuffer, HASH_INDEX, HASH_SIZE);
        final boolean group = isGroup();
        return "RowKey{" +
                "depth=" + depth +
                ", parentHash=" + Arrays.toString(parentHash) +
                ", hash=" + Arrays.toString(hash) +
                ", group=" + group +
                '}';
    }

    private static void addKeyHash(final ByteBuffer byteBuffer, final byte[] key) {
        byteBuffer.putLong(LongHashFunction.xx().hashBytes(key));
        byteBuffer.putLong(LongHashFunction.xx().hashBytes(invert(key)));
        byteBuffer.putLong(0L);
    }

    private static void addUniqueId(final ByteBuffer byteBuffer, final long uniqueId) {
        byteBuffer.putLong(0L);
        byteBuffer.putLong(0L);
        byteBuffer.putLong(uniqueId);
    }

    private static byte[] invert(byte[] array) {
        final byte[] inverted = Arrays.copyOf(array, array.length);
        for (int i = 0; i < inverted.length / 2; i++) {
            byte temp = inverted[i];
            inverted[i] = inverted[inverted.length - 1 - i];
            inverted[inverted.length - 1 - i] = temp;
        }
        return inverted;
    }

    static ByteBuffer createKeyStem(final int depth,
                                    final Key parentKey) {
        final byte[] keyBytes = parentKey.getBytes();
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(KEY_STEM_SIZE);
        byteBuffer.putInt(depth);
        addKeyHash(byteBuffer, keyBytes);
        byteBuffer.flip();
        return byteBuffer;
    }

    static class Builder {

        private int depth;
        private LmdbKey parentRowKey;
        private byte[] keyBytes;
        private boolean group;
        private Long uniqueId;

        Builder depth(final int depth) {
            this.depth = depth;
            return this;
        }

        Builder parentRowKey(final LmdbKey parentRowKey) {
            this.parentRowKey = parentRowKey;
            return this;
        }

        Builder keyBytes(final byte[] keyBytes) {
            this.keyBytes = keyBytes;
            return this;
        }

        Builder group(final boolean group) {
            this.group = group;
            return this;
        }

        Builder uniqueId(final Long uniqueId) {
            this.uniqueId = uniqueId;
            return this;
        }

        LmdbKey build() {
            final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(KEY_SIZE);
            byteBuffer.putInt(depth);

            // Add the hash from the parent row key.
            if (parentRowKey != null) {
                final ByteBuffer parentByteBuffer = parentRowKey.byteBuffer;
                byteBuffer.putLong(parentByteBuffer.getLong(HASH_INDEX_PART_ONE));
                byteBuffer.putLong(parentByteBuffer.getLong(HASH_INDEX_PART_TWO));
                byteBuffer.putLong(parentByteBuffer.getLong(HASH_INDEX_PART_THREE));
            } else {
                addUniqueId(byteBuffer, 0L);
            }

            if (uniqueId != null) {
                addUniqueId(byteBuffer, uniqueId);
            } else {
                addKeyHash(byteBuffer, keyBytes);
            }
            byteBuffer.put(group
                    ? (byte) 1
                    : (byte) 0);
            return new LmdbKey(byteBuffer.flip());
        }
    }
}
