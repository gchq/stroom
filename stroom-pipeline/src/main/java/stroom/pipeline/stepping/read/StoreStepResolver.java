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

import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.SourceLocation;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.SharedStepData;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.shared.stepping.SteppingFilterSettings;
import stroom.pipeline.stepping.fingerprint.ElementFingerprints;
import stroom.pipeline.stepping.store.CapturedElementData;
import stroom.pipeline.stepping.store.CapturedElementDataMapper;
import stroom.pipeline.stepping.store.Coverage;
import stroom.pipeline.stepping.store.StepDataStore;
import stroom.util.shared.ElementId;
import stroom.util.shared.NullSafe;

import java.util.HashMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves a step (FIRST/FORWARD/BACKWARD/LAST/REFRESH, with optional filters) against <b>one stream's</b>
 * captured data - by lookup and scan, with no pipeline reprocessing. This is what makes stepping cheap: the
 * pipeline runs once per stream to fill the store, not once per keypress.
 * <p>
 * Deliberately pure and synchronous. It knows a {@link StepDataStore} and nothing else - no sessions, no
 * sweeps, no waiting, no threads - so it can be reasoned about and tested on its own. Waiting for a record
 * that has not been captured yet, and walking into neighbouring streams, are
 * {@link SessionStepResolver}'s job.
 * <p>
 * A record that is not in the store reads back as absent rather than as "no such record": the store holds a
 * contiguous range per part, so {@link #next}/{@link #prev} refuse to step outside it and return empty,
 * which the caller must interpret as "not captured yet" unless the stream is known to be fully captured.
 * <p>
 * Records are ordered by (partIndex, recordIndex). Filtering mirrors {@code SteppingController.endRecord}:
 * a record matches if no filters are applied, or if any applied element's filter matches (see
 * {@link PersistedFilterEvaluator}).
 */
public class StoreStepResolver {

    private final PersistedFilterEvaluator filterEvaluator = new PersistedFilterEvaluator();


    /**
     * @return the resolved record location and assembled step data, or empty if no matching record exists
     * in this stream. Navigation is bounded by the store's own captured range.
     */
    public Optional<ResolvedStep> resolve(final StepDataStore store,
                                          final long metaId,
                                          final ElementFingerprints fingerprints,
                                          final PipelineStepRequest request) {
        return resolve(store, metaId, fingerprints, request, CapturedRange.of(store));
    }

    /**
     * As {@link #resolve(StepDataStore, long, ElementFingerprints, PipelineStepRequest)}, but navigation is
     * bounded by {@code range} rather than the store's full range. This matters for a reprocess: it writes the
     * changed elements into a store that already holds the reused upstream at the full range, so the caller
     * passes the reprocess sweep's own captured range and a record is only reachable once the reprocess has
     * actually written it - never via the reused upstream alone. For a full sweep the two ranges coincide.
     *
     * @return the resolved record location and assembled step data, or empty if no matching record exists
     * within {@code range}.
     */
    public Optional<ResolvedStep> resolve(final StepDataStore store,
                                          final long metaId,
                                          final ElementFingerprints fingerprints,
                                          final PipelineStepRequest request,
                                          final CapturedRange range) {
        final List<Long> parts = store.getPartIndices();
        if (parts.isEmpty()) {
            return Optional.empty();
        }

        final StepType stepType = request.getStepType();
        // A reference location only makes sense for the stream being resolved; ignore one from another
        // stream. Crossing stream boundaries is resolveSession's job, not this per-stream scan's.
        final StepLocation requestRef = request.getStepLocation();
        final StepLocation ref = (requestRef != null && requestRef.getMetaId() == metaId) ? requestRef : null;

        final Optional<StepLocation> target = switch (stepType) {
            case FIRST -> scanForward(store, parts, metaId, firstRecord(parts, metaId, range), request,
                    fingerprints, range);
            case LAST -> scanBackward(store, parts, metaId, lastRecord(parts, metaId, range), request,
                    fingerprints, range);
            case FORWARD -> {
                final StepLocation start = ref == null
                        ? firstRecord(parts, metaId, range)
                        : next(store, parts, metaId, ref, range).orElse(null);
                yield start == null
                        ? Optional.empty()
                        : scanForward(store, parts, metaId, start, request, fingerprints, range);
            }
            case BACKWARD -> {
                final StepLocation start = ref == null
                        ? lastRecord(parts, metaId, range)
                        : prev(store, parts, metaId, ref, range).orElse(null);
                yield start == null
                        ? Optional.empty()
                        : scanBackward(store, parts, metaId, start, request, fingerprints, range);
            }
            case REFRESH -> (ref != null && exists(parts, ref, range))
                    ? Optional.of(new StepLocation(metaId, ref.getPartIndex(), ref.getRecordIndex()))
                    : Optional.empty();
        };

        return target.map(loc -> new ResolvedStep(loc, assemble(store, metaId, fingerprints, loc)));
    }

    /**
     * The per-part record range a resolve may navigate within: usually the store's own range, but for a
     * reprocess the sweep's captured range (see the {@code CapturedRange} overload of {@link #resolve}).
     */
    public interface CapturedRange {

        long NONE = -1;

        /**
         * @return the first captured record index for the part, or {@link #NONE} if none.
         */
        long first(long partIndex);

        /**
         * @return the last captured record index for the part, or {@link #NONE} if none.
         */
        long last(long partIndex);

        /**
         * @return true if this record is actually available to serve.
         * <p>
         * Separate from {@link #first}/{@link #last} because those describe where a stream <i>reaches</i>,
         * which is not the same question once records are materialised individually rather than swept in
         * order: an element that holds only the records the user has visited has gaps inside its own span.
         * The default answers from the bounds, which is right for anything captured contiguously.
         */
        default boolean contains(final long partIndex, final long recordIndex) {
            final long first = first(partIndex);
            final long last = last(partIndex);
            return first >= 0 && last >= 0 && recordIndex >= first && recordIndex <= last;
        }

        /**
         * The navigation view of a {@link Coverage}. This is the only bridge between the two: coverage is
         * what a producer has captured, a range is what a resolve may navigate, and every range in the
         * system is now some coverage seen through this view.
         */
        static CapturedRange of(final Coverage coverage) {
            return new CapturedRange() {
                @Override
                public long first(final long partIndex) {
                    return coverage.first(partIndex);
                }

                @Override
                public long last(final long partIndex) {
                    return coverage.last(partIndex);
                }

                @Override
                public boolean contains(final long partIndex, final long recordIndex) {
                    return coverage.holds(partIndex, recordIndex);
                }
            };
        }

        /**
         * The whole store's range. Correct while a single producer writes every element of a record together,
         * which is what a full sweep does. Bounds-only on purpose: the synchronous single-producer path this
         * serves has no holes, and answering {@code contains} from the bounds is what its callers always got.
         */
        static CapturedRange of(final StepDataStore store) {
            return new CapturedRange() {
                @Override
                public long first(final long partIndex) {
                    return store.getFirstRecordIndex(partIndex);
                }

                @Override
                public long last(final long partIndex) {
                    return store.getLastRecordIndex(partIndex);
                }
            };
        }

        /**
         * The range every one of {@code ranges} has reached: {@code first} is the highest of their firsts and
         * {@code last} the lowest of their lasts.
         * <p>
         * Once elements are captured by independent producers they sit at different positions, and a step has
         * to show <b>all</b> of a record's elements at once - a record that only some of them have reached
         * would be served with the rest of its panes silently blank. So the servable range is the intersection,
         * and a step aimed past it waits rather than resolving. If any contributor has captured nothing for a
         * part, or their ranges do not overlap, the part has nothing servable at all.
         * <p>
         * An empty collection yields an empty range for the same reason: nothing has been captured, so there
         * is nothing to serve.
         */
        static CapturedRange intersectionOf(final Collection<? extends CapturedRange> ranges) {
            final List<CapturedRange> contributors = List.copyOf(ranges);
            return new CapturedRange() {
                @Override
                public long first(final long partIndex) {
                    return intersect(contributors, partIndex)[0];
                }

                @Override
                public long last(final long partIndex) {
                    return intersect(contributors, partIndex)[1];
                }

                @Override
                public boolean contains(final long partIndex, final long recordIndex) {
                    // Every contributor must hold it, not merely span it - one that has a hole here cannot
                    // show its pane, and a record served with a blank pane is the failure this prevents.
                    return !contributors.isEmpty()
                           && contributors.stream().allMatch(r -> r.contains(partIndex, recordIndex));
                }
            };
        }

        /**
         * A range that takes its <b>bounds</b> from one source and its <b>contents</b> from another.
         * <p>
         * This is how a step can be answered about an element that holds only what the user has visited. Such
         * an element never "completes" and its own last record is just the last one looked at, so asking it
         * where the stream ends gives the wrong answer - LAST would land mid-stream. The bounds therefore
         * come from the upstream element that really did capture the whole stream, while what is servable
         * comes from the materialised element itself.
         */
        static CapturedRange spanning(final CapturedRange bounds, final CapturedRange held) {
            return new CapturedRange() {
                @Override
                public long first(final long partIndex) {
                    return bounds.first(partIndex);
                }

                @Override
                public long last(final long partIndex) {
                    return bounds.last(partIndex);
                }

                @Override
                public boolean contains(final long partIndex, final long recordIndex) {
                    return held.contains(partIndex, recordIndex);
                }
            };
        }

        /**
         * @return {@code [first, last]}, or {@code [NONE, NONE]} if any contributor has nothing for this part
         * or the ranges do not overlap. Recomputed per call rather than cached: the contributors are live
         * producers, and a stale range would either hide records that have arrived or, worse, admit ones that
         * have not.
         */
        private static long[] intersect(final List<CapturedRange> contributors, final long partIndex) {
            final long[] none = {NONE, NONE};
            if (contributors.isEmpty()) {
                return none;
            }
            long first = Long.MIN_VALUE;
            long last = Long.MAX_VALUE;
            for (final CapturedRange range : contributors) {
                final long rangeFirst = range.first(partIndex);
                final long rangeLast = range.last(partIndex);
                if (rangeFirst < 0 || rangeLast < 0) {
                    return none;
                }
                first = Math.max(first, rangeFirst);
                last = Math.min(last, rangeLast);
            }
            return first > last ? none : new long[]{first, last};
        }
    }

    // --- scanning -------------------------------------------------------------------------------

    private Optional<StepLocation> scanForward(final StepDataStore store,
                                               final List<Long> parts,
                                               final long metaId,
                                               final StepLocation start,
                                               final PipelineStepRequest request,
                                               final ElementFingerprints fingerprints,
                                               final CapturedRange range) {
        StepLocation loc = start;
        while (loc != null) {
            if (matches(store, loc, request, fingerprints)) {
                return Optional.of(loc);
            }
            loc = next(store, parts, metaId, loc, range).orElse(null);
        }
        return Optional.empty();
    }

    private Optional<StepLocation> scanBackward(final StepDataStore store,
                                                final List<Long> parts,
                                                final long metaId,
                                                final StepLocation start,
                                                final PipelineStepRequest request,
                                                final ElementFingerprints fingerprints,
                                                final CapturedRange range) {
        StepLocation loc = start;
        while (loc != null) {
            if (matches(store, loc, request, fingerprints)) {
                return Optional.of(loc);
            }
            loc = prev(store, parts, metaId, loc, range).orElse(null);
        }
        return Optional.empty();
    }

    /**
     * Mirrors {@code SteppingController.endRecord}: found if no filters are applied, or any applied
     * element's filter matches this record.
     */
    private boolean matches(final StepDataStore store,
                            final StepLocation loc,
                            final PipelineStepRequest request,
                            final ElementFingerprints fingerprints) {
        final Map<String, SteppingFilterSettings> filterMap = request.getStepFilterMap();
        if (NullSafe.isEmptyMap(filterMap)) {
            return true;
        }

        boolean anyApplied = false;
        for (final Map.Entry<String, SteppingFilterSettings> entry : filterMap.entrySet()) {
            final SteppingFilterSettings settings = entry.getValue();
            if (settings == null || !settings.isFilterApplied()) {
                continue;
            }
            anyApplied = true;
            // Filter against the stored (captured) form, not the rendered wire form: XPath filters build
            // their tree from the events directly, with no re-parse.
            final CapturedElementData data = readCaptured(store, loc, entry.getKey(), fingerprints);
            if (data != null && filterEvaluator.matches(
                    data, settings, loc.getMetaId(), loc.getRecordIndex())) {
                return true;
            }
        }
        // No applied filters => every record is a match; otherwise a match needed at least one hit.
        return !anyApplied;
    }

    // --- navigation over (part, record) ---------------------------------------------------------

    private StepLocation firstRecord(final List<Long> parts, final long metaId, final CapturedRange range) {
        // The first part that has captured records; a part with none yet (first == -1) is skipped.
        for (final long part : parts) {
            final long first = range.first(part);
            if (first >= 0) {
                return new StepLocation(metaId, part, first);
            }
        }
        return null;
    }

    private StepLocation lastRecord(final List<Long> parts, final long metaId, final CapturedRange range) {
        for (int i = parts.size() - 1; i >= 0; i--) {
            final long part = parts.get(i);
            final long last = range.last(part);
            if (last >= 0) {
                return new StepLocation(metaId, part, last);
            }
        }
        return null;
    }

    /**
     * The neighbouring records of a location, or empty if the captured range cannot answer yet.
     * <p>
     * A sweep fills a part in record order, so the captured range is contiguous and anything outside it is
     * simply "not captured yet". Both directions must refuse to step onto such a record: empty makes
     * {@code resolveSession} wait for the sweep to get there (and only means "there is no such record", i.e.
     * cross into the neighbouring stream, once the sweep has completed and the range is final).
     */
    private Optional<StepLocation> next(final StepDataStore store,
                                        final List<Long> parts,
                                        final long metaId,
                                        final StepLocation loc,
                                        final CapturedRange range) {
        final long part = loc.getPartIndex();
        final long record = loc.getRecordIndex();
        final long last = range.last(part);
        if (last < 0) {
            // The serving range holds NOTHING of this part - a materialisation produced for a neighbouring
            // part, which is what a cross-part step demands. Crossing is only safe when the reference sits at
            // its part's true end (per the store, whose extent the parser capture fills whatever else has or
            // has not run), so no record of this part is being stepped over; anywhere short of that the
            // absent records may yet be captured here, and the answer is to wait, not to skip them.
            final int idx = parts.indexOf(part);
            if (idx >= 0 && record >= store.getLastRecordIndex(part)) {
                for (int i = idx + 1; i < parts.size(); i++) {
                    final long candidateFirst = range.first(parts.get(i));
                    if (candidateFirst >= 0) {
                        return Optional.of(new StepLocation(metaId, parts.get(i), candidateFirst));
                    }
                }
            }
            return Optional.empty();
        }
        if (record < last) {
            return Optional.of(new StepLocation(metaId, part, record + 1));
        }
        if (record > last) {
            // Ahead of the sweep - the next record may yet be captured here.
            return Optional.empty();
        }
        final int idx = parts.indexOf(part);
        if (idx >= 0 && idx + 1 < parts.size()) {
            final long nextPart = parts.get(idx + 1);
            final long nextFirst = range.first(nextPart);
            // The next part has not been captured yet; wait rather than step onto a not-yet-present record.
            return nextFirst < 0 ? Optional.empty() : Optional.of(new StepLocation(metaId, nextPart, nextFirst));
        }
        return Optional.empty();
    }

    private Optional<StepLocation> prev(final StepDataStore store,
                                        final List<Long> parts,
                                        final long metaId,
                                        final StepLocation loc,
                                        final CapturedRange range) {
        final long part = loc.getPartIndex();
        final long record = loc.getRecordIndex();
        final long first = range.first(part);
        final long last = range.last(part);
        if (first < 0) {
            // Mirror of next(): the range holds nothing of this part, so it serves a neighbouring part's
            // records. Cross back only from the part's true first record - stepping back from anywhere later
            // would skip this part's earlier records, which are merely not captured yet (the BACKWARD
            // ahead-of-the-sweep trap).
            final int idx = parts.indexOf(part);
            if (idx >= 0 && record <= store.getFirstRecordIndex(part)) {
                for (int i = idx - 1; i >= 0; i--) {
                    final long candidateLast = range.last(parts.get(i));
                    if (candidateLast >= 0) {
                        return Optional.of(new StepLocation(metaId, parts.get(i), candidateLast));
                    }
                }
            }
            return Optional.empty();
        }
        if (record > first) {
            final long candidate = record - 1;
            // Stepping back from a reference the sweep has not reached yet would walk down over records
            // that are merely absent-so-far, treat each as a non-match, and land on the first record of the
            // part. Wait for the sweep instead.
            return candidate <= last
                    ? Optional.of(new StepLocation(metaId, part, candidate))
                    : Optional.empty();
        }
        if (record > last) {
            return Optional.empty();
        }
        final int idx = parts.indexOf(part);
        if (idx > 0) {
            final long prevPart = parts.get(idx - 1);
            final long prevLast = range.last(prevPart);
            return prevLast < 0 ? Optional.empty() : Optional.of(new StepLocation(metaId, prevPart, prevLast));
        }
        return Optional.empty();
    }

    private boolean exists(final List<Long> parts, final StepLocation loc, final CapturedRange range) {
        return parts.contains(loc.getPartIndex())
               && range.contains(loc.getPartIndex(), loc.getRecordIndex());
    }

    // --- assembly -------------------------------------------------------------------------------

    private SharedStepData assemble(final StepDataStore store,
                                    final long metaId,
                                    final ElementFingerprints fingerprints,
                                    final StepLocation loc) {
        final Map<String, SharedElementData> map = new HashMap<>();
        for (final String elementId : fingerprints.getElementIds()) {
            final SharedElementData data = readElement(store, loc, elementId, fingerprints);
            if (data != null) {
                map.put(elementId, data);
            }
        }
        // Enrich the served location with the per-record highlight/DataRange snapshotted at capture. The
        // authoritative (metaId, part, record) coordinates stay those of the resolved step; only the source
        // ranges come from the store. A record captured without a snapshot degrades to no highlight, exactly
        // as the served path behaved before the snapshot was persisted.
        final SourceLocation stored = store.getSourceLocation(loc).orElse(null);
        final SourceLocation sourceLocation = SourceLocation.builder(metaId)
                .withPartIndex(loc.getPartIndex())
                .withRecordIndex(loc.getRecordIndex())
                .withDataRange(stored != null ? stored.getDataRange() : null)
                .withHighlight(stored != null ? stored.getHighlights() : null)
                .build();
        return new SharedStepData(sourceLocation, map);
    }

    private SharedElementData readElement(final StepDataStore store,
                                          final StepLocation loc,
                                          final String elementId,
                                          final ElementFingerprints fingerprints) {
        // The store holds the element-specific captured form (SAX events or text); render it to the wire
        // form (text on both sides). This is where a stored XML element's events become display text, via
        // the Saxon tree path, so it stays byte-identical to the pre-events store.
        return CapturedElementDataMapper.toShared(readCaptured(store, loc, elementId, fingerprints));
    }

    private CapturedElementData readCaptured(final StepDataStore store,
                                             final StepLocation loc,
                                             final String elementId,
                                             final ElementFingerprints fingerprints) {
        final String fingerprint = fingerprints.getCumulativeFingerprint(elementId);
        if (fingerprint == null) {
            return null;
        }
        return store.getElementData(loc, new ElementId(elementId), fingerprint).orElse(null);
    }

    // --------------------------------------------------------------------------------

    /**
     * A resolved step: the record that was found and the per-element data assembled for it.
     */
    public record ResolvedStep(StepLocation foundLocation, SharedStepData stepData) {
    }

}
