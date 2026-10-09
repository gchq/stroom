/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.widget.histogram.client;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests for [HistogramLayout], which places bucketed bars over their own times.
class TestHistogramLayout {

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;
    private static final Offset<Double> TOLERANCE = Offset.offset(1e-9);

    private static long at(final String iso) {
        return Instant.parse(iso).toEpochMilli();
    }

    /// The fraction of the range from `start` to `end` that `time` sits at.
    private static double fractionOf(final long time, final long start, final long end) {
        return (time - start) / (double) (end - start);
    }

    // ------------------------------------------------------------------
    // Bucketed bars.
    // ------------------------------------------------------------------

    /// Regression: the bars were drawn as an even division of the range. With Show All over a
    /// month of data, the busiest day's bar was drawn over the day before it, so the histogram
    /// showed nothing at the times entities were moving.
    @Test
    void bucketBarStartsAtItsOwnTime() {
        final long start = at("2026-09-07T18:18:00.000Z");
        final long end = at("2026-10-08T00:03:00.000Z");
        final int binCount = HistogramDataModel.binCountFor(start, end, DAY);
        final HistogramLayout layout = HistogramLayout.forBuckets(start, end, DAY);

        // 2026-09-07 is bucket 0, so 2026-10-06 is bucket 29.
        final long bucket = at("2026-10-06T00:00:00.000Z");
        final int index = 29;
        assertThat(layout.barStart(index)).isCloseTo(fractionOf(bucket, start, end), TOLERANCE);
        assertThat(layout.barEnd(index)).isCloseTo(fractionOf(bucket + DAY, start, end), TOLERANCE);

        // The even division this replaced put the bar most of a day early.
        final double evenStart = HistogramLayout.even(binCount).barStart(index);
        assertThat(fractionOf(bucket, start, end) - evenStart)
                .as("the old placement was this far off, as a fraction of the range")
                .isGreaterThan(0.02);
    }

    /// The hover reads the bar under the pointer from the same placement as drawing.
    @Test
    void barAtFindsTheBucketContainingATime() {
        final long start = at("2026-09-07T18:18:00.000Z");
        final long end = at("2026-10-08T00:03:00.000Z");
        final int binCount = HistogramDataModel.binCountFor(start, end, DAY);
        final HistogramLayout layout = HistogramLayout.forBuckets(start, end, DAY);

        final double busyMorning = fractionOf(at("2026-10-06T09:00:00.000Z"), start, end);
        assertThat(layout.barAt(busyMorning, binCount)).isEqualTo(29);
    }

    /// The first bar starts at the bucket boundary before the range, so it hangs over the left
    /// edge; drawing clips it.
    @Test
    void firstBarHangsOverTheStart() {
        final long start = at("2026-01-01T10:30:00.000Z");
        final long end = at("2026-01-01T12:30:00.000Z");
        final HistogramLayout layout = HistogramLayout.forBuckets(start, end, HOUR);

        assertThat(layout.barStart(0)).isCloseTo(-0.25, TOLERANCE);
        assertThat(layout.barEnd(0)).isCloseTo(0.25, TOLERANCE);
        // Bar 2 is 12:00-13:00, past the end of the range.
        assertThat(layout.barEnd(2)).isCloseTo(1.25, TOLERANCE);
    }

    /// A range already on bucket boundaries has its first bar flush with the start.
    @Test
    void rangeOnBoundariesStartsFlush() {
        final long start = at("2026-01-01T10:00:00.000Z");
        final long end = at("2026-01-01T11:00:00.000Z");
        final HistogramLayout layout = HistogramLayout.forBuckets(start, end, 10 * MINUTE);

        assertThat(layout.barStart(0)).isCloseTo(0.0, TOLERANCE);
        assertThat(layout.barStart(6)).isCloseTo(1.0, TOLERANCE);
    }

    @Test
    void bucketLayoutCarriesItsWidth() {
        assertThat(HistogramLayout.forBuckets(0, DAY, HOUR).getBucketWidthMs()).isEqualTo(HOUR);
    }

    /// Positions outside every bar, including either side of the widget, pick the nearest bar
    /// rather than an index that is out of bounds.
    @Test
    void barAtClampsToTheBars() {
        final HistogramLayout layout = HistogramLayout.forBuckets(
                at("2026-01-01T10:30:00.000Z"), at("2026-01-01T12:30:00.000Z"), HOUR);

        assertThat(layout.barAt(-0.5, 3)).isZero();
        assertThat(layout.barAt(0.0, 3)).isZero();
        assertThat(layout.barAt(1.0, 3)).isEqualTo(2);
        assertThat(layout.barAt(5.0, 3)).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // Invalid input.
    // ------------------------------------------------------------------

    /// A zero-length or inverted range has nothing to place bars across, so it gets one even bar
    /// rather than a division by zero.
    @Test
    void emptyOrInvertedRangeFallsBackToOneEvenBar() {
        final long instant = at("2026-01-01T10:00:00.000Z");
        for (final HistogramLayout layout : new HistogramLayout[]{
                HistogramLayout.forBuckets(instant, instant, HOUR),
                HistogramLayout.forBuckets(instant + HOUR, instant, HOUR)}) {
            assertThat(layout.barStart(0)).isCloseTo(0.0, TOLERANCE);
            assertThat(layout.barEnd(0)).isCloseTo(1.0, TOLERANCE);
            assertThat(layout.getBucketWidthMs()).isZero();
        }
    }

    @Test
    void nonPositiveWidthFallsBackToOneEvenBar() {
        final HistogramLayout layout = HistogramLayout.forBuckets(0, DAY, 0);

        assertThat(layout.barEnd(0)).isCloseTo(1.0, TOLERANCE);
        assertThat(layout.getBucketWidthMs()).isZero();
    }

    // ------------------------------------------------------------------
    // Even bars - counts binned over exactly the visible range.
    // ------------------------------------------------------------------

    @Test
    void evenBarsDivideTheRange() {
        final HistogramLayout layout = HistogramLayout.even(4);

        assertThat(layout.barStart(0)).isCloseTo(0.0, TOLERANCE);
        assertThat(layout.barStart(1)).isCloseTo(0.25, TOLERANCE);
        assertThat(layout.barEnd(3)).isCloseTo(1.0, TOLERANCE);
        assertThat(layout.barAt(0.6, 4)).isEqualTo(2);
        assertThat(layout.getBucketWidthMs()).isZero();
    }

    @Test
    void evenWithNoBinsIsOneBar() {
        assertThat(HistogramLayout.even(0).barEnd(0)).isCloseTo(1.0, TOLERANCE);
        assertThat(HistogramLayout.even(-1).barEnd(0)).isCloseTo(1.0, TOLERANCE);
    }
}
