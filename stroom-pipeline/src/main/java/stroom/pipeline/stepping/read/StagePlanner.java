/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pipeline.stepping.read;

import stroom.pipeline.stepping.fingerprint.ElementFingerprints;
import stroom.pipeline.stepping.store.Coverage;
import stroom.pipeline.stepping.store.StepDataStore;
import stroom.util.shared.ElementId;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides, for a stepping request, which elements' captured IO can be reused from the store and which must
 * be (re)processed.
 * <p>
 * Because the store is content-addressed by {@code cumulativeFingerprint} (which changes for an element
 * iff it or anything upstream changed), an element is reusable exactly when the store already holds a
 * chunk for its current cumulative fingerprint. So the plan is simply: reuse where the fingerprinted
 * chunk exists, reprocess where it does not - and reverting a pipeline edit naturally reuses the still
 * present prior-fingerprint chunks.
 * <p>
 * One exception: a change at or above the record-boundary (Source / readers / parser / split) redefines
 * what a "record" is, so record-indexed chunks are meaningless and everything must be re-captured from
 * source. When such an element is not reusable the plan is a {@code fullRecapture}.
 * <p>
 * "Reusable" is a {@link Coverage} question, and it depends on <b>which records the step is about</b>. A
 * whole-stream reprocess needs the whole stream ({@code span == null}, answered by the
 * {@code hasCompleteElement} fast path); a step materialising a handful of records only needs the feed
 * element to hold <i>those</i> records - an element captured up to a frontier is reusable for everything
 * behind it long before it is "complete". Callers that pass null today get exactly the old behaviour; the
 * span is what lets a partial capture serve (see {@code stepping-design.md} §11, build order stage 1).
 * <p>
 * Live via {@link ReprocessPlanner}, which turns the plan into the sweep-vs-reprocess launch decision.
 */
public class StagePlanner {

    /**
     * @param elements  the steppable elements in processing (topological) order.
     * @param store     the current stream's store.
     * @param current   the fingerprints for the current pipeline configuration.
     * @return the reuse/reprocess plan.
     */
    public StagePlan plan(final List<PlannerElement> elements,
                          final StepDataStore store,
                          final ElementFingerprints current) {
        return plan(elements, store, current, null);
    }

    /**
     * As above, but reuse is judged against the records the step is actually about: an element is reusable
     * if its coverage <b>holds every record of the span</b>, complete or not.
     *
     * @param span the records the step needs, or null for the whole stream.
     */
    public StagePlan plan(final List<PlannerElement> elements,
                          final StepDataStore store,
                          final ElementFingerprints current,
                          final RecordSpan span) {
        final Set<String> reuse = new LinkedHashSet<>();
        final Set<String> reprocess = new LinkedHashSet<>();
        boolean boundaryChanged = false;

        for (final PlannerElement element : elements) {
            final String fingerprint = current.getCumulativeFingerprint(element.id());
            final boolean reusable = fingerprint != null
                    && covers(store, new ElementId(element.id()), fingerprint, span);
            if (reusable) {
                reuse.add(element.id());
            } else {
                reprocess.add(element.id());
                if (element.atOrAboveRecordBoundary()) {
                    boundaryChanged = true;
                }
            }
        }

        if (boundaryChanged) {
            // Record boundaries themselves changed; nothing record-indexed can be trusted.
            final Set<String> all = new LinkedHashSet<>();
            elements.forEach(e -> all.add(e.id()));
            return new StagePlan(true, Set.of(), all);
        }

        return new StagePlan(false, reuse, reprocess);
    }

    /**
     * Does this element's captured IO cover what is being asked of it?
     * <p>
     * Whole stream ({@code span == null}): the {@code hasCompleteElement} fast path - an element materialised
     * a record at a time is <i>present</i> long before it is <i>reusable wholesale</i>, and treating presence
     * as reuse once made the planner conclude there was nothing left to reprocess.
     * <p>
     * A span: every record of it must be <b>held</b>, individually - a sparse element has holes inside its
     * own bounds, and a bounds answer here would feed a replay from records that do not exist. The walk is
     * bounded by the span, which is a scan window at most, never a stream.
     */
    private boolean covers(final StepDataStore store,
                           final ElementId elementId,
                           final String fingerprint,
                           final RecordSpan span) {
        if (span == null) {
            return store.hasCompleteElement(elementId, fingerprint);
        }
        if (span.firstRecord() > span.lastRecord()) {
            // An empty span is a caller error, not a vacuously-covered demand; permissive here would hand a
            // replay a feed that was never checked at all.
            return false;
        }
        final Coverage coverage = store.elementCoverage(elementId, fingerprint, () -> false);
        for (long record = span.firstRecord(); record <= span.lastRecord(); record++) {
            if (!coverage.holds(span.partIndex(), record)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The records a step is about: a contiguous run within one part. What a REFRESH (one record) or a
     * windowed scan (a window of them) asks the plan to cover.
     */
    public record RecordSpan(long partIndex, long firstRecord, long lastRecord) {
    }

    /**
     * An element for planning: its id and whether it sits at or above the record-boundary (Source,
     * readers, parser, split) - a change to such an element forces a full re-capture.
     */
    public record PlannerElement(String id, boolean atOrAboveRecordBoundary) {
    }

    /**
     * The outcome: whether a full re-capture from source is required, and otherwise which elements to
     * reuse from the store vs (re)process.
     */
    public record StagePlan(boolean fullRecapture, Set<String> reuse, Set<String> reprocess) {
    }
}
