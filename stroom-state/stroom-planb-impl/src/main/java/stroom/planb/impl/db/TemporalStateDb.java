package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.lmdb2.BBKV;
import stroom.planb.impl.db.TemporalState.Key;

import net.openhft.hashing.LongHashFunction;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.KeyRange;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;

public class TemporalStateDb extends AbstractLmdb<Key, StateValue> {

    public TemporalStateDb(final Path path,
                           final ByteBufferFactory byteBufferFactory) {
        this(path, byteBufferFactory, true, false);
    }

    public TemporalStateDb(final Path path,
                           final ByteBufferFactory byteBufferFactory,
                           final boolean overwrite,
                           final boolean readOnly) {
        super(path, byteBufferFactory, new TemporalStateSerde(byteBufferFactory), overwrite, readOnly);
    }


    public Optional<TemporalState> getState(final TemporalStateRequest request) {
        final long rowHash = LongHashFunction.xx3().hashBytes(request.key());
        final ByteBuffer start = byteBufferFactory.acquire(Long.BYTES + Long.BYTES);
        final ByteBuffer stop = byteBufferFactory.acquire(Long.BYTES);
        try {
            start.putLong(rowHash);
            start.putLong(request.effectiveTime());
            start.flip();

            stop.putLong(rowHash);
            stop.flip();

            final KeyRange<ByteBuffer> keyRange = KeyRange.closedBackward(start, stop);
            return read(readTxn -> {
                try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(readTxn, keyRange)) {
                    final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
                    while (iterator.hasNext()
                           && !Thread.currentThread().isInterrupted()) {
                        final BBKV kv = BBKV.create(iterator.next());
                        final long effectiveTime = kv.key().getLong(Long.BYTES);
                        final int keyLength = kv.val().getInt(0);
                        final byte[] keyBytes = new byte[keyLength];
                        kv.val().get(Integer.BYTES, keyBytes);

                        // We might have had a hash collision so test the key equality.
                        if (Arrays.equals(keyBytes, request.key())) {
                            final Key key = Key
                                    .builder()
                                    .name(keyBytes)
                                    .effectiveTime(effectiveTime)
                                    .build();
                            final StateValue value = serde.getVal(kv);
                            return Optional.of(new TemporalState(key, value));
                        }
                    }
                }
                return Optional.empty();
            });
        } finally {
            byteBufferFactory.release(start);
            byteBufferFactory.release(stop);
        }
    }

    // TODO: Note that LMDB does not free disk space just because you delete entries, instead it just frees pages for
    //  reuse. We might want to create a new compacted instance instead of deleting in place.
    @Override
    public void condense(final long condenseBeforeMs,
                         final long deleteBeforeMs) {
        write(writer -> {
            Key lastKey = null;
            StateValue lastValue = null;
            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(writer.getWriteTxn())) {
                final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
                while (iterator.hasNext()
                       && !Thread.currentThread().isInterrupted()) {
                    final BBKV kv = BBKV.create(iterator.next());
                    final Key key = serde.getKey(kv);
                    final StateValue value = serde.getVal(kv);

                    if (key.effectiveTime() <= deleteBeforeMs) {
                        // If this is data we no longer want to retain then delete it.
                        dbi.delete(writer.getWriteTxn(), kv.key(), kv.val());
                        writer.tryCommit();

                    } else {
                        if (lastKey != null &&
                            Arrays.equals(lastKey.bytes(), key.bytes()) &&
                            lastValue.byteBuffer().equals(value.byteBuffer())) {
                            if (key.effectiveTime() <= condenseBeforeMs) {
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
    }
}
