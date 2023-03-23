package stroom.query.common.v2;

import org.lmdbjava.KeyRange;

import java.nio.ByteBuffer;

public class LmdbRowKeyFactory {

    private final KeyFactory keyFactory;
    private final KeyFactoryConfig keyFactoryConfig;
    private final CompiledDepths compiledDepths;

    public LmdbRowKeyFactory(final KeyFactory keyFactory,
                             final KeyFactoryConfig keyFactoryConfig,
                             final CompiledDepths compiledDepths) {
        this.keyFactory = keyFactory;
        this.keyFactoryConfig = keyFactoryConfig;
        this.compiledDepths = compiledDepths;
    }

    public LmdbRowKey create(final int depth,
                             final long parentGroupHash,
                             final long groupHash,
                             final long timeMs) {
        ByteBuffer byteBuffer;

        // If we have no grouping then create a unique key.
        if (!compiledDepths.hasGroup()) {
            if (keyFactoryConfig.addTimeToKey()) {
                // Create a time based unique key. <TIME_MS><UNIQUE_ID>
                byteBuffer = ByteBuffer.allocateDirect(Long.BYTES + Long.BYTES);
                byteBuffer.putLong(timeMs);
                byteBuffer.putLong(keyFactory.getUniqueId());
            } else {
                // Just create a unique key. <UNIQUE_ID>
                byteBuffer = ByteBuffer.allocateDirect(Long.BYTES);
                byteBuffer.putLong(keyFactory.getUniqueId());
            }
        } else {
            // If this is a grouping key then we need to add the depth first.
            if (depth == 0) {
                if (keyFactoryConfig.addTimeToKey()) {
                    // Create a time based top level group key. <DEPTH><TIME_MS><GROUP_HASH>
                    byteBuffer = ByteBuffer.allocateDirect(Byte.BYTES + Long.BYTES + Long.BYTES);
                    byteBuffer.put((byte) depth);
                    byteBuffer.putLong(timeMs);
                    byteBuffer.putLong(groupHash);
                } else {
                    // Create a top level group key. <DEPTH><GROUP_HASH>
                    byteBuffer = ByteBuffer.allocateDirect(Byte.BYTES + Long.BYTES);
                    byteBuffer.put((byte) depth);
                    byteBuffer.putLong(groupHash);
                }
            } else if (compiledDepths.isDetailLevel(depth)) {
                // This is a detail level - non-grouped row.
                if (keyFactoryConfig.addTimeToKey()) {
                    // Create a time based child unique key. <DEPTH><TIME_MS><PARENT_GROUP_HASH><UNIQUE_ID>
                    byteBuffer = ByteBuffer.allocateDirect(Byte.BYTES + Long.BYTES + Long.BYTES + Long.BYTES);
                    byteBuffer.put((byte) depth);
                    byteBuffer.putLong(timeMs);
                    byteBuffer.putLong(parentGroupHash);
                    byteBuffer.putLong(keyFactory.getUniqueId());
                } else {
                    // Create a child unique key. <DEPTH><PARENT_GROUP_HASH><UNIQUE_ID>
                    byteBuffer = ByteBuffer.allocateDirect(Byte.BYTES + Long.BYTES + Long.BYTES);
                    byteBuffer.put((byte) depth);
                    byteBuffer.putLong(parentGroupHash);
                    byteBuffer.putLong(keyFactory.getUniqueId());
                }
            } else {
                if (keyFactoryConfig.addTimeToKey()) {
                    // Create a time based child group key. <DEPTH><TIME_MS><PARENT_GROUP_HASH><GROUP_HASH>
                    byteBuffer = ByteBuffer.allocateDirect(Byte.BYTES + Long.BYTES + Long.BYTES + Long.BYTES);
                    byteBuffer.put((byte) depth);
                    byteBuffer.putLong(timeMs);
                    byteBuffer.putLong(parentGroupHash);
                    byteBuffer.putLong(groupHash);
                } else {
                    // Create a child group key. <DEPTH><PARENT_GROUP_HASH><GROUP_HASH>
                    byteBuffer = ByteBuffer.allocateDirect(Byte.BYTES + Long.BYTES + Long.BYTES);
                    byteBuffer.put((byte) depth);
                    byteBuffer.putLong(parentGroupHash);
                    byteBuffer.putLong(groupHash);
                }
            }
        }

        return new LmdbRowKey(byteBuffer.flip());
    }

    public LmdbRowKey makeUnique(final LmdbRowKey rowKey) {
        final ByteBuffer byteBuffer = rowKey.getByteBuffer();

        // If we have no grouping then create a unique key.
        if (!compiledDepths.hasGroup()) {
            if (keyFactoryConfig.addTimeToKey()) {
                // Create a time based unique key. <TIME_MS><UNIQUE_ID>
                byteBuffer.putLong(Long.BYTES, keyFactory.getUniqueId());
            } else {
                // Just create a unique key. <UNIQUE_ID>
                byteBuffer.putLong(0, keyFactory.getUniqueId());
            }
        } else {
            // If this is a grouping key then we need to add the depth first.
            byte depth = byteBuffer.get(0);
            if (compiledDepths.isDetailLevel(depth)) {
                // This is a detail level - non-grouped row.
                if (keyFactoryConfig.addTimeToKey()) {
                    // Create a time based child unique key. <DEPTH><TIME_MS><PARENT_GROUP_HASH><UNIQUE_ID>
                    byteBuffer.putLong(Byte.BYTES + Long.BYTES + Long.BYTES, keyFactory.getUniqueId());
                } else {
                    // Create a child unique key. <DEPTH><PARENT_GROUP_HASH><UNIQUE_ID>
                    byteBuffer.putLong(Byte.BYTES + Long.BYTES, keyFactory.getUniqueId());
                }
            }
        }

        return rowKey;
    }

    public boolean isGroup(final LmdbRowKey rowKey) {
        // If we have no grouping then no key is a group key.
        if (!compiledDepths.hasGroup()) {
            return false;
        } else {
            // Get the depth.
            final int depth = Byte.toUnsignedInt(rowKey.getByteBuffer().get(0));
            return !compiledDepths.isDetailLevel(depth);
        }
    }

    public KeyRange<ByteBuffer> createChildKeyRange(final Key parentKey) {
        KeyRange<ByteBuffer> keyRange = KeyRange.all();
        if (compiledDepths.hasGroup()) {
            // If this is a grouping key then we need to add the depth first.
            final int depth = parentKey.getDepth();
            if (compiledDepths.isDetailLevel(depth)) {
                // This is a detail level - non-grouped row.
                // Not valid for a parent key stem.
                throw new RuntimeException("Invalid parent as detail key");
            } else if (depth == -1) {
                ByteBuffer start = ByteBuffer.allocateDirect(Byte.BYTES);
                start.put((byte) 0);
                start = start.flip();
                keyRange = KeyRange.closed(start, start);
            } else {
                final KeyPart last = parentKey.getKeyParts().get(parentKey.getKeyParts().size() - 1);
                final GroupKeyPart groupKeyPart = (GroupKeyPart) last;

                if (keyFactoryConfig.addTimeToKey()) {
                    // Create a time based child group key. <DEPTH><TIME_MS><GROUP_HASH>
                    ByteBuffer start = ByteBuffer.allocateDirect(Byte.BYTES + Long.BYTES + Long.BYTES);
                    start.put((byte) (depth + 1));
                    start.putLong(parentKey.getTimeMs());
                    start.putLong(ValHasher.hash(groupKeyPart.getGroupValues()));
                    start = start.flip();
                    keyRange = KeyRange.closed(start, start);
                } else {
                    // Create a child group key. <DEPTH><GROUP_HASH>
                    ByteBuffer start = ByteBuffer.allocateDirect(Byte.BYTES + Long.BYTES);
                    start.put((byte) (depth + 1));
                    start.putLong(ValHasher.hash(groupKeyPart.getGroupValues()));
                    start = start.flip();
                    keyRange = KeyRange.closed(start, start);
                }
            }
        }

        return keyRange;
    }
}
