package stroom.planb.impl.serde.temporalkey;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.UidLookupDb;
import stroom.planb.impl.db.UidLookupRecorder;
import stroom.planb.impl.db.UsedLookupsRecorder;
import stroom.planb.impl.serde.keyprefix.KeyPrefix;
import stroom.planb.impl.serde.keyprefix.Tag;
import stroom.planb.impl.serde.time.TimeSerde;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class TagsKeySerde implements TemporalKeySerde {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TagsKeySerde.class);

    private final UnsignedBytes uidUnsignedBytes = UnsignedBytesInstances.FOUR;

    private final UidLookupDb uidLookupDb;
    private final ByteBuffers byteBuffers;
    private final TimeSerde timeSerde;

    public TagsKeySerde(final UidLookupDb uidLookupDb,
                        final ByteBuffers byteBuffers,
                        final TimeSerde timeSerde) {
        this.uidLookupDb = uidLookupDb;
        this.byteBuffers = byteBuffers;
        this.timeSerde = timeSerde;
    }

    @Override
    public TemporalKey read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        try {
            // Slice off the end to get the effective time.
            final ByteBuffer timeSlice = byteBuffer.slice(byteBuffer.remaining() - timeSerde.getSize(),
                    timeSerde.getSize());
            final Instant time = timeSerde.read(timeSlice);

            // Slice off the key prefix bytes.
            final ByteBuffer prefixSlice = getPrefix(byteBuffer);

            final List<Tag> tags = new ArrayList<>();
            // The first 4 bytes gives us a lookup to the tag combination uid.
            final long tagSetUid = getUid(prefixSlice);
            final ByteBuffer tagSetByteBuffer = uidLookupDb.getValue(txn, tagSetUid);

            // Decompose the tag combination down into tag names.
            final List<Long> tagNameUids = new ArrayList<>();
            while (tagSetByteBuffer.remaining() > 0) {
                final long tagNameUid = getUid(tagSetByteBuffer);
                tagNameUids.add(tagNameUid);
            }

            // Read all the tag values.
            int i = 0;
            while (prefixSlice.remaining() > 0) {
                final long tagNameUid = tagNameUids.get(i);
                final ByteBuffer tagNameByteBuffer = uidLookupDb.getValue(txn, tagNameUid);
                final Val tagName = ValSerdeUtil.read(tagNameByteBuffer);

                final long tagValueUid = getUid(prefixSlice);
                final ByteBuffer tagValueByteBuffer = uidLookupDb.getValue(txn, tagValueUid);
                final Val tagValue = ValSerdeUtil.read(tagValueByteBuffer);
                final Tag tag = new Tag(tagName.toString(), tagValue);
                tags.add(tag);
                i++;
            }

            return new TemporalKey(KeyPrefix.create(tags), time);

        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            LOGGER.debug(() -> "ByteBuffer contains " + ByteBufferUtils.byteBufferToHexAll(byteBuffer));
            throw e;
        }
    }

    private ByteBuffer getPrefix(final ByteBuffer byteBuffer) {
        // Slice off the name.
        return byteBuffer.slice(0, byteBuffer.remaining() - timeSerde.getSize());
    }

    private List<Tag> sortTags(final TemporalKey key) {
        return key
                .getPrefix()
                .getTags()
                .stream()
                .sorted(Comparator.comparing(Tag::getTagName))
                .toList();
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final TemporalKey key, final Consumer<ByteBuffer> consumer) {
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

        byteBuffers.use(Integer.BYTES + (tags.size() * Integer.BYTES) + timeSerde.getSize(), keyByteBuffer -> {
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

            timeSerde.write(keyByteBuffer, key.getTime());
            keyByteBuffer.flip();
            consumer.accept(keyByteBuffer);
        });
    }

    @Override
    public <R> R toBufferForGet(final Txn<ByteBuffer> txn,
                                final TemporalKey key,
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

            return byteBuffers.use(Integer.BYTES + (tags.size() * Integer.BYTES) + timeSerde.getSize(),
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

                        timeSerde.write(keyByteBuffer, key.getTime());
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
                final ByteBuffer prefix = getPrefix(byteBuffer);
                // The first 4 bytes give us a lookup to the tag combination uid.
                final long tagSetUid = getUid(prefix);
                uidLookupRecorder.recordUsed(writer, tagSetUid);

                final ByteBuffer tagSetByteBuffer = uidLookupDb.getValue(writer.getWriteTxn(), tagSetUid);

                // Remember each tag name used.
                while (tagSetByteBuffer.remaining() > 0) {
                    final long tagNameUid = getUid(tagSetByteBuffer);
                    uidLookupRecorder.recordUsed(writer, tagNameUid);
                }

                // Remember all the tag values.
                while (prefix.remaining() > 0) {
                    final long tagValueUid = getUid(prefix);
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
