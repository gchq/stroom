package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.lmdb2.BBKV;
import stroom.planb.impl.db.RangedState.Key;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.RangedStateSettings;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.KeyRange;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;

public class RangedStateDb extends AbstractDb<Key, StateValue> {

    private static final ByteBuffer ZERO = ByteBuffer.allocateDirect(0);

    RangedStateDb(final Path path,
                  final ByteBufferFactory byteBufferFactory) {
        this(
                path,
                byteBufferFactory,
                RangedStateSettings.builder().build(),
                false);
    }

    RangedStateDb(final Path path,
                  final ByteBufferFactory byteBufferFactory,
                  final RangedStateSettings settings,
                  final boolean readOnly) {
        super(
                path,
                byteBufferFactory,
                new RangedStateSerde(byteBufferFactory),
                settings.getMaxStoreSize(),
                settings.getOverwrite(),
                readOnly);
    }

    public static RangedStateDb create(final Path path,
                                       final ByteBufferFactory byteBufferFactory,
                                       final PlanBDoc doc,
                                       final boolean readOnly) {
        return new RangedStateDb(path, byteBufferFactory, getSettings(doc), readOnly);
    }

    private static RangedStateSettings getSettings(final PlanBDoc doc) {
        if (doc.getSettings() instanceof final RangedStateSettings settings) {
            return settings;
        }
        return RangedStateSettings.builder().build();
    }

    public Optional<RangedState> getState(final RangedStateRequest request) {
        final ByteBuffer start = byteBufferFactory.acquire(Long.BYTES);
        try {
            start.putLong(request.key() + 1);
            start.flip();

//            read(readTxn -> {
//                try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(readTxn)) {
//                    final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
//                    while (iterator.hasNext()
//                           && !Thread.currentThread().isInterrupted()) {
//                        final BBKV kv = BBKV.create(iterator.next());
//                        final long keyStart = kv.key().getLong(0);
//                        final long keyEnd = kv.key().getLong(Long.BYTES);
//                        System.out.println("start=" + keyStart + ", keyEnd=" + keyEnd);
//                    }
//                }
//                return Optional.empty();
//            });

            final KeyRange<ByteBuffer> keyRange = KeyRange.openBackward(start, ZERO);
            return read(readTxn -> {
                try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(readTxn, keyRange)) {
                    final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
                    while (iterator.hasNext()
                           && !Thread.currentThread().isInterrupted()) {
                        final BBKV kv = BBKV.create(iterator.next());
                        final long keyStart = kv.key().getLong(0);
                        final long keyEnd = kv.key().getLong(Long.BYTES);
                        if (keyEnd < request.key()) {
                            return Optional.empty();
                        } else if (keyStart <= request.key()) {
                            final Key key = Key.builder().keyStart(keyStart).keyEnd(keyEnd).build();
                            final StateValue value = serde.getVal(kv);
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
