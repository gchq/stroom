package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.lmdb2.BBKV;
import stroom.planb.impl.db.TemporalRangedState.Key;
import stroom.planb.impl.db.state.StateValue;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.TemporalRangedStateSettings;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.KeyRange;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Iterator;

public class TemporalRangedStateDb extends AbstractDb<Key, StateValue> {

    private static final ByteBuffer ZERO = ByteBuffer.allocateDirect(0);

    TemporalRangedStateDb(final Path path,
                          final ByteBuffers byteBuffers) {
        this(
                path,
                byteBuffers,
                TemporalRangedStateSettings.builder().build(),
                false);
    }

    TemporalRangedStateDb(final Path path,
                          final ByteBuffers byteBuffers,
                          final TemporalRangedStateSettings settings,
                          final boolean readOnly) {
        super(
                path,
                byteBuffers,
                new TemporalRangedStateSerde(byteBuffers),
                settings.getMaxStoreSize(),
                settings.getOverwrite(),
                readOnly);
    }

    public static TemporalRangedStateDb create(final Path path,
                                               final ByteBuffers byteBuffers,
                                               final PlanBDoc doc,
                                               final boolean readOnly) {
        return new TemporalRangedStateDb(path, byteBuffers, getSettings(doc), readOnly);
    }

    private static TemporalRangedStateSettings getSettings(final PlanBDoc doc) {
        if (doc.getSettings() instanceof final TemporalRangedStateSettings settings) {
            return settings;
        }
        return TemporalRangedStateSettings.builder().build();
    }

    public TemporalRangedState getState(final TemporalRangedStateRequest request) {
        return byteBuffers.useLong(request.key() + 1, start -> {
//            read(readTxn -> {
//                try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(readTxn)) {
//                    final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
//                    while (iterator.hasNext()
//                           && !Thread.currentThread().isInterrupted()) {
//                        final BBKV kv = BBKV.create(iterator.next());
//                        final long keyStart = kv.key().getLong(0);
//                        final long keyEnd = kv.key().getLong(Long.BYTES);
//                        final long effectiveTime = kv.key().getLong(Long.BYTES + Long.BYTES);
//                        System.out.println("start=" +
//                                           keyStart +
//                                           ", keyEnd=" +
//                                           keyEnd +
//                                           ", effectiveTime=" +
//                                           DateUtil.createNormalDateTimeString(effectiveTime));
//                    }
//                }
//                return Optional.empty();
//            });

            final KeyRange<ByteBuffer> keyRange = KeyRange.openBackward(start, ZERO);
            return read(readTxn -> {
                TemporalRangedState result = null;
                try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(readTxn, keyRange)) {
                    final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
                    while (iterator.hasNext()
                           && !Thread.currentThread().isInterrupted()) {
                        final BBKV kv = BBKV.create(iterator.next());
                        final long keyStart = kv.key().getLong(0);
                        final long keyEnd = kv.key().getLong(Long.BYTES);
                        final long effectiveTime = kv.key().getLong(Long.BYTES + Long.BYTES);
                        if (keyEnd < request.key()) {
                            return result;
                        } else if (effectiveTime <= request.effectiveTime() &&
                                   keyStart <= request.key()) {
                            final Key key = Key
                                    .builder()
                                    .keyStart(keyStart)
                                    .keyEnd(keyEnd)
                                    .effectiveTime(effectiveTime)
                                    .build();
                            final StateValue value = serde.getVal(kv);
                            result = new TemporalRangedState(key, value);
                        }
                    }
                }
                return result;
            });
        });
    }

    // TODO: Note that LMDB does not free disk space just because you delete entries, instead it just frees pages for
    //  reuse. We might want to create a new compacted instance instead of deleting in place.
    @Override
    public void condense(final long condenseBeforeMs,
                         final long deleteBeforeMs) {
        read(readTxn -> {
            write(writer -> {
                Key lastKey = null;
                StateValue lastValue = null;
                try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(readTxn)) {
                    final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
                    while (iterator.hasNext()
                           && !Thread.currentThread().isInterrupted()) {
                        final BBKV kv = BBKV.create(iterator.next());
                        final Key key = serde.getKey(kv);
                        final StateValue value = serde.getVal(kv);

                        if (key.getEffectiveTime() <= deleteBeforeMs) {
                            // If this is data we no longer want to retain then delete it.
                            dbi.delete(writer.getWriteTxn(), kv.key(), kv.val());
                            writer.tryCommit();

                        } else {
                            if (lastKey != null &&
                                lastKey.getKeyStart() == key.getKeyStart() &&
                                lastKey.getKeyEnd() == key.getKeyEnd() &&
                                lastValue.getByteBuffer().equals(value.getByteBuffer())) {
                                if (key.getEffectiveTime() <= condenseBeforeMs) {
                                    // If the key and value are the same then delete the duplicate entry.
                                    dbi.delete(writer.getWriteTxn(), kv.key(), kv.val());
                                    writer.tryCommit();
                                }
                            }

                            lastKey = key;
                            lastValue = value;
                        }
                    }
                }
            });
            return null;
        });
    }
}
