package stroom.query.common.v2;

import stroom.query.api.v2.TimeFilter;

import org.lmdbjava.KeyRange;

import java.nio.ByteBuffer;

public class LmdbRowKeyFactoryFactory {

    private static final int DB_STATE_KEY_LENGTH = 1;
    public static final ByteBuffer DB_STATE_KEY = ByteBuffer.allocateDirect(DB_STATE_KEY_LENGTH);

    static {
        DB_STATE_KEY.put((byte) -1);
        DB_STATE_KEY.flip();
    }

    public static final KeyRange<ByteBuffer> DB_STATE_KEY_RANGE = KeyRange.closed(DB_STATE_KEY, DB_STATE_KEY);
    public static final KeyRange<ByteBuffer> ALL = KeyRange.all();

    private LmdbRowKeyFactoryFactory() {
        // Non instantiable.
    }

    public static boolean isNotStateKey(final ByteBuffer key) {
        return key.limit() != LmdbRowKeyFactoryFactory.DB_STATE_KEY_LENGTH;
    }

    public static boolean isStateKey(final ByteBuffer key) {
        return key.limit() == LmdbRowKeyFactoryFactory.DB_STATE_KEY_LENGTH &&
                key.equals(LmdbRowKeyFactoryFactory.DB_STATE_KEY);
    }

    public static LmdbRowKeyFactory create(final UniqueIdProvider uniqueIdProvider,
                                           final KeyFactoryConfig keyFactoryConfig,
                                           final CompiledDepths compiledDepths,
                                           final ValHasher valHasher) {
        final boolean flat = compiledDepths.getMaxDepth() == 0 &&
                compiledDepths.getMaxGroupDepth() <= compiledDepths.getMaxDepth();
        if (flat) {
            if (keyFactoryConfig.addTimeToKey()) {
                if (compiledDepths.hasGroup()) {
                    return new FlatTimeGroupedLmdbRowKeyFactory();
                } else {
                    return new FlatTimeUngroupedLmdbRowKeyFactory(uniqueIdProvider);
                }
            } else {
                if (compiledDepths.hasGroup()) {
                    return new FlatGroupedLmdbRowKeyFactory();
                } else {
                    return new FlatUngroupedLmdbRowKeyFactory(uniqueIdProvider);
                }
            }
        } else {
            if (keyFactoryConfig.addTimeToKey()) {
                return new NestedTimeGroupedLmdbRowKeyFactory(uniqueIdProvider, compiledDepths, valHasher);

            } else {
                return new NestedGroupedLmdbRowKeyFactory(uniqueIdProvider, compiledDepths, valHasher);
            }
        }
    }

    public static KeyRange<ByteBuffer> all() {
        return ALL;
    }

    /**
     * Creates a flat group key. <GROUP_HASH>
     */
    static class FlatGroupedLmdbRowKeyFactory implements LmdbRowKeyFactory {

        private static final int KEY_LENGTH = Long.BYTES;

        @Override
        public ByteBuffer create(final int depth,
                                 final ByteBuffer parentRowKey,
                                 final long groupHash,
                                 final long timeMs) {
            final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(KEY_LENGTH);
            byteBuffer.putLong(groupHash);
            return byteBuffer.flip();
        }

        @Override
        public LmdbKV makeUnique(final LmdbKV lmdbKV) {
            return lmdbKV;
        }

        @Override
        public boolean isGroup(final int depth) {
            return true;
        }

        @Override
        public int getDepth(final LmdbKV lmdbKV) {
            return 0;
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
    static class FlatUngroupedLmdbRowKeyFactory implements LmdbRowKeyFactory {

        private static final int KEY_LENGTH = Long.BYTES;

        private final UniqueIdProvider uniqueIdProvider;

        public FlatUngroupedLmdbRowKeyFactory(final UniqueIdProvider uniqueIdProvider) {
            this.uniqueIdProvider = uniqueIdProvider;
        }

        @Override
        public ByteBuffer create(final int depth,
                                 final ByteBuffer parentRowKey,
                                 final long groupHash,
                                 final long timeMs) {
            final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(KEY_LENGTH);
            byteBuffer.putLong(uniqueIdProvider.getUniqueId());
            return byteBuffer.flip();
        }

        @Override
        public LmdbKV makeUnique(final LmdbKV lmdbKV) {
            lmdbKV.getRowKey().putLong(0, uniqueIdProvider.getUniqueId());
            return lmdbKV;
        }

        @Override
        public boolean isGroup(final int depth) {
            return false;
        }

        @Override
        public int getDepth(final LmdbKV lmdbKV) {
            return 0;
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
    static class FlatTimeGroupedLmdbRowKeyFactory implements LmdbRowKeyFactory {

        private static final int KEY_LENGTH = Long.BYTES + Long.BYTES;

        @Override
        public ByteBuffer create(final int depth,
                                 final ByteBuffer parentRowKey,
                                 final long groupHash,
                                 final long timeMs) {
            final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(KEY_LENGTH);
            byteBuffer.putLong(timeMs);
            byteBuffer.putLong(groupHash);
            return byteBuffer.flip();
        }

        @Override
        public LmdbKV makeUnique(final LmdbKV lmdbKV) {
            return lmdbKV;
        }

        @Override
        public boolean isGroup(final int depth) {
            return true;
        }

        @Override
        public int getDepth(final LmdbKV lmdbKV) {
            return 0;
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
    static class FlatTimeUngroupedLmdbRowKeyFactory implements LmdbRowKeyFactory {

        private static final int KEY_LENGTH = Long.BYTES + Long.BYTES;

        private final UniqueIdProvider uniqueIdProvider;

        public FlatTimeUngroupedLmdbRowKeyFactory(final UniqueIdProvider uniqueIdProvider) {
            this.uniqueIdProvider = uniqueIdProvider;
        }

        @Override
        public ByteBuffer create(final int depth,
                                 final ByteBuffer parentRowKey,
                                 final long groupHash,
                                 final long timeMs) {
            final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(KEY_LENGTH);
            byteBuffer.putLong(timeMs);
            byteBuffer.putLong(uniqueIdProvider.getUniqueId());
            return byteBuffer.flip();
        }

        @Override
        public LmdbKV makeUnique(final LmdbKV lmdbKV) {
            lmdbKV.getRowKey().putLong(Long.BYTES, uniqueIdProvider.getUniqueId());
            return lmdbKV;
        }

        @Override
        public boolean isGroup(final int depth) {
            return false;
        }

        @Override
        public int getDepth(final LmdbKV lmdbKV) {
            return 0;
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
    static class NestedGroupedLmdbRowKeyFactory implements LmdbRowKeyFactory {

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

        private final UniqueIdProvider uniqueIdProvider;
        private final CompiledDepths compiledDepths;
        private final ValHasher valHasher;

        public NestedGroupedLmdbRowKeyFactory(final UniqueIdProvider uniqueIdProvider,
                                              final CompiledDepths compiledDepths,
                                              final ValHasher valHasher) {
            this.uniqueIdProvider = uniqueIdProvider;
            this.compiledDepths = compiledDepths;
            this.valHasher = valHasher;
        }

        @Override
        public ByteBuffer create(final int depth,
                                 final ByteBuffer parentRowKey,
                                 final long groupHash,
                                 final long timeMs) {
            ByteBuffer byteBuffer;

            // If this is a grouping key then we need to add the depth first.
            if (depth == 0) {
                // Create a top level group key. <DEPTH><GROUP_HASH>
                byteBuffer = ByteBuffer.allocateDirect(Byte.BYTES + Long.BYTES);
                byteBuffer.put((byte) depth);
                byteBuffer.putLong(groupHash);
            } else if (isDetailLevel(depth)) {
                // This is a detail level - non-grouped row.
                // Create a child unique key. <DEPTH><PARENT_GROUP_HASHES...><UNIQUE_ID>
                byteBuffer = ByteBuffer.allocateDirect(parentRowKey.limit() + Long.BYTES);
                byteBuffer.put((byte) depth);
                // Add parent group hashes.
                for (int index = Byte.BYTES; index < parentRowKey.limit(); index += Long.BYTES) {
                    byteBuffer.putLong(parentRowKey.getLong(index));
                }
                byteBuffer.putLong(uniqueIdProvider.getUniqueId());
            } else {
                // Create a child group key. <DEPTH><PARENT_GROUP_HASHES...><GROUP_HASH>
                byteBuffer = ByteBuffer.allocateDirect(parentRowKey.limit() + Long.BYTES);
                byteBuffer.put((byte) depth);
                // Add parent group hashes.
                for (int index = Byte.BYTES; index < parentRowKey.limit(); index += Long.BYTES) {
                    byteBuffer.putLong(parentRowKey.getLong(index));
                }
                byteBuffer.putLong(groupHash);
            }

            return byteBuffer.flip();
        }

        @Override
        public LmdbKV makeUnique(final LmdbKV lmdbKV) {
            if (isDetailLevel(getDepth(lmdbKV))) {
                // Create a child unique key. <DEPTH><PARENT_GROUP_HASHES...><UNIQUE_ID>
                lmdbKV
                        .getRowKey()
                        .putLong(lmdbKV.getRowKey().limit() - Long.BYTES, uniqueIdProvider.getUniqueId());
            }
            return lmdbKV;
        }

        @Override
        public boolean isGroup(final int depth) {
            return !isDetailLevel(depth);
        }

        @Override
        public int getDepth(final LmdbKV lmdbKV) {
            return Byte.toUnsignedInt(lmdbKV.getRowKey().get(0));
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
                // Calculate all group hashes.
                final long[] groupHashes = new long[parentKey.getKeyParts().size()];
                int i = 0;
                for (final KeyPart keyPart : parentKey.getKeyParts()) {
                    final GroupKeyPart groupKeyPart = (GroupKeyPart) keyPart;
                    final long groupHash = valHasher.hash(groupKeyPart.getGroupValues());
                    groupHashes[i++] = groupHash;
                }

                // Create a child group key. <DEPTH><GROUP_HASHES...>
                final ByteBuffer start = ByteBuffer.allocateDirect(
                        Byte.BYTES + (groupHashes.length * Long.BYTES));
                start.put(childDepth);
                for (final long groupHash : groupHashes) {
                    start.putLong(groupHash);
                }
                start.flip();
                final ByteBuffer stop = ByteBuffer.allocateDirect(start.limit());
                stop.put(childDepth);
                if (groupHashes.length > 0) {
                    for (i = 0; i < groupHashes.length - 1; i++) {
                        stop.putLong(groupHashes[i]);
                    }
                    // Add final long that is one greater than the value we are looking to match.
                    stop.putLong(groupHashes[groupHashes.length - 1] + 1);
                }
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
    static class NestedTimeGroupedLmdbRowKeyFactory implements LmdbRowKeyFactory {

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

        private final UniqueIdProvider uniqueIdProvider;
        private final CompiledDepths compiledDepths;
        private final ValHasher valHasher;

        public NestedTimeGroupedLmdbRowKeyFactory(final UniqueIdProvider uniqueIdProvider,
                                                  final CompiledDepths compiledDepths,
                                                  final ValHasher valHasher) {
            this.uniqueIdProvider = uniqueIdProvider;
            this.compiledDepths = compiledDepths;
            this.valHasher = valHasher;
        }

        @Override
        public ByteBuffer create(final int depth,
                                 final ByteBuffer parentRowKey,
                                 final long groupHash,
                                 final long timeMs) {
            ByteBuffer byteBuffer;

            // If this is a grouping key then we need to add the depth first.
            if (depth == 0) {
                // Create a time based top level group key. <DEPTH><TIME_MS><GROUP_HASH>
                byteBuffer = ByteBuffer.allocateDirect(Byte.BYTES + Long.BYTES + Long.BYTES);
                byteBuffer.put((byte) depth);
                byteBuffer.putLong(timeMs);
                byteBuffer.putLong(groupHash);
            } else if (isDetailLevel(depth)) {
                // This is a detail level - non-grouped row.
                // Create a time based child unique key. <DEPTH><TIME_MS><PARENT_GROUP_HASHES...><UNIQUE_ID>
                byteBuffer = ByteBuffer.allocateDirect(parentRowKey.limit() + Long.BYTES);
                byteBuffer.put((byte) depth);
                byteBuffer.putLong(timeMs);
                // Add parent group hashes.
                for (int index = Byte.BYTES + Long.BYTES; index < parentRowKey.limit(); index += Long.BYTES) {
                    byteBuffer.putLong(parentRowKey.getLong(index));
                }
                byteBuffer.putLong(uniqueIdProvider.getUniqueId());
            } else {
                // Create a time based child group key. <DEPTH><TIME_MS><PARENT_GROUP_HASHES...><GROUP_HASH>
                byteBuffer = ByteBuffer.allocateDirect(parentRowKey.limit() + Long.BYTES);
                byteBuffer.put((byte) depth);
                byteBuffer.putLong(timeMs);
                // Add parent group hashes.
                for (int index = Byte.BYTES + Long.BYTES; index < parentRowKey.limit(); index += Long.BYTES) {
                    byteBuffer.putLong(parentRowKey.getLong(index));
                }
                byteBuffer.putLong(groupHash);
            }

            return byteBuffer.flip();
        }

        @Override
        public LmdbKV makeUnique(final LmdbKV lmdbKV) {
            // If this isn't a group key then make it unique.
            if (isDetailLevel(getDepth(lmdbKV))) {
                lmdbKV.getRowKey().putLong(lmdbKV.getRowKey().limit() - Long.BYTES, uniqueIdProvider.getUniqueId());
            }
            return lmdbKV;
        }

        @Override
        public boolean isGroup(final int depth) {
            return !isDetailLevel(depth);
        }

        @Override
        public int getDepth(final LmdbKV lmdbKV) {
            return Byte.toUnsignedInt(lmdbKV.getRowKey().get(0));
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
                // Calculate all group hashes.
                final long[] groupHashes = new long[parentKey.getKeyParts().size()];
                int i = 0;
                for (final KeyPart keyPart : parentKey.getKeyParts()) {
                    final GroupKeyPart groupKeyPart = (GroupKeyPart) keyPart;
                    final long groupHash = valHasher.hash(groupKeyPart.getGroupValues());
                    groupHashes[i++] = groupHash;
                }

                // Create a time based child group key. <DEPTH><TIME_MS><GROUP_HASHES...>
                final ByteBuffer start = ByteBuffer.allocateDirect(
                        Byte.BYTES + Long.BYTES + (groupHashes.length * Long.BYTES));
                start.put(childDepth);
                start.putLong(parentKey.getTimeMs());
                for (final long groupHash : groupHashes) {
                    start.putLong(groupHash);
                }
                start.flip();
                final ByteBuffer stop = ByteBuffer.allocateDirect(start.limit());
                stop.put(childDepth);
                stop.putLong(parentKey.getTimeMs());
                if (groupHashes.length > 0) {
                    for (i = 0; i < groupHashes.length - 1; i++) {
                        stop.putLong(groupHashes[i]);
                    }
                    // Add final long that is one greater than the value we are looking to match.
                    stop.putLong(groupHashes[groupHashes.length - 1] + 1);
                }
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
                final ByteBuffer start = ByteBuffer.allocateDirect(Byte.BYTES + Long.BYTES);
                start.put(childDepth);
                start.putLong(timeFilter.getFrom());
                start.flip();
                final ByteBuffer stop = ByteBuffer.allocateDirect(start.limit());
                stop.put(childDepth);
                stop.putLong(timeFilter.getTo() + 1);
                stop.flip();
                keyRange = KeyRange.closedOpen(start, stop);
            } else {
                // Calculate all group hashes.
                final long[] groupHashes = new long[parentKey.getKeyParts().size()];
                int i = 0;
                for (final KeyPart keyPart : parentKey.getKeyParts()) {
                    final GroupKeyPart groupKeyPart = (GroupKeyPart) keyPart;
                    final long groupHash = valHasher.hash(groupKeyPart.getGroupValues());
                    groupHashes[i++] = groupHash;
                }

                // Create a time based child group key. <DEPTH><TIME_MS><GROUP_HASHES...>
                final ByteBuffer start = ByteBuffer.allocateDirect(
                        Byte.BYTES + Long.BYTES + (groupHashes.length * Long.BYTES));
                start.put(childDepth);
                start.putLong(timeFilter.getFrom());
                for (final long groupHash : groupHashes) {
                    start.putLong(groupHash);
                }
                start.flip();
                final ByteBuffer stop = ByteBuffer.allocateDirect(start.limit());
                stop.put(childDepth);
                stop.putLong(timeFilter.getTo());
                if (groupHashes.length > 0) {
                    for (i = 0; i < groupHashes.length - 1; i++) {
                        stop.putLong(groupHashes[i]);
                    }
                    // Add final long that is one greater than the value we are looking to match.
                    stop.putLong(groupHashes[groupHashes.length - 1] + 1);
                }
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
