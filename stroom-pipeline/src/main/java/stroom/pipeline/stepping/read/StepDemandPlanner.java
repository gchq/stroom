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

import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.stepping.fingerprint.ElementFingerprints;
import stroom.pipeline.stepping.read.ReprocessPlanner.Decision;
import stroom.pipeline.stepping.read.SteppingGraphBuilder.Graph;
import stroom.pipeline.stepping.store.Coverage;
import stroom.pipeline.stepping.store.RecordRange;
import stroom.pipeline.stepping.store.StepDataStore;
import stroom.pipeline.stepping.store.SteppingConfig;
import stroom.util.shared.ElementId;

import jakarta.inject.Provider;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The pure arithmetic of what a step <b>demands</b>: which record it is about, how wide a window to
 * materialise around it, and where a filtered scan's next window starts. Everything here is a computation
 * over the request, the plan and the store's {@link Coverage} - no launching, no waiting, no sessions -
 * so it sits on the read side and is testable without a pipeline. {@code SteppingService}'s launch policy
 * consumes its answers.
 */
public class StepDemandPlanner {

    // A Provider, not the object: config is live (the UI can change it, and tests override it per class).
    private final Provider<SteppingConfig> configProvider;

    public StepDemandPlanner(final Provider<SteppingConfig> configProvider) {
        this.configProvider = configProvider;
    }

    /**
     * Decide whether this step can be answered by materialising the <b>one record</b> it is about, rather
     * than re-running the edited element over the whole stream.
     * <p>
     * The step's record has to be knowable ({@link #demandedRecordFor}), and no filter may sit on the edited
     * element or below it: deciding whether a record matches such a filter means running the element to find
     * out, so the target cannot be known in advance and records have to be worked through in order instead
     * (the windowed scan). In principle a filter on an element <i>above</i> the edit is no obstacle - its
     * output is already in the store under an unchanged fingerprint - but {@code demandedRecordFor}
     * currently refuses on ANY applied filter; the above-the-edit refinement is possible, not built.
     *
     * @return the records to materialise, or null to reprocess the whole stream as before.
     */
    public RecordRange onDemandRangeFor(final PipelineStepRequest request,
                                         final Graph graph,
                                         final Decision decision,
                                         final StepDataStore store,
                                         final ElementFingerprints fingerprints,
                                         final long metaId,
                                         final boolean extentFinal) {
        final StepLocation located = onDemandTargetFor(request, graph, decision, store, metaId, extentFinal);
        if (located != null) {
            return prefetched(request.getStepType(), located, decision, store, fingerprints);
        }
        return filteredWindowFor(request, decision, store, fingerprints, metaId);
    }

    /**
     * Widen a navigation step's demand to a window in its direction of travel, so the next few steps hit
     * records this one already materialised - a store read, no launch - instead of launching per record.
     * <p>
     * The demanded record stays at the near edge of the window, and the replay produces records in
     * ascending order, so for FORWARD/FIRST the step is served as soon as its own record commits and the
     * rest materialise while the user reads it. BACKWARD/LAST extend downward, so the demanded record
     * commits last - a few milliseconds of below-boundary work per extra record, well under the launch
     * overhead the window saves.
     * <p>
     * Two hard edges. A REFRESH is never widened: it is the post-edit inner loop, and it materialises
     * exactly the record it names. And the window is clamped to the <b>feed's contiguous coverage</b> from
     * the target - the replay reads each record's input from the feed's stored output and fails loudly on a
     * gap, so a window must never reach past the backbone's frontier or across a hole punched by an earlier
     * on-demand materialisation... the hole's records are ALREADY materialised, which is exactly when the
     * window has nothing left to buy.
     */
    private RecordRange prefetched(final StepType stepType,
                                   final StepLocation target,
                                   final Decision decision,
                                   final StepDataStore store,
                                   final ElementFingerprints fingerprints) {
        final int window = configProvider.get().getPrefetchWindow();
        final String upstreamFingerprint = decision.upstreamElementId() == null
                ? null
                : fingerprints.getCumulativeFingerprint(decision.upstreamElementId());
        if (window <= 1 || stepType == StepType.REFRESH || upstreamFingerprint == null) {
            return RecordRange.of(target);
        }
        final Coverage feed = store.elementCoverage(
                new ElementId(decision.upstreamElementId()), upstreamFingerprint, () -> false);
        final boolean forward = stepType == StepType.FORWARD || stepType == StepType.FIRST;
        long start = target.getRecordIndex();
        long end = target.getRecordIndex();
        for (int i = 1; i < window; i++) {
            final long candidate = forward ? end + 1 : start - 1;
            if (candidate < 0 || !feed.holds(target.getPartIndex(), candidate)) {
                break;
            }
            if (forward) {
                end = candidate;
            } else {
                start = candidate;
            }
        }
        return new RecordRange(target.getPartIndex(), start, end);
    }

    /**
     * The next run of records to materialise for a <b>filtered</b> step, or null to reprocess the stream.
     * <p>
     * A filter makes "the next record" mean "the next one that matches", which cannot be known without
     * running records to find out. So rather than materialise one record, materialise a window of them in the
     * direction of travel and let the resolver scan it; if nothing in the window matches, the client's next
     * poll asks again and the window moves on.
     * <p>
     * Where the next window starts is <b>read back from the store</b> rather than remembered: the records
     * already materialised for this element at this fingerprint are the frontier, so the window simply starts
     * past them. That is what keeps this on one side of the {@code capture/}-{@code read/} line - the two
     * sides meet at the store, as everything else here does, instead of one driving the other round a loop.
     */
    // Package-private rather than private so a test can drive it directly. Its output is a range, which is
    // otherwise only visible through what a whole step happens to materialise - and the window size defaults
    // to the value it had as a constant, so a test that went through step() would pass whether or not the
    // size is really read from config.
    RecordRange filteredWindowFor(final PipelineStepRequest request,
                                  final Decision decision,
                                  final StepDataStore store,
                                  final ElementFingerprints fingerprints,
                                  final long metaId) {
        final StepType stepType = request.getStepType();
        final boolean forward = stepType == StepType.FORWARD || stepType == StepType.FIRST;
        final boolean backward = stepType == StepType.BACKWARD || stepType == StepType.LAST;
        if (!forward && !backward) {
            return null;
        }
        final String fingerprint = fingerprints.getCumulativeFingerprint(decision.startElementId());
        if (fingerprint == null) {
            return null;
        }
        final StepLocation ref = request.getStepLocation();
        // Two coverages frame the scan: the stream's records say where a scan may reach, and the scanned
        // element's own coverage says where the last window ended - the frontier read back from the store
        // rather than remembered between polls.
        final Coverage stream = store.recordCoverage(() -> false);
        final Coverage scanned = store.elementCoverage(
                new ElementId(decision.startElementId()), fingerprint, () -> false);
        // A step that names no record starts at the end of the stream it is walking from.
        final List<Long> parts = stream.parts();
        if (parts.isEmpty()) {
            return null;
        }
        final long part = ref != null && ref.getMetaId() == metaId
                ? ref.getPartIndex()
                : (forward ? parts.getFirst() : parts.getLast());
        final long streamFirst = stream.first(part);
        final long streamLast = stream.last(part);
        if (streamFirst < 0 || streamLast < 0) {
            return null;
        }

        // The scan's ORIGIN: just past the reference for FORWARD/BACKWARD, the end of the stream being
        // walked from for FIRST/LAST.
        final long origin;
        if (ref != null && ref.getMetaId() == metaId && ref.getPartIndex() == part
            && (stepType == StepType.FORWARD || stepType == StepType.BACKWARD)) {
            origin = forward ? ref.getRecordIndex() + 1 : ref.getRecordIndex() - 1;
        } else {
            origin = forward ? streamFirst : streamLast;
        }
        // The window starts at the first record FROM THE ORIGIN the scanned element does not already hold -
        // a per-record walk, not the coverage bounds. The bounds are poisoned by any other materialisation
        // under the same fingerprint (a post-edit REFRESH mid-stream, an unfiltered step's prefetch
        // window): starting past those would leave the records between the origin and them unmaterialised,
        // and the resolver would read them as non-matches - a silently WRONG filtered landing. Walking
        // holds() both resumes past this scan's own windows (they are contiguous from the origin) and
        // fills anybody else's gaps, and already-held records cost the resolver only store reads.
        long start = origin;
        while (start >= streamFirst && start <= streamLast && scanned.holds(part, start)) {
            start += forward ? 1 : -1;
        }
        if (start < streamFirst || start > streamLast) {
            // Everything from the origin is held (or the stream is exhausted): nothing left for a window to
            // materialise; the whole-stream path handles crossing to the next stream.
            return null;
        }

        final int window = configProvider.get().getFilteredScanWindow();
        final long end = forward
                ? Math.min(streamLast, start + window - 1)
                : Math.max(streamFirst, start - window + 1);
        return forward
                ? new RecordRange(part, start, end)
                : new RecordRange(part, end, start);
    }

    private StepLocation onDemandTargetFor(final PipelineStepRequest request,
                                           final Graph graph,
                                           final Decision decision,
                                           final StepDataStore store,
                                           final long metaId,
                                           final boolean extentFinal) {
        if (isFilteredAtOrBelow(request, graph, decision.startElementId())) {
            return null;
        }
        return demandedRecordFor(request, metaId, store, extentFinal);
    }

    /**
     * The single record this step is about, if that can be worked out from what has already been captured.
     * <p>
     * A REFRESH names its record outright. Navigation does not - it has to be derived - but deriving it is
     * simple arithmetic on the captured record coverage, and that arithmetic is just as valid against a
     * capture still in flight as against a finished one: the record after the one the user is looking at is
     * the record after it, whether or not the stream beyond has been captured yet. That is what lets an edit
     * followed by a step (rather than a refresh) be answered by materialising one record instead of capturing
     * the stream a second time.
     * <p>
     * Two kinds of step cannot be answered this way:
     * <ul>
     *   <li><b>Filtered</b> - a filter makes "the next record" mean "the next one that <i>matches</i>", which
     *   cannot be known without running records to find out. The windowed scan (see {@link #filteredWindowFor})
     *   answers those, and it needs the whole-stream path first.</li>
     *   <li><b>LAST against a capture still running</b> - it asks where the stream <i>ends</i>, and the last
     *   record captured so far is merely the last so far. Answering from it would land mid-stream.</li>
     * </ul>
     *
     * @param extentFinal whether the captured extent is the whole stream, which is what LAST needs.
     * @return the record, or null if this step's demand cannot be named yet.
     */
    public StepLocation demandedRecordFor(final PipelineStepRequest request,
                                           final long metaId,
                                           final StepDataStore store,
                                           final boolean extentFinal) {
        final StepType stepType = request.getStepType();
        final StepLocation ref = request.getStepLocation();
        if (stepType == StepType.REFRESH) {
            // A reference to another stream's record is not a demand on this one; the resolver only ever
            // refreshes the stream the reference is in, so this is a guard rather than a case.
            return (ref != null && ref.getMetaId() == metaId) ? ref : null;
        }
        if (isAnyFilterApplied(request)) {
            return null;
        }
        final Coverage stream = store.recordCoverage(() -> extentFinal);
        final List<Long> parts = stream.parts();
        if (parts.isEmpty()) {
            return null;
        }
        return switch (stepType) {
            case FIRST -> locationIn(metaId, stream, parts.getFirst(), true);
            case LAST -> extentFinal ? locationIn(metaId, stream, parts.getLast(), false) : null;
            case FORWARD, BACKWARD -> neighbourOf(metaId, stream, ref, stepType == StepType.FORWARD);
            default -> null;
        };
    }

    private StepLocation locationIn(final long metaId,
                                    final Coverage stream,
                                    final long partIndex,
                                    final boolean first) {
        final long record = first ? stream.first(partIndex) : stream.last(partIndex);
        return record < 0 ? null : new StepLocation(metaId, partIndex, record);
    }

    /**
     * @return the record either side of {@code ref}, continuing into the neighbouring <b>part</b> when it
     * runs off the end of its own. Null once the stream itself runs out: crossing to another stream is the
     * resolver's job - the next stream may not even be swept yet - and it needs the whole-stream path.
     */
    private StepLocation neighbourOf(final long metaId,
                                     final Coverage stream,
                                     final StepLocation ref,
                                     final boolean forward) {
        if (ref == null || ref.getMetaId() != metaId) {
            return null;
        }
        final long part = ref.getPartIndex();
        final long candidate = ref.getRecordIndex() + (forward ? 1 : -1);
        // Bounds, not holds(): the question is whether the record EXISTS in the stream - a hole punched by
        // an earlier on-demand materialisation is still a legitimate record to step to and materialise.
        if (candidate >= stream.first(part) && candidate <= stream.last(part)) {
            return new StepLocation(metaId, part, candidate);
        }

        // Off the end of this part. A multi-part stream is one stream to the user, so stepping should carry
        // on into the next part rather than drop to reprocessing the whole stream to do it.
        final List<Long> parts = stream.parts();
        final int index = parts.indexOf(part);
        if (index < 0) {
            return null;
        }
        final int neighbourIndex = forward ? index + 1 : index - 1;
        if (neighbourIndex < 0 || neighbourIndex >= parts.size()) {
            return null;
        }
        return locationIn(metaId, stream, parts.get(neighbourIndex), forward);
    }

    public boolean isAnyFilterApplied(final PipelineStepRequest request) {
        return request.getStepFilterMap() != null
               && request.getStepFilterMap().values().stream()
                       .anyMatch(settings -> settings != null && settings.isFilterApplied());
    }

    private boolean isFilteredAtOrBelow(final PipelineStepRequest request,
                                        final Graph graph,
                                        final String startElementId) {
        if (request.getStepFilterMap() == null || request.getStepFilterMap().isEmpty()) {
            return false;
        }
        final Set<String> atOrBelow = new HashSet<>();
        collectAtOrBelow(graph, startElementId, atOrBelow);
        return atOrBelow.stream()
                .map(request::getStepFilterSettings)
                .anyMatch(settings -> settings != null && settings.isFilterApplied());
    }

    private void collectAtOrBelow(final Graph graph, final String elementId, final Set<String> collected) {
        if (!collected.add(elementId)) {
            return;
        }
        graph.parentsOf().forEach((child, parents) -> {
            if (parents.contains(elementId)) {
                collectAtOrBelow(graph, child, collected);
            }
        });
    }
}
