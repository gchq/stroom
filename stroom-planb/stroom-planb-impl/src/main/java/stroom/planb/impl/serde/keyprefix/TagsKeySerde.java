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

package stroom.planb.impl.serde.keyprefix;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.UidLookupDb;
import stroom.planb.impl.db.UidLookupRecorder;
import stroom.planb.impl.db.UsedLookupsRecorder;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class TagsKeySerde implements KeyPrefixSerde {

    private final UnsignedBytes uidUnsignedBytes = UnsignedBytesInstances.FOUR;

    private final UidLookupDb uidLookupDb;
    private final ByteBuffers byteBuffers;

    public TagsKeySerde(final UidLookupDb uidLookupDb,
                        final ByteBuffers byteBuffers) {
        this.uidLookupDb = uidLookupDb;
        this.byteBuffers = byteBuffers;
    }

    @Override
    public KeyPrefix read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        final List<Tag> tags = new ArrayList<>();
        // The first 4 bytes gives us a lookup to the tag combination uid.
        final long tagSetUid = getUid(byteBuffer);
        final ByteBuffer tagSetByteBuffer = uidLookupDb.getValue(txn, tagSetUid);

        // Decompose the tag combination down into tag names.
        final List<Long> tagNameUids = new ArrayList<>();
        while (tagSetByteBuffer.remaining() > 0) {
            final long tagNameUid = getUid(tagSetByteBuffer);
            tagNameUids.add(tagNameUid);
        }

        // Read all the tag values.
        int i = 0;
        while (byteBuffer.remaining() > 0) {
            final long tagNameUid = tagNameUids.get(i);
            final ByteBuffer tagNameByteBuffer = uidLookupDb.getValue(txn, tagNameUid);
            final Val tagName = ValSerdeUtil.read(tagNameByteBuffer);

            final long tagValueUid = getUid(byteBuffer);
            final ByteBuffer tagValueByteBuffer = uidLookupDb.getValue(txn, tagValueUid);
            final Val tagValue = ValSerdeUtil.read(tagValueByteBuffer);
            final Tag tag = new Tag(tagName.toString(), tagValue);
            tags.add(tag);
            i++;
        }

        return KeyPrefix.create(tags);
    }

    private List<Tag> sortTags(final KeyPrefix key) {
        return key
                .getTags()
                .stream()
                .sorted(Comparator.comparing(Tag::getTagName))
                .toList();
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final KeyPrefix key, final Consumer<ByteBuffer> consumer) {
        final List<Tag> tags = sortTags(key);

        // Get a UID for the combination of tag names.
        final long tagSetUid = byteBuffers.use(tags.size() * Integer.BYTES, tagSetByteBuffer -> {
            for (final Tag tag : tags) {
                final Val tagName = ValString.create(tag.getTagName());
                ValSerdeUtil.write(tagName, byteBuffers, byteBuffer -> {
                    uidLookupDb.put(txn, byteBuffer, uidByteBuffer -> {
                        final long uid = UnsignedBytesInstances.ofLength(uidByteBuffer.remaining()).get(uidByteBuffer);
                        putUid(tagSetByteBuffer, uid);
                        return null;
                    });
                    return null;
                });
            }
            tagSetByteBuffer.flip();

            // Get a UID for the tag combination.
            return uidLookupDb.put(txn, tagSetByteBuffer, uidByteBuffer ->
                    UnsignedBytesInstances.ofLength(uidByteBuffer.remaining()).get(uidByteBuffer));
        });

        byteBuffers.use(Integer.BYTES + (tags.size() * Integer.BYTES), keyByteBuffer -> {
            putUid(keyByteBuffer, tagSetUid);
            for (final Tag tag : tags) {
                final Val tagValue = tag.getTagValue();
                ValSerdeUtil.write(tagValue, byteBuffers, byteBuffer -> {
                    uidLookupDb.put(txn, byteBuffer, uidByteBuffer -> {
                        final long uid = UnsignedBytesInstances.ofLength(uidByteBuffer.remaining()).get(uidByteBuffer);
                        putUid(keyByteBuffer, uid);
                        return null;
                    });
                    return null;
                });
            }

            keyByteBuffer.flip();
            consumer.accept(keyByteBuffer);
        });
    }

    @Override
    public <R> R toBufferForGet(final Txn<ByteBuffer> txn,
                                final KeyPrefix key,
                                final Function<Optional<ByteBuffer>, R> function) {
        try {
            final List<Tag> tags = sortTags(key);

            // Get a UID for the combination of tag names.
            final long tagSetUid = byteBuffers.use(tags.size() * Integer.BYTES,
                    tagSetByteBuffer -> {
                        for (final Tag tag : tags) {
                            final Val tagName = ValString.create(tag.getTagName());
                            ValSerdeUtil.write(tagName, byteBuffers, byteBuffer -> {
                                uidLookupDb.get(txn, byteBuffer, optionalUidByteBuffer -> {
                                    final ByteBuffer uidByteBuffer = optionalUidByteBuffer
                                            .orElseThrow(KeyNotFoundException::new);
                                    final long uid = UnsignedBytesInstances.ofLength(uidByteBuffer.remaining())
                                            .get(uidByteBuffer);
                                    putUid(tagSetByteBuffer, uid);
                                    return null;
                                });
                                return null;
                            });
                        }
                        tagSetByteBuffer.flip();

                        // Get a UID for the tag combination.
                        return uidLookupDb.get(txn, tagSetByteBuffer, optionalUidByteBuffer -> {
                            final ByteBuffer uidByteBuffer = optionalUidByteBuffer
                                    .orElseThrow(KeyNotFoundException::new);
                            return UnsignedBytesInstances.ofLength(uidByteBuffer.remaining())
                                    .get(uidByteBuffer);
                        });
                    });

            return byteBuffers.use(Integer.BYTES + (tags.size() * Integer.BYTES),
                    keyByteBuffer -> {
                        putUid(keyByteBuffer, tagSetUid);
                        for (final Tag tag : tags) {
                            final Val tagValue = tag.getTagValue();
                            ValSerdeUtil.write(tagValue, byteBuffers, byteBuffer -> {
                                uidLookupDb.get(txn, byteBuffer, optionalUidByteBuffer -> {
                                    final ByteBuffer uidByteBuffer = optionalUidByteBuffer
                                            .orElseThrow(KeyNotFoundException::new);
                                    final long uid = UnsignedBytesInstances.ofLength(uidByteBuffer.remaining())
                                            .get(uidByteBuffer);
                                    putUid(keyByteBuffer, uid);
                                    return null;
                                });
                                return null;
                            });
                        }

                        keyByteBuffer.flip();
                        return function.apply(Optional.of(keyByteBuffer));
                    });
        } catch (final KeyNotFoundException e) {
            return function.apply(Optional.empty());
        }
    }

    @Override
    public boolean usesLookup(final ByteBuffer byteBuffer) {
        return true;
    }

    @Override
    public UsedLookupsRecorder getUsedLookupsRecorder(final PlanBEnv env) {
        return new UsedLookupsRecorder() {
            final UidLookupRecorder uidLookupRecorder = new UidLookupRecorder(env, uidLookupDb);

            @Override
            public void recordUsed(final LmdbWriter writer, final ByteBuffer byteBuffer) {
                // The first 4 bytes gives us a lookup to the tag combination uid.
                final long tagSetUid = getUid(byteBuffer);
                uidLookupRecorder.recordUsed(writer, tagSetUid);

                final ByteBuffer tagSetByteBuffer = uidLookupDb.getValue(writer.getWriteTxn(), tagSetUid);

                // Remember each tag name used.
                while (tagSetByteBuffer.remaining() > 0) {
                    final long tagNameUid = getUid(tagSetByteBuffer);
                    uidLookupRecorder.recordUsed(writer, tagNameUid);
                }

                // Remember all the tag values.
                while (byteBuffer.remaining() > 0) {
                    final long tagValueUid = getUid(byteBuffer);
                    uidLookupRecorder.recordUsed(writer, tagValueUid);
                }

                writer.tryCommit();
            }

            @Override
            public void deleteUnused(final Txn<ByteBuffer> readTxn, final LmdbWriter writer) {
                uidLookupRecorder.deleteUnused(readTxn, writer);
            }
        };
    }

    private void putUid(final ByteBuffer byteBuffer, final long uid) {
        uidUnsignedBytes.put(byteBuffer, uid);
    }

    private long getUid(final ByteBuffer byteBuffer) {
        return uidUnsignedBytes.get(byteBuffer);
    }

    private static class KeyNotFoundException extends RuntimeException {

    }
}
