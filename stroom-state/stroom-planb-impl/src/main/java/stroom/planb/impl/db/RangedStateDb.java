package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.planb.impl.db.RangedState.Key;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.KeyRange;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;

public class RangedStateDb extends AbstractLmdb<Key, StateValue> {

    public RangedStateDb(final Path path,
                         final ByteBufferFactory byteBufferFactory) {
        this(path, byteBufferFactory, true, false);
    }

    public RangedStateDb(final Path path,
                         final ByteBufferFactory byteBufferFactory,
                         final boolean overwrite,
                         final boolean readOnly) {
        super(path, byteBufferFactory, new RangedStateSerde(byteBufferFactory), overwrite, readOnly);
    }

    public Optional<RangedState> getState(final RangedStateRequest request) {
        final ByteBuffer start = byteBufferFactory.acquire(Long.BYTES);
        try {
            start.putLong(request.key());
            start.flip();

            final KeyRange<ByteBuffer> keyRange = KeyRange.atLeastBackward(start);

            return read(readTxn -> {
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
                return Optional.empty();
            });
        } finally {
            byteBufferFactory.release(start);
        }
    }
}
