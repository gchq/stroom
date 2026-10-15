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

/// Where each histogram bar sits across the widget, as fractions of the visible range.
///
/// **Bars are not an even division of the range.** Bucketed counts start at the bucket boundary at
/// or before the range start and run to the end of the bucket containing the range end, so the
/// first and last bars hang over the edges. Spreading them evenly across the widget instead shifts
/// every bar away from its own time: with day buckets over a month, a day's bar was drawn most of a
/// day early or late, so the bars showed nothing at the times entities were moving.
///
/// Fractions are of the visible range, `0` at its start and `1` at its end. A bar's
/// fractions may fall outside that interval; drawing clips them.
///
/// GWT-free so the arithmetic can be tested on the JVM.
public final class HistogramLayout {

    /// Where bar `0` starts, at or before `0`.
    private final double firstBarStart;
    /// How much of the visible range one bar spans.
    private final double barFraction;
    /// The time one bar spans, or `0` when the bars are not tied to a bucket width.
    private final long bucketWidthMs;

    private HistogramLayout(final double firstBarStart,
                            final double barFraction,
                            final long bucketWidthMs) {
        this.firstBarStart = firstBarStart;
        this.barFraction = barFraction;
        this.bucketWidthMs = bucketWidthMs;
    }

    /// Bars dividing the range evenly, for counts that were binned over exactly the visible range.
    ///
    /// @param binCount the number of bars; zero or negative is treated as one
    /// @return a layout with bar `0` at the start and the last bar ending at the end
    public static HistogramLayout even(final int binCount) {
        return new HistogramLayout(0, 1.0 / Math.max(1, binCount), 0);
    }

    /// Bars for counts grouped into buckets of `bucketWidthMs`, floored to the epoch as
    /// `floorTime` and [HistogramDataModel#floorTo] floor them.
    ///
    /// A range or width that is zero or negative has no meaningful placement, so the result is an
    /// [#even] layout of one bar rather than a division by zero.
    ///
    /// @param rangeStart    the visible range start, in epoch milliseconds
    /// @param rangeEnd      the visible range end, in epoch milliseconds
    /// @param bucketWidthMs the width each bar covers, in milliseconds
    /// @return a layout placing each bar over its own bucket
    public static HistogramLayout forBuckets(final long rangeStart,
                                             final long rangeEnd,
                                             final long bucketWidthMs) {
        final long range = rangeEnd - rangeStart;
        if (range <= 0 || bucketWidthMs <= 0) {
            return even(1);
        }
        final long firstBucket = HistogramDataModel.floorTo(rangeStart, bucketWidthMs);
        return new HistogramLayout(
                (firstBucket - rangeStart) / (double) range,
                bucketWidthMs / (double) range,
                bucketWidthMs);
    }

    /// Where bar `index` starts.
    ///
    /// @param index the bar index
    /// @return the fraction of the visible range, possibly below `0` for the first bar
    public double barStart(final int index) {
        return firstBarStart + (index * barFraction);
    }

    /// Where bar `index` ends.
    ///
    /// @param index the bar index
    /// @return the fraction of the visible range, possibly above `1` for the last bar
    public double barEnd(final int index) {
        return barStart(index + 1);
    }

    /// The bar under a position across the widget.
    ///
    /// @param fraction the position, as a fraction of the visible range
    /// @param binCount the number of bars
    /// @return the bar index, clamped to `0` and `binCount - 1`
    public int barAt(final double fraction, final int binCount) {
        final double index = Math.floor((fraction - firstBarStart) / barFraction);
        return (int) Math.max(0, Math.min(binCount - 1, index));
    }

    /// The time one bar spans.
    ///
    /// @return the bucket width in milliseconds, or `0` for an [#even] layout
    public long getBucketWidthMs() {
        return bucketWidthMs;
    }

    @Override
    public String toString() {
        return "HistogramLayout{"
               + "firstBarStart=" + firstBarStart
               + ", barFraction=" + barFraction
               + ", bucketWidthMs=" + bucketWidthMs
               + '}';
    }
}
