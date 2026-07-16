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

package stroom.pipeline.stepping;

import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.SourceLocation;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.SharedStepData;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.shared.stepping.SteppingFilterSettings;
import stroom.util.shared.ElementId;
import stroom.util.shared.NullSafe;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Resolves a requested step (FIRST/FORWARD/BACKWARD/LAST/REFRESH, with optional filters) against a
 * single stream's populated {@link StepDataStore} - i.e. by lookup/scan, with no pipeline reprocessing.
 * <p>
 * This is the Phase 2c "serve a step from the store" logic, scoped to one stream (cross-stream
 * navigation is added with the async session in Phase 3). Records are ordered by (partIndex, recordIndex).
 * Filtering mirrors the live {@code SteppingController.endRecord} semantics: a record is a match if no
 * filters are applied, or if any applied element's filter matches (see {@link PersistedFilterEvaluator}).
 */
public class StepResultResolver {

    private final PersistedFilterEvaluator filterEvaluator = new PersistedFilterEvaluator();

    /**
     * Resolve a step across a whole {@link SteppingSession}, waiting for records to be captured and
     * lazily sweeping neighbouring streams as navigation crosses stream boundaries. Streams are swept on
     * demand (never all up front): FORWARD/FIRST that exhaust a completed stream continue into the next
     * stream; BACKWARD/LAST into the previous. LAST waits for the target stream to finish so it can find
     * the true last record. REFRESH is exact and never crosses.
     *
     * @param timeoutMs how long to wait for the requested record before returning an "incomplete" result
     *                  (the client then polls again).
     */
    public SessionStepResult resolveSession(final SteppingSession session,
                                            final PipelineStepRequest request,
                                            final long timeoutMs) {
        final long deadline = System.currentTimeMillis() + timeoutMs;
        final StepType stepType = request.getStepType();
        final boolean forward = stepType == StepType.FIRST || stepType == StepType.FORWARD;
        final StepLocation ref = request.getStepLocation();

        final OptionalLong initial = initialStream(session, stepType, ref);
        if (initial.isEmpty()) {
            return SessionStepResult.notFound();
        }
        long currentStream = initial.getAsLong();
        boolean crossed = false;

        while (true) {
            if (System.currentTimeMillis() >= deadline) {
                return SessionStepResult.incomplete(null);
            }
            final StreamSweep sweep = session.ensureStreamSwept(currentStream);
            final long version = sweep.getVersion();

            // On the first stream use the requested step; after crossing a boundary, take the first
            // (forward) or last (backward) record of the neighbour, preserving any filters.
            final PipelineStepRequest streamRequest = crossed
                    ? request.copy()
                            .stepType(forward ? StepType.FIRST : StepType.LAST)
                            .stepLocation(null)
                            .build()
                    : request;

            // LAST needs the stream fully captured to know the true last record.
            if (streamRequest.getStepType() == StepType.LAST && !sweep.isComplete()) {
                final long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0 || !sweep.awaitComplete(remaining)) {
                    return SessionStepResult.incomplete(sweep.getLastCapturedLocation());
                }
                continue;
            }

            final Optional<ResolvedStep> resolved = resolve(
                    sweep.getStore(), currentStream, session.getFingerprints(), streamRequest);
            if (resolved.isPresent()) {
                return SessionStepResult.resolved(resolved.get().foundLocation(), resolved.get().stepData());
            }

            if (sweep.getError() != null) {
                final String message = sweep.getError().getMessage();
                return SessionStepResult.error(message != null ? message : "Stepping capture error");
            }

            if (!sweep.isComplete()) {
                // The target record may still be captured in this stream; wait for progress.
                final long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0 || !sweep.awaitChangeSince(version, remaining)) {
                    return SessionStepResult.incomplete(sweep.getLastCapturedLocation());
                }
                continue;
            }

            // Stream fully captured with no match in the required direction. REFRESH is exact - no cross.
            if (stepType == StepType.REFRESH) {
                return SessionStepResult.notFound();
            }
            final OptionalLong neighbour = forward
                    ? session.nextStreamId(currentStream)
                    : session.prevStreamId(currentStream);
            if (neighbour.isEmpty()) {
                return SessionStepResult.notFound();
            }
            currentStream = neighbour.getAsLong();
            crossed = true;
        }
    }

    private OptionalLong initialStream(final SteppingSession session,
                                       final StepType stepType,
                                       final StepLocation ref) {
        return switch (stepType) {
            case FIRST -> session.firstStreamId();
            case LAST -> session.lastStreamId();
            case FORWARD -> ref != null ? OptionalLong.of(ref.getMetaId()) : session.firstStreamId();
            case BACKWARD -> ref != null ? OptionalLong.of(ref.getMetaId()) : session.lastStreamId();
            case REFRESH -> ref != null ? OptionalLong.of(ref.getMetaId()) : OptionalLong.empty();
        };
    }

    /**
     * @return the resolved record location and assembled step data, or empty if no matching record exists
     * in this stream.
     */
    public Optional<ResolvedStep> resolve(final StepDataStore store,
                                          final long metaId,
                                          final ElementFingerprints fingerprints,
                                          final PipelineStepRequest request) {
        final List<Long> parts = store.getPartIndices();
        if (parts.isEmpty()) {
            return Optional.empty();
        }

        final StepType stepType = request.getStepType();
        // A reference location only makes sense for the stream being resolved; ignore one from another
        // stream (cross-stream navigation is handled by the caller in a later phase).
        final StepLocation requestRef = request.getStepLocation();
        final StepLocation ref = (requestRef != null && requestRef.getMetaId() == metaId) ? requestRef : null;

        final Optional<StepLocation> target = switch (stepType) {
            case FIRST -> scanForward(store, parts, metaId, firstRecord(store, parts, metaId), request, fingerprints);
            case LAST -> scanBackward(store, parts, metaId, lastRecord(store, parts, metaId), request, fingerprints);
            case FORWARD -> {
                final StepLocation start = ref == null
                        ? firstRecord(store, parts, metaId)
                        : next(store, parts, metaId, ref).orElse(null);
                yield start == null
                        ? Optional.empty()
                        : scanForward(store, parts, metaId, start, request, fingerprints);
            }
            case BACKWARD -> {
                final StepLocation start = ref == null
                        ? lastRecord(store, parts, metaId)
                        : prev(store, parts, metaId, ref).orElse(null);
                yield start == null
                        ? Optional.empty()
                        : scanBackward(store, parts, metaId, start, request, fingerprints);
            }
            case REFRESH -> (ref != null && exists(store, parts, ref))
                    ? Optional.of(new StepLocation(metaId, ref.getPartIndex(), ref.getRecordIndex()))
                    : Optional.empty();
        };

        return target.map(loc -> new ResolvedStep(loc, assemble(store, metaId, fingerprints, loc)));
    }

    // --- scanning -------------------------------------------------------------------------------

    private Optional<StepLocation> scanForward(final StepDataStore store,
                                               final List<Long> parts,
                                               final long metaId,
                                               final StepLocation start,
                                               final PipelineStepRequest request,
                                               final ElementFingerprints fingerprints) {
        StepLocation loc = start;
        while (loc != null) {
            if (matches(store, loc, request, fingerprints)) {
                return Optional.of(loc);
            }
            loc = next(store, parts, metaId, loc).orElse(null);
        }
        return Optional.empty();
    }

    private Optional<StepLocation> scanBackward(final StepDataStore store,
                                                final List<Long> parts,
                                                final long metaId,
                                                final StepLocation start,
                                                final PipelineStepRequest request,
                                                final ElementFingerprints fingerprints) {
        StepLocation loc = start;
        while (loc != null) {
            if (matches(store, loc, request, fingerprints)) {
                return Optional.of(loc);
            }
            loc = prev(store, parts, metaId, loc).orElse(null);
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
            final SharedElementData data = readElement(store, loc, entry.getKey(), fingerprints);
            if (data != null && filterEvaluator.matches(
                    data, settings, loc.getMetaId(), loc.getRecordIndex())) {
                return true;
            }
        }
        // No applied filters => every record is a match; otherwise a match needed at least one hit.
        return !anyApplied;
    }

    // --- navigation over (part, record) ---------------------------------------------------------

    private StepLocation firstRecord(final StepDataStore store, final List<Long> parts, final long metaId) {
        final long part = parts.get(0);
        return new StepLocation(metaId, part, store.getFirstRecordIndex(part));
    }

    private StepLocation lastRecord(final StepDataStore store, final List<Long> parts, final long metaId) {
        final long lastPart = parts.get(parts.size() - 1);
        return new StepLocation(metaId, lastPart, store.getLastRecordIndex(lastPart));
    }

    private Optional<StepLocation> next(final StepDataStore store,
                                        final List<Long> parts,
                                        final long metaId,
                                        final StepLocation loc) {
        final long part = loc.getPartIndex();
        final long record = loc.getRecordIndex();
        if (record < store.getLastRecordIndex(part)) {
            return Optional.of(new StepLocation(metaId, part, record + 1));
        }
        final int idx = parts.indexOf(part);
        if (idx >= 0 && idx + 1 < parts.size()) {
            final long nextPart = parts.get(idx + 1);
            return Optional.of(new StepLocation(metaId, nextPart, store.getFirstRecordIndex(nextPart)));
        }
        return Optional.empty();
    }

    private Optional<StepLocation> prev(final StepDataStore store,
                                        final List<Long> parts,
                                        final long metaId,
                                        final StepLocation loc) {
        final long part = loc.getPartIndex();
        final long record = loc.getRecordIndex();
        if (record > store.getFirstRecordIndex(part)) {
            return Optional.of(new StepLocation(metaId, part, record - 1));
        }
        final int idx = parts.indexOf(part);
        if (idx > 0) {
            final long prevPart = parts.get(idx - 1);
            return Optional.of(new StepLocation(metaId, prevPart, store.getLastRecordIndex(prevPart)));
        }
        return Optional.empty();
    }

    private boolean exists(final StepDataStore store, final List<Long> parts, final StepLocation loc) {
        return parts.contains(loc.getPartIndex())
                && loc.getRecordIndex() >= store.getFirstRecordIndex(loc.getPartIndex())
                && loc.getRecordIndex() <= store.getLastRecordIndex(loc.getPartIndex());
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
        final SourceLocation sourceLocation = SourceLocation.builder(metaId)
                .withPartIndex(loc.getPartIndex())
                .withRecordIndex(loc.getRecordIndex())
                .build();
        return new SharedStepData(sourceLocation, map);
    }

    private SharedElementData readElement(final StepDataStore store,
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

    /**
     * The outcome of a whole-session step resolution.
     *
     * @param foundRecord      whether a matching record was found.
     * @param complete         whether the step query resolved (true) or is still waiting on capture (false).
     * @param foundLocation    the resolved record (when found).
     * @param stepData         the assembled per-element data (when found).
     * @param progressLocation the furthest-captured record while still sweeping (for progress display).
     * @param generalError     a capture error message, if the sweep failed.
     */
    public record SessionStepResult(boolean foundRecord,
                                    boolean complete,
                                    StepLocation foundLocation,
                                    SharedStepData stepData,
                                    StepLocation progressLocation,
                                    String generalError) {

        static SessionStepResult resolved(final StepLocation location, final SharedStepData stepData) {
            return new SessionStepResult(true, true, location, stepData, null, null);
        }

        static SessionStepResult notFound() {
            return new SessionStepResult(false, true, null, null, null, null);
        }

        static SessionStepResult incomplete(final StepLocation progressLocation) {
            return new SessionStepResult(false, false, null, null, progressLocation, null);
        }

        static SessionStepResult error(final String message) {
            return new SessionStepResult(false, true, null, null, null, message);
        }
    }
}
