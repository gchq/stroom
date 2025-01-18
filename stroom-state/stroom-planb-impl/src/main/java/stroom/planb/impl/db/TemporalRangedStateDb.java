package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.planb.impl.db.TemporalRangedState.Key;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.KeyRange;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Iterator;
import java.util.Optional;

public class TemporalRangedStateDb extends AbstractLmdb<Key, StateValue> {

    public TemporalRangedStateDb(final Path path,
                                 final ByteBufferFactory byteBufferFactory) {
        this(path, byteBufferFactory, true, false);
    }

    public TemporalRangedStateDb(final Path path,
                                 final ByteBufferFactory byteBufferFactory,
                                 final boolean overwrite,
                                 final boolean readOnly) {
        super(path, byteBufferFactory, new TemporalRangedStateSerde(byteBufferFactory), overwrite, readOnly);
    }


    public Optional<TemporalRangedState> getState(final TemporalRangedStateRequest request) {
        final ByteBuffer start = byteBufferFactory.acquire(Long.BYTES);
        try {
            start.putLong(request.key());
            start.flip();

            final KeyRange<ByteBuffer> keyRange = KeyRange.atLeastBackward(start);
            return read(readTxn -> {
                Optional<TemporalRangedState> result = Optional.empty();
                try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(readTxn, keyRange)) {
                    final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
                    while (iterator.hasNext()
                           && !Thread.currentThread().isInterrupted()) {
                        final KeyVal<ByteBuffer> keyVal = iterator.next();
                        final long keyStart = keyVal.key().getLong(0);
                        final long keyEnd = keyVal.key().getLong(Long.BYTES);
                        final long effectiveTime = keyVal.key().getLong(Long.BYTES + Long.BYTES);
                        if (keyEnd < request.key()) {
                            return result;
                        } else if (effectiveTime >= request.effectiveTime() &&
                                   keyStart <= request.key()) {
                            final Key key = Key
                                    .builder()
                                    .keyStart(keyStart)
                                    .keyEnd(keyEnd)
                                    .effectiveTime(effectiveTime)
                                    .build();
                            final StateValue value = serde.getVal(keyVal);
                            result = Optional.of(new TemporalRangedState(key, value));
                        }
                    }
                }
                return result;
            });
        } finally {
            byteBufferFactory.release(start);
        }
    }

    // TODO: Note that LMDB does not free disk space just because you delete entries, instead it just fees pages for
    //  reuse. We might want to create a new compacted instance instead of deleting in place.
    @Override
    public void condense(final Instant maxAge) {
        final long maxTime = maxAge.toEpochMilli();

        write(writer -> {
            Key lastKey = null;
            StateValue lastValue = null;
            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(writer.getWriteTxn())) {
                final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
                while (iterator.hasNext()
                       && !Thread.currentThread().isInterrupted()) {
                    final KeyVal<ByteBuffer> keyVal = iterator.next();
                    final Key key = serde.getKey(keyVal);
                    final StateValue value = serde.getVal(keyVal);

                    if (lastKey != null &&
                        lastKey.keyStart() == key.keyStart() &&
                        lastKey.keyEnd() == key.keyEnd() &&
                        lastValue.byteBuffer().equals(value.byteBuffer())) {
                        if (key.effectiveTime() <= maxTime) {
                            // If the key and value are the same then delete the duplicate entry.
                            dbi.delete(writer.getWriteTxn(), keyVal.key(), keyVal.val());
                            writer.tryCommit();
                        }
                    }

                    lastKey = key;
                    lastValue = value;
                }
            }
        });
    }
}
