package stroom.floormap.client.playback;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestFloorMapHistogramBuckets {

    private static final long SECOND = 1_000L;
    private static final long MINUTE = 60L * SECOND;
    private static final long HOUR = 60L * MINUTE;
    private static final long DAY = 24L * HOUR;

    @Test
    void theLadderPicksTheWidthTheRangeCallsFor() {
        assertThat(FloorMapHistogramBuckets.widthFor(2L * HOUR)).isEqualTo(5L * MINUTE);
        assertThat(FloorMapHistogramBuckets.widthFor(3L * HOUR)).isEqualTo(10L * MINUTE);
        assertThat(FloorMapHistogramBuckets.widthFor(12L * HOUR)).isEqualTo(10L * MINUTE);
        assertThat(FloorMapHistogramBuckets.widthFor(DAY)).isEqualTo(HOUR);
        assertThat(FloorMapHistogramBuckets.widthFor(3L * DAY)).isEqualTo(DAY);
        assertThat(FloorMapHistogramBuckets.widthFor(365L * DAY)).isEqualTo(30L * DAY);
    }

    @Test
    void eachRungIsInclusiveOfItsOwnBoundary() {
        // A range exactly on a boundary takes the coarser width, not the finer one below it.
        assertThat(FloorMapHistogramBuckets.widthFor(3L * HOUR - 1)).isEqualTo(5L * MINUTE);
        assertThat(FloorMapHistogramBuckets.widthFor(DAY - 1)).isEqualTo(10L * MINUTE);
        assertThat(FloorMapHistogramBuckets.widthFor(3L * DAY - 1)).isEqualTo(HOUR);
        assertThat(FloorMapHistogramBuckets.widthFor(365L * DAY - 1)).isEqualTo(DAY);
    }

    @Test
    void degenerateRangeStillYieldsAUsableWidth() {
        assertThat(FloorMapHistogramBuckets.widthFor(0L)).isEqualTo(5L * MINUTE);
        assertThat(FloorMapHistogramBuckets.widthFor(-1L)).isEqualTo(5L * MINUTE);
    }

    @Test
    void durationsAreAcceptableToDurationParse() {
        // floorTime calls Duration.parse, which takes days and time units but has no month.
        assertThat(FloorMapHistogramBuckets.durationFor(2L * HOUR)).isEqualTo("PT5M");
        assertThat(FloorMapHistogramBuckets.durationFor(12L * HOUR)).isEqualTo("PT10M");
        assertThat(FloorMapHistogramBuckets.durationFor(DAY)).isEqualTo("PT1H");
        assertThat(FloorMapHistogramBuckets.durationFor(3L * DAY)).isEqualTo("P1D");
        assertThat(FloorMapHistogramBuckets.durationFor(400L * DAY)).isEqualTo("P30D");
    }

    @Test
    void everyDurationOnTheLadderActuallyParses() {
        // The whole point of PnD over PnM: Duration.parse rejects months.
        final long[] ranges = {0, HOUR, 3L * HOUR, DAY, 3L * DAY, 365L * DAY, 4000L * DAY};
        for (final long range : ranges) {
            final String iso = FloorMapHistogramBuckets.durationFor(range);
            assertThat(java.time.Duration.parse(iso).toMillis())
                    .as("%s should parse to the width for range %d", iso, range)
                    .isEqualTo(FloorMapHistogramBuckets.widthFor(range));
        }
    }

    @Test
    void bucketCountSpansTheRangeInclusively() {
        // Six hours at ten-minute buckets, both edges on a boundary.
        final long start = 10L * HOUR;
        assertThat(FloorMapHistogramBuckets.bucketCount(start, start + 6L * HOUR)).isEqualTo(37);
    }

    @Test
    void rangeEndingMidBucketStillGetsABarForIt() {
        final long start = 10L * HOUR;
        final long whole = FloorMapHistogramBuckets.bucketCount(start, start + 6L * HOUR);
        assertThat(FloorMapHistogramBuckets.bucketCount(start, start + 6L * HOUR + MINUTE))
                .isEqualTo(whole);
    }

    @Test
    void bucketCountIsNeverZero() {
        assertThat(FloorMapHistogramBuckets.bucketCount(0, 0)).isEqualTo(1);
        assertThat(FloorMapHistogramBuckets.bucketCount(100, 50)).isEqualTo(1);
    }

    @Test
    void indexCountsFromTheBucketContainingTheStart() {
        final long start = 10L * HOUR;
        final long end = start + 6L * HOUR;
        assertThat(FloorMapHistogramBuckets.bucketIndex(start, end, start)).isEqualTo(0);
        assertThat(FloorMapHistogramBuckets.bucketIndex(start, end, start + 10L * MINUTE)).isEqualTo(1);
        assertThat(FloorMapHistogramBuckets.bucketIndex(start, end, end)).isEqualTo(36);
    }

    @Test
    void everythingInsideOneBucketSharesAnIndex() {
        final long start = 10L * HOUR;
        final long end = start + 6L * HOUR;
        assertThat(FloorMapHistogramBuckets.bucketIndex(start, end, start + MINUTE)).isEqualTo(0);
        assertThat(FloorMapHistogramBuckets.bucketIndex(start, end, start + 9L * MINUTE)).isEqualTo(0);
    }

    @Test
    void outOfRangeIsRejectedRatherThanClamped() {
        // Clamping would pile out-of-range activity onto the first and last bars, which is what the
        // client-side version was careful not to do.
        final long start = 10L * HOUR;
        final long end = start + 6L * HOUR;
        assertThat(FloorMapHistogramBuckets.bucketIndex(start, end, start - 1)).isEqualTo(-1);
        assertThat(FloorMapHistogramBuckets.bucketIndex(start, end, end + 1)).isEqualTo(-1);
    }

    @Test
    void everyIndexInARangeIsWithinItsBucketCount() {
        final long start = 1_767_225_600_000L;   // 2026-01-01, not a round bucket boundary for all widths
        final long[] ranges = {2L * HOUR, 12L * HOUR, 2L * DAY, 40L * DAY, 500L * DAY};
        for (final long range : ranges) {
            final long end = start + range;
            final int count = FloorMapHistogramBuckets.bucketCount(start, end);
            assertThat(FloorMapHistogramBuckets.bucketIndex(start, end, start)).isZero();
            assertThat(FloorMapHistogramBuckets.bucketIndex(start, end, end))
                    .as("the last bucket of a %d ms range must be addressable", range)
                    .isBetween(0, count - 1);
        }
    }
}
