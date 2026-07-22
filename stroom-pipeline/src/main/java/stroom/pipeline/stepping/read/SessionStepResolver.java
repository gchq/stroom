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
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.SharedStepData;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.stepping.capture.StreamSweep;
import stroom.pipeline.stepping.fingerprint.ElementFingerprints;
import stroom.pipeline.stepping.session.SteppingSession;
import stroom.util.shared.ElementId;
import stroom.util.shared.Indicators;

import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Resolves a step against a whole {@link SteppingSession} - the entry point the service uses.
 * <p>
 * Everything asynchronous about stepping lives here. {@link StoreStepResolver} answers from one stream's
 * captured data and nothing else; this class decides <b>which</b> stream to ask, <b>waits</b> when the
 * answer has not been captured yet, and <b>crosses</b> into neighbouring streams when a step runs off the
 * end of one. Streams are swept on demand, never all up front.
 * <p>
 * The subtle part is telling "not captured yet" apart from "no such record". Both look like an empty result
 * from the store, and they are distinguished by whether the sweep is fully captured:
 * <ul>
 *   <li><b>Still capturing</b> - wait for progress and re-scan. Concluding "no match" here would step over
 *       records that are merely late, and they would never be reachable again.</li>
 *   <li><b>Fully captured</b> - the range is final, so empty really does mean the end of the stream: cross
 *       into the neighbour (except REFRESH, which is exact and never crosses).</li>
 * </ul>
 * The version is read <b>before</b> scanning and re-checked after, so a record landing mid-scan is never
 * mistaken for the end of a stream.
 */
public class SessionStepResolver {

    private final StoreStepResolver storeStepResolver;

    @Inject
    public SessionStepResolver(final StoreStepResolver storeStepResolver) {
        this.storeStepResolver = storeStepResolver;
    }

    /**
     * Resolve a step across a whole {@link SteppingSession}, waiting for records to be captured and
     * lazily sweeping neighbouring streams as navigation crosses stream boundaries. Streams are swept on
     * demand (never all up front): FORWARD/FIRST that exhaust a completed stream continue into the next
     * stream; BACKWARD/LAST into the previous. LAST waits for the target stream to finish so it can find
     * the true last record. REFRESH is exact and never crosses.
     *
     * @param fingerprints the configuration to serve this step under; an edit changes these, which starts a
     *                     new sweep while leaving the pre-edit one cached for a revert.
     * @param timeoutMs    how long to wait for the requested record before returning an "incomplete" result
     *                     (the client then polls again).
     */
    public SessionStepResult resolve(final SteppingSession session,
                                     final PipelineStepRequest request,
                                     final ElementFingerprints fingerprints,
                                     final long timeoutMs) {
        // Saturate rather than overflow: callers legitimately pass Long.MAX_VALUE to mean "wait as long as it
        // takes", and a wrapped deadline lands in the past, which would abandon every step immediately.
        final long now = System.currentTimeMillis();
        final long deadline = timeoutMs >= Long.MAX_VALUE - now
                ? Long.MAX_VALUE
                : now + timeoutMs;
        final StepType stepType = request.getStepType();
        final boolean forward = stepType == StepType.FIRST || stepType == StepType.FORWARD;
        final StepLocation ref = request.getStepLocation();

        final OptionalLong initial = initialStream(session, stepType, ref);
        if (initial.isEmpty()) {
            return SessionStepResult.notFound();
        }
        long currentStream = initial.getAsLong();
        boolean crossed = false;
        StreamSweep lastSweep = null;

        while (true) {
            if (System.currentTimeMillis() >= deadline) {
                // Report how far the sweep got, so the UI's progress indicator keeps advancing rather than
                // blanking out when a step's budget expires mid-sweep.
                return SessionStepResult.incomplete(
                        lastSweep == null ? null : lastSweep.getLastCapturedLocation());
            }
            final StreamSweep sweep = session.sweepFor(currentStream, request, fingerprints);
            lastSweep = sweep;
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
            if (streamRequest.getStepType() == StepType.LAST && !sweep.isFullyCaptured()) {
                final long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0 || !sweep.awaitFullyCaptured(remaining)) {
                    return SessionStepResult.incomplete(sweep.getLastCapturedLocation());
                }
                continue;
            }

            final Optional<StoreStepResolver.ResolvedStep> resolved = storeStepResolver.resolve(
                    sweep.getStore(), currentStream, fingerprints, streamRequest);
            if (resolved.isPresent()) {
                final StepLocation found = resolved.get().foundLocation();
                return SessionStepResult.resolved(
                        found,
                        // Indicators raised during pipeline startup belong to the stream, so they are folded
                        // into whichever record the step lands on - exactly as the live path does.
                        mergeStartProcessIndicators(resolved.get().stepData(), sweep.getStartProcessIndicators()),
                        sweep.isSegmented(found.getPartIndex()));
            }

            if (sweep.getError() != null) {
                final String message = sweep.getError().getMessage();
                return SessionStepResult.error(message != null ? message : "Stepping capture error");
            }

            if (!sweep.isFullyCaptured()) {
                // The target record may still be captured in this stream; wait for progress.
                final long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0 || !sweep.awaitChangeSince(version, remaining)) {
                    return SessionStepResult.incomplete(sweep.getLastCapturedLocation());
                }
                continue;
            }

            if (sweep.getVersion() != version) {
                // Records landed while we were scanning, and the sweep has since completed. Our scan is
                // stale, so concluding "no match in this stream" here would step straight over them into the
                // next stream - and they would never be reachable again. Re-scan against the final store.
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

    /**
     * Pick the stream a step starts from. The reference location comes from the client, so its stream is
     * only honoured if it is one of the session's candidate streams - that list is resolved as the
     * requesting user, and sweeping a stream outside it would both read data the session was never scoped
     * to and capture a stream the user may have no permission to see.
     * <p>
     * FORWARD falls back to the first stream when the reference is unusable, mirroring the live path
     * (which resets to {@code StepLocation.first} when the stream is not in its filtered list).
     */
    private OptionalLong initialStream(final SteppingSession session,
                                       final StepType stepType,
                                       final StepLocation ref) {
        final OptionalLong refStream = ref != null && session.containsStream(ref.getMetaId())
                ? OptionalLong.of(ref.getMetaId())
                : OptionalLong.empty();
        return switch (stepType) {
            case FIRST -> session.firstStreamId();
            case LAST -> session.lastStreamId();
            case FORWARD -> refStream.isPresent() ? refStream : session.firstStreamId();
            case BACKWARD -> refStream.isPresent() ? refStream : session.lastStreamId();
            case REFRESH -> refStream;
        };
    }
    /**
     * Fold a stream's startup indicators into the step data for a record. The live path merges these into
     * the element data it is about to return; here the data has been read back from the store, so the
     * (immutable) element data is rebuilt with the combined indicators. An element that raised indicators
     * while starting up but never captured any IO still gets an entry, or its errors would be invisible.
     */
    private SharedStepData mergeStartProcessIndicators(final SharedStepData stepData,
                                                       final Map<ElementId, Indicators> startProcessIndicators) {
        if (stepData == null || startProcessIndicators.isEmpty()) {
            return stepData;
        }
        final Map<String, SharedElementData> merged = new HashMap<>(stepData.getElementMap());
        startProcessIndicators.forEach((elementId, indicators) -> {
            final SharedElementData existing = merged.get(elementId.getId());
            merged.put(elementId.getId(), existing == null
                    ? new SharedElementData(null, null, indicators, false, false, false)
                    : new SharedElementData(
                            existing.getInput(),
                            existing.getOutput(),
                            Indicators.combine(indicators, existing.getIndicators()),
                            existing.isFormatInput(),
                            existing.isFormatOutput(),
                            existing.isHasOutput()));
        });
        return new SharedStepData(stepData.getSourceLocation(), merged);
    }

    // --------------------------------------------------------------------------------

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
                                    String generalError,
                                    boolean segmentedData) {

        static SessionStepResult resolved(final StepLocation location,
                                          final SharedStepData stepData,
                                          final boolean segmentedData) {
            return new SessionStepResult(true, true, location, stepData, null, null, segmentedData);
        }

        static SessionStepResult notFound() {
            return new SessionStepResult(false, true, null, null, null, null, false);
        }

        static SessionStepResult incomplete(final StepLocation progressLocation) {
            return new SessionStepResult(false, false, null, null, progressLocation, null, false);
        }

        static SessionStepResult error(final String message) {
            return new SessionStepResult(false, true, null, null, null, message, false);
        }
    }}
