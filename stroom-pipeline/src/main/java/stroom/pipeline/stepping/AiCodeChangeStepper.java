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

package stroom.pipeline.stepping;

import stroom.ai.api.CodeChangeStepper;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.StoredError;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// [CodeChangeStepper] through Stroom's stepping, as the stepping screen steps: a REFRESH of one record, with the
/// code given for the elements in place of their saved code. Every set of code is stepped in one session, so the
/// stream is read once and the elements left alone are served from what was captured, and the session is ended
/// when the trial ends, however it ends, so no capture is left behind. A step not finished in one request is asked
/// for again until the trial's time limit.
class AiCodeChangeStepper implements CodeChangeStepper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AiCodeChangeStepper.class);

    /// How long each step request waits for the step before answering, so it can be asked for again.
    static final long REQUEST_WAIT_MS = 2_000;
    /// The element given for the pipeline's own errors.
    static final String PIPELINE = "pipeline";

    private final Provider<SteppingService> steppingServiceProvider;
    private final Provider<PipelineStore> pipelineStoreProvider;

    @Inject
    AiCodeChangeStepper(final Provider<SteppingService> steppingServiceProvider,
                        final Provider<PipelineStore> pipelineStoreProvider) {
        this.steppingServiceProvider = steppingServiceProvider;
        this.pipelineStoreProvider = pipelineStoreProvider;
    }

    @Override
    public List<StepOutcome> step(final StepTrial trial) {
        // Read as the user, so a pipeline they can't view is refused.
        final PipelineDoc pipelineDoc = pipelineStoreProvider.get().readDocument(trial.pipeline());
        if (pipelineDoc == null) {
            throw new IllegalStateException("The pipeline can't be read.");
        }
        final SteppingService steppingService = steppingServiceProvider.get();
        final long stopAtMs = System.currentTimeMillis() + trial.timeLimit().toMillis();
        final List<StepOutcome> outcomes = new ArrayList<>();
        String sessionUuid = null;
        PipelineStepRequest last = null;
        try {
            for (final Map<String, String> code : trial.codes()) {
                PipelineStepRequest request = request(trial, pipelineDoc, code, sessionUuid);
                last = request;
                SteppingResult result = steppingService.step(request);
                sessionUuid = NullSafe.getOrElse(result, SteppingResult::getSessionUuid, sessionUuid);
                while (result != null && !result.isComplete()) {
                    if (System.currentTimeMillis() > stopAtMs) {
                        throw new IllegalStateException("Stepping did not finish in time.");
                    }
                    request = request(trial, pipelineDoc, code, sessionUuid);
                    last = request;
                    result = steppingService.step(request);
                    sessionUuid = NullSafe.getOrElse(result, SteppingResult::getSessionUuid, sessionUuid);
                }
                outcomes.add(outcome(result));
            }
            return outcomes;
        } finally {
            terminate(steppingService, last, sessionUuid);
        }
    }

    private static PipelineStepRequest request(final StepTrial trial,
                                               final PipelineDoc pipelineDoc,
                                               final Map<String, String> code,
                                               final String sessionUuid) {
        return new PipelineStepRequest(
                sessionUuid,
                new FindMetaCriteria(MetaExpressionUtil.createDataIdExpression(trial.metaId())),
                trial.childStreamType(),
                new StepLocation(trial.metaId(), trial.partIndex(), trial.recordIndex()),
                StepType.REFRESH,
                null,
                pipelineDoc,
                code,
                1,
                REQUEST_WAIT_MS);
    }

    /// @return What one step gave: each element's problems and output, and the pipeline's own errors.
    static StepOutcome outcome(final SteppingResult result) {
        if (result == null) {
            return new StepOutcome(false, List.of(), Map.of());
        }
        final List<StepProblem> problems = new ArrayList<>();
        final Map<String, String> outputs = new LinkedHashMap<>();
        final Map<String, SharedElementData> elements = NullSafe.getOrElse(result.getStepData(),
                data -> data.getElementMap(), Map.of());
        NullSafe.map(elements).forEach((elementId, data) -> {
            if (data == null) {
                return;
            }
            NullSafe.consume(data.getOutput(), output -> outputs.put(elementId, output));
            for (final StoredError error : NullSafe.list(NullSafe.get(data.getIndicators(),
                    indicators -> indicators.getErrorList()))) {
                problems.add(new StepProblem(
                        NullSafe.getOrElse(error.getElementId(), id -> id.getId(), elementId),
                        NullSafe.get(error.getSeverity(), Enum::name),
                        error.getMessage(),
                        NullSafe.getOrElse(error.getLocation(), location -> location.getLineNo(), 0),
                        NullSafe.getOrElse(error.getLocation(), location -> location.getColNo(), 0)));
            }
        });
        for (final String message : NullSafe.set(result.getGeneralErrors())) {
            problems.add(new StepProblem(PIPELINE, "ERROR", message, 0, 0));
        }
        return new StepOutcome(result.isFoundRecord(), List.copyOf(problems), Map.copyOf(outputs));
    }

    // Ends the session, so its capture of the stream isn't kept until it goes idle.
    private static void terminate(final SteppingService steppingService,
                                  final PipelineStepRequest last,
                                  final String sessionUuid) {
        if (last == null || sessionUuid == null) {
            return;
        }
        try {
            steppingService.terminateStepping(new PipelineStepRequest(sessionUuid, last.getCriteria(),
                    last.getChildStreamType(), null, StepType.REFRESH, null, last.getPipelineDoc(), null, 1, null));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to end stepping session " + sessionUuid, e);
        }
    }
}
