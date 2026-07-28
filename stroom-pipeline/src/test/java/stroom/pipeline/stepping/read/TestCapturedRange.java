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
