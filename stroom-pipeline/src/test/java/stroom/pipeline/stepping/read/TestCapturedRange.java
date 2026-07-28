package stroom.pipeline.stepping.read;

import stroom.pipeline.stepping.read.StoreStepResolver.CapturedRange;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rule that decides when a record is servable once elements are captured by independent producers: only
 * when <b>every</b> one of them has reached it. Get this wrong in the permissive direction and a step resolves
 * with some of its panes silently blank, which is the failure mode this whole design is most exposed to - the
 * same shape as the bug where a step landed on a record because the reused upstream was in range before the
 * reprocessed element had been written.
 */
class TestCapturedRange {

    private static CapturedRange range(final long first, final long last) {
        return new CapturedRange() {
            @Override
            public long first(final long partIndex) {
                return partIndex == 0 ? first : NONE;
            }

            @Override
            public long last(final long partIndex) {
                return partIndex == 0 ? last : NONE;
            }
        };
    }

    private static void assertRange(final CapturedRange actual, final long first, final long last) {
        assertThat(actual.first(0)).as("first").isEqualTo(first);
        assertThat(actual.last(0)).as("last").isEqualTo(last);
    }

    /**
     * A range holding only the listed records - what an element materialised on demand looks like.
     */
    private static CapturedRange holding(final long first, final long last, final Long... held) {
        final java.util.Set<Long> records = java.util.Set.of(held);
        return new CapturedRange() {
            @Override
            public long first(final long partIndex) {
                return partIndex == 0 ? first : NONE;
            }

            @Override
            public long last(final long partIndex) {
                return partIndex == 0 ? last : NONE;
            }

            @Override
            public boolean contains(final long partIndex, final long recordIndex) {
                return partIndex == 0 && records.contains(recordIndex);
            }
        };
    }

    @Test
    void testContainmentDefaultsToTheBounds() {
        // Anything captured contiguously answers from its bounds, which is every sweep.
        final CapturedRange range = range(2, 6);

        assertThat(range.contains(0, 2)).isTrue();
        assertThat(range.contains(0, 4)).isTrue();
        assertThat(range.contains(0, 6)).isTrue();
        assertThat(range.contains(0, 1)).isFalse();
        assertThat(range.contains(0, 7)).isFalse();
        assertThat(range.contains(1, 4)).as("a part with nothing holds nothing").isFalse();
    }

    @Test
    void testAHoleIsNotServableEvenThoughItIsWithinTheSpan() {
        // The whole reason containment is asked separately: this record is inside the element's span but was
        // never materialised, and serving it would show the user a blank pane.
        final CapturedRange sparse = holding(0, 100, 0L, 50L, 100L);

        assertThat(sparse.contains(0, 50)).isTrue();
        assertThat(sparse.contains(0, 51)).as("inside the span, but not held").isFalse();
        assertThat(sparse.last(0)).as("the span still reaches the end").isEqualTo(100);
    }

    @Test
    void testSpanningTakesBoundsFromOneSourceAndContentsFromAnother() {
        // The shape a mid-point replay needs: the upstream element swept the whole stream, so it knows where
        // the stream ends; the edited element holds only record 50. LAST must still mean record 999.
        final CapturedRange upstream = range(0, 999);
        final CapturedRange materialised = holding(50, 50, 50L);
        final CapturedRange combined = CapturedRange.spanning(upstream, materialised);

        assertThat(combined.first(0)).as("bounds come from upstream").isEqualTo(0);
        assertThat(combined.last(0)).as("so LAST does not land mid-stream").isEqualTo(999);
        assertThat(combined.contains(0, 50)).as("only what was materialised is servable").isTrue();
        assertThat(combined.contains(0, 51)).isFalse();
        assertThat(combined.contains(0, 999)).isFalse();
    }

    @Test
    void testAnIntersectionNeedsEveryContributorToHoldTheRecord() {
        // Spanning ranges do not overlap by index arithmetic alone; each has to actually hold it.
        final CapturedRange a = holding(0, 10, 1L, 2L, 3L);
        final CapturedRange b = holding(0, 10, 2L, 3L, 4L);
        final CapturedRange intersection = CapturedRange.intersectionOf(List.of(a, b));

        assertThat(intersection.contains(0, 2)).isTrue();
        assertThat(intersection.contains(0, 1)).as("only one of them holds it").isFalse();
        assertThat(intersection.contains(0, 4)).isFalse();
    }

    @Test
    void testAnEmptyIntersectionHoldsNothing() {
        assertThat(CapturedRange.intersectionOf(List.of()).contains(0, 0)).isFalse();
    }

    @Test
    void testASingleContributorIsItsOwnRange() {
        assertRange(CapturedRange.intersectionOf(List.of(range(0, 9))), 0, 9);
    }

    @Test
    void testTheSlowestContributorSetsTheEnd() {
        // The whole point: one element lagging holds the servable frontier back to where it has got to.
        assertRange(CapturedRange.intersectionOf(List.of(
                range(0, 9),
                range(0, 3),
                range(0, 7))), 0, 3);
    }

    @Test
    void testTheLatestStartSetsTheBeginning() {
        assertRange(CapturedRange.intersectionOf(List.of(
                range(0, 9),
                range(4, 9))), 4, 9);
    }

    @Test
    void testAContributorWithNothingMakesThePartUnservable() {
        // Not "ignore the ones that have nothing" - an element that has captured no records for this part
        // cannot contribute its pane, so no record of the part can be shown yet.
        assertRange(CapturedRange.intersectionOf(List.of(
                range(0, 9),
                range(CapturedRange.NONE, CapturedRange.NONE))), CapturedRange.NONE, CapturedRange.NONE);
    }

    @Test
    void testNonOverlappingRangesAreUnservable() {
        // first > last would otherwise be handed to the scan as a backwards range.
        assertRange(CapturedRange.intersectionOf(List.of(
                range(0, 2),
                range(5, 9))), CapturedRange.NONE, CapturedRange.NONE);
    }

    @Test
    void testTouchingRangesLeaveExactlyOneServableRecord() {
        assertRange(CapturedRange.intersectionOf(List.of(
                range(0, 5),
                range(5, 9))), 5, 5);
    }

    @Test
    void testNoContributorsMeansNothingIsServable() {
        // An empty set is "nothing has been captured", not "everything is available".
        assertRange(CapturedRange.intersectionOf(List.of()), CapturedRange.NONE, CapturedRange.NONE);
    }

    @Test
    void testPartsAreIndependent() {
        final CapturedRange perPart = new CapturedRange() {
            @Override
            public long first(final long partIndex) {
                return partIndex == 1 ? 2 : 0;
            }

            @Override
            public long last(final long partIndex) {
                return partIndex == 1 ? 4 : 9;
            }
        };
        final CapturedRange intersection = CapturedRange.intersectionOf(List.of(perPart, range(0, 6)));

        assertThat(intersection.first(0)).isEqualTo(0);
        assertThat(intersection.last(0)).isEqualTo(6);
        // range(...) only knows about part 0, so part 1 has a contributor with nothing.
        assertThat(intersection.first(1)).isEqualTo(CapturedRange.NONE);
    }

    @Test
    void testTheRangeFollowsAContributorThatMovesOn() {
        // Recomputed per call, not snapshotted: contributors are live producers. A cached range would either
        // hide records that have since arrived or admit ones that have not.
        final long[] movingLast = {3};
        final CapturedRange moving = new CapturedRange() {
            @Override
            public long first(final long partIndex) {
                return 0;
            }

            @Override
            public long last(final long partIndex) {
                return movingLast[0];
            }
        };
        final CapturedRange intersection = CapturedRange.intersectionOf(List.of(moving, range(0, 9)));

        assertThat(intersection.last(0)).isEqualTo(3);
        movingLast[0] = 8;
        assertThat(intersection.last(0)).as("the frontier advanced with the producer").isEqualTo(8);
    }
}
