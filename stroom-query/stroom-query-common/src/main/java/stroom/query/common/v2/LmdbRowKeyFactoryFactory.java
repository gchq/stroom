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

package stroom.query.common.v2;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.lmdb.stream.LmdbKeyRange;
import stroom.query.api.TimeFilter;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ref.StoredValues;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.Input;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;

public class LmdbRowKeyFactoryFactory {

    private static final int DB_STATE_KEY_LENGTH = 1;
    public static final ByteBuffer DB_STATE_KEY = ByteBuffer.allocateDirect(DB_STATE_KEY_LENGTH);

    static {
        DB_STATE_KEY.put((byte) -1);
        DB_STATE_KEY.flip();
    }

    private LmdbRowKeyFactoryFactory() {
        // Non instantiable.
    }

    public static LmdbRowKeyFactory create(final ByteBufferFactory byteBufferFactory,
                                           final UniqueIdProvider uniqueIdProvider,
                                           final KeyFactoryConfig keyFactoryConfig,
                                           final CompiledDepths compiledDepths,
                                           final StoredValueKeyFactory storedValueKeyFactory) {
        final boolean flat = compiledDepths.getMaxDepth() == 0 &&
                             compiledDepths.getMaxGroupDepth() <= compiledDepths.getMaxDepth();
        if (flat) {
            if (keyFactoryConfig.addTimeToKey()) {
                if (compiledDepths.hasGroup()) {
                    return new FlatTimeGroupedLmdbRowKeyFactory(
                            byteBufferFactory, storedValueKeyFactory);
                } else {
                    return new FlatTimeUngroupedLmdbRowKeyFactory(
                            byteBufferFactory, uniqueIdProvider, storedValueKeyFactory);
                }
            } else {
                if (compiledDepths.hasGroup()) {
                    return new FlatGroupedLmdbRowKeyFactory(
                            byteBufferFactory, storedValueKeyFactory);
                } else {
                    return new FlatUngroupedLmdbRowKeyFactory(
                            byteBufferFactory, uniqueIdProvider);
                }
            }
        } else {
            if (keyFactoryConfig.addTimeToKey()) {
                return new NestedTimeGroupedLmdbRowKeyFactory(
                        byteBufferFactory, uniqueIdProvider, compiledDepths, storedValueKeyFactory);

            } else {
                return new NestedGroupedLmdbRowKeyFactory(
                        byteBufferFactory, uniqueIdProvider, compiledDepths, storedValueKeyFactory);
            }
        }
    }

    public static LmdbKeyRange all() {
        return LmdbKeyRange.all();
    }

    /**
     * Creates a flat group key. <GROUP_HASH>
     */
    static class FlatGroupedLmdbRowKeyFactory implements LmdbRowKeyFactory {

        private static final int KEY_LENGTH = Long.BYTES;

        private final ByteBufferFactory byteBufferFactory;
        private final StoredValueKeyFactory storedValueKeyFactory;

        public FlatGroupedLmdbRowKeyFactory(final ByteBufferFactory byteBufferFactory,
                                            final StoredValueKeyFactory storedValueKeyFactory) {
            this.byteBufferFactory = byteBufferFactory;
            this.storedValueKeyFactory = storedValueKeyFactory;
        }

        @Override
        public ByteBuffer create(final int depth, final ByteBuffer parentRowKey, final StoredValues storedValues) {
            final long groupHash = storedValueKeyFactory.getGroupHash(depth, storedValues);
            final ByteBuffer byteBuffer = byteBufferFactory.acquire(KEY_LENGTH);
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
        public void createChildKeyRange(final Key parentKey, final Consumer<LmdbKeyRange> consumer) {
            consumer.accept(all());
        }

        @Override
        public void createChildKeyRange(final Key parentKey,
                                        final TimeFilter timeFilter,
                                        final Consumer<LmdbKeyRange> consumer) {
            if (timeFilter != null) {
                throw new RuntimeException("Time filtering is not supported by this key factory");
            }
            consumer.accept(all());
        }

        @Override
        public Key createKey(final Key parentKey,
                             final StoredValues storedValues,
                             final ByteBuffer keyBuffer) {
            final int parentDepth = parentKey.getDepth();
            if (parentDepth >= 0) {
                throw new RuntimeException("Unexpected parent key");
            }

            final Val[] groupValues = storedValueKeyFactory.getGroupValues(parentDepth + 1, storedValues);
            return parentKey.resolve(0, groupValues);
        }
    }

    /**
     * Creates a flat unique key. <UNIQUE_ID>
     */
    static class FlatUngroupedLmdbRowKeyFactory implements LmdbRowKeyFactory {

        private static final int KEY_LENGTH = Long.BYTES;

        private final ByteBufferFactory byteBufferFactory;
        private final UniqueIdProvider uniqueIdProvider;

        public FlatUngroupedLmdbRowKeyFactory(final ByteBufferFactory byteBufferFactory,
                                              final UniqueIdProvider uniqueIdProvider) {
            this.byteBufferFactory = byteBufferFactory;
            this.uniqueIdProvider = uniqueIdProvider;
        }

        @Override
        public ByteBuffer create(final int depth, final ByteBuffer parentRowKey, final StoredValues storedValues) {
            final ByteBuffer byteBuffer = byteBufferFactory.acquire(KEY_LENGTH);
            byteBuffer.putLong(uniqueIdProvider.getUniqueId());
            return byteBuffer.flip();
        }

        @Override
        public LmdbKV makeUnique(final LmdbKV lmdbKV) {
            lmdbKV.key().putLong(0, uniqueIdProvider.getUniqueId());
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
        public void createChildKeyRange(final Key parentKey, final Consumer<LmdbKeyRange> consumer) {
            consumer.accept(all());
        }

        @Override
        public void createChildKeyRange(final Key parentKey,
                                        final TimeFilter timeFilter,
                                        final Consumer<LmdbKeyRange> consumer) {
            if (timeFilter != null) {
                throw new RuntimeException("Time filtering is not supported by this key factory");
            }
            consumer.accept(all());
        }

        @Override
        public Key createKey(final Key parentKey,
                             final StoredValues storedValues,
                             final ByteBuffer keyBuffer) {
            if (parentKey.getDepth() > 0) {
                throw new RuntimeException("Unexpected parent key");
            }
            try (final Input input = new ByteBufferInput(keyBuffer.duplicate())) {
                final long uniqueId = input.readLong();
                return new Key(0, List.of(new UngroupedKeyPart(uniqueId)));
            }
        }
    }

    /**
     * Creates a flat time based group key. <TIME_MS><GROUP_HASH>
     */
    static class FlatTimeGroupedLmdbRowKeyFactory implements LmdbRowKeyFactory {

        private static final int KEY_LENGTH = Long.BYTES + Long.BYTES;

        private final ByteBufferFactory byteBufferFactory;
        private final StoredValueKeyFactory storedValueKeyFactory;

        public FlatTimeGroupedLmdbRowKeyFactory(final ByteBufferFactory byteBufferFactory,
                                                final StoredValueKeyFactory storedValueKeyFactory) {
            this.byteBufferFactory = byteBufferFactory;
            this.storedValueKeyFactory = storedValueKeyFactory;
        }

        @Override
        public ByteBuffer create(final int depth, final ByteBuffer parentRowKey, final StoredValues storedValues) {
            final long timeMs = storedValueKeyFactory.getTimeMs(storedValues);
            final long groupHash = storedValueKeyFactory.getGroupHash(depth, storedValues);

            final ByteBuffer byteBuffer = byteBufferFactory.acquire(KEY_LENGTH);
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
        public void createChildKeyRange(final Key parentKey, final Consumer<LmdbKeyRange> consumer) {
            consumer.accept(all());
        }

        @Override
        public void createChildKeyRange(final Key parentKey,
                                        final TimeFilter timeFilter,
                                        final Consumer<LmdbKeyRange> consumer) {
            if (timeFilter == null) {
                consumer.accept(all());
            } else {
                final ByteBuffer start = byteBufferFactory.acquire(Long.BYTES);
                final ByteBuffer stop = byteBufferFactory.acquire(Long.BYTES);
                try {
                    start.putLong(timeFilter.getFrom());
                    start.flip();
                    stop.putLong(timeFilter.getTo() + 1);
                    stop.flip();
                    consumer.accept(LmdbKeyRange.builder().start(start).stop(stop, false).build());
                } finally {
                    byteBufferFactory.release(start);
                    byteBufferFactory.release(stop);
                }
            }
        }

        @Override
        public Key createKey(final Key parentKey,
                             final StoredValues storedValues,
                             final ByteBuffer keyBuffer) {
            final int parentDepth = parentKey.getDepth();
            if (parentDepth >= 0) {
                throw new RuntimeException("Unexpected parent key");
            }
            final long timeMs = storedValueKeyFactory.getTimeMs(storedValues);
            final Val[] groupValues = storedValueKeyFactory.getGroupValues(parentDepth + 1, storedValues);
            return parentKey.resolve(timeMs, groupValues);
        }
    }

    /**
     * Creates flat time based unique key. <TIME_MS><UNIQUE_ID>
     */
    static class FlatTimeUngroupedLmdbRowKeyFactory implements LmdbRowKeyFactory {

        private static final int KEY_LENGTH = Long.BYTES + Long.BYTES;

        private final ByteBufferFactory byteBufferFactory;
        private final UniqueIdProvider uniqueIdProvider;
        private final StoredValueKeyFactory storedValueKeyFactory;

        public FlatTimeUngroupedLmdbRowKeyFactory(final ByteBufferFactory byteBufferFactory,
                                                  final UniqueIdProvider uniqueIdProvider,
                                                  final StoredValueKeyFactory storedValueKeyFactory) {
            this.byteBufferFactory = byteBufferFactory;
            this.uniqueIdProvider = uniqueIdProvider;
            this.storedValueKeyFactory = storedValueKeyFactory;
        }

        @Override
        public ByteBuffer create(final int depth, final ByteBuffer parentRowKey, final StoredValues storedValues) {
            final long timeMs = storedValueKeyFactory.getTimeMs(storedValues);
            final ByteBuffer byteBuffer = byteBufferFactory.acquire(KEY_LENGTH);
            byteBuffer.putLong(timeMs);
            byteBuffer.putLong(uniqueIdProvider.getUniqueId());
            return byteBuffer.flip();
        }

        @Override
        public LmdbKV makeUnique(final LmdbKV lmdbKV) {
            lmdbKV.key().putLong(Long.BYTES, uniqueIdProvider.getUniqueId());
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
        public void createChildKeyRange(final Key parentKey, final Consumer<LmdbKeyRange> consumer) {
            consumer.accept(all());
        }

        @Override
        public void createChildKeyRange(final Key parentKey,
                                        final TimeFilter timeFilter,
                                        final Consumer<LmdbKeyRange> consumer) {
            if (timeFilter == null) {
                consumer.accept(all());
            } else {
                final ByteBuffer start = byteBufferFactory.acquire(Long.BYTES);
                final ByteBuffer stop = byteBufferFactory.acquire(Long.BYTES);
                try {
                    start.putLong(timeFilter.getFrom());
                    start.flip();
                    stop.putLong(timeFilter.getTo() + 1);
                    stop.flip();
                    consumer.accept(LmdbKeyRange.builder().start(start).stop(stop, false).build());
                } finally {
                    byteBufferFactory.release(start);
                    byteBufferFactory.release(stop);
                }
            }
        }

        @Override
        public Key createKey(final Key parentKey,
                             final StoredValues storedValues,
                             final ByteBuffer keyBuffer) {
            if (parentKey.getDepth() > 0) {
                throw new RuntimeException("Unexpected parent key");
            }
            final long timeMs = storedValueKeyFactory.getTimeMs(storedValues);
            try (final Input input = new ByteBufferInput(keyBuffer.duplicate())) {
                final long uniqueId = input.readLong();
                return new Key(timeMs, List.of(new UngroupedKeyPart(uniqueId)));
            }
        }
    }

    /**
     * Creates a nested group key. <DEPTH><PARENT_GROUP_HASH><GROUP_HASH>
     */
    static class NestedGroupedLmdbRowKeyFactory implements LmdbRowKeyFactory {

        private static final LmdbKeyRange ZERO_DEPTH_KEY_RANGE;

        static {
            final ByteBuffer start = ByteBuffer.allocateDirect(Byte.BYTES);
            start.put((byte) 0);
            start.flip();
            final ByteBuffer stop = ByteBuffer.allocateDirect(Byte.BYTES);
            stop.put((byte) 1);
            stop.flip();
            ZERO_DEPTH_KEY_RANGE = LmdbKeyRange.builder().start(start).stop(stop, false).build();
        }

        private final ByteBufferFactory byteBufferFactory;
        private final UniqueIdProvider uniqueIdProvider;
        private final CompiledDepths compiledDepths;
        private final StoredValueKeyFactory storedValueKeyFactory;

        public NestedGroupedLmdbRowKeyFactory(final ByteBufferFactory byteBufferFactory,
                                              final UniqueIdProvider uniqueIdProvider,
                                              final CompiledDepths compiledDepths,
                                              final StoredValueKeyFactory storedValueKeyFactory) {
            this.byteBufferFactory = byteBufferFactory;
            this.uniqueIdProvider = uniqueIdProvider;
            this.compiledDepths = compiledDepths;
            this.storedValueKeyFactory = storedValueKeyFactory;
        }

        @Override
        public ByteBuffer create(final int depth, final ByteBuffer parentRowKey, final StoredValues storedValues) {
            final long groupHash = storedValueKeyFactory.getGroupHash(depth, storedValues);

            final ByteBuffer byteBuffer;

            // If this is a grouping key then we need to add the depth first.
            if (depth == 0) {
                // Create a top level group key. <DEPTH><GROUP_HASH>
                byteBuffer = byteBufferFactory.acquire(Byte.BYTES + Long.BYTES);
                byteBuffer.put((byte) depth);
                byteBuffer.putLong(groupHash);
            } else if (isDetailLevel(depth)) {
                // This is a detail level - non-grouped row.
                // Create a child unique key. <DEPTH><PARENT_GROUP_HASHES...><UNIQUE_ID>
                byteBuffer = byteBufferFactory.acquire(parentRowKey.limit() + Long.BYTES);
                byteBuffer.put((byte) depth);
                // Add parent group hashes.
                for (int index = Byte.BYTES; index < parentRowKey.limit(); index += Long.BYTES) {
                    byteBuffer.putLong(parentRowKey.getLong(index));
                }
                byteBuffer.putLong(uniqueIdProvider.getUniqueId());
            } else {
                // Create a child group key. <DEPTH><PARENT_GROUP_HASHES...><GROUP_HASH>
                byteBuffer = byteBufferFactory.acquire(parentRowKey.limit() + Long.BYTES);
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
                lmdbKV.key()
                        .putLong(lmdbKV.key().limit() - Long.BYTES, uniqueIdProvider.getUniqueId());
            }
            return lmdbKV;
        }

        @Override
        public boolean isGroup(final int depth) {
            return !isDetailLevel(depth);
        }

        @Override
        public int getDepth(final LmdbKV lmdbKV) {
            return Byte.toUnsignedInt(lmdbKV.key().get(0));
        }

        @Override
        public void createChildKeyRange(final Key parentKey, final Consumer<LmdbKeyRange> consumer) {
            LmdbKeyRange keyRange = ZERO_DEPTH_KEY_RANGE;

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
                    final long groupHash = storedValueKeyFactory.hash(groupKeyPart.getGroupValues());
                    groupHashes[i++] = groupHash;
                }

                // Create a child group key. <DEPTH><GROUP_HASHES...>
                final ByteBuffer start = byteBufferFactory.acquire(
                        Byte.BYTES + (groupHashes.length * Long.BYTES));
                start.put(childDepth);
                for (final long groupHash : groupHashes) {
                    start.putLong(groupHash);
                }
                start.flip();
                final ByteBuffer stop = byteBufferFactory.acquire(start.limit());
                stop.put(childDepth);
                if (groupHashes.length > 0) {
                    for (i = 0; i < groupHashes.length - 1; i++) {
                        stop.putLong(groupHashes[i]);
                    }
                    // Add final long that is one greater than the value we are looking to match.
                    stop.putLong(groupHashes[groupHashes.length - 1] + 1);
                }
                stop.flip();
                keyRange = LmdbKeyRange.builder().start(start).stop(stop, false).build();
            }

            consumer.accept(keyRange);
        }

        @Override
        public void createChildKeyRange(final Key parentKey,
                                        final TimeFilter timeFilter,
                                        final Consumer<LmdbKeyRange> consumer) {
            if (timeFilter != null) {
                throw new RuntimeException("Time filtering is not supported by this key factory");
            }
            createChildKeyRange(parentKey, consumer);
        }

        private boolean isDetailLevel(final int depth) {
            return depth == compiledDepths.getMaxDepth() &&
                   compiledDepths.getMaxGroupDepth() < compiledDepths.getMaxDepth();
        }

        @Override
        public Key createKey(final Key parentKey,
                             final StoredValues storedValues,
                             final ByteBuffer keyBuffer) {
            final int depth = parentKey.getDepth() + 1;
            final boolean grouped = depth <= compiledDepths.getMaxGroupDepth();
            if (grouped) {
                final Val[] groupValues = storedValueKeyFactory.getGroupValues(parentKey.getDepth() + 1, storedValues);
                return parentKey.resolve(0, groupValues);

            } else {
                try (final Input input = new ByteBufferInput(keyBuffer.duplicate())) {
                    input.skip(keyBuffer.limit() - Long.BYTES);
                    final long uniqueId = input.readLong();
                    return parentKey.resolve(0, uniqueId);
                }
            }
        }
    }

    /**
     * Creates a nested time based group key. <DEPTH><TIME_MS><PARENT_GROUP_HASH><GROUP_HASH>
     */
    static class NestedTimeGroupedLmdbRowKeyFactory implements LmdbRowKeyFactory {

        private static final LmdbKeyRange ZERO_DEPTH_KEY_RANGE;

        static {
            final ByteBuffer start = ByteBuffer.allocateDirect(Byte.BYTES);
            start.put((byte) 0);
            start.flip();
            final ByteBuffer stop = ByteBuffer.allocateDirect(Byte.BYTES);
            stop.put((byte) 1);
            stop.flip();
            ZERO_DEPTH_KEY_RANGE = LmdbKeyRange.builder().start(start).stop(stop, false).build();
        }

        private final ByteBufferFactory byteBufferFactory;
        private final UniqueIdProvider uniqueIdProvider;
        private final CompiledDepths compiledDepths;
        private final StoredValueKeyFactory storedValueKeyFactory;

        public NestedTimeGroupedLmdbRowKeyFactory(final ByteBufferFactory byteBufferFactory,
                                                  final UniqueIdProvider uniqueIdProvider,
                                                  final CompiledDepths compiledDepths,
                                                  final StoredValueKeyFactory storedValueKeyFactory) {
            this.byteBufferFactory = byteBufferFactory;
            this.uniqueIdProvider = uniqueIdProvider;
            this.compiledDepths = compiledDepths;
            this.storedValueKeyFactory = storedValueKeyFactory;
        }

        @Override
        public ByteBuffer create(final int depth, final ByteBuffer parentRowKey, final StoredValues storedValues) {
            final long timeMs = storedValueKeyFactory.getTimeMs(storedValues);
            final long groupHash = storedValueKeyFactory.getGroupHash(depth, storedValues);

            final ByteBuffer byteBuffer;

            // If this is a grouping key then we need to add the depth first.
            if (depth == 0) {
                // Create a time based top level group key. <DEPTH><TIME_MS><GROUP_HASH>
                byteBuffer = byteBufferFactory.acquire(Byte.BYTES + Long.BYTES + Long.BYTES);
                byteBuffer.put((byte) depth);
                byteBuffer.putLong(timeMs);
                byteBuffer.putLong(groupHash);
            } else if (isDetailLevel(depth)) {
                // This is a detail level - non-grouped row.
                // Create a time based child unique key. <DEPTH><TIME_MS><PARENT_GROUP_HASHES...><UNIQUE_ID>
                byteBuffer = byteBufferFactory.acquire(parentRowKey.limit() + Long.BYTES);
                byteBuffer.put((byte) depth);
                byteBuffer.putLong(timeMs);
                // Add parent group hashes.
                for (int index = Byte.BYTES + Long.BYTES; index < parentRowKey.limit(); index += Long.BYTES) {
                    byteBuffer.putLong(parentRowKey.getLong(index));
                }
                byteBuffer.putLong(uniqueIdProvider.getUniqueId());
            } else {
                // Create a time based child group key. <DEPTH><TIME_MS><PARENT_GROUP_HASHES...><GROUP_HASH>
                byteBuffer = byteBufferFactory.acquire(parentRowKey.limit() + Long.BYTES);
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
                lmdbKV.key().putLong(lmdbKV.key().limit() - Long.BYTES,
                        uniqueIdProvider.getUniqueId());
            }
            return lmdbKV;
        }

        @Override
        public boolean isGroup(final int depth) {
            return !isDetailLevel(depth);
        }

        @Override
        public int getDepth(final LmdbKV lmdbKV) {
            return Byte.toUnsignedInt(lmdbKV.key().get(0));
        }

        @Override
        public void createChildKeyRange(final Key parentKey, final Consumer<LmdbKeyRange> consumer) {
            // Create a first level child group key. <DEPTH = 0>
            LmdbKeyRange keyRange = ZERO_DEPTH_KEY_RANGE;

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
                    final long groupHash = storedValueKeyFactory.hash(groupKeyPart.getGroupValues());
                    groupHashes[i++] = groupHash;
                }

                // Create a time based child group key. <DEPTH><TIME_MS><GROUP_HASHES...>
                final ByteBuffer start = byteBufferFactory.acquire(
                        Byte.BYTES + Long.BYTES + (groupHashes.length * Long.BYTES));
                start.put(childDepth);
                start.putLong(parentKey.getTimeMs());
                for (final long groupHash : groupHashes) {
                    start.putLong(groupHash);
                }
                start.flip();
                final ByteBuffer stop = byteBufferFactory.acquire(start.limit());
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
                keyRange = LmdbKeyRange.builder().start(start).stop(stop, false).build();
            }

            consumer.accept(keyRange);
        }

        @Override
        public void createChildKeyRange(final Key parentKey,
                                        final TimeFilter timeFilter,
                                        final Consumer<LmdbKeyRange> consumer) {
            if (timeFilter == null) {
                createChildKeyRange(parentKey, consumer);

            } else {
                final LmdbKeyRange keyRange;

                // If this is a grouping key then we need to add the depth first.
                final int depth = parentKey.getDepth();
                final byte childDepth = (byte) parentKey.getChildDepth();
                if (isDetailLevel(depth)) {
                    // This is a detail level - non-grouped row.
                    // Not valid for a parent key stem.
                    throw new RuntimeException("Invalid parent as detail key");

                } else if (depth == -1) {
                    final ByteBuffer start = byteBufferFactory.acquire(Byte.BYTES + Long.BYTES);
                    start.put(childDepth);
                    start.putLong(timeFilter.getFrom());
                    start.flip();
                    final ByteBuffer stop = byteBufferFactory.acquire(start.limit());
                    stop.put(childDepth);
                    stop.putLong(timeFilter.getTo() + 1);
                    stop.flip();
                    keyRange = LmdbKeyRange.builder().start(start).stop(stop, false).build();
                } else {
                    // Calculate all group hashes.
                    final long[] groupHashes = new long[parentKey.getKeyParts().size()];
                    int i = 0;
                    for (final KeyPart keyPart : parentKey.getKeyParts()) {
                        final GroupKeyPart groupKeyPart = (GroupKeyPart) keyPart;
                        final long groupHash = storedValueKeyFactory.hash(groupKeyPart.getGroupValues());
                        groupHashes[i++] = groupHash;
                    }

                    // Create a time based child group key. <DEPTH><TIME_MS><GROUP_HASHES...>
                    final ByteBuffer start = byteBufferFactory.acquire(
                            Byte.BYTES + Long.BYTES + (groupHashes.length * Long.BYTES));
                    start.put(childDepth);
                    start.putLong(timeFilter.getFrom());
                    for (final long groupHash : groupHashes) {
                        start.putLong(groupHash);
                    }
                    start.flip();
                    final ByteBuffer stop = byteBufferFactory.acquire(start.limit());
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
                    keyRange = LmdbKeyRange.builder().start(start).stop(stop, false).build();
                }

                consumer.accept(keyRange);
            }
        }

        private boolean isDetailLevel(final int depth) {
            return depth == compiledDepths.getMaxDepth() &&
                   compiledDepths.getMaxGroupDepth() < compiledDepths.getMaxDepth();
        }

        @Override
        public Key createKey(final Key parentKey,
                             final StoredValues storedValues,
                             final ByteBuffer keyBuffer) {
            final int depth = parentKey.getDepth() + 1;
            final boolean grouped = depth <= compiledDepths.getMaxGroupDepth();
            final long timeMs = storedValueKeyFactory.getTimeMs(storedValues);
            if (grouped) {
                final Val[] groupValues = storedValueKeyFactory.getGroupValues(parentKey.getDepth() + 1, storedValues);
                return parentKey.resolve(0, groupValues);

            } else {
                try (final Input input = new ByteBufferInput(keyBuffer.duplicate())) {
                    input.skip(keyBuffer.limit() - Long.BYTES);
                    final long uniqueId = input.readLong();
                    return parentKey.resolve(timeMs, uniqueId);
                }
            }
        }
    }
}
