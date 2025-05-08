package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.lmdb2.BBKV;
import stroom.planb.impl.db.RangedState.Key;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.RangedStateSettings;
import stroom.query.language.functions.Val;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.KeyRange;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Iterator;

public class RangedStateDb extends AbstractDb<Key, Val> {

    private static final ByteBuffer ZERO = ByteBuffer.allocateDirect(0);

    RangedStateDb(final Path path,
                  final ByteBuffers byteBuffers) {
        this(
                path,
                byteBuffers,
                RangedStateSettings.builder().build(),
                false);
    }

    RangedStateDb(final Path path,
                  final ByteBuffers byteBuffers,
                  final RangedStateSettings settings,
                  final boolean readOnly) {
        super(
                path,
                byteBuffers,
                new RangedStateSerde(byteBuffers),
                settings.getMaxStoreSize(),
                settings.getOverwrite(),
                readOnly);
    }

    public static RangedStateDb create(final Path path,
                                       final ByteBuffers byteBuffers,
                                       final PlanBDoc doc,
                                       final boolean readOnly) {
        return new RangedStateDb(path, byteBuffers, getSettings(doc), readOnly);
    }

    private static RangedStateSettings getSettings(final PlanBDoc doc) {
        if (doc.getSettings() instanceof final RangedStateSettings settings) {
            return settings;
        }
        return RangedStateSettings.builder().build();
    }

    public RangedState getState(final RangedStateRequest request) {
        return byteBuffers.useLong(request.key() + 1, start -> {
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
                            return null;
                        } else if (keyStart <= request.key()) {
                            final Key key = Key.builder().keyStart(keyStart).keyEnd(keyEnd).build();
                            final Val value = serde.getVal(kv);
                            return new RangedState(key, value);
                        }
                    }
                }
                return null;
            });
        });
    }
}
