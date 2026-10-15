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

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bucket arithmetic behind the timeline's density bars.
 *
 * <p>Only the arithmetic: the surrounding methods parse timestamps through {@code UTCDate}, a native
 * browser object that cannot run outside a browser. That is why these parts are separated — the
 * arithmetic is where the edge cases are, and leaving it unreachable would mean testing none of it.
 * </p>
 */
class TestHistogramDataModel {

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;

    private static long at(final String iso) {
        return Instant.parse(iso).toEpochMilli();
    }

    // ------------------------------------------------------------------
    // floorTo - shared with the server's floorTime, which floors to the epoch.
    // ------------------------------------------------------------------

    @Test
    void floorsToAMultipleOfTheWidth() {
        assertThat(HistogramDataModel.floorTo(at("2026-01-01T10:44:30.000Z"), 10 * MINUTE))
                .isEqualTo(at("2026-01-01T10:40:00.000Z"));
    }

    @Test
    void valueAlreadyOnABoundaryIsUnchanged() {
        assertThat(HistogramDataModel.floorTo(at("2026-01-01T10:40:00.000Z"), 10 * MINUTE))
                .isEqualTo(at("2026-01-01T10:40:00.000Z"));
    }

    /**
     * Pre-epoch times floor downwards, not towards zero.
     *
     * <p>Java's {@code %} yields a negative remainder for a negative operand, so the naive
     * subtraction would round a pre-1970 timestamp <em>up</em> — putting an event in a bucket that
     * starts after it. Unlikely on a floor map, but the bars are a generic widget.</p>
     */
    @Test
    void preEpochTimesFloorDownwards() {
        assertThat(HistogramDataModel.floorTo(at("1969-12-31T23:55:00.000Z"), 10 * MINUTE))
                .isEqualTo(at("1969-12-31T23:50:00.000Z"));
        assertThat(HistogramDataModel.floorTo(-1L, 10 * MINUTE)).isEqualTo(-10 * MINUTE);
    }

    // ------------------------------------------------------------------
    // How many bars a range needs.
    // ------------------------------------------------------------------

    @Test
    void rangeGetsOneBarPerBucketItSpans() {
        // 10:00 to 11:00 at ten minutes: 10:00, 10:10 ... 11:00 - seven boundaries.
        assertThat(HistogramDataModel.binCountFor(
                at("2026-01-01T10:00:00.000Z"), at("2026-01-01T11:00:00.000Z"), 10 * MINUTE))
                .isEqualTo(7);
    }

    @Test
    void rangeInsideOneBucketStillGetsABar() {
        assertThat(HistogramDataModel.binCountFor(
                at("2026-01-01T10:01:00.000Z"), at("2026-01-01T10:02:00.000Z"), HOUR))
                .isEqualTo(1);
    }

    @Test
    void zeroWidthRangeStillGetsABar() {
        final long instant = at("2026-01-01T10:00:00.000Z");
        assertThat(HistogramDataModel.binCountFor(instant, instant, HOUR)).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Where a bucket's count lands.
    // ------------------------------------------------------------------

    @Test
    void bucketLandsInTheBarItStarts() {
        final long firstBucket = at("2026-01-01T10:00:00.000Z");
        final long rangeEnd = at("2026-01-01T11:00:00.000Z");

        assertThat(HistogramDataModel.binIndexFor(firstBucket, firstBucket, rangeEnd, 10 * MINUTE, 7))
                .isZero();
        assertThat(HistogramDataModel.binIndexFor(
                at("2026-01-01T10:30:00.000Z"), firstBucket, rangeEnd, 10 * MINUTE, 7))
                .isEqualTo(3);
    }

    /**
     * Out-of-range buckets are dropped, not clamped.
     *
     * <p>Clamping would pile everything before the range onto the first bar and everything after it
     * onto the last, which reads as a spike at each end that is not in the data.</p>
     */
    @Test
    void bucketOutsideTheRangeIsDroppedRatherThanClamped() {
        final long firstBucket = at("2026-01-01T10:00:00.000Z");
        final long rangeEnd = at("2026-01-01T11:00:00.000Z");

        assertThat(HistogramDataModel.binIndexFor(
                at("2026-01-01T09:00:00.000Z"), firstBucket, rangeEnd, 10 * MINUTE, 7))
                .as("before the range")
                .isEqualTo(-1);
        assertThat(HistogramDataModel.binIndexFor(
                at("2026-01-01T12:00:00.000Z"), firstBucket, rangeEnd, 10 * MINUTE, 7))
                .as("after the range")
                .isEqualTo(-1);
    }

    // ------------------------------------------------------------------
    // Which two columns hold the extent.
    // ------------------------------------------------------------------

    /**
     * The pair is taken from the end of the row, not the start.
     *
     * <p>The extent query has to group by a constant - aggregates with no {@code group by} do not
     * collapse in StroomQL, and the grouped column must be selected for grouping to happen at all -
     * so the row is {@code group, min, max}. Reading the first two took the group key as the
     * minimum and the minimum as the maximum, which passed every null and ordering check and left
     * the timeline showing "No events in this time range".</p>
     */
    @Test
    void extentPairIsTheLastTwoColumns() {
        assertThat(HistogramDataModel.extentPair(List.of("1", "2026-01-01T10:00:00.000Z", "2026-01-02T10:00:00.000Z")))
                .containsExactly("2026-01-01T10:00:00.000Z", "2026-01-02T10:00:00.000Z");
    }

    @Test
    void extentPairStillWorksWithoutALeadingColumn() {
        assertThat(HistogramDataModel.extentPair(List.of("2026-01-01T10:00:00.000Z", "2026-01-02T10:00:00.000Z")))
                .containsExactly("2026-01-01T10:00:00.000Z", "2026-01-02T10:00:00.000Z");
    }

    @Test
    void extentPairIsNullWhenTheRowCannotHoldOne() {
        assertThat(HistogramDataModel.extentPair(null)).isNull();
        assertThat(HistogramDataModel.extentPair(List.of())).isNull();
        assertThat(HistogramDataModel.extentPair(List.of("2026-01-01T10:00:00.000Z"))).isNull();
    }

    @Test
    void bucketBeyondTheBarArrayIsDropped() {
        // The store can return a bucket the array was not sized for; it must not write past the end.
        final long firstBucket = at("2026-01-01T10:00:00.000Z");
        assertThat(HistogramDataModel.binIndexFor(
                at("2026-01-01T10:50:00.000Z"),
                firstBucket,
                at("2026-01-01T23:00:00.000Z"),
                10 * MINUTE,
                3))
                .isEqualTo(-1);
    }
}
