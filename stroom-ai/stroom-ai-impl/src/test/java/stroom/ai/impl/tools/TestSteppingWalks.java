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

import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.SharedStepData;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.pipeline.stepping.SteppingService;
import stroom.util.shared.ElementId;
import stroom.util.shared.Indicators;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestSteppingWalks {

    private final SteppingService steppingService = Mockito.mock(SteppingService.class);
    private final SteppingWalks walks = new SteppingWalks(() -> steppingService);
    private final PipelineDoc pipeline = PipelineDoc.builder().uuid("p").name("P").build();

    @Test
    void recordKeysNameThePartPastTheFirst() {
        assertThat(SteppingWalks.recordKey(38, new StepLocation(38L, 0L, 4L))).isEqualTo("38:4");
        assertThat(SteppingWalks.recordKey(38, new StepLocation(38L, 1L, 0L))).isEqualTo("38:1:0");
    }

    @Test
    void eachStepOfAWalkIsInOneSessionWhichIsEndedAfter() {
        // Seen: 500 records stepped with a fresh session a step ran 500 captures of the same stream at once.
        Mockito.when(steppingService.step(Mockito.any()))
                .thenReturn(result("s1", false, null))
                .thenReturn(result("s1", true, "<out/>"))
                .thenReturn(result("s1", true, "<next/>"));

        try (final SteppingWalks.Walk walk = walks.open(pipeline, 7, Map.of(), Duration.ofSeconds(10))) {
            // A step not finished in one request is asked for again.
            final SteppingResult first = walk.first();
            assertThat(SteppingWalks.output(first, "out")).isEqualTo("<out/>");
            walk.forward(first.getFoundLocation());
        }

        final ArgumentCaptor<PipelineStepRequest> requests = ArgumentCaptor.forClass(PipelineStepRequest.class);
        Mockito.verify(steppingService, Mockito.times(3)).step(requests.capture());
        assertThat(requests.getAllValues()).extracting(PipelineStepRequest::getSessionUuid)
                .containsExactly(null, "s1", "s1");
        assertThat(requests.getAllValues()).extracting(PipelineStepRequest::getStepType)
                .containsExactly(StepType.FIRST, StepType.FIRST, StepType.FORWARD);
        final ArgumentCaptor<PipelineStepRequest> ended = ArgumentCaptor.forClass(PipelineStepRequest.class);
        Mockito.verify(steppingService).terminateStepping(ended.capture());
        assertThat(ended.getValue().getSessionUuid()).isEqualTo("s1");
    }

    @Test
    void aStepsErrorsAreMarkersOfTheirElementAndRecord() {
        final Indicators indicators = new Indicators();
        indicators.add(new StoredError(Severity.FATAL_ERROR, null, null, "XsltPool - bad"));
        final SteppingResult result = new SteppingResult("s", null, null, new StepLocation(7L, 0L, 3L),
                new SharedStepData(null, Map.of("translationFilter",
                        new SharedElementData("in", "out", indicators, false, false, true))),
                null, true, Set.of("Pipeline failed"), false, true);

        final List<ErrorTriage.Marker> markers = SteppingWalks.markers(result, "7:3");

        assertThat(markers).containsExactlyInAnyOrder(
                new ErrorTriage.Marker("FATAL", "translationFilter", "XsltPool - bad", null, "7:3", null),
                new ErrorTriage.Marker("ERROR", SteppingWalks.PIPELINE, "Pipeline failed", null, "7:3", null));
    }

    @Test
    void anElementsOwnIdIsKeptOverTheOneItIsListedUnder() {
        final Indicators indicators = new Indicators();
        indicators.add(new StoredError(Severity.WARNING, null, new ElementId("schemaFilter"), "x"));
        final SteppingResult result = new SteppingResult("s", null, null, null, new SharedStepData(null,
                Map.of("other", new SharedElementData(null, null, indicators, false, false, false))), null, true,
                null, false, true);

        assertThat(SteppingWalks.markers(result, null)).extracting(ErrorTriage.Marker::element)
                .containsExactly("schemaFilter");
    }

    private static SteppingResult result(final String session, final boolean complete, final String output) {
        return new SteppingResult(session, null, null, new StepLocation(7L, 0L, 1L), new SharedStepData(null,
                output == null
                        ? Map.of()
                        : Map.of("out", new SharedElementData("in", output, null, false, false, true))),
                null, complete, null, false, complete);
    }
}
