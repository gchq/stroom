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

import stroom.ai.api.CodeChangeStepper.StepOutcome;
import stroom.ai.api.CodeChangeStepper.StepProblem;
import stroom.ai.api.CodeChangeStepper.StepTrial;
import stroom.docref.DocRef;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.SharedStepData;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.ElementId;
import stroom.util.shared.Indicators;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestAiCodeChangeStepper {

    private static final DocRef PIPELINE = new DocRef(PipelineDoc.TYPE, "pipe-uuid", "Twitter-Events");
    private static final Map<String, String> CURRENT = Map.of("translationFilter", "<current/>");
    private static final Map<String, String> PROPOSED = Map.of("translationFilter", "<proposed/>");

    private final SteppingService steppingService = Mockito.mock(SteppingService.class);
    private final PipelineStore pipelineStore = Mockito.mock(PipelineStore.class);
    private final AiCodeChangeStepper stepper = new AiCodeChangeStepper(() -> steppingService, () -> pipelineStore);

    @BeforeEach
    void setUp() {
        Mockito.when(pipelineStore.readDocument(PIPELINE)).thenReturn(PipelineDoc.builder().uuid("pipe-uuid")
                .name("Twitter-Events").build());
    }

    @Test
    void eachSetOfCodeIsSteppedInOneSessionThatIsThenEnded() {
        final StoredError invalid = new StoredError(Severity.ERROR, new DefaultLocation(12, 9),
                new ElementId("schemaFilter"), "Invalid content was found starting with element 'Document'.");
        Mockito.when(steppingService.step(Mockito.any())).thenReturn(
                // The first step isn't finished in one request, so it is asked for again.
                result("session-1", false, Map.of()),
                result("session-1", true, Map.of("translationFilter", element("<Events/>", null))),
                result("session-1", true, Map.of("translationFilter", element("<Events>Send</Events>", null),
                        "schemaFilter", element(null, invalid))));

        final List<StepOutcome> outcomes = stepper.step(trial());

        assertThat(outcomes).hasSize(2);
        assertThat(outcomes.get(0).problems()).isEmpty();
        assertThat(outcomes.get(0).outputs()).containsEntry("translationFilter", "<Events/>");
        assertThat(outcomes.get(1).foundRecord()).isTrue();
        assertThat(outcomes.get(1).problems()).containsExactly(new StepProblem("schemaFilter", "ERROR",
                "Invalid content was found starting with element 'Document'.", 12, 9));

        final ArgumentCaptor<PipelineStepRequest> requests = ArgumentCaptor.forClass(PipelineStepRequest.class);
        Mockito.verify(steppingService, Mockito.times(3)).step(requests.capture());
        final List<PipelineStepRequest> sent = requests.getAllValues();
        // The record the user is stepping, refreshed, with the code given.
        assertThat(sent.getFirst().getStepType()).isEqualTo(StepType.REFRESH);
        assertThat(sent.getFirst().getStepLocation().getMetaId()).isEqualTo(73146L);
        assertThat(sent.getFirst().getStepLocation().getPartIndex()).isZero();
        assertThat(sent.getFirst().getStepLocation().getRecordIndex()).isEqualTo(4L);
        assertThat(sent.getFirst().getCode()).isEqualTo(CURRENT);
        assertThat(sent.getFirst().getSessionUuid()).isNull();
        // Later steps carry on the session, so the stream is read once.
        assertThat(sent.get(1).getSessionUuid()).isEqualTo("session-1");
        assertThat(sent.get(2).getSessionUuid()).isEqualTo("session-1");
        assertThat(sent.get(2).getCode()).isEqualTo(PROPOSED);
        final ArgumentCaptor<PipelineStepRequest> terminated = ArgumentCaptor.forClass(PipelineStepRequest.class);
        Mockito.verify(steppingService).terminateStepping(terminated.capture());
        assertThat(terminated.getValue().getSessionUuid()).isEqualTo("session-1");
    }

    @Test
    void theSessionIsEndedWhenSteppingFailsOrRunsOutOfTime() {
        Mockito.when(steppingService.step(Mockito.any())).thenReturn(result("session-2", false, Map.of()));

        assertThatThrownBy(() -> stepper.step(new StepTrial(PIPELINE, 73146L, null, 0, 4, List.of(CURRENT),
                Duration.ofMillis(50))))
                .hasMessageContaining("did not finish in time");
        Mockito.verify(steppingService).terminateStepping(Mockito.argThat(request ->
                "session-2".equals(request.getSessionUuid())));
    }

    @Test
    void aPipelineTheUserCantReadIsRefused() {
        Mockito.when(pipelineStore.readDocument(PIPELINE)).thenReturn(null);

        assertThatThrownBy(() -> stepper.step(trial())).hasMessageContaining("can't be read");
        Mockito.verifyNoInteractions(steppingService);
    }

    @Test
    void thePipelinesOwnErrorsAreGiven() {
        final StepOutcome outcome = AiCodeChangeStepper.outcome(new SteppingResult("s", null, null, null, null,
                null, true, Set.of("No pipeline elements"), false, true));

        assertThat(outcome.problems()).containsExactly(new StepProblem(AiCodeChangeStepper.PIPELINE, "ERROR",
                "No pipeline elements", 0, 0));
        assertThat(AiCodeChangeStepper.outcome(null).foundRecord()).isFalse();
    }

    private static StepTrial trial() {
        return new StepTrial(PIPELINE, 73146L, null, 0, 4, List.of(CURRENT, PROPOSED), Duration.ofSeconds(30));
    }

    private static SteppingResult result(final String session,
                                         final boolean complete,
                                         final Map<String, SharedElementData> elements) {
        return new SteppingResult(session, null, null, null, new SharedStepData(null, elements), null, complete,
                Set.of(), false, complete);
    }

    private static SharedElementData element(final String output, final StoredError error) {
        final Indicators indicators = new Indicators();
        if (error != null) {
            indicators.add(error);
        }
        return new SharedElementData(null, output, indicators, false, false, output != null);
    }
}
