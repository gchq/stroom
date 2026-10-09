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

package stroom.widget.histogram.client;

import stroom.query.api.Row;
import stroom.query.api.TableResult;
import stroom.widget.datepicker.client.UTCDate;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Buckets {@link TableResult} timestamps into a fixed number of histogram bins.
 * <p>
 *     The model is agnostic of how the query is run. It just takes result data
 *     and produces {@code int[]} bin counts that can be fed to a histogram widget.
 * </p>
 */
public class HistogramDataModel {

    private final int binCount;
    private long rangeStart;
    private long rangeEnd;

    /** Called when bin data is ready, with where each bin belongs across the visible range. */
    private BiConsumer<int[], HistogramLayout> dataHandler;

    /**
     * Creates a new histogram data model with the given number of bins.
     *
     * @param binCount the number of histogram bins
     */
    public HistogramDataModel(final int binCount) {
        this.binCount = binCount;
    }

    /**
     * Sets the visible time range for bucketing.
     *
     * @param start range start (epoch millis, inclusive)
     * @param end   range end (epoch millis, inclusive)
     */
    public void setRange(final long start, final long end) {
        this.rangeStart = start;
        this.rangeEnd = end;
    }

    /// Sets the handler called with each set of bin counts.
    ///
    /// The layout travels with the counts because the bins are bucket-aligned rather than an even
    /// division of the visible range, and only this model knows the range the counts were taken over.
    ///
    /// @param handler receives the counts and their layout, or `null` to remove it
    public void setDataHandler(final BiConsumer<int[], HistogramLayout> handler) {
        this.dataHandler = handler;
    }

    /**
     * Places counts that the server has already bucketed.
     *
     * <p>Where the query groups by a time bucket, each row is one bucket and a count, so the number
     * of rows is bounded by the range rather than by how many events the store holds — which is the
     * whole reason for grouping server-side. This replaced a path that returned every event and
     * bucketed them here; that path is gone, along with the timestamp-column sniffing it needed.</p>
     *
     * <p><b>Columns are taken by position, not by name.</b> The caller generated the query, so it
     * knows the first column is the bucket and the second is its count; matching on a name would
     * couple this to the exact text of a query it does not own, and an aggregate's default column
     * name is not something to depend on.</p>
     *
     * <p><b>It reports no data extent.</b> The bars are bounded below at the visible range, so the
     * buckets returned can never start earlier than what is already shown — an extent taken from
     * them could only ever grow forwards, which is not what "Show All" means. The extent comes from
     * its own unbounded query instead.</p>
     *
     * <p>Bins are sized to the given width, so one bin is one bucket and no
     * redistribution is needed. They start at the bucket boundary at or before the range start, not at
     * the range start itself, so they are handed on with a {@link HistogramLayout} saying where each
     * one belongs; drawing them as an even division of the range shifts every bar off its own time.
     * A bucket outside the visible range is skipped rather than clamped to an edge bin: clamping
     * would pile activity from outside the range onto the first and last bars.</p>
     *
     * @param tableResult  the grouped result; a null or empty one yields empty bins
     * @param bucketWidthMs the width each row covers, which must match the width the query grouped
     *                      by, or counts land in the wrong bars
     * @return the per-bin counts, also passed to the data handler
     */
    public int[] processBuckets(final TableResult tableResult, final long bucketWidthMs) {
        final long range = rangeEnd - rangeStart;
        if (bucketWidthMs <= 0 || range <= 0) {
            final int[] empty = new int[binCount];
            notifyDataHandler(empty, HistogramLayout.even(empty.length));
            return empty;
        }

        final long firstBucket = floorTo(rangeStart, bucketWidthMs);
        final int[] counts = new int[binCountFor(rangeStart, rangeEnd, bucketWidthMs)];
        final HistogramLayout layout = HistogramLayout.forBuckets(rangeStart, rangeEnd, bucketWidthMs);

        if (tableResult == null || tableResult.getRows() == null) {
            notifyDataHandler(counts, layout);
            return counts;
        }

        for (final Row row : tableResult.getRows()) {
            final List<String> values = row.getValues();
            if (values == null || values.size() < 2) {
                continue;
            }
            final Long bucketStart = parseTime(values.get(0));
            if (bucketStart == null) {
                continue;
            }

            final int index = binIndexFor(bucketStart, firstBucket, rangeEnd, bucketWidthMs, counts.length);
            if (index >= 0) {
                counts[index] += parseCount(values.get(1));
            }
        }

        notifyDataHandler(counts, layout);
        return counts;
    }

    /**
     * How many bars a range needs at a given bucket width.
     *
     * <p>Package-private, and separated from {@link #processBuckets} for one reason: the timestamp
     * parsing there goes through {@code UTCDate}, which is a native browser object and cannot run
     * outside a browser. The arithmetic is where the edge cases live, so it is kept where a test can
     * reach it.</p>
     */
    static int binCountFor(final long rangeStart, final long rangeEnd, final long bucketWidthMs) {
        final long firstBucket = floorTo(rangeStart, bucketWidthMs);
        final long lastBucket = floorTo(rangeEnd, bucketWidthMs);
        return Math.max(1, (int) (((lastBucket - firstBucket) / bucketWidthMs) + 1L));
    }

    /**
     * The bar a bucket belongs in, or {@code -1} where it belongs in none.
     *
     * <p>A bucket outside the visible range is skipped rather than clamped to an edge bar: clamping
     * would pile activity from outside the range onto the first and last bars, which reads as a spike
     * that is not there.</p>
     */
    static int binIndexFor(final long bucketStart,
                           final long firstBucket,
                           final long rangeEnd,
                           final long bucketWidthMs,
                           final int binCount) {
        if (bucketStart < firstBucket || bucketStart > rangeEnd) {
            return -1;
        }
        final int index = (int) ((bucketStart - firstBucket) / bucketWidthMs);
        return index >= 0 && index < binCount
                ? index
                : -1;
    }

    /**
     * The data's time extent, from a result of one row holding a minimum and a maximum.
     *
     * <p>Separate from {@link #processBuckets} because the two answer different queries. The bars are
     * bounded below at the visible range and so can never see data earlier than what is already
     * shown; the extent has to come from an unbounded read, which is the whole point of "Show
     * All".</p>
     *
     * <p>Columns are taken by position, as the <em>last two</em> of the row rather than the first
     * two. The query has to group by a constant to make the whole store one group, and the grouped
     * column must be selected for that to happen at all, so a leading column is always present and
     * would otherwise shift the pair. Reading from the end also survives a user adding columns
     * ahead of the aggregates. An aggregate's default column name is not something to depend on.</p>
     *
     * <p>An earlier version of this took the first and last of a set of day buckets, which could only
     * place "Show All" on the right day and cost a row per day the store spanned. Two aggregates give
     * the exact instants in one row.</p>
     *
     * @return {@code {min, max}}, or {@code null} if the result holds no parseable pair — an empty
     *         store, or a failed read
     */
    public static long[] extentOf(final TableResult tableResult) {
        if (tableResult == null || tableResult.getRows() == null) {
            return null;
        }

        for (final Row row : tableResult.getRows()) {
            final List<String> pair = extentPair(row.getValues());
            if (pair == null) {
                continue;
            }
            final Long min = parseTime(pair.get(0));
            final Long max = parseTime(pair.get(1));
            if (min != null && max != null && min <= max) {
                return new long[]{min, max};
            }
        }

        return null;
    }

    /**
     * The two values holding the minimum and maximum - the last two of the row.
     *
     * <p>Split out because it is the part that can be tested: {@link #extentOf} parses times through
     * {@code UTCDate}, which cannot run outside a browser, and picking the wrong two columns is what
     * actually broke - the extent query grew a leading group column and the pair silently became
     * the group key and the minimum.</p>
     *
     * @param values a result row's values; may be {@code null}
     * @return the final two values, or {@code null} where there are fewer than two
     */
    static List<String> extentPair(final List<String> values) {
        return values == null || values.size() < 2
                ? null
                : values.subList(values.size() - 2, values.size());
    }

    /** Floors to a multiple of {@code width}, matching {@code floorTime}, which floors to the epoch. */
    static long floorTo(final long time, final long width) {
        final long remainder = time % width;
        return remainder >= 0
                ? time - remainder
                : time - remainder - width;
    }

    /** An ISO-8601 instant as epoch millis, or null where it will not parse. */
    private static Long parseTime(final String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            final UTCDate date = UTCDate.create(value);
            return date == null
                    ? null
                    : (long) date.getTime();
        } catch (final Exception e) {
            return null;
        }
    }

    /** A count column as an int; anything unreadable counts as zero rather than failing the bar. */
    private static int parseCount(final String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        try {
            return (int) Double.parseDouble(value.trim());
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    private void notifyDataHandler(final int[] bins, final HistogramLayout layout) {
        if (dataHandler != null) {
            dataHandler.accept(bins, layout);
        }
    }
}
