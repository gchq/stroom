package stroom.lmdb;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.util.concurrent.ThreadUtil;

import org.lmdbjava.Cursor;
import org.lmdbjava.Dbi;
import org.lmdbjava.GetOp;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class LmdbStreamSupport {

    private static final UnsignedByteBufferComparator BUFFER_COMPARATOR = new UnsignedByteBufferComparator();
    private static final LmdbEntryComparator ENTRY_COMPARATOR = new LmdbEntryComparator();
    private static final ReversedLmdbEntryComparator REVERSED_ENTRY_COMPARATOR = new ReversedLmdbEntryComparator();

    public static Stream<LmdbEntry> stream(final Txn<ByteBuffer> txn,
                                           final Dbi<ByteBuffer> dbi) {
        return streamBuilder(txn, dbi).create();
    }

    public static StreamBuilder streamBuilder(final Txn<ByteBuffer> txn,
                                              final Dbi<ByteBuffer> dbi) {
        return new StreamBuilder(txn, dbi);
    }

    public static abstract class AbstractStreamBuilder<B extends AbstractStreamBuilder<?>> {

        final Txn<ByteBuffer> txn;
        final Dbi<ByteBuffer> dbi;
        boolean reverse;

        AbstractStreamBuilder(final Txn<ByteBuffer> txn,
                              final Dbi<ByteBuffer> dbi) {
            this.txn = txn;
            this.dbi = dbi;
        }

        AbstractStreamBuilder(final AbstractStreamBuilder<?> streamBuilder) {
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

        public abstract Stream<LmdbEntry> create();
    }

    public static class StreamBuilder extends AbstractStreamBuilder<StreamBuilder> {

        private StreamBuilder(final Txn<ByteBuffer> txn,
                              final Dbi<ByteBuffer> dbi) {
            super(txn, dbi);
        }

        public PrefixStreamBuilder prefix(final ByteBuffer prefix) {
            final PrefixStreamBuilder prefixStreamBuilder = new PrefixStreamBuilder(this);
            prefixStreamBuilder.prefix(prefix);
            return prefixStreamBuilder;
        }

        public RangeStreamBuilder start(final ByteBuffer start) {
            return start(start, true);
        }

        public RangeStreamBuilder start(final ByteBuffer start,
                                        final boolean startInclusive) {
            final RangeStreamBuilder rangeStreamBuilder = new RangeStreamBuilder(this);
            return rangeStreamBuilder.start(start, startInclusive);
        }

        public RangeStreamBuilder stop(final ByteBuffer stop) {
            return stop(stop, true);
        }

        public RangeStreamBuilder stop(final ByteBuffer stop,
                                       final boolean stopInclusive) {
            final RangeStreamBuilder rangeStreamBuilder = new RangeStreamBuilder(this);
            return rangeStreamBuilder.stop(stop, stopInclusive);
        }

        StreamBuilder self() {
            return this;
        }

        public Stream<LmdbEntry> create() {
            final Cursor<ByteBuffer> cursor = dbi.openCursor(txn);
            try {
                final LmdbSpliterator spliterator;
                if (reverse) {
                    spliterator = new LmdbReversedSpliterator(cursor);
                } else {
                    spliterator = new LmdbSpliterator(cursor);
                }
                return StreamSupport.stream(spliterator, false)
                        .onClose(cursor::close);
            } catch (final Error | RuntimeException e) {
                cursor.close();
                throw e;
            }
        }
    }

    public static class PrefixStreamBuilder extends AbstractStreamBuilder<PrefixStreamBuilder> {

        private ByteBuffer prefix;

        private PrefixStreamBuilder(final StreamBuilder streamBuilder) {
            super(streamBuilder);
        }

        public PrefixStreamBuilder prefix(final ByteBuffer prefix) {
            this.prefix = prefix;
            return self();
        }

        @Override
        PrefixStreamBuilder self() {
            return this;
        }

        public Stream<LmdbEntry> create() {
            final Cursor<ByteBuffer> cursor = dbi.openCursor(txn);
            try {
                final LmdbSpliterator spliterator;
                if (reverse) {
                    if (prefix != null) {
                        spliterator = new LmdbPrefixReversedSpliterator(cursor, prefix);
                    } else {
                        spliterator = new LmdbReversedSpliterator(cursor);
                    }
                } else {
                    if (prefix != null) {
                        spliterator = new LmdbPrefixSpliterator(cursor, prefix);
                    } else {
                        spliterator = new LmdbSpliterator(cursor);
                    }
                }
                return StreamSupport.stream(spliterator, false)
                        .onClose(cursor::close);
            } catch (final Error | RuntimeException e) {
                cursor.close();
                throw e;
            }
        }
    }

    public static class RangeStreamBuilder extends AbstractStreamBuilder<RangeStreamBuilder> {

        private ByteBuffer start;
        private ByteBuffer stop;
        private boolean startInclusive;
        private boolean stopInclusive;

        private RangeStreamBuilder(final StreamBuilder streamBuilder) {
            super(streamBuilder);
        }

        public RangeStreamBuilder start(final ByteBuffer start) {
            return start(start, true);
        }

        public RangeStreamBuilder start(final ByteBuffer start,
                                        final boolean startInclusive) {
            this.start = start;
            this.startInclusive = startInclusive;
            return self();
        }

        public RangeStreamBuilder stop(final ByteBuffer stop) {
            return stop(stop, true);
        }

        public RangeStreamBuilder stop(final ByteBuffer stop,
                                       final boolean stopInclusive) {
            this.stop = stop;
            this.stopInclusive = stopInclusive;
            return self();
        }

        @Override
        RangeStreamBuilder self() {
            return this;
        }

        public Stream<LmdbEntry> create() {
            final Cursor<ByteBuffer> cursor = dbi.openCursor(txn);
            try {
                final LmdbSpliterator spliterator;
                if (reverse) {
                    // Check that start >= stop.
                    if (start != null && stop != null) {
                        if (BUFFER_COMPARATOR.compare(start, stop) < 0) {
                            throw new IllegalStateException("Start key < stop key");
                        }
                    }
                    spliterator = new LmdbRangeReversedSpliterator(cursor, start, stop, startInclusive, stopInclusive);
                } else {
                    // Check that start <= stop.
                    if (start != null && stop != null) {
                        if (BUFFER_COMPARATOR.compare(start, stop) > 0) {
                            throw new IllegalStateException("Start key > stop key");
                        }
                    }
                    spliterator = new LmdbRangeSpliterator(cursor, start, stop, startInclusive, stopInclusive);
                }
                return StreamSupport.stream(spliterator, false)
                        .onClose(cursor::close);
            } catch (final Error | RuntimeException e) {
                cursor.close();
                throw e;
            }
        }
    }

    private static class LmdbSpliterator implements Spliterator<LmdbEntry> {

        final Cursor<ByteBuffer> cursor;
        Boolean isFound;
        final LmdbEntry entry = new LmdbEntry();

        private LmdbSpliterator(final Cursor<ByteBuffer> cursor) {
            this.cursor = cursor;
        }

        public final boolean tryAdvance(final Consumer<? super LmdbEntry> action) {
            if (hasNext()) {
                action.accept(createEntry());
                return true;
            }
            return false;
        }

        public final void forEachRemaining(final Consumer<? super LmdbEntry> action) {
            while (hasNext()) {
                action.accept(createEntry());
            }
        }

        boolean hasNext() {
            ThreadUtil.checkInterrupt();
            if (isFound == null) {
                isFound = cursor.first();
            } else {
                isFound = cursor.next();
            }
            return isFound;
        }

        private LmdbEntry createEntry() {
            entry.setKey(cursor.key());
            entry.setVal(cursor.val());
            return entry;
        }

        @Override
        public final Spliterator<LmdbEntry> trySplit() {
            // Splitting not allowed.
            return null;
        }

        @Override
        public final long estimateSize() {
            return Long.MAX_VALUE;
        }

        @Override
        public final int characteristics() {
            return Spliterator.ORDERED | Spliterator.DISTINCT | Spliterator.SORTED | Spliterator.NONNULL;
        }

        @Override
        public Comparator<? super LmdbEntry> getComparator() {
            return ENTRY_COMPARATOR;
        }
    }

    private static class LmdbReversedSpliterator extends LmdbSpliterator {

        private LmdbReversedSpliterator(final Cursor<ByteBuffer> cursor) {
            super(cursor);
        }

        @Override
        boolean hasNext() {
            ThreadUtil.checkInterrupt();
            if (isFound == null) {
                isFound = cursor.last();
            } else {
                isFound = cursor.prev();
            }
            return isFound;
        }

        @Override
        public Comparator<? super LmdbEntry> getComparator() {
            return REVERSED_ENTRY_COMPARATOR;
        }
    }

    private static class LmdbPrefixSpliterator extends LmdbSpliterator {

        private final ByteBuffer prefix;

        private LmdbPrefixSpliterator(final Cursor<ByteBuffer> cursor,
                                      final ByteBuffer prefix) {
            super(cursor);
            this.prefix = prefix;
        }

        @Override
        boolean hasNext() {
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

    private static class LmdbPrefixReversedSpliterator extends LmdbSpliterator {

        private final ByteBuffer prefix;
        private final ByteBuffer oneBigger;

        private LmdbPrefixReversedSpliterator(final Cursor<ByteBuffer> cursor,
                                              final ByteBuffer prefix) {
            super(cursor);
            this.prefix = prefix;

            // Create a prefix that is one bit greater than then supplied prefix.
            oneBigger = incrementLeastSignificantByte(prefix);
        }

        @Override
        boolean hasNext() {
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

        @Override
        public Comparator<? super LmdbEntry> getComparator() {
            return REVERSED_ENTRY_COMPARATOR;
        }
    }

    private static class LmdbRangeSpliterator extends LmdbSpliterator {

        private final ByteBuffer start;
        private final ByteBuffer stop;
        private final boolean startInclusive;
        private final boolean stopInclusive;

        private LmdbRangeSpliterator(final Cursor<ByteBuffer> cursor,
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
        boolean hasNext() {
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

    private static class LmdbRangeReversedSpliterator extends LmdbSpliterator {

        private final ByteBuffer start;
        private final ByteBuffer stop;
        private final boolean startInclusive;
        private final boolean stopInclusive;

        private LmdbRangeReversedSpliterator(final Cursor<ByteBuffer> cursor,
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
        boolean hasNext() {
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

        @Override
        public Comparator<? super LmdbEntry> getComparator() {
            return REVERSED_ENTRY_COMPARATOR;
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

    private static class LmdbEntryComparator implements Comparator<LmdbEntry> {

        @Override
        public int compare(final LmdbEntry o1, final LmdbEntry o2) {
            return BUFFER_COMPARATOR.compare(o1.key, o2.key);
        }
    }

    private static class ReversedLmdbEntryComparator implements Comparator<LmdbEntry> {

        @Override
        public int compare(final LmdbEntry o1, final LmdbEntry o2) {
            return BUFFER_COMPARATOR.compare(o2.key, o1.key);
        }
    }
}
