package stroom.planb.impl.dao;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.planb.impl.dao.TemporalRangedState.Key;
import stroom.planb.impl.dao.TemporalRangedState.Value;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;

public class TemporalRangedStateReader extends AbstractLmdbReader<Key, Value> {

    public TemporalRangedStateReader(final Path path,
                                     final ByteBufferFactory byteBufferFactory) {
        super(path, byteBufferFactory, new TemporalRangedStateSerde(byteBufferFactory));
    }

    public Optional<TemporalRangedState> getState(final TemporalRangedStateRequest request) {
        Optional<TemporalRangedState> result = Optional.empty();

        final ByteBuffer start = byteBufferFactory.acquire(Long.BYTES);
        try {
            start.putLong(request.key());
            start.flip();

            final KeyRange<ByteBuffer> keyRange = KeyRange.atLeastBackward(start);
            try (final Txn<ByteBuffer> readTxn = env.txnRead()) {
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
                            final Value value = serde.get(keyVal);
                            result = Optional.of(new TemporalRangedState(key, value));
                        }
                    }
                }
            }
        } finally {
            byteBufferFactory.release(start);
        }

        return result;
    }
}
