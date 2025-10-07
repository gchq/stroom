package stroom.lmdb;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.util.concurrent.ThreadUtil;

import org.lmdbjava.Cursor;
import org.lmdbjava.Dbi;
import org.lmdbjava.GetOp;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.function.BiConsumer;

public class LmdbIterableSupport {

    private static final UnsignedByteBufferComparator BUFFER_COMPARATOR = new UnsignedByteBufferComparator();

    public static IterableBuilder builder(final Txn<ByteBuffer> txn,
                                          final Dbi<ByteBuffer> dbi) {
        return new IterableBuilder(txn, dbi);
    }

    public static void iterate(final Txn<ByteBuffer> txn,
                               final Dbi<ByteBuffer> dbi,
                               final EntryConsumer consumer) {
        try (final Cursor<ByteBuffer> cursor = dbi.openCursor(txn)) {
            boolean isFound = cursor.first();
            while (isFound) {
                ThreadUtil.checkInterrupt();

                consumer.accept(cursor.key(), cursor.val());
                isFound = cursor.next();
            }
        }
    }

    public static abstract class AbstractIterableBuilder<B extends AbstractIterableBuilder<?>> {

        final Txn<ByteBuffer> txn;
        final Dbi<ByteBuffer> dbi;
        boolean reverse;

        AbstractIterableBuilder(final Txn<ByteBuffer> txn,
                                final Dbi<ByteBuffer> dbi) {
            this.txn = txn;
            this.dbi = dbi;
        }

        AbstractIterableBuilder(final AbstractIterableBuilder<?> streamBuilder) {
            this.txn = streamBuilder.txn;
            this.dbi = streamBuilder.dbi;
            this.reverse = streamBuilder.reverse;
        }

        public B reverse() {
            this.reverse = true;
            return self();
        }

        public B reverse(final boolean reverse) {
            this.reverse = reverse;
            return self();
        }

        abstract B self();

        public final LmdbIterable create() {
            final Cursor<ByteBuffer> cursor = dbi.openCursor(txn);
            try {
                final LmdbIterator iterator = createIterator(cursor);
                return new LmdbIterable(cursor, iterator);
            } catch (final Error | RuntimeException e) {
                cursor.close();
                throw e;
            }
        }

        public final void iterate(final EntryConsumer consumer) {
            try (final LmdbIterable iterable = create()) {
                for (final LmdbEntry entry : iterable) {
                    consumer.accept(entry.getKey(), entry.getVal());
                }
            }
        }

        public abstract LmdbIterator createIterator(Cursor<ByteBuffer> cursor);
    }

    public static class IterableBuilder extends AbstractIterableBuilder<IterableBuilder> {

        private IterableBuilder(final Txn<ByteBuffer> txn,
                                final Dbi<ByteBuffer> dbi) {
            super(txn, dbi);
        }

        public PrefixBuilder prefix(final ByteBuffer prefix) {
            final PrefixBuilder prefixBuilder = new PrefixBuilder(this);
            prefixBuilder.prefix(prefix);
            return prefixBuilder;
        }

        public RangeBuilder start(final ByteBuffer start) {
            return start(start, true);
        }

        public RangeBuilder start(final ByteBuffer start,
                                  final boolean startInclusive) {
            final RangeBuilder rangeBuilder = new RangeBuilder(this);
            return rangeBuilder.start(start, startInclusive);
        }

        public RangeBuilder stop(final ByteBuffer stop) {
            return stop(stop, true);
        }

        public RangeBuilder stop(final ByteBuffer stop,
                                 final boolean stopInclusive) {
            final RangeBuilder rangeBuilder = new RangeBuilder(this);
            return rangeBuilder.stop(stop, stopInclusive);
        }

        IterableBuilder self() {
            return this;
        }

        @Override
        public LmdbIterator createIterator(final Cursor<ByteBuffer> cursor) {
            if (reverse) {
                return new LmdbReversedIterator(cursor);
            }
            return new LmdbIterator(cursor);
        }
    }

    public static class PrefixBuilder extends AbstractIterableBuilder<PrefixBuilder> {

        private ByteBuffer prefix;

        private PrefixBuilder(final IterableBuilder streamBuilder) {
            super(streamBuilder);
        }

        public PrefixBuilder prefix(final ByteBuffer prefix) {
            this.prefix = prefix;
            return self();
        }

        @Override
        PrefixBuilder self() {
            return this;
        }

        @Override
        public LmdbIterator createIterator(final Cursor<ByteBuffer> cursor) {
            if (reverse) {
                if (prefix != null) {
                    return new LmdbPrefixReversedIterator(cursor, prefix);
                }
                return new LmdbReversedIterator(cursor);
            }
            if (prefix != null) {
                return new LmdbPrefixIterator(cursor, prefix);
            }
            return new LmdbIterator(cursor);
        }
    }

    public static class RangeBuilder extends AbstractIterableBuilder<RangeBuilder> {

        private ByteBuffer start;
        private ByteBuffer stop;
        private boolean startInclusive;
        private boolean stopInclusive;

        private RangeBuilder(final IterableBuilder streamBuilder) {
            super(streamBuilder);
        }

        public RangeBuilder start(final ByteBuffer start) {
            return start(start, true);
        }

        public RangeBuilder start(final ByteBuffer start,
                                  final boolean startInclusive) {
            this.start = start;
            this.startInclusive = startInclusive;
            return self();
        }

        public RangeBuilder stop(final ByteBuffer stop) {
            return stop(stop, true);
        }

        public RangeBuilder stop(final ByteBuffer stop,
                                 final boolean stopInclusive) {
            this.stop = stop;
            this.stopInclusive = stopInclusive;
            return self();
        }

        @Override
        RangeBuilder self() {
            return this;
        }

        @Override
        public LmdbIterator createIterator(final Cursor<ByteBuffer> cursor) {
            if (reverse) {
                // Check that start >= stop.
                if (start != null && stop != null) {
                    if (BUFFER_COMPARATOR.compare(start, stop) < 0) {
                        throw new IllegalStateException("Start key < stop key");
                    }
                }
                return new LmdbRangeReversedIterator(cursor, start, stop, startInclusive, stopInclusive);
            }

            // Check that start <= stop.
            if (start != null && stop != null) {
                if (BUFFER_COMPARATOR.compare(start, stop) > 0) {
                    throw new IllegalStateException("Start key > stop key");
                }
            }
            return new LmdbRangeIterator(cursor, start, stop, startInclusive, stopInclusive);
        }
    }

    public static class LmdbIterator implements Iterator<LmdbEntry> {

        final Cursor<ByteBuffer> cursor;
        Boolean isFound;
        final LmdbEntry entry = new LmdbEntry();

        private LmdbIterator(final Cursor<ByteBuffer> cursor) {
            this.cursor = cursor;
        }

        @Override
        public boolean hasNext() {
            ThreadUtil.checkInterrupt();
            if (isFound == null) {
                isFound = cursor.first();
            } else {
                isFound = cursor.next();
            }
            return isFound;
        }

        @Override
        public LmdbEntry next() {
            entry.setKey(cursor.key());
            entry.setVal(cursor.val());
            return entry;
        }

        @Override
        public void remove() {
            cursor.delete();
        }
    }

    private static class LmdbReversedIterator extends LmdbIterator {

        private LmdbReversedIterator(final Cursor<ByteBuffer> cursor) {
            super(cursor);
        }

        @Override
        public boolean hasNext() {
            ThreadUtil.checkInterrupt();
            if (isFound == null) {
                isFound = cursor.last();
            } else {
                isFound = cursor.prev();
            }
            return isFound;
        }
    }

    private static class LmdbPrefixIterator extends LmdbIterator {

        private final ByteBuffer prefix;

        private LmdbPrefixIterator(final Cursor<ByteBuffer> cursor,
                                   final ByteBuffer prefix) {
            super(cursor);
            this.prefix = prefix;
        }

        @Override
        public boolean hasNext() {
            ThreadUtil.checkInterrupt();
            if (isFound == null) {
                isFound = cursor.get(prefix, GetOp.MDB_SET_RANGE);
            } else {
                isFound = cursor.next();
            }

            if (isFound) {
                isFound = ByteBufferUtils.containsPrefix(cursor.key(), prefix);
            }

            return isFound;
        }
    }

    private static class LmdbPrefixReversedIterator extends LmdbIterator {

        private final ByteBuffer prefix;
        private final ByteBuffer oneBigger;

        private LmdbPrefixReversedIterator(final Cursor<ByteBuffer> cursor,
                                           final ByteBuffer prefix) {
            super(cursor);
            this.prefix = prefix;

            // Create a prefix that is one bit greater than then supplied prefix.
            oneBigger = incrementLeastSignificantByte(prefix);
        }

        @Override
        public boolean hasNext() {
            ThreadUtil.checkInterrupt();
            if (isFound == null) {
                // If we don't have a byte buffer that is one bigger than the prefix then go to the last row.
                if (oneBigger == null) {
                    isFound = cursor.last();
                } else {
                    // We have a byte buffer that is one bigger than the prefix so navigate to that row or the next
                    // biggest if no exact match.
                    isFound = cursor.get(oneBigger, GetOp.MDB_SET_RANGE);
                    if (isFound) {
                        // If we found a row then move to the previous row.
                        isFound = cursor.prev();
                    } else {
                        // We didn't find a row so go to the last row.
                        isFound = cursor.last();
                    }
                }
            } else {
                isFound = cursor.prev();
            }

            if (!isFound) {
                return false;
            }

            return ByteBufferUtils.containsPrefix(cursor.key(), prefix);
        }
    }

    private static class LmdbRangeIterator extends LmdbIterator {

        private final ByteBuffer start;
        private final ByteBuffer stop;
        private final boolean startInclusive;
        private final boolean stopInclusive;

        private LmdbRangeIterator(final Cursor<ByteBuffer> cursor,
                                  final ByteBuffer start,
                                  final ByteBuffer stop,
                                  final boolean startInclusive,
                                  final boolean stopInclusive) {
            super(cursor);
            this.start = start;
            this.stop = stop;
            this.startInclusive = startInclusive;
            this.stopInclusive = stopInclusive;
        }

        @Override
        public boolean hasNext() {
            ThreadUtil.checkInterrupt();
            if (isFound == null) {
                if (start == null) {
                    isFound = cursor.first();
                } else {
                    isFound = cursor.get(start, GetOp.MDB_SET_RANGE);
                    if (isFound && !startInclusive) {
                        while (isFound && start.equals(cursor.key())) {
                            // Loop until we move past the start key. Looping in case of duplicate keys.
                            ThreadUtil.checkInterrupt();
                            isFound = cursor.next();
                        }
                    }
                }
            } else {
                isFound = cursor.next();
            }

            if (isFound && stop != null) {
                final int compareResult = BUFFER_COMPARATOR.compare(stop, cursor.key());
                isFound = compareResult > 0 || (compareResult == 0 && stopInclusive);
            }

            return isFound;
        }
    }

    private static class LmdbRangeReversedIterator extends LmdbIterator {

        private final ByteBuffer start;
        private final ByteBuffer stop;
        private final boolean startInclusive;
        private final boolean stopInclusive;

        private LmdbRangeReversedIterator(final Cursor<ByteBuffer> cursor,
                                          final ByteBuffer start,
                                          final ByteBuffer stop,
                                          final boolean startInclusive,
                                          final boolean stopInclusive) {
            super(cursor);
            this.start = start;
            this.stop = stop;
            this.startInclusive = startInclusive;
            this.stopInclusive = stopInclusive;
        }

        @Override
        public boolean hasNext() {
            ThreadUtil.checkInterrupt();
            if (isFound == null) {
                if (start == null) {
                    isFound = cursor.last();
                } else {
                    isFound = cursor.get(start, GetOp.MDB_SET_RANGE) || cursor.last();
                    if (isFound) {
                        final int compareResult = BUFFER_COMPARATOR.compare(start, cursor.key());
                        if (compareResult < 0 || (compareResult == 0 && !startInclusive)) {
                            isFound = cursor.prev();
                        }
                    }
                }
            } else {
                isFound = cursor.prev();
            }

            if (isFound && stop != null) {
                final int compareResult = BUFFER_COMPARATOR.compare(stop, cursor.key());
                isFound = compareResult < 0 || (compareResult == 0 && stopInclusive);
            }

            return isFound;
        }
    }

    private static ByteBuffer incrementLeastSignificantByte(final ByteBuffer buffer) {
        if (buffer == null || buffer.remaining() == 0) {
            return null;
        }

        // Start from the least significant byte (closest to limit)
        for (int i = buffer.limit() - 1; i >= buffer.position(); i--) {
            final byte b = buffer.get(i);

            // Check if byte is not at max unsigned value (0xFF = 255 = -1 in signed)
            if (b != (byte) 0xFF) {
                final ByteBuffer oneBigger = ByteBuffer.allocateDirect(buffer.remaining());
                oneBigger.put(buffer.duplicate());
                oneBigger.flip();
                oneBigger.put(i - buffer.position(), (byte) (b + 1));
                return oneBigger;
            }
        }

        // All bytes are at maximum value
        return null;
    }

    public static class LmdbIterable implements Iterable<LmdbEntry>, AutoCloseable {

        private final Cursor<ByteBuffer> cursor;
        private final Iterator<LmdbEntry> iterator;

        public LmdbIterable(final Cursor<ByteBuffer> cursor, final Iterator<LmdbEntry> iterator) {
            this.cursor = cursor;
            this.iterator = iterator;
        }

        @Override
        public Iterator<LmdbEntry> iterator() {
            return iterator;
        }

        @Override
        public void close() {
            cursor.close();
        }
    }

    public interface EntryConsumer extends BiConsumer<ByteBuffer, ByteBuffer> {

        @Override
        void accept(ByteBuffer key, ByteBuffer val);
    }
}
