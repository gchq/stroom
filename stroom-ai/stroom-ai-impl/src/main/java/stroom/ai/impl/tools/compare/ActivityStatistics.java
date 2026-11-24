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

package stroom.ai.impl.tools.compare;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/// The statistics `compare_activity` scores with, each robust to the odd extreme day: medians and the median
/// absolute deviation (MAD) rather than means and standard deviations, a two-window median shift for a lasting change,
/// Theil-Sen slopes for a drift, and the total variation distance between two hour-of-week profiles. Pure functions of
/// their arguments, so each is checked by hand-computed cases.
final class ActivityStatistics {

    /// The fewest baseline values a score is given on: fewer, and no score is given.
    static final int MIN_BASELINE = 5;
    /// The factor that makes a MAD estimate a normal distribution's standard deviation.
    static final double MAD_SCALE = 1.4826;
    /// The factor that makes a mean absolute deviation estimate a normal distribution's standard deviation, used
    /// when the MAD is 0 (more than half the baseline the same value) but the values still vary.
    static final double MEAN_AD_SCALE = 1.2533;
    /// The z-score given to a value that differs from a baseline whose values are all the same: large, as any change
    /// from a constant is notable, but finite so it ranks.
    static final double CONSTANT_BASELINE_Z = 10.0;

    private ActivityStatistics() {
        // Static only.
    }

    /// @param values The values; not changed.
    /// @return Their median, or NaN if there are none.
    static double median(final double[] values) {
        if (values.length == 0) {
            return Double.NaN;
        }
        final double[] sorted = values.clone();
        Arrays.sort(sorted);
        final int middle = sorted.length / 2;
        return sorted.length % 2 == 1
                ? sorted[middle]
                : (sorted[middle - 1] + sorted[middle]) / 2.0;
    }

    /// @param values The values; not changed.
    /// @return Their median absolute deviation from their median (unscaled), or NaN if there are none.
    static double mad(final double[] values) {
        if (values.length == 0) {
            return Double.NaN;
        }
        final double median = median(values);
        final double[] deviations = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            deviations[i] = Math.abs(values[i] - median);
        }
        return median(deviations);
    }

    /// A robust z-score: how many (MAD-estimated) standard deviations a value is from a baseline's median. When the
    /// MAD is 0 the mean absolute deviation is used; when the baseline is constant, a value equal to it scores 0 and
    /// any other [#CONSTANT_BASELINE_Z] with its sign.
    ///
    /// @param value    The value scored.
    /// @param baseline The baseline's values.
    /// @return The score, or empty if the baseline has fewer than [#MIN_BASELINE] values.
    static Optional<Score> robustZ(final double value, final double[] baseline) {
        if (baseline.length < MIN_BASELINE) {
            return Optional.empty();
        }
        final double median = median(baseline);
        final double mad = mad(baseline);
        final double difference = value - median;
        if (mad > 0) {
            return Optional.of(new Score(difference / (MAD_SCALE * mad), median, mad, baseline.length, Basis.MAD));
        }
        final double meanAd = meanAd(baseline);
        if (meanAd > 0) {
            return Optional.of(new Score(difference / (MEAN_AD_SCALE * meanAd), median, 0, baseline.length,
                    Basis.MEAN_AD));
        }
        final double z = difference == 0
                ? 0
                : Math.copySign(CONSTANT_BASELINE_Z, difference);
        return Optional.of(new Score(z, median, 0, baseline.length, Basis.CONSTANT));
    }

    /// The Theil-Sen slope: the median of the slopes between every pair of points with different x.
    ///
    /// @param x The points' x values.
    /// @param y The points' y values, as many.
    /// @return The slope, or NaN with fewer than two distinct x values.
    static double theilSen(final double[] x, final double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("x and y differ in length");
        }
        final List<Double> slopes = new ArrayList<>();
        for (int i = 0; i < x.length; i++) {
            for (int j = i + 1; j < x.length; j++) {
                if (x[j] != x[i]) {
                    slopes.add((y[j] - y[i]) / (x[j] - x[i]));
                }
            }
        }
        return median(slopes.stream().mapToDouble(Double::doubleValue).toArray());
    }

    /// The most lasting shift in a series: the split with the largest difference between the medians before and
    /// after it, in units of the two sides' pooled (MAD-estimated) spread; each side at least `minSide` values long.
    /// A series whose two sides are each constant (pooled spread 0) but differ scores [#CONSTANT_BASELINE_Z].
    ///
    /// @param series  The values in time order.
    /// @param minSide The fewest values on each side of a split.
    /// @return The shift, or empty if the series is too short or nothing shifts.
    static Optional<Shift> changePoint(final double[] series, final int minSide) {
        if (minSide < 1 || series.length < 2 * minSide) {
            return Optional.empty();
        }
        Shift best = null;
        for (int split = minSide; split <= series.length - minSide; split++) {
            final double[] before = Arrays.copyOfRange(series, 0, split);
            final double[] after = Arrays.copyOfRange(series, split, series.length);
            final double medianBefore = median(before);
            final double medianAfter = median(after);
            final double difference = medianAfter - medianBefore;
            if (difference == 0) {
                continue;
            }
            final double score = shiftScore(before, after, Math.abs(difference));
            if (best == null || score > best.score()) {
                best = new Shift(split, medianBefore, medianAfter, score);
            }
        }
        return Optional.ofNullable(best);
    }

    /// The total variation distance between two profiles (e.g. events by hour of the week), each normalised to
    /// shares first: half the sum of the absolute differences of the shares, from 0 (the same shape) to 1 (no
    /// overlap).
    ///
    /// @param a One profile's counts.
    /// @param b The other's, as many.
    /// @return The distance, or NaN if either profile is all zero.
    static double profileDistance(final double[] a, final double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("The profiles differ in length");
        }
        final double totalA = Arrays.stream(a).sum();
        final double totalB = Arrays.stream(b).sum();
        if (totalA <= 0 || totalB <= 0) {
            return Double.NaN;
        }
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += Math.abs(a[i] / totalA - b[i] / totalB);
        }
        return sum / 2.0;
    }

    /// The runs of quiet (inactive) positions at least `minLength` long.
    ///
    /// @param active    Whether each position (e.g. a day) had activity.
    /// @param minLength The shortest run reported.
    /// @return Each run as `{start, length}`, in order.
    static List<int[]> quietRuns(final boolean[] active, final int minLength) {
        final List<int[]> runs = new ArrayList<>();
        int start = -1;
        for (int i = 0; i <= active.length; i++) {
            final boolean quiet = i < active.length && !active[i];
            if (quiet && start < 0) {
                start = i;
            } else if (!quiet && start >= 0) {
                if (i - start >= minLength) {
                    runs.add(new int[]{start, i - start});
                }
                start = -1;
            }
        }
        return runs;
    }

    // A shift's size in units of the two sides' pooled spread: their MADs, else their mean absolute deviations (so a
    // side with one odd value still has a spread), else none, the shift between two constant sides.
    private static double shiftScore(final double[] before, final double[] after, final double difference) {
        final int n = before.length + after.length;
        final double pooledMad = Math.sqrt((sq(mad(before)) * before.length + sq(mad(after)) * after.length) / n);
        if (pooledMad > 0) {
            return difference / (MAD_SCALE * pooledMad);
        }
        final double pooledMeanAd = Math.sqrt((sq(meanAd(before)) * before.length
                                               + sq(meanAd(after)) * after.length) / n);
        if (pooledMeanAd > 0) {
            return difference / (MEAN_AD_SCALE * pooledMeanAd);
        }
        return CONSTANT_BASELINE_Z;
    }

    // The mean absolute deviation from the median.
    private static double meanAd(final double[] values) {
        final double median = median(values);
        double sum = 0;
        for (final double value : values) {
            sum += Math.abs(value - median);
        }
        return sum / values.length;
    }

    private static double sq(final double value) {
        return value * value;
    }


    // --------------------------------------------------------------------------------


    /// How a score's spread was estimated.
    enum Basis {
        /// The median absolute deviation.
        MAD,
        /// The mean absolute deviation, as the MAD was 0.
        MEAN_AD,
        /// None: the baseline's values are all the same.
        CONSTANT
    }


    // --------------------------------------------------------------------------------


    /// A robust z-score.
    ///
    /// @param z      The score.
    /// @param median The baseline's median.
    /// @param mad    The baseline's MAD (unscaled).
    /// @param n      How many baseline values it was scored against.
    /// @param basis  How the spread was estimated.
    record Score(double z, double median, double mad, int n, Basis basis) {

    }


    // --------------------------------------------------------------------------------


    /// A lasting shift in a series.
    ///
    /// @param index  The first position after the shift.
    /// @param before The median before it.
    /// @param after  The median from it on.
    /// @param score  Its size in units of the pooled spread.
    record Shift(int index, double before, double after, double score) {

    }
}
