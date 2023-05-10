package stroom.query.common.v2;

import stroom.query.api.v2.TimeFilter;

import org.lmdbjava.KeyRange;

import java.nio.ByteBuffer;

public class LmdbRowKeyFactoryFactory {

    public static final ByteBuffer DB_STATE_KEY = ByteBuffer.allocateDirect(1);

    static {
        DB_STATE_KEY.put((byte) -1);
        DB_STATE_KEY.flip();
    }

    public static final KeyRange<ByteBuffer> DB_STATE_KEY_RANGE = KeyRange.closed(DB_STATE_KEY, DB_STATE_KEY);
    public static final KeyRange<ByteBuffer> ALL = KeyRange.all();

    private LmdbRowKeyFactoryFactory() {
        // Non instantiable.
    }

    public static LmdbRowKeyFactory create(final KeyFactory keyFactory,
                                           final KeyFactoryConfig keyFactoryConfig,
                                           final CompiledDepths compiledDepths) {
        final boolean flat = compiledDepths.getMaxDepth() == 0 &&
                compiledDepths.getMaxGroupDepth() <= compiledDepths.getMaxDepth();
        if (flat) {
            if (keyFactoryConfig.addTimeToKey()) {
                if (compiledDepths.hasGroup()) {
                    return new FlatTimeGroupedLmdbRowKeyFactory();
                } else {
                    return new FlatTimeUngroupedLmdbRowKeyFactory(keyFactory);
                }
            } else {
                if (compiledDepths.hasGroup()) {
                    return new FlatGroupedLmdbRowKeyFactory();
                } else {
                    return new FlatUngroupedLmdbRowKeyFactory(keyFactory);
                }
            }
        } else {
            if (keyFactoryConfig.addTimeToKey()) {
                return new NestedTimeGroupedLmdbRowKeyFactory(keyFactory, compiledDepths);

            } else {
                return new NestedGroupedLmdbRowKeyFactory(keyFactory, compiledDepths);
            }
        }
    }

    public static KeyRange<ByteBuffer> all() {
        return ALL;
    }

    /**
     * Creates a flat group key. <GROUP_HASH>
     */
    private static class FlatGroupedLmdbRowKeyFactory implements LmdbRowKeyFactory {

        private static final int KEY_LENGTH = Long.BYTES;

        @Override
        public LmdbRowKey create(final int depth,
                                 final long parentGroupHash,
                                 final long groupHash,
                                 final long timeMs) {
            final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(KEY_LENGTH);
            byteBuffer.putLong(groupHash);
            return new LmdbRowKey(byteBuffer.flip());
        }

        @Override
        public LmdbRowKey makeUnique(final LmdbRowKey rowKey) {
            return rowKey;
        }

        @Override
        public boolean isGroup(final LmdbRowKey rowKey) {
            return true;
        }

        @Override
        public KeyRange<ByteBuffer> createChildKeyRange(final Key parentKey) {
            return all();
        }

        @Override
        public KeyRange<ByteBuffer> createChildKeyRange(final Key parentKey, final TimeFilter timeFilter) {
            if (timeFilter != null) {
                throw new RuntimeException("Time filtering is not supported by this key factory");
            }
            return all();
        }
    }

    /**
     * Creates a flat unique key. <UNIQUE_ID>
     */
    private static class FlatUngroupedLmdbRowKeyFactory implements LmdbRowKeyFactory {

        private static final int KEY_LENGTH = Long.BYTES;

        private final KeyFactory keyFactory;

        public FlatUngroupedLmdbRowKeyFactory(final KeyFactory keyFactory) {
            this.keyFactory = keyFactory;
        }

        @Override
        public LmdbRowKey create(final int depth,
                                 final long parentGroupHash,
                                 final long groupHash,
                                 final long timeMs) {
            final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(KEY_LENGTH);
            byteBuffer.putLong(keyFactory.getUniqueId());
            return new LmdbRowKey(byteBuffer.flip());
        }

        @Override
        public LmdbRowKey makeUnique(final LmdbRowKey rowKey) {
            final ByteBuffer byteBuffer = rowKey.getByteBuffer();
            byteBuffer.putLong(0, keyFactory.getUniqueId());
            return rowKey;
        }

        @Override
        public boolean isGroup(final LmdbRowKey rowKey) {
            return false;
        }

        @Override
        public KeyRange<ByteBuffer> createChildKeyRange(final Key parentKey) {
            return all();
        }

        @Override
        public KeyRange<ByteBuffer> createChildKeyRange(final Key parentKey, final TimeFilter timeFilter) {
            if (timeFilter != null) {
                throw new RuntimeException("Time filtering is not supported by this key factory");
            }
            return all();
        }
    }

    /**
     * Creates a flat time based group key. <TIME_MS><GROUP_HASH>
     */
    private static class FlatTimeGroupedLmdbRowKeyFactory implements LmdbRowKeyFactory {

        private static final int KEY_LENGTH = Long.BYTES + Long.BYTES;

        @Override
        public LmdbRowKey create(final int depth,
                                 final long parentGroupHash,
                                 final long groupHash,
                                 final long timeMs) {
            final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(KEY_LENGTH);
            byteBuffer.putLong(timeMs);
            byteBuffer.putLong(groupHash);
            return new LmdbRowKey(byteBuffer.flip());
        }

        @Override
        public LmdbRowKey makeUnique(final LmdbRowKey rowKey) {
            return rowKey;
        }

        @Override
        public boolean isGroup(final LmdbRowKey rowKey) {
            return true;
        }

        @Override
        public KeyRange<ByteBuffer> createChildKeyRange(final Key parentKey) {
            return all();
        }

        @Override
        public KeyRange<ByteBuffer> createChildKeyRange(final Key parentKey, final TimeFilter timeFilter) {
            if (timeFilter == null) {
                return all();
            }

            final ByteBuffer start = ByteBuffer.allocateDirect(Long.BYTES);
            start.putLong(timeFilter.getFrom());
            start.flip();
            final ByteBuffer stop = ByteBuffer.allocateDirect(Long.BYTES);
            stop.putLong(timeFilter.getTo() + 1);
            stop.flip();
            return KeyRange.closedOpen(start, stop);
        }
    }

    /**
     * Creates flat time based unique key. <TIME_MS><UNIQUE_ID>
     */
    private static class FlatTimeUngroupedLmdbRowKeyFactory implements LmdbRowKeyFactory {

        private static final int KEY_LENGTH = Long.BYTES + Long.BYTES;

        private final KeyFactory keyFactory;

        public FlatTimeUngroupedLmdbRowKeyFactory(final KeyFactory keyFactory) {
            this.keyFactory = keyFactory;
        }

        @Override
        public LmdbRowKey create(final int depth,
                                 final long parentGroupHash,
                                 final long groupHash,
                                 final long timeMs) {
            final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(KEY_LENGTH);
            byteBuffer.putLong(timeMs);
            byteBuffer.putLong(keyFactory.getUniqueId());
            return new LmdbRowKey(byteBuffer.flip());
        }

        @Override
        public LmdbRowKey makeUnique(final LmdbRowKey rowKey) {
            final ByteBuffer byteBuffer = rowKey.getByteBuffer();
            byteBuffer.putLong(Long.BYTES, keyFactory.getUniqueId());
            return rowKey;
        }

        @Override
        public boolean isGroup(final LmdbRowKey rowKey) {
            return false;
        }

        @Override
        public KeyRange<ByteBuffer> createChildKeyRange(final Key parentKey) {
            return all();
        }

        @Override
        public KeyRange<ByteBuffer> createChildKeyRange(final Key parentKey, final TimeFilter timeFilter) {
            if (timeFilter == null) {
                return all();
            }

            final ByteBuffer start = ByteBuffer.allocateDirect(Long.BYTES);
            start.putLong(timeFilter.getFrom());
            start.flip();
            final ByteBuffer stop = ByteBuffer.allocateDirect(Long.BYTES);
            stop.putLong(timeFilter.getTo() + 1);
            stop.flip();
            return KeyRange.closedOpen(start, stop);
        }
    }


    /**
     * Creates a nested group key. <DEPTH><PARENT_GROUP_HASH><GROUP_HASH>
     */
    public static class NestedGroupedLmdbRowKeyFactory implements LmdbRowKeyFactory {

        private static final int SHORT_KEY_LENGTH = Byte.BYTES + Long.BYTES;
        private static final int LONG_KEY_LENGTH = Byte.BYTES + Long.BYTES + Long.BYTES;
        private static final int PREFIX_LENGTH = Byte.BYTES + Long.BYTES;

        private static final KeyRange<ByteBuffer> ZERO_DEPTH_KEY_RANGE;

        static {
            final ByteBuffer start = ByteBuffer.allocateDirect(Byte.BYTES);
            start.put((byte) 0);
            start.flip();
            final ByteBuffer stop = ByteBuffer.allocateDirect(Byte.BYTES);
            stop.put((byte) 1);
            stop.flip();
            ZERO_DEPTH_KEY_RANGE = KeyRange.closedOpen(start, stop);
        }

        private final KeyFactory keyFactory;
        private final CompiledDepths compiledDepths;

        public NestedGroupedLmdbRowKeyFactory(final KeyFactory keyFactory,
                                              final CompiledDepths compiledDepths) {
            this.keyFactory = keyFactory;
            this.compiledDepths = compiledDepths;
        }

        @Override
        public LmdbRowKey create(final int depth,
                                 final long parentGroupHash,
                                 final long groupHash,
                                 final long timeMs) {
            ByteBuffer byteBuffer;

            // If this is a grouping key then we need to add the depth first.
            if (depth == 0) {
                // Create a top level group key. <DEPTH><GROUP_HASH>
                byteBuffer = ByteBuffer.allocateDirect(SHORT_KEY_LENGTH);
                byteBuffer.put((byte) depth);
                byteBuffer.putLong(groupHash);
            } else if (isDetailLevel(depth)) {
                // This is a detail level - non-grouped row.
                // Create a child unique key. <DEPTH><PARENT_GROUP_HASH><UNIQUE_ID>
                byteBuffer = ByteBuffer.allocateDirect(LONG_KEY_LENGTH);
                byteBuffer.put((byte) depth);
                byteBuffer.putLong(parentGroupHash);
                byteBuffer.putLong(keyFactory.getUniqueId());
            } else {
                // Create a child group key. <DEPTH><PARENT_GROUP_HASH><GROUP_HASH>
                byteBuffer = ByteBuffer.allocateDirect(LONG_KEY_LENGTH);
                byteBuffer.put((byte) depth);
                byteBuffer.putLong(parentGroupHash);
                byteBuffer.putLong(groupHash);
            }

            return new LmdbRowKey(byteBuffer.flip());
        }

        @Override
        public LmdbRowKey makeUnique(final LmdbRowKey rowKey) {
            final ByteBuffer byteBuffer = rowKey.getByteBuffer();
            if (!isGroup(rowKey)) {
                // Create a child unique key. <DEPTH><PARENT_GROUP_HASH><UNIQUE_ID>
                byteBuffer.putLong(SHORT_KEY_LENGTH, keyFactory.getUniqueId());
            }

            return rowKey;
        }

        @Override
        public boolean isGroup(final LmdbRowKey rowKey) {
            // Get the depth.
            final int depth = Byte.toUnsignedInt(rowKey.getByteBuffer().get(0));
            return !isDetailLevel(depth);
        }

        @Override
        public KeyRange<ByteBuffer> createChildKeyRange(final Key parentKey) {
            KeyRange<ByteBuffer> keyRange = ZERO_DEPTH_KEY_RANGE;

            // If this is a grouping key then we need to add the depth first.
            final int depth = parentKey.getDepth();
            final byte childDepth = (byte) parentKey.getChildDepth();
            if (isDetailLevel(depth)) {
                // This is a detail level - non-grouped row.
                // Not valid for a parent key stem.
                throw new RuntimeException("Invalid parent as detail key");

            } else if (depth >= 0) {
                final KeyPart last = parentKey.getKeyParts().get(parentKey.getKeyParts().size() - 1);
                final GroupKeyPart groupKeyPart = (GroupKeyPart) last;

                // Create a child group key. <DEPTH><GROUP_HASH>
                final long groupHash = ValHasher.hash(groupKeyPart.getGroupValues());
                final ByteBuffer start = ByteBuffer.allocateDirect(PREFIX_LENGTH);
                start.put(childDepth);
                start.putLong(groupHash);
                start.flip();
                final ByteBuffer stop = ByteBuffer.allocateDirect(PREFIX_LENGTH);
                stop.put(childDepth);
                stop.putLong(groupHash + 1);
                stop.flip();
                keyRange = KeyRange.closedOpen(start, stop);
            }

            return keyRange;
        }

        @Override
        public KeyRange<ByteBuffer> createChildKeyRange(final Key parentKey, final TimeFilter timeFilter) {
            if (timeFilter != null) {
                throw new RuntimeException("Time filtering is not supported by this key factory");
            }

            return createChildKeyRange(parentKey);
        }

        private boolean isDetailLevel(final int depth) {
            return depth == compiledDepths.getMaxDepth() &&
                    compiledDepths.getMaxGroupDepth() < compiledDepths.getMaxDepth();
        }
    }

    /**
     * Creates a nested time based group key. <DEPTH><TIME_MS><PARENT_GROUP_HASH><GROUP_HASH>
     */
    private static class NestedTimeGroupedLmdbRowKeyFactory implements LmdbRowKeyFactory {

        private static final int SHORT_KEY_LENGTH = Byte.BYTES + Long.BYTES + Long.BYTES;
        private static final int LONG_KEY_LENGTH = Byte.BYTES + Long.BYTES + Long.BYTES + Long.BYTES;

        private static final int SHORT_PREFIX_LENGTH = Byte.BYTES + Long.BYTES;
        private static final int LONG_PREFIX_LENGTH = Byte.BYTES + Long.BYTES + Long.BYTES;

        private static final KeyRange<ByteBuffer> ZERO_DEPTH_KEY_RANGE;

        static {
            final ByteBuffer start = ByteBuffer.allocateDirect(Byte.BYTES);
            start.put((byte) 0);
            start.flip();
            final ByteBuffer stop = ByteBuffer.allocateDirect(Byte.BYTES);
            stop.put((byte) 1);
            stop.flip();
            ZERO_DEPTH_KEY_RANGE = KeyRange.closedOpen(start, stop);
        }

        private final KeyFactory keyFactory;
        private final CompiledDepths compiledDepths;

        public NestedTimeGroupedLmdbRowKeyFactory(final KeyFactory keyFactory,
                                                  final CompiledDepths compiledDepths) {
            this.keyFactory = keyFactory;
            this.compiledDepths = compiledDepths;
        }

        @Override
        public LmdbRowKey create(final int depth,
                                 final long parentGroupHash,
                                 final long groupHash,
                                 final long timeMs) {
            ByteBuffer byteBuffer;

            // If this is a grouping key then we need to add the depth first.
            if (depth == 0) {
                // Create a time based top level group key. <DEPTH><TIME_MS><GROUP_HASH>
                byteBuffer = ByteBuffer.allocateDirect(SHORT_KEY_LENGTH);
                byteBuffer.put((byte) depth);
                byteBuffer.putLong(timeMs);
                byteBuffer.putLong(groupHash);

            } else if (isDetailLevel(depth)) {
                // This is a detail level - non-grouped row.
                // Create a time based child unique key. <DEPTH><TIME_MS><PARENT_GROUP_HASH><UNIQUE_ID>
                byteBuffer = ByteBuffer.allocateDirect(LONG_KEY_LENGTH);
                byteBuffer.put((byte) depth);
                byteBuffer.putLong(timeMs);
                byteBuffer.putLong(parentGroupHash);
                byteBuffer.putLong(keyFactory.getUniqueId());
            } else {
                // Create a time based child group key. <DEPTH><TIME_MS><PARENT_GROUP_HASH><GROUP_HASH>
                byteBuffer = ByteBuffer.allocateDirect(LONG_KEY_LENGTH);
                byteBuffer.put((byte) depth);
                byteBuffer.putLong(timeMs);
                byteBuffer.putLong(parentGroupHash);
                byteBuffer.putLong(groupHash);
            }

            return new LmdbRowKey(byteBuffer.flip());
        }

        @Override
        public LmdbRowKey makeUnique(final LmdbRowKey rowKey) {
            final ByteBuffer byteBuffer = rowKey.getByteBuffer();
            // If this isn't a group key then make it unique.
            if (!isGroup(rowKey)) {
                byteBuffer.putLong(SHORT_KEY_LENGTH, keyFactory.getUniqueId());
            }
            return rowKey;
        }

        @Override
        public boolean isGroup(final LmdbRowKey rowKey) {
            // Get the depth.
            final int depth = Byte.toUnsignedInt(rowKey.getByteBuffer().get(0));
            return !isDetailLevel(depth);
        }

        @Override
        public KeyRange<ByteBuffer> createChildKeyRange(final Key parentKey) {
            // Create a first level child group key. <DEPTH = 0>
            KeyRange<ByteBuffer> keyRange = ZERO_DEPTH_KEY_RANGE;

            // If this is a grouping key then we need to add the depth first.
            final int depth = parentKey.getDepth();
            final byte childDepth = (byte) parentKey.getChildDepth();
            if (isDetailLevel(depth)) {
                // This is a detail level - non-grouped row.
                // Not valid for a parent key stem.
                throw new RuntimeException("Invalid parent as detail key");

            } else if (depth >= 0) {
                final KeyPart last = parentKey.getKeyParts().get(parentKey.getKeyParts().size() - 1);
                final GroupKeyPart groupKeyPart = (GroupKeyPart) last;

                // Create a time based child group key. <DEPTH><TIME_MS><GROUP_HASH>
                final long groupHash = ValHasher.hash(groupKeyPart.getGroupValues());
                final ByteBuffer start = ByteBuffer.allocateDirect(SHORT_KEY_LENGTH);
                start.put(childDepth);
                start.putLong(parentKey.getTimeMs());
                start.putLong(groupHash);
                start.flip();
                final ByteBuffer stop = ByteBuffer.allocateDirect(SHORT_KEY_LENGTH);
                stop.put(childDepth);
                stop.putLong(parentKey.getTimeMs());
                stop.putLong(groupHash + 1);
                stop.flip();
                keyRange = KeyRange.closedOpen(start, stop);
            }

            return keyRange;
        }

        @Override
        public KeyRange<ByteBuffer> createChildKeyRange(final Key parentKey, final TimeFilter timeFilter) {
            if (timeFilter == null) {
                return createChildKeyRange(parentKey);
            }

            KeyRange<ByteBuffer> keyRange;

            // If this is a grouping key then we need to add the depth first.
            final int depth = parentKey.getDepth();
            final byte childDepth = (byte) parentKey.getChildDepth();
            if (isDetailLevel(depth)) {
                // This is a detail level - non-grouped row.
                // Not valid for a parent key stem.
                throw new RuntimeException("Invalid parent as detail key");

            } else if (depth == -1) {
                final ByteBuffer start = ByteBuffer.allocateDirect(SHORT_PREFIX_LENGTH);
                start.put(childDepth);
                start.putLong(timeFilter.getFrom());
                start.flip();
                final ByteBuffer stop = ByteBuffer.allocateDirect(SHORT_PREFIX_LENGTH);
                stop.put(childDepth);
                stop.putLong(timeFilter.getTo() + 1);
                stop.flip();
                keyRange = KeyRange.closedOpen(start, stop);
            } else {
                final KeyPart last = parentKey.getKeyParts().get(parentKey.getKeyParts().size() - 1);
                final GroupKeyPart groupKeyPart = (GroupKeyPart) last;

                // Create a time based child group key. <DEPTH><TIME_MS><GROUP_HASH>
                final long groupHash = ValHasher.hash(groupKeyPart.getGroupValues());
                final ByteBuffer start = ByteBuffer.allocateDirect(LONG_PREFIX_LENGTH);
                start.put(childDepth);
                start.putLong(timeFilter.getFrom());
                start.putLong(groupHash);
                start.flip();
                final ByteBuffer stop = ByteBuffer.allocateDirect(LONG_PREFIX_LENGTH);
                stop.put(childDepth);
                stop.putLong(timeFilter.getTo());
                stop.putLong(groupHash + 1);
                stop.flip();
                keyRange = KeyRange.closedOpen(start, stop);
            }

            return keyRange;
        }

        private boolean isDetailLevel(final int depth) {
            return depth == compiledDepths.getMaxDepth() &&
                    compiledDepths.getMaxGroupDepth() < compiledDepths.getMaxDepth();
        }
    }
}
