/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.floormap.client.playback;

/**
 * Chooses the bucket width for the timeline's density histogram.
 *
 * <p>The histogram used to be counted on the client from every event the store held, which is the
 * one read that grows without bound. Counting it server-side with a {@code group by} makes the
 * answer one row per bucket instead, so the width has to be decided before the query is built —
 * which is what this does.</p>
 *
 * <p><b>Widths are round numbers rather than a fixed bucket count.</b> A bar that spans ten minutes
 * is something a reader can reason about; one that spans eleven minutes and thirty-six seconds,
 * because the range happened to divide that way, is not. The cost is that bucket count varies with
 * the range — between roughly twelve and four hundred — which the timeline already copes with,
 * because {@code setHistogramData} takes the array's length as its bin count.</p>
 *
 * <p><b>Every width is a uniform duration</b>, including the largest. A calendar month would be the
 * natural top of the ladder, but {@code floorTime} takes an ISO-8601 {@code Duration}, which has no
 * month — and a variable-width bucket cannot be indexed by division, which is how counts are placed.
 * Thirty days reads as "about a month" at a range where the distinction does not signify.</p>
 *
 * <p>GWT-free and free of {@code java.time} so it is usable on the client and testable on the JVM.</p>
 */
public final class FloorMapHistogramBuckets {

    private static final long SECOND = 1_000L;
    private static final long MINUTE = 60L * SECOND;
    private static final long HOUR = 60L * MINUTE;
    private static final long DAY = 24L * HOUR;

    /** The ladder, coarsest first: a range at or above {@code fromRange} gets {@code width}. */
    private static final long[][] LADDER = {
            {365L * DAY, 30L * DAY},
            {3L * DAY, DAY},
            {DAY, HOUR},
            {3L * HOUR, 10L * MINUTE},
    };

    /** The width used below every rung of the ladder. */
    private static final long FINEST = 5L * MINUTE;

    private FloorMapHistogramBuckets() {
        // Utility class.
    }

    /**
     * The bucket width for a visible range of {@code rangeMs}.
     *
     * @param rangeMs the visible duration in milliseconds; zero or negative yields the finest width
     * @return the width in milliseconds, always positive
     */
    public static long widthFor(final long rangeMs) {
        for (final long[] rung : LADDER) {
            if (rangeMs >= rung[0]) {
                return rung[1];
            }
        }
        return FINEST;
    }

    /**
     * The same width as an ISO-8601 duration, for {@code floorTime}'s second argument.
     *
     * <p>Days are expressed as {@code PnD} and everything else as {@code PTnM}/{@code PTnH}, both of
     * which {@code Duration.parse} accepts. A width that is neither a whole number of days nor of
     * minutes cannot arise from {@link #widthFor}, so it falls back to minutes rather than failing.</p>
     */
    public static String durationFor(final long rangeMs) {
        final long width = widthFor(rangeMs);
        if (width % DAY == 0) {
            return "P" + (width / DAY) + "D";
        }
        if (width % HOUR == 0) {
            return "PT" + (width / HOUR) + "H";
        }
        return "PT" + (width / MINUTE) + "M";
    }

    /**
     * How many buckets span {@code [start, end]} at the width that range implies.
     *
     * <p>Inclusive of the bucket the end falls in, so a range that ends mid-bucket still has a bar
     * for it. Always at least one.</p>
     */
    public static int bucketCount(final long start, final long end) {
        final long range = end - start;
        if (range <= 0) {
            return 1;
        }
        final long width = widthFor(range);
        // Both edges are floored to their bucket, so the count is the number of bucket starts
        // between them, plus the one the start sits in.
        final long first = floorTo(start, width);
        final long last = floorTo(end, width);
        final long count = ((last - first) / width) + 1L;
        return count > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) count;
    }

    /**
     * The index of the bucket {@code time} falls in, counting from the bucket containing
     * {@code start}, or {@code -1} where it falls outside {@code [start, end]}.
     */
    public static int bucketIndex(final long start, final long end, final long time) {
        if (time < start || time > end) {
            return -1;
        }
        final long range = end - start;
        if (range <= 0) {
            return 0;
        }
        final long width = widthFor(range);
        return (int) ((floorTo(time, width) - floorTo(start, width)) / width);
    }

    /**
     * Floors {@code time} to a multiple of {@code width}, matching {@code floorTime}, which floors
     * against the epoch rather than against the range — so buckets land on round clock times.
     */
    private static long floorTo(final long time, final long width) {
        final long remainder = time % width;
        return remainder >= 0
                ? time - remainder
                : time - remainder - width;
    }
}
