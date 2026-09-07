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

package stroom.pathways.impl;

import stroom.pathways.shared.TraceHistogram;
import stroom.planb.shared.PlanBDocument;
import stroom.planb.shared.TraceSettings;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.TimeFilter;
import stroom.query.api.TimeRange;
import stroom.query.common.v2.DateExpressionParser;

import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * How wide a window a trace query may cover, and how that window is divided into histogram buckets.
 *
 * <p>Depends on nothing but the document's settings and the requested range, so the sizing rules can
 * be exercised without a store.
 */
final class TraceHistograms {

    /**
     * Widest window a single request may cover, and the window used when a request supplies no time
     * range at all. Bounded because every archive bucket the window overlaps is copied to local disk
     * to be read, so the cost of one request grows with the width of its window.
     */
    static final long DEFAULT_MAX_WINDOW_MS = 24L * 60 * 60 * 1000;

    // Bucket widths the histogram may use, narrowest first. The width is chosen from this ladder
    // rather than by dividing the window, because a relative range like day() to now() grows by a
    // millisecond every millisecond: a derived width would grow with it, every bucket edge would
    // creep, and traces near an edge would move between buckets on each request with no new data.
    // A ladder width only changes when the window crosses a rung, so the same data gives the same
    // histogram.
    private static final long[] BUCKET_WIDTHS_MS = {
            100L,
            500L,
            1_000L,
            5_000L,
            10_000L,
            30_000L,
            60_000L,
            5L * 60_000,
            10L * 60_000,
            15L * 60_000,
            30L * 60_000,
            60L * 60_000,
            3L * 60 * 60_000,
            6L * 60 * 60_000,
            12L * 60 * 60_000,
            24L * 60 * 60_000,
    };

    private TraceHistograms() {
    }

    // The doc's maxQueryTimeRange when one is set, else DEFAULT_MAX_WINDOW_MS.
    static long maxWindowMs(final PlanBDocument doc) {
        if (doc.getSettings() instanceof final TraceSettings ts && ts.getMaxQueryTimeRange() != null) {
            return ts.getMaxQueryTimeRange().getApproxMillis();
        }
        return DEFAULT_MAX_WINDOW_MS;
    }

    @Nullable
    static TimeFilter resolveTimeFilter(@Nullable final TimeRange timeRange) {
        if (timeRange == null) {
            return null;
        }
        return DateExpressionParser.getTimeFilter(timeRange, DateTimeSettings.builder().build());
    }

    // Resolves the histogram window + equal-bucket layout, or an unavailable spec when the range is
    // unbounded or wider than maxWindowMs (so a wide/all-time range never scans).
    static HistogramSpec histogramSpec(final TimeRange timeRange,
                                       final int bucketCount,
                                       final PlanBDocument doc) {
        final long maxWindowMs = maxWindowMs(doc);
        final TimeFilter timeFilter = resolveTimeFilter(timeRange);
        // Judged on the window as asked for, before it is rounded out to whole buckets, so rounding
        // can never carry a request over the limit.
        if (timeFilter == null || timeFilter.getTo() - timeFilter.getFrom() > maxWindowMs) {
            return new HistogramSpec(false, maxWindowMs, null, 0L, 0L, 0L, 0);
        }
        final int requestedBuckets = Math.max(1, bucketCount);
        final long fromMs = timeFilter.getFrom();
        final long span = Math.max(1L, timeFilter.getTo() - fromMs);
        final long bucketWidthMs = bucketWidth(span, requestedBuckets);
        // Enough whole buckets to reach the requested end, so the axis ends on or after it. Ending it
        // early would leave the newest traces out of the histogram while the list still listed them.
        final int nBuckets = (int) Math.max(1L, (span + bucketWidthMs - 1) / bucketWidthMs);
        final long toMs = fromMs + (long) nBuckets * bucketWidthMs;
        // Layout and scan range are not the same window. The layout (origin, width, bucket count) is
        // rounded out so the axis holds still between requests; the scan stays on the window as asked
        // for, which is the one the traces list uses. Handing the rounded end to TraceDb.histogram
        // instead would count traces past the requested end - its scan bound is inclusive and
        // addToBucket folds anything beyond the last edge into the final bucket - so on a range that
        // ends in the past the last bar would gain traces the list leaves out. fromMs is not rounded,
        // so this filter already carries the bucket origin the layout is built from.
        return new HistogramSpec(true, maxWindowMs, timeFilter,
                fromMs, toMs, bucketWidthMs, nBuckets);
    }

    // Narrowest ladder width that fits the span into requestedBuckets, else the widest on offer.
    private static long bucketWidth(final long span, final int requestedBuckets) {
        final long minWidth = (span + requestedBuckets - 1) / requestedBuckets;
        for (final long width : BUCKET_WIDTHS_MS) {
            if (width >= minWidth) {
                return width;
            }
        }
        return BUCKET_WIDTHS_MS[BUCKET_WIDTHS_MS.length - 1];
    }

    static TraceHistogram assembleHistogram(final HistogramSpec spec, final long[] totals) {
        final List<Long> counts = new ArrayList<>(spec.nBuckets());
        for (int b = 0; b < spec.nBuckets(); b++) {
            counts.add(totals[b]);
        }
        // Already on the narrowest rung, so narrowing the range to one bucket would only come back as
        // that same single bucket. The client decides nothing here; it is told.
        final boolean drillable = spec.bucketWidthMs() > BUCKET_WIDTHS_MS[0];
        return new TraceHistogram(
                true, spec.fromMs(), spec.toMs(), spec.bucketWidthMs(), spec.maxWindowMs(),
                counts, drillable);
    }

    record HistogramSpec(boolean available,
                         long maxWindowMs,
                         TimeFilter timeFilter,
                         long fromMs,
                         long toMs,
                         long bucketWidthMs,
                         int nBuckets) {

    }
}
