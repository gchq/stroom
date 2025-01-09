package stroom.planb.impl.io;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.planb.impl.io.RangedState.Key;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;

public class RangedStateReader extends AbstractLmdbReader<Key, StateValue> {

    public RangedStateReader(final Path path,
                             final ByteBufferFactory byteBufferFactory) {
        super(path, byteBufferFactory, new RangedStateSerde(byteBufferFactory));
    }

    public Optional<RangedState> getState(final RangedStateRequest request) {
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
                        if (keyEnd < request.key()) {
                            return Optional.empty();
                        } else if (keyStart <= request.key()) {
                            final Key key = Key.builder().keyStart(keyStart).keyEnd(keyEnd).build();
                            final StateValue value = serde.getVal(keyVal);
                            return Optional.of(new RangedState(key, value));
                        }
                    }
                }
            }
            return Optional.empty();
        } finally {
            byteBufferFactory.release(start);
        }
    }
}
