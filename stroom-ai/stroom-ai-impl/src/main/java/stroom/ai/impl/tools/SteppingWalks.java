/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl.tools;

import stroom.ai.impl.workspace.ToolDeadline;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.pipeline.stepping.SteppingService;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.StoredError;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// Walks of one stream through a pipeline, record by record, with Stroom's stepping, as the user (which needs the
/// Stepping permission).
///
/// Stroom's stepping engine captures the whole stream in a session and keeps the session, and its capture, until
/// it is ended or has been idle for a while; a step with no session starts another capture of the whole stream.
/// Seen: 500 records stepped with a fresh session a step ran 500 captures of the same stream at once and held
/// Stroom's CPU at 100%. So every step of a walk is in one session, and the session is ended when the walk ends,
/// however it ends.
public class SteppingWalks {

    /// How long each step request waits for the step before answering, so it can be asked for again.
    static final long REQUEST_WAIT_MS = 2_000;
    /// The element given for the pipeline's own errors.
    public static final String PIPELINE = "pipeline";

    private final Provider<SteppingService> steppingServiceProvider;

    /// @param steppingServiceProvider Steps the pipelines, as the user.
    @Inject
    public SteppingWalks(final Provider<SteppingService> steppingServiceProvider) {
        this.steppingServiceProvider = steppingServiceProvider;
    }

    /// @param pipeline  The pipeline, as saved.
    /// @param streamId  The stream to walk.
    /// @param code      Code to step with in place of some elements' saved code, by element id, or null.
    /// @param timeLimit The longest one step may take.
    /// @return A walk, to close when done.
    public Walk open(final PipelineDoc pipeline,
                     final long streamId,
                     final Map<String, String> code,
                     final Duration timeLimit) {
        return new Walk(steppingServiceProvider.get(), pipeline, streamId, code, timeLimit);
    }

    /// The errors one step met, each element's and the pipeline's own, as triage markers.
    ///
    /// @param result The step.
    /// @param record The record they are of, e.g. `38:4`, or null.
    /// @return The markers.
    public static List<ErrorTriage.Marker> markers(final SteppingResult result, final String record) {
        final List<ErrorTriage.Marker> markers = new ArrayList<>();
        NullSafe.map(elements(result)).forEach((element, data) -> {
            for (final StoredError error : NullSafe.set(NullSafe.get(data, SharedElementData::getIndicators,
                    indicators -> indicators.getUniqueErrorSet()))) {
                ErrorTriage.Marker marker = ErrorTriage.fromStoredError(error);
                if ("unknown".equals(marker.element())) {
                    marker = marker.withElement(element);
                }
                markers.add(marker.withRecord(record));
            }
        });
        for (final String message : NullSafe.set(result.getGeneralErrors())) {
            markers.add(ErrorTriage.marker("ERROR", PIPELINE, message, null).withRecord(record));
        }
        return markers;
    }

    /// @return Each element's data from a step, by element id.
    public static Map<String, SharedElementData> elements(final SteppingResult result) {
        return NullSafe.getOrElse(result, SteppingResult::getStepData, data -> data.getElementMap(), Map.of());
    }

    /// @return An element's output from a step, or an empty string.
    public static String output(final SteppingResult result, final String element) {
        return NullSafe.getOrElse(elements(result).get(element), SharedElementData::getOutput, "");
    }

    /// @return An element's input from a step, or null.
    public static String input(final SteppingResult result, final String element) {
        return NullSafe.get(elements(result).get(element), SharedElementData::getInput);
    }

    /// `stream:record`, or `stream:part:record` past the first part (record numbers restart in each part).
    ///
    /// @param streamId The stream.
    /// @param location Where in it.
    /// @return The record's key.
    public static String recordKey(final long streamId, final StepLocation location) {
        return location.getPartIndex() > 0
                ? streamId + ":" + location.getPartIndex() + ":" + location.getRecordIndex()
                : streamId + ":" + location.getRecordIndex();
    }


    // --------------------------------------------------------------------------------


    /// One walk of a stream, in one stepping session.
    public static final class Walk implements AutoCloseable {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Walk.class);

        private final SteppingService steppingService;
        private final PipelineDoc pipeline;
        private final long streamId;
        private final Map<String, String> code;
        private final Duration timeLimit;
        private String sessionUuid;

        private Walk(final SteppingService steppingService,
                     final PipelineDoc pipeline,
                     final long streamId,
                     final Map<String, String> code,
                     final Duration timeLimit) {
            this.steppingService = steppingService;
            this.pipeline = pipeline;
            this.streamId = streamId;
            this.code = NullSafe.map(code);
            this.timeLimit = timeLimit;
        }

        /// @return The stream's first record, stepped.
        /// @throws IllegalStateException if the step doesn't finish in time.
        /// @throws stroom.ai.impl.workspace.ToolTimeoutException if the call is cancelled, or runs past its time
        /// limit ([ToolDeadline]), while the step is unfinished.
        public SteppingResult first() {
            return step(StepType.FIRST, null);
        }

        /// @return The stream's last record, stepped.
        /// @throws IllegalStateException if the step doesn't finish in time.
        public SteppingResult last() {
            return step(StepType.LAST, null);
        }

        /// @param location Where the last step was.
        /// @return The next record, stepped.
        /// @throws IllegalStateException if the step doesn't finish in time.
        public SteppingResult forward(final StepLocation location) {
            return step(StepType.FORWARD, location);
        }

        /// @param location A record.
        /// @return That record, stepped again.
        /// @throws IllegalStateException if the step doesn't finish in time.
        public SteppingResult refresh(final StepLocation location) {
            return step(StepType.REFRESH, location);
        }

        private SteppingResult step(final StepType stepType, final StepLocation location) {
            final long stopAtMs = System.currentTimeMillis() + timeLimit.toMillis();
            SteppingResult result = steppingService.step(request(stepType, location));
            sessionUuid = NullSafe.getOrElse(result, SteppingResult::getSessionUuid, sessionUuid);
            while (result != null && !result.isComplete()) {
                // Stops at once when the call is cancelled (its thread interrupted) or past its time limit.
                ToolDeadline.check();
                if (System.currentTimeMillis() > stopAtMs) {
                    throw new IllegalStateException("Stepping did not finish in time; try fewer records or a "
                                                    + "smaller stream");
                }
                result = steppingService.step(request(stepType, location));
                sessionUuid = NullSafe.getOrElse(result, SteppingResult::getSessionUuid, sessionUuid);
            }
            return result;
        }

        private PipelineStepRequest request(final StepType stepType, final StepLocation location) {
            return PipelineStepRequest.builder()
                    .sessionUuid(sessionUuid)
                    .criteria(new FindMetaCriteria(MetaExpressionUtil.createDataIdExpression(streamId)))
                    .stepLocation(location)
                    .stepType(stepType)
                    .pipelineDoc(pipeline)
                    .code(code)
                    .stepSize(1)
                    .timeout(REQUEST_WAIT_MS)
                    .build();
        }

        /// Ends the session, so its capture of the stream isn't kept until it goes idle.
        @Override
        public void close() {
            if (sessionUuid == null) {
                return;
            }
            try {
                steppingService.terminateStepping(request(StepType.REFRESH, null));
            } catch (final RuntimeException e) {
                LOGGER.debug("Unable to end stepping session {}", sessionUuid, e);
            }
        }
    }
}
