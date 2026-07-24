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
import stroom.pipeline.stepping.store.StepDataStore;
import stroom.util.shared.ElementId;
import stroom.util.shared.NullSafe;

import java.util.HashMap;
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
        return resolve(store, metaId, fingerprints, request, new CapturedRange() {
            @Override
            public long first(final long partIndex) {
                return store.getFirstRecordIndex(partIndex);
            }

            @Override
            public long last(final long partIndex) {
                return store.getLastRecordIndex(partIndex);
            }
        });
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
                        : next(parts, metaId, ref, range).orElse(null);
                yield start == null
                        ? Optional.empty()
                        : scanForward(store, parts, metaId, start, request, fingerprints, range);
            }
            case BACKWARD -> {
                final StepLocation start = ref == null
                        ? lastRecord(parts, metaId, range)
                        : prev(parts, metaId, ref, range).orElse(null);
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

        /**
         * @return the first captured record index for the part, or -1 if none.
         */
        long first(long partIndex);

        /**
         * @return the last captured record index for the part, or -1 if none.
         */
        long last(long partIndex);
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
            loc = next(parts, metaId, loc, range).orElse(null);
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
            loc = prev(parts, metaId, loc, range).orElse(null);
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
    private Optional<StepLocation> next(final List<Long> parts,
                                        final long metaId,
                                        final StepLocation loc,
                                        final CapturedRange range) {
        final long part = loc.getPartIndex();
        final long record = loc.getRecordIndex();
        final long last = range.last(part);
        if (record < last) {
            return Optional.of(new StepLocation(metaId, part, record + 1));
        }
        if (record > last) {
            // Ahead of the sweep (or this part not captured yet) - the next record may yet be captured here.
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

    private Optional<StepLocation> prev(final List<Long> parts,
                                        final long metaId,
                                        final StepLocation loc,
                                        final CapturedRange range) {
        final long part = loc.getPartIndex();
        final long record = loc.getRecordIndex();
        final long first = range.first(part);
        final long last = range.last(part);
        if (first >= 0 && record > first) {
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
        final long part = loc.getPartIndex();
        final long first = range.first(part);
        return parts.contains(part)
                && first >= 0
                && loc.getRecordIndex() >= first
                && loc.getRecordIndex() <= range.last(part);
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
