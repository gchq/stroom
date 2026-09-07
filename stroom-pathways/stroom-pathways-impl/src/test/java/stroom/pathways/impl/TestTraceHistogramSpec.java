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

import stroom.pathways.impl.TraceHistograms.HistogramSpec;
import stroom.pathways.shared.TraceHistogram;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TraceSettings;
import stroom.query.api.TimeRange;
import stroom.util.io.ByteSize;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The traces screen asks for a window that ends at "now", so consecutive requests describe windows
 * whose ends differ by however long apart they arrive. The bucket layout must not follow that: were
 * the width derived from the window, it would grow with it, every bucket edge would creep, and traces
 * near an edge would move between buckets with no new data — the histogram would change on a page
 * turn purely because time had passed.
 */
class TestTraceHistogramSpec {

    private static final int BUCKETS = 80;
    private static final Instant DAY_START = Instant.parse("2026-01-15T00:00:00.000Z");

    @Test
    void windowEndMovingWithinABucketChangesNothing() {
        // Both ends fall inside the bucket starting at 06:00, which is the common case: the whole
        // layout is identical, so the same data renders to the same histogram.
        final Instant now = DAY_START.plus(Duration.ofHours(6)).plusSeconds(30);
        final HistogramSpec first = spec(DAY_START, now);
        final HistogramSpec second = spec(DAY_START, now.plusSeconds(1));

        assertThat(second.available()).isTrue();
        assertThat(second.bucketWidthMs()).isEqualTo(first.bucketWidthMs());
        assertThat(second.fromMs()).isEqualTo(first.fromMs());
        assertThat(second.toMs()).isEqualTo(first.toMs());
        assertThat(second.nBuckets()).isEqualTo(first.nBuckets());
    }

    @Test
    void crossingABucketEdgeAddsABucketAndMovesNoneOfTheRest() {
        // Once the window passes an edge the histogram gains a bucket, at most once per bucket width.
        // What must not happen is the existing edges moving, which is what would shuffle traces
        // between buckets: the origin and the width both hold, so every earlier bucket covers exactly
        // the span it did before.
        final Instant edge = DAY_START.plus(Duration.ofHours(6));
        final HistogramSpec before = spec(DAY_START, edge);
        final HistogramSpec after = spec(DAY_START, edge.plusSeconds(1));

        assertThat(after.bucketWidthMs()).isEqualTo(before.bucketWidthMs());
        assertThat(after.fromMs()).isEqualTo(before.fromMs());
        assertThat(after.nBuckets()).isEqualTo(before.nBuckets() + 1);
        assertThat(after.toMs()).isEqualTo(before.toMs() + before.bucketWidthMs());
    }

    @Test
    void widthComesFromTheLadder() {
        // Six hours over 80 buckets needs at least 4.5 minutes, so the 5 minute rung is taken.
        final HistogramSpec spec = spec(DAY_START, DAY_START.plus(Duration.ofHours(6)));
        assertThat(spec.bucketWidthMs()).isEqualTo(Duration.ofMinutes(5).toMillis());
        assertThat(spec.nBuckets()).isEqualTo(72);
    }

    @Test
    void theWindowNeverEndsBeforeTheRangeAsked() {
        // An end part way through a bucket rounds up, so nothing newly arrived is left uncounted.
        final Instant now = DAY_START.plus(Duration.ofHours(6)).plusSeconds(30);
        final HistogramSpec spec = spec(DAY_START, now);

        assertThat(spec.toMs()).isGreaterThanOrEqualTo(now.toEpochMilli());
        assertThat(spec.toMs() - spec.fromMs()).isEqualTo(spec.nBuckets() * spec.bucketWidthMs());
        // The scan shares the layout's origin, so the bars sit under the right labels...
        assertThat(spec.timeFilter().getFrom()).isEqualTo(spec.fromMs());
        // ...but it stops at the window as asked for, not the rounded-out end. Scanning to the rounded
        // end would give the last bar traces that fall outside the window the traces list queries.
        assertThat(spec.timeFilter().getTo()).isEqualTo(now.toEpochMilli());
        assertThat(spec.timeFilter().getTo()).isLessThan(spec.toMs());
    }

    @Test
    void widerWindowStepsUpARung() {
        // Stability holds within a rung, not across one: this is the width changing as it should.
        final long sixHours = spec(DAY_START, DAY_START.plus(Duration.ofHours(6))).bucketWidthMs();
        final long twelveHours = spec(DAY_START, DAY_START.plus(Duration.ofHours(12))).bucketWidthMs();
        assertThat(twelveHours).isGreaterThan(sixHours);
    }

    @Test
    void windowWiderThanTheStoreAllowsIsNotCounted() {
        // Too wide to count, so nothing is scanned - but the traces list is unaffected: the page is
        // still fetched and returned, and the histogram comes back marked unavailable. Nothing may
        // read the layout in that state, and the widget names the limit in its place of the bars, so
        // the window has to be absent and the limit present.
        final HistogramSpec spec = spec(DAY_START, DAY_START.plus(Duration.ofHours(25)));
        assertThat(spec.available()).isFalse();
        assertThat(spec.timeFilter()).isNull();
        assertThat(spec.nBuckets()).isZero();
        assertThat(spec.maxWindowMs()).isEqualTo(Duration.ofHours(24).toMillis());
    }

    @Test
    void anUnboundedWindowIsNotCounted() {
        assertThat(TraceHistograms.histogramSpec(null, BUCKETS, doc()).available()).isFalse();
    }

    @Test
    void theNarrowestBucketCannotBeDrilledInto() {
        // A window of a single millisecond always lands on the narrowest width available, whatever
        // widths are on offer. Narrowing the range to one of its buckets would return that same
        // single bucket, so the click must be refused.
        final HistogramSpec narrowest = spec(DAY_START, DAY_START.plusMillis(1));
        assertThat(narrowest.nBuckets()).isEqualTo(1);
        assertThat(TraceHistograms.assembleHistogram(narrowest, new long[narrowest.nBuckets()])
                .isDrillable()).isFalse();

        // Anything wider has room to narrow into.
        final HistogramSpec wider = spec(DAY_START, DAY_START.plus(Duration.ofHours(6)));
        assertThat(TraceHistograms.assembleHistogram(wider, new long[wider.nBuckets()])
                .isDrillable()).isTrue();
    }

    @Test
    void anUncountableWindowCannotBeDrilledInto() {
        assertThat(TraceHistogram.unavailable(Duration.ofHours(24).toMillis()).isDrillable()).isFalse();
    }

    private static HistogramSpec spec(final Instant from, final Instant to) {
        return TraceHistograms.histogramSpec(
                new TimeRange("test", from.toString(), to.toString()), BUCKETS, doc());
    }

    private static PlanBDoc doc() {
        return PlanBDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("test-doc")
                .stateType(StateType.TRACE)
                .settings(new TraceSettings.Builder()
                        .maxStoreSize(ByteSize.ofGibibytes(1).getBytes())
                        .maxQueryTimeRange(SimpleDuration.builder().time(24).timeUnit(TimeUnit.HOURS).build())
                        .build())
                .build();
    }
}
