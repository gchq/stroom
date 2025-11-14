package stroom.lmdb.stream;

import java.nio.ByteBuffer;

public abstract class LmdbKeyRange {

    private static final UnsignedByteBufferComparator BUFFER_COMPARATOR = new UnsignedByteBufferComparator();
    private static final LmdbKeyRange ALL = builder().build();
    private static final LmdbKeyRange ALL_REVERSE = builder().reverse().build();

    public static LmdbKeyRange all() {
        return ALL;
    }

    public static LmdbKeyRange allReverse() {
        return ALL_REVERSE;
    }

    public static AllBuilder builder() {
        return new AllBuilder();
    }

    private abstract static class BaseBuilder<B extends BaseBuilder<?>> {

        boolean reverse;

        private BaseBuilder() {
        }

        private BaseBuilder(final BaseBuilder<?> builder) {
            this.reverse = builder.reverse;
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

        public abstract LmdbKeyRange build();
    }

    public static class AllBuilder
            extends BaseBuilder<AllBuilder> {

        private AllBuilder() {
        }

        private AllBuilder(final BaseBuilder<?> builder) {
            super(builder);
        }

        public PrefixBuilder prefix(final ByteBuffer prefix) {
            final PrefixBuilder keyRange = new PrefixBuilder(this);
            keyRange.prefix(prefix);
            return keyRange;
        }

        public RangeBuilder start(final ByteBuffer start) {
            return start(start, true);
        }

        public RangeBuilder start(final ByteBuffer start,
                                  final boolean startInclusive) {
            final RangeBuilder range = new RangeBuilder(this);
            return range.start(start, startInclusive);
        }

        public RangeBuilder stop(final ByteBuffer stop) {
            return stop(stop, true);
        }

        public RangeBuilder stop(final ByteBuffer stop,
                                 final boolean stopInclusive) {
            final RangeBuilder range = new RangeBuilder(this);
            return range.stop(stop, stopInclusive);
        }

        AllBuilder self() {
            return this;
        }

        @Override
        public LmdbKeyRange build() {
            return new All(reverse);
        }
    }

    static class All extends LmdbKeyRange {

        final boolean reverse;

        private All(final boolean reverse) {
            this.reverse = reverse;
        }
    }

    public static class PrefixBuilder extends BaseBuilder<PrefixBuilder> {

        ByteBuffer prefix;

        private PrefixBuilder(final AllBuilder rootKeyRange) {
            super(rootKeyRange);
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
        public LmdbKeyRange build() {
            if (prefix == null) {
                return new All(reverse);
            }
            return new Prefix(prefix, reverse);
        }
    }

    static class Prefix extends LmdbKeyRange {

        final ByteBuffer prefix;
        final boolean reverse;

        private Prefix(final ByteBuffer prefix, final boolean reverse) {
            this.prefix = prefix;
            this.reverse = reverse;
        }
    }

    public static class RangeBuilder extends BaseBuilder<RangeBuilder> {

        ByteBuffer start;
        ByteBuffer stop;
        boolean startInclusive;
        boolean stopInclusive;

        private RangeBuilder(final AllBuilder rootKeyRange) {
            super(rootKeyRange);
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
        public LmdbKeyRange build() {
            if (reverse) {
                // Check that start >= stop.
                if (start != null && stop != null) {
                    if (BUFFER_COMPARATOR.compare(start, stop) < 0) {
                        throw new IllegalStateException("Start key < stop key");
                    }
                }
            } else {
                // Check that start <= stop.
                if (start != null && stop != null) {
                    if (BUFFER_COMPARATOR.compare(start, stop) > 0) {
                        throw new IllegalStateException("Start key > stop key");
                    }
                }
            }

            return new Range(start, stop, startInclusive, stopInclusive, reverse);
        }
    }

    static class Range extends LmdbKeyRange {

        final ByteBuffer start;
        final ByteBuffer stop;
        final boolean startInclusive;
        final boolean stopInclusive;
        final boolean reverse;


        private Range(final ByteBuffer start,
                      final ByteBuffer stop,
                      final boolean startInclusive,
                      final boolean stopInclusive,
                      final boolean reverse) {
            this.start = start;
            this.stop = stop;
            this.startInclusive = startInclusive;
            this.stopInclusive = stopInclusive;
            this.reverse = reverse;
        }
    }
}
