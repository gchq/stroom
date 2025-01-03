package stroom.planb.impl.dao;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.planb.impl.dao.TemporalState.Key;
import stroom.planb.impl.dao.TemporalState.Value;

import net.openhft.hashing.LongHashFunction;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;

public class TemporalStateReader extends AbstractLmdbReader<Key, Value> {

    public TemporalStateReader(final Path path,
                               final ByteBufferFactory byteBufferFactory) {
        super(path, byteBufferFactory, new TemporalStateSerde(byteBufferFactory));
    }

    public synchronized Optional<TemporalState> getState(final TemporalStateRequest request) {
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
            try (final Txn<ByteBuffer> readTxn = env.txnRead()) {
                try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(readTxn, keyRange)) {
                    final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
                    while (iterator.hasNext()
                           && !Thread.currentThread().isInterrupted()) {
                        final KeyVal<ByteBuffer> keyVal = iterator.next();
                        final long effectiveTime = keyVal.key().getLong(Long.BYTES);
                        final int keyLength = keyVal.val().getInt(0);
                        final byte[] keyBytes = new byte[keyLength];
                        keyVal.val().get(Integer.BYTES, keyBytes);

                        // We might have had a hash collision so test the key equality.
                        if (Arrays.equals(keyBytes, request.key())) {
                            final Key key = Key
                                    .builder()
                                    .name(keyBytes)
                                    .effectiveTime(effectiveTime)
                                    .build();
                            final Value value = serde.get(keyVal);
                            return Optional.of(new TemporalState(key, value));
                        }
                    }
                }
            }
        } finally {
            byteBufferFactory.release(start);
            byteBufferFactory.release(stop);
        }

        return Optional.empty();
    }
}
